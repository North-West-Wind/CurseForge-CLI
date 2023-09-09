## This project is dead. Consider using an alternative such as [Ferium](https://github.com/gorilla-devs/ferium) instead.

# CurseForge CLI
Written in Java. Designed for all platform and portable use.  
Note that this is not a launcher. It is just a modpack manager. Game launching still requires Minecraft launcher.
#### Some mod searching tips
Use [this website](https://superstormer.github.io/cf-search/) instead of CurseForge. It is way easier to search with it.  
Remember to enable "Show Project ID" on the left of the site, so you can easily copy the ID.
## Usage
1. Download the "curseforge.zip" at the [release page](https://github.com/North-West-Wind/CurseForge-CLI/releases/latest).
2. Extract it to your desire location.
3. Open a terminal in the extracted folder.
4. If you're using Linux, make the "curseforge" file executable. There are 2 approaches:
    - GUI: Open your file manager, right-click the file and get into "Properties", search and check "Executable" (or similar options).
    - CLI: Run `chmod +x <path>`, where "path" is the path to the "curseforge" bash script.
5. Run `./curseforge` in the terminal you opened to start it.
6. (Optional) Put the path where you extracted the program to the PATH variable. That way you can run `curseforge` directly from terminal without `cd`-ing to there first.

If you want to use the "curseforge-cli.jar" file directly, follow step 2 and 3 above, and then do the following:
- Run `java -jar curseforge-cli.jar --args` and put your arguments after `--args`.

### Commands
- `help` Display commands of the program.
- `config` Configure the program.
  - `directory <path>` Set the working directory of the program.
- `modpack` Commands for modpack.
  - `install <ID[_FileID]>` Install modpack(s). The ID parameter is the ID of the modpack found on CurseForge. To install a modpack with a specific version, use the format ID_FileID, where FileID is the ID of the modpack zip file on CurseForge. Omit to install the latest version.
  - `delete <ID[_FileID] | Slug>` Delete modpack(s). The ID/slug parameter is the ID/slug of the modpack. Specify the FileID to remove a spcific version.
  - `list` List all installed modpacks.
- `profile` Commands for custom profile.
  - `create` Create a custom profile. You will need to answer some questions.
  - `edit <profile>` Edit a custom profile. Similar to `create`.
  - `delete <name>` Delete a profile. The name parameter is the name of the profile.
  - `add <profile> <ID[_FileID]>` Add a mod to the profile. ID is the ID of the mod on CurseForge.
  - `remove <profile> <ID>` Remove a mod from the profile. Works similarly to `add`.
  - `update <profile> [all | ID[_FileID]]` Update mods of a profile. Append `all` to update all mods. Omit ID to check for updates. Include FileID to update to a specific version.
  - `export` Export the profile as an uploadable modpack format.
  - `import <path>` Import a downloaded modpack.
- `mod` Commands for mods.
  - `search <keywords>` Search for mods with keywords. Will also export results to file for easier adding to profile.

### Configuration
You can configure CurseForge CLI by editing `cf.json`. The file should be generated once the JAR file has run once.  
These are the options you can edit:
- `directory` The directory where modpacks and profiles are stored. Default: `./curseforge-cli`
- `acceptParent` Whether mods with only the parent version of a profile is accepted or not. For example, you can install a 1.18 mod into a 1.18.1 profile. Default: `true`
- `suppressUpdates` Whether the update messages should be suppressed or not. Default: `false`
- `silentExceptions` Whether the exception stack traces should be silenced or not. Default: `false`
- `retries` The amount of retries when downloading something fails. Default: `3`

### Other Features
If you put the JAR file together with a zip of the exported profile, CurseForge CLI will automatically install that profile to the current directory.  
This has 1 limitation, which is you should not put multiple zip files in the same directory.

### Note
When you run the CLI, you should see a new file called `cf.json`. It is essentially the save file of the program. If you don't know what you are doing, do not touch it.

You will find modpack/mod name with some numbers after it. That is totally intentional. Do not attempt to remove it. It is used for recognition.

Installing a modpack will also generate a profile. However, the profile doesn't use the correct mod loader. Please install the mod loader yourself as instructed.

## Compiling
To compile the program yourself, some libraries are needed:
- [Apache Commons IO](https://commons.apache.org/proper/commons-io/download_io.cgi)
- [jansi](https://fusesource.github.io/jansi/download.html)
- [JSON Simple](https://code.google.com/archive/p/json-simple/downloads)

Download them and put them somewhere.

This program is compiled with IntelliJ, here are the steps to follow if you're using it: 
1. Download the repository by `git clone` or downloading as ZIP and extract it. 
2. Launch IntelliJ IDEA if you haven't.
3. Open the directory where the files are extracted into.
4. In the top bar, go to File->Project Structure.
5. On the left side, go to Global Libraries.
6. Click the + symbol and choose Java.
7. In the file chooser, choose the Java libraries you downloaded.
8. Click OK on everything.
9. In the top bar, go to Build->Build Artifacts.
10. In the little window, choose Build.
11. The `.jar` file should be built inside `out/artifacts`.

Steps to follow if you're using VSCode: 
1. Open VSCode, make sure you have the `Extension Pack for Java` installed.
2. Download the repository by going on the left panel->Source Control->Clone Repository and paste the git clone link.
3. Create a new folder in the root directory and call it lib.
4. Download and extract all the necessary libraries, then put the jar files in the lib directory.
5. Press ctrl+shift+p and search for `Tasks:Configure Default Build Task`, select buildArtifact.
6. Copy and substitute this: `"targetPath": "${workspaceFolder}/out/artifacts/curseforge-cli.jar"`.
7. Press ctrl+shift+b OR ctrl+shift+p and search for `Tasks:Run Build Task` to build.
8. The `.jar` file should be built inside `out/artifacts`.

## Support
If you want to support me, please consider becoming [my Patron](https://www.patreon.com/nww).

## License
GNU GPLv3
