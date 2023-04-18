package ml.northwestwind;

import org.apache.commons.io.FileUtils;
import org.fusesource.jansi.Ansi;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Utils {
    private static final int BUFFER_SIZE = 4096;
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/North-West-Wind/CurseForge-CLI/main/update.json";
    private static final Charset[] CHARSETS = { StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.US_ASCII, StandardCharsets.UTF_16, StandardCharsets.UTF_16BE, StandardCharsets.UTF_16LE };
    private static final String[] LAUNCHERS = { "forge", "fabric", "quilt" /*, "rift" idk how rift works*/ };

    public static void invalid() {
        System.err.println("Invalid usage. Use \"curseforge help\" for command list,");
    }

    public static boolean isInteger(String s) {
        return isInteger(s, 10);
    }

    public static boolean isInteger(String s, int radix) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (i == 0 && s.charAt(i) == '-') {
                if (s.length() == 1) return false;
                else continue;
            }
            if (Character.digit(s.charAt(i), radix) < 0) return false;
        }
        return true;
    }

    public static String downloadFile(String fileURL, String saveDir) throws IOException {
        return downloadFile(fileURL, saveDir, null);
    }

    public static String downloadFile(String fileURL, String saveDir, String name) throws IOException {
        URL url = new URL(fileURL.replace(" ", "%20"));
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        httpConn.addRequestProperty("User-Agent", "Mozilla/5.0");
        int responseCode = httpConn.getResponseCode();
        String saveFilePath = null;

        // always check HTTP response code first
        if (responseCode == HttpURLConnection.HTTP_OK) {
            String fileName = "";
            String disposition = httpConn.getHeaderField("Content-Disposition");
            String contentType = httpConn.getContentType();
            int contentLength = httpConn.getContentLength();

            if (disposition != null) {
                // extracts file name from header field
                int index = disposition.indexOf("filename=");
                if (index > 0) {
                    fileName = disposition.substring(index + 10,
                            disposition.length() - 1);
                }
            } else {
                // extracts file name from URL
                fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1
                );
            }

            // opens input stream from the HTTP connection
            InputStream inputStream = httpConn.getInputStream();
            saveFilePath = saveDir + File.separator + (name == null ? fileName : name);

            // opens an output stream to save into file
            FileOutputStream outputStream = new FileOutputStream(saveFilePath);

            int bytesRead = -1;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();
        } else
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("No file to download. Server replied HTTP code: " + responseCode));
        httpConn.disconnect();
        return saveFilePath;
    }

    public static <E> E getLast(List<E> list) {
        return list.get(list.size() - 1);
    }

    public static boolean unzip(String file) {
        for (Charset charset : CHARSETS) {
            try {
                File destDir = new File(file).getParentFile();
                byte[] buffer = new byte[1024];

                FileInputStream fis = new FileInputStream(file);
                ZipInputStream zis = new ZipInputStream(fis, charset);
                ZipEntry zipEntry = zis.getNextEntry();
                while (zipEntry != null) {
                    File newFile = newFile(destDir, zipEntry);
                    if (newFile.exists()) {
                        if (newFile.isDirectory()) FileUtils.cleanDirectory(newFile);
                        else newFile.delete();
                    }
                    if (zipEntry.isDirectory()) {
                        if (!newFile.isDirectory() && !newFile.mkdirs())
                            throw new IOException("Failed to create directory " + newFile);
                    } else {
                        File parent = newFile.getParentFile();
                        if (!parent.isDirectory() && !parent.mkdirs())
                            throw new IOException("Failed to create directory " + parent);
                        FileOutputStream fos = new FileOutputStream(newFile);
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                        fos.close();
                    }
                    zipEntry = zis.getNextEntry();
                }
                zis.closeEntry();
                zis.close();
                fis.close();
                return true;
            } catch (Exception e) {
                System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Charset " + charset.name() + " failed. Trying the next one...").reset());
            }
        }
        System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("All charsets failed. Installation stopped.").reset());
        return false;
    }

    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) sb.append((char) cp);
        return sb.toString();
    }

    public static Object readJsonFromUrl(String url) {
        JSONParser parser = new JSONParser();
        try (InputStream is = readStreamFromUrl(url)) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String jsonText = readAll(rd);
            return parser.parse(jsonText);
        } catch (Exception e) {
            if (Config.silentExceptions) e.printStackTrace();
            return null;
        }
    }

    private static InputStream readStreamFromUrl(String url) throws IOException {
        HttpURLConnection httpcon = (HttpURLConnection) new URL(url).openConnection();
        httpcon.addRequestProperty("User-Agent", "Mozilla/5.0");
        return httpcon.getInputStream();
    }

    public static boolean isMCVersionValid(String ver) {
        String[] splitted = ver.replace("-Snapshot", "").split("\\.");
        if (splitted.length > 3) return false;
        for (int ii = 0; ii < splitted.length; ii++) {
            String split = splitted[ii];
            if (!isInteger(split)) return false;
            if (ii == 0 && Integer.parseInt(split) != 1) return false;
        }
        return true;
    }

    public static void zip(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
            }
            zipOut.closeEntry();
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zip(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }

    public static boolean checkVersion(JSONArray versions, JSONObject config) {
        String requiredVer = (String) config.get("mcVer");
        String launcher = (String) config.get("launcher");
        boolean launcherMatch = false, versionMatch = false;
        int trueCount = 0;
        for (String s : LAUNCHERS)
            if (versions.stream().filter(str -> ((String) str).equalsIgnoreCase(s)).findAny().isPresent()) {
                trueCount++;
                if (s.equalsIgnoreCase(launcher)) {
                    launcherMatch = true;
                    break;
                }
            }
        // Assume it is ok if no launcher is specified by the mod file
        if (trueCount == 0) launcherMatch = true;
        // Special relationship of fabric and quilt
        if (!launcherMatch && launcher.equalsIgnoreCase("quilt") && versions.contains("fabric")) launcherMatch = true;
        if (!versions.contains(requiredVer)) {
            if (Config.acceptParentVersionMod && versions.contains(parentVersion(requiredVer))) versionMatch = true;
            else if (!versions.contains(parentVersion(requiredVer)) && versions.stream().noneMatch(ver -> isMCVersionValid((String) ver))) versionMatch = true;
        } else versionMatch = true;
        return versionMatch && launcherMatch;
    }

    private static String parentVersion(String version) {
        if (!isMCVersionValid(version)) return null;
        String[] splitted = version.replace("-Snapshot", "").split("\\.");
        if (splitted.length != 3) return version;
        return splitted[0] + "." + splitted[1];
    }

    public static void readUpdate() {
        Object received = readJsonFromUrl(UPDATE_URL);
        if (received == null) return;
        JSONObject json = (JSONObject) received;
        String latest = (String) json.get("latest");
        if (!latest.equals(Constants.VERSION)) {
            JSONObject details = (JSONObject) json.get(latest);
            String title = (String) details.get("title");
            String desc = (String) details.getOrDefault("description", "");
            System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("New version available: ").a(latest).reset());
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a(title).reset());
            if (!desc.isEmpty()) System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a(desc).reset());
        }
    }

    public static String getMinecraftPath() {
        if (Constants.IS_UNIX) return System.getProperty("user.home") + "/.minecraft";
        else if (Constants.IS_MAC) return System.getProperty("user.home") + "/Library/Application Support/minecraft";
        else if (Constants.IS_WINDOWS) return System.getenv("APPDATA") + "/.minecraft";
        else return null;
    }

    public static <T> T runRetry(SupplierWithException<T> supplier) throws Exception {
        T obj = null;
        long tries = 0;
        while (obj == null) {
            try {
                obj = supplier.getWithException();
            } catch (Exception e) {
                if (tries == Config.retries) throw e;
                tries++;
            }
        }
        return obj;
    }

    public static Map<String, String> getAllMods(String modFolder) {
        Map<String, String> map = new HashMap<>();
        File modsFolder = new File(modFolder);
        if (!modsFolder.exists() || !modsFolder.isDirectory()) return map;
        for (String filename : modsFolder.list()) {
            if (!filename.endsWith(".jar")) continue;
            String[] splitted = filename.replace(".jar", "").split("_");
            String fileId = Utils.getLast(Arrays.asList(splitted));
            if (!Utils.isInteger(fileId)) continue;
            String[] splitted1 = Arrays.stream(Arrays.copyOf(splitted, splitted.length - 1)).toArray(String[]::new);
            String id = Utils.getLast(Arrays.asList(splitted1));
            if (!Utils.isInteger(id)) continue;
            map.put(id, fileId);
        }
        return map;
    }

    public static Map<String, String> getAllModNames(String modFolder) {
        Map<String, String> map = new HashMap<>();
        File modsFolder = new File(modFolder);
        if (!modsFolder.exists() || !modsFolder.isDirectory()) return map;
        for (String filename : modsFolder.list()) {
            if (!filename.endsWith(".jar")) continue;
            String[] splitted = filename.replace(".jar", "").split("_");
            String fileId = Utils.getLast(Arrays.asList(splitted));
            if (!Utils.isInteger(fileId)) continue;
            String[] splitted1 = Arrays.stream(Arrays.copyOf(splitted, splitted.length - 1)).toArray(String[]::new);
            String id = Utils.getLast(Arrays.asList(splitted1));
            if (!Utils.isInteger(id)) continue;
            map.put(id, filename);
        }
        return map;
    }

    public static boolean readYesNo() {
        Scanner scanner = new Scanner(System.in);
        String res = scanner.nextLine();
        return res.equalsIgnoreCase("y");
    }

    public static String getModSlug(String id) {
        JSONObject json = (JSONObject) readJsonFromUrl(Constants.CURSEFORGE_API + id);
        return (String) json.get("slug");
    }

    @FunctionalInterface
    public interface SupplierWithException<T> {
        T getWithException() throws Exception;
    }

    public static class NullEntry<K, V> implements Map.Entry<K, V> {
        @Override
        public K getKey() {
            return null;
        }

        @Override
        public V getValue() {
            return null;
        }

        @Override
        public V setValue(V value) {
            return null;
        }
    }
}
