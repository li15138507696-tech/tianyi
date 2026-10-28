package net.guzhenren.tianyi.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.guzhenren.tianyi.journal.JournalLayout;
import net.guzhenren.tianyi.journal.JournalLayout.Rect;
import net.guzhenren.tianyi.net.JournalActionPayload;
import net.guzhenren.tianyi.net.JournalNet;
import net.guzhenren.tianyi.net.JournalSyncPayload;
import net.guzhenren.tianyi.net.EncounterActionPayload;
import net.guzhenren.tianyi.net.EncounterSyncPayload;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** A player's evidence book, showing only the server-filtered personal journal. */
public final class JournalScreen extends Screen {
    static final int INK = 0xFF1B1D1B, BONE = 0xFFD8D0BA, GOLD = 0xFFC0A363;
    static final int TEAL = 0xFF91A29B, RED = 0xFFC7806D, MUTED = 0xFF7E8377, LINE = 0xFF464B3F;
    private static final int ROW_H = 42, LINE_H = 13;
    private static final String ENCOUNTER_ROW = "\u0000current_encounter";
    private static final String BATTLE_RULES = "挑战限时180秒。妖兽死亡时，本人须存活且在机缘32格内。死亡、退出游戏、切换维度、离开生存模式、认输或超时均失败；距机缘超过64格持续5秒也会失败。";
    private enum Tab {
        SIGHT("观象", "见天地之变", "亲历之后，再来翻阅此卷。", 0),
        CASE("察局", "循迹而辨局", "将已有见闻记入疑局，再比对其中的联系。", -1),
        KNOW("藏识", "以实证存识", "经得起核实的认识，才值得留在卷中。", 1),
        TRACE("行藏", "知行止之迹", "尚无痕迹记载，不代表无人知晓你的行踪。", 2);
        final String name, subtitle, empty;
        final int kind;
        Tab(String name, String subtitle, String empty, int kind) {
            this.name = name; this.subtitle = subtitle; this.empty = empty; this.kind = kind;
        }
    }
    private record TextLine(FormattedCharSequence text, int color) {}
    private JournalLayout book;
    private Tab tab = Tab.SIGHT;
    private String selectedId = "", query = "";
    private boolean favoritesOnly, detailOpen, draggingList, draggingDetail;
    private int listScroll, detailScroll, detailCount;
    private List<JournalSyncPayload.Row> lastSnapshot;
    private EncounterSyncPayload lastEncounter;
    private EditBox search;
    private JournalButton favoriteFilter, back, favorite, doubtful, caseButton;
    private final List<JournalButton> tabButtons = new ArrayList<>();
    private long pendingUntil;

