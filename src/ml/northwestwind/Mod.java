package ml.northwestwind;

import org.apache.commons.io.Charsets;
import org.fusesource.jansi.Ansi;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Mod {
    public static void run(String[] args) {
        String cmd = args[1];
        if (args.length < 3) {
            Utils.invalid();
            return;
        }
        args = Arrays.stream(args).skip(2).toArray(String[]::new);
        if (cmd.equalsIgnoreCase("search")) search(args);
        else Utils.invalid();
    }

    private static void search(String[] keywords) {
        try {
            JSONArray json = (JSONArray) Utils.readJsonFromUrl(Constants.CURSEFORGE_API + "search?gameId=432&sectionId=6&searchFilter=" + URLEncoder.encode(String.join(" ", keywords), Charsets.UTF_8.name()));
            if (json == null || json.size() < 1) throw new Exception("No mods found.");
            Map<Long, String> mods = new HashMap<>();
            json.forEach(o -> {
                JSONObject jo = (JSONObject) o;
                long id = (long) jo.get("id");
                String name = (String) jo.get("name");
                System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(name).reset().a(" | ").fg(Ansi.Color.CYAN).a(id).reset().a(" : ").fg(Ansi.Color.MAGENTA).a(jo.get("websiteUrl")).reset());
                mods.put(id, name);
            });
            PrintWriter pw = new PrintWriter("search.txt");
            for (Map.Entry<Long, String> entry : mods.entrySet()) pw.println(entry.getValue() + " = " + entry.getKey());
            pw.println();
            pw.println("You can add these mods to a profile with the following command: ");
            String cmd = "curseforge profile add <profile> " + mods.keySet().stream().map(String::valueOf).collect(Collectors.joining(" "));
            pw.println(cmd);

            pw.close();
            System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Exported search results to search.txt"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void printHelp(String prefix) {
        System.out.println(prefix + "mod: Commands for mods.");
        System.out.println(prefix + "\tsearch: Search mods by keywords. Will also export results to text file.");
        System.out.println(prefix + "\t\targ <keywords>: Keywords to search.");
    }
}
