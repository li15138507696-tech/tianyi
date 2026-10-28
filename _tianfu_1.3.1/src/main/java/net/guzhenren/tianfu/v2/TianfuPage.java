// src/main/java/net/guzhenren/tianfu/v2/TianfuPage.java
package net.guzhenren.tianfu.v2;

/** 七页唯一枚举；序号仅用于显示，不写入旧 Kind 或玩家 NBT。 */
public enum TianfuPage {
    DIFFICULTY("difficulty", "难度"), ORIGIN("origin", "出身"),
    APTITUDE("aptitude", "资质"), LIUPAI("liupai", "流派"),
    PHYSIQUE("physique", "体质"), MIND("mind", "心智"), QIYUN("qiyun", "气运");

    public final String id;
    public final String label;

    TianfuPage(String id, String label) {
        this.id = id;
        this.label = label;
    }
}
