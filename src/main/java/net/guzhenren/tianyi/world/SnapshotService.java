package net.guzhenren.tianyi.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.guzhenren.tianyi.will.History;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/**
 * 全存档快照：<b>真实的文件级备份</b>，不是内存里的一份假记录。
 *
 * <h2>为什么必须落盘</h2>
 * 春秋蝉要能「整个存档回到过去」。区块、方块实体、容器、实体、玩家数据里
 * 绝大部分在<b>磁盘</b>上，而且包含未加载区块。只抓内存里的已加载区块，
 * 回溯完会发现远处的地形没变 —— 那就不是回溯，是回血。所以这里直接对
 * 存档目录做带摘要的完整备份。
 *
 * <h2>安全边界（重要）</h2>
 * <ol>
 *   <li><b>先落盘再复制</b>：调用 {@code saveEverything} 之后才复制文件，
 *       避免复制到写了一半的区块。</li>
 *   <li><b>恢复不在运行期做</b>：运行期零散覆盖 region 文件会直接毁档。
 *       恢复采用「受控停机 + 离线交换」两段式（{@link #requestRewind} +
 *       {@code _pack/rewind_apply.py}），交换前先做完整性校验，校验不过就拒绝。</li>
 *   <li><b>交换前自动备份当前世界</b>，回溯本身也可回退。</li>
 * </ol>
 */
public final class SnapshotService {
    private SnapshotService() {
    }

    /** 快照根目录（在世界存档目录下）。 */
    public static final String DIR = "tianyi_snapshots";
    /** 待恢复标记文件名。 */
    public static final String PENDING = "pending_rewind.json";
    /** 交换前备份目录前缀。 */
    public static final String BACKUP_PREFIX = "tianyi_rewind_backup_";

    /** 会被快照的存档子路径（存在才复制）。 */
    private static final String[] SUBDIRS = {
            "region", "entities", "poi", "data", "playerdata", "dimensions", "datapacks",
    };

    /**
     * 3.13.0：宿命蛊的<b>世界账本</b>文件（在 {@code data/} 下）。
     *
     * <p>快照是整目录复制，{@link #SUBDIRS} 已含 {@code data}，所以宿命身份、阶段、
     * 保管事务与隔离证据全都跟着走——<b>不需要改算法</b>。这个常量只为让
     * 审计/交付能明确断言「口径包含账本」，避免哪天有人把 {@code data} 从
     * {@code SUBDIRS} 里删掉，变成「只回退物品不回退规则」。
     */
    public static final String DESTINY_LEDGER_FILE = "tianyi_destiny_custody.dat";
    /** 阶段与宿命本体身份所在的旧账文件（同一个 {@code data/} 目录）。 */
    public static final String WILL_STATE_FILE = "tianyi_will.dat";

    /** 快照口径是否覆盖宿命账本（审计/单测用；不参与运行期逻辑）。 */
    public static boolean coversDestinyLedger() {
        for (String sub : SUBDIRS) {
            if ("data".equals(sub)) {
                return true;
            }
        }
        return false;
    }

    /** 分区 ← 子路径的映射。 */
    private static final Map<History.Section, String[]> SECTIONS = new LinkedHashMap<>();

    static {
        SECTIONS.put(History.Section.CHUNK, new String[]{"region", "dimensions"});
        SECTIONS.put(History.Section.BLOCK_ENTITY, new String[]{"poi"});
        SECTIONS.put(History.Section.ENTITY, new String[]{"entities"});
        SECTIONS.put(History.Section.PLAYER, new String[]{"playerdata"});
        SECTIONS.put(History.Section.MOD_DATA, new String[]{"data"});
        SECTIONS.put(History.Section.WORLD_STAGE, new String[]{"data"});
    }

    // ---------------------------------------------------------------- 快照

