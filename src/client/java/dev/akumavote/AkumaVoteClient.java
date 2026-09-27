package dev.akumavote;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class AkumaVoteClient implements ClientModInitializer {
    public static final Logger LOGGER = createLogger();
    private static AkumaVoteClient instance;
    private final VoteStore store = new VoteStore();
    private volatile boolean connected;
    private boolean notifiedThisSession;
    private final VoteService service = new VoteService(store, this::debug, () -> connected);

    public static AkumaVoteClient instance() {
        return instance;
    }

    public VoteStore store() {
        return store;
    }

    public VoteService service() {
        return service;
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("AkumaVote загружен. Используйте /autovote на play.akumamc.net.");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerInfo server = client.getCurrentServerEntry();
            connected = server != null && (server.address.equalsIgnoreCase("play.akumamc.net")
                    || server.address.equalsIgnoreCase("play.akumamc.net:25565"));
            if (connected && !notifiedThisSession) {
                client.execute(() -> {
                    if (connected && client.player != null) {
                        client.player.sendMessage(Text.literal("[AkumaVote] Мод загружен и работает. Используйте /autovote."), false);
                        notifiedThisSession = true;
                    }
                });
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            connected = false;
            service.cancel();
            if (client.currentScreen instanceof VoteScreen) {
                client.setScreen(null);
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("autovote")
                        .executes(context -> {
                            MinecraftClient client = context.getSource().getClient();
                            if (!connected || client.getCurrentServerEntry() == null) {
                                context.getSource().sendError(Text.literal("Голосование доступно только на play.akumamc.net."));
                                return 0;
                            }
                            client.execute(() -> {
                                client.setScreen(new VoteScreen(this));
                            });
                            return 1;
                        })));
    }

    private void debug(String message) {
        LOGGER.info("[AkumaVote-Debug] " + message);
        if (store.debug()) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (connected && client.player != null) {
                    client.player.sendMessage(Text.literal("[AkumaVote-Debug] " + message), false);
                }
            });
        }
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