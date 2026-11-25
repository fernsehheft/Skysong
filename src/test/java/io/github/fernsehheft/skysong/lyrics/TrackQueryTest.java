package io.github.fernsehheft.skysong.lyrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackQueryTest {

    private static TrackQuery query(String title, String artist) {
        return new TrackQuery(title, artist, "", 0);
    }

    @Test
    void cleansTitles() {
        assertEquals("Snowman", query("Snowman (feat. Someone)", "").cleanTitle());
        assertEquals("Here Comes the Sun", query("Here Comes the Sun - Remastered 2009", "").cleanTitle());
        assertEquals("Song", query("Song [Official Music Video]", "").cleanTitle());
        assertEquals("Song", query("Song ft. Other Artist", "").cleanTitle());
        assertEquals("Song (Interlude)", query("Song (Interlude)", "").cleanTitle());
    }

    @Test
    void keepsTitlesThatWouldBecomeEmpty() {
        assertEquals("(Official Video)", query("(Official Video)", "").cleanTitle());
    }

    @Test
    void findsTheMainArtist() {
        assertEquals("Sia", query("", "Sia, David Guetta").mainArtist());
        assertEquals("Simon", query("", "Simon & Garfunkel").mainArtist());
        assertEquals("Rick Astley", query("", "Rick Astley - Topic").mainArtist());
        assertEquals("RickAstley", query("", "RickAstleyVEVO").mainArtist());
    }

    @Test
    void simplifies() {
        assertEquals("beyonce", TrackQuery.simplify("Beyoncé"));
        assertEquals("acdchighwaytohell", TrackQuery.simplify("AC/DC - Highway to Hell"));
        assertEquals("снеговик", TrackQuery.simplify("Снеговик!"));
    }

    @Test
    void recognisesAndStripsRemixes() {
        TrackQuery techno = query("Shape of You (Hard Techno)", "Someone");
        assertEquals("Shape of You", techno.cleanTitle());
        assertTrue(techno.isAlternateVersion());
        assertFalse(techno.isSpeedChange());

        assertEquals("Shape Of You", query("Shape Of You - Hardtekk Remix", "").cleanTitle());
        assertEquals("Shape of You", query("Shape of You [Techno Version]", "").cleanTitle());

        TrackQuery spedUp = query("Shape of You (Sped Up)", "");
        assertEquals("Shape of You", spedUp.cleanTitle());
        assertTrue(spedUp.isSpeedChange());
    }

    @Test
    void ordinaryTitlesAreLeftAlone() {
        assertFalse(query("Shape of You", "Ed Sheeran").isAlternateVersion());
        // Only cut when it's clearly a note about the version, not part of the name.
        assertEquals("Mix Tape", query("Mix Tape", "").cleanTitle());
    }
}
