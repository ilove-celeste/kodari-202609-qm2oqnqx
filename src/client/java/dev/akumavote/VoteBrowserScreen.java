package dev.akumavote;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.akumavote.AkumaVoteClient.VoteStatus;
import dev.akumavote.accessor.DrawContextAccessor;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import org.cef.callback.CefStringVisitor;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.util.Locale;

public final class VoteBrowserScreen extends Screen {
    private static final int PAGE_LEFT = 8;
    private static final int PAGE_TOP = 48;
    private static final int PAGE_MARGIN = 8;
    private static final int HEADER_HEIGHT = 40;
    private static final int TEXT_SCAN_INTERVAL_TICKS = 20;
    private static final int DIAGNOSTIC_INTERVAL_TICKS = 100;
    private static final int MIN_ZOOM_PERCENT = 50;
    private static final int MAX_ZOOM_PERCENT = 200;
    private static final int ZOOM_STEP_PERCENT = 10;

    private final AkumaVoteClient mod;
    private final VoteSite site;
    private final int siteIndex;
    private final Screen parent;

    private MCEFBrowser browser;
    private boolean webView2;
    private String browserError;
    private String message = "";
    private int textScanCooldown;
    private int diagnosticCooldown;
    private String lastPageTextFingerprint = "";
    private String lastPageSourceFingerprint = "";
    private int zoomPercent;
    private String lastWebView2State = "";
    private int lastWindowWidth = -1;
    private int lastWindowHeight = -1;
    private int webView2StartupTicks;

    public VoteBrowserScreen(AkumaVoteClient mod, int siteIndex, Screen parent) {
        super(Text.literal("Голосование — " + VoteSite.ALL.get(siteIndex).name()));
        this.mod = mod;
        this.siteIndex = siteIndex;
        this.site = VoteSite.ALL.get(siteIndex);
        this.parent = parent;
        this.zoomPercent = mod.store().browserZoom();
    }

    @Override
    protected void init() {
        if (browser != null || webView2) {
            resizeBrowser();
            return;
        }

        if (WebView2Native.isSupported()) {
            try {
                long glfwHandle = MinecraftClient.getInstance().getWindow().getHandle();
                long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwHandle);
                AkumaVoteClient.logStoreDebug("WebView2 host handles: GLFWwindow*=0x"
                        + Long.toHexString(glfwHandle) + ", HWND=0x" + Long.toHexString(hwnd));
                boolean started = WebView2Native.create(
                        hwnd,
                        site.url(),
                        width,
                        height,
                        PAGE_LEFT,
                        PAGE_TOP,
                        Math.max(1, width - PAGE_LEFT - PAGE_MARGIN),
                        Math.max(1, height - PAGE_TOP - PAGE_MARGIN),
                        zoomPercent / 100.0D
                );
                if (started) {
                    webView2 = true;
                    webView2StartupTicks = 0;
                    mod.setVoteStatus(siteIndex, VoteStatus.IN_PROGRESS);
                    message = "WebView2 запускается...";
                    AkumaVoteClient.logStoreDebug("Using WebView2 backend for " + site.name());
                    return;
                }
                browserError = WebView2Native.state();
                AkumaVoteClient.logStoreDebug("WebView2 backend unavailable, falling back to MCEF: " + browserError);
                AkumaVoteClient.logStoreDebug("WebView2 start rejected: " + browserError);
            } catch (RuntimeException exception) {
                browserError = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                AkumaVoteClient.reportError("Не удалось запустить WebView2.", exception);
            }
        }

        if (!mod.isMcefAvailable()) {
            browserError = browserError == null ? "WebView2 и MCEF недоступны." : browserError;
            mod.setVoteStatus(siteIndex, VoteStatus.UNAVAILABLE);
            return;
        }

