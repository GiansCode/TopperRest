package gg.gianluca.topperrest.service;

import gg.gianluca.topperrest.TopperRest;
import gg.gianluca.topperrest.cache.TopperCache;
import gg.gianluca.topperrest.geyser.FloodgateHook;
import gg.gianluca.topperrest.model.PlayerBoardInfo;
import gg.gianluca.topperrest.model.PlayerInfo;
import gg.gianluca.topperrest.model.TopBoard;
import gg.gianluca.topperrest.model.TopEntry;
import me.hsgamer.topper.agent.snapshot.SnapshotAgent;
import me.hsgamer.topper.spigot.plugin.TopperPlugin;
import me.hsgamer.topper.spigot.plugin.template.SpigotTopTemplate;
import me.hsgamer.topper.template.topplayernumber.holder.NumberTopHolder;
import me.hsgamer.topper.template.topplayernumber.manager.TopManager;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service layer that reads data from Topper and applies caching.
 * All snapshot reads come from Topper's already-computed snapshots, so
 * they are O(1) list operations — no in-memory sorting is triggered here.
 */
public class TopperService {

    private final TopperPlugin topperPlugin;
    private final TopperRest plugin;
    private final TopperCache cache;
    private final FloodgateHook floodgateHook; // nullable

    public TopperService(TopperPlugin topperPlugin, TopperRest plugin,
                         TopperCache cache, FloodgateHook floodgateHook) {
        this.topperPlugin = topperPlugin;
        this.plugin = plugin;
        this.cache = cache;
        this.floodgateHook = floodgateHook;
    }

    // ---- Board queries ----

    public List<String> getBoardNames() {
        return getTemplate().getTopManager().getHolderNames();
    }

