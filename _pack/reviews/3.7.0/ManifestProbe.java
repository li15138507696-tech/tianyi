import java.util.jar.JarFile;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class ManifestProbe {
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            try {
                var type = Class.forName("cpw.mods.jarhandling.JarContents");
                var contents = type.getMethod("of", Path.class).invoke(null, Path.of(args[0]));
                var mf = (java.util.jar.Manifest) type.getMethod("getManifest").invoke(contents);
                System.out.println("NeoForge JarContents: OK; manifest attributes=" + mf.getMainAttributes().entrySet());
            } catch (Throwable e) {
                while (e.getCause() != null) e = e.getCause();
                System.out.println("NeoForge JarContents: " + e);
            }
            return;
        }
        try (var jar = new JarFile(args[0])) {
            try {
                jar.getManifest();
                System.out.println("JarFile manifest: OK");
            } catch (Exception e) {
                System.out.println("JarFile manifest: " + e);
            }
        }
        try (var loader = new URLClassLoader(new java.net.URL[] {Path.of(args[0]).toUri().toURL()})) {
            try {
                Class.forName("net.guzhenren.tianyi.will.AlchemyTracker", true, loader);
                System.out.println("URLClassLoader: OK");
            } catch (Exception e) {
                System.out.println("URLClassLoader: " + e);
            }
        }
    }
}
