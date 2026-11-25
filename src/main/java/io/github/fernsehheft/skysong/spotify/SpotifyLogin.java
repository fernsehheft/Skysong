package io.github.fernsehheft.skysong.spotify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.fernsehheft.skysong.Skysong;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Logging in happens in the browser. Spotify then sends the browser back to a tiny web
 * server we run on this computer, which hands us the code to finish the login.
 */
public final class SpotifyLogin {
    public static final int PORT = 18652;
    static final String CALLBACK_PATH = "/callback";
    /** Give up waiting for the browser after this long. */
    private static final long TIMEOUT_MINUTES = 5;

    private final SpotifyApi api;
    private HttpServer server;
    private CompletableFuture<Void> result;

    public SpotifyLogin(SpotifyApi api) {
        this.api = api;
    }

    /** A login that is running, with the page the player has to open in their browser. */
    public record Started(String url, CompletableFuture<Void> done) {
    }

    /** Starts listening for Spotify's answer. Any login that's still waiting is cancelled. */
    public synchronized Started start() throws IOException {
        cancel();
        Pkce pkce = Pkce.create();
        String state = Pkce.randomState();
        CompletableFuture<Void> done = new CompletableFuture<>();

        // Only reachable from this computer.
        HttpServer callback = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 0);
        callback.createContext(CALLBACK_PATH, exchange -> handleCallback(exchange, state, pkce, done));
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Skysong Spotify login");
            thread.setDaemon(true);
            return thread;
        });
        callback.setExecutor(executor);
        callback.start();
        executor.schedule(() -> done.completeExceptionally(new IOException("The login took too long")),
                TIMEOUT_MINUTES, TimeUnit.MINUTES);
        done.whenComplete((ignored, error) -> {
            // Let the browser receive its page before the server goes away.
            callback.stop(1);
            executor.shutdown();
        });

        server = callback;
        result = done;
        return new Started(api.authorizeUrl(pkce, state), done);
    }

    public synchronized void cancel() {
        if (result != null && !result.isDone()) {
            result.cancel(false);
        }
        server = null;
        result = null;
    }

    private void handleCallback(HttpExchange exchange, String state, Pkce pkce, CompletableFuture<Void> done)
            throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String page;
        if (!state.equals(query.get("state"))) {
            // Not the answer to our login: ignore it rather than failing the real one.
            respond(exchange, 400, "This link doesn't belong to the login you started in Minecraft.");
            return;
        }
        if (query.containsKey("error")) {
            String error = query.get("error");
            page = "access_denied".equals(error)
                    ? "You didn't allow Skysong to see your music. You can close this tab."
                    : "Spotify said: " + error;
            respond(exchange, 200, page);
            done.completeExceptionally(new SpotifyException(SpotifyException.Reason.LOGIN_REJECTED,
                    "access_denied".equals(error) ? "The login was cancelled" : "Spotify said: " + error));
            return;
        }
        try {
            api.finishLogin(query.get("code"), pkce);
            respond(exchange, 200, "Skysong is now connected to Spotify. You can close this tab and go back to Minecraft.");
            Skysong.LOGGER.info("Logged in to Spotify");
            done.complete(null);
        } catch (IOException e) {
            respond(exchange, 200, "That didn't work: " + e.getMessage());
            done.completeExceptionally(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String message) throws IOException {
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Skysong</title></head>"
                + "<body style=\"font-family:sans-serif;background:#14141c;color:#eee;text-align:center;padding-top:15vh\">"
                + "<h1 style=\"color:#ffd966\">Skysong</h1><p>" + escape(message) + "</p></body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                values.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
