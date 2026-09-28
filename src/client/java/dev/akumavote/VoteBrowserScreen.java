package dev.akumavote;

import dev.akumavote.AkumaVoteClient.VoteStatus;
import dev.akumavote.mixin.client.DrawContextRenderAccess;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.concurrent.CompletableFuture;

public final class VoteBrowserScreen extends Screen {
    private static final int PAGE_LEFT = 8;
    private static final int PAGE_TOP = 48;
    private static final int PAGE_MARGIN = 8;
    private static final int CONFIRM_BUTTON_WIDTH = 148;

    private final AkumaVoteClient mod;
    private final VoteSite site;
    private final int siteIndex;
    private final Screen parent;
    private CompletableFuture<MCEFApi> apiFuture;
    private MCEFBrowser browser;
    private String browserError;
    private String message = "";

    public VoteBrowserScreen(AkumaVoteClient mod, int siteIndex, Screen parent) {
        super(Text.literal("Голосование — " + VoteSite.ALL.get(siteIndex).name()));
        this.mod = mod;
        this.siteIndex = siteIndex;
        this.site = VoteSite.ALL.get(siteIndex);
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (browser != null) {
            resizeBrowser();
        } else if (apiFuture == null) {
            apiFuture = MCEFApi.getInstanceFuture();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0xFF101720);
        context.fill(PAGE_LEFT, PAGE_TOP, width - PAGE_MARGIN, height - PAGE_MARGIN, 0xFF090D12);
        initializeBrowserIfReady();

        if (browser != null) {
            GpuTextureView texture = browser.getTextureView();
            if (texture != null) {
                DrawContextRenderAccess.drawBrowserTexture(context, texture, PAGE_LEFT, PAGE_TOP,
                        width - PAGE_MARGIN, height - PAGE_MARGIN);
            }
        }

        context.fill(0, 0, width, 40, 0xFF192635);
        drawButton(context, "Назад", 8, 8, 58, 24, mouseX, mouseY);
        drawButton(context, "Подтвердить голос", 72, 8, CONFIRM_BUTTON_WIDTH, 24, mouseX, mouseY);
        context.drawTextWithShadow(textRenderer, site.name(), 232, 16, 0xFFE6EDF7);
        context.drawTextWithShadow(textRenderer, statusText(), Math.max(240, width - 150), 16, statusColor());

        if (browser == null) {
            String loadingText = browserError != null ? "Ошибка MCEF: " + browserError : "Подготовка встроенного браузера...";
            context.drawTextWithShadow(textRenderer, loadingText, PAGE_LEFT + 12, PAGE_TOP + 20,
                    browserError != null ? 0xFFFF596B : 0xFFFFD166);
        }
        if (!message.isEmpty()) {
            context.drawTextWithShadow(textRenderer, message, PAGE_LEFT + 12, height - 20, 0xFFE6EDF7);
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(click.x(), click.y(), 8, 8, 58, 24)) {
            close();
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && inside(click.x(), click.y(), 72, 8, CONFIRM_BUTTON_WIDTH, 24)) {
            mod.setVoteStatus(siteIndex, VoteStatus.CONFIRMED);
            message = "Голос отмечен как подтверждённый.";
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
        if (browser != null) {
            browser.onMouseReleased(toBrowserClick(click));
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (browser != null && insideBrowser(mouseX, mouseY)) {
            browser.onMouseScrolled((int) (mouseX - PAGE_LEFT), (int) (mouseY - PAGE_TOP), verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null) {
            browser.onMouseMoved((int) (mouseX - PAGE_LEFT), (int) (mouseY - PAGE_TOP));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
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
        if (browser != null) {
            browser.onKeyReleased(input);
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
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
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }

    private void initializeBrowserIfReady() {
        if (browser != null || browserError != null || apiFuture == null || !apiFuture.isDone()) {
            return;
        }
        try {
            browser = apiFuture.join().createBrowser(site.url(), false);
            resizeBrowser();
            browser.setFocus(true);
        } catch (RuntimeException exception) {
            browserError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            AkumaVoteClient.reportError("Не удалось запустить встроенный браузер MCEF.", exception);
        }
    }

    private void resizeBrowser() {
        if (browser != null) {
            browser.resize(Math.max(1, width - PAGE_LEFT - PAGE_MARGIN),
                    Math.max(1, height - PAGE_TOP - PAGE_MARGIN));
        }
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

    private Click toBrowserClick(Click click) {
        return new Click(click.x() - PAGE_LEFT, click.y() - PAGE_TOP,
                new MouseInput(click.button(), click.modifiers()));
    }

    private boolean insideBrowser(double x, double y) {
        return inside(x, y, PAGE_LEFT, PAGE_TOP, width - PAGE_LEFT - PAGE_MARGIN,
                height - PAGE_TOP - PAGE_MARGIN);
    }

    private void drawButton(DrawContext context, String label, int x, int y, int buttonWidth, int buttonHeight,
                            int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        context.fill(x, y, x + buttonWidth, y + buttonHeight, hovered ? 0xFF354A60 : 0xFF28394B);
        context.drawTextWithShadow(textRenderer, label, x + 7, y + 8, 0xFFFFFFFF);
    }

    private boolean inside(double x, double y, int left, int top, int boxWidth, int boxHeight) {
        return x >= left && x < left + boxWidth && y >= top && y < top + boxHeight;
    }
}