package io.github.fernsehheft.skysong.lyrics;

import io.github.fernsehheft.skysong.Skysong;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A folder where players can put their own lyrics files. They win over anything we
 * would download, which makes it easy to fix wrong or badly timed lyrics.
 */
public final class LocalLyricsFolder {
    private static final String README = """
            Your own lyrics for Skysong
            ===========================

            Lyrics files in this folder are used instead of the ones from the internet.

            Name the file after the song: "Artist - Title.lrc", or just "Title.lrc".
            Alternatively put these two lines at the top of the file:
                [ar:Artist]
                [ti:Title]

            .lrc files have a timestamp in front of every line:
                [01:23.45]A line of the song
            You can even time every word:
                [01:23.45]<01:23.45>A <01:23.90>line <01:24.30>of ...
            [offset:+300] at the top makes every line show up 0.3 seconds earlier.

            .txt files are plain lyrics, Skysong spreads them over the song by itself.
            """;
    private static final Pattern TAG = Pattern.compile("(?im)^\\s*\\[(ar|ti):([^\\]]*)]");

    private final Path directory;

    public LocalLyricsFolder(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    /** Creates the folder with a short explanation the first time. */
    public void prepare() {
        try {
            Files.createDirectories(directory);
            Path readme = directory.resolve("README.txt");
            if (Files.notExists(readme)) {
                Files.writeString(readme, README, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Skysong.LOGGER.warn("Couldn't create the lyrics folder {}", directory, e);
        }
    }

    public Optional<LyricsText> find(TrackQuery query) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        String title = TrackQuery.simplify(query.cleanTitle());
        String artist = TrackQuery.simplify(query.mainArtist());
        String fullName = TrackQuery.simplify(query.artist() + query.title());
        String cleanName = artist + title;

        List<Path> files;
        try (Stream<Path> listing = Files.list(directory)) {
            files = listing.filter(LocalLyricsFolder::isLyricsFile).sorted().toList();
        }

        // Matching file names are best, so check those before opening any file.
        for (Path file : files) {
            String name = TrackQuery.simplify(baseName(file));
            if (name.equals(fullName) || name.equals(cleanName)) {
                return Optional.of(read(file));
            }
        }
        for (Path file : files) {
            if (TrackQuery.simplify(baseName(file)).equals(title)) {
                return Optional.of(read(file));
            }
        }
        for (Path file : files) {
            if (isLrc(file)) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (tagsMatch(content, artist, title)) {
                    return Optional.of(LyricsText.synced(content));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean tagsMatch(String content, String artist, String title) {
        String taggedArtist = null;
        String taggedTitle = null;
        Matcher matcher = TAG.matcher(content);
        while (matcher.find()) {
            String value = TrackQuery.simplify(matcher.group(2));
            if (matcher.group(1).equalsIgnoreCase("ar")) {
                taggedArtist = value;
            } else {
                taggedTitle = value;
            }
        }
        return title.equals(taggedTitle) && (taggedArtist == null || taggedArtist.equals(artist));
    }

    private static LyricsText read(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return isLrc(file) ? LyricsText.synced(content) : LyricsText.plain(content);
    }

    private static boolean isLyricsFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(file) && (name.endsWith(".lrc") || name.endsWith(".txt")) && !name.equals("readme.txt");
    }

    private static boolean isLrc(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lrc");
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.lastIndexOf('.'));
    }
}
