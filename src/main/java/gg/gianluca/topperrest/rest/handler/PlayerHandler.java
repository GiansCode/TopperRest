package gg.gianluca.topperrest.rest.handler;

import com.sun.net.httpserver.HttpExchange;
import gg.gianluca.topperrest.config.RestConfig;
import gg.gianluca.topperrest.model.PlayerInfo;
import gg.gianluca.topperrest.rest.RateLimiter;
import gg.gianluca.topperrest.rest.util.HttpUtils;
import gg.gianluca.topperrest.service.TopperService;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles GET requests under /api/v1/player
 *
 * Routes:
 *   GET /api/v1/player/{id}          → player info across all boards
 *   GET /api/v1/player/{id}/{board}  → player info for a specific board
 *
 * {id} can be:
 *   - A UUID string
 *   - A Java player name (resolved via OfflinePlayer)
 *   - ~BedrockName (resolved via Floodgate, if available)
 */
public class PlayerHandler extends BaseHandler {

    private static final String PREFIX = "/api/v1/player";

    public PlayerHandler(TopperService topperService, RestConfig config,
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

        if (segments.length == 0) {
            HttpUtils.sendError(exchange, 400, "Provide a player identifier: /api/v1/player/{uuid|name|~bedrockName}");
            return;
        }

        String identifier = URLDecoder.decode(segments[0], StandardCharsets.UTF_8);
        Optional<UUID> uuidOpt = topperService.resolveIdentifier(identifier);

        if (uuidOpt.isEmpty()) {
            HttpUtils.sendError(exchange, 404, "Player '" + identifier + "' not found");
            return;
        }

        UUID uuid = uuidOpt.get();

        if (segments.length >= 2) {
            String boardName = URLDecoder.decode(segments[1], StandardCharsets.UTF_8);
            Optional<PlayerInfo> infoOpt = topperService.getPlayerBoardDetails(uuid, boardName);
            if (infoOpt.isEmpty()) {
                HttpUtils.sendError(exchange, 404, "Board '" + boardName + "' not found");
                return;
            }
            HttpUtils.sendJson(exchange, 200, infoOpt.get());
        } else {
            Optional<PlayerInfo> infoOpt = topperService.getPlayerInfo(uuid);
            infoOpt.ifPresentOrElse(
                    info -> {
                        try { HttpUtils.sendJson(exchange, 200, info); }
                        catch (IOException e) { throw new RuntimeException(e); }
                    },
                    () -> {
                        try { HttpUtils.sendError(exchange, 404, "Player not found"); }
                        catch (IOException e) { throw new RuntimeException(e); }
                    }
            );
        }
    }
}
