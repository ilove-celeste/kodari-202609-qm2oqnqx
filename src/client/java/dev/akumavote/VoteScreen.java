package dev.akumavote;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public final class VoteScreen extends Screen {
    private static final int COLOR_RED = 0xFFFF596B;
    private static final int COLOR_GREEN = 0xFF4FE18D;
    private static final int COLOR_YELLOW = 0xFFFFD166;
    private static final int COLOR_GRAY = 0xFF9AA6B2;

    private final AkumaVoteClient mod;
    private int panelX = -1;
    private int panelY = -1;
    private int tab = 0;
    private int listScroll;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean debug;
    private boolean lightTheme;
    private boolean dragging;
    private boolean closing;
    private float opacity;
    private float hoverGlow;
    private String feedback = "";

    public VoteScreen(AkumaVoteClient mod) {
        super(Text.literal("AkumaVote"));
        this.mod = mod;
        debug = mod.store().debug();
        lightTheme = mod.store().lightTheme();
    }

    private int panelWidth() {
        return Math.min(560, Math.max(1, width - 20));
    }

    private int panelHeight() {
        return Math.min(440, Math.max(1, height - 20));
    }

    private int left() {
        return panelX;
    }

    private int top() {
        return panelY;
    }

    @Override
    protected void init() {
        if (panelX < 0) {
            panelX = (width - panelWidth()) / 2;
            panelY = (height - panelHeight()) / 2;
        }
        panelX = Math.clamp(panelX, 0, Math.max(0, width - panelWidth()));
        panelY = Math.clamp(panelY, 0, Math.max(0, height - panelHeight()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        float step = Math.min(1.0F, deltaTicks * 0.14F);
        opacity = Math.clamp(opacity + (closing ? -step : step), 0.0F, 1.0F);
        hoverGlow += ((inside(mouseX, mouseY, left(), top(), panelWidth(), 30) ? 1.0F : 0.0F) - hoverGlow) * step;
        if (closing && opacity == 0.0F) {
            super.close();
            return;
        }

        context.fill(0, 0, width, height, fade(lightTheme ? 0xCCEEF2F7 : 0xB805090F));
        context.fill(left(), top(), left() + panelWidth(), top() + panelHeight(),
                fade(lightTheme ? 0xF7FFFFFF : 0xF0101722));
        outline(context, left(), top(), panelWidth(), panelHeight(), fade(lightTheme ? 0xFFB8C2CC : 0xFF34465B));
        context.fill(left() + 1, top() + 1, left() + panelWidth() - 1, top() + 30,
                fade(lightTheme ? 0xFFE0E7EE : (hoverGlow > 0.05F ? 0xFF26394C : 0xFF1A2635)));
        context.fill(left() + 1, top() + 30, left() + panelWidth() - 1, top() + 32,
                fade(0xFF5B9DFF));

        text(context, "AKUMA VOTE", left() + 14, top() + 11, lightTheme ? 0xFF17212B : 0xFFFFFFFF);
        text(context, "play.akumamc.net", left() + panelWidth() - 136, top() + 11,
                lightTheme ? 0xFF5B6570 : 0xFF9AADC1);

        button(context, tab == 0 ? "Voting  •" : "Voting", left() + 14, top() + 42, 150, 25,
                mouseX, mouseY, tab == 0);
        button(context, tab == 1 ? "Settings  •" : "Settings", left() + 172, top() + 42, 136, 25,
                mouseX, mouseY, tab == 1);

        if (tab == 0) {
            renderVoting(context, mouseX, mouseY);
        } else {
            renderSettings(context, mouseX, mouseY);
        }
    }

    private void renderSettings(DrawContext context, int mouseX, int mouseY) {
        text(context, "Settings", left() + 18, top() + 82, primaryText());
        text(context, "Debug logging", left() + 18, top() + 116, primaryText());
        text(context, debug ? "Enabled" : "Disabled", left() + 18, top() + 138, secondaryText());

        outline(context, left() + panelWidth() - 58, top() + 109, 36, 18,
                fade(debug ? 0xFF5B9DFF : 0xFF718095));
        if (debug) {
            context.fill(left() + panelWidth() - 54, top() + 113,
                    left() + panelWidth() - 44, top() + 123, fade(0xFF5B9DFF));
        }

        text(context, "Theme", left() + 18, top() + 181, primaryText());
        button(context, lightTheme ? "Light" : "Dark", left() + 18, top() + 198, 120, 28,
                mouseX, mouseY, false);

        text(context, "Debug messages are written to chat and logs/akumavote.log.",
                left() + 18, top() + 246, secondaryText());

        button(context, "Save", left() + 18, top() + panelHeight() - 70, 120, 28,
                mouseX, mouseY, true);
        text(context, feedback, left() + 18, top() + panelHeight() - 31, secondaryText());
    }

    private void renderVoting(DrawContext context, int mouseX, int mouseY) {
        text(context, "Voting sites", left() + 18, top() + 84, primaryText());
        button(context, "Reset All", left() + panelWidth() - 118, top() + 76,
                100, 26, mouseX, mouseY, false);

        int listTop = top() + 112;
        int listBottom = top() + panelHeight() - 16;
        context.enableScissor(left() + 12, listTop, left() + panelWidth() - 12, listBottom);

        for (int i = 0; i < VoteSite.ALL.size(); i++) {
            int rowY = listTop + i * 29 - listScroll;
            if (rowY + 27 < listTop || rowY >= listBottom) {
                continue;
            }

            int statusColor = statusColor(i);
            context.fill(left() + 15, rowY, left() + panelWidth() - 15, rowY + 26,
                    fade(lightTheme ? 0xE8F4F7FA : 0xC51B2735));
            outline(context, left() + 15, rowY, panelWidth() - 30, 26, fade(statusColor));

            context.enableScissor(left() + 21, rowY, left() + panelWidth() - 248, rowY + 26);
            text(context, VoteSite.ALL.get(i).name(), left() + 23, rowY + 9, primaryText());
            context.disableScissor();

            String label = statusLabel(i);
            context.fill(left() + panelWidth() - 240, rowY + 4, left() + panelWidth() - 110, rowY + 22,
                    fade(lightTheme ? 0xFFDCE5EB : 0xB5212B38));
            text(context, label, left() + panelWidth() - 235, rowY + 9, statusColor);

            button(context, "Vote", left() + panelWidth() - 101, rowY + 3, 82, 20,
                    mouseX, mouseY, false);
        }

        context.disableScissor();
    }

    private void saveSettings() {
        mod.store().saveSettings(debug, lightTheme);
        feedback = "Settings saved.";
        modDebug(feedback);
    }

    private void modDebug(String message) {
        AkumaVoteClient.logStoreDebug(message);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing || click.button() != 0) {
            return super.mouseClicked(click, doubled);
        }

        double x = click.x();
        double y = click.y();

        if (inside(x, y, left(), top(), panelWidth(), 30)) {
            dragging = true;
            dragOffsetX = x - left();
            dragOffsetY = y - top();
            return true;
        }

        if (inside(x, y, left() + 14, top() + 42, 150, 25)) {
            tab = 0;
            return true;
        }

        if (inside(x, y, left() + 172, top() + 42, 136, 25)) {
            tab = 1;
            return true;
        }

        if (tab == 1) {
            if (inside(x, y, left() + 18, top() + 102, panelWidth() - 36, 32)) {
                debug = !debug;
                return true;
            }
            if (inside(x, y, left() + 18, top() + 196, 120, 32)) {
                lightTheme = !lightTheme;
                return true;
            }
            if (inside(x, y, left() + 18, top() + panelHeight() - 70, 120, 28)) {
                saveSettings();
                return true;
            }
            return super.mouseClicked(click, doubled);
        }

        if (inside(x, y, left() + panelWidth() - 118, top() + 76, 100, 26)) {
            mod.resetAllStatuses();
            listScroll = 0;
            return true;
        }

        int listTop = top() + 112;
        int listBottom = top() + panelHeight() - 16;
        if (inside(x, y, left() + 15, listTop, panelWidth() - 30, listBottom - listTop)) {
            int row = (int) (y - listTop + listScroll) / 29;
            if (row >= 0 && row < VoteSite.ALL.size()) {
                int rowY = listTop + row * 29 - listScroll;
                if (inside(x, y, left() + panelWidth() - 101, rowY + 3, 82, 20)) {
                    mod.openVoteSite(row);
                }
            }
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging && click.button() == 0) {
            panelX = Math.clamp((int) (click.x() - dragOffsetX), 0, Math.max(0, width - panelWidth()));
            panelY = Math.clamp((int) (click.y() - dragOffsetY), 0, Math.max(0, height - panelHeight()));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == 0) {
            int viewport = Math.max(1, panelHeight() - 128);
            listScroll = Math.clamp(listScroll - (int) (verticalAmount * 29), 0,
                    Math.max(0, VoteSite.ALL.size() * 29 - viewport));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 256) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        closing = true;
    }

    private int statusColor(int index) {
        return switch (mod.voteStatus(index)) {
            case NOT_VOTED -> COLOR_RED;
            case IN_PROGRESS -> COLOR_YELLOW;
            case CONFIRMED -> COLOR_GREEN;
            case UNAVAILABLE -> COLOR_GRAY;
        };
    }

    private String statusLabel(int index) {
        return switch (mod.voteStatus(index)) {
            case NOT_VOTED -> "Not Voted";
            case IN_PROGRESS -> "In Progress";
            case CONFIRMED -> "Voted";
            case UNAVAILABLE -> "Unavailable";
        };
    }

    private int primaryText() {
        return lightTheme ? 0xFF17212B : 0xFFE6EDF7;
    }

    private int secondaryText() {
        return lightTheme ? 0xFF5B6570 : 0xFF9BB1C8;
    }

    private int fade(int color) {
        return ((int) (((color >>> 24) & 255) * opacity) << 24) | (color & 0xFFFFFF);
    }

    private void text(DrawContext context, String value, int x, int y, int color) {
        context.drawTextWithShadow(textRenderer, value, x, y, fade(color));
    }

    private void outline(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void button(DrawContext context, String label, int x, int y, int w, int h,
                        int mouseX, int mouseY, boolean selected) {
        int accent = 0xFF5B9DFF;
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int fill = selected ? 0xD72A4561 : hovered ? 0xDE30465A : 0xC9253545;
        int edge = selected || hovered ? accent : 0xFF40536A;
        if (lightTheme) {
            fill = selected ? 0xFFD7E4F1 : hovered ? 0xFFE3EBF2 : 0xFFF2F5F8;
            edge = selected || hovered ? accent : 0xFFB8C2CC;
        }
        context.fill(x, y, x + w, y + h, fade(fill));
        outline(context, x, y, w, h, fade(edge));
        context.enableScissor(x + 4, y + 2, x + w - 4, y + h - 2);
        text(context, label, x + 8, y + (h - 9) / 2,
                lightTheme ? 0xFF17212B : 0xFFF1F6FA);
        context.disableScissor();
    }

    private boolean inside(double x, double y, int bx, int by, int bw, int bh) {
        return bw > 0 && bh > 0 && x >= bx && x < bx + bw && y >= by && y < by + bh;
    }
}