        try {
            browser = MCEFApi.getInstance().createBrowser(site.url(), false);
            resizeBrowser();
            applyZoom("browser-created");
            browser.setFocus(true);
            mod.setVoteStatus(siteIndex, VoteStatus.IN_PROGRESS);
            logBrowserLifecycle("created");
        } catch (RuntimeException exception) {
            browserError = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            mod.setVoteStatus(siteIndex, VoteStatus.UNAVAILABLE);
            AkumaVoteClient.reportError("Не удалось запустить встроенный браузер MCEF.", exception);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (width != lastWindowWidth || height != lastWindowHeight) {
            lastWindowWidth = width;
            lastWindowHeight = height;
            resizeBrowser();
        }

        if (webView2) {
            String state = WebView2Native.state();
            if (WebView2Native.isReady()) {
                if (webView2StartupTicks != -1) {
                    webView2StartupTicks = -1;
                    message = "WebView2 готов.";
                    AkumaVoteClient.logStoreDebug("WebView2 is ready.");
                }
            } else {
                webView2StartupTicks++;
            }
            if (state.contains("error=") && !state.endsWith("error=")) {
                fallbackFromWebView2(state, "native-error");
                return;
            }
            if (webView2StartupTicks >= 200) {
                fallbackFromWebView2(state, "startup-timeout-10s");
                return;
            }

            if (diagnosticCooldown > 0) {
                diagnosticCooldown--;
            } else {
                diagnosticCooldown = DIAGNOSTIC_INTERVAL_TICKS;
                collectBrowserDiagnostics();
            }
            return;
        }

        if (browser == null || mod.voteStatus(siteIndex) != VoteStatus.IN_PROGRESS) {
            return;
        }

        if (diagnosticCooldown > 0) {
            diagnosticCooldown--;
        } else {
            diagnosticCooldown = DIAGNOSTIC_INTERVAL_TICKS;
            collectBrowserDiagnostics();
        }

        if (textScanCooldown > 0) {
            textScanCooldown--;
            return;
        }

        textScanCooldown = TEXT_SCAN_INTERVAL_TICKS;
        scanPageText();
    }

    private void scanPageText() {
        try {
            browser.getCefBrowser().getText(new CefStringVisitor() {
                @Override
                public void visit(String text) {
                    debugPageText(text);
                    if (!containsConfirmation(text)) {
                        return;
                    }

                    MinecraftClient client = MinecraftClient.getInstance();
                    client.execute(() -> {
                        if (browser != null && mod.voteStatus(siteIndex) == VoteStatus.IN_PROGRESS) {
                            mod.setVoteStatus(siteIndex, VoteStatus.CONFIRMED);
                            message = "Страница подтвердила получение голоса.";
                        }
                    });
                }
            });
        } catch (RuntimeException exception) {
            AkumaVoteClient.reportError("Не удалось прочитать текст страницы голосования.", exception);
        }
    }

    private boolean containsConfirmation(String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return false;
        }

        String normalized = pageText.toLowerCase(Locale.ROOT)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ");

        String[] confirmations = {
                "thank you for voting",
                "thanks for voting",
                "vote received",
                "voting received",
                "thank you for your vote",
                "thanks for your vote",
                "your vote has been recorded",
                "your vote was recorded",
                "vote has been recorded",
                "vote submitted",
                "voting successful",
                "successfully voted",
                "you have voted",
                "already voted today",
                "you already voted"
        };

