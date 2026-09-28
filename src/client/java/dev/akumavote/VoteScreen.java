package dev.akumavote;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public final class VoteScreen extends Screen {
    private static final int COLOR_RED = 0xFFFF596B;
    private static final int COLOR_GREEN = 0xFF4FE18D;
    private static final int COLOR_YELLOW = 0xFFFFD166;
    private static final int COLOR_GRAY = 0xFF9AA6B2;
    private final AkumaVoteClient mod;
    private TextFieldWidget nickname;
    private int panelX = -1;
    private int panelY = -1;
    private int tab = 1;
    private int listScroll;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean debug;
    private boolean dragging;
    private boolean draggingSlider;
    private boolean closing;
    private float opacity;
    private float hoverGlow;
    private int delay;
    private String feedback = "";

    public VoteScreen(AkumaVoteClient mod) {
        super(Text.literal("AkumaVote"));
        this.mod = mod;
        debug = mod.store().debug();
        delay = Math.clamp(mod.store().delay(), 1, 60);
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
        nickname = addDrawableChild(new TextFieldWidget(textRenderer, left() + 18, top() + 103,
                panelWidth() - 36, 22, Text.literal("Ник Minecraft")));
        nickname.setMaxLength(16);
        nickname.setText(mod.store().nickname());
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
        context.fill(0, 0, width, height, fade(0xB805090F));
        context.fill(left(), top(), left() + panelWidth(), top() + panelHeight(), fade(0xF0101722));
        outline(context, left(), top(), panelWidth(), panelHeight(), fade(0xFF34465B));
        context.fill(left() + 1, top() + 1, left() + panelWidth() - 1, top() + 30,
                fade(hoverGlow > 0.05F ? 0xFF26394C : 0xFF1A2635));
        context.fill(left() + 1, top() + 30, left() + panelWidth() - 1, top() + 32, fade(0xFF5B9DFF));
        text(context, "AKUMA VOTE", left() + 14, top() + 11, 0xFFFFFFFF);
        text(context, "play.akumamc.net", left() + panelWidth() - 136, top() + 11, 0xFF9AADC1);
        button(context, tab == 0 ? "Настройки  •" : "Настройки", left() + 14, top() + 42, 136, 25,
                mouseX, mouseY, tab == 0);
        button(context, tab == 1 ? "Голосование  •" : "Голосование", left() + 158, top() + 42, 150, 25,
                mouseX, mouseY, tab == 1);
        if (tab == 0) {
            renderSettings(context, mouseX, mouseY);
        } else {
            renderVoting(context, mouseX, mouseY);
        }
        nickname.visible = tab == 0 && !closing;
        nickname.setPosition(left() + 18, top() + 103);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void renderSettings(DrawContext context, int mouseX, int mouseY) {
        text(context, "Ник Minecraft", left() + 18, top() + 83, 0xFFE6EDF7);
        text(context, "Задержка (сек.)", left() + 18, top() + 140, 0xFFE6EDF7);
        text(context, "1", left() + 18, top() + 174, 0xFF91A6BC);
        text(context, "60", left() + panelWidth() - 35, top() + 174, 0xFF91A6BC);
        int sliderX = left() + 42;
        int sliderWidth = Math.max(1, panelWidth() - 84);
        int thumb = sliderX + sliderWidth * (delay - 1) / 59;
        context.fill(sliderX, top() + 178, sliderX + sliderWidth, top() + 183, fade(0xFF344456));
        context.fill(sliderX, top() + 178, thumb, top() + 183, fade(0xFF5B9DFF));
        context.fill(thumb - 4, top() + 173, thumb + 5, top() + 188, fade(0xFF5B9DFF));
        outline(context, left() + 18, top() + 207, 16, 16, fade(debug ? 0xFF5B9DFF : 0xFF718095));
        if (debug) {
            context.fill(left() + 22, top() + 211, left() + 30, top() + 219, fade(0xFF5B9DFF));
        }
        text(context, "Debug: сообщения в чате и logs/akumavote.log", left() + 44, top() + 211, 0xFFE6EDF7);
        button(context, "Сохранить", left() + 18, top() + panelHeight() - 70, 140, 28,
                mouseX, mouseY, true);
        text(context, feedback, left() + 18, top() + panelHeight() - 31, 0xFF9BB1C8);
    }

    private void renderVoting(DrawContext context, int mouseX, int mouseY) {
        text(context, "Голосование внутри Minecraft", left() + 18, top() + 84, 0xFFE6EDF7);
        button(context, "Отметить все как не голосованные", left() + panelWidth() - 238, top() + 76,
                220, 26, mouseX, mouseY, false);
        int listTop = top() + 112;
        int listBottom = top() + panelHeight() - 16;
        context.enableScissor(left() + 12, listTop, left() + panelWidth() - 12, listBottom);
        for (int i = 0; i < VoteSite.ALL.size(); i++) {
            int rowY = listTop + i * 29 - listScroll;
            if (rowY + 27 < listTop || rowY >= listBottom) {
                continue;
            }
            int statusColor = statusColor(i);
            context.fill(left() + 15, rowY, left() + panelWidth() - 15, rowY + 26, fade(0xC51B2735));
            outline(context, left() + 15, rowY, panelWidth() - 30, 26, fade(statusColor));
            context.enableScissor(left() + 21, rowY, left() + panelWidth() - 248, rowY + 26);
            text(context, VoteSite.ALL.get(i).name(), left() + 23, rowY + 9, 0xFFE8EDF5);
            context.disableScissor();
            String label = statusLabel(i);
            context.fill(left() + panelWidth() - 240, rowY + 4, left() + panelWidth() - 110, rowY + 22,
                    fade(0xB5212B38));
            text(context, label, left() + panelWidth() - 235, rowY + 9, statusColor);
            button(context, "Голосовать", left() + panelWidth() - 101, rowY + 3, 82, 20,
                    mouseX, mouseY, false);
        }
        context.disableScissor();
    }

    private void saveSettings() {
        String name = nickname.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            feedback = "Введите ник: 3–16 латинских букв, цифр или _.";
            modDebug(feedback);
            return;
        }
        mod.store().saveSettings(name, delay, debug);
        feedback = "Настройки сохранены.";
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
        if (inside(x, y, left() + 14, top() + 42, 136, 25)) {
            tab = 0;
            return true;
        }
        if (inside(x, y, left() + 158, top() + 42, 150, 25)) {
            tab = 1;
            nickname.setFocused(false);
            return true;
        }
        if (tab == 0) {
            if (inside(x, y, left() + 34, top() + 168, panelWidth() - 68, 28)) {
                draggingSlider = true;
                moveSlider(x);
                return true;
            }
            if (inside(x, y, left() + 18, top() + 202, panelWidth() - 36, 26)) {
                debug = !debug;
                return true;
            }
            if (inside(x, y, left() + 18, top() + panelHeight() - 70, 140, 28)) {
                saveSettings();
                return true;
            }
            return super.mouseClicked(click, doubled);
        }
        if (inside(x, y, left() + panelWidth() - 238, top() + 76, 220, 26)) {
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
        if (draggingSlider && click.button() == 0) {
            moveSlider(click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && (dragging || draggingSlider)) {
            dragging = false;
            draggingSlider = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == 1) {
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
            case NOT_VOTED -> "Не голосовал";
            case IN_PROGRESS -> "В процессе";
            case CONFIRMED -> "Голос подтверждён";
            case UNAVAILABLE -> "Недоступно";
        };
    }

    private void moveSlider(double x) {
        int sliderX = left() + 42;
        int sliderWidth = Math.max(1, panelWidth() - 84);
        delay = 1 + (int) Math.round(Math.clamp((x - sliderX) / sliderWidth, 0.0, 1.0) * 59);
    }

    private boolean inside(double x, double y, int bx, int by, int bw, int bh) {
        return bw > 0 && bh > 0 && x >= bx && x < bx + bw && y >= by && y < by + bh;
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
        button(context, label, x, y, w, h, mouseX, mouseY, selected, 0xFF5B9DFF);
    }

    private void button(DrawContext context, String label, int x, int y, int w, int h,
                        int mouseX, int mouseY, boolean selected, int accent) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        context.fill(x, y, x + w, y + h, fade(selected ? 0xD72A4561 : hovered ? 0xDE30465A : 0xC9253545));
        outline(context, x, y, w, h, fade(selected || hovered ? accent : 0xFF40536A));
        context.enableScissor(x + 4, y + 2, x + w - 4, y + h - 2);
        text(context, label, x + 8, y + (h - 9) / 2, 0xFFF1F6FA);
        context.disableScissor();
    }
}