package ml.northwestwind;

public class Constants {
    public static final String CURSEFORGE_API = "https://addons-ecs.forgesvc.net/api/v2/addon/";
    public static final String VERSION = "1.2.6";
    private static final String OS = System.getProperty("os.name").toLowerCase();
    public static final boolean IS_WINDOWS = (OS.contains("win"));
    public static final boolean IS_MAC = (OS.contains("mac"));
    public static final boolean IS_UNIX = (OS.contains("nix") || OS.contains("nux") || OS.contains("aix"));
}
