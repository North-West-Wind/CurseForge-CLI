package ml.northwestwind;

import org.apache.commons.io.FileUtils;
import org.fusesource.jansi.Ansi;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.nio.file.FileSystemException;
import java.rmi.NoSuchObjectException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

public class Profile {
    private static final JSONParser parser = new JSONParser();

    public static void run(String[] args) {
        String cmd = args[1];
        if (args.length < 3) {
            if (cmd.equalsIgnoreCase("create")) create();
            else Utils.invalid();
            return;
        }
        args = Arrays.stream(args).skip(2).toArray(String[]::new);
        if (cmd.equalsIgnoreCase("add")) add(args);
        else if (cmd.equalsIgnoreCase("remove")) remove(args);
        else if (cmd.equalsIgnoreCase("update")) update(args);
        else if (cmd.equalsIgnoreCase("export")) export(args);
        else if (cmd.equalsIgnoreCase("edit")) edit(args[0]);
        else if (cmd.equalsIgnoreCase("delete")) delete(args);
        else if (cmd.equals("import")) importProf(args);
        else Utils.invalid();
    }

    private static void create() {
        Scanner scanner = new Scanner(System.in);
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("Creating modpack profile... You will need to answer a few questions.").reset());
        System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Name: ").reset());
        String name = null;
        while (name == null) {
            name = scanner.nextLine();
            if (name.isEmpty()) {
                System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Name cannot be empty. Name: ").reset());
                name = null;
            }
        }
        System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Minecraft version [1.x.x/1.x.x-Snapshot]: ").reset());
        String mcVer = null;
        while (mcVer == null) {
            mcVer = scanner.nextLine();
            if (!Utils.isMCVersionValid(mcVer)) {
                mcVer = null;
                System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("The version is invalid. Minecraft version: ").reset());
            }
        }
        System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Mod launcher [forge/fabric]: ").reset());
        String launcher = null;
        while (launcher == null) {
            launcher = scanner.nextLine().toLowerCase();
            if (!launcher.equalsIgnoreCase("forge") && !launcher.equalsIgnoreCase("fabric")) {
                launcher = null;
                System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("The launcher is invalid. Mod launcher [forge/fabric]: ").reset().a("\r"));
            }
        }
        System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Mod launcher version: ").reset());
        String modVer = scanner.nextLine();
        File profile = new File(Config.profileDir.getAbsolutePath() + File.separator + name.toLowerCase().replaceAll("[^\\w]+", "-"));
        if (profile.exists()) {
            System.out.println(name + " already exists.");
            return;
        }
        profile.mkdir();
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("mcVer", mcVer);
        json.put("modVer", modVer);
        json.put("launcher", launcher);
        try {
            PrintWriter pw = new PrintWriter(profile.getAbsolutePath() + File.separator + "profile.json");
            pw.write(json.toJSONString());

            pw.flush();
            pw.close();
            System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Created profile " + name));
            System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("The slug of the profile is ").a(profile.getName()));
        } catch (Exception e) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Failed to create profile " + profile));
            if (!Config.silentExceptions) e.printStackTrace();
        }
    }

    private static void edit(String profile) {
        File config = new File(Config.profileDir.getAbsolutePath() + File.separator + profile + File.separator + "profile.json");
        if (!config.exists()) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Cannot find config of profile " + profile));
            return;
        }
        JSONObject json;
        try {
            json = (JSONObject) parser.parse(new FileReader(config));
        } catch (IOException | ParseException e) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Cannot read config of profile " + profile));
            if (!Config.silentExceptions) e.printStackTrace();
            return;
        }
        String name = (String) json.getOrDefault("name", "");
        String mcVer = (String) json.getOrDefault("mcVer", "");
        String launcher = (String) json.getOrDefault("launcher", "");
        String modVer = (String) json.getOrDefault("modVer", "");
        String nName = null, nMcVer = null, nModVer = null, nLauncher = null;
        Scanner scanner = new Scanner(System.in);
        System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("Editing profile " + profile));
        System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Name (" + name + "): ").reset());
        while (nName == null) {
            String received = scanner.nextLine();
            if (received.isEmpty() && name.isEmpty()) {
                System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Name cannot be empty. Name (" + name + "): ").reset());
            } else nName = received.isEmpty() ? name : received;
        }
        System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Minecraft version (" + mcVer + "): ").reset());
        while (nMcVer == null) {
            String received = scanner.nextLine();
            if (((received.isEmpty() || !Utils.isMCVersionValid(received)) && mcVer.isEmpty())) {
                System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("The version is invalid. Minecraft version (" + mcVer + "): ").reset());
            } else nMcVer = received.isEmpty() ? mcVer : received;
        }
        System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Mod launcher [forge/fabric] (" + launcher + "): ").reset());
        while (nLauncher == null) {
            String received = scanner.nextLine().toLowerCase();
            if (((received.isEmpty() || (!received.equals("forge") && !received.equals("fabric"))) && launcher.isEmpty())) {
                System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("The launcher is invalid. Mod launcher [forge/fabric] (" + launcher + "): ").reset());
            } else nLauncher = received.isEmpty() ? launcher : received;
        }
        System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Mod launcher version (" + modVer + "): ").reset());
        while (nModVer == null) {
            String received = scanner.nextLine();
            if (received.isEmpty() && modVer.isEmpty()) {
                System.out.print(Ansi.ansi().fg(Ansi.Color.YELLOW).a("The launcher version is invalid. Mod launcher version (" + modVer + "): ").reset());
            } else nModVer = received.isEmpty() ? modVer : received;
        }
        json.put("name", nName);
        json.put("mcVer", nMcVer);
        json.put("modVer", nModVer);
        json.put("launcher", nLauncher);
        try {
            PrintWriter pw = new PrintWriter(config.getAbsolutePath());
            pw.write(json.toJSONString());

            pw.flush();
            pw.close();
            System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Edited profile " + profile));
        } catch (Exception e) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Failed to edit profile " + profile));
            if (!Config.silentExceptions) e.printStackTrace();
        }
    }

    private static void delete(String[] names) {
        List<String> profiles = Config.loadProfiles();
        List<String> folders = new ArrayList<>();
        for (String name : names) {
            try {
                if (!profiles.contains(name)) throw new NoSuchObjectException("Cannot find profile with name " + name);
                folders.add(name);
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
            }
        }
        if (folders.size() < 1) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Cannot find profile to delete."));
            return;
        }
        System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Profiles we are going to delete:").reset());
        folders.forEach((s) -> System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(s).reset()));
        System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Are you sure you want to delete these profiles? [y/n]").reset());
        Scanner scanner = new Scanner(System.in);
        String res = scanner.nextLine();
        if (!res.equalsIgnoreCase("y")) {
            System.out.println("Cancelled profile deletion.");
            return;
        }
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Deleting " + folders.size() + " profiles...").reset());
        AtomicInteger failed = new AtomicInteger();
        folders.forEach((s) -> {
            try {
                FileUtils.deleteDirectory(new File(Config.profileDir.getAbsolutePath() + File.separator + s));
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
                failed.getAndIncrement();
            }
        });
        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a(String.format("%d success, %d failed", folders.size() - failed.get(), failed.get())).reset());
    }

    private static void add(String[] ids) {
        String profile = ids[0];
        List<String> profiles = Config.loadProfiles();
        if (!profiles.contains(profile)) {
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Profile " + profile + " does not exist."));
            return;
        }
        File profileConfig = new File(Config.profileDir.getAbsolutePath() + File.separator + profile + File.separator + "profile.json");
        if (!profileConfig.exists() || !profileConfig.isFile()) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Profile config is missing!"));
            return;
        }
        JSONObject config;
        try {
            config = (JSONObject) parser.parse(new FileReader(profileConfig));
        } catch (ParseException | IOException e) {
            if (!Config.silentExceptions) e.printStackTrace();
            return;
        }
        File modsFolder = new File(Config.profileDir.getAbsolutePath() + File.separator + profile + File.separator + "mods");
        if (!modsFolder.exists() || !modsFolder.isDirectory()) modsFolder.mkdir();
        Map<String, String> mods = Utils.getAllMods(modsFolder.getAbsolutePath());
        Map<String, String> modNames = Utils.getAllModNames(modsFolder.getAbsolutePath());
        for (String arg : Arrays.stream(ids).skip(1).toArray(String[]::new)) {
            try {
                String[] modIds = arg.split("_");
                String id = modIds[0], fileId = null;
                if (modIds.length > 1) {
                    fileId = modIds[1];
                    if (!Utils.isInteger(fileId)) throw new NoSuchObjectException("Mod file ID is invalid: " + fileId);
                }
                if (!Utils.isInteger(id)) throw new NoSuchObjectException("Mod ID is invalid: " + id);
                JSONObject json = Utils.runRetry(() -> (JSONObject) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + id));
                if (((long) json.get("classId")) != 6)
                    throw new NoSuchObjectException("The ID " + id + " does not represent a mod.");
                JSONObject bestFile;
                if (fileId == null) {
                    JSONArray files = Utils.runRetry(() -> (JSONArray) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + id + "/files"));
                    List f = (List) files.stream().filter(o -> Utils.checkVersion((JSONArray) ((JSONObject) o).get("gameVersions"), config)).collect(Collectors.toList());
                    f.sort((a, b) -> (int) ((long) ((JSONObject) b).get("id") - (long) ((JSONObject) a).get("id")));
                    if (f.size() < 1)
                        throw new InputMismatchException("No available file of " + id + " found for this profile.");
                    bestFile = (JSONObject) f.get(0);
                } else {
                    String finalFileId = fileId;
                    bestFile = Utils.runRetry(() -> (JSONObject) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + id + "/files/" + finalFileId));
                }
                String downloadUrl = (String) bestFile.get("downloadUrl");
                if (downloadUrl == null) {
                    long parsed = (long) bestFile.get("id");
                    long first = parsed / 1000;
                    downloadUrl = String.format("https://edge.forgecdn.net/files/%d/%d/%s", first, parsed - first * 1000, bestFile.get("fileName"));
                }
                if (mods.containsKey(id)) {
                    File oldFile = new File(modsFolder.getAbsolutePath() + File.separator + modNames.get(id));
                    if (oldFile.exists() && oldFile.isFile()) oldFile.delete();
                }
                String loc = Utils.downloadFile(downloadUrl, modsFolder.getAbsolutePath(), ((String) bestFile.get("fileName")).replace(".jar", "_" + id + "_" + bestFile.get("id") + ".jar"));
                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Downloaded " + loc));
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
            }
        }
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Finished all mod downloads."));
    }

    private static void remove(String[] ids) {
        String profile = ids[0];
        List<String> profiles = Config.loadProfiles();
        if (!profiles.contains(profile)) {
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Profile " + profile + " does not exist."));
            return;
        }
        File modsFolder = new File(Config.profileDir.getAbsolutePath() + File.separator + profile + File.separator + "mods");
        Map<String, String> mods = Utils.getAllMods(modsFolder.getAbsolutePath());
        Map<String, String> modNames = Utils.getAllModNames(modsFolder.getAbsolutePath());
        Set<String> files = new HashSet<>();
        for (String id : Arrays.stream(ids).skip(1).toArray(String[]::new)) {
            String filename = null;
            try {
                if (Utils.isInteger(id)) {
                    if (!mods.containsKey(id)) throw new NoSuchObjectException("Cannot find mod with ID " + id);
                    filename = modNames.get(id);
                } else {
                    for (String key : mods.keySet()) {
                        String[] split = key.split("_");
                        if (String.join("", Arrays.copyOf(split, split.length - 2)).equalsIgnoreCase(id)) {
                            filename = modNames.get(key);
                            break;
                        }
                    }
                    if (filename == null) throw new NoSuchObjectException("Cannot find mod with name " + id);
                }
                files.add(filename);
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
            }
        }
        if (files.size() < 1) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Cannot find mods to remove."));
            return;
        }
        System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Mods we are going to remove:").reset());
        files.forEach(file -> System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(file).reset()));
        System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Are you sure you want to remove these mods? [y/n]").reset());
        Scanner scanner = new Scanner(System.in);
        String res = scanner.nextLine();
        if (!res.equalsIgnoreCase("y")) {
            System.out.println("Cancelled mod removal.");
            return;
        }
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Removing " + files.size() + " mods...").reset());
        AtomicInteger failed = new AtomicInteger();
        files.forEach(file -> {
            try {
                File oldFile = new File(Config.profileDir + File.separator + profile + File.separator + "mods" + File.separator + file);
                if (oldFile.exists() && oldFile.isFile()) oldFile.delete();
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
                failed.getAndIncrement();
            }
        });
        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a(String.format("%d success, %d failed", files.size() - failed.get(), failed.get())).reset());
    }

    private static void export(String[] args) {
        String profile = args[0];
        if (!Config.loadProfiles().contains(profile)) {
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Profile " + profile + " does not exist."));
            return;
        }
        File profFolder = new File(Config.profileDir.getAbsolutePath() + File.separator + profile);
        File profileConfig = new File(profFolder.getAbsolutePath() + File.separator + "profile.json");
        if (!profileConfig.exists() || !profileConfig.isFile()) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Profile config is missing!"));
            return;
        }
        JSONObject config;
        try {
            config = (JSONObject) parser.parse(new FileReader(profileConfig));
        } catch (ParseException | IOException e) {
            if (!Config.silentExceptions) e.printStackTrace();
            return;
        }
        File modsFolder = new File(profFolder.getAbsolutePath() + File.separator + "mods");
        if (!Config.tempDir.exists()) Config.tempDir.mkdir();
        File modsCopy = new File(Config.tempDir.getAbsolutePath() + File.separator + "mods");
        try {
            FileUtils.copyDirectoryToDirectory(modsFolder, Config.tempDir);
        } catch (Exception e) {
            if (!Config.silentExceptions) e.printStackTrace();
            return;
        }
        Map<String, String> mods = Utils.getAllMods(modsCopy.getAbsolutePath());
        Map<String, String> modName = Utils.getAllModNames(modsCopy.getAbsolutePath());
        JSONArray files = new JSONArray();
        for (Map.Entry<String, String> mod : mods.entrySet()) {
            JSONObject jo = new JSONObject();
            jo.put("projectID", Long.parseLong(mod.getKey()));
            jo.put("fileID", Long.parseLong(mod.getValue()));
            jo.put("required", true);
            files.add(jo);
            File oldFile = new File(modsCopy.getAbsolutePath() + File.separator + modName.get(mod.getKey()));
            if (oldFile.exists() && oldFile.isFile()) oldFile.delete();
        }
        System.out.println("What is the version of this export?");
        Scanner scanner = new Scanner(System.in);
        String version = scanner.nextLine();
        JSONObject mjo = new JSONObject();
        mjo.put("files", files);
        mjo.put("author", "");
        mjo.put("version", version);
        mjo.put("name", config.get("name"));
        mjo.put("manifestVersion", 1);
        mjo.put("manifestType", "minecraftModpack");
        JSONObject minejo = new JSONObject();
        minejo.put("version", config.get("mcVer"));
        JSONArray loaders = new JSONArray();
        JSONObject loader = new JSONObject();
        loader.put("id", config.get("launcher") + "-" + config.get("modVer"));
        loader.put("primary", true);
        loaders.add(loader);
        minejo.put("modLoaders", loaders);
        mjo.put("minecraft", minejo);
        mjo.put("overrides", "overrides");
        File manifest = new File(Config.tempDir.getAbsolutePath() + File.separator + "manifest.json");
        try {
            PrintWriter pw = new PrintWriter(manifest.getAbsolutePath());
            pw.write(mjo.toJSONString());

            pw.flush();
            pw.close();
        } catch (Exception e) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Failed to create manifest.json."));
            if (!Config.silentExceptions) e.printStackTrace();
            return;
        }
        List<String> overridesDir = new ArrayList<>(), overridesFile = new ArrayList<>();
        for (int ii = 1; ii < args.length; ii++) {
            String path = profFolder.getAbsolutePath() + File.separator + args[ii];
            File f = new File(path);
            if (!f.exists()) continue;
            if (f.isDirectory()) overridesDir.add(args[ii]);
            else overridesFile.add(args[ii]);
        }
        File overridesFolder = new File(Config.tempDir.getAbsolutePath() + File.separator + "overrides");
        overridesFolder.mkdir();
        try {
            for (String dir : overridesDir) {
                File directory = new File(overridesFolder.getAbsolutePath() + File.separator + dir);
                directory.mkdirs();
                FileUtils.copyDirectory(new File(profFolder.getAbsolutePath() + File.separator + dir), directory);
            }
            for (String file : overridesFile) {
                File file1 = new File(overridesFolder.getAbsolutePath() + File.separator + file);
                File parent = file1.getParentFile();
                parent.mkdirs();
                FileUtils.copyFileToDirectory(new File(profFolder.getAbsolutePath() + File.separator + file), parent);
            }
            if (modsCopy.listFiles().length > 0) {
                File overrideMods = new File(overridesFolder.getAbsolutePath() + File.separator + "mods");
                if (!overrideMods.exists() || !overrideMods.isDirectory()) overrideMods.mkdir();
                FileUtils.copyDirectory(modsCopy, overrideMods);
            }
        } catch (Exception e) {
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Failed to copy files to overrides."));
            if (!Config.silentExceptions) e.printStackTrace();
            return;
        }
        String zipName = config.get("name") + "-" + version + ".zip";
        try {
            FileOutputStream fos = new FileOutputStream(Config.exportDir.getAbsolutePath() + File.separator + zipName);
            ZipOutputStream zipOut = new ZipOutputStream(fos);

            Utils.zip(overridesFolder, overridesFolder.getName(), zipOut);
            Utils.zip(manifest, manifest.getName(), zipOut);
            zipOut.close();
            fos.close();
        } catch (Exception e) {
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Failed to zip everything."));
            if (!Config.silentExceptions) e.printStackTrace();
            return;
        }
        try {
            FileUtils.deleteDirectory(Config.tempDir);
        } catch (Exception e) {
            System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Failed to clean up, but we can keep running."));
            if (!Config.silentExceptions) e.printStackTrace();
        }
        System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Exported profile " + profile + " to " + zipName));
    }

    private static void update(String[] args) {
        String profile = args[0];
        if (!Config.loadProfiles().contains(profile)) {
            System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Profile " + profile + " does not exist."));
            return;
        }
        File profileConfig = new File(Config.profileDir.getAbsolutePath() + File.separator + profile + File.separator + "profile.json");
        if (!profileConfig.exists() || !profileConfig.isFile()) {
            System.err.println(Ansi.ansi().fg(Ansi.Color.RED).a("Profile config is missing!"));
            return;
        }
        JSONObject config;
        try {
            config = (JSONObject) parser.parse(new FileReader(profileConfig));
        } catch (ParseException | IOException e) {
            if (!Config.silentExceptions) e.printStackTrace();
            return;
        }
        File modsFolder = new File(Config.profileDir.getAbsolutePath() + File.separator + profile + File.separator + "mods");
        Map<String, String> mods = Utils.getAllMods(modsFolder.getAbsolutePath());
        Map<String, String> modNames = Utils.getAllModNames(modsFolder.getAbsolutePath());
        boolean doUpdate = args.length > 1 && args[1].equalsIgnoreCase("all");
        if (args.length == 1 || doUpdate) {
            System.out.println(Ansi.ansi().fg(Ansi.Color.CYAN).a("Mods with update available:"));
            Map<String, String> updatables = new HashMap<>();
            for (Map.Entry<String, String> entry : mods.entrySet()) {
                try {
                    JSONObject json = Utils.runRetry(() -> (JSONObject) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + entry.getKey()));
                    if (((long) json.get("classId")) != 6)
                        throw new NoSuchObjectException("The ID " + entry.getKey() + " does not represent a mod.");
                    JSONArray files = Utils.runRetry(() -> (JSONArray) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + entry.getKey() + "/files"));
                    List f = (List) files.stream().filter(o -> Utils.checkVersion((JSONArray) ((JSONObject) o).get("gameVersions"), config)).collect(Collectors.toList());
                    f.sort((a, b) -> (int) ((long) ((JSONObject) b).get("id") - (long) ((JSONObject) a).get("id")));
                    if (f.size() < 1) continue;
                    JSONObject fjson = (JSONObject) f.get(0);
                    if (((long) fjson.get("id")) > Long.parseLong(entry.getValue())) {
                        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(modNames.get(entry.getKey())).reset().a(" | ").fg(Ansi.Color.MAGENTA).a(entry.getKey()));
                        if (doUpdate) {
                            String downloadUrl = (String) fjson.get("downloadUrl");
                            if (downloadUrl == null) {
                                long parsed = (long) fjson.get("id");
                                long first = parsed / 1000;
                                downloadUrl = String.format("https://edge.forgecdn.net/files/%d/%d/%s", first, parsed - first * 1000, fjson.get("fileName"));
                                System.out.println(downloadUrl);
                            }
                            String loc = Utils.downloadFile(downloadUrl, profileConfig.getParent() + File.separator + "mods", ((String) fjson.get("fileName")).replace(".jar", "_" + entry.getKey() + "_" + fjson.get("id") + ".jar"));
                            if (loc == null) continue;
                            File oldFile = new File(modsFolder.getAbsolutePath() + File.separator + modNames.get(entry.getKey()));
                            if (oldFile.exists() && oldFile.isFile()) oldFile.delete();
                            System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Downloaded " + loc));
                        } else updatables.put(entry.getKey(), modNames.get(entry.getKey()));
                    }
                } catch (Exception e) {
                    System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Having trouble with mod " + entry.getKey()));
                    if (!Config.silentExceptions) e.printStackTrace();
                }
            }
            if (!doUpdate) {
                try {
                    PrintWriter pw = new PrintWriter(profileConfig.getParent() + File.separator + "mod_updates.txt");
                    for (Map.Entry<String, String> entry : updatables.entrySet())
                        pw.println(entry.getValue() + " = " + entry.getKey());
                    pw.println();
                    pw.println("You can update these mods of a profile with the following command: ");
                    String cmd = "curseforge profile update " + profile + " " + updatables.keySet().stream().map(String::valueOf).collect(Collectors.joining(" "));
                    pw.println(cmd);
                    pw.close();
                    System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Exported mods with update available to mod_updates.txt"));
                } catch (Exception e) {
                    if (!Config.silentExceptions) e.printStackTrace();
                }
            }
            return;
        }

        for (String arg : Arrays.stream(args).skip(1).toArray(String[]::new)) {
            try {
                String[] ids = arg.split("_");
                String id = ids[0], fileId = null;
                if (!Utils.isInteger(id)) throw new NoSuchObjectException("Mod ID is invalid: " + id);
                if (ids.length > 1) {
                    fileId = ids[1];
                    if (!Utils.isInteger(fileId)) throw new NoSuchObjectException("Mod file ID is invalid: " + id);
                }
                if (!mods.containsKey(id)) {
                    System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Cannot find mod with ID " + id + " installed. Skipping updating this mod..."));
                    continue;
                }
                JSONObject json = Utils.runRetry(() -> (JSONObject) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + id));
                if (((long) json.get("classId")) != 6)
                    throw new NoSuchObjectException("The ID " + id + " does not represent a mod.");
                JSONObject bestFile;
                if (fileId == null) {
                    JSONArray files = Utils.runRetry(() -> (JSONArray) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + id + "/files"));
                    List f = (List) files.stream().filter(o -> Utils.checkVersion((JSONArray) ((JSONObject) o).get("gameVersions"), config)).collect(Collectors.toList());
                    f.sort((a, b) -> (int) ((long) ((JSONObject) b).get("id") - (long) ((JSONObject) a).get("id")));
                    if (f.size() < 1)
                        throw new InputMismatchException("No available file of " + id + " found for this profile.");
                    bestFile = (JSONObject) f.get(0);
                } else {
                    String finalFileId = fileId;
                    bestFile = Utils.runRetry(() -> (JSONObject) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + id + "/files/" + finalFileId));
                }
                if (((long) bestFile.get("id")) > Long.parseLong(mods.get(id)) || fileId != null) {
                    System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(modNames.get(id)).reset().a(" | ").fg(Ansi.Color.MAGENTA).a(id));
                    String downloadUrl = (String) bestFile.get("downloadUrl");
                    if (downloadUrl == null) {
                        long parsed = (long) bestFile.get("id");
                        long first = parsed / 1000;
                        downloadUrl = String.format("https://edge.forgecdn.net/files/%d/%d/%s", first, parsed - first * 1000, bestFile.get("fileName"));
                        System.out.println(downloadUrl);
                    }
                    String loc = Utils.downloadFile(downloadUrl, profileConfig.getParent() + File.separator + "mods", ((String) bestFile.get("fileName")).replace(".jar", "_" + id + "_" + bestFile.get("id") + ".jar"));
                    if (loc == null) continue;
                    File oldFile = new File(modsFolder.getAbsolutePath() + File.separator + modNames.get(id));
                    if (oldFile.exists() && oldFile.isFile()) oldFile.delete();
                    System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Downloaded " + loc));
                }
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
            }
        }
    }

    private static void importProf(String[] paths) {
        importProf(paths, false);
    }

    private static boolean importProf(String[] paths, boolean toCD) {
        if (toCD && paths.length > 1) return false;
        boolean imported = false;
        for (String path : paths) {
            if (!Config.tempDir.exists() || !Config.tempDir.isDirectory()) Config.tempDir.mkdir();
            else {
                System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Another instance is importing a modpack. Please wait until it is finished."));
                return imported;
            }
            File zipped = new File(path);
            try {
                if (!zipped.exists()) throw new FileNotFoundException("The file " + path + " does not exist");
                if (zipped.isFile()) {
                    FileUtils.copyFileToDirectory(zipped, Config.tempDir);
                    if (!Utils.unzip(Config.tempDir.getAbsolutePath() + File.separator + zipped.getName()))
                        throw new FileSystemException("Failed to extract modpack content of " + path);
                } else FileUtils.copyDirectoryToDirectory(zipped, Config.tempDir);
                File manifest = new File(Config.tempDir.getAbsolutePath() + File.separator + "manifest.json");
                if (!manifest.exists() || !manifest.isFile())
                    throw new FileNotFoundException("Modpack is missing manifest. Cannot convert to profile.");
                Modpack.copyFromOverride(Config.tempDir.getAbsolutePath(), (String) ((JSONObject) parser.parse(new FileReader(manifest))).get("overrides"));
                Modpack.downloadMods(Config.tempDir.getAbsolutePath());
                JSONObject json = (JSONObject) parser.parse(new FileReader(manifest));
                String slug = ((String) json.get("name")).toLowerCase().replaceAll("[^a-z0-9]", "-");
                if (toCD) {
                    File current = new File(Constants.ABSOLUTE_PATH);
                    for (File file : Config.tempDir.listFiles()) {
                        if (file.getName().equals(zipped.getName())) continue;
                        if (file.isFile()) FileUtils.moveFileToDirectory(file, current, true);
                        else FileUtils.moveDirectoryToDirectory(file, current, true);
                    }
                    FileUtils.deleteDirectory(Config.tempDir);
                } else {
                    FileUtils.moveDirectoryToDirectory(Config.tempDir, Config.profileDir, true);
                    if (!Config.profileDir.exists() || !Config.profileDir.isDirectory()) Config.profileDir.mkdir();
                    File packFolder = new File(Config.profileDir.getAbsolutePath() + File.separator + "tmp");
                    File newFolder = new File(Config.profileDir.getAbsolutePath() + File.separator + slug);
                    if (!packFolder.renameTo(newFolder)) {
                        System.out.println(Ansi.ansi().fg(Ansi.Color.RED).a("Failed to rename modpack, but we are continuing anyway."));
                        newFolder = packFolder;
                    }
                    JSONObject profile = new JSONObject();
                    JSONObject minecraft = (JSONObject) json.get("minecraft");
                    String[] launcher = ((String) ((JSONObject) ((JSONArray) minecraft.get("modLoaders")).get(0)).get("id")).split("-", 2);
                    profile.put("name", json.get("name"));
                    profile.put("mcVer", minecraft.get("version"));
                    profile.put("launcher", launcher[0]);
                    profile.put("modVer", launcher[1]);
                    PrintWriter pw = new PrintWriter(newFolder.getAbsolutePath() + File.separator + "profile.json");
                    pw.write(profile.toJSONString());

                    pw.flush();
                    pw.close();
                    manifest.delete();
                }
                System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Converted modpack " + slug + " into profile."));
                imported = true;
            } catch (Exception e) {
                if (!Config.silentExceptions) e.printStackTrace();
            }
            try {
                if (Config.tempDir.exists() && Config.tempDir.isDirectory()) FileUtils.deleteDirectory(Config.tempDir);
            } catch (Exception ignored) {
            }
        }
        return imported;
    }

    public static boolean findAndImport() {
        try {
            File currentDir = new File(Constants.ABSOLUTE_PATH);
            return importProf(currentDir.list((dir, name) -> name.endsWith(".zip")), true);
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void printHelp(String prefix) {
        System.out.println(prefix + "profile: Commands for custom profiles.");
        System.out.println(prefix + "\tcreate: Create a profile.");
        System.out.println(prefix + "\tdelete: Delete a profile.");
        System.out.println(prefix + "\t\targ <name>: Name of the profile.");
        System.out.println(prefix + "\texport: Export a profile into zip.");
        System.out.println(prefix + "\t\targ <name>: Name of the profile.");
        System.out.println(prefix + "\t\targ [folders|files]: Folders or files to put in overrides.");
        System.out.println(prefix + "\tadd: Add a mod to the profile.");
        System.out.println(prefix + "\t\targ <profile>: Name of the profile to target.");
        System.out.println(prefix + "\t\targ <ID[_FileID]>: The ID of the mod. Can be multiple IDs or slugs.");
        System.out.println(prefix + "\t\t\t If an argument is in the format of ID_FileID, where FileID is the ID of a specific mod file, that specific mod file will be installed.");
        System.out.println(prefix + "\tremove: Remove a mod from the profile.");
        System.out.println(prefix + "\t\targ <profile>: Name of the profile to target.");
        System.out.println(prefix + "\t\targ <ID>: The ID of the mod. Can be multiple IDs or slugs.");
        System.out.println(prefix + "\tupdate: Update mods in the profile.");
        System.out.println(prefix + "\t\targ <profile>: Name of the profile to target.");
        System.out.println(prefix + "\t\targ [all | ID[_FileID]]: Type all to update all mods. ID are the ID of mods. Omit to check for updates. Can be multiple IDs or slugs.");
        System.out.println(prefix + "\t\t\t If an argument is in the format of ID_FileID, where FileID is the ID of a specific mod file, that specific mod file will be installed.");
        System.out.println(prefix + "\timport: Import a downloaded modpack.");
        System.out.println(prefix + "\t\targ <path>: Path to the zip or directory.");
    }
}
