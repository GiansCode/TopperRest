package gg.gianluca.topperrest.rest;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import gg.gianluca.topperrest.TopperRest;
import gg.gianluca.topperrest.config.RestConfig;
import gg.gianluca.topperrest.rest.handler.BulkHandler;
import gg.gianluca.topperrest.rest.handler.PlayerHandler;
import gg.gianluca.topperrest.rest.handler.TopHandler;
import gg.gianluca.topperrest.rest.util.HttpUtils;
import gg.gianluca.topperrest.service.TopperService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Manages the embedded JDK HTTP server lifecycle.
 * Uses a fixed thread pool for request handling and a single-threaded scheduler
 * for periodic cache eviction.
 */
public class RestServer {

    private final TopperRest plugin;
    private final TopperService topperService;
    private final RestConfig config;

    private HttpServer httpServer;
    private ScheduledExecutorService scheduler;
    private RateLimiter rateLimiter;

    public RestServer(TopperRest plugin, TopperService topperService, RestConfig config) {
        this.plugin = plugin;
        this.topperService = topperService;
        this.config = config;
    }

    public void start() throws IOException {
        InetSocketAddress address = new InetSocketAddress(config.getBindAddress(), config.getPort());
        httpServer = HttpServer.create(address, config.getMaxConnections());

        rateLimiter = new RateLimiter(config.getRateLimitMaxRequests(), config.getRateLimitWindowSeconds());

        TopHandler topHandler = new TopHandler(topperService, config, rateLimiter, plugin.getLogger());
        PlayerHandler playerHandler = new PlayerHandler(topperService, config, rateLimiter, plugin.getLogger());
        BulkHandler bulkHandler = new BulkHandler(topperService, config, rateLimiter, plugin.getLogger());

        httpServer.createContext("/api/v1/tops", topHandler);
        httpServer.createContext("/api/v1/player", playerHandler);
        httpServer.createContext("/api/v1/bulk", bulkHandler);
        httpServer.createContext("/api/v1/health", this::handleHealth);

        // Thread pool for handling requests (separate from Bukkit's main thread)
        httpServer.setExecutor(Executors.newFixedThreadPool(config.getThreads(), r -> {
            Thread t = new Thread(r, "TopperRest-worker");
            t.setDaemon(true);
            return t;
        }));

        httpServer.start();

        // Periodic maintenance: evict expired cache and rate-limit entries
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TopperRest-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            topperService.evictExpiredCache();
            rateLimiter.evictExpired();
        }, 60, 60, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (httpServer != null) {
            // 0 = stop immediately
            httpServer.stop(0);
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtils.handleOptions(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtils.sendMethodNotAllowed(exchange);
                return;
            }
            JsonObject health = new JsonObject();
            health.addProperty("status", "ok");
            health.addProperty("plugin", "TopperRest");
            health.addProperty("boards", topperService.getBoardNames().size());
            HttpUtils.sendJson(exchange, 200, health);
        } finally {
            exchange.close();
        }
    }
}
