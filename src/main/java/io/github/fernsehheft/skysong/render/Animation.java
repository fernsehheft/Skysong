package io.github.fernsehheft.skysong.render;

/** How a line of lyrics comes in and plays. */
public enum Animation {
    /** The whole line is there and the words light up as they're sung. */
    KARAOKE,
    /** Every word fades in at the moment it's sung. */
    FADE,
    /** The line rises into place and floats up when the next one comes. */
    RISE,
    /** The words gently bob up and down, one after the other. */
    WAVE,
    /** The words are typed out letter by letter. */
    TYPEWRITER,
    /** Every word flickers in as scrambled characters before it settles. */
    GLITCH,
    /** Every word drops in from above and bounces into place. */
    DROP,
    /** Every word starts big and shrinks into place. */
    ZOOM
}