    /**
     * 抓一份快照。
     *
     * @param scope 人类可读的保存范围说明（写进快照元数据，回溯时展示）
     * @return 快照；失败返回 {@code null}
     */
    public static History.Snapshot capture(MinecraftServer server, WillState state,
                                           String scope, Logger log) {
        // 1) 先把世界刷到磁盘，避免复制半截区块
        boolean saved = server.saveEverything(true, true, true);
        if (!saved) {
            log.warn("[天意] 快照前保存失败，放弃本次快照");
            return null;
        }

        Path world = server.getWorldPath(LevelResource.ROOT);
        String id = "snap-" + System.currentTimeMillis();
        Path dest = world.resolve(DIR).resolve(id);
        try {
            Files.createDirectories(dest);

            // 2) level.dat 与各子目录
            copyIfExists(world.resolve("level.dat"), dest.resolve("level.dat"));
            for (String sub : SUBDIRS) {
                copyTree(world.resolve(sub), dest.resolve(sub));
            }

            // 3) 逐分区算摘要
            List<History.Part> parts = new ArrayList<>();
            MessageDigest whole = md();
            for (Map.Entry<History.Section, String[]> e : SECTIONS.entrySet()) {
                Digest d = digestPaths(dest, e.getValue());
                parts.add(new History.Part(e.getKey(), d.files, d.bytes, d.hex));
                whole.update(e.getKey().name().getBytes(StandardCharsets.UTF_8));
                whole.update(d.hex.getBytes(StandardCharsets.UTF_8));
            }
            String digest = hex(whole.digest());

            History.Snapshot snap = new History.Snapshot(id, "main", "", server.overworld().getGameTime(),
                    scope, parts, digest, true, "level.dat + " + String.join(" ", SUBDIRS));
            state.history().add(snap);
            state.setDirty();

            // 4) 把元数据也写到快照目录，离线脚本据此校验
            writeMeta(dest, snap, world);
            log.info("[天意] 已生成全存档快照 {}（{} 分区，{} 字节）", id, parts.size(), snap.totalBytes());
            return snap;
        } catch (IOException e) {
            log.error("[天意] 快照失败：{}", e.toString());
            return null;
        }
    }

    // ---------------------------------------------------------------- 回溯

