package io.github.fernsehheft.skysong.showcase;

import io.github.fernsehheft.skysong.Skysong;
import io.github.fernsehheft.skysong.audio.AudioListener;
import io.github.fernsehheft.skysong.audio.AudioState;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * A camera that films the player like a music video: it cuts to a new shot whenever a new
 * line of lyrics starts and makes one big slow turn at the end of the song. Only the
 * player's own view changes; the player stands still, also for everybody else on a server.
 */
public final class Showcase {
    /** How long the camera swings from one shot to the next. */
    private static final long CUT_MS = 700;
    /** Without lyrics to cut on, the shot changes this often. */
    private static final long SHOT_MS_WITHOUT_LYRICS = 6000;
    /** The big turn at the end starts this long before the song is over. */
    private static final long FINALE_MS = 12_000;
    private static final double FINALE_RADIUS = 6;
    /** Keeps the camera this far in front of walls it would otherwise end up in. */
    private static final double WALL_MARGIN = 0.3;
    private static final double LYRICS_ABOVE_HEAD = 1.4;
    /** How long a cut may wait for the next beat. */
    private static final long MAX_CUT_WAIT_MS = 500;

    private enum Shot {
        /** Circles the player slowly. */
        ORBIT,
        /** Close to the ground, looking up at the player and the sky. */
        LOW_ANGLE,
        /** Far and high, the player small in the landscape. */
        WIDE,
        /** Just behind the player, looking where they look. */
        OVER_SHOULDER,
        /** From the player's eyes, up into the sky. */
        SKYWARD
    }

    private record Pose(Vec3 position, float yaw, float pitch) {
    }

    private static final Showcase INSTANCE = new Showcase();

    private final Random random = new Random();
    private LyricsPlayback playback;
    private AudioListener audio;
    /** A cut that waits for the next beat, so it lands on it; 0 when none is waiting. */
    private long cutWantedAt;
    private long beatsSeen;

    private boolean active;
    private boolean hudWasHidden;
    private Vec3 anchor;
    private float anchorYaw;
    private float healthAtStart;

    private Shot shot = Shot.ORBIT;
    /** Turns each shot to a different side of the player, so cuts actually change the view. */
    private float shotAngle;
    private long shotStartedAt;
    private Pose cutFrom;
    private Pose lastPose;
    private int lastLineIndex = Integer.MIN_VALUE;
    private Float finaleStartAngle;

    private Showcase() {
    }

    public static Showcase get() {
        return INSTANCE;
    }

    public void setPlayback(LyricsPlayback playback) {
        this.playback = playback;
    }

    public void setAudio(AudioListener audio) {
        this.audio = audio;
    }

    public boolean active() {
        return active;
    }

    public void toggle() {
        if (active) {
            stop(Component.translatable("skysong.showcase.stopped"));
        } else {
            start();
        }
    }

