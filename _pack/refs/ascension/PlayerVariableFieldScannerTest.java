import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Exercises real class files without loading the synthetic Minecraft classes. */
public final class PlayerVariableFieldScannerTest implements Opcodes {
    private static final String VARIABLES =
            "net/guzhenren/network/GuzhenrenModVariables$PlayerVariables";
    private static final String PREFIX = "net/guzhenren/audit/";
    private static final String PARAMETER = "(L" + VARIABLES + ";)V";

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("ascension-callers-test-");
        try {
            Map<String, byte[]> classes = fixture();
            Path firstJar = temporary.resolve("first.jar");
            Path secondJar = temporary.resolve("reversed.jar");
            writeJar(firstJar, new ArrayList<>(classes.entrySet()));
            List<Map.Entry<String, byte[]>> reversed = new ArrayList<>(classes.entrySet());
            java.util.Collections.reverse(reversed);
            writeJar(secondJar, reversed);
            Path uses = temporary.resolve("uses.txt");
            Path graph = temporary.resolve("callers.tsv");
            try {
                PlayerVariableFieldScanner.main(new String[]{firstJar.toString(), uses.toString(), graph.toString()});
            } catch (IllegalArgumentException unsupported) {
                throw new AssertionError("scanner must export the caller graph with a third output path", unsupported);
            }

            String output = Files.readString(graph);
            check(output.startsWith("targetClass\ttargetMethod\tcallerClass\tcallerMethod\tcallerSuper\treferenceKind"),
                    "graph declares its exact columns");
            check(output.contains(edge("Writer", "setRank" + PARAMETER, "Caller", "run" + PARAMETER,
                    "INVOKESTATIC")), "direct rank writer call is retained");
            check(output.contains(edge("Writer", "setStage" + PARAMETER, "Caller", "run" + PARAMETER,
                    "INVOKESTATIC")), "stage writers also seed the graph");
            check(output.contains(edge("Caller", "run" + PARAMETER, "Relay", "run" + PARAMETER,
                    "INVOKESTATIC")), "reverse traversal retains intermediate callers");
            check(output.contains(edge("Relay", "run" + PARAMETER, "Entry", "run()V", "INDY_HANDLE")),
                    "lambda bootstrap implementation handles connect to callers");
            check(output.contains(edge("Writer", "setStage" + PARAMETER, "HandleEntry", "run()V", "LDC_HANDLE")),
                    "constant method handles are labeled as references");
            check(!output.contains("NonRankCaller") && !output.contains("NoopCaller"),
                    "read-only, unrelated-field and overloaded nonwriter calls are excluded");
            check(output.lines().count() == 6, "duplicate bytecode calls produce one graph edge");
            check(Files.readAllLines(uses).size() == 4, "legacy class/method/op/field tuples remain deduplicated");

            Path legacy = temporary.resolve("legacy.txt");
            PlayerVariableFieldScanner.main(new String[]{firstJar.toString(), legacy.toString()});
            check(Files.readString(legacy).equals(Files.readString(uses)), "two-argument legacy output is unchanged");
            Path reorderedUses = temporary.resolve("reordered-uses.txt");
            Path reorderedGraph = temporary.resolve("reordered-graph.tsv");
            PlayerVariableFieldScanner.main(new String[]{secondJar.toString(), reorderedUses.toString(), reorderedGraph.toString()});
            check(Files.readString(reorderedUses).equals(Files.readString(uses))
                    && Files.readString(reorderedGraph).equals(output), "ZIP entry order cannot change evidence");
            System.out.println("PASS direct, transitive and lambda rank-writer references; stable legacy evidence");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static String edge(String target, String targetMethod, String caller, String callerMethod, String kind) {
        return (PREFIX + target).replace('/', '.') + "\t" + targetMethod + "\t"
                + (PREFIX + caller).replace('/', '.') + "\t" + callerMethod + "\tjava.lang.Object\t" + kind;
    }

    private static Map<String, byte[]> fixture() {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        ClassWriter writer = type("Writer");
        writeField(writer, "setRank", "zhuanshu", 2);
        writeField(writer, "setStage", "jieduan", 1);
        writeField(writer, "renqiOnly", "renqi", 1);
        MethodVisitor read = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "readRank", PARAMETER, null, null);
        read.visitCode();
        read.visitVarInsn(ALOAD, 0);
        read.visitFieldInsn(GETFIELD, VARIABLES, "zhuanshu", "D");
        read.visitInsn(POP2);
        finish(read);
        MethodVisitor noop = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "setRank", "()V", null, null);
        noop.visitCode();
        finish(noop);
        classes.put(PREFIX + "Writer", writer.toByteArray());

        ClassWriter caller = type("Caller");
        MethodVisitor call = caller.visitMethod(ACC_PUBLIC | ACC_STATIC, "run", PARAMETER, null, null);
        call.visitCode();
        for (String name : new String[]{"setRank", "setRank", "setStage"}) {
            call.visitVarInsn(ALOAD, 0);
            call.visitMethodInsn(INVOKESTATIC, PREFIX + "Writer", name, PARAMETER, false);
        }
        finish(call);
        classes.put(PREFIX + "Caller", caller.toByteArray());

        ClassWriter relay = type("Relay");
        MethodVisitor relayMethod = relay.visitMethod(ACC_PUBLIC | ACC_STATIC, "run", PARAMETER, null, null);
        relayMethod.visitCode();
        relayMethod.visitVarInsn(ALOAD, 0);
        relayMethod.visitMethodInsn(INVOKESTATIC, PREFIX + "Caller", "run", PARAMETER, false);
        finish(relayMethod);
        classes.put(PREFIX + "Relay", relay.toByteArray());

        ClassWriter entry = type("Entry");
        MethodVisitor lambda = entry.visitMethod(ACC_PUBLIC | ACC_STATIC, "run", "()V", null, null);
        lambda.visitCode();
        lambda.visitInsn(ACONST_NULL);
        lambda.visitInvokeDynamicInsn("run", "(L" + VARIABLES + ";)Ljava/lang/Runnable;",
                new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                Type.getMethodType("()V"), new Handle(H_INVOKESTATIC, PREFIX + "Relay", "run", PARAMETER, false),
                Type.getMethodType("()V"));
        lambda.visitInsn(POP);
        finish(lambda);
        classes.put(PREFIX + "Entry", entry.toByteArray());

        ClassWriter handles = type("HandleEntry");
        MethodVisitor handle = handles.visitMethod(ACC_PUBLIC | ACC_STATIC, "run", "()V", null, null);
        handle.visitCode();
        handle.visitLdcInsn(new Handle(H_INVOKESTATIC, PREFIX + "Writer", "setStage", PARAMETER, false));
        handle.visitInsn(POP);
        finish(handle);
        classes.put(PREFIX + "HandleEntry", handles.toByteArray());

        for (String name : new String[]{"NonRankCaller", "NoopCaller"}) {
            ClassWriter unrelated = type(name);
            MethodVisitor method = unrelated.visitMethod(ACC_PUBLIC | ACC_STATIC, "run", "()V", null, null);
            method.visitCode();
            if (name.equals("NonRankCaller")) {
                for (String target : new String[]{"renqiOnly", "readRank"}) {
                    method.visitInsn(ACONST_NULL);
                    method.visitMethodInsn(INVOKESTATIC, PREFIX + "Writer", target, PARAMETER, false);
                }
            } else {
                method.visitMethodInsn(INVOKESTATIC, PREFIX + "Writer", "setRank", "()V", false);
            }
            finish(method);
            classes.put(PREFIX + name, unrelated.toByteArray());
        }
        return classes;
    }

    private static ClassWriter type(String name) {
        ClassWriter result = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        result.visit(V21, ACC_PUBLIC, PREFIX + name, null, "java/lang/Object", null);
        return result;
    }

    private static void writeField(ClassWriter writer, String name, String field, int repeats) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, name, PARAMETER, null, null);
        method.visitCode();
        for (int i = 0; i < repeats; i++) {
            method.visitVarInsn(ALOAD, 0);
            method.visitInsn(DCONST_1);
            method.visitFieldInsn(PUTFIELD, VARIABLES, field, "D");
        }
        finish(method);
    }

    private static void finish(MethodVisitor method) {
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void writeJar(Path output, List<Map.Entry<String, byte[]>> classes) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (Map.Entry<String, byte[]> entry : classes) {
                zip.putNextEntry(new ZipEntry(entry.getKey() + ".class"));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }

    private static void check(boolean accepted, String message) {
        if (!accepted) throw new AssertionError(message);
    }
}