    /**
     * 申请回到某个快照。
     *
     * <p>本方法<b>不会</b>在运行期改文件。它做四件事：
     * 校验快照 → 写待恢复标记 → 提示必须停机 → 由调用方触发 {@code server.halt(false)}。
     * 真正的文件交换由 {@code _pack/rewind_apply.py} 在服务器停止后执行。
     *
     * @return 提出成功（可以停机）返回 {@code true}
     */
    public static RewindRequest requestRewind(MinecraftServer server, WillState state,
                                              String snapshotId, boolean playersWarned,
                                              Logger log) {
        History.RewindPlan plan = History.plan(state.history(), snapshotId, false, playersWarned);
        // plan 的 worldClosed 传 false：运行期一律先拦下来，只在写了标记后才允许停机
        if (plan.blockers().stream().anyMatch(b -> b.contains("未通过完整性校验")
                || b.contains("不存在") || b.contains("不含区块分区"))) {
            return new RewindRequest(false, plan.blockers(), plan.boundaryNotes(), "");
        }
        if (!playersWarned) {
            return new RewindRequest(false, plan.blockers(), plan.boundaryNotes(), "");
        }
        History.Snapshot snap = state.history().get(snapshotId);
        if (snap == null) {
            return new RewindRequest(false, List.of("快照不存在"), plan.boundaryNotes(), "");
        }
        Path world = server.getWorldPath(LevelResource.ROOT);
        Path snapDir = world.resolve(DIR).resolve(snapshotId);
        if (!Files.isDirectory(snapDir)) {
            return new RewindRequest(false,
                    List.of("快照目录缺失（只有元数据、没有真实文件）：" + snapDir),
                    plan.boundaryNotes(), "");
        }
        // 复核磁盘上的摘要：元数据说验证过还不够，要按当前磁盘重算
        Map<History.Section, String> actual = new LinkedHashMap<>();
        for (History.Part p : snap.parts) {
            String[] paths = SECTIONS.get(p.section());
            if (paths == null) {
                continue;
            }
            actual.put(p.section(), digestPaths(snapDir, paths).hex);
        }
        if (!History.verify(snap, actual)) {
            return new RewindRequest(false,
                    List.of("磁盘上的快照与元数据摘要不符，拒绝恢复"), plan.boundaryNotes(), "");
        }

        String newBranch = state.history().forkBranch(snap.branch);
        try {
            Files.writeString(world.resolve(DIR).resolve(PENDING), pendingJson(snap, newBranch),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new RewindRequest(false, List.of("写待恢复标记失败：" + e), plan.boundaryNotes(), "");
        }
        log.warn("[天意] 已写入待恢复标记：快照 {} → 新分支 {}。服务器将停机，"
                + "请运行 _pack/rewind_apply.py 完成文件交换后再启动。", snapshotId, newBranch);
        return new RewindRequest(true, List.of(), plan.boundaryNotes(), newBranch);
    }

    public record RewindRequest(boolean accepted, List<String> blockers, List<String> notes,
                                String newBranch) {
    }

    // ---------------------------------------------------------------- 工具

    private static String pendingJson(History.Snapshot snap, String newBranch) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"snapshot\": \"").append(snap.id).append("\",\n");
        sb.append("  \"branch\": \"").append(snap.branch).append("\",\n");
        sb.append("  \"newBranch\": \"").append(newBranch).append("\",\n");
        sb.append("  \"takenAt\": ").append(snap.takenAt).append(",\n");
        sb.append("  \"digest\": \"").append(snap.digest).append("\",\n");
        sb.append("  \"scope\": \"").append(snap.scope.replace("\"", "'")).append("\",\n");
        sb.append("  \"parts\": [\n");
        for (int i = 0; i < snap.parts.size(); i++) {
            History.Part p = snap.parts.get(i);
            sb.append("    {\"section\": \"").append(p.section().name())
                    .append("\", \"files\": ").append(p.entries())
                    .append(", \"bytes\": ").append(p.bytes())
                    .append(", \"digest\": \"").append(p.digest()).append("\"}");
            sb.append(i + 1 < snap.parts.size() ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private static void writeMeta(Path dest, History.Snapshot snap, Path world) throws IOException {
        Files.writeString(world.resolve(DIR).resolve(snap.id + ".json"), pendingJson(snap, ""),
                StandardCharsets.UTF_8);
        Files.writeString(dest.resolve("_meta.json"), pendingJson(snap, ""),
                StandardCharsets.UTF_8);
    }

    private static void copyIfExists(Path from, Path to) throws IOException {
        if (Files.isRegularFile(from)) {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path rel = from.relativize(p);
                Path target = to.resolve(rel.toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** 目录摘要结果。 */
    private record Digest(int files, long bytes, String hex) {
    }

    private static Digest digestPaths(Path base, String[] rels) {
        MessageDigest md = md();
        int files = 0;
        long bytes = 0L;
        List<String> lines = new ArrayList<>();
        for (String rel : rels) {
            Path p = base.resolve(rel);
            if (!Files.isDirectory(p)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(p)) {
                List<Path> all = walk.filter(Files::isRegularFile).sorted().toList();
                for (Path f : all) {
                    String h = sha256(f);
                    long size = Files.size(f);
                    lines.add(base.relativize(f).toString().replace('\\', '/') + ":" + h);
                    files++;
                    bytes += size;
                }
            } catch (IOException e) {
                return new Digest(files, bytes, "unreadable");
            }
        }
        for (String line : lines) {
            md.update(line.getBytes(StandardCharsets.UTF_8));
        }
        return new Digest(files, bytes, hex(md.digest()));
    }

    private static String sha256(Path f) {
        MessageDigest md = md();
        try (InputStream in = Files.newInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        } catch (IOException e) {
            return "unreadable";
        }
        return hex(md.digest());
    }

    private static MessageDigest md() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16));
            sb.append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }

    /** 快照目录列表（供命令显示）。 */
    public static List<String> listOnDisk(MinecraftServer server) {
        Path world = server.getWorldPath(LevelResource.ROOT);
        Path dir = world.resolve(DIR);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
