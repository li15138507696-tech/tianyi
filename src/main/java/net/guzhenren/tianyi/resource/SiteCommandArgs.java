package net.guzhenren.tianyi.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@code /tianyi site …} 的<b>纯参数解析</b>：短 id、id/前缀解析、列表上限。
 *
 * <p>命令层最容易出错的地方不是世界操作，而是「玩家打错一个字符」：把歧义前缀当成精确匹配，
 * 就会去动一个不是他想的那个点。所以解析单独抽出来、单独单测（{@code _pack/tests}），
 * 命令层只负责把结果翻译成一两句回话。
 *
 * <p>本类不 import 任何 {@code net.minecraft.*} / {@code net.neoforged.*}。
 */
public final class SiteCommandArgs {
    /** {@code site list} 一次最多列几条（再多也没人看，而且容易把全图摊开）。 */
    public static final int MAX_LIST = 20;
    /** 列表里显示的前缀长度（完整 UUID 仍在 status/retire/release 里可用）。 */
    public static final int SHORT_LENGTH = 8;

    private SiteCommandArgs() {
    }

    /** 完整 id 的短显示形式（不足 8 位原样返回；null 视为空串，绝不返回 null）。 */
    public static String shortId(String full) {
        if (full == null) return "";
        String text = full.trim();
        return text.length() <= SHORT_LENGTH ? text : text.substring(0, SHORT_LENGTH);
    }

    /**
     * token → 完整 id。
     *
     * <p>规则（顺序固定）：<b>精确匹配优先</b>；否则收集所有以该 token 开头（忽略大小写）的候选，
     * 只有恰好一个才算命中。歧义、空 token、没人匹配一律返回空串——宁可不做，也不猜是哪一个。
     *
     * @param known 现有全部 id（含墓碑），由调用方从账本里取
     */
    public static String siteId(String token, Collection<String> known) {
        if (token == null || known == null) return "";
        String text = token.trim();
        if (text.isEmpty()) return "";
        for (String id : known) {
            if (id != null && id.equalsIgnoreCase(text)) return id;
        }
        List<String> hits = new ArrayList<>();
        for (String id : known) {
            if (id != null && id.regionMatches(true, 0, text, 0, text.length()) && !hits.contains(id)) {
                hits.add(id);
            }
        }
        return hits.size() == 1 ? hits.get(0) : "";
    }

    /** 这个 token 是不是「有歧义的前缀」（用于把「找不到」和「请多打几位」分开说）。 */
    public static boolean ambiguous(String token, Collection<String> known) {
        if (token == null || known == null) return false;
        String text = token.trim();
        if (text.isEmpty()) return false;
        int hits = 0;
        for (String id : known) {
            if (id != null && id.regionMatches(true, 0, text, 0, text.length())) hits++;
        }
        return hits > 1;
    }
}
