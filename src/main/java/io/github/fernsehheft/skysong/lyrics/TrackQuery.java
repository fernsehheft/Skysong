package io.github.fernsehheft.skysong.lyrics;

import io.github.fernsehheft.skysong.media.NowPlaying;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What we search lyrics for. Players often report titles like
 * "Song (feat. Someone) - 2011 Remaster" or "Song [Official Video]", which lyrics
 * databases don't know, so this also offers cleaned up versions.
 */
public record TrackQuery(String title, String artist, String album, long durationMs) {
    /** Words that mark a different version of a song: a remix, a sped up cut and so on. */
    private static final String VERSION_WORDS = "remix|rmx|mix|edit|version|bootleg|flip|cover|vip|extended"
            + "|sped up|speed up|spedup|slowed|reverb|nightcore|daycore|techno|hardtekk|tekk|hardstyle|phonk"
            + "|bass ?boost(ed)?|8d|acoustic";
    /** Versions that are only faster or slower, so their timing is the original's, stretched. */
    private static final Pattern SPEED_CHANGE = Pattern.compile(
            "\\b(sped up|speed up|spedup|slowed|nightcore|daycore)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTERNATE_VERSION = Pattern.compile(
            "\\b(" + VERSION_WORDS + ")\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOISE_IN_BRACKETS = Pattern.compile(
            "\\s*[(\\[][^)\\]]*\\b(feat|ft|with|prod|remaster(ed)?|official|lyrics?|audio|video|visuali[sz]er"
                    + "|explicit|clean|radio edit|mv|hd|4k|" + VERSION_WORDS + ")\\b[^)\\]]*[)\\]]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_SUFFIX = Pattern.compile(
            "\\s+-\\s+.*\\b(remaster(ed)?|live|radio edit|single version|album version|mono|stereo|"
                    + VERSION_WORDS + ")\\b.*$",
            Pattern.CASE_INSENSITIVE);
    /**
     * Version words at the very end of a title without brackets or a dash, as in
     * "Shape of You Hardtekk". Only words that are hardly ever part of a song's real name.
     */
    private static final Pattern TRAILING_VERSION = Pattern.compile(
            "(\\s+(remix|rmx|bootleg|sped up|speed up|spedup|slowed|reverb|nightcore|daycore|hard ?techno|techno"
                    + "|hard ?tekk|tekk|hardstyle|phonk|bass ?boosted|extended mix|club mix|radio mix|vip mix))+\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FEATURING = Pattern.compile(
            "\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANNEL_SUFFIX = Pattern.compile(
            "(\\s+-\\s+topic|vevo|\\s+official)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIST_SEPARATOR = Pattern.compile(
            "\\s*(,|&|;|/|\\s+x\\s+|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+)\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    public static TrackQuery of(NowPlaying track) {
        return new TrackQuery(track.title(), track.artist(), track.album(), track.durationMs());
    }

    public String cleanTitle() {
        String title = NOISE_IN_BRACKETS.matcher(this.title).replaceAll("");
        title = VERSION_SUFFIX.matcher(title).replaceAll("");
        title = TRAILING_VERSION.matcher(title).replaceAll("");
        title = FEATURING.matcher(title).replaceAll("");
        title = title.replaceAll("\\s{2,}", " ").strip();
        return title.isEmpty() ? this.title.strip() : title;
    }

    /** The title says it's a remix, a sped up version or similar, not the original song. */
    public boolean isAlternateVersion() {
        return ALTERNATE_VERSION.matcher(title).find();
    }

    /** The title says it's the original, only played faster or slower. */
    public boolean isSpeedChange() {
        return SPEED_CHANGE.matcher(title).find();
    }

    /** The first artist only, without YouTube channel decorations like "VEVO". */
    public String mainArtist() {
        String artist = CHANNEL_SUFFIX.matcher(this.artist.strip()).replaceAll("");
        String first = ARTIST_SEPARATOR.split(artist, 2)[0].strip();
        return first.isEmpty() ? artist : first;
    }

    /** Lower case letters and digits only, for comparing names that are written slightly differently. */
    public static String simplify(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return NOT_LETTER_OR_DIGIT.matcher(decomposed.toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
