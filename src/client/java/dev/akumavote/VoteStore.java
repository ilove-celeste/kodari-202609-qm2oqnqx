package dev.akumavote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Properties;

public final class VoteStore {
    private final Path file = Path.of("config", "akumavote.properties");
    private final Properties data = new Properties();
    private boolean resetPending;

    public VoteStore() {
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                data.load(input);
            } catch (IOException e) {
                AkumaVoteClient.reportError("Failed to read configuration.", e);
            }
        }
        resetPending = resetDay();
    }

    public synchronized boolean debug() {
        return Boolean.parseBoolean(data.getProperty("debug", "false"));
    }

    public synchronized boolean lightTheme() {
        return Boolean.parseBoolean(data.getProperty("lightTheme", "false"));
    }

    public synchronized void saveSettings(boolean debug, boolean lightTheme) {
        data.setProperty("debug", Boolean.toString(debug));
        data.setProperty("lightTheme", Boolean.toString(lightTheme));
        save();
    }

    public synchronized boolean hasNotified() {
        return Boolean.parseBoolean(data.getProperty("firstConnected", "false"));
    }

    public synchronized void markNotified() {
        data.setProperty("firstConnected", "true");
        save();
    }

    public synchronized boolean isVoted(int index) {
        return Boolean.parseBoolean(data.getProperty("vote." + index, "false"));
    }

    public synchronized void setVoted(int index, boolean voted) {
        data.setProperty("vote." + index, Boolean.toString(voted));
        save();
    }

    public synchronized void resetVotes() {
        for (String key : data.stringPropertyNames()) {
            if (key.startsWith("vote.")) {
                data.remove(key);
            }
        }
        save();
    }

    public synchronized boolean checkDailyReset() {
        if (resetPending) {
            resetPending = false;
            return true;
        }
        return resetDay();
    }

    private boolean resetDay() {
        String today = LocalDate.now().toString();
        if (today.equals(data.getProperty("lastResetDate"))) {
            return false;
        }
        for (String key : data.stringPropertyNames()) {
            if (key.startsWith("vote.")) {
                data.remove(key);
            }
        }
        data.setProperty("lastResetDate", today);
        save();
        return true;
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                data.store(output, null);
            }
            AkumaVoteClient.logStoreDebug("Configuration saved.");
        } catch (IOException e) {
            AkumaVoteClient.reportError("Failed to save configuration.", e);
        }
    }
}
