# Troubleshooting

## The mod does not show up

Check that you installed:

- the correct Minecraft version
- Fabric Loader
- Fabric API
- the main Vital jar, not the `-sources` jar

## The keybinds are missing

Go to:

**Options -> Controls -> Key Binds**

Search for **Vital**.

If they still do not appear, confirm that the correct jar is loaded and no older duplicate build is still in the `mods` folder.

## The config screen will not open

Try the Vital keybind first.

If you rely on Mod Menu, verify that Mod Menu matches your Minecraft/Fabric version.

## The mod does not build from source

Use:

```powershell
gradle clean build
```

Confirm Java 25 is active:

```powershell
java -version
```

## I built two jars

Use the normal jar.
Do not use the `-sources.jar` file.
