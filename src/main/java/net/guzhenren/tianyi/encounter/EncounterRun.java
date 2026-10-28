package net.guzhenren.tianyi.encounter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Portable encounter state. The world service performs inventory and entity effects. */
public final class EncounterRun {
    public enum Phase { WAITING, SEEK, READY, FIGHT, WON, LOST, FINISHED }
    private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    public final String owner;
    public String id = "", dimension = "", enemyUuid = "", enemyId = "", enemyName = "", warning = "", message = "";
    public long position = Long.MIN_VALUE;
    public Phase phase = Phase.WAITING;
    public int remainingTicks = 1200, outsideTicks = 0, baseReward = 32, stake = 32, bonus = 128;
    public boolean baseGranted = false, stakePaid = false, settled = false, penaltyPending = false;

    public EncounterRun(String owner) { this.owner = uuid(owner); }

    public boolean open(String id, String dimension, long pos, String enemyId, String enemyName,
                        String warning, int base, int stake, int bonus) {
        if (penaltyPending || !(phase == Phase.WAITING || (terminal() && remainingTicks <= 0))) return false;
        uuid(id);
        resource(dimension);
        resource(enemyId);
        nonnegative(base); nonnegative(stake); nonnegative(bonus);
        if (pos == Long.MIN_VALUE || enemyName == null || warning == null) throw new IllegalArgumentException("Invalid encounter metadata");
        this.id = id; this.dimension = dimension; position = pos; this.enemyId = enemyId;
        this.enemyName = enemyName; this.warning = warning; baseReward = base; this.stake = stake; this.bonus = bonus;
        enemyUuid = ""; message = ""; remainingTicks = 0; outsideTicks = 0;
        baseGranted = false; stakePaid = false; settled = false; penaltyPending = false;
        phase = Phase.SEEK;
        return true;
    }

    public boolean investigate() {
        if (phase != Phase.SEEK || baseGranted) return false;
        baseGranted = true; phase = Phase.READY; return true;
    }

    public boolean startBattle(String entityUuid, int durationTicks) {
        if (phase != Phase.READY || settled || entityUuid == null || entityUuid.isEmpty()) return false;
        uuid(entityUuid);
        if (durationTicks <= 0) throw new IllegalArgumentException("Battle duration must be positive");
        enemyUuid = entityUuid; phase = Phase.FIGHT; stakePaid = true;
        remainingTicks = durationTicks; outsideTicks = 0; return true;
    }

    public boolean win(int cooldownTicks) {
        if (!unsettledBattle()) return false;
        nonnegative(cooldownTicks);
        phase = Phase.WON; settled = true; penaltyPending = false; remainingTicks = cooldownTicks; message = "胜利";
        return true;
    }

    public boolean lose(String reason, int cooldownTicks) {
        if (!unsettledBattle()) return false;
        nonnegative(cooldownTicks);
        if (reason == null) throw new IllegalArgumentException("Missing loss reason");
        phase = Phase.LOST; settled = true; penaltyPending = true; remainingTicks = cooldownTicks; message = reason;
        return true;
    }

    public boolean finish(int cooldownTicks) {
        if (phase != Phase.SEEK && phase != Phase.READY) return false;
        nonnegative(cooldownTicks);
        phase = Phase.FINISHED; settled = true; penaltyPending = false; remainingTicks = cooldownTicks; return true;
    }

    public boolean consumePenalty() {
        if (!penaltyPending) return false;
        penaltyPending = false; return true;
    }

    public boolean terminal() { return phase == Phase.WON || phase == Phase.LOST || phase == Phase.FINISHED; }
    public boolean due() { return (phase == Phase.WAITING || terminal()) && remainingTicks <= 0 && !penaltyPending; }
    public void elapse(int ticks) {
        if (phase == Phase.WAITING || phase == Phase.FIGHT || terminal()) remainingTicks = Math.max(0, remainingTicks - Math.max(0, ticks));
    }
    public void outside(boolean beyond, int ticks) {
        if (phase == Phase.FIGHT) outsideTicks = beyond ? (int) Math.min(100000L, (long) outsideTicks + Math.max(0, ticks)) : 0;
    }
    public boolean outsideExpired(int graceTicks) { return phase == Phase.FIGHT && outsideTicks >= Math.max(0, graceTicks); }
    private boolean unsettledBattle() { return phase == Phase.FIGHT && stakePaid && !settled; }