        for (String phrase : confirmations) {
            if (normalized.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0xFF101720);
        context.fill(PAGE_LEFT, PAGE_TOP, width - PAGE_MARGIN, height - PAGE_MARGIN, 0xFF090D12);

        if (browser != null) {
            GpuTextureView texture = browser.getTextureView();
            if (texture != null) {
                DrawContextAccessor accessor = (DrawContextAccessor) (Object) context;
                accessor.akumavote$getState().addSimpleElement(new TexturedQuadGuiElementRenderState(
                        RenderPipelines.GUI_TEXTURED,
                        TextureSetup.of(
                                texture,
                                RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
                        ),
                        new Matrix3x2f(context.getMatrices()),
                        PAGE_LEFT,
                        PAGE_TOP,
                        width - PAGE_MARGIN,
                        height - PAGE_MARGIN,
                        0.0F,
                        1.0F,
                        0.0F,
                        1.0F,
                        0xFFFFFFFF,
                        null
                ));
            }
        }

        context.fill(0, 0, width, HEADER_HEIGHT, 0xFF192635);
        drawButton(context, "Назад", 8, 8, 58, 24, mouseX, mouseY);
        context.drawTextWithShadow(textRenderer, site.name(), 76, 16, 0xFFE6EDF7);
        context.drawTextWithShadow(
                textRenderer,
                statusText(),
                Math.max(220, width - 150),
                16,
                statusColor()
        );
        renderBrowserControls(context, mouseX, mouseY);

        if (browser == null && webView2) {
            if (!WebView2Native.isReady()) {
                context.drawTextWithShadow(textRenderer, "Подготовка WebView2: " + WebView2Native.state(),
                        PAGE_LEFT + 12, PAGE_TOP + 20, 0xFFFFD166);
            }
        } else if (browser == null) {
            if (mod.voteStatus(siteIndex) == VoteStatus.UNAVAILABLE) {
                context.drawTextWithShadow(
                        textRenderer,
                        "Встроенный браузер недоступен. Откройте страницу вручную:",
                        PAGE_LEFT + 12,
                        PAGE_TOP + 20,
                        0xFF9AA6B2
                );
                context.drawTextWithShadow(
                        textRenderer,
                        site.url(),
                        PAGE_LEFT + 12,
                        PAGE_TOP + 36,
                        0xFFE6EDF7
                );
                drawButton(
                        context,
                        "Копировать ссылку",
                        PAGE_LEFT + 12,
                        PAGE_TOP + 52,
                        128,
                        24,
                        mouseX,
                        mouseY
                );
            } else {
                context.drawTextWithShadow(
                        textRenderer,
                        browserError == null ? "Подготовка встроенного браузера..." : "Ошибка MCEF: " + browserError,
                        PAGE_LEFT + 12,
                        PAGE_TOP + 20,
                        0xFFFFD166
                );
            }
        }

        if (!message.isEmpty()) {
            context.drawTextWithShadow(
                    textRenderer,
                    message,
                    PAGE_LEFT + 12,
                    height - 20,
                    0xFFE6EDF7
            );
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && inside(click.x(), click.y(), 8, 8, 58, 24)) {
            close();
            return true;
        }

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && inside(click.x(), click.y(), width - 110, 8, 22, 24)) {
            setZoomPercent(zoomPercent + ZOOM_STEP_PERCENT);
            return true;
        }

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && inside(click.x(), click.y(), width - 136, 8, 22, 24)) {
            setZoomPercent(zoomPercent - ZOOM_STEP_PERCENT);
            return true;
        }

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && inside(click.x(), click.y(), width - 82, 8, 52, 24)) {
            setZoomPercent(100);
            return true;
        }

        if (browser == null
                && mod.voteStatus(siteIndex) == VoteStatus.UNAVAILABLE
                && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && inside(click.x(), click.y(), PAGE_LEFT + 12, PAGE_TOP + 52, 128, 24)) {
            MinecraftClient.getInstance().keyboard.setClipboard(site.url());
            message = "Ссылка скопирована в буфер обмена.";
            return true;
        }

        if (webView2 && insideBrowser(click.x(), click.y())) {
            return true;
        }

        if (browser != null && insideBrowser(click.x(), click.y())) {
            browser.onMouseClicked(toBrowserClick(click), doubled);
            browser.setFocus(true);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (webView2 && insideBrowser(click.x(), click.y())) {
            return true;
        }
        if (browser != null) {
            browser.onMouseReleased(toBrowserClick(click));
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (webView2 && insideBrowser(mouseX, mouseY)) {
            return true;
        }
        if (browser != null && insideBrowser(mouseX, mouseY)) {
            boolean ctrlDown = isControlDown();
            if (ctrlDown) {
                setZoomPercent(zoomPercent + (verticalAmount > 0 ? ZOOM_STEP_PERCENT : -ZOOM_STEP_PERCENT));
                return true;
            }
            browser.onMouseScrolled(
                    (int) (mouseX - PAGE_LEFT),
                    (int) (mouseY - PAGE_TOP),
                    verticalAmount
            );
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (webView2 && insideBrowser(mouseX, mouseY)) {
            return;
        }
        if (browser != null && insideBrowser(mouseX, mouseY)) {
            browser.onMouseMoved(
                    (int) (mouseX - PAGE_LEFT),
                    (int) (mouseY - PAGE_TOP)
            );
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (isControlDown()) {
            if (input.key() == GLFW.GLFW_KEY_EQUAL || input.key() == GLFW.GLFW_KEY_KP_ADD) {
                setZoomPercent(zoomPercent + ZOOM_STEP_PERCENT);
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_MINUS || input.key() == GLFW.GLFW_KEY_KP_SUBTRACT) {
                setZoomPercent(zoomPercent - ZOOM_STEP_PERCENT);
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_0 || input.key() == GLFW.GLFW_KEY_KP_0) {
                setZoomPercent(100);
                return true;
            }
        }
        if (webView2) {
            return true;
        }
        if (browser != null) {
            browser.onKeyPressed(input);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (webView2) {
            return true;
        }
        if (browser != null) {
            browser.onKeyReleased(input);
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (webView2) {
            return true;
        }
        if (browser != null) {
            browser.onCharTyped(input);
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        if (webView2) {
            AkumaVoteClient.logStoreDebug("Closing WebView2 backend. State=" + WebView2Native.state());
            WebView2Native.close();
            webView2 = false;
            webView2StartupTicks = 0;
        }
        if (browser != null) {
            logBrowserLifecycle("closing");
            browser.close();
            browser = null;
        }
    }

    private void resizeBrowser() {
        int browserWidth = Math.max(1, width - PAGE_LEFT - PAGE_MARGIN);
        int browserHeight = Math.max(1, height - PAGE_TOP - PAGE_MARGIN);
        if (webView2) {
            long hwnd = getWebView2Hwnd();
            if (hwnd == 0L) {
                return;
            }
            WebView2Native.resize(
                    hwnd,
                    width,
                    height,
                    PAGE_LEFT,
                    PAGE_TOP,
                    browserWidth,
                    browserHeight
            );
            return;
        }
        if (browser != null) {
            browser.resize(browserWidth, browserHeight);
            AkumaVoteClient.logStoreDebug("Browser resized: " + browserWidth + "x" + browserHeight
                    + " at " + width + "x" + height);
        }
    }

    private void fallbackFromWebView2(String state, String reason) {
        browserError = state;
        message = "WebView2 не запустился, переключаюсь на MCEF...";
        AkumaVoteClient.logStoreDebug("WebView2 fallback [" + reason + "]: " + state);
        WebView2Native.close();
        webView2 = false;
        webView2StartupTicks = 0;
        if (mod.isMcefAvailable()) {
            startMcefBrowser();
        } else {
            mod.setVoteStatus(siteIndex, VoteStatus.UNAVAILABLE);
        }
    }

    private long getWebView2Hwnd() {
        long glfwHandle = MinecraftClient.getInstance().getWindow().getHandle();
        long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwHandle);
        if (hwnd == 0L) {
            AkumaVoteClient.logStoreDebug("WebView2 host HWND lookup failed: GLFWwindow*=0x"
                    + Long.toHexString(glfwHandle));
        }
        return hwnd;
    }

    private void startMcefBrowser() {
        if (browser != null || webView2) {
            return;
        }
        try {
            browser = MCEFApi.getInstance().createBrowser(site.url(), false);
            resizeBrowser();
            applyZoom("mcef-fallback");
            browser.setFocus(true);
            mod.setVoteStatus(siteIndex, VoteStatus.IN_PROGRESS);
            logBrowserLifecycle("mcef-fallback-created");
        } catch (RuntimeException exception) {
            browserError = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            mod.setVoteStatus(siteIndex, VoteStatus.UNAVAILABLE);
            AkumaVoteClient.reportError("Не удалось запустить fallback MCEF.", exception);
        }
    }

    private void applyZoom(String reason) {
        if (webView2) {
            WebView2Native.setZoom(zoomPercent / 100.0D);
            AkumaVoteClient.logStoreDebug("WebView2 zoom " + zoomPercent + "%");
            return;
        }
        if (browser == null) {
            return;
        }
        try {
            double level = Math.log(zoomPercent / 100.0D) / Math.log(1.2D);
            browser.getCefBrowser().setZoomLevel(level);
            double actualLevel = browser.getCefBrowser().getZoomLevel();
            AkumaVoteClient.logStoreDebug("Browser zoom " + zoomPercent + "% (CEF level="
                    + String.format(Locale.ROOT, "%.3f", actualLevel) + ", reason=" + reason + ")");
        } catch (RuntimeException exception) {
            AkumaVoteClient.reportError("Не удалось изменить масштаб встроенного браузера.", exception);
        }
    }

    private void setZoomPercent(int percent) {
        int clamped = Math.clamp(percent, MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT);
        if (clamped == zoomPercent && browser != null) {
            applyZoom("unchanged");
            return;
        }
        zoomPercent = clamped;
        if (browser != null) {
            applyZoom("user");
        }
        message = "Масштаб страницы: " + zoomPercent + "%";
    }

    private void renderBrowserControls(DrawContext context, int mouseX, int mouseY) {
        if (browser == null && !webView2) {
            return;
        }
        int minusX = width - 136;
        int resetX = width - 82;
        int plusX = width - 110;
        drawButton(context, "−", minusX, 8, 22, 24, mouseX, mouseY);
        drawButton(context, "100%", resetX, 8, 52, 24, mouseX, mouseY);
        drawButton(context, "+", plusX, 8, 22, 24, mouseX, mouseY);
        context.drawTextWithShadow(textRenderer, zoomPercent + "%", width - 178, 16, 0xFFE6EDF7);
    }

    private boolean isControlDown() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private void logBrowserLifecycle(String reason) {
        if (browser == null) {
            AkumaVoteClient.logStoreDebug("Browser lifecycle [" + reason + "]: browser=null");
            return;
        }
        try {
            var cef = browser.getCefBrowser();
            AkumaVoteClient.logStoreDebug("Browser lifecycle [" + reason + "]: site=" + site.name()
                    + ", url=" + safe(cef.getURL())
                    + ", id=" + cef.getIdentifier()
                    + ", loading=" + cef.isLoading()
                    + ", document=" + cef.hasDocument()
                    + ", frames=" + cef.getFrameCount()
                    + ", zoomLevel=" + String.format(Locale.ROOT, "%.3f", cef.getZoomLevel())
                    + ", cefClass=" + cef.getClass().getName()
                    + ", mcefClass=" + browser.getClass().getName());
        } catch (RuntimeException exception) {
            AkumaVoteClient.reportError("Ошибка получения состояния браузера (" + reason + ").", exception);
        }
    }

    private void collectBrowserDiagnostics() {
        if (webView2) {
            String state = WebView2Native.state();
            if (!state.equals(lastWebView2State)) {
                lastWebView2State = state;
                AkumaVoteClient.logStoreDebug("WebView2 diagnostics: " + state);
            }
            return;
        }
        if (browser == null) {
            return;
        }
        try {
            var cef = browser.getCefBrowser();
            String url = safe(cef.getURL());
            String mainFrameUrl = cef.getMainFrame() == null ? "null" : safe(cef.getMainFrame().getURL());
            AkumaVoteClient.logStoreDebug("Browser diagnostics: url=" + url
                    + ", mainFrameUrl=" + mainFrameUrl
                    + ", loading=" + cef.isLoading()
                    + ", document=" + cef.hasDocument()
                    + ", frames=" + cef.getFrameCount()
                    + ", viewport=" + Math.max(1, width - PAGE_LEFT - PAGE_MARGIN) + "x"
                    + Math.max(1, height - PAGE_TOP - PAGE_MARGIN)
                    + ", zoom=" + zoomPercent + "%/" + String.format(Locale.ROOT, "%.3f", cef.getZoomLevel()));

            if (cef.hasDocument()) {
                cef.getSource(new CefStringVisitor() {
                    @Override
                    public void visit(String source) {
                        debugPageSource(source);
                    }
                });
            }
        } catch (RuntimeException exception) {
            AkumaVoteClient.reportError("Ошибка browser diagnostics.", exception);
        }
    }

    private void debugPageText(String text) {
        String safeText = text == null ? "" : text;
        String fingerprint = Integer.toHexString(safeText.hashCode());
        if (fingerprint.equals(lastPageTextFingerprint)) {
            return;
        }
        lastPageTextFingerprint = fingerprint;

        String normalized = safeText.toLowerCase(Locale.ROOT).replace('\u00A0', ' ').replaceAll("\\s+", " ");
        boolean cloudflare = normalized.contains("cloudflare");
        boolean verificationFailed = normalized.contains("verification failed");
        boolean troubleshoot = normalized.contains("troubleshoot");
        boolean turnstile = normalized.contains("turnstile");
        boolean success = containsConfirmation(normalized);
        String challengeSnippet = firstSnippet(normalized, "verification failed", 360);
        if (challengeSnippet == null) {
            challengeSnippet = firstSnippet(normalized, "troubleshoot", 360);
        }

        AkumaVoteClient.logStoreDebug("Page text changed: chars=" + safeText.length()
                + ", hash=" + fingerprint
                + ", cloudflare=" + cloudflare
                + ", verificationFailed=" + verificationFailed
                + ", troubleshoot=" + troubleshoot
                + ", turnstile=" + turnstile
                + ", confirmation=" + success
                + (challengeSnippet == null ? "" : ", challengeSnippet=\"" + challengeSnippet + "\""));
    }

    private void debugPageSource(String source) {
        String safeSource = source == null ? "" : source;
        String fingerprint = Integer.toHexString(safeSource.hashCode());
        if (fingerprint.equals(lastPageSourceFingerprint)) {
            return;
        }
        lastPageSourceFingerprint = fingerprint;

        String lower = safeSource.toLowerCase(Locale.ROOT);
        boolean cloudflare = lower.contains("cloudflare");
        boolean turnstile = lower.contains("turnstile");
        boolean challengePlatform = lower.contains("challenge-platform");
        boolean cfChallenge = lower.contains("cf_chl_") || lower.contains("__cf_chl");
        boolean challengesDomain = lower.contains("challenges.cloudflare.com");
        String rayId = extractFirst(safeSource, "(?i)(?:ray id|cf-ray)[^a-z0-9]{0,20}([a-z0-9-]{8,32})");
        String title = extractFirst(safeSource, "(?is)<title[^>]*>\\s*(.*?)\\s*</title>");
        int iframeCount = count(lower, "<iframe");
        int scriptCount = count(lower, "<script");
        int turnstileFrameCount = count(lower, "challenges.cloudflare.com/turnstile");
        AkumaVoteClient.logStoreDebug("Page source changed: chars=" + safeSource.length()
                + ", hash=" + fingerprint
                + ", title=\"" + compact(title) + "\""
                + ", cloudflare=" + cloudflare
                + ", turnstile=" + turnstile
                + ", challengePlatform=" + challengePlatform
                + ", cfChallenge=" + cfChallenge
                + ", challengesDomain=" + challengesDomain
                + ", iframes=" + iframeCount
                + ", scripts=" + scriptCount
                + ", turnstileFrames=" + turnstileFrameCount
                + ", rayId=" + (rayId == null ? "not-found" : rayId));
    }

    private String firstSnippet(String text, String needle, int radius) {
        int index = text.indexOf(needle);
        if (index < 0) {
            return null;
        }
        int start = Math.max(0, index - radius);
        int end = Math.min(text.length(), index + needle.length() + radius);
        return compact(text.substring(start, end));
    }

    private String compact(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "…";
    }

    private int count(String text, String needle) {
        int result = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            result++;
            from += needle.length();
        }
        return result;
    }

    private String extractFirst(String text, String regex) {
        var matcher = java.util.regex.Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String safe(String value) {
        return value == null ? "null" : value;
    }

    private Click toBrowserClick(Click click) {
        return new Click(
                click.x() - PAGE_LEFT,
                click.y() - PAGE_TOP,
                new MouseInput(click.button(), click.modifiers())
        );
    }

    private boolean insideBrowser(double x, double y) {
        return inside(
                x,
                y,
                PAGE_LEFT,
                PAGE_TOP,
                width - PAGE_LEFT - PAGE_MARGIN,
                height - PAGE_TOP - PAGE_MARGIN
        );
    }

    private String statusText() {
        return switch (mod.voteStatus(siteIndex)) {
            case NOT_VOTED -> "Не голосовал";
            case IN_PROGRESS -> "В процессе";
            case CONFIRMED -> "Подтверждён";
            case UNAVAILABLE -> "Недоступно";
        };
    }

    private int statusColor() {
        return switch (mod.voteStatus(siteIndex)) {
            case NOT_VOTED -> 0xFFFF596B;
            case IN_PROGRESS -> 0xFFFFD166;
            case CONFIRMED -> 0xFF4FE18D;
            case UNAVAILABLE -> 0xFF9AA6B2;
        };
    }

    private void drawButton(
            DrawContext context,
            String label,
            int x,
            int y,
            int buttonWidth,
            int buttonHeight,
            int mouseX,
            int mouseY
    ) {
        boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        context.fill(
                x,
                y,
                x + buttonWidth,
                y + buttonHeight,
                hovered ? 0xFF354A60 : 0xFF28394B
        );
        context.drawTextWithShadow(textRenderer, label, x + 7, y + 8, 0xFFFFFFFF);
    }

    private boolean inside(double x, double y, int left, int top, int boxWidth, int boxHeight) {
        return x >= left && x < left + boxWidth
                && y >= top && y < top + boxHeight;
    }
}
