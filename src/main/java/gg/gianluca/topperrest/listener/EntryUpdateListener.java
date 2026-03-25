package gg.gianluca.topperrest.listener;

import gg.gianluca.topperrest.service.TopperService;
import me.hsgamer.topper.spigot.plugin.event.GenericEntryUpdateEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Listens for Topper data changes and invalidates the relevant cache entries,
 * ensuring the REST API always returns data consistent with the latest snapshot.
 */
public class EntryUpdateListener implements Listener {

    private final TopperService topperService;

    public EntryUpdateListener(TopperService topperService) {
        this.topperService = topperService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntryUpdate(GenericEntryUpdateEvent event) {
        String board = event.getHolder();
        UUID uuid = event.getUuid();

        // Invalidate the board snapshot so the next request rebuilds it
        topperService.invalidateBoard(board);
        // Invalidate the per-player cache for this board
        topperService.invalidatePlayerBoard(uuid, board);
    }
}
