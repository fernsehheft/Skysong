package io.github.fernsehheft.skysong.spotify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A stand-in for Spotify's servers that answers with whatever the test queued up. */
final class FakeSpotify implements AutoCloseable {
    record Answer(int status, String body, String retryAfter) {
        static Answer json(int status, String body) {
            return new Answer(status, body, null);
        }
    }

    record Request(String method, String path, String authorization, String body) {
    }

    private final HttpServer server;
    final Deque<Answer> tokenAnswers = new ArrayDeque<>();
    final Deque<Answer> apiAnswers = new ArrayDeque<>();
    final List<Request> requests = new ArrayList<>();

    FakeSpotify() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/accounts", exchange -> answer(exchange, tokenAnswers));
        server.createContext("/api", exchange -> answer(exchange, apiAnswers));
        server.start();
    }

    String accounts() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/accounts";
    }

    String api() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    private void answer(HttpExchange exchange, Deque<Answer> answers) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        synchronized (this) {
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("Authorization"), body));
        }
        Answer answer = answers.isEmpty() ? Answer.json(500, "{\"error\":\"nothing queued\"}") : answers.poll();
        if (answer.retryAfter() != null) {
            exchange.getResponseHeaders().set("Retry-After", answer.retryAfter());
        }
        byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answer.status(), answer.status() == 204 ? -1 : bytes.length);
        if (answer.status() != 204) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
