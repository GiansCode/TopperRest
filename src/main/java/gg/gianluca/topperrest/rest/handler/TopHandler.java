package gg.gianluca.topperrest.rest.handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import gg.gianluca.topperrest.config.RestConfig;
import gg.gianluca.topperrest.model.TopBoard;
import gg.gianluca.topperrest.model.TopEntry;
import gg.gianluca.topperrest.rest.RateLimiter;
import gg.gianluca.topperrest.rest.util.HttpUtils;
import gg.gianluca.topperrest.service.TopperService;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Handles GET requests under /api/v1/tops
 *
 * Routes:
 *   GET /api/v1/tops               → list all board names
 *   GET /api/v1/tops/{name}        → paginated board snapshot (?page=1&size=10)
 *   GET /api/v1/tops/{name}/{pos}  → single entry at 1-indexed position
 */
public class TopHandler extends BaseHandler {

    private static final String PREFIX = "/api/v1/tops";

    public TopHandler(TopperService topperService, RestConfig config,
                      RateLimiter rateLimiter, Logger logger) {
        super(topperService, config, rateLimiter, logger);
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtils.sendMethodNotAllowed(exchange);
            return;
        }

        String[] segments = HttpUtils.pathSegmentsAfter(exchange, PREFIX);

        // GET /api/v1/tops
        if (segments.length == 0) {
            handleListBoards(exchange);
            return;
        }

        String boardName = URLDecoder.decode(segments[0], StandardCharsets.UTF_8);

        // GET /api/v1/tops/{name}/{position}
        if (segments.length >= 2) {
            int position = HttpUtils.parseInt(segments[1], -1);
            if (position < 1) {
                HttpUtils.sendError(exchange, 400, "Position must be a positive integer");
                return;
            }
            handleEntryAtPosition(exchange, boardName, position);
            return;
        }

        // GET /api/v1/tops/{name}
        handleBoard(exchange, boardName);
    }

    private void handleListBoards(HttpExchange exchange) throws IOException {
        List<String> names = topperService.getBoardNames();
        JsonObject response = new JsonObject();
        response.add("boards", HttpUtils.GSON.toJsonTree(names));
        response.addProperty("count", names.size());
        HttpUtils.sendJson(exchange, 200, response);
    }

    private void handleBoard(HttpExchange exchange, String boardName) throws IOException {
        Map<String, String> params = HttpUtils.parseQueryParams(exchange);
        int page = Math.max(1, HttpUtils.parseInt(params.get("page"), 1));
        int size = Math.min(200, Math.max(1, HttpUtils.parseInt(params.get("size"), 10)));

        Optional<TopBoard> boardOpt = topperService.getTopBoard(boardName, page, size);
        if (boardOpt.isEmpty()) {
            HttpUtils.sendError(exchange, 404, "Board '" + boardName + "' not found");
            return;
        }
        HttpUtils.sendJson(exchange, 200, boardOpt.get());
    }

    private void handleEntryAtPosition(HttpExchange exchange, String boardName, int position) throws IOException {
        Optional<TopEntry> entryOpt = topperService.getEntryAtPosition(boardName, position);
        if (entryOpt.isEmpty()) {
            HttpUtils.sendError(exchange, 404,
                    "No entry at position " + position + " on board '" + boardName + "'");
            return;
        }
        HttpUtils.sendJson(exchange, 200, entryOpt.get());
    }
}
