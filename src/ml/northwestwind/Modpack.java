package ml.northwestwind;

import org.apache.commons.io.FileUtils;
import org.fusesource.jansi.Ansi;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Modpack {
    private static final JSONParser parser = new JSONParser();
    public static void run(String[] args) {
        String cmd = args[1];
        if (args.length < 3) {
            if (cmd.equalsIgnoreCase("list")) list();
            else Utils.invalid();
            return;
        }
        args = Arrays.stream(args).skip(2).toArray(String[]::new);
        if (cmd.equalsIgnoreCase("install")) install(args);
        else if (cmd.equalsIgnoreCase("update")) update(args);
        else if (cmd.equalsIgnoreCase("delete")) delete(args);
        else if (cmd.equalsIgnoreCase("convert")) convert(args);
        else Utils.invalid();
    }

    private static void install(String[] ids) {
        for (String id : ids) {
            if (!Utils.isInteger(id)) {
                System.err.println(id + " is not a valid modpack ID!");
                continue;
            }
            try {
                JSONObject json = (JSONObject) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + id);
                if (json == null || ((long) ((JSONObject) json.get("categorySection")).get("gameCategoryId")) != 4471) throw new Exception("The ID "+id+" does not represent a modpack.");
                String slug = (String) json.get("slug");
                String name = (String) json.get("name");
                System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Installing ").a(name).a("...").reset());
                File packFolder = new File(Config.modpackDir.getPath() + File.separator +slug+"_"+id);
                if (packFolder.exists()) throw new Exception("Found old folder of "+name+". Installation of "+name+" cancelled.");
                packFolder.mkdir();
                String downloadUrl = ((String) ((JSONObject) Utils.getLast((JSONArray) json.get("latestFiles"))).get("downloadUrl")).replaceFirst("edge", "media");
                String loc = Utils.downloadFile(downloadUrl, packFolder.getPath());
                if (loc == null) throw new Exception("Failed to download modpack " + name);
                boolean success = Utils.unzip(loc);
                if (!success) throw new Exception("Failed to extract modpack content of "+name);
                File manifest = new File(packFolder + File.separator + "manifest.json");
                if (!manifest.exists()) throw new Exception("Cannot find modpack manifest of "+name);
                copyFromOverride(packFolder.getPath());
                downloadMods(packFolder.getPath());
                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Finished download of " + name).reset());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void copyFromOverride(String folder) throws IOException {
        File overrides = new File(folder + File.separator + "overrides");
        FileUtils.copyDirectory(overrides, new File(folder));
        FileUtils.deleteDirectory(overrides);
    }

    private static void downloadMods(String folder) {
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("Starting mod download").reset());
        try {
            File modsFolder = new File(folder + File.separator + "mods");
            if (!modsFolder.exists() || !modsFolder.isDirectory()) modsFolder.mkdir();
            File manifest = new File(folder + File.separator + "manifest.json");
            JSONObject json = (JSONObject) parser.parse(new FileReader(manifest));
            JSONArray array = (JSONArray) json.get("files");
            int i = 0, suc = 0, fai = 0;
            for (Object o : array) {
                String name = "";
                try {
                    JSONObject obj = (JSONObject) o;
                    long project = (long) obj.get("projectID");
                    long file = (long) obj.get("fileID");
                    JSONArray files = (JSONArray) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + project + "/files");
                    Optional f = files.stream().filter(o1 -> ((JSONObject) o1).get("id").equals(file)).findFirst();
                    if (!f.isPresent()) throw new Exception("Cannot find required file");
                    JSONObject j = (JSONObject) f.get();
                    name = (String) j.get("displayName");
                    String downloaded = Utils.downloadFile((String) j.get("downloadUrl"), folder + File.separator + "mods", ((String) j.get("fileName")).replace(".jar", "_" + project + "_" + file + ".jar"));
                    if (downloaded == null) throw new Exception("Failed to download mod " + name);
                    suc++;
                } catch (Exception e) {
                    fai++;
                    e.printStackTrace();
                } finally {
                    System.out.print(Ansi.ansi().fg(Ansi.Color.GREEN).a(String.format("[%d/%d] [S: %d | F: %d] Downloaded %s", ++i, array.size(), suc, fai, name)).reset()+"\r");
                    System.out.flush();
                }
            }
            System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(String.format("Iterated through %d projects. %d success, %d failed", i, suc, fai)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void delete(String[] ids) {
        Map<Integer, String> modpacks = Config.loadModpacks();
        Map<String, String> folders = new HashMap<>();
        for (String id : ids) {
            String name = null, packName = null;
            try {
                if (Utils.isInteger(id)) {
                    String slug = modpacks.getOrDefault(Integer.parseInt(id), null);
                    if (slug == null) throw new Exception("Cannot find modpack with ID " + id);
                    name = slug + "_" + id;
                    packName = slug;
                } else {
                    for (Map.Entry<Integer, String> entry : modpacks.entrySet()) if (entry.getValue().equalsIgnoreCase(id)) {
                        name = entry.getValue() + "_" + entry.getKey();
                        packName = entry.getValue();
                        break;
                    }
                    if (name == null) throw new Exception("Cannot find modpack with name " + id);
                }
                folders.put(name, packName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (folders.size() < 1) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Cannot find modpacks to delete."));
            return;
        }
        System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Modpacks we are going to delete:").reset());
        folders.forEach((key, value) -> System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(value).reset()));
        System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Are you sure you want to delete these modpacks? [y/n]").reset());
        Scanner scanner = new Scanner(System.in);
        String res = scanner.nextLine();
        if (!res.equalsIgnoreCase("y")) {
            System.out.println("Cancelled modpack deletion.");
            return;
        }
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Deleting " + folders.size() + " modpacks...").reset());
        AtomicInteger failed = new AtomicInteger();
        folders.forEach((key, value) -> {
            try {
                FileUtils.deleteDirectory(new File(Config.modpackDir.getPath() + File.separator + key));
            } catch (Exception e) {
                e.printStackTrace();
                failed.getAndIncrement();
            }
        });
        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a(String.format("%d success, %d failed", folders.size() - failed.get(), failed.get())).reset());
    }

    private static void list() {
        Map<Integer, String> modpacks = Config.loadModpacks();
        if (modpacks.isEmpty()) {
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("No modpack installed.").reset());
            return;
        }
        List<String> modpack = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : modpacks.entrySet()) {
            String folderName = entry.getValue() + "_" + entry.getKey();
            File manifest = new File(Config.modpackDir.getPath() + File.separator + folderName + File.separator + "manifest.json");
            if (!manifest.exists() || !manifest.isFile()) continue;
            try {
                JSONObject json = (JSONObject) parser.parse(new FileReader(manifest));
                String name = (String) json.get("name");
                String version = (String) json.get("version");
                String mcVer = (String) ((JSONObject) json.get("minecraft")).get("version");
                modpack.add(String.format("%s | %s | %s", name, version, mcVer));
            } catch (Exception ignored) { }
        }
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("Installed modpacks:"));
        modpack.forEach(s -> System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(s)));
        System.out.println(modpack.size() + " modpacks in total.");
    }

    private static void update(String[] ids) {
        Map<Integer, String> modpacks = Config.loadModpacks();
        for (String id : ids) {
            try {
                String slug = null, key = null;
                if (Utils.isInteger(id)) {
                    String ss = modpacks.getOrDefault(Integer.parseInt(id), null);
                    if (ss == null) throw new Exception("Cannot find modpack with ID " + id);
                    slug = ss;
                    key = id;
                } else {
                    for (Map.Entry<Integer, String> entry : modpacks.entrySet()) if (entry.getValue().equalsIgnoreCase(id)) {
                        slug = entry.getValue();
                        key = entry.getKey().toString();
                        break;
                    }
                    if (slug == null) throw new Exception("Cannot find modpack with name " + id);
                }
                JSONObject json = (JSONObject) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + key);
                if (json == null || ((long) ((JSONObject) json.get("categorySection")).get("gameCategoryId")) != 4471) throw new Exception("The ID "+id+" does not represent a modpack.");
                String name = (String) json.get("name");
                System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Updating ").a(name).a("...").reset());
                File packFolder = new File(Config.modpackDir.getPath() + File.separator +slug+"_"+key);
                JSONObject latest = (JSONObject) Utils.getLast((JSONArray) json.get("latestFiles"));
                String downloadUrl = ((String) latest.get("downloadUrl")).replaceFirst("edge", "media");
                String loc = Utils.downloadFile(downloadUrl, packFolder.getPath());
                if (loc == null) throw new Exception("Failed to download modpack " + name);
                boolean success = Utils.unzip(loc);
                if (!success) throw new Exception("Failed to extract modpack content of "+name);
                File manifest = new File(packFolder + File.separator + "manifest.json");
                if (!manifest.exists()) throw new Exception("Cannot find modpack manifest of "+name);
                FileUtils.cleanDirectory(new File(packFolder.getPath() + File.separator + "mods"));
                copyFromOverride(packFolder.getPath());
                downloadMods(packFolder.getPath());
                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Finished download of " + name).reset());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void convert(String[] ids) {
        Map<Integer, String> modpacks = Config.loadModpacks();
        for (String id : ids) {
            try {
                String slug = null, key = null;
                if (Utils.isInteger(id)) {
                    String ss = modpacks.getOrDefault(Integer.parseInt(id), null);
                    if (ss == null) throw new Exception("Cannot find modpack with ID " + id);
                    slug = ss;
                    key = id;
                } else {
                    for (Map.Entry<Integer, String> entry : modpacks.entrySet())
                        if (entry.getValue().equalsIgnoreCase(id)) {
                            slug = entry.getValue();
                            key = entry.getKey().toString();
                            break;
                        }
                    if (slug == null) throw new Exception("Cannot find modpack with name " + id);
                }
                File packFolder = new File(Config.modpackDir.getPath() + File.separator +slug+"_"+key);
                File manifest = new File(packFolder.getPath() + File.separator + "manifest.json");
                if (!manifest.exists() || !manifest.isFile()) throw new Exception("Modpack is missing manifest. Cannot convert to profile.");
                FileUtils.moveDirectoryToDirectory(packFolder, Config.profileDir, true);
                packFolder = new File(Config.profileDir.getPath() + File.separator +slug+"_"+key);
                manifest = new File(packFolder.getPath() + File.separator + "manifest.json");
                JSONObject json = (JSONObject) parser.parse(new FileReader(manifest));
                JSONObject profile = new JSONObject();
                JSONObject minecraft = (JSONObject) json.get("minecraft");
                String[] launcher = ((String) ((JSONObject) ((JSONArray) minecraft.get("modLoaders")).get(0)).get("id")).split("-", 2);
                profile.put("name", json.get("name"));
                profile.put("mcVer", minecraft.get("version"));
                profile.put("launcher", launcher[0]);
                profile.put("modVer", launcher[1]);
                PrintWriter pw = new PrintWriter(packFolder.getPath() + File.separator + "profile.json");
                pw.write(profile.toJSONString());

                pw.flush();
                pw.close();
                manifest.delete();
                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Converted modpack " + slug + " into profile."));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void printHelp(String prefix) {
        System.out.println(prefix + "modpack: Commands for modpack.");
        System.out.println(prefix + "\tinstall: Install a modpack.");
        System.out.println(prefix + "\t\targ <ID>: The ID or slug of the modpack.");
        System.out.println(prefix + "\tdelete: Delete a modpack.");
        System.out.println(prefix + "\t\targ <ID|Slug>: The ID or slug of the modpack.");
        System.out.println(prefix + "\tupdate: Update a modpack. Also act as repairing.");
        System.out.println(prefix + "\t\targ <ID|Slug>: The ID or slug of the modpack.");
        System.out.println(prefix + "\tlist: List all installed modpacks.");
        System.out.println(prefix + "\tconvert: Convert a modpack into a profile.");
        System.out.println(prefix + "\t\targ <ID|Slug>: The ID or slug of the modpack.");
    }
}