    public JournalScreen() { super(Component.literal("见闻录")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

    @Override
    protected void init() {
        book = JournalLayout.of(width, height);
        tabButtons.clear();
        Rect tabs = book.tabs();
        for (int i = 0; i < Tab.values().length; i++) {
            Tab target = Tab.values()[i];
            int w = book.compact() ? (tabs.w() - 9) / 4 : tabs.w();
            int x = tabs.x() + (book.compact() ? i * (w + 3) : 0);
            int y = tabs.y() + (book.compact() ? 0 : i * 42);
            tabButtons.add(addRenderableWidget(new JournalButton(x, y, w,
                    book.compact() ? 25 : 34, target.name, true, b -> changeTab(target))));
        }
        Rect p = book.panel(), tools = book.toolbar();
        addRenderableWidget(new JournalButton(p.right() - 31, p.y() + 14, 18, 18, "×", false, b -> onClose()));
        search = new EditBox(font, tools.x() + 7, tools.y() + 7, tools.w() - 58, 12,
                Component.literal("查阅当前卷"));
        search.setBordered(false);
        search.setTextColor(BONE);
        search.setTextColorUneditable(MUTED);
        search.setMaxLength(64);
        search.setHint(Component.literal("查阅此卷…"));
        search.setValue(query);
        search.setResponder(value -> { query = value; listScroll = detailScroll = 0; reconcileSelection(); });
        addRenderableWidget(search);
        favoriteFilter = addRenderableWidget(new JournalButton(tools.right() - 44, tools.y(), 44, 22,
                "收藏", false, b -> {
                    favoritesOnly = !favoritesOnly;
                    listScroll = detailScroll = 0;
                    reconcileSelection();
                }));
        back = addRenderableWidget(new JournalButton(tools.x(), tools.y(), 70, 22,
                "‹ 返回卷目", false, b -> { detailOpen = false; clearFocus(); }));
        Rect a = book.actions();
        int bw = (a.w() - 8) / 3;
        favorite = actionButton(a.x(), a.y(), bw, "收藏", JournalActionPayload.Action.FAVORITE);
        doubtful = actionButton(a.x() + bw + 4, a.y(), bw, "存疑", JournalActionPayload.Action.DOUBTFUL);
        caseButton = actionButton(a.x() + 2 * (bw + 4), a.y(), a.w() - 2 * (bw + 4),
                "记入疑局", JournalActionPayload.Action.TOGGLE_CASE);
        reconcileSelection();
        updateWidgets();
        clearFocus();
    }

    private JournalButton actionButton(int x, int y, int w, String text, JournalActionPayload.Action action) {
        return addRenderableWidget(new JournalButton(x, y, w, 21, text, false, b -> {
            var row = selected();
            if (row != null) {
                if (isEncounter(row)) {
                    encounterButton(action);
                    return;
                }
                pendingUntil = System.currentTimeMillis() + 1000;
                JournalNet.send(action, row.id());
            }
        }));
    }

    private void changeTab(Tab next) {
        tab = next; selectedId = ""; listScroll = detailScroll = 0; detailOpen = false;
        query = ""; search.setValue(""); reconcileSelection(); clearFocus();
    }

    private List<JournalSyncPayload.Row> rows() {
        List<JournalSyncPayload.Row> result = new ArrayList<>();
        String term = query.strip().toLowerCase(Locale.ROOT);
        for (var row : JournalClientState.rows()) {
            if (tab == Tab.CASE ? row.caseId().isEmpty() : row.kind() != tab.kind) continue;
            if (favoritesOnly && !row.favorite()) continue;
            if (!term.isEmpty() && !(row.title() + " " + row.detail() + " " + row.caseTitle()
                    + " " + sourceName(row.source())).toLowerCase(Locale.ROOT).contains(term)) continue;
            result.add(row);
        }
        result.sort((a, b) -> Long.compare(b.observedAt(), a.observedAt()));
        // A view-only row: never insert this into the journal or send journal actions for it.
        if (tab == Tab.SIGHT && !favoritesOnly) {
            var e = JournalClientState.encounter();
            String title = "当前机缘 · " + phaseName(e.phase());
            if (term.isEmpty() || (title + e.enemyName() + e.message()).toLowerCase(Locale.ROOT).contains(term))
                result.addFirst(new JournalSyncPayload.Row(ENCOUNTER_ROW, 0, 0, title,
                        e.message(), e.dimension(), e.position(), 0, 0, false, false, "", "", ""));
        }
        return result;
    }

    private JournalSyncPayload.Row selected() {
        for (var row : rows()) if (row.id().equals(selectedId)) return row;
        return null;
    }

    private void reconcileSelection() {
        var visible = rows();
        boolean exists = visible.stream().anyMatch(row -> row.id().equals(selectedId));
        if (!exists) {
            selectedId = !book.compact() && !visible.isEmpty() ? visible.getFirst().id() : "";
            detailScroll = 0;
            if (book.compact() || visible.isEmpty()) detailOpen = false;
        }
        listScroll = JournalLayout.clampScroll(listScroll, visible.size(), visibleRows());
    }

    private boolean showingDetail() { return !book.compact() || detailOpen; }
    private int visibleRows() { return Math.max(1, book.list().h() / ROW_H); }
    private int visibleLines() { return Math.max(1, book.detail().h() / LINE_H); }

    private void updateWidgets() {
        boolean listVisible = !book.compact() || !detailOpen;
        search.visible = favoriteFilter.visible = listVisible;
        back.visible = book.compact() && detailOpen;
        favoriteFilter.selected(favoritesOnly);
        for (int i = 0; i < tabButtons.size(); i++) tabButtons.get(i).selected(Tab.values()[i] == tab);
        var row = selected();
        boolean actions = showingDetail() && row != null;
        favorite.visible = doubtful.visible = caseButton.visible = actions;
        favorite.active = doubtful.active = caseButton.active = actions && System.currentTimeMillis() >= pendingUntil;
        if (row != null && isEncounter(row)) {
            String phase = JournalClientState.encounter().phase();
            boolean seek = phase.equals("SEEK"), ready = phase.equals("READY"), fight = phase.equals("FIGHT");
            favorite.visible = actions && seek;
            doubtful.visible = actions && ready;
            caseButton.visible = actions && (ready || fight);
            favorite.setMessage(Component.literal("前去查探"));
            doubtful.setMessage(Component.literal("押注挑战"));
            caseButton.setMessage(Component.literal(fight ? "认输离场" : "见好就收"));
            favorite.selected(false); doubtful.selected(false); caseButton.selected(false);
            return;
        }
        if (row != null) {
            favorite.setMessage(Component.literal(row.favorite() ? "取消收藏" : "收藏此条"));
            doubtful.setMessage(Component.literal(row.doubtful() ? "取消存疑" : "标记存疑"));
            caseButton.setMessage(Component.literal(row.caseId().isEmpty() ? "记入疑局" : "移出疑局"));
            favorite.selected(row.favorite()); doubtful.selected(row.doubtful());
            caseButton.selected(!row.caseId().isEmpty());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        if (lastSnapshot != JournalClientState.rows() || lastEncounter != JournalClientState.encounter()) {
            lastEncounter = JournalClientState.encounter();
            lastSnapshot = JournalClientState.rows(); pendingUntil = 0; reconcileSelection();
        }
        updateWidgets();
        g.fill(0, 0, width, height, 0xA9000000);
        drawBook(g); drawHeading(g);
        if (!book.compact() || !detailOpen) { drawSearchFrame(g); drawList(g, mx, my); }
        if (showingDetail()) drawDetail(g);
        drawFooter(g);
        // Widgets only: no second background pass or framebuffer blur.
        for (var widget : renderables) widget.render(g, mx, my, partial);
    }

    private void drawBook(GuiGraphics g) {
        Rect p = book.panel();
        fill(g, p, INK); frame(g, p, 0xFF716044);
        frame(g, new Rect(p.x() + 3, p.y() + 3, p.w() - 6, p.h() - 6), 0xFF30372C);
        // One set of pixel ornaments, never a tiled framed texture.
        for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
            int x = sx < 0 ? p.x() + 7 : p.right() - 8;
            int y = sy < 0 ? p.y() + 7 : p.bottom() - 8;
            g.fill(Math.min(x, x - sx * 15), y, Math.max(x, x - sx * 15) + 1, y + 1, GOLD);
            g.fill(x, Math.min(y, y - sy * 9), x + 1, Math.max(y, y - sy * 9) + 1, GOLD);
        }
        g.fill(p.x() + 12, p.y() + 42, p.right() - 12, p.y() + 43, LINE);
        if (!book.compact()) {
            int spine = book.tabs().right() + 6;
            g.fill(spine, p.y() + 51, spine + 1, book.footer().y() - 8, LINE);
            int fold = book.list().right() + 9;
            g.fill(fold, p.y() + 51, fold + 1, book.footer().y() - 8, 0xFF373E32);
            if (book.tabs().h() > 200) {
                g.drawString(font, "有据", book.tabs().x() + 15, book.footer().y() - 51, MUTED, false);
                g.drawString(font, "而后知", book.tabs().x() + 10, book.footer().y() - 37, MUTED, false);
            }
        }
    }

    private void drawHeading(GuiGraphics g) {
        Rect p = book.panel();
        String title = JournalClientState.title();
        g.drawString(font, title, p.x() + 20, p.y() + 16, GOLD, false);
        if (p.w() > 400) {
            String motto = JournalClientState.titleUnlocked() ? "所见未必为真，所得未必为福" : "亲历有所记，疑处有所存";
            g.drawString(font, motto, p.x() + 30 + font.width(title), p.y() + 16, MUTED, false);
        }
        g.drawString(font, tab.subtitle, p.x() + 20, p.y() + 29, TEAL, false);
    }

    private void drawSearchFrame(GuiGraphics g) {
        Rect r = book.toolbar(), field = new Rect(r.x(), r.y(), r.w() - 49, r.h());
        fill(g, field, 0xFF151A15); frame(g, field, search.isFocused() ? GOLD : LINE);
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        Rect r = book.list();
        var list = rows();
        if (list.isEmpty()) {
            boolean filtered = !query.isBlank() || favoritesOnly;
            drawEmpty(g, r, filtered ? "未找到相符的记载" : "尚无相关见闻",
                    filtered ? "可清空查阅文字，或关闭收藏筛选。" : tab.empty);
            return;
        }
        int count = visibleRows();
        listScroll = JournalLayout.clampScroll(listScroll, list.size(), count);
        g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
        for (int i = 0; i < count && i + listScroll < list.size(); i++) {
            var row = list.get(i + listScroll);
            Rect cell = new Rect(r.x(), r.y() + i * ROW_H, r.w() - 6, ROW_H - 3);
            boolean selected = row.id().equals(selectedId), hover = cell.contains(mx, my);
            if (selected || hover) fill(g, cell, selected ? 0xFF303126 : 0xFF232921);
            g.fill(cell.x(), cell.bottom(), cell.right(), cell.bottom() + 1, 0xFF333A2E);
            if (selected) g.fill(cell.x(), cell.y() + 2, cell.x() + 2, cell.bottom() - 2, GOLD);
            if (row.favorite()) g.fill(cell.right() - 6, cell.y() + 7, cell.right() - 3, cell.y() + 10, GOLD);
            g.drawString(font, fit(row.title(), cell.w() - 22), cell.x() + 8, cell.y() + 7,
                    row.doubtful() ? RED : selected ? GOLD : BONE, false);
            String meta = isEncounter(row) ? encounterMeta() : sourceName(row.source()) + "  " + day(row.observedAt());
            if (!isEncounter(row)) {
                if (row.doubtful()) meta += "  存疑";
                else if (stale(row)) meta += "  久未核实";
            }
            g.drawString(font, fit(meta, cell.w() - 14), cell.x() + 8, cell.y() + 23, TEAL, false);
        }
        g.disableScissor();
        scrollbar(g, r, list.size(), count, listScroll);
    }

    private void drawDetail(GuiGraphics g) {
        Rect r = book.detail();
        var row = selected();
        if (row == null) {
            drawEmpty(g, r, "择一条目，展卷细察", "见闻的来处、时序与疑点，将收录于此。");
            detailCount = 0; return;
        }
        List<TextLine> lines = new ArrayList<>();
        int textW = Math.max(1, r.w() - 12);
        if (isEncounter(row)) {
            encounterLines(lines, textW);
        } else {
        append(lines, row.title(), textW, GOLD);
        append(lines, sourceName(row.source()) + (row.doubtful() ? " / 个人存疑" : " / 见闻记录"), textW, TEAL);
        append(lines, "", textW, BONE); append(lines, "所记", textW, GOLD);
        append(lines, row.detail().isBlank() ? "此条尚无补充记述。" : row.detail(), textW, BONE);
        append(lines, "", textW, BONE); append(lines, "来处与时序", textW, GOLD);
        append(lines, "获知方式：" + sourceName(row.source()), textW, TEAL);
        append(lines, "记录于：" + day(row.observedAt()), textW, TEAL);
        append(lines, "最后核实：" + day(row.verifiedAt()) + (stale(row) ? "（久未核实）" : ""), textW, TEAL);
        append(lines, "已知地界：" + region(row.dim()), textW, TEAL);
        if (!row.caseId().isEmpty()) {
            append(lines, "", textW, BONE); append(lines, "疑局札记", textW, GOLD);
            append(lines, row.caseTitle().isBlank() ? "未名疑局" : row.caseTitle(), textW, BONE);
            append(lines, row.caseMissing().isBlank() ? "尚缺更多独立依据。" : row.caseMissing(), textW, TEAL);
            append(lines, "归入同一疑局，尚不能证明存在同一幕后布局。", textW, MUTED);
        }
        if (row.doubtful()) {
            append(lines, "", textW, BONE); append(lines, "个人批注：此条存疑，仍需核实。", textW, RED);
        }
        }
        detailCount = lines.size();
        detailScroll = JournalLayout.clampScroll(detailScroll, detailCount, visibleLines());
        g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
        for (int i = 0; i < visibleLines() && i + detailScroll < lines.size(); i++) {
            TextLine line = lines.get(i + detailScroll);
            g.drawString(font, line.text, r.x() + 3, r.y() + i * LINE_H + 2, line.color, false);
        }
        g.disableScissor();
        scrollbar(g, r, detailCount, visibleLines(), detailScroll);
    }

    private void append(List<TextLine> lines, String text, int w, int color) {
        if (text.isEmpty()) { lines.add(new TextLine(FormattedCharSequence.EMPTY, color)); return; }
        for (var line : font.split(Component.literal(text), w)) lines.add(new TextLine(line, color));
    }

    private void drawEmpty(GuiGraphics g, Rect r, String title, String hint) {
        g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
        int y = r.y() + Math.max(4, r.h() / 3 - 12), x = r.x() + 8;
        g.fill(x, y, Math.min(r.right() - 8, x + 24), y + 1, GOLD);
        List<TextLine> lines = new ArrayList<>();
        append(lines, title, Math.max(1, r.w() - 18), BONE);
        append(lines, "", Math.max(1, r.w() - 18), BONE);
        append(lines, hint, Math.max(1, r.w() - 18), MUTED);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i).text, x, y + 10 + i * LINE_H, lines.get(i).color, false);
        g.disableScissor();
    }

