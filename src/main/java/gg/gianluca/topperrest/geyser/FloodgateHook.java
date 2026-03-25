package gg.gianluca.topperrest.geyser;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Optional integration with GeyserMC Floodgate for Bedrock player support.
 * Only instantiated when the floodgate plugin is loaded.
 */
public class FloodgateHook {

    private final FloodgateApi api;

    public FloodgateHook() {
        this.api = FloodgateApi.getInstance();
    }

    /**
     * Returns true if the given Java UUID belongs to a Bedrock player currently online.
     */
    public boolean isBedrockPlayer(UUID javaUuid) {
        return api.isFloodgatePlayer(javaUuid);
    }

    /**
     * Attempt to resolve a Bedrock username to their Java UUID (works only while online).
     */
    public Optional<UUID> getUuidByBedrockName(String bedrockName) {
        for (FloodgatePlayer player : api.getPlayers()) {
            if (player.getUsername().equalsIgnoreCase(bedrockName)) {
                return Optional.of(player.getJavaUniqueId());
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the Bedrock username for the given Java UUID if online, else empty.
     */
    public Optional<String> getBedrockName(UUID javaUuid) {
        FloodgatePlayer player = api.getPlayer(javaUuid);
        if (player == null) return Optional.empty();
        return Optional.of(player.getUsername());
    }
}
