package io.github.fernsehheft.skysong.config;

/** Where the colour of the words that aren't being sung comes from. */
public enum ColorMode {
    /** The text colour. */
    SINGLE,
    /** A gradient between two colours that slowly flows through the line. */
    GRADIENT,
    /** All colours, drifting along the line. */
    RAINBOW
}
