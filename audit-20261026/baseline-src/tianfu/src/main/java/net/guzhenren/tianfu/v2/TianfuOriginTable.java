// src/main/java/net/guzhenren/tianfu/v2/TianfuOriginTable.java
package net.guzhenren.tianfu.v2;

import java.util.List;
import java.util.Map;

/** 出身纯数据。空字符串表示无出身，不是可购买的第六地域或第五类型。 */
public final class TianfuOriginTable {
    private TianfuOriginTable() { }
    /** @param daohen 精确本体字段到加值；@param lianguBonus 0.10 表示额外 +10%。 */
    public record Region(String id, String name, int cost, Map<String, Double> daohen, double lianguBonus) { }
    /**
     * @param cost 正值扣点、负值给点；其它百分比用小数，确认前不得生效。
     * @param yuanshiBonus 确认时一次性发放的元石比例，0.15 表示 +15%（写 yuanshishuliang）。
     */
    public record Background(String id, String name, int cost, double luckCapBonus,
                             double lianguBonus, double yuanshiBonus) { }
    /** 中州仅非汇总 44 项；不能统一字段前缀，不顺带添加 liupai 或 daohen_zong。 */
    public static final Map<String, Double> CENTRAL_DAOHEN = Map.ofEntries(
            Map.entry("dahen_liandao", 30.0),
            Map.entry("dahen_nudao", 30.0),
            Map.entry("dahen_qidao", 30.0),
            Map.entry("dahen_rendao", 30.0),
            Map.entry("dahen_tiandao", 30.0),
            Map.entry("dahen_xingdao", 30.0),
            Map.entry("dahen_zhendao", 30.0),
            Map.entry("dahen_zhidao", 30.0),
            Map.entry("dahen_zhoudao", 30.0),
            Map.entry("daohen_andao", 30.0),
            Map.entry("daohen_bianhuadao", 30.0),
            Map.entry("daohen_bingdao", 30.0),
            Map.entry("daohen_bingxuedao", 30.0),
            Map.entry("daohen_dandao", 30.0),
            Map.entry("daohen_daodao", 30.0),
            Map.entry("daohen_dudao", 30.0),
            Map.entry("daohen_fengdao", 30.0),
            Map.entry("daohen_guangdao", 30.0),
            Map.entry("daohen_gudao", 30.0),
            Map.entry("daohen_huadao", 30.0),
            Map.entry("daohen_huandao", 30.0),
            Map.entry("daohen_hundao", 30.0),
            Map.entry("daohen_jiandao", 30.0),
            Map.entry("daohen_jindao", 30.0),
            Map.entry("daohen_jindao2", 30.0),
            Map.entry("daohen_leidao", 30.0),
            Map.entry("daohen_lidao", 30.0),
            Map.entry("daohen_lvdao", 30.0),
            Map.entry("daohen_mengdao", 30.0),
            Map.entry("daohen_mudao", 30.0),
            Map.entry("daohen_shidao", 30.0),
            Map.entry("daohen_shuidao", 30.0),
            Map.entry("daohen_toudao", 30.0),
            Map.entry("daohen_tudao", 30.0),
            Map.entry("daohen_xindao", 30.0),
            Map.entry("daohen_xudao", 30.0),
            Map.entry("daohen_xuedao", 30.0),
            Map.entry("daohen_yandao", 30.0),
            Map.entry("daohen_yindao", 30.0),
            Map.entry("daohen_yingdao", 30.0),
            Map.entry("daohen_yudao", 30.0),
            Map.entry("daohen_yuedao", 30.0),
            Map.entry("daohen_yundao", 30.0),
            Map.entry("daohen_yundao2", 30.0)
    );
    public static final List<Region> REGIONS = List.of(
            new Region("beiyuan", "北原", 0, Map.of("daohen_lidao", 200.0), 0),
            new Region("nanjiang", "南疆", 0, Map.of("daohen_dudao", 200.0), 0.10),
            new Region("ximo", "西漠", 0, Map.of("daohen_jindao", 200.0), 0),
            new Region("donghai", "东海", 0, Map.of("daohen_shuidao", 200.0), 0),
            new Region("zhongzhou", "中州", 0, CENTRAL_DAOHEN, 0));
    public static final List<Background> BACKGROUNDS = List.of(
            new Background("clan", "家族子弟", -1, -0.10, 0, 0),
            new Background("wanderer", "散修", 1, 0.15, 0, 0),
            new Background("apprentice", "蛊师学徒", 2, 0, 0.20, 0),
            new Background("merchant", "商贾", 1, 0, 0, 0.15));
}
