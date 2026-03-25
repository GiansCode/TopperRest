package gg.gianluca.topperrest.rest.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import gg.gianluca.topperrest.config.RestConfig;
import gg.gianluca.topperrest.rest.RateLimiter;
import gg.gianluca.topperrest.rest.util.HttpUtils;
import gg.gianluca.topperrest.service.TopperService;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base handler that enforces authentication and rate limiting before delegating
 * to the concrete implementation.
 */
public abstract class BaseHandler implements HttpHandler {

    protected final TopperService topperService;
    protected final RestConfig config;
    protected final RateLimiter rateLimiter;
    protected final Logger logger;

    protected BaseHandler(TopperService topperService, RestConfig config,
                          RateLimiter rateLimiter, Logger logger) {
        this.topperService = topperService;
        this.config = config;
        this.rateLimiter = rateLimiter;
        this.logger = logger;
    }

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try {
            // CORS pre-flight
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtils.handleOptions(exchange);
                return;
            }

            // Rate limiting
            if (config.isRateLimitEnabled()) {
                String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
                if (!rateLimiter.tryAcquire(ip)) {
                    HttpUtils.sendError(exchange, 429, "Too many requests");
                    return;
                }
            }

            // API key authentication
            if (config.hasApiKey()) {
                String providedKey = exchange.getRequestHeaders().getFirst("X-API-Key");
                if (!config.getApiKey().equals(providedKey)) {
                    HttpUtils.sendError(exchange, 401, "Unauthorized");
                    return;
                }
            }

            handleRequest(exchange);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling REST request: " + exchange.getRequestURI(), e);
            try {
                HttpUtils.sendError(exchange, 500, "Internal server error");
            } catch (IOException ignored) {}
        } finally {
            exchange.close();
        }
    }

    protected abstract void handleRequest(HttpExchange exchange) throws IOException;
}
