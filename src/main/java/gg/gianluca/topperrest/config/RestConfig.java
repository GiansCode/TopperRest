package gg.gianluca.topperrest.config;

import org.bukkit.configuration.file.FileConfiguration;

public class RestConfig {

    private final int port;
    private final String bindAddress;
    private final String apiKey;
    private final int maxConnections;
    private final int requestTimeoutSeconds;
    private final int threads;
    private final long cacheTtlSeconds;
    private final long playerNameTtlSeconds;
    private final boolean rateLimitEnabled;
    private final int rateLimitMaxRequests;
    private final int rateLimitWindowSeconds;
    private final String bedrockPrefix;

    public RestConfig(FileConfiguration config) {
        this.port = config.getInt("rest.port", 4567);
        this.bindAddress = config.getString("rest.bind-address", "0.0.0.0");
        this.apiKey = config.getString("rest.api-key", "");
        this.maxConnections = config.getInt("rest.max-connections", 50);
        this.requestTimeoutSeconds = config.getInt("rest.request-timeout", 10);
        this.threads = config.getInt("rest.threads", 4);
        this.cacheTtlSeconds = config.getLong("cache.ttl-seconds", 5);
        this.playerNameTtlSeconds = config.getLong("cache.player-name-ttl-seconds", 300);
        this.rateLimitEnabled = config.getBoolean("rate-limit.enabled", true);
        this.rateLimitMaxRequests = config.getInt("rate-limit.max-requests", 100);
        this.rateLimitWindowSeconds = config.getInt("rate-limit.window-seconds", 60);
        this.bedrockPrefix = config.getString("geyser.bedrock-prefix", "~");
    }

    public int getPort() { return port; }
    public String getBindAddress() { return bindAddress; }
    public String getApiKey() { return apiKey; }
    public boolean hasApiKey() { return apiKey != null && !apiKey.isEmpty(); }
    public int getMaxConnections() { return maxConnections; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public int getThreads() { return threads; }
    public long getCacheTtlSeconds() { return cacheTtlSeconds; }
    public long getPlayerNameTtlSeconds() { return playerNameTtlSeconds; }
    public boolean isRateLimitEnabled() { return rateLimitEnabled; }
    public int getRateLimitMaxRequests() { return rateLimitMaxRequests; }
    public int getRateLimitWindowSeconds() { return rateLimitWindowSeconds; }
    public String getBedrockPrefix() { return bedrockPrefix; }
}
