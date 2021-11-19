# CurseForge CLI
Written in Java. Designed for all platform and portable use.
## Usage
1. Download the "curseforge.zip" at the [release page](https://github.com/North-West-Wind/CurseForge-CLI/releases/latest).
2. Extract it to your desire location.
3. Open a terminal in the extracted folder.
4. If you're using Linux, make the "curseforge" file executable. There are 2 approaches:
    - GUI: Open your file manager, right-click the file and get into "Properties", search and check "Executable" (or similar options).
    - CLI: Run `chmod +x <path>`, where "path" is the path to the "curseforge" bash script.
5. Run `./curseforge` in the terminal you opened to start it.

### Commands
- `help` Display commands of the program.
- `config` Configure the program.
  - `directory <path>` Set the working directory of the program.
- `modpack` Commands for modpack.
  - `install <ID>` Install modpack(s). The ID parameter is the ID of the modpack found on CurseForge.
  - `delete <ID|Slug>` Delete modpack(s). The ID/slug parameter is the ID/slug of the modpack.
  - `list` List all installed modpacks.
- `profile` Commands for custom profile.
  - `create` Create a custom profile. You will need to answer some questions.
  - `edit <profile>` Edit a custom profile. Similar to `create`.
  - `delete <name>` Delete a profile. The name parameter is the name of the profile.
  - `add <profile> <ID>` Add a mod to the profile. ID is the ID of the mod on CurseForge.
  - `remove <profile> <ID>` Remove a mod from the profile. Works similarly to `add`.
  - `update <profile> [ID]` Update mods of a profile. Omit ID to check for updates.
  - `export` Export the profile as an uploadable modpack format.
  - `import <path>` Import a downloaded modpack.
- `mod` Commands for mods.
  - `search <keywords>` Search for mods with keywords. Will also export results to file for easier adding to profile.

### Note
When you run the CLI, you should see a new file called `cf.json`. It is essentially the save file of the program. If you don't know what you are doing, do not touch it.

You will find modpack/mod name with some numbers after it. That is totally intentional. Do not attempt to remove it. It is used for recognition.

## License
GNU GPLv3