    public Map<String, String> snapshot() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("owner", owner); data.put("id", id); data.put("dimension", dimension); data.put("enemyUuid", enemyUuid);
        data.put("enemyId", enemyId); data.put("enemyName", enemyName); data.put("warning", warning); data.put("message", message);
        data.put("position", Long.toString(position)); data.put("phase", phase.name());
        data.put("remainingTicks", Integer.toString(remainingTicks)); data.put("outsideTicks", Integer.toString(outsideTicks));
        data.put("baseReward", Integer.toString(baseReward)); data.put("stake", Integer.toString(stake)); data.put("bonus", Integer.toString(bonus));
        data.put("baseGranted", Boolean.toString(baseGranted)); data.put("stakePaid", Boolean.toString(stakePaid));
        data.put("settled", Boolean.toString(settled)); data.put("penaltyPending", Boolean.toString(penaltyPending));
        return data;
    }

    /** Corruption is rejected so a caller may skip that record without granting money. */
    public static EncounterRun restore(Map<String, String> data) {
        if (data == null) throw new IllegalArgumentException("Missing encounter snapshot");
        EncounterRun r = new EncounterRun(required(data, "owner"));
        r.id = required(data, "id"); r.dimension = required(data, "dimension"); r.enemyUuid = required(data, "enemyUuid");
        r.enemyId = required(data, "enemyId"); r.enemyName = required(data, "enemyName");
        r.warning = required(data, "warning"); r.message = required(data, "message");
        r.position = Long.parseLong(required(data, "position")); r.phase = Phase.valueOf(required(data, "phase"));
        r.remainingTicks = number(data, "remainingTicks"); r.outsideTicks = number(data, "outsideTicks");
        r.baseReward = number(data, "baseReward"); r.stake = number(data, "stake"); r.bonus = number(data, "bonus");
        r.baseGranted = bool(data, "baseGranted"); r.stakePaid = bool(data, "stakePaid");
        r.settled = bool(data, "settled"); r.penaltyPending = bool(data, "penaltyPending");
        if (!r.id.isEmpty()) uuid(r.id);
        if (!r.enemyUuid.isEmpty()) uuid(r.enemyUuid);
        if (!r.dimension.isEmpty()) resource(r.dimension);
        if (!r.enemyId.isEmpty()) resource(r.enemyId);
        if (r.baseReward > 16384 || r.stake > 16384 || r.bonus > 16384
                || r.remainingTicks > 1728000 || r.dimension.length() > 128 || r.enemyId.length() > 128
                || r.enemyName.length() > 128 || r.warning.length() > 512 || r.message.length() > 1024)
            throw new IllegalArgumentException("Encounter value exceeds supported limits");
        if (r.outsideTicks > 100000) throw new IllegalArgumentException("Invalid outside timer");
        if (r.phase != Phase.WAITING && (r.id.isEmpty() || r.dimension.isEmpty() || r.enemyId.isEmpty() || r.position == Long.MIN_VALUE))
            throw new IllegalArgumentException("Incomplete encounter");
        if ((r.phase == Phase.FIGHT || r.phase == Phase.WON || r.phase == Phase.LOST)
                && (r.enemyUuid.isEmpty() || !r.stakePaid || !r.baseGranted)) throw new IllegalArgumentException("Incomplete battle");
        if (r.terminal() != r.settled || (r.penaltyPending && r.phase != Phase.LOST)
                || (r.phase == Phase.SEEK && r.baseGranted) || (r.phase == Phase.READY && !r.baseGranted))
            throw new IllegalArgumentException("Inconsistent encounter state");
        return r;
    }

    private static String required(Map<String, String> data, String key) {
        String value = data.get(key);
        if (value == null) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
    private static int number(Map<String, String> data, String key) { int n = Integer.parseInt(required(data, key)); nonnegative(n); return n; }
    private static boolean bool(Map<String, String> data, String key) {
        String value = required(data, key);
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid " + key);
        return Boolean.parseBoolean(value);
    }
    private static void nonnegative(int n) { if (n < 0) throw new IllegalArgumentException("Negative encounter value"); }
    private static String uuid(String value) {
        if (value == null || !UUID.fromString(value).toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Invalid UUID");
        return value;
    }
    private static void resource(String value) {
        if (value == null || !RESOURCE.matcher(value).matches()) throw new IllegalArgumentException("Invalid resource identifier");
    }
}
