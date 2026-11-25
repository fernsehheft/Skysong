package io.github.fernsehheft.skysong.spotify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The "proof key" for logging in without a client secret (RFC 7636). We make up a random
 * verifier, send its hash with the login and the verifier itself when trading the code
 * for tokens, which proves both requests came from us.
 */
record Pkce(String verifier, String challenge) {
    private static final SecureRandom RANDOM = new SecureRandom();

    static Pkce create() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return forVerifier(base64Url(bytes));
    }

    static Pkce forVerifier(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return new Pkce(verifier, base64Url(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }

    /** A random value to tie the login page's answer to the login we started. */
    static String randomState() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return base64Url(bytes);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
