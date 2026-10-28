import net.minecraft.resources.ResourceLocation;

/**
 * 通道 id 探针：在**真实 Minecraft 类**上验证 3.8.0 的崩溃与 3.8.1 的修法。
 *
 * <p>不用启动游戏：直接拿 client jar 里的 ResourceLocation 走一遍。
 * 退出码 0 = 修复成立；非 0 = 仍然有问题。
 */
public final class PayloadIdProbe {
    public static void main(String[] args) {
        boolean ok = true;

        // ① 复现旧写法：withDefaultNamespace 把整串当 path
        try {
            ResourceLocation bad = ResourceLocation.withDefaultNamespace("tianyi:journal");
            System.out.println("FAIL 旧写法居然没抛异常：" + bad);
            ok = false;
        } catch (RuntimeException e) {
            System.out.println("PASS 旧写法确实抛异常：" + e.getClass().getSimpleName()
                    + " → " + e.getMessage());
        }

        // ② 验证新写法
        ResourceLocation good = ResourceLocation.fromNamespaceAndPath("tianyi", "journal");
        System.out.println("PASS 新写法 id = " + good
                + "（namespace=" + good.getNamespace() + ", path=" + good.getPath() + "）");
        if (!"tianyi".equals(good.getNamespace()) || !"journal".equals(good.getPath())) {
            System.out.println("FAIL 命名空间/路径不符合预期");
            ok = false;
        }

        ResourceLocation act = ResourceLocation.fromNamespaceAndPath("tianyi", "journal_action");
        System.out.println("PASS 动作通道 id = " + act);
        if (!"tianyi".equals(act.getNamespace())) {
            System.out.println("FAIL 动作通道命名空间不对");
            ok = false;
        }

        // ③ 通道 id 必须能往返解析
        ResourceLocation round = ResourceLocation.parse(good.toString());
        if (!round.equals(good)) {
            System.out.println("FAIL 通道 id 无法往返解析");
            ok = false;
        } else {
            System.out.println("PASS 通道 id 可往返解析：" + round);
        }

        System.out.println(ok ? "结果：修复成立" : "结果：仍有问题");
        System.exit(ok ? 0 : 1);
    }
}
