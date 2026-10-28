package net.guzhenren.tianyi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Native focusable book button. All coordinates stay in GUI pixels. */
final class JournalButton extends Button {
    private final boolean tab;
    private boolean selected;

    JournalButton(int x, int y, int w, int h, String label, boolean tab, OnPress press) {
        super(x, y, w, h, Component.literal(label), press, DEFAULT_NARRATION);
        this.tab = tab;
    }
    void selected(boolean value) { selected = value; }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = isHoveredOrFocused() && active;
        int line = selected || hover ? JournalScreen.GOLD : JournalScreen.LINE;
        g.fill(x, y, x + w, y + h, selected ? 0xFF302C22 : hover ? 0xFF292C27 : 0xFF20231F);
        if (tab) {
            g.fill(x, y, x + 2, y + h, line);
            if (selected) g.fill(x + 4, y + h - 1, x + w, y + h, 0xFF62543A);
        } else {
            g.fill(x, y, x + w, y + 1, line);
            g.fill(x, y + h - 1, x + w, y + h, line);
            g.fill(x, y, x + 1, y + h, line);
            g.fill(x + w - 1, y, x + w, y + h, line);
        }
        if (isFocused()) g.fill(x + 3, y + 3, x + w - 3, y + 4, JournalScreen.GOLD);
        var font = Minecraft.getInstance().font;
        String fit = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, w - 8));
        int color = !active ? JournalScreen.MUTED : selected || hover ? JournalScreen.GOLD : JournalScreen.BONE;
        g.drawString(font, fit, x + (w - font.width(fit)) / 2, y + (h - 9) / 2, color, false);
    }
}
