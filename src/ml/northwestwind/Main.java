package ml.northwestwind;

import org.fusesource.jansi.AnsiConsole;

import java.util.Arrays;

import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.YELLOW;
import static org.fusesource.jansi.Ansi.ansi;

public class Main {
    public static void main(String[] args) {
        System.out.println("CurseForge CLI v" + Constants.VERSION);
        AnsiConsole.systemInstall();
        Utils.readUpdate();
        Config.load();
        int index = Arrays.asList(args).indexOf("--args");
        if (index < 0) {
            if (Profile.findAndImport()) return;
            printHelp();
            return;
        }
        args = Arrays.stream(args).skip(index + 1).toArray(String[]::new);
        if (args.length < 1 && Profile.findAndImport()) return;
        Config.createDirs();
        if (args.length < 1 || args[0].equalsIgnoreCase("help")) printHelp();
        else if (args[0].equalsIgnoreCase("mod")) Mod.run(args);
        else if (args[0].equalsIgnoreCase("modpack")) Modpack.run(args);
        else if (args[0].equalsIgnoreCase("profile")) Profile.run(args);
        else if (args[0].equalsIgnoreCase("config")) Config.run(args);
        System.out.println(ansi().reset());
        AnsiConsole.systemUninstall();
    }

    private static void printHelp() {
        System.out.println(ansi().fg(YELLOW).a(
                " ________  ________      ________  ___       ___     \n" +
                "|\\   ____\\|\\  _____\\    |\\   ____\\|\\  \\     |\\  \\    \n" +
                "\\ \\  \\___|\\ \\  \\__/     \\ \\  \\___|\\ \\  \\    \\ \\  \\   \n" +
                " \\ \\  \\    \\ \\   __\\     \\ \\  \\    \\ \\  \\    \\ \\  \\  \n" +
                "  \\ \\  \\____\\ \\  \\_|      \\ \\  \\____\\ \\  \\____\\ \\  \\ \n" +
                "   \\ \\_______\\ \\__\\        \\ \\_______\\ \\_______\\ \\__\\\n" +
                "    \\|_______|\\|__|         \\|_______|\\|_______|\\|__|"
        ).reset());
        System.out.println(ansi().fg(GREEN).a("Made by NorthWestWind").reset());
        System.out.println();
        System.out.println("\"curseforge\", or \"./curseforge\" if you are running terminal in this directory.");
        System.out.println("\thelp: Display this message.");
        Config.printHelp("\t");
        Modpack.printHelp("\t");
        Profile.printHelp("\t");
        Mod.printHelp("\t");
        System.out.println();
        System.out.println("You can also put this in the same directory with a CurseForge export to install it.");
    }
}