    /**
     * Returns a paginated view of the specified leaderboard.
     *
     * @param boardName name of the holder/board
     * @param page      1-indexed page number
     * @param pageSize  entries per page
     */
    public Optional<TopBoard> getTopBoard(String boardName, int page, int pageSize) {
        TopManager topManager = getTemplate().getTopManager();
        Optional<NumberTopHolder> holderOpt = topManager.getHolder(boardName);
        if (holderOpt.isEmpty()) return Optional.empty();

        NumberTopHolder holder = holderOpt.get();
        List<TopEntry> snapshot = getSnapshot(boardName, holder);

        int total = snapshot.size();
        int fromIndex = Math.min((page - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<TopEntry> page_entries = snapshot.subList(fromIndex, toIndex);
        return Optional.of(new TopBoard(boardName, total, page, pageSize, page_entries));
    }

    /**
     * Returns the entry at the given 1-indexed position on the board, or empty if out of range.
     */
    public Optional<TopEntry> getEntryAtPosition(String boardName, int position) {
        TopManager topManager = getTemplate().getTopManager();
        Optional<NumberTopHolder> holderOpt = topManager.getHolder(boardName);
        if (holderOpt.isEmpty()) return Optional.empty();

        NumberTopHolder holder = holderOpt.get();
        List<TopEntry> snapshot = getSnapshot(boardName, holder);

        if (position < 1 || position > snapshot.size()) return Optional.empty();
        return Optional.of(snapshot.get(position - 1));
    }

    // ---- Player queries ----

    /**
     * Returns the player's rank and value across all boards.
     */
    public Optional<PlayerInfo> getPlayerInfo(UUID uuid) {
        String name = resolvePlayerName(uuid);
        if (name == null) name = uuid.toString();

        boolean bedrock = floodgateHook != null && floodgateHook.isBedrockPlayer(uuid);

        List<PlayerBoardInfo> boards = new ArrayList<>();
        for (String boardName : getBoardNames()) {
            boards.add(getPlayerBoardInfo(uuid, boardName));
        }

        return Optional.of(new PlayerInfo(uuid, name, bedrock, boards));
    }

    /**
     * Returns the player's rank + value for a specific board.
     */
    public Optional<PlayerInfo> getPlayerBoardDetails(UUID uuid, String boardName) {
        TopManager topManager = getTemplate().getTopManager();
        if (topManager.getHolder(boardName).isEmpty()) return Optional.empty();

        String name = resolvePlayerName(uuid);
        if (name == null) name = uuid.toString();

        boolean bedrock = floodgateHook != null && floodgateHook.isBedrockPlayer(uuid);
        List<PlayerBoardInfo> boards = List.of(getPlayerBoardInfo(uuid, boardName));

        return Optional.of(new PlayerInfo(uuid, name, bedrock, boards));
    }

    // ---- Bulk queries ----

    /**
     * Returns snapshots for all requested boards. Boards not found are omitted.
     */
    public Map<String, TopBoard> getBulkBoards(List<String> boardNames, int page, int pageSize) {
        Map<String, TopBoard> result = new LinkedHashMap<>();
        for (String name : boardNames) {
            getTopBoard(name, page, pageSize).ifPresent(b -> result.put(name, b));
        }
        return result;
    }

    /**
     * Returns PlayerInfo for all requested UUIDs.
     */
    public Map<String, PlayerInfo> getBulkPlayers(List<UUID> uuids) {
        Map<String, PlayerInfo> result = new LinkedHashMap<>();
        for (UUID uuid : uuids) {
            getPlayerInfo(uuid).ifPresent(p -> result.put(uuid.toString(), p));
        }
        return result;
    }

    // ---- Cache invalidation (called from event listener) ----

    public void invalidateBoard(String boardName) {
        cache.invalidateSnapshot(boardName);
    }

    public void invalidatePlayerBoard(UUID uuid, String boardName) {
        cache.invalidatePlayerBoard(uuid, boardName);
    }

    public void evictExpiredCache() {
        cache.evictExpired();
    }

    // ---- Identifier resolution ----

    /**
     * Resolves an identifier string (UUID, Java name, or ~BedrockName) to a UUID.
     * Returns empty when the identifier cannot be resolved to a known player.
     */
    public Optional<UUID> resolveIdentifier(String identifier) {
        String bedrockPrefix = plugin.getRestConfig().getBedrockPrefix();

        // Bedrock player lookup
        if (identifier.startsWith(bedrockPrefix) && floodgateHook != null) {
            String bedrockName = identifier.substring(bedrockPrefix.length());
            return floodgateHook.getUuidByBedrockName(bedrockName);
        }

        // UUID string
        try {
            return Optional.of(UUID.fromString(identifier));
        } catch (IllegalArgumentException ignored) {
        }

        // Java username lookup (offline player — works in online and offline mode)
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(identifier);
        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
            return Optional.of(offlinePlayer.getUniqueId());
        }

        return Optional.empty();
    }

    // ---- Private helpers ----

    private SpigotTopTemplate getTemplate() {
        return topperPlugin.get(SpigotTopTemplate.class);
    }

    /**
     * Returns the cached snapshot for a board, rebuilding it if stale.
     * The snapshot from Topper is already sorted descending by value.
     */
    private List<TopEntry> getSnapshot(String boardName, NumberTopHolder holder) {
        List<TopEntry> cached = cache.getSnapshot(boardName);
        if (cached != null) return cached;

        SnapshotAgent<UUID, Double> snapshotAgent = holder.getSnapshotAgent();
        List<Map.Entry<UUID, Double>> raw = snapshotAgent.getSnapshot();

        List<TopEntry> entries = new ArrayList<>(raw.size());
        SpigotTopTemplate template = getTemplate();

        for (int i = 0; i < raw.size(); i++) {
            Map.Entry<UUID, Double> entry = raw.get(i);
            UUID uuid = entry.getKey();
            double value = entry.getValue() != null ? entry.getValue() : 0.0;
            String name = resolvePlayerName(uuid, template);
            boolean bedrock = floodgateHook != null && floodgateHook.isBedrockPlayer(uuid);
            entries.add(new TopEntry(i + 1, uuid, name, value, bedrock));
        }

        cache.putSnapshot(boardName, entries);
        return entries;
    }

    private PlayerBoardInfo getPlayerBoardInfo(UUID uuid, String boardName) {
        PlayerBoardInfo cached = cache.getPlayerBoard(uuid, boardName);
        if (cached != null) return cached;

        TopManager topManager = getTemplate().getTopManager();
        Optional<NumberTopHolder> holderOpt = topManager.getHolder(boardName);

        if (holderOpt.isEmpty()) {
            return new PlayerBoardInfo(boardName, -1, null);
        }

        NumberTopHolder holder = holderOpt.get();
        SnapshotAgent<UUID, Double> snapshotAgent = holder.getSnapshotAgent();

        // 0-indexed from Topper; -1 if not present
        int zeroIndex = snapshotAgent.getSnapshotIndex(uuid);
        int rank = zeroIndex >= 0 ? zeroIndex + 1 : -1;

        Double value = holder.getEntry(uuid)
                .map(e -> e.getValue())
                .orElse(null);

        PlayerBoardInfo info = new PlayerBoardInfo(boardName, rank, value);
        cache.putPlayerBoard(uuid, boardName, info);
        return info;
    }

    private String resolvePlayerName(UUID uuid) {
        return resolvePlayerName(uuid, getTemplate());
    }

    private String resolvePlayerName(UUID uuid, SpigotTopTemplate template) {
        String cached = cache.getPlayerName(uuid);
        if (cached != null) return cached;

        String name = template.getName(uuid);
        if (name == null || name.isBlank()) {
            // Fallback: Bukkit offline player lookup
            name = Bukkit.getOfflinePlayer(uuid).getName();
        }
        cache.putPlayerName(uuid, name);
        return name;
    }
}
