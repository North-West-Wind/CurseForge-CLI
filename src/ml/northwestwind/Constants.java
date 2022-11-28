package ml.northwestwind;

import java.io.File;
import java.net.URISyntaxException;
import java.util.function.Supplier;

public class Constants {
    public static final String CURSEFORGE_API = "https://northwestwind.ml/api/curseforge/mods/";
    public static final String VERSION = "1.4.0";
    private static final String OS = System.getProperty("os.name").toLowerCase();
    public static final boolean IS_WINDOWS = (OS.contains("win"));
    public static final boolean IS_MAC = (OS.contains("mac"));
    public static final boolean IS_UNIX = (OS.contains("nix") || OS.contains("nux") || OS.contains("aix"));
    public static final String ABSOLUTE_PATH = ((Supplier<String>) () -> {
        try {
            return new File(Constants.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getPath();
        } catch (URISyntaxException exception) {
            return ".";
        }
    }).get();
}
