package dev.akumavote;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

public final class AkumaVoteClient implements ClientModInitializer {
    public static final Logger LOGGER = createLogger();
    private static AkumaVoteClient instance;
    private final VoteStore store = new VoteStore();
    private final Map<Integer, VoteStatus> voteStatuses = new HashMap<>();
    private volatile boolean connected;

    public enum VoteStatus {
        NOT_VOTED,
        IN_PROGRESS,
        CONFIRMED,
        UNAVAILABLE
    }

    public static AkumaVoteClient instance() {
        return instance;
    }

    public VoteStore store() {
        return store;
    }

    public boolean isConnected() {
        return connected;
    }

    public VoteStatus voteStatus(int index) {
        VoteStatus status = voteStatuses.get(index);
        if (status != null) {
            return status;
        }
        return store.isVoted(index) ? VoteStatus.CONFIRMED : VoteStatus.NOT_VOTED;
    }

    public void setVoteStatus(int index, VoteStatus status) {
        voteStatuses.put(index, status);
        if (status == VoteStatus.CONFIRMED) {
            store.setVoted(index, true);
        } else if (status == VoteStatus.NOT_VOTED) {
            store.setVoted(index, false);
        }
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("AkumaVote loaded. Use /akumavote on play.akumamc.net.");
        LOGGER.info("Runtime: Minecraft=" + clientVersion()
                + ", Java=" + System.getProperty("java.version")
                + ", OS=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + ", Arch=" + System.getProperty("os.arch"));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            connected = isTargetServer(client.getCurrentServerEntry());
            ServerInfo server = client.getCurrentServerEntry();
            LOGGER.info("[AkumaVote-Debug] JOIN server=" + (server == null ? "null" : server.address)
                    + ", target=" + connected);
            if (connected && !store.hasNotified()) {
                client.execute(() -> {
                    if (connected && client.player != null && !store.hasNotified()) {
                        client.player.sendMessage(Text.literal("[AkumaVote] Mod loaded. Use /akumavote to vote."), false);
                        store.markNotified();
                    }
                });
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            connected = false;
            if (client.currentScreen instanceof VoteScreen || client.currentScreen instanceof VoteBrowserScreen) {
                client.setScreen(null);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (store.checkDailyReset()) {
                voteStatuses.clear();
                debug("Vote statuses reset for the new day.");
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("akumavote")
                        .executes(context -> {
                            MinecraftClient client = context.getSource().getClient();
                            if (!connected || client.getCurrentServerEntry() == null) {
                                context.getSource().sendError(Text.literal("Voting is available only on play.akumamc.net."));
                                return 0;
                            }
                            client.execute(() -> client.setScreen(new VoteScreen(this)));
                            return 1;
                        })));
    }

    public void openVoteSite(int index) {
        if (!connected || index < 0 || index >= VoteSite.ALL.size()) {
            return;
        }

        // The Vote button itself is the completion point for local status tracking.
        // The browser remains open so the user can finish the site's interaction.
        setVoteStatus(index, VoteStatus.CONFIRMED);

        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new VoteBrowserScreen(this, index, client.currentScreen));
    }

    public void resetAllStatuses() {
        store.resetVotes();
        voteStatuses.clear();
        debug("All vote statuses reset manually.");
    }

    static void logStoreDebug(String message) {
        if (instance != null) {
            instance.debug(message);
        }
    }

    static void reportError(String message, Throwable error) {
        LOGGER.log(Level.SEVERE, "[AkumaVote-Debug] " + message, error);
        if (instance != null) {
            instance.debug(message + " " + error);
        }
    }

    private String clientVersion() {
        String version = MinecraftClient.getInstance().getGameVersion();
        return version == null ? "unknown" : version;
    }

    private boolean isTargetServer(ServerInfo server) {
        return server != null && (server.address.equalsIgnoreCase("play.akumamc.net")
                || server.address.equalsIgnoreCase("play.akumamc.net:25565"));
    }

    private void debug(String message) {
        if (!store.debug()) {
            return;
        }
        LOGGER.info("[AkumaVote-Debug] " + message);
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (connected && client.player != null) {
                client.player.sendMessage(Text.literal("[AkumaVote-Debug] " + message), false);
            }
        });
    }

    private static Logger createLogger() {
        Logger logger = Logger.getLogger("AkumaVote");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        try {
            Path logFile = Path.of("logs", "akumavote.log");
            Files.createDirectories(logFile.getParent());
            FileHandler handler = new FileHandler(logFile.toString(), true);
            handler.setLevel(Level.ALL);
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
        } catch (IOException | SecurityException e) {
            logger.setUseParentHandlers(true);
            logger.log(Level.SEVERE, "Cannot initialize logs/akumavote.log", e);
        }
        return logger;
    }
}