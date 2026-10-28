package net.guzhenren.tianfu;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.guzhenren.tianfu.init.TianfuParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 体质「形态层」：把原先靠本体实体做的 8 种虚影，<b>全部改为纯服务端粒子</b>。
 *
 * <h2>为什么重做（血泪教训）</h2>
 * 旧实现用 {@code EntityType.create(level)} 生成本体的 GeckoLib 实体（龙卷风、妖藤、
 * 百鬼、镜月、血影、多重剑影、御剑、地震），当时以为「{@code setNoAi(true)} +
 * {@code setInvulnerable(true)}」就等于「不会伤人」。
 *
 * <p><b>这是错的。</b>反编译本体字节码后确认：
 * <ul>
 *   <li>{@code setNoAi} 只关掉 {@code Goal} 选择，<b>不影响 {@code baseTick}</b>；</li>
 *   <li>这些实体的 {@code baseTick} 里直接 {@code invokestatic} 调
 *       {@code *ShiTi*GengXin*Procedure.execute(...)}，与 AI 完全无关；</li>
 *   <li>{@code DalongjuanfengZaiShiTiKeGengXinShiProcedure} 每 tick
 *       {@code getEntitiesOfClass(Entity.class, AABB.inflate(7.5), e -> true)}
 *       ——<b>过滤器直接返回 true，不过滤任何人</b>——然后对每个结果
 *       {@code hurt(new DamageSource(PLAYER_ATTACK, 自身), 600.0f)}。</li>
 * </ul>
 * 也就是说：31 太息养元体的龙卷风装饰会持续对周围 7.5 格内的<b>所有实体</b>
 * （包括玩家自己）造成 600 点伤害。这不是「特效」，是一台绞肉机。
 *
 * <p>其余 7 个实体虽然只把 {@code hurt} 覆写成「免疫」，没有主动攻击，
 * 但它们是 {@code PathfinderMob}：会占实体 ID、参与区块实体计数、
 * 可能被其他模组的 AOE / 索敌逻辑选中，而且本体一升级 procedure 行为就可能变。
 * <b>与其逐个打补丁，不如彻底不生成实体。</b>
 *
 * <h2>现在的做法</h2>
 * 每 tick 只在服务端 {@code sendParticles}，形态由 8 套专属配方表达：
 * <table>
 *   <caption>形态 → 粒子语义</caption>
 *   <tr><th>体质</th><th>原实体</th><th>现在的粒子形态</th></tr>
 *   <tr><td>25 断岳剑心体</td><td>多重剑影</td><td>4 道剑气分层绕身、切向飞掠</td></tr>
 *   <tr><td>26 百炼庚金体</td><td>御剑</td><td>3 柄飞剑自下而上环绕、带金属尾迹</td></tr>
 *   <tr><td>31 太息养元体</td><td>龙卷风</td><td>贴地螺旋风柱，逐层收窄上升</td></tr>
 *   <tr><td>17 九命不灭体</td><td>地震</td><td>脚底环形冲击波 + 碎石外抛</td></tr>
 *   <tr><td>30 苍木回春体</td><td>妖藤</td><td>藤蔓自脚下螺旋缠绕上升</td></tr>
 *   <tr><td>24 幽魂凝识体</td><td>百鬼夜行</td><td>幽绿鬼火高低错落绕行</td></tr>
 *   <tr><td>40 明月入怀体</td><td>镜月</td><td>三面月轮悬于头顶、银辉垂落</td></tr>
 *   <tr><td>53 血煞蜕变体</td><td>血影流花</td><td>血影自腰腹炸开成花、向外凋落</td></tr>
 * </table>
 *
 * <h2>性能</h2>
 * <ul>
 *   <li>错峰：8 种形态各占一个相位槽（{@link #PERIOD}），同屏多人也不会挤在同一 tick；</li>
 *   <li>相位预筛在最前面，绝大多数 tick 直接 return，不做任何计算；</li>
 *   <li>每次 {@code sendParticles} 的 count 恒为 1，位置自己算——避免高斯云，
 *       也让单次发包量可预测；</li>
 *   <li>无实体 = 无 tick 开销、无实体同步、无存档残留、无 {@code discard} 清理逻辑。</li>
 * </ul>
 */
public final class TianfuVfxEntity {

    /** 错峰周期：8 种形态各占一个相位。 */
    private static final int PERIOD = 8;

    /**
     * 玩家的形态相位：按 UUID 散列，避免同屏多人同时播同一种形态。
     *
     * <p>用 {@code getUUID().hashCode()} 而不是 {@code getId()}：后者会随实体 ID
     * 复用而变化，导致形态在重登后跳相位。
     */
    private static int phaseOf(ServerPlayer player) {
        return Math.floorMod(player.getUUID().hashCode(), PERIOD);
    }

    private TianfuVfxEntity() {
    }

    /**
     * 该体质是否走「形态层」。
     *
     * <p>保留这个入口是为了让 {@link TianfuEffects} 的调用点不用改结构，
     * 但现在它只表示「有专属形态配方」，不再涉及任何实体。
     */
    public static boolean has(int tizhi) {
        return shapeOf(tizhi) != Shape.NONE;
    }

    /**
     * 每 tick 调用：按错峰相位播放该体质的形态粒子。
     *
     * <p><b>绝不生成任何实体。</b>这是本类的核心契约，改代码时不要破坏它。
     *
     * @param level     服务端世界（为 null 或客户端直接返回）
     * @param player    目标玩家（形态绕他生成）
     * @param tizhi     体质值
     * @param tickCount 玩家 tick 计数
     */
    public static void tick(ServerLevel level, ServerPlayer player, int tizhi, int tickCount) {
        if (level == null || level.isClientSide() || player == null) {
            return;
        }
        Shape shape = shapeOf(tizhi);
        if (shape == Shape.NONE) {
            return;
        }
        // 相位预筛：8 个相位槽各管一种形态，绝大多数 tick 在这里就返回了
        if (Math.floorMod(tickCount, PERIOD) != phaseOf(player)) {
            return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        RandomSource random = player.getRandom();

        switch (shape) {
            case SWORD_SHADOW -> swordShadow(level, x, y, z, random, tickCount);
            case FLYING_SWORD -> flyingSword(level, x, y, z, random, tickCount);
            case TORNADO -> tornado(level, x, y, z, random, tickCount);
            case QUAKE -> quake(level, x, y, z, random, tickCount);
            case VINE -> vine(level, x, y, z, random, tickCount);
            case GHOST -> ghost(level, x, y, z, random, tickCount);
            case MOON -> moon(level, x, y, z, random, tickCount);
            case BLOOD_FLOWER -> bloodFlower(level, x, y, z, random, tickCount);
            default -> {
            }
        }
    }

    /** 形态枚举。{@code NONE} = 该体质不走形态层（交给 {@link TianfuVfx} 的通用配方）。 */
    private enum Shape {
        NONE,
        SWORD_SHADOW,
        FLYING_SWORD,
        TORNADO,
        QUAKE,
        VINE,
        GHOST,
        MOON,
        BLOOD_FLOWER
    }

    private static Shape shapeOf(int tizhi) {
        return switch (tizhi) {
            case 25 -> Shape.SWORD_SHADOW;   // 断岳剑心体：多重剑影
            case 26 -> Shape.FLYING_SWORD;   // 百炼庚金体：御剑
            case 31 -> Shape.TORNADO;        // 太息养元体：龙卷风
            case 17 -> Shape.QUAKE;          // 九命不灭体：地震
            case 30 -> Shape.VINE;           // 苍木回春体：妖藤
            case 24 -> Shape.GHOST;          // 幽魂凝识体：百鬼夜行
            case 40 -> Shape.MOON;           // 明月入怀体：镜月
            case 53 -> Shape.BLOOD_FLOWER;   // 血煞蜕变体：血影流花
            default -> Shape.NONE;
        };
    }

    // ------------------------------------------------------------------ 八种形态

    /**
     * 多重剑影（25）：4 道剑气分两层绕身，沿切线方向飞掠。
     *
     * <p>「剑影」的关键是<b>方向感</b>：粒子必须沿圆周切线飞出，看起来才像剑在扫，
     * 而不是原地抖动的光点。所以速度向量取 {@code (-sin, cos)} 的切向分量。
     */
    private static void swordShadow(ServerLevel level, double x, double y, double z,
            RandomSource random, int tickCount) {
        ParticleOptions p = TianfuParticleTypes.JIANQI.get();
        int n = 4;
        double base = (tickCount % 30) * (Mth.TWO_PI / 30.0);
        double breathe = 0.70 + Mth.sin((float) (tickCount * 0.15)) * 0.12;

        for (int i = 0; i < n; i++) {
            double a = base + i * (Mth.TWO_PI / n);
            double h = (i % 2 == 0) ? 0.55 : 1.05;
            double r = breathe * (0.92 + random.nextDouble() * 0.16);
            double px = x + Mth.cos((float) a) * r;
            double pz = z + Mth.sin((float) a) * r;
            // 切向 + 轻微上扬，模拟剑气被甩出去
            double vx = -Mth.sin((float) a) * 0.06;
            double vz = Mth.cos((float) a) * 0.06;
            level.sendParticles(p, px, y + h, pz, 1, vx, 0.02, vz, 0.06);
        }
    }

    /**
     * 御剑（26）：3 柄飞剑自下而上环绕，带银色金属尾迹。
     *
     * <p>与剑影的区别：御剑是<b>垂直分层</b>（腰 → 胸 → 头顶），
     * 让三柄剑在高度上错开，看起来像「三把剑悬在你身边」。
     */
    private static void flyingSword(ServerLevel level, double x, double y, double z,
            RandomSource random, int tickCount) {
        ParticleOptions blade = TianfuParticleTypes.JIANQI.get();
        int n = 3;
        double base = (tickCount % 40) * (Mth.TWO_PI / 40.0);

        for (int i = 0; i < n; i++) {
            double a = base + i * (Mth.TWO_PI / n);
            double h = 0.60 + i * 0.42;                 // 腰 → 胸 → 头顶
            double r = 1.05 + random.nextDouble() * 0.12;
            double px = x + Mth.cos((float) a) * r;
            double pz = z + Mth.sin((float) a) * r;
            // 缓慢上升 + 切向，像飞剑在巡航
            double vx = -Mth.sin((float) a) * 0.03;
            double vz = Mth.cos((float) a) * 0.03;
            level.sendParticles(blade, px, y + h, pz, 1, vx, 0.012, vz, 0.04);
            // 尾迹：剑身后方一点点的镀金粒子，做出金属反光
            level.sendParticles(net.guzhenren.init.GuzhenrenModParticleTypes.DUJINLIZI.get(),
                    px - vx * 3.0, y + h - 0.04, pz - vz * 3.0, 1, 0.0, 0.0, 0.0, 0.01);
        }
    }

    /**
     * 龙卷风（31）：贴地螺旋风柱，逐层收窄上升。
     *
     * <p><b>这条是重点修复对象</b>：原实现生成 {@code DalongjuanfengEntity}，
     * 它的 tick procedure 对 7.5 格内所有实体造成 600 伤害。现在改为纯粒子，
     * 视觉保留「气柱旋转」的观感，但<b>零伤害、零实体</b>。
     *
     * <p>形态做法：多条螺旋线同时上升，半径随高度<b>收窄</b>（漏斗形），
     * 这是龙卷风与普通柱状粒子的区别。
     */
    private static void tornado(ServerLevel level, double x, double y, double z,
            RandomSource random, int tickCount) {
        ParticleOptions wind = net.guzhenren.init.GuzhenrenModParticleTypes.KUANGFENGLIZI.get();
        ParticleOptions dust = ParticleTypes.CLOUD;
        int n = 10;                                  // 10 条螺旋线，够密才像风柱
        double base = (tickCount % 12) * (Mth.TWO_PI / 12.0);

        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double a = base + t * Mth.TWO_PI * 2.0;  // 绕两圈
            double h = 0.05 + t * 2.20;              // 从贴地升到 2.2 格
            // 漏斗形：底部宽（1.15）、顶部窄（0.35），这是龙卷风的标志性轮廓
            double r = 1.15 - t * 0.80;
            double px = x + Mth.cos((float) a) * r;
            double pz = z + Mth.sin((float) a) * r;
            // 切向速度让风柱「转」起来，同时向上抽
            double vx = -Mth.sin((float) a) * 0.09;
            double vz = Mth.cos((float) a) * 0.09;
            level.sendParticles(wind, px, y + h, pz, 1, vx, 0.05, vz, 0.05);
            // 每 3 条掺一颗白云做「尘土被卷起」，避免全是亮线
            if (i % 3 == 0) {
                level.sendParticles(dust, px, y + 0.1 + random.nextDouble() * 0.4, pz,
                        1, vx * 0.5, 0.03, vz * 0.5, 0.02);
            }
        }
    }

    /**
     * 地震（17）：脚底环形冲击波 + 碎石外抛。
     *
     * <p>用 {@code count=1} 逐点发，而不是一次大 count——{@code sendParticles}
     * 的偏移是高斯分布，大 count 会得到一团云而不是环。
     */
    private static void quake(ServerLevel level, double x, double y, double z,
            RandomSource random, int tickCount) {
        ParticleOptions rock = net.guzhenren.init.GuzhenrenModParticleTypes.QIDAOLIZILIQI.get();
        ParticleOptions sand = net.guzhenren.init.GuzhenrenModParticleTypes.FEISHA.get();
        int n = 14;
        // 冲击环向外扩张：0.6 → 2.6 格，循环
        double grow = 0.6 + (tickCount % 20) * 0.10;

        for (int i = 0; i < n; i++) {
            double a = i * (Mth.TWO_PI / n) + (tickCount * 0.05);
            double r = grow * (0.94 + random.nextDouble() * 0.12);
            double px = x + Mth.cos((float) a) * r;
            double pz = z + Mth.sin((float) a) * r;
            // 径向向外抛，贴地
            double vx = Mth.cos((float) a) * 0.06;
            double vz = Mth.sin((float) a) * 0.06;
            level.sendParticles(rock, px, y + 0.08, pz, 1, vx, 0.01, vz, 0.03);
            if (i % 4 == 0) {
                level.sendParticles(sand, px, y + 0.15 + random.nextDouble() * 0.2, pz,
                        1, vx * 1.5, 0.06, vz * 1.5, 0.05);
            }
        }
    }

    /**
     * 妖藤（30）：藤蔓自脚下螺旋缠绕上升。
     *
     * <p>与龙卷风的区别：藤蔓<b>半径不随高度收窄</b>（始终贴着身体），
     * 而且只在两条螺旋上（左右各一丛），不是整圈。
     */
    private static void vine(ServerLevel level, double x, double y, double z,
            RandomSource random, int tickCount) {
        ParticleOptions leaf = net.guzhenren.init.GuzhenrenModParticleTypes.QITENGJIANYI.get();
        ParticleOptions pollen = net.guzhenren.init.GuzhenrenModParticleTypes.QINGFENLUNGULIZI.get();
        int n = 8;
        double base = (tickCount % 24) * (Mth.TWO_PI / 24.0);

        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double a = base + t * Mth.TWO_PI * 1.5;
            double h = 0.05 + t * 1.60;              // 脚下爬到胸口
            double r = 0.55 + t * 0.20;              // 微微外扩，但始终贴身
            double px = x + Mth.cos((float) a) * r;
            double pz = z + Mth.sin((float) a) * r;
            double vx = -Mth.sin((float) a) * 0.02;
            double vz = Mth.cos((float) a) * 0.02;
            level.sendParticles(leaf, px, y + h, pz, 1, vx, 0.03, vz, 0.02);
            // 花粉：稀疏飘落的光点
            if (i % 3 == 0) {
                level.sendParticles(pollen, px, y + h + 0.2, pz,
                        1, 0.01, -0.005, 0.01, 0.01);
            }
        }
    }

    /**
     * 百鬼夜行（24）：幽绿鬼火高低错落绕行。
     *
     * <p>「鬼火」的关键是<b>高度不均</b>：如果都在同一高度就成了光环。
     * 这里用 {@code i} 的奇偶与正弦叠加，让每团火在高度上各走各的。
     */
    private static void ghost(ServerLevel level, double x, double y, double z,
            RandomSource random, int tickCount) {
        ParticleOptions fire = TianfuParticleTypes.HUNHUO.get();
        ParticleOptions face = net.guzhenren.init.GuzhenrenModParticleTypes.GUIYAN_2.get();
        int n = 5;
        double base = (tickCount % 36) * (Mth.TWO_PI / 36.0);

        for (int i = 0; i < n; i++) {
            double a = base + i * (Mth.TWO_PI / n);
            // 高度：0.4 ~ 1.9，奇偶错开 + 正弦浮动，绝不齐平
            double h = 0.45 + (i % 2) * 0.55
                    + Mth.sin((float) (tickCount * 0.08 + i)) * 0.35;
            double r = 1.30 + random.nextDouble() * 0.22;
            double px = x + Mth.cos((float) a) * r;
            double pz = z + Mth.sin((float) a) * r;
            double vx = -Mth.sin((float) a) * 0.025;
            double vz = Mth.cos((float) a) * 0.025;
            level.sendParticles(fire, px, y + h, pz, 1, vx, 0.008, vz, 0.015);
            if (i % 2 == 0) {
                level.sendParticles(face, px, y + h + 0.25, pz, 1, 0.0, 0.01, 0.0, 0.01);
            }
        }
    }

    /**
     * 镜月（40）：三面月轮悬于头顶，银辉垂落。
     *
     * <p>月轮必须<b>在头顶之上</b>（1.5 格起）才有「悬」的感觉；
     * 垂落用向下速度的星光表达。
     */
    private static void moon(ServerLevel level, double x, double y, double z,
            RandomSource random, int tickCount) {
        ParticleOptions star = net.guzhenren.init.GuzhenrenModParticleTypes.XINGXIU.get();
        ParticleOptions glow = net.guzhenren.init.GuzhenrenModParticleTypes.XINGXIU_2.get();
        int n = 3;
        double base = (tickCount % 48) * (Mth.TWO_PI / 48.0);

        for (int i = 0; i < n; i++) {
            double a = base + i * (Mth.TWO_PI / n);
            double h = 1.55 + i * 0.18;              // 头顶之上，逐个略高
            double r = 1.10 + random.nextDouble() * 0.10;
            double px = x + Mth.cos((float) a) * r;
            double pz = z + Mth.sin((float) a) * r;
            // 月轮本身：缓慢环绕
            double vx = -Mth.sin((float) a) * 0.012;
            double vz = Mth.cos((float) a) * 0.012;
            level.sendParticles(star, px, y + h, pz, 1, vx, 0.0, vz, 0.02);
            // 银辉垂落：从月轮向下缓缓飘
            level.sendParticles(glow, px, y + h - 0.3, pz, 1, 0.0, -0.02, 0.0, 0.01);
        }
    }

    /**
     * 血影流花（53）：血影自腰腹炸开成花、向外凋落。
     *
     * <p>「花」= 先向外爆发、再受重力下落。所以初速度是径向外抛，
     * 同时给一点向下的加速度（{@code yDist} 为负）模拟凋落。
     */
    private static void bloodFlower(ServerLevel level, double x, double y, double z,
            RandomSource random, int tickCount) {
        ParticleOptions blood = net.guzhenren.init.GuzhenrenModParticleTypes.QIDAOLIZIXUEQI.get();
        int n = 10;
        // 周期 30：花瓣每 1.5 秒绽放一次
        double grow = 0.25 + (tickCount % 30) * 0.055;

        for (int i = 0; i < n; i++) {
            double a = i * (Mth.TWO_PI / n) + tickCount * 0.12;
            double r = grow * (0.85 + random.nextDouble() * 0.30);
            double h = 0.75 + random.nextDouble() * 0.35;   // 腰腹一带
            double px = x + Mth.cos((float) a) * r;
            double pz = z + Mth.sin((float) a) * r;
            // 径向外抛 + 向下凋落
            double vx = Mth.cos((float) a) * 0.05;
            double vz = Mth.sin((float) a) * 0.05;
            level.sendParticles(blood, px, y + h, pz, 1, vx, -0.025, vz, 0.03);
        }
    }

    /**
     * 玩家下线 / 换维度 / 死亡时清理。
     *
     * <p><b>保留这个入口是为了兼容调用点</b>：现在没有实体需要回收，
     * 所以它是空操作。留着是因为 {@link TianfuEvents} 里三处调用已存在，
     * 而且将来若再引入任何需要清理的状态，这里是唯一该挂的地方。
     */
    public static void forget(ServerPlayer player) {
        // 无实体、无缓存，无需清理。
        // 相位由 UUID 散列实时计算，也不存在需要重置的状态。
    }

    /**
     * 供审计脚本读取：错峰周期。
     *
     * <p>脚本用它验证「形态数与相位槽数一致」，防止以后加形态时忘记调 {@link #PERIOD}
     * 导致两种形态永远撞在同一 tick（其中一种永远不播）。
     */
    public static int period() {
        return PERIOD;
    }

    /** 供审计脚本读取：走形态层的体质数量。 */
    public static int shapeCount() {
        int n = 0;
        for (Shape s : Shape.values()) {
            if (s != Shape.NONE) {
                n++;
            }
        }
        return n;
    }
}
