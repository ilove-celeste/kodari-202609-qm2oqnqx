package dev.akumavote;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

public final class VoteBrowserScreen extends Screen {
    private static final int PAGE_LEFT = 8;
    private static final int PAGE_TOP = 48;
    private static final int PAGE_MARGIN = 8;
    private static final int HEADER_HEIGHT = 40;
    private static final int STARTUP_TIMEOUT_TICKS = 200;
    private static final int PURPLE = 0xFFB56CFF;
    private static final int PURPLE_DARK = 0xFF170A24;
    private static final int PURPLE_BAR = 0xFF261136;
    private static final int TEXT = 0xFFF3E9FF;
    private static final int ERROR = 0xFFFFB4C4;

    private final AkumaVoteClient mod;
    private final VoteSite site;
    private final int siteIndex;
    private final Screen parent;

    private boolean webView2;
    private String browserError;
    private int startupTicks;
    private int lastWindowWidth = -1;
    private int lastWindowHeight = -1;

    public VoteBrowserScreen(AkumaVoteClient mod, int siteIndex, Screen parent) {
        super(Text.literal("Voting — " + VoteSite.ALL.get(siteIndex).name()));
        this.mod = mod;
        this.siteIndex = siteIndex;
        this.site = VoteSite.ALL.get(siteIndex);
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (webView2) {
            resizeBrowser();
            return;
        }

        long glfwHandle = MinecraftClient.getInstance().getWindow().getHandle();
        long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwHandle);

        if (hwnd == 0L) {
            browserError = "Failed to resolve the Minecraft window HWND.";
            return;
        }

        if (!WebView2Native.isSupported()) {
            browserError = "WebView2 Runtime is unavailable.";
            return;
        }

        try {
            boolean started = WebView2Native.create(
                    hwnd,
                    site.url(),
                    width,
                    height,
                    PAGE_LEFT,
                    PAGE_TOP,
                    Math.max(1, width - PAGE_LEFT - PAGE_MARGIN),
                    Math.max(1, height - PAGE_TOP - PAGE_MARGIN),
                    1.0D
            );

            if (!started) {
                browserError = WebView2Native.state();
                return;
            }

            webView2 = true;
            startupTicks = 0;
        } catch (RuntimeException exception) {
            browserError = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            AkumaVoteClient.reportError("Failed to start WebView2.", exception);
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

        if (!webView2) {
            return;
        }

        String state = WebView2Native.state();
        if (WebView2Native.isReady()) {
            if (startupTicks != -1) {
                startupTicks = -1;
            }
            return;
        }

        startupTicks++;
        if (state.contains("error=") && !state.endsWith("error=")) {
            failWebView2(state);
            return;
        }

        if (startupTicks >= STARTUP_TIMEOUT_TICKS) {
            failWebView2(state);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, PURPLE_DARK);
        context.fill(PAGE_LEFT, PAGE_TOP, width - PAGE_MARGIN, height - PAGE_MARGIN, 0xFF0E0716);
        context.fill(0, 0, width, HEADER_HEIGHT, PURPLE_BAR);

        drawButton(context, "Back", 8, 8, 58, 24, mouseX, mouseY);
        context.drawTextWithShadow(textRenderer, site.name(), 76, 16, TEXT);

        String status = statusText();
        int statusWidth = textRenderer.getWidth(status);
        context.drawTextWithShadow(textRenderer, status,
                Math.max(220, width - statusWidth - 14), 16, PURPLE);

        if (!webView2) {
            context.drawTextWithShadow(
                    textRenderer,
                    browserError == null ? "Embedded browser unavailable." : "WebView2 error: " + browserError,
                    PAGE_LEFT + 12,
                    PAGE_TOP + 20,
                    ERROR
            );
        } else if (!WebView2Native.isReady()) {
            context.drawTextWithShadow(
                    textRenderer,
                    "Preparing WebView2...",
                    PAGE_LEFT + 12,
                    PAGE_TOP + 20,
                    PURPLE
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

        if (webView2 && insideBrowser(click.x(), click.y())) {
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (webView2 && insideBrowser(click.x(), click.y())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (webView2 && insideBrowser(mouseX, mouseY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (webView2 && insideBrowser(mouseX, mouseY)) {
            return;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (webView2) {
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (webView2) {
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (webView2) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        mod.setVoteStatus(siteIndex, AkumaVoteClient.VoteStatus.CONFIRMED);
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        if (webView2) {
            WebView2Native.close();
            webView2 = false;
        }
    }

    private void resizeBrowser() {
        if (!webView2) {
            return;
        }

        long glfwHandle = MinecraftClient.getInstance().getWindow().getHandle();
        long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwHandle);
        if (hwnd == 0L) {
            return;
        }

        WebView2Native.resize(
                hwnd,
                width,
                height,
                PAGE_LEFT,
                PAGE_TOP,
                Math.max(1, width - PAGE_LEFT - PAGE_MARGIN),
                Math.max(1, height - PAGE_TOP - PAGE_MARGIN)
        );
    }

    private void failWebView2(String state) {
        browserError = state;
        WebView2Native.close();
        webView2 = false;
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
            case NOT_VOTED -> "Not Voted";
            case CONFIRMED -> "Voted";
            case UNAVAILABLE -> "Unavailable";
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
                hovered ? 0xFF4B2665 : 0xFF321844
        );
        outline(context, x, y, buttonWidth, buttonHeight,
                hovered ? 0xFFD9A7FF : 0xFF694B7D);
        context.drawTextWithShadow(textRenderer, label, x + 7, y + 8, TEXT);
    }

    private void outline(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private boolean inside(double x, double y, int left, int top, int boxWidth, int boxHeight) {
        return x >= left && x < left + boxWidth
                && y >= top && y < top + boxHeight;
    }
}
