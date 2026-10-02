package dev.akumavote;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WebView2Native {
    private static final String RESOURCE = "/assets/akumavote/native/akumavote-webview2.dll";
    private static final Path NATIVE_DIR = Path.of("config", "akumavote", "native");
    private static volatile boolean loaded;

    private WebView2Native() {
    }

    public static synchronized boolean isSupported() {
        if (!isWindows() || !isX64()) {
            return false;
        }
        if (!load()) {
            return false;
        }
        try {
            return nativeIsAvailable();
        } catch (Throwable error) {
            AkumaVoteClient.reportError("WebView2 runtime check failed.", error);
            return false;
        }
    }

    public static synchronized boolean create(long parentHwnd, String url,
                                              int logicalWindowWidth, int logicalWindowHeight,
                                              int x, int y, int width, int height,
                                              double zoomFactor) {
        if (!isSupported()) {
            return false;
        }
        try {
            return nativeCreate(parentHwnd, url, logicalWindowWidth, logicalWindowHeight,
                    x, y, width, height, zoomFactor);
        } catch (Throwable error) {
            AkumaVoteClient.reportError("WebView2 create failed.", error);
            return false;
        }
    }

    public static void resize(long parentHwnd,
                              int logicalWindowWidth, int logicalWindowHeight,
                              int x, int y, int width, int height) {
        if (!loaded) {
            return;
        }
        try {
            nativeSetBounds(parentHwnd, logicalWindowWidth, logicalWindowHeight, x, y, width, height);
        } catch (Throwable error) {
            AkumaVoteClient.reportError("WebView2 resize failed.", error);
        }
    }

    public static boolean isReady() {
        if (!loaded) {
            return false;
        }
        try {
            return nativeIsReady();
        } catch (Throwable error) {
            return false;
        }
    }

    public static String state() {
        if (!loaded) {
            return "not-loaded";
        }
        try {
            return nativeGetState();
        } catch (Throwable error) {
            return "state-error: " + error;
        }
    }

    public static synchronized void close() {
        if (!loaded) {
            return;
        }
        try {
            nativeClose();
        } catch (Throwable error) {
            AkumaVoteClient.reportError("WebView2 close failed.", error);
        }
    }

    private static boolean load() {
        if (loaded) {
            return true;
        }
        try (InputStream input = WebView2Native.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                return false;
            }
            Files.createDirectories(NATIVE_DIR);
            Path target = NATIVE_DIR.resolve("akumavote-webview2.dll");
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.load(target.toAbsolutePath().toString());
            loaded = true;
            return true;
        } catch (IOException | UnsatisfiedLinkError | SecurityException error) {
            AkumaVoteClient.reportError("Cannot load WebView2 native bridge.", error);
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static boolean isX64() {
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        return arch.equals("amd64") || arch.equals("x86_64");
    }

    private static native boolean nativeIsAvailable();

    private static native boolean nativeCreate(long parentHwnd, String url,
                                               int logicalWindowWidth, int logicalWindowHeight,
                                               int x, int y, int width, int height,
                                               double zoomFactor);

    private static native void nativeSetBounds(long parentHwnd,
                                               int logicalWindowWidth, int logicalWindowHeight,
                                               int x, int y, int width, int height);

    private static native boolean nativeIsReady();

    private static native String nativeGetState();

    private static native void nativeClose();
}