    private void drawFooter(GuiGraphics g) {
        Rect f = book.footer();
        g.fill(f.x(), f.y() - 3, f.right(), f.y() - 2, LINE);
        long recordCount = rows().stream().filter(row -> !isEncounter(row)).count();
        String status = !JournalClientState.hasData() ? "正在翻阅记载…" : recordCount + " 条记载";
        if (tab == Tab.SIGHT && !favoritesOnly) status += " / 当前机缘";
        if (System.currentTimeMillis() < pendingUntil) status = isEncounter(selected()) ? "机缘核对中…" : "批注核对中…";
        if (favoritesOnly) status += " / 收藏";
        g.drawString(font, fit(status, f.w() / 2), f.x() + 2, f.y() + 2, TEAL, false);
        String help = f.w() >= 390 ? "滚轮翻阅 / Ctrl+F 查阅 / Esc 合卷" : "滚轮翻阅 / Esc 合卷";
        String fitted = fit(help, f.w() / 2);
        g.drawString(font, fitted, f.right() - font.width(fitted) - 2, f.y() + 2, MUTED, false);
    }

    private static void fill(GuiGraphics g, Rect r, int color) { g.fill(r.x(), r.y(), r.right(), r.bottom(), color); }
    private static void frame(GuiGraphics g, Rect r, int color) {
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, color);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), color);
        g.fill(r.x(), r.y(), r.x() + 1, r.bottom(), color);
        g.fill(r.right() - 1, r.y(), r.right(), r.bottom(), color);
    }
    private void scrollbar(GuiGraphics g, Rect r, int total, int visible, int scroll) {
        if (total <= visible) return;
        int thumb = Math.min(r.h(), Math.max(8, r.h() * visible / total));
        int y = r.y() + (r.h() - thumb) * scroll / Math.max(1, total - visible);
        g.fill(r.right() - 3, r.y(), r.right() - 1, r.bottom(), LINE);
        g.fill(r.right() - 3, y, r.right() - 1, y + thumb, GOLD);
    }
    private int scrollAt(double my, Rect r, int total, int visible) {
        int thumb = Math.min(r.h(), Math.max(8, r.h() * visible / Math.max(1, total)));
        double fraction = (my - r.y() - thumb / 2.0) / Math.max(1, r.h() - thumb);
        return JournalLayout.clampScroll((int) Math.round(fraction * Math.max(0, total - visible)), total, visible);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        clearFocus();
        Rect r = book.list();
        var list = rows();
        if ((!book.compact() || !detailOpen) && r.contains(mx, my)) {
            if (mx >= r.right() - 6 && list.size() > visibleRows()) {
                draggingList = true; listScroll = scrollAt(my, r, list.size(), visibleRows());
            } else {
                int index = (int) (my - r.y()) / ROW_H;
                if (index < visibleRows() && index + listScroll < list.size()) {
                    selectedId = list.get(index + listScroll).id(); detailScroll = 0; detailOpen = true;
                }
            }
            return true;
        }
        r = book.detail();
        if (showingDetail() && r.contains(mx, my) && mx >= r.right() - 6 && detailCount > visibleLines()) {
            draggingDetail = true; detailScroll = scrollAt(my, r, detailCount, visibleLines()); return true;
        }
        return false;
    }
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && draggingList) { listScroll = scrollAt(my, book.list(), rows().size(), visibleRows()); return true; }
        if (button == 0 && draggingDetail) { detailScroll = scrollAt(my, book.detail(), detailCount, visibleLines()); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean wasDragging = draggingList || draggingDetail;
        draggingList = draggingDetail = false;
        return super.mouseReleased(mx, my, button) || wasDragging;
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy == 0) return false;
        int step = dy > 0 ? -1 : 1;
        if ((!book.compact() || !detailOpen) && book.list().contains(mx, my)) {
            listScroll = JournalLayout.clampScroll(listScroll + step, rows().size(), visibleRows()); return true;
        }
        if (showingDetail() && book.detail().contains(mx, my)) {
            detailScroll = JournalLayout.clampScroll(detailScroll + step * 3, detailCount, visibleLines()); return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (hasControlDown() && key == GLFW.GLFW_KEY_F && search.visible) {
            setFocused(search); search.setFocused(true); return true;
        }
        if (!search.isFocused() && JournalKeys.OPEN_JOURNAL.matches(key, scan)) { onClose(); return true; }
        if (!search.isFocused() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)
                && (!book.compact() || !detailOpen)) {
            var list = rows();
            if (!list.isEmpty()) {
                int index = -1;
                for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(selectedId)) index = i;
                index = Math.max(0, Math.min(list.size() - 1, index + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
                selectedId = list.get(index).id();
                if (index < listScroll) listScroll = index;
                if (index >= listScroll + visibleRows()) listScroll = index - visibleRows() + 1;
                detailScroll = 0;
            }
            return true;
        }
        if (!search.isFocused() && key == GLFW.GLFW_KEY_ENTER && book.compact() && !detailOpen && selected() != null) {
            detailOpen = true; clearFocus(); return true;
        }
        if (!search.isFocused() && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            int dir = key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1;
            if (showingDetail() && selected() != null) detailScroll = JournalLayout.clampScroll(detailScroll + dir * visibleLines(), detailCount, visibleLines());
            else listScroll = JournalLayout.clampScroll(listScroll + dir * visibleRows(), rows().size(), visibleRows());
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    private String fit(String text, int w) {
        if (w <= 0) return "";
        if (font.width(text) <= w) return text;
        return font.plainSubstrByWidth(text, Math.max(0, w - font.width("…"))) + "…";
    }
    private static boolean isEncounter(JournalSyncPayload.Row row) {
        return row != null && ENCOUNTER_ROW.equals(row.id());
    }

    private static String phaseName(String phase) {
        return switch (phase) {
            case "SEEK" -> "异象待寻";
            case "READY" -> "已查明";
            case "FIGHT" -> "挑战进行中";
            case "WON" -> "挑战获胜";
            case "LOST" -> "挑战失利";
            case "FINISHED" -> "机缘已了";
            default -> "静候机缘";
        };
    }

    private String encounterMeta() {
        var e = JournalClientState.encounter();
        return switch (e.phase()) {
            case "SEEK" -> "前往微光木桶查探";
            case "READY" -> "收手或押注 · 挑战180秒";
            case "FIGHT" -> "击败异兽 · " + JournalClientState.encounterSecondsLeft() + "秒";
            case "WON", "LOST", "FINISHED" -> phaseName(e.phase()) + " · 冷却 " + JournalClientState.encounterSecondsLeft() + "秒";
            default -> phaseName(e.phase());
        };
    }

    private void encounterLines(List<TextLine> lines, int w) {
        var e = JournalClientState.encounter();
        append(lines, "当前机缘 · " + phaseName(e.phase()), w, GOLD);
        if (!e.message().isBlank()) append(lines, e.message(), w, BONE);
        append(lines, "", w, BONE);
        String next = switch (e.phase()) {
            case "SEEK" -> "下一步：前往已知地点，在木桶4格内点击「前去查探」，也可右键木桶调查。";
            case "READY" -> "基础元石已入袋。留在木桶4格内，可见好就收，或先看清代价再押注挑战。";
            case "FIGHT" -> "下一步：在时限内击败异兽；认输将按失败结算。";
            case "WON" -> "挑战已获胜，奖励与效果以服务端结算为准。";
            case "LOST" -> "挑战失利，代价与效果以服务端结算为准。";
            case "FINISHED" -> "此次机缘已结束，继续行走，留意下一次异象。";
            default -> "继续在天地间行走。新的异象出现后，此处会给出你已知的线索。";
        };
        append(lines, next, w, TEAL);
        if (e.phase().equals("FIGHT"))
            append(lines, "剩余时间：" + JournalClientState.encounterSecondsLeft() + " 秒", w, GOLD);
        if (e.phase().equals("WON") || e.phase().equals("LOST") || e.phase().equals("FINISHED"))
            append(lines, "下次机缘冷却：" + JournalClientState.encounterSecondsLeft()
                    + " 秒；冷却结束后，仍需附近有适合的地点。", w, TEAL);
        append(lines, encounterLocation(e), w, TEAL);
        if (!e.enemyName().isBlank()) append(lines, "遭遇异兽：" + e.enemyName(), w, BONE);
        append(lines, "", w, BONE);
        append(lines, "基础机缘：" + e.baseReward() + " 元石", w, GOLD);
        append(lines, "押注代价：" + e.stake() + " 元石", w, RED);
        append(lines, "挑战胜利额外奖励：" + e.bonus() + " 元石", w, GOLD);
        append(lines, "获胜：返还押注，另得额外奖励；速度 I，持续 120 秒。", w, TEAL);
        append(lines, "失败：失去押注，当前真元减少 50%；虚弱 I，持续 60 秒。", w, RED);
        if (e.phase().equals("READY") || e.phase().equals("FIGHT"))
            append(lines, BATTLE_RULES, w, RED);
    }

    private String encounterLocation(EncounterSyncPayload e) {
        if (e.position() == Long.MIN_VALUE || e.dimension().isBlank()) return "地点：尚无确切方位。";
        String area = region(e.dimension());
        if (minecraft == null || minecraft.player == null
                || !minecraft.player.level().dimension().location().toString().equals(e.dimension()))
            return "已知地界：" + area + "（当前不在同一地界）";
        BlockPos pos = BlockPos.of(e.position());
        double dx = pos.getX() + 0.5 - minecraft.player.getX();
        double dz = pos.getZ() + 0.5 - minecraft.player.getZ();
        String direction;
        if (Math.hypot(dx, dz) < 2) direction = "就在附近";
        else {
            String[] directions = {"东", "东南", "南", "西南", "西", "西北", "北", "东北"};
            int octant = Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8);
            direction = directions[octant] + "方";
        }
        return "方位：" + direction + " · 水平距离约 " + Math.round(Math.hypot(dx, dz)) + " 格";
    }

    private void encounterButton(JournalActionPayload.Action button) {
        if (System.currentTimeMillis() < pendingUntil) return;
        var e = JournalClientState.encounter();
        if (button == JournalActionPayload.Action.FAVORITE && e.phase().equals("SEEK")) {
            sendEncounter(EncounterActionPayload.Action.INVESTIGATE, e);
        } else if (button == JournalActionPayload.Action.DOUBTFUL && e.phase().equals("READY")) {
            confirmEncounter(EncounterActionPayload.Action.WAGER, e, "确认押注挑战？",
                    "立即押上 " + e.stake() + " 元石，挑战「" + e.enemyName() + "」。获胜返还押注并额外获得 "
                            + e.bonus() + " 元石与速度 I（120秒）；失败失去押注、当前真元减少50%、虚弱 I（60秒）。"
                            + BATTLE_RULES + (e.message().isBlank() ? "" : " 异兽提示：" + e.message()));
        } else if (button == JournalActionPayload.Action.TOGGLE_CASE) {
            if (e.phase().equals("READY")) sendEncounter(EncounterActionPayload.Action.FINISH, e);
            else if (e.phase().equals("FIGHT")) confirmEncounter(EncounterActionPayload.Action.FINISH, e,
                    "确认认输离场？", "认输按失败结算：失去 " + e.stake()
                            + " 元石押注，当前真元减少50%，虚弱 I 持续60秒。");
        }
    }

    private void confirmEncounter(EncounterActionPayload.Action action, EncounterSyncPayload e,
            String title, String warning) {
        if (minecraft == null) return;
        minecraft.setScreen(new ConfirmScreen(accepted -> {
            minecraft.setScreen(this);
            if (accepted) sendEncounter(action, e);
        }, Component.literal(title), Component.literal(warning),
                Component.literal(action == EncounterActionPayload.Action.WAGER ? "确认押注" : "确认认输"),
                Component.literal("返回")) {
            @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
                g.fill(0, 0, width, height, 0xE0151A15);
            }
        });
    }

    private void sendEncounter(EncounterActionPayload.Action action, EncounterSyncPayload expected) {
        var current = JournalClientState.encounter();
        if (!current.id().equals(expected.id()) || !current.phase().equals(expected.phase()) || current.id().isEmpty()) return;
        if (action == EncounterActionPayload.Action.WAGER && (current.stake() != expected.stake()
                || current.bonus() != expected.bonus() || !current.enemyId().equals(expected.enemyId()))) return;
        pendingUntil = System.currentTimeMillis() + 1000;
        PacketDistributor.sendToServer(new EncounterActionPayload(action, current.id()));
    }
    private static boolean stale(JournalSyncPayload.Row row) { return JournalClientState.now() - row.verifiedAt() >= 48_000L; }
    private static String sourceName(int source) { return switch (source) { case 0 -> "亲见"; case 1 -> "听闻"; case 2 -> "推断"; default -> "来处未明"; }; }
    private static String day(long tick) { return tick < 0 ? "未记时日" : "第 " + (tick / 24_000L + 1) + " 日"; }
    private static String region(String dim) {
        return switch (dim) { case "" -> "不明"; case "minecraft:overworld" -> "主世界"; case "minecraft:the_nether" -> "下界"; case "minecraft:the_end" -> "末地"; default -> dim.substring(dim.indexOf(':') + 1); };
    }
}
