package gg.gianluca.topperrest.cache;

import gg.gianluca.topperrest.model.PlayerBoardInfo;
import gg.gianluca.topperrest.model.TopEntry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe TTL cache for Topper data.
 * Snapshot caches are invalidated on board update events; player name caches use a longer TTL.
 */
public class TopperCache {

    private final long snapshotTtlMillis;
    private final long playerNameTtlMillis;

    // board name -> sorted snapshot
    private final ConcurrentHashMap<String, CacheEntry<List<TopEntry>>> snapshotCache = new ConcurrentHashMap<>();
    // "uuid:board" -> PlayerBoardInfo
    private final ConcurrentHashMap<String, CacheEntry<PlayerBoardInfo>> playerBoardCache = new ConcurrentHashMap<>();
    // uuid string -> display name
    private final ConcurrentHashMap<String, CacheEntry<String>> playerNameCache = new ConcurrentHashMap<>();

    public TopperCache(long snapshotTtlSeconds, long playerNameTtlSeconds) {
        this.snapshotTtlMillis = snapshotTtlSeconds * 1000L;
        this.playerNameTtlMillis = playerNameTtlSeconds * 1000L;
    }

    // ---- Snapshot cache ----

    public List<TopEntry> getSnapshot(String board) {
        CacheEntry<List<TopEntry>> entry = snapshotCache.get(board);
        if (entry == null || entry.isExpired()) return null;
        return entry.getValue();
    }

    public void putSnapshot(String board, List<TopEntry> entries) {
        snapshotCache.put(board, new CacheEntry<>(entries, snapshotTtlMillis));
    }

    /** Invalidate snapshot for a board (called on entry update events). */
    public void invalidateSnapshot(String board) {
        snapshotCache.remove(board);
    }

    /** Invalidate all snapshot caches. */
    public void invalidateAllSnapshots() {
        snapshotCache.clear();
    }

    // ---- Player board cache ----

    public PlayerBoardInfo getPlayerBoard(UUID uuid, String board) {
        CacheEntry<PlayerBoardInfo> entry = playerBoardCache.get(playerBoardKey(uuid, board));
        if (entry == null || entry.isExpired()) return null;
        return entry.getValue();
    }

    public void putPlayerBoard(UUID uuid, String board, PlayerBoardInfo info) {
        playerBoardCache.put(playerBoardKey(uuid, board), new CacheEntry<>(info, snapshotTtlMillis));
    }

    public void invalidatePlayerBoard(UUID uuid, String board) {
        playerBoardCache.remove(playerBoardKey(uuid, board));
    }

    // ---- Player name cache ----

    public String getPlayerName(UUID uuid) {
        CacheEntry<String> entry = playerNameCache.get(uuid.toString());
        if (entry == null || entry.isExpired()) return null;
        return entry.getValue();
    }

    public void putPlayerName(UUID uuid, String name) {
        if (name != null) {
            playerNameCache.put(uuid.toString(), new CacheEntry<>(name, playerNameTtlMillis));
        }
    }

    // ---- Maintenance ----

    /** Remove expired entries to prevent unbounded memory growth. */
    public void evictExpired() {
        snapshotCache.entrySet().removeIf(e -> e.getValue().isExpired());
        playerBoardCache.entrySet().removeIf(e -> e.getValue().isExpired());
        playerNameCache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private String playerBoardKey(UUID uuid, String board) {
        return uuid.toString() + ':' + board;
    }
}
