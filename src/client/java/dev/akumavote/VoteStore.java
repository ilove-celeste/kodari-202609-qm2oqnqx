package dev.akumavote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Properties;
import java.util.logging.Level;

public final class VoteStore {
    private final Path file = Path.of("config", "akumavote.properties");
    private final Properties data = new Properties();

    public VoteStore() {
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                data.load(input);
            } catch (IOException e) {
                AkumaVoteClient.LOGGER.log(Level.SEVERE, "Cannot read voting configuration", e);
            }
        }
        resetDay();
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

    public synchronized int theme() {
        try {
            return Math.clamp(Integer.parseInt(data.getProperty("theme", "0")), 0, 2);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public synchronized void saveSettings(String nickname, int delay, boolean debug, int theme) {
        data.setProperty("nickname", nickname);
        data.setProperty("delay", Integer.toString(delay));
        data.setProperty("debug", Boolean.toString(debug));
        data.setProperty("theme", Integer.toString(theme));
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
        resetDay();
        return Boolean.parseBoolean(data.getProperty("vote." + index, "false"));
    }

    public synchronized void setVoted(int index, boolean voted) {
        resetDay();
        data.setProperty("vote." + index, Boolean.toString(voted));
        save();
    }

    private void resetDay() {
        String today = LocalDate.now().toString();
        if (!today.equals(data.getProperty("date"))) {
            for (String key : data.stringPropertyNames()) {
                if (key.startsWith("vote.")) {
                    data.remove(key);
                }
            }
            data.setProperty("date", today);
            save();
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                data.store(output, null);
            }
        } catch (IOException e) {
            AkumaVoteClient.LOGGER.log(Level.SEVERE, "Cannot save voting configuration", e);
        }
    }
}