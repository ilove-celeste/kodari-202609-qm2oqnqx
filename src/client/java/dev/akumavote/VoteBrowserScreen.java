package dev.akumavote;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.text.Text;
import org.cef.browser.CefFrame;
import org.cef.callback.CefStringVisitor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class VoteBrowserScreen extends Screen {
    private static final int PAGE_LEFT = 8;
    private static final int PAGE_TOP = 48;
    private static final int PAGE_MARGIN = 8;
    private static final int HEADER_HEIGHT = 40;
    private static final int TEXT_SCAN_INTERVAL_TICKS = 20;

    private final AkumaVoteClient mod;
    private final VoteSite site;
    private final int siteIndex;
    private final Screen parent;
    private MCEFBrowser browser;
    private String browserError;
    private String message = "";
    private int textScanCooldown;

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
            return;
        }

        if (!mod.isMcefAvailable()) {
            browserError = "MCEF недоступен.";
            mod.setVoteStatus(siteIndex, AkumaVoteClient.VoteStatus.UNAVAILABLE);
            return;
        }

        try {
            browser = MCEFApi.getInstance().createBrowser(site.url(), false);
            resizeBrowser();
            browser.setFocus(true);
            mod.setVoteStatus(siteIndex, AkumaVoteClient.VoteStatus.IN_PROGRESS);
        } catch (RuntimeException exception) {
            browserError = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            mod.setVoteStatus(siteIndex, AkumaVoteClient.VoteStatus.UNAVAILABLE);
            AkumaVoteClient.reportError("Не удалось запустить встроенный браузер MCEF.", exception);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (browser == null || mod.voteStatus(siteIndex) != AkumaVoteClient.VoteStatus.IN_PROGRESS) {
            return;
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
            CefFrame frame = browser.getCefBrowser().getMainFrame();
            if (frame == null) {
                return;
            }
            frame.getText(new CefStringVisitor() {
                @Override
                public void visit(String text) {
                    if (containsConfirmation(text)) {
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> {
                            if (browser != null && mod.voteStatus(siteIndex) == AkumaVoteClient.VoteStatus.IN_PROGRESS) {
                                mod.setVoteStatus(siteIndex, AkumaVoteClient.VoteStatus.CONFIRMED);
                                message = "Страница подтвердила получение голоса.";
                            }
                        });
                    }
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
                .replace('\u00a0', ' ')
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
                context.guiRenderState.submitGuiElement(new BlitRenderState(
                        RenderPipelines.GUI_TEXTURED,
                        TextureSetup.singleTexture(
                                texture,
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        ),
                        new Matrix3x2f(context.pose()),
                        PAGE_LEFT,
                        PAGE_TOP,
                        width - PAGE_MARGIN,
                        height - PAGE_MARGIN,
                        0.0F,
                        1.0F,
                        0.0F,
                        1.0F,
                        0xFFFFFFFF,
                        context.scissorStack.peek()
                ));
                context.requestCursor(browser.getCursorType());
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

        if (browser == null) {
            if (mod.voteStatus(siteIndex) == AkumaVoteClient.VoteStatus.UNAVAILABLE) {
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
                        "Подготовка встроенного браузера...",
                        PAGE_LEFT + 12,
                        PAGE_TOP + 20,
                        0xFFFFD166
                );
            }
        }

        if (!message.isEmpty()) {
            context.drawTextWithShadow(textRenderer, message, PAGE_LEFT + 12, height - 20, 0xFFE6EDF7);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(event.x(), event.y(), 8, 8, 58, 24)) {
            close();
            return true;
        }

        if (browser == null
                && mod.voteStatus(siteIndex) == AkumaVoteClient.VoteStatus.UNAVAILABLE
                && inside(event.x(), event.y(), PAGE_LEFT + 12, PAGE_TOP + 52, 128, 24)
                && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            MinecraftClient.getInstance().keyboard.setClipboard(site.url());
            message = "Ссылка скопирована в буфер обмена.";
            return true;
        }

        if (browser != null && insideBrowser(event.x(), event.y())) {
            browser.onMouseClicked(toBrowserEvent(event), doubled);
            browser.setFocus(true);
            return true;
        }

        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (browser != null) {
            browser.onMouseReleased(toBrowserEvent(event));
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (browser != null && insideBrowser(mouseX, mouseY)) {
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
        if (browser != null && insideBrowser(mouseX, mouseY)) {
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

    private void resizeBrowser() {
        if (browser != null) {
            browser.resize(
                    Math.max(1, width - PAGE_LEFT - PAGE_MARGIN),
                    Math.max(1, height - PAGE_TOP - PAGE_MARGIN)
            );
        }
    }

    private MouseButtonEvent toBrowserEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(
                event.x() - PAGE_LEFT,
                event.y() - PAGE_TOP,
                event.buttonInfo()
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
        return x >= left && x < left + boxWidth && y >= top && y < top + boxHeight;
    }
}
