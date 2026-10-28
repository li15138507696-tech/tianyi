package net.guzhenren.tianyi.journal;

/** Pixel-aligned book layout, shared by drawing, hit-testing and offline checks. */
public record JournalLayout(boolean compact, Rect panel, Rect tabs, Rect toolbar,
                            Rect list, Rect detail, Rect actions, Rect footer) {
    public record Rect(int x, int y, int w, int h) {
        public int right() { return x + w; }
        public int bottom() { return y + h; }
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < right() && py < bottom();
        }
        public boolean encloses(Rect other) {
            return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
        }
    }

    public static JournalLayout of(int width, int height) {
        int margin = width < 480 || height < 280 ? 6 : 16;
        int w = Math.max(1, Math.min(860, width - margin * 2));
        int h = Math.max(1, Math.min(480, height - margin * 2));
        int x = Math.max(0, (width - w) / 2);
        int y = Math.max(0, (height - h) / 2);
        Rect panel = new Rect(x, y, w, h);
        boolean compact = w < 540;
        int footerY = y + h - 20;
        Rect footer = new Rect(x + 12, footerY, Math.max(1, w - 24), 14);
        int tabW = compact ? w - 24 : 62;
        Rect tabs = new Rect(x + 12, y + 48, Math.max(1, tabW), compact ? 25 : Math.max(1, h - 80));
        int listX = compact ? x + 12 : x + 86;
        int listW = compact ? w - 24 : Math.max(156, Math.min(230, (w - 110) / 3));
        int toolbarY = compact ? tabs.bottom() + 3 : y + 52;
        Rect toolbar = new Rect(listX, toolbarY, Math.max(1, listW), 22);
        int listY = toolbar.bottom() + (compact ? 3 : 7);
        Rect list = new Rect(listX, listY, Math.max(1, listW), Math.max(1, footerY - 8 - listY));
        int detailX = compact ? listX : list.right() + 18;
        int detailW = compact ? listW : x + w - 16 - detailX;
        Rect actions = new Rect(detailX, footerY - 27, Math.max(1, detailW), 21);
        int detailY = compact ? listY : toolbarY;
        Rect detail = new Rect(detailX, detailY, Math.max(1, detailW), Math.max(1, actions.y - 8 - detailY));
        return new JournalLayout(compact, panel, tabs, toolbar, list, detail, actions, footer);
    }

    public static int clampScroll(int requested, int count, int visible) {
        return Math.max(0, Math.min(requested, Math.max(0, count - Math.max(1, visible))));
    }
}
