package ml.northwestwind;

import org.apache.commons.io.FileUtils;
import org.fusesource.jansi.Ansi;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.nio.file.FileSystemException;
import java.rmi.NoSuchObjectException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
        else if (cmd.equalsIgnoreCase("update")) update(args, false);
        else if (cmd.equalsIgnoreCase("repair")) update(args, true);
        else if (cmd.equalsIgnoreCase("delete")) delete(args);
        else if (cmd.equalsIgnoreCase("convert")) convert(args);
        else Utils.invalid();
    }

    private static void install(String[] args) {
        for (String arg : args) {
            String[] ids = arg.split("_");
            String id = ids[0];
            if (!Utils.isInteger(id)) {
                System.err.println(id + " is not a valid modpack ID!");
                return;
            }
            String fileId = null;
            if (ids.length > 1) {
                fileId = ids[1];
                if (!Utils.isInteger(fileId)) {
                    System.err.println(fileId + " is not a valid modpack file ID!");
                    return;
                }
            }
            try {
                JSONObject json = Utils.runRetry(() -> (JSONObject) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + id));
                if (((long) json.get("classId")) != 4471)
                    throw new NoSuchObjectException("The ID " + id + " does not represent a modpack.");
                JSONObject files;
                if (fileId == null) {
                    files = (JSONObject) Utils.getLast((JSONArray) json.get("latestFiles"));
                } else {
                    String finalFileId = fileId;
                    files = Utils.runRetry(() -> (JSONObject) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + id + "/files/" + finalFileId));
                }
                String slug = (String) json.get("slug");
                String name = (String) json.get("name");
                System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Installing ").a(name).a("...").reset());
                File packFolder = new File(Config.modpackDir.getAbsolutePath() + File.separator + slug + "_" + id + (fileId != null ? "_" + fileId : ""));
                if (packFolder.exists()) {
                    Scanner scanner = new Scanner(System.in);
                    System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Found old folder of ").a(name).a(". If you want to re-install while keeping your files, use \"repair\" instead.").reset());
                    System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Proceed anyway? ").fg(Ansi.Color.RED).a("(This will delete the entire folder!)").reset().a(" [y/n] "));
                    String res = scanner.nextLine();
                    if (!res.equalsIgnoreCase("y")) {
                        System.out.println("Cancelled modpack installation.");
                        return;
                    }
                    System.out.println("Deleting old folder...");
                    FileUtils.deleteDirectory(packFolder);
                }
                packFolder.mkdir();
                String downloadUrl = (String) files.get("downloadUrl");
                if (downloadUrl == null) {
                    int parsed = fileId != null ? Integer.parseInt(fileId) : (int) (long) files.get("id");
                    int first = parsed / 1000;
                    downloadUrl = String.format("https://edge.forgecdn.net/files/%d/%d/%s", first, parsed - first * 1000, files.get("fileName"));
                }
                String loc = Utils.downloadFile(downloadUrl, packFolder.getAbsolutePath());
                if (loc == null) throw new SyncFailedException("Failed to download modpack " + name);
                boolean success = Utils.unzip(loc);
                if (!success) throw new FileSystemException("Failed to extract modpack content of " + name);
                File manifest = new File(packFolder + File.separator + getManifestFile(files));
                if (!manifest.exists()) throw new FileNotFoundException("Cannot find modpack manifest of " + name);
                JSONObject manifestJson = (JSONObject) parser.parse(new FileReader(manifest));
                copyFromOverride(packFolder.getAbsolutePath(), (String) manifestJson.get("overrides"));
                downloadMods(packFolder.getAbsolutePath());
                String thumb = downloadThumb(json);
                genProfile(name, packFolder.getAbsolutePath(), thumb, getModVersion(manifestJson));
                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Finished download of " + name).reset());
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
            }
        }
    }

    public static void copyFromOverride(String folder, String overridesName) throws IOException {
        File overrides = new File(folder + File.separator + overridesName);
        if (!overrides.exists()) return;
        FileUtils.copyDirectory(overrides, new File(folder));
        FileUtils.deleteDirectory(overrides);
    }

    public static void downloadMods(String folder) {
        downloadMods(folder, false);
    }

    public static void downloadMods(String folder, boolean force) {
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("Starting mod download").reset());
        try {
            File modsFolder = new File(folder + File.separator + "mods");
            if (!modsFolder.exists() || !modsFolder.isDirectory()) modsFolder.mkdir();
            Map<String, String> mods = Utils.getAllMods(modsFolder.getAbsolutePath());
            Map<String, String> modNames = Utils.getAllModNames(modsFolder.getAbsolutePath());
            File manifest = new File(folder + File.separator + "manifest.json");
            JSONObject json = (JSONObject) parser.parse(new FileReader(manifest));
            JSONArray array = (JSONArray) json.get("files");
            int i = 0, suc = 0, fai = 0, ski = 0, lastSuc = 0, lastFai = 0, lastSki = 0;
            Map<String, String> failed = new HashMap<>();
            Set<String> exists = new HashSet<>();
            for (Object o : array) {
                String name = "";
                JSONObject obj = (JSONObject) o;
                String project = Long.toString((long) obj.get("projectID"));
                String file = Long.toString((long) obj.get("fileID"));
                try {
                    if (!force && mods.containsKey(project) && mods.get(project).equalsIgnoreCase(file)) ski++;
                    else {
                        JSONObject j = Utils.runRetry(() -> (JSONObject) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + project + "/files/" + file));
                        name = (String) j.get("displayName");
                        String downloadUrl = (String) j.get("downloadUrl");
                        if (downloadUrl == null) {
                            long parsed = (long) obj.get("fileID");
                            long first = parsed / 1000;
                            downloadUrl = String.format("https://edge.forgecdn.net/files/%d/%d/%s", first, parsed - first * 1000, j.get("fileName"));
                        }
                        String downloaded = Utils.downloadFile(downloadUrl, folder + File.separator + "mods", ((String) j.get("fileName")).replace(".jar", "_" + project + "_" + file + ".jar"));
                        if (downloaded == null) throw new SyncFailedException("Failed to download mod " + name);
                        suc++;
                    }
                } catch (Exception e) {
                    fai++;
                    failed.put(project, file);
                    if (!Config.silentExceptions) e.printStackTrace();
                } finally {
                    exists.add(project);
                    if (suc > lastSuc) {
                        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a(String.format("[%d/%d] [S/F/S: %d/%d/%d] Downloaded %s", ++i, array.size(), suc, fai, ski, name)));
                        File oldFile = new File(modsFolder.getAbsolutePath() + File.separator + modNames.get(project));
                        if (oldFile.exists() && oldFile.isFile()) oldFile.delete();
                    } else if (fai > lastFai) System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a(String.format("[%d/%d] [S/F/S: %d/%d/%d] Failed to download %s", ++i, array.size(), suc, fai, ski, name)));
                    else if (ski > lastSki) System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(String.format("[%d/%d] [S/F/S: %d/%d/%d] Skipped %s", ++i, array.size(), suc, fai, ski, name)));
                    lastSuc = suc;
                    lastFai = fai;
                    lastSki = ski;
                }
            }
            mods.keySet().stream().filter(key -> !exists.contains(key)).forEach(key -> {
                File oldFile = new File(modsFolder.getAbsolutePath() + File.separator + modNames.get(key));
                if (oldFile.exists() && oldFile.isFile()) oldFile.delete();
            });
            System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(String.format("Iterated through %d projects. %d success, %d failed, %d skipped", i, suc, fai, ski)));
            if (fai > 0) {
                System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Mods that failed to update:").reset());
                for (Map.Entry<String, String> entry : failed.entrySet())
                    System.out.println(Ansi.ansi().a("Project: ").fg(Ansi.Color.YELLOW).a(entry.getKey()).reset().a(", File: ").fg(Ansi.Color.CYAN).a(entry.getValue()).reset());
            }
        } catch (Exception e) {
            if (!Config.silentExceptions) e.printStackTrace();
        }
    }

    // Pass in latestFiles' last element
    private static String getManifestFile(JSONObject latest) {
        if (latest.containsKey("modules")) {
            JSONArray modules = (JSONArray) latest.get("modules");
            Optional optional = modules.stream().filter(obj -> ((long) ((JSONObject) obj).getOrDefault("type", 0L)) == 3).findFirst();
            if (optional.isPresent()) return (String) ((JSONObject) optional.get()).get("foldername");
        }
        return "manifest.json";
    }

    // Get mod loader version from manifest
    private static String getModVersion(JSONObject manifest) {
        if (manifest.containsKey("minecraft")) {
            JSONObject minecraft = (JSONObject) manifest.get("minecraft");
            if (minecraft.containsKey("version") && minecraft.containsKey("modLoaders")) {
                JSONArray modLoaders = (JSONArray) minecraft.get("modLoaders");
                Optional optional = modLoaders.stream().filter(obj -> (boolean) ((JSONObject) obj).getOrDefault("primary", false)).findFirst();
                if (optional.isPresent()) {
                    String version = (String) minecraft.get("version");
                    String id = (String) ((JSONObject) optional.get()).get("id");
                    String[] splitted = id.split("-");
                    String loader = splitted[0];
                    String modVer = Arrays.stream(splitted).skip(1).collect(Collectors.joining("-"));
                    if (loader.equalsIgnoreCase("forge")) id = version + "-forge-" + modVer;
                    else if (loader.equalsIgnoreCase("fabric")) id = "fabric-loader-" + modVer + "-" + version;
                    else id = null;
                    return id;
                }
            }
        }
        return null;
    }

    private static String downloadThumb(JSONObject json) {
        if (!(json.get("attachments") instanceof JSONArray) || !(((JSONArray) json.get("attachments")).get(0) instanceof JSONObject))
            return null;
        JSONObject attachment = (JSONObject) ((JSONArray) json.get("attachments")).get(0);
        if (!attachment.containsKey("url")) return null;
        try {
            if (!Config.tempDir.exists()) Config.tempDir.mkdir();
            return Utils.downloadFile((String) attachment.get("url"), Config.tempDir.getAbsolutePath(), "thumbnail.png");
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void genProfile(String name, String path, String icon, String loader) {
        String base64 = null;
        if (icon != null) {
            try {
                byte[] fileContent = FileUtils.readFileToByteArray(new File(icon));
                base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(fileContent);
            } catch (Exception ignored) {
            }
            try {
                FileUtils.deleteDirectory(Config.tempDir);
            } catch (Exception ignored) {
            }
        }
        try {
            File profileFile = new File(Utils.getMinecraftPath() + File.separator + "launcher_profiles.json");
            if (!profileFile.exists() || !profileFile.isFile()) return;
            JSONObject json = (JSONObject) parser.parse(new FileReader(profileFile));
            JSONObject profiles = (JSONObject) json.get("profiles");
            JSONObject profile = new JSONObject();
            profile.put("created", LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE));
            profile.put("gameDir", path);
            if (base64 == null) profile.put("icon", "Furnace_On");
            else profile.put("icon", base64);
            int memory = (int) Math.ceil(Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0 / 1024.0);
            profile.put("javaArgs", String.format("-Xmx%dG -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M", memory));
            profile.put("lastUsed", "1970-01-01T00:00:00.000Z");
            profile.put("lastVersionId", loader == null ? "latest-release" : loader);
            profile.put("name", name);
            profile.put("type", "custom");
            profiles.put(UUID.randomUUID(), profile);
            json.put("profiles", profiles);

            PrintWriter pw = new PrintWriter(profileFile);
            pw.write(json.toJSONString());

            pw.flush();
            pw.close();
            System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Created installation profile for " + name + ".").reset());
            System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(" However, the mod loader may not be configured correctly, especially for older versions. Please open/restart your Minecraft Launcher to edit it. Installation of mod loader might be needed, and can be downloaded in the following:").reset());
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Forge: ").fg(Ansi.Color.CYAN).a("https://files.minecraftforge.net/").reset());
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Fabric: ").fg(Ansi.Color.CYAN).a("https://fabricmc.net/use/installer/").reset());
        } catch (Exception e) {
            if (!Config.silentExceptions) e.printStackTrace();
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Failed to generate installation profile for " + name + ". Please be reminded to make one yourself.").reset());
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
                    if (slug == null) throw new NoSuchObjectException("Cannot find modpack with ID " + id);
                    name = slug + "_" + id;
                    packName = slug;
                } else {
                    for (Map.Entry<Integer, String> entry : modpacks.entrySet())
                        if (entry.getValue().equalsIgnoreCase(id)) {
                            name = entry.getValue() + "_" + entry.getKey();
                            packName = entry.getValue();
                            break;
                        }
                    if (name == null) throw new NoSuchObjectException("Cannot find modpack with name " + id);
                }
                folders.put(name, packName);
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
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
                FileUtils.deleteDirectory(new File(Config.modpackDir.getAbsolutePath() + File.separator + key));
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
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
        List<Ansi> modpack = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : modpacks.entrySet()) {
            String folderName = entry.getValue() + "_" + entry.getKey();
            File manifest = new File(Config.modpackDir.getAbsolutePath() + File.separator + folderName + File.separator + "manifest.json");
            if (!manifest.exists() || !manifest.isFile()) continue;
            try {
                JSONObject json = (JSONObject) parser.parse(new FileReader(manifest));
                String name = (String) json.get("name");
                String version = (String) json.get("version");
                String mcVer = (String) ((JSONObject) json.get("minecraft")).get("version");
                modpack.add(Ansi.ansi().fg(Ansi.Color.YELLOW).a(name).reset().a(" | ").fg(Ansi.Color.CYAN).a(version).reset().a(" | ").fg(Ansi.Color.MAGENTA).a(mcVer).reset().a(" | ").fg(Ansi.Color.MAGENTA).a(entry.getKey()));
            } catch (Exception ignored) {
            }
        }
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("Installed modpacks:"));
        modpack.forEach(System.out::println);
        System.out.println(modpack.size() + " modpacks in total.");
    }

    private static void update(String[] ids, boolean force) {
        Map<Integer, String> modpacks = Config.loadModpacks();
        for (String id : ids) {
            try {
                String slug = null, key = null;
                if (Utils.isInteger(id)) {
                    String ss = modpacks.getOrDefault(Integer.parseInt(id), null);
                    if (ss == null) throw new NoSuchObjectException("Cannot find modpack with ID " + id);
                    slug = ss;
                    key = id;
                } else {
                    for (Map.Entry<Integer, String> entry : modpacks.entrySet())
                        if (entry.getValue().equalsIgnoreCase(id)) {
                            slug = entry.getValue();
                            key = entry.getKey().toString();
                            break;
                        }
                    if (slug == null) throw new NoSuchObjectException("Cannot find modpack with name " + id);
                }
                String finalKey = key;
                JSONObject json = Utils.runRetry(() -> (JSONObject) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + finalKey));
                if (((long) json.get("classId")) != 4471)
                    throw new NoSuchObjectException("The ID " + id + " does not represent a modpack.");
                String name = (String) json.get("name");
                System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Updating ").a(name).a("...").reset());
                File packFolder = new File(Config.modpackDir.getAbsolutePath() + File.separator + slug + "_" + key);
                JSONObject latest = (JSONObject) Utils.getLast((JSONArray) json.get("latestFiles"));
                String downloadUrl = (String) latest.get("downloadUrl");
                if (downloadUrl == null) {
                    long parsed = (long) latest.get("id");
                    long first = parsed / 1000;
                    downloadUrl = String.format("https://edge.forgecdn.net/files/%d/%d/%s", first, parsed - first * 1000, latest.get("fileName"));
                }
                String loc = Utils.downloadFile(downloadUrl, packFolder.getAbsolutePath());
                if (loc == null) throw new SyncFailedException("Failed to download modpack " + name);
                boolean success = Utils.unzip(loc);
                if (!success) throw new FileSystemException("Failed to extract modpack content of " + name);
                File manifest = new File(packFolder + File.separator + "manifest.json");
                if (!manifest.exists()) throw new FileNotFoundException("Cannot find modpack manifest of " + name);
                if (force) FileUtils.cleanDirectory(new File(packFolder.getAbsolutePath() + File.separator + "mods"));
                copyFromOverride(packFolder.getAbsolutePath(), (String) ((JSONObject) parser.parse(new FileReader(manifest))).get("overrides"));
                downloadMods(packFolder.getAbsolutePath(), force);
                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Finished download of " + name).reset());
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
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
                    if (ss == null) throw new NoSuchObjectException("Cannot find modpack with ID " + id);
                    slug = ss;
                    key = id;
                } else {
                    for (Map.Entry<Integer, String> entry : modpacks.entrySet())
                        if (entry.getValue().equalsIgnoreCase(id)) {
                            slug = entry.getValue();
                            key = entry.getKey().toString();
                            break;
                        }
                    if (slug == null) throw new NoSuchObjectException("Cannot find modpack with name " + id);
                }
                File packFolder = new File(Config.modpackDir.getAbsolutePath() + File.separator + slug + "_" + key);
                File manifest = new File(packFolder.getAbsolutePath() + File.separator + "manifest.json");
                if (!manifest.exists() || !manifest.isFile())
                    throw new FileNotFoundException("Modpack is missing manifest. Cannot convert to profile.");
                FileUtils.moveDirectoryToDirectory(packFolder, Config.profileDir, true);
                packFolder = new File(Config.profileDir.getAbsolutePath() + File.separator + slug + "_" + key);
                manifest = new File(packFolder.getAbsolutePath() + File.separator + "manifest.json");
                JSONObject json = (JSONObject) parser.parse(new FileReader(manifest));
                JSONObject profile = new JSONObject();
                JSONObject minecraft = (JSONObject) json.get("minecraft");
                String[] launcher = ((String) ((JSONObject) ((JSONArray) minecraft.get("modLoaders")).get(0)).get("id")).split("-", 2);
                profile.put("name", json.get("name"));
                profile.put("mcVer", minecraft.get("version"));
                profile.put("launcher", launcher[0]);
                profile.put("modVer", launcher[1]);
                PrintWriter pw = new PrintWriter(packFolder.getAbsolutePath() + File.separator + "profile.json");
                pw.write(profile.toJSONString());

                pw.flush();
                pw.close();
                manifest.delete();
                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Converted modpack " + slug + " into profile."));
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
            }
        }
    }

    public static void printHelp(String prefix) {
        System.out.println(prefix + "modpack: Commands for modpack.");
        System.out.println(prefix + "\tinstall: Install a modpack.");
        System.out.println(prefix + "\t\targ <ID[_FileID]>: The ID of modpacks.");
        System.out.println(prefix + "\t\t\t If an argument is in the format of ID_FileID, where FileID is the ID of a specific modpack ZIP, that specific modpack ZIP will be installed.");
        System.out.println(prefix + "\tdelete: Delete a modpack.");
        System.out.println(prefix + "\t\targ <ID|Slug>: The ID or slug of the modpack. Can be multiple IDs or slugs.");
        System.out.println(prefix + "\tupdate: Update a modpack.");
        System.out.println(prefix + "\t\targ <ID|Slug>: The ID or slug of the modpack. Can be multiple IDs or slugs.");
        System.out.println(prefix + "\trepair: Repair a modpack.");
        System.out.println(prefix + "\t\targ <ID|Slug>: The ID or slug of the modpack. Can be multiple IDs or slugs.");
        System.out.println(prefix + "\tlist: List all installed modpacks.");
        System.out.println(prefix + "\tconvert: Convert a modpack into a profile.");
        System.out.println(prefix + "\t\targ <ID|Slug>: The ID or slug of the modpack. Can be multiple IDs or slugs.");
    }
}
