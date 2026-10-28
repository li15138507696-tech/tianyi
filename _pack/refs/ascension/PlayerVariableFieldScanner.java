import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** One-off read-only scanner for direct field instructions in the pinned upstream JAR. */
public final class PlayerVariableFieldScanner {
    private static final String OWNER = "net/guzhenren/network/GuzhenrenModVariables$PlayerVariables";
    private static final Set<String> FIELDS = Set.of(
            "zhuanshu", "jieduan", "kongqiao", "renqi", "shouyuan", "zhenyuan",
            "zuida_zhenyuan", "BenMingGu", "BenMingGu_1", "tizhi", "benminggu");

    private PlayerVariableFieldScanner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: PlayerVariableFieldScanner <upstream.jar> <output.txt>");
        }

        Set<String> uses = new TreeSet<>();
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

                        @Override
                        public void visit(int version, int access, String name, String signature,
                                          String superName, String[] interfaces) {
                            className = name.replace('/', '.');
                        }

                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                         String signature, String[] exceptions) {
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
                                        uses.add(className + "\t" + name + descriptor + "\t"
                                                + operation + "\t" + field + "\t" + fieldDescriptor);
                                    }
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        java.nio.file.Files.write(Path.of(args[1]), uses);
        System.out.println("distinct class/method/op/field tuples: " + uses.size());
    }
}
