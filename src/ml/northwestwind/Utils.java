package ml.northwestwind;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.fusesource.jansi.Ansi;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Utils {
    private static final int BUFFER_SIZE = 4096;
    public static void invalid() {
        System.err.println("Invalid usage. Use \"curseforge help\" for command list,");
    }

    public static boolean isInteger(String s) {
        return isInteger(s,10);
    }

    public static boolean isInteger(String s, int radix) {
        if(s.isEmpty()) return false;
        for(int i = 0; i < s.length(); i++) {
            if(i == 0 && s.charAt(i) == '-') {
                if(s.length() == 1) return false;
                else continue;
            }
            if(Character.digit(s.charAt(i),radix) < 0) return false;
        }
        return true;
    }

    public static String downloadFile(String fileURL, String saveDir) throws IOException {
        return downloadFile(fileURL, saveDir, null);
    }

    public static String downloadFile(String fileURL, String saveDir, String name) throws IOException {
        URL url = new URL(fileURL.replace(" ", "%20"));
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
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
                fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1,
                        fileURL.length());
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
        } else System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("No file to download. Server replied HTTP code: " + responseCode));
        httpConn.disconnect();
        return saveFilePath;
    }

    public static <E> E getLast(List<E> list) {
        return list.get(list.size() - 1);
    }

    public static boolean unzip(String file) {
        try {
            String fileZip = file;
            String[] splitted = file.split("/");
            File destDir = new File(Arrays.stream(Arrays.copyOf(splitted, splitted.length - 1)).collect(Collectors.joining("/")));
            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
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
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Failed to create directory " + parent);
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    fos.close();
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
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
        try (InputStream is = new URL(url).openStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String jsonText = readAll(rd);
            return parser.parse(jsonText);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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

    public static String getOppositeLauncher(String launcher) {
        if (launcher.equalsIgnoreCase("forge")) return "Fabric";
        if (launcher.equalsIgnoreCase("fabric")) return "Forge";
        return null;
    }

    public static boolean checkVersion(JSONArray versions, JSONObject config) {
        String requiredVer = (String) config.get("mcVer");
        String oppoLauncher = getOppositeLauncher((String) config.get("launcher"));
        boolean launcherMatch = false, versionMatch = false;
        if (oppoLauncher == null || !versions.contains(oppoLauncher)) launcherMatch = true;
        if (!versions.contains(requiredVer)) {
            if (versions.stream().filter(ver -> isMCVersionValid((String) ver)).count() <= 0) versionMatch = true;
        } else versionMatch = true;
        return versionMatch && launcherMatch;
    }
}
