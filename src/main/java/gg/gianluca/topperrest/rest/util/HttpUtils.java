package gg.gianluca.topperrest.rest.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class HttpUtils {

    public static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    private HttpUtils() {}

    public static void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        sendJson(exchange, statusCode, obj);
    }

    public static void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, 405, "Method not allowed");
    }

    public static void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-API-Key");
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    /**
     * Parses query parameters from the URI query string.
     * e.g. "page=2&size=10" → {"page": "2", "size": "10"}
     */
    public static Map<String, String> parseQueryParams(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isEmpty()) return Collections.emptyMap();

        Map<String, String> params = new HashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                params.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else {
                params.put(pair, "");
            }
        }
        return params;
    }

    /**
     * Reads the full request body as a UTF-8 string.
     */
    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Extracts the path segments after a given prefix.
     * e.g. prefix="/api/v1/tops", path="/api/v1/tops/myboard/3" → ["myboard", "3"]
     */
    public static String[] pathSegmentsAfter(HttpExchange exchange, String prefix) {
        String path = exchange.getRequestURI().getPath();
        if (path.equals(prefix) || path.equals(prefix + "/")) return new String[0];
        String rest = path.substring(prefix.length()).replaceAll("^/+|/+$", "");
        if (rest.isEmpty()) return new String[0];
        return rest.split("/");
    }

    public static int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
