import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Read-only field inventory and potential caller references in the pinned upstream JAR. */
public final class PlayerVariableFieldScanner {
    private static final String OWNER = "net/guzhenren/network/GuzhenrenModVariables$PlayerVariables";
    private static final Set<String> FIELDS = Set.of(
            "zhuanshu", "jieduan", "kongqiao", "renqi", "shouyuan", "zhenyuan",
            "zuida_zhenyuan", "BenMingGu", "BenMingGu_1", "tizhi", "benminggu");
    private static final Set<String> RANK_FIELDS = Set.of("zhuanshu", "jieduan");

    private record MethodKey(String owner, String signature) {
    }

    private record Reference(MethodKey target, MethodKey caller, String callerSuper, String kind) {
        String row() {
            return target.owner().replace('/', '.') + "\t" + target.signature() + "\t"
                    + caller.owner().replace('/', '.') + "\t" + caller.signature() + "\t"
                    + callerSuper.replace('/', '.') + "\t" + kind;
        }
    }

    private PlayerVariableFieldScanner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: PlayerVariableFieldScanner <upstream.jar> <uses.txt> [rank-callers.tsv]");
        }

        Set<String> uses = new TreeSet<>();
        Set<MethodKey> rankWriters = new HashSet<>();
        Map<MethodKey, Set<Reference>> callers = new HashMap<>();
        boolean exportCallers = args.length == 3;
        try (ZipFile jar = new ZipFile(Path.of(args[0]).toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(input);
                    reader.accept(new ClassVisitor(Opcodes.ASM9) {
                        private String className;
                        private String classSuper;

                        @Override
                        public void visit(int version, int access, String name, String signature,
                                          String superName, String[] interfaces) {
                            className = name;
                            classSuper = superName == null ? "" : superName;
                        }

                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                         String signature, String[] exceptions) {
                            MethodKey caller = new MethodKey(className, name + descriptor);
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override
                                public void visitFieldInsn(int opcode, String owner, String field,
                                                           String fieldDescriptor) {
                                    if (OWNER.equals(owner) && FIELDS.contains(field)) {
                                        String operation = switch (opcode) {
                                            case Opcodes.GETFIELD -> "GETFIELD";
                                            case Opcodes.PUTFIELD -> "PUTFIELD";
                                            default -> "OP_" + opcode;
                                        };
                                        uses.add(className.replace('/', '.') + "\t" + name + descriptor + "\t"
                                                + operation + "\t" + field + "\t" + fieldDescriptor);
                                        if (opcode == Opcodes.PUTFIELD && RANK_FIELDS.contains(field)) {
                                            rankWriters.add(caller);
                                        }
                                    }
                                }

                                @Override
                                public void visitMethodInsn(int opcode, String owner, String method,
                                                            String methodDescriptor, boolean isInterface) {
                                    String kind = switch (opcode) {
                                        case Opcodes.INVOKESTATIC -> "INVOKESTATIC";
                                        case Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL";
                                        case Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE";
                                        case Opcodes.INVOKESPECIAL -> "INVOKESPECIAL";
                                        default -> "OP_" + opcode;
                                    };
                                    addReference(owner, method, methodDescriptor, kind);
                                }

                                @Override
                                public void visitInvokeDynamicInsn(String method, String methodDescriptor,
                                                                   Handle bootstrap, Object... arguments) {
                                    handles(bootstrap, "INDY_BOOTSTRAP");
                                    for (Object argument : arguments) handles(argument, "INDY_HANDLE");
                                }

                                @Override
                                public void visitLdcInsn(Object value) {
                                    handles(value, "LDC_HANDLE");
                                }

                                private void handles(Object value, String kind) {
                                    if (!exportCallers) return;
                                    if (value instanceof Handle handle && handle.getTag() >= Opcodes.H_INVOKEVIRTUAL) {
                                        addReference(handle.getOwner(), handle.getName(), handle.getDesc(), kind);
                                    } else if (value instanceof ConstantDynamic dynamic) {
                                        handles(dynamic.getBootstrapMethod(), kind);
                                        for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) {
                                            handles(dynamic.getBootstrapMethodArgument(i), kind);
                                        }
                                    }
                                }

                                private void addReference(String owner, String method, String methodDescriptor,
                                                          String kind) {
                                    if (!exportCallers || !owner.startsWith("net/guzhenren/")) return;
                                    MethodKey target = new MethodKey(owner, method + methodDescriptor);
                                    callers.computeIfAbsent(target, ignored -> new HashSet<>())
                                            .add(new Reference(target, caller, classSuper, kind));
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        Files.write(Path.of(args[1]), uses);
        System.out.println("distinct class/method/op/field tuples: " + uses.size());
        if (exportCallers) {
            Set<String> references = new TreeSet<>();
            Set<MethodKey> visited = new HashSet<>();
            var pending = new ArrayDeque<>(rankWriters);
            while (!pending.isEmpty()) {
                MethodKey target = pending.removeFirst();
                if (!visited.add(target)) continue;
                for (Reference reference : callers.getOrDefault(target, Set.of())) {
                    references.add(reference.row());
                    pending.addLast(reference.caller());
                }
            }
            var rows = new ArrayList<String>();
            rows.add("targetClass\ttargetMethod\tcallerClass\tcallerMethod\tcallerSuper\treferenceKind");
            rows.addAll(references);
            Files.write(Path.of(args[2]), rows);
            System.out.println("rank/stage writer methods: " + rankWriters.size()
                    + "; potential caller references: " + references.size());
        }
    }
}