    public void start() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || active) {
            return;
        }
        active = true;
        anchor = player.getEyePosition();
        anchorYaw = player.getYRot();
        healthAtStart = player.getHealth();
        hudWasHidden = minecraft.options.hideGui;
        // The game HUD would spoil the video; Skysong's own HUD stays (see hudWhenHidden).
        minecraft.options.hideGui = true;
        lastPose = null;
        lastLineIndex = Integer.MIN_VALUE;
        finaleStartAngle = null;
        shotAngle = 180;
        cutTo(Shot.ORBIT, System.currentTimeMillis());
        minecraft.gui.setOverlayMessage(Component.translatable("skysong.showcase.started"), false);
        Skysong.LOGGER.info("Showcase started");
    }

    public void stop(Component reason) {
        if (!active) {
            return;
        }
        active = false;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.hideGui = hudWasHidden;
        minecraft.gui.setOverlayMessage(reason, false);
        Skysong.LOGGER.info("Showcase ended: {}", reason.getString());
    }

    /** Called every client tick: ends the showcase when the player is hurt or leaves the world. */
    public void tick() {
        if (!active) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            active = false;
            return;
        }
        if (player.hurtTime > 0 || player.getHealth() < healthAtStart || player.isDeadOrDying()) {
            stop(Component.translatable("skysong.showcase.hurt"));
        }
    }

    /**
     * Where the camera is this frame, or null when the showcase isn't running. Decides on
     * cuts first, so lyrics that start in this frame are placed in front of the new shot.
     */
    public CameraPose pose() {
        if (!active || anchor == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        LyricsPlayback.Moment moment = playback == null ? null : playback.now().orElse(null);
        Pose target = target(moment, now);

        float blend = (float) Math.min(1.0, (now - shotStartedAt) / (double) CUT_MS);
        float eased = blend * blend * (3 - 2 * blend);
        Pose pose = target;
        if (cutFrom != null && eased < 1) {
            pose = new Pose(
                    cutFrom.position().lerp(target.position(), eased),
                    cutFrom.yaw() + Mth.wrapDegrees(target.yaw() - cutFrom.yaw()) * eased,
                    Mth.lerp(eased, cutFrom.pitch(), target.pitch()));
        }
        lastPose = pose;
        return new CameraPose(pose.position(), pose.yaw(), pose.pitch());
    }

    /** The camera's place and direction for the shot that's on, cutting to the next one when it's time. */
    private Pose target(LyricsPlayback.Moment moment, long now) {
        boolean finale = moment != null && moment.track().durationMs() > 0
                && moment.track().durationMs() - moment.track().positionAt(System.nanoTime()) < FINALE_MS;
        if (finale) {
            return finalePose(moment);
        }
        finaleStartAngle = null;

        AudioState sound = audio == null ? AudioState.NONE : audio.state();
        boolean beats = sound.running() && !sound.silent() && sound.bpm() > 0;
        boolean onBeat = beats && sound.beatCount() != beatsSeen;
        beatsSeen = sound.beatCount();

        int line = moment == null || moment.lyrics() == null ? -1 : moment.lineIndex();
        boolean newLine = line >= 0 && line != lastLineIndex
                && !moment.lyrics().lines().get(line).isBreak();
        // Without lyrics: a new shot every eight beats, or every few seconds without a beat.
        long shotLength = beats ? Math.round(8 * 60_000 / sound.bpm()) : SHOT_MS_WITHOUT_LYRICS;
        boolean timeUp = line < 0 && now - shotStartedAt > shotLength;
        if ((newLine || timeUp) && cutWantedAt == 0) {
            cutWantedAt = now;
        }
        // With a beat to go by, the cut waits for it, but never long.
        boolean cutNow = cutWantedAt != 0 && (!beats || onBeat || now - cutWantedAt > MAX_CUT_WAIT_MS);
        if (cutNow) {
            cutWantedAt = 0;
            Shot next = Shot.values()[random.nextInt(Shot.values().length)];
            if (next == shot) {
                next = Shot.values()[(next.ordinal() + 1) % Shot.values().length];
            }
            // Jump to another side of the player, but never all the way round in one cut.
            shotAngle += 70 + random.nextFloat() * 110;
            cutTo(next, now);
        }
        if (line >= 0) {
            lastLineIndex = line;
        }
        return shotPose(shot, now);
    }

    private void cutTo(Shot next, long now) {
        cutFrom = lastPose;
        shot = next;
        shotStartedAt = now;
    }

    private Pose shotPose(Shot shot, long now) {
        float seconds = (now - shotStartedAt) / 1000f;
        Vec3 head = anchor;
        return switch (shot) {
            case ORBIT -> lookAt(around(shotAngle + seconds * 8, 5, 1.2), head);
            // Just above the ground (the anchor is at eye height), so no block fills the picture.
            case LOW_ANGLE -> lookAt(around(shotAngle + seconds * 3, 3.5, -0.8), head.add(0, 1.5, 0));
            case WIDE -> lookAt(around(shotAngle + seconds * 2, 13, 6), head);
            case OVER_SHOULDER -> {
                Vec3 behind = around(anchorYaw + 180 + 25, 2.2, 0.4);
                Vec3 forward = Vec3.directionFromRotation(0, anchorYaw).scale(10);
                yield lookAt(behind, head.add(forward).add(0, 1 + seconds * 0.2, 0));
            }
            case SKYWARD -> {
                // A little behind and below the head, so the camera isn't inside it.
                Vec3 below = around(anchorYaw + 180, 2.4, -0.6);
                yield new Pose(below, anchorYaw + seconds * 4, -35);
            }
        };
    }

    /** The slow half turn round the player that ends the song. */
    private Pose finalePose(LyricsPlayback.Moment moment) {
        long left = moment.track().durationMs() - moment.track().positionAt(System.nanoTime());
        if (finaleStartAngle == null) {
            finaleStartAngle = shotAngle;
            cutTo(Shot.ORBIT, System.currentTimeMillis());
        }
        float done = 1 - Math.max(0, Math.min(1, left / (float) FINALE_MS));
        float eased = done * done * (3 - 2 * done);
        return lookAt(around(finaleStartAngle + 180 * eased, FINALE_RADIUS, 1 + 2 * eased), anchor);
    }

    /** A point round the player's head at the given compass angle, distance and height. */
    private Vec3 around(float angleDegrees, double distance, double height) {
        double radians = Math.toRadians(angleDegrees);
        Vec3 wanted = anchor.add(-Math.sin(radians) * distance, height, Math.cos(radians) * distance);
        return clearOfWalls(wanted);
    }

    /** Pulls the camera in towards the player if it would end up inside a block. */
    private Vec3 clearOfWalls(Vec3 wanted) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return wanted;
        }
        HitResult hit = minecraft.level.clip(new ClipContext(anchor, wanted,
                // OUTLINE rather than VISUAL: vines and the like have no visual shape but block the view.
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        if (hit.getType() == HitResult.Type.MISS) {
            return wanted;
        }
        Vec3 direction = wanted.subtract(anchor).normalize();
        return anchor.add(direction.scale(Math.max(0.5, anchor.distanceTo(hit.getLocation()) - WALL_MARGIN)));
    }

    private static Pose lookAt(Vec3 from, Vec3 target) {
        Vec3 d = target.subtract(from);
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, horizontal));
        return new Pose(from, yaw, pitch);
    }

    /**
     * Where lyrics go while the showcase runs: just above the player's head, so they're in
     * every shot. Null when the showcase isn't running.
     */
    public Vec3 lyricsSpot() {
        return active && anchor != null ? anchor.add(0, LYRICS_ABOVE_HEAD, 0) : null;
    }

    /** What the camera mixin needs: where to put the camera and where to point it. */
    public record CameraPose(Vec3 position, float yaw, float pitch) {
    }
}
