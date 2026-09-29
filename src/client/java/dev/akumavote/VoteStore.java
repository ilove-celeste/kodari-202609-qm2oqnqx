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
                AkumaVoteClient.reportError("Не удалось прочитать конфигурацию.", e);
            }
        }
        resetPending = resetDay();
    }

    public synchronized String nickname() {
        return data.getProperty("nickname", "");
    }

    public synchronized int delay() {
        try {
            return Math.clamp(Integer.parseInt(data.getProperty("delay", "1")), 1, 3600);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public synchronized boolean debug() {
        return Boolean.parseBoolean(data.getProperty("debug", "true"));
    }

    public synchronized int browserZoom() {
        try {
            return Math.clamp(Integer.parseInt(data.getProperty("browserZoom", "100")), 50, 200);
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    public synchronized void saveSettings(String nickname, int delay, boolean debug, int browserZoom) {
        data.setProperty("nickname", nickname);
        data.setProperty("delay", Integer.toString(Math.clamp(delay, 1, 3600)));
        data.setProperty("debug", Boolean.toString(debug));
        data.setProperty("browserZoom", Integer.toString(Math.clamp(browserZoom, 50, 200)));
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
            AkumaVoteClient.logStoreDebug("Конфигурация сохранена.");
        } catch (IOException e) {
            AkumaVoteClient.reportError("Не удалось сохранить конфигурацию.", e);
        }
    }
}