package gg.gianluca.topperrest.rest.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import gg.gianluca.topperrest.config.RestConfig;
import gg.gianluca.topperrest.model.PlayerInfo;
import gg.gianluca.topperrest.model.TopBoard;
import gg.gianluca.topperrest.rest.RateLimiter;
import gg.gianluca.topperrest.rest.util.HttpUtils;
import gg.gianluca.topperrest.service.TopperService;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles POST requests under /api/v1/bulk
 *
 * Routes:
 *   POST /api/v1/bulk/tops    → fetch multiple boards in one request
 *   POST /api/v1/bulk/players → fetch multiple players in one request
 *
 * Request body for /bulk/tops:
 * {
 *   "boards": ["board1", "board2"],
 *   "page": 1,     // optional, default 1
 *   "size": 10     // optional, default 10
 * }
 *
 * Request body for /bulk/players:
 * {
 *   "players": ["uuid1", "name2", "~bedrockName"]
 * }
 */
public class BulkHandler extends BaseHandler {

    private static final String PREFIX = "/api/v1/bulk";
    private static final int MAX_BULK_SIZE = 50;

    public BulkHandler(TopperService topperService, RestConfig config,
                       RateLimiter rateLimiter, Logger logger) {
        super(topperService, config, rateLimiter, logger);
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtils.sendMethodNotAllowed(exchange);
            return;
        }

        String[] segments = HttpUtils.pathSegmentsAfter(exchange, PREFIX);
        if (segments.length == 0) {
            HttpUtils.sendError(exchange, 400, "Use /api/v1/bulk/tops or /api/v1/bulk/players");
            return;
        }

        String sub = URLDecoder.decode(segments[0], StandardCharsets.UTF_8);
        String body = HttpUtils.readBody(exchange);

        try {
            switch (sub.toLowerCase()) {
                case "tops" -> handleBulkTops(exchange, body);
                case "players" -> handleBulkPlayers(exchange, body);
                default -> HttpUtils.sendError(exchange, 404, "Unknown bulk endpoint: " + sub);
            }
        } catch (JsonParseException e) {
            HttpUtils.sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
        }
    }

    private void handleBulkTops(HttpExchange exchange, String body) throws IOException {
        JsonObject req = HttpUtils.GSON.fromJson(body, JsonObject.class);
        if (req == null || !req.has("boards")) {
            HttpUtils.sendError(exchange, 400, "Request body must contain 'boards' array");
            return;
        }

        JsonArray boardsArray = req.getAsJsonArray("boards");
        if (boardsArray.size() > MAX_BULK_SIZE) {
            HttpUtils.sendError(exchange, 400, "Too many boards requested (max " + MAX_BULK_SIZE + ")");
            return;
        }

        int page = req.has("page") ? req.get("page").getAsInt() : 1;
        int size = req.has("size") ? Math.min(200, req.get("size").getAsInt()) : 10;
        page = Math.max(1, page);
        size = Math.max(1, size);

        List<String> boardNames = new ArrayList<>();
        for (JsonElement el : boardsArray) {
            boardNames.add(el.getAsString());
        }

        Map<String, TopBoard> result = topperService.getBulkBoards(boardNames, page, size);
        HttpUtils.sendJson(exchange, 200, result);
    }

    private void handleBulkPlayers(HttpExchange exchange, String body) throws IOException {
        JsonObject req = HttpUtils.GSON.fromJson(body, JsonObject.class);
        if (req == null || !req.has("players")) {
            HttpUtils.sendError(exchange, 400, "Request body must contain 'players' array");
            return;
        }

        JsonArray playersArray = req.getAsJsonArray("players");
        if (playersArray.size() > MAX_BULK_SIZE) {
            HttpUtils.sendError(exchange, 400, "Too many players requested (max " + MAX_BULK_SIZE + ")");
            return;
        }

        List<UUID> uuids = new ArrayList<>();
        List<String> notFound = new ArrayList<>();

        for (JsonElement el : playersArray) {
            String identifier = el.getAsString();
            Optional<UUID> uuidOpt = topperService.resolveIdentifier(identifier);
            if (uuidOpt.isPresent()) {
                uuids.add(uuidOpt.get());
            } else {
                notFound.add(identifier);
            }
        }

        Map<String, PlayerInfo> players = topperService.getBulkPlayers(uuids);

        JsonObject response = new JsonObject();
        response.add("players", HttpUtils.GSON.toJsonTree(players));
        if (!notFound.isEmpty()) {
            response.add("notFound", HttpUtils.GSON.toJsonTree(notFound));
        }
        HttpUtils.sendJson(exchange, 200, response);
    }
}
