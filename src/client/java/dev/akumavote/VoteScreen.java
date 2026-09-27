package dev.akumavote;

import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public final class VoteScreen extends Screen {
    private static final int[] ACCENTS = {0xFF52A9FF, 0xFFB083FF, 0xFF45D1AB};
    private static final String[] THEMES = {"Azure", "Amethyst", "Mint"};
    private final AkumaVoteClient mod;
    private TextFieldWidget nickname;
    private TextFieldWidget delay;
    private int panelX = -1;
    private int panelY = -1;
    private int tab;
    private int theme;
    private int mainScroll;
    private int manualScroll;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean debug;
    private boolean manual;
    private boolean dragging;
    private boolean draggingSlider;
    private boolean closing;
    private float opacity;
    private float hoverGlow;
    private String feedback = "Голосование доступно только на play.akumamc.net";

    public VoteScreen(AkumaVoteClient mod) {
        super(Text.literal("AkumaVote"));
        this.mod = mod;
        theme = mod.store().theme();
        debug = mod.store().debug();
    }

    private int panelWidth() {
        return Math.min(500, width - 12);
    }

    private int panelHeight() {
        return Math.min(450, height - 12);
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
        nickname = addDrawableChild(new TextFieldWidget(textRenderer, left() + 173, top() + 92,
                Math.max(60, panelWidth() - 192), 20, Text.literal("Ваш ник в Minecraft")));
        nickname.setMaxLength(16);
        nickname.setText(mod.store().nickname());
        delay = addDrawableChild(new TextFieldWidget(textRenderer, left() + 173, top() + 128,
                Math.max(60, panelWidth() - 192), 20, Text.literal("Задержка между голосованиями (сек)")));
        delay.setMaxLength(4);
        delay.setText(Integer.toString(mod.store().delay()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (!mod.isConnected()) {
            client.setScreen(null);
            return;
        }
        float step = Math.min(1.0F, deltaTicks * 0.14F);
        opacity = Math.clamp(opacity + (closing ? -step : step), 0.0F, 1.0F);
        hoverGlow += ((inside(mouseX, mouseY, left(), top(), panelWidth(), 30) ? 1.0F : 0.0F) - hoverGlow) * step;
        if (closing && opacity == 0.0F) {
            super.close();
            return;
        }
        context.fill(0, 0, width, height, fade(0x9D030910));
        context.fill(left(), top(), left() + panelWidth(), top() + panelHeight(), fade(0xED121925));
        outline(context, left(), top(), panelWidth(), panelHeight(), fade(0xFF354357));
        context.fill(left() + 1, top() + 1, left() + panelWidth() - 1, top() + 30,
                fade(hoverGlow > 0.05F ? 0xFF25394D : 0xFF1B2736));
        context.fill(left() + 1, top() + 30, left() + panelWidth() - 1, top() + 32, fade(ACCENTS[theme]));
        text(context, "AKUMA VOTE", left() + 12, top() + 11, 0xFFFFFFFF);
        text(context, "play.akumamc.net", left() + panelWidth() - 132, top() + 11, 0xFF9CAFC4);
        button(context, tab == 0 ? "Настройки ●" : "Настройки", left() + 12, top() + 42, 128, 24,
                mouseX, mouseY, tab == 0);
        button(context, tab == 1 ? "Голосование ●" : "Голосование", left() + 148, top() + 42, 148, 24,
                mouseX, mouseY, tab == 1);
        if (tab == 0) {
            renderSettings(context, mouseX, mouseY);
        } else {
            renderVoting(context, mouseX, mouseY);
        }
        nickname.visible = tab == 0 && !manual && !closing;
        delay.visible = tab == 0 && !manual && !closing;
        nickname.setPosition(left() + 173, top() + 92);
        delay.setPosition(left() + 173, top() + 128);
        super.render(context, mouseX, mouseY, deltaTicks);
        if (manual) {
            renderManual(context, mouseX, mouseY);
        }
    }

    private void renderSettings(DrawContext context, int mouseX, int mouseY) {
        text(context, "Ваш ник в Minecraft", left() + 16, top() + 98, 0xFFE6EDF7);
        text(context, "Задержка (сек)", left() + 16, top() + 134, 0xFFE6EDF7);
        text(context, "1", left() + 16, top() + 171, 0xFF93A9C0);
        text(context, "60", left() + panelWidth() - 33, top() + 171, 0xFF93A9C0);
        int sliderX = left() + 38;
        int sliderWidth = panelWidth() - 81;
        int value = sliderValue();
        context.fill(sliderX, top() + 174, sliderX + sliderWidth, top() + 179, fade(0xFF344456));
        context.fill(sliderX, top() + 174, sliderX + sliderWidth * (value - 1) / 59,
                top() + 179, fade(ACCENTS[theme]));
        int thumb = sliderX + sliderWidth * (value - 1) / 59;
        context.fill(thumb - 3, top() + 170, thumb + 4, top() + 184, fade(ACCENTS[theme]));
        outline(context, left() + 16, top() + 200, 15, 15, fade(ACCENTS[theme]));
        if (debug) {
            context.fill(left() + 19, top() + 203, left() + 28, top() + 212, fade(ACCENTS[theme]));
        }
        text(context, "Debug: подробные сообщения в чате", left() + 39, top() + 204, 0xFFE6EDF7);
        text(context, "Цветовая тема", left() + 16, top() + 242, 0xFFE6EDF7);
        button(context, THEMES[theme] + "  ▾", left() + 173, top() + 233,
                Math.max(60, panelWidth() - 192), 24, mouseX, mouseY, false);
        button(context, "Сохранить", left() + 16, top() + panelHeight() - 78, 130, 26,
                mouseX, mouseY, true);
        text(context, feedback, left() + 16, top() + panelHeight() - 39, 0xFF9BB1C8);
    }

    private void renderVoting(DrawContext context, int mouseX, int mouseY) {
        int half = (panelWidth() - 40) / 2;
        button(context, mod.service().isRunning() ? "Остановить" : "Автоматическое голосование",
                left() + 12, top() + 80, half, 26, mouseX, mouseY, true);
        button(context, "Ручное голосование", left() + 28 + half, top() + 80, half, 26,
                mouseX, mouseY, false);
        text(context, "Сайты для голосования  ·  12", left() + 16, top() + 120, 0xFFE6EDF7);
        int listTop = top() + 141;
        int listBottom = top() + panelHeight() - 77;
        if (listBottom > listTop) {
            context.enableScissor(left() + 12, listTop, left() + panelWidth() - 12, listBottom);
            for (int i = 0; i < VoteSite.ALL.size(); i++) {
                int rowY = listTop + i * 19 - mainScroll;
                if (rowY + 18 < listTop || rowY >= listBottom) {
                    continue;
                }
                context.fill(left() + 15, rowY, left() + panelWidth() - 15, rowY + 17,
                        fade(i % 2 == 0 ? 0xB0263546 : 0xB01C2939));
                text(context, String.format("%02d", i + 1) + "  " + VoteSite.ALL.get(i).name(),
                        left() + 22, rowY + 4, 0xFFE6EDF7);
                text(context, mod.store().isVoted(i) ? "✓" : "○", left() + panelWidth() - 35,
                        rowY + 4, mod.store().isVoted(i) ? 0xFF5DE59B : 0xFFFF7582);
            }
            context.disableScissor();
        }
        int logTop = top() + panelHeight() - 72;
        context.fill(left() + 12, logTop, left() + panelWidth() - 12, top() + panelHeight() - 12,
                fade(0xE709101B));
        text(context, "КОНСОЛЬ  " + (mod.service().isRunning() ? "● ВЫПОЛНЯЕТСЯ" : "○ ОЖИДАНИЕ"),
                left() + 18, logTop + 5, ACCENTS[theme]);
        List<String> logs = mod.service().logs();
        context.enableScissor(left() + 17, logTop + 18, left() + panelWidth() - 17,
                top() + panelHeight() - 15);
        for (int i = Math.max(0, logs.size() - 3); i < logs.size(); i++) {
            text(context, "> " + logs.get(i), left() + 18, logTop + 18 + (i - Math.max(0, logs.size() - 3)) * 11,
                    0xFF9CB4CA);
        }
        context.disableScissor();
    }

    private void renderManual(DrawContext context, int mouseX, int mouseY) {
        context.fill(left() + 1, top() + 32, left() + panelWidth() - 1, top() + panelHeight() - 1,
                fade(0xFA101925));
        text(context, "РУЧНОЕ ГОЛОСОВАНИЕ", left() + 16, top() + 47, ACCENTS[theme]);
        button(context, "×", left() + panelWidth() - 43, top() + 41, 27, 23, mouseX, mouseY, false);
        int listTop = top() + 72;
        int listBottom = top() + panelHeight() - 42;
        context.enableScissor(left() + 12, listTop, left() + panelWidth() - 12, listBottom);
        for (int i = 0; i < VoteSite.ALL.size(); i++) {
            int rowY = listTop + i * 24 - manualScroll;
            if (rowY + 22 < listTop || rowY >= listBottom) {
                continue;
            }
            boolean voted = mod.store().isVoted(i);
            int border = voted ? 0xFF52D991 : 0xFFFF6175;
            context.fill(left() + 16, rowY, left() + panelWidth() - 16, rowY + 22, fade(0xE9233142));
            outline(context, left() + 16, rowY, panelWidth() - 32, 22, fade(border));
            context.enableScissor(left() + 20, rowY, left() + panelWidth() - 105, rowY + 22);
            text(context, VoteSite.ALL.get(i).name(), left() + 23, rowY + 7, 0xFFE8EDF5);
            context.disableScissor();
            context.fill(left() + panelWidth() - 100, rowY + 2, left() + panelWidth() - 18, rowY + 20,
                    fade(voted ? 0xCC26634B : 0xCC6B2B3A));
            text(context, voted ? "Учтён ✓" : "Отправить", left() + panelWidth() - 95,
                    rowY + 6, 0xFFFFFFFF);
        }
        context.disableScissor();
        List<String> logs = mod.service().logs();
        String status = logs.isEmpty() ? "Нажмите сайт, чтобы отправить голос из игры." : logs.get(logs.size() - 1);
        context.enableScissor(left() + 16, top() + panelHeight() - 31, left() + panelWidth() - 16,
                top() + panelHeight() - 12);
        text(context, status, left() + 16, top() + panelHeight() - 27, 0xFF9CB4CA);
        context.disableScissor();
    }

    private void saveSettings() {
        String name = nickname.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            feedback = "Введите ник: 3–16 латинских букв, цифр или _.";
            mod.service().log(feedback);
            return;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(delay.getText().trim());
        } catch (NumberFormatException e) {
            feedback = "Задержка должна быть числом от 1 до 3600.";
            mod.service().log(feedback);
            return;
        }
        if (seconds < 1 || seconds > 3600) {
            feedback = "Задержка должна быть числом от 1 до 3600.";
            mod.service().log(feedback);
            return;
        }
        mod.store().saveSettings(name, seconds, debug, theme);
        feedback = "Настройки сохранены.";
        mod.service().log(feedback);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing || click.button() != 0) {
            return super.mouseClicked(click, doubled);
        }
        double x = click.x();
        double y = click.y();
        if (manual) {
            if (inside(x, y, left() + panelWidth() - 43, top() + 41, 27, 23)) {
                manual = false;
                return true;
            }
            int listTop = top() + 72;
            int listBottom = top() + panelHeight() - 42;
            if (inside(x, y, left() + 16, listTop, panelWidth() - 32, listBottom - listTop)) {
                int row = (int) (y - listTop + manualScroll) / 24;
                if (row >= 0 && row < VoteSite.ALL.size()) {
                    mod.service().voteManually(row);
                }
            }
            return true;
        }
        if (inside(x, y, left(), top(), panelWidth(), 30)) {
            dragging = true;
            dragOffsetX = x - left();
            dragOffsetY = y - top();
            return true;
        }
        if (inside(x, y, left() + 12, top() + 42, 128, 24)) {
            tab = 0;
            return true;
        }
        if (inside(x, y, left() + 148, top() + 42, 148, 24)) {
            tab = 1;
            nickname.setFocused(false);
            delay.setFocused(false);
            return true;
        }
        if (tab == 0) {
            if (inside(x, y, left() + 38, top() + 164, panelWidth() - 81, 23)) {
                draggingSlider = true;
                moveSlider(x);
                return true;
            }
            if (inside(x, y, left() + 16, top() + 197, panelWidth() - 32, 23)) {
                debug = !debug;
                return true;
            }
            if (inside(x, y, left() + 173, top() + 233, panelWidth() - 192, 24)) {
                theme = (theme + 1) % THEMES.length;
                return true;
            }
            if (inside(x, y, left() + 16, top() + panelHeight() - 78, 130, 26)) {
                saveSettings();
                return true;
            }
            return super.mouseClicked(click, doubled);
        }
        int half = (panelWidth() - 40) / 2;
        if (inside(x, y, left() + 12, top() + 80, half, 26)) {
            if (mod.service().isRunning()) {
                mod.service().cancel();
            } else {
                mod.service().start();
            }
            return true;
        }
        if (inside(x, y, left() + 28 + half, top() + 80, half, 26)) {
            manual = true;
            return true;
        }
        int listTop = top() + 141;
        int listBottom = top() + panelHeight() - 77;
        if (inside(x, y, left() + 15, listTop, panelWidth() - 30, listBottom - listTop)) {
            int row = (int) (y - listTop + mainScroll) / 19;
            if (row >= 0 && row < VoteSite.ALL.size()) {
                manual = true;
                manualScroll = Math.max(0, row * 24 - 24);
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
        if (manual) {
            int viewport = Math.max(1, panelHeight() - 114);
            manualScroll = Math.clamp(manualScroll - (int) (verticalAmount * 22), 0,
                    Math.max(0, VoteSite.ALL.size() * 24 - viewport));
            return true;
        }
        if (tab == 1) {
            int viewport = Math.max(1, panelHeight() - 218);
            mainScroll = Math.clamp(mainScroll - (int) (verticalAmount * 19), 0,
                    Math.max(0, VoteSite.ALL.size() * 19 - viewport));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 256) {
            if (manual) {
                manual = false;
            } else {
                close();
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        closing = true;
    }

    private void moveSlider(double x) {
        int sliderX = left() + 38;
        int sliderWidth = Math.max(1, panelWidth() - 81);
        int seconds = 1 + (int) Math.round(Math.clamp((x - sliderX) / sliderWidth, 0.0, 1.0) * 59);
        delay.setText(Integer.toString(seconds));
    }

    private int sliderValue() {
        try {
            return Math.clamp(Integer.parseInt(delay.getText()), 1, 60);
        } catch (NumberFormatException e) {
            return 1;
        }
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
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        context.fill(x, y, x + w, y + h, fade(selected ? 0xCC294563 : hovered ? 0xDD30465B : 0xCC263446));
        outline(context, x, y, w, h, fade(selected || hovered ? ACCENTS[theme] : 0xFF40536A));
        context.enableScissor(x + 4, y + 2, x + w - 4, y + h - 2);
        text(context, label, x + 9, y + (h - 9) / 2, 0xFFF1F6FA);
        context.disableScissor();
    }
}