# Building Vital Popups from the terminal

## Required
- JDK 25
- Internet access for Gradle to download Minecraft/Fabric dependencies the first time

## Fast path on Windows
Open PowerShell or CMD in this folder and run:

```bat
gradle build
```

If your installed Gradle is too old, generate the wrapper once:

```bat
gradle wrapper --gradle-version 9.3.0
```

Then use:

```bat
gradlew.bat build
```

## Output jar
After a successful build, your release jar will be in:

```text
build\libs\
```

Use the jar **without** the `-sources` suffix.

Example:

```text
build\libs\vitalpopups-1.1.0+26.1.1.jar
```

## Install the jar into Minecraft
1. Install Fabric Loader for Minecraft 26.1.1.
2. Put the built jar into your `.minecraft\\mods` folder.
3. Also install Fabric API.
4. Mod Menu is optional.

## If `gradle build` fails immediately
Your earlier error was caused by using `modImplementation` in a Minecraft 26.1+ non-obfuscated Loom project. This project now uses `implementation` and `compileOnly` instead.


Note for 26.1+: client-only code lives under `src/client/java`, which matches Fabric Loom's split environment source set setup.
