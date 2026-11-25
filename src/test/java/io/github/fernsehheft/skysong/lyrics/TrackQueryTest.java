package io.github.fernsehheft.skysong.lyrics;

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
}
