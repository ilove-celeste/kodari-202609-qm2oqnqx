package dev.akumavote;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public final class VoteScreen extends Screen {
    private static final int COLOR_PURPLE = 0xFFB56CFF;
    private static final int COLOR_PURPLE_BRIGHT = 0xFFD9A7FF;
    private static final int COLOR_PURPLE_DARK = 0xFF2A163D;
    private static final int COLOR_PANEL = 0xF0140B1C;
    private static final int COLOR_ROW = 0xCC21142E;
    private static final int COLOR_ROW_HOVER = 0xDD2D1A3F;
    private static final int COLOR_TEXT = 0xFFF3E9FF;
    private static final int COLOR_SECONDARY = 0xFFBDA8D1;
    private static final int COLOR_RED = 0xFFFF6B81;
    private static final int COLOR_GREEN = 0xFF7EF0AE;
    private static final int COLOR_GRAY = 0xFF9589A4;

    private final AkumaVoteClient mod;
    private int panelX = -1;
    private int panelY = -1;
    private int listScroll;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean dragging;
    private boolean closing;
    private float opacity;
    private float hoverGlow;

    public VoteScreen(AkumaVoteClient mod) {
        super(Text.literal("AkumaVote"));
        this.mod = mod;
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

        context.fill(0, 0, width, height, fade(0xB3070510));
        context.fill(left(), top(), left() + panelWidth(), top() + panelHeight(), fade(COLOR_PANEL));
        outline(context, left(), top(), panelWidth(), panelHeight(), fade(0xFF5F367A));
        context.fill(left() + 1, top() + 1, left() + panelWidth() - 1, top() + 31,
                fade(hoverGlow > 0.05F ? 0xFF261133 : 0xFF1C0D28));
        context.fill(left() + 1, top() + 31, left() + panelWidth() - 1, top() + 33,
                fade(COLOR_PURPLE));

        text(context, "AKUMA VOTE", left() + 14, top() + 11, COLOR_TEXT);
        text(context, "play.akumamc.net", left() + panelWidth() - 136, top() + 11, COLOR_SECONDARY);

        renderVoting(context, mouseX, mouseY);
    }

    private void renderVoting(DrawContext context, int mouseX, int mouseY) {
        text(context, "Voting sites", left() + 18, top() + 62, COLOR_TEXT);
        button(context, "Reset All", left() + panelWidth() - 118, top() + 54,
                100, 26, mouseX, mouseY, false);

        int listTop = top() + 90;
        int listBottom = top() + panelHeight() - 16;
        context.enableScissor(left() + 12, listTop, left() + panelWidth() - 12, listBottom);

        for (int i = 0; i < VoteSite.ALL.size(); i++) {
            int rowY = listTop + i * 29 - listScroll;
            if (rowY + 27 < listTop || rowY >= listBottom) {
                continue;
            }

            int statusColor = statusColor(i);
            boolean hovered = inside(mouseX, mouseY, left() + 15, rowY,
                    panelWidth() - 30, 26);

            context.fill(left() + 15, rowY, left() + panelWidth() - 15, rowY + 26,
                    fade(hovered ? COLOR_ROW_HOVER : COLOR_ROW));
            outline(context, left() + 15, rowY, panelWidth() - 30, 26, fade(statusColor));

            context.enableScissor(left() + 21, rowY, left() + panelWidth() - 248, rowY + 26);
            text(context, VoteSite.ALL.get(i).name(), left() + 23, rowY + 9, COLOR_TEXT);
            context.disableScissor();

            String label = statusLabel(i);
            context.fill(left() + panelWidth() - 240, rowY + 4, left() + panelWidth() - 110, rowY + 22,
                    fade(COLOR_PURPLE_DARK));
            text(context, label, left() + panelWidth() - 235, rowY + 9, statusColor);

            button(context, "Vote", left() + panelWidth() - 101, rowY + 3, 82, 20,
                    mouseX, mouseY, false);
        }

        context.disableScissor();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing || click.button() != 0) {
            return super.mouseClicked(click, doubled);
        }

        double x = click.x();
        double y = click.y();

        if (inside(x, y, left(), top(), panelWidth(), 33)) {
            dragging = true;
            dragOffsetX = x - left();
            dragOffsetY = y - top();
            return true;
        }

        if (inside(x, y, left() + panelWidth() - 118, top() + 54, 100, 26)) {
            mod.resetAllStatuses();
            listScroll = 0;
            return true;
        }

        int listTop = top() + 90;
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
        int listViewport = Math.max(1, panelHeight() - 106);
        listScroll = Math.clamp(listScroll - (int) (verticalAmount * 29), 0,
                Math.max(0, VoteSite.ALL.size() * 29 - listViewport));
        return true;
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
            case CONFIRMED -> COLOR_GREEN;
            case UNAVAILABLE -> COLOR_GRAY;
        };
    }

    private String statusLabel(int index) {
        return switch (mod.voteStatus(index)) {
            case NOT_VOTED -> "Not Voted";
            case CONFIRMED -> "Voted";
            case UNAVAILABLE -> "Unavailable";
        };
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
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int fill = selected ? 0xFF43215B : hovered ? 0xFF4B2665 : 0xFF321844;
        int edge = selected || hovered ? COLOR_PURPLE_BRIGHT : 0xFF694B7D;
        context.fill(x, y, x + w, y + h, fade(fill));
        outline(context, x, y, w, h, fade(edge));
        context.enableScissor(x + 4, y + 2, x + w - 4, y + h - 2);
        text(context, label, x + 8, y + (h - 9) / 2, COLOR_TEXT);
        context.disableScissor();
    }

    private boolean inside(double x, double y, int bx, int by, int bw, int bh) {
        return bw > 0 && bh > 0 && x >= bx && x < bx + bw && y >= by && y < by + bh;
    }
}
