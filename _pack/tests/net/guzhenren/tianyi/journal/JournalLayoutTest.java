package net.guzhenren.tianyi.journal;

/** Responsive layout and hit-testing, independent of Minecraft/OpenGL. */
public final class JournalLayoutTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int[][] screens = {{240,180}, {320,240}, {426,240}, {640,360}, {960,540}, {1920,1080}};
        for (int[] screen : screens) {
            JournalLayout l = JournalLayout.of(screen[0], screen[1]);
            for (JournalLayout.Rect r : new JournalLayout.Rect[]{l.panel(), l.tabs(), l.toolbar(), l.list(), l.detail(), l.actions(), l.footer()}) {
                check(r.w() > 0 && r.h() > 0, "positive region on " + screen[0]);
                check(r.x() >= 0 && r.y() >= 0 && r.right() <= screen[0] && r.bottom() <= screen[1], "region inside screen");
                check(l.panel().encloses(r), "region inside book");
                check(r.contains(r.x(), r.y()), "top-left is clickable");
                check(!r.contains(r.right(), r.y()), "right edge belongs to next region");
                check(!r.contains(r.x() - 1, r.y()), "outside stays outside");
            }
            check(l.detail().bottom() <= l.actions().y(), "text cannot overlap action buttons");
            check(l.detail().h() >= 11, "even the smallest supported viewport fits a text line");
            check(l.actions().bottom() <= l.footer().y(), "actions cannot overlap footer");
            check(l.actions().w() >= 150, "three readable actions fit");
            if (!l.compact()) check(l.list().right() < l.detail().x(), "wide columns do not overlap");
        }
        check(JournalLayout.of(320,240).compact(), "small window uses master/detail");
        check(!JournalLayout.of(960,540).compact(), "wide window shows both panes");
        check(JournalLayout.clampScroll(99, 3, 6) == 0, "short content cannot overscroll");
        check(JournalLayout.clampScroll(99, 30, 6) == 24, "long content clamps at last complete page");
        check(JournalLayout.clampScroll(-8, 30, 6) == 0, "negative scrolling clamps");
        check(JournalLayout.clampScroll(5, 0, 0) == 0, "empty content has no scroll");
        System.out.println("JournalLayoutTest: " + checks + " checks passed");
    }
}
