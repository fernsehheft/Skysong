package io.github.fernsehheft.skysong.config;

/** Where Skysong finds out what's playing. */
public enum MusicSource {
    /** Spotify when logged in, otherwise the system's music player. */
    AUTO,
    SPOTIFY,
    /** The music player of the operating system, whatever app it is. */
    SYSTEM
}
