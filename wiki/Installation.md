# Installation

## Requirements

- Minecraft Java Edition **26.1.1**
- Fabric Loader
- Fabric API
- Java **25**

## Install Steps

1. Install Fabric Loader for Minecraft 26.1.1.
2. Download the latest Vital jar.
3. Download Fabric API.
4. Put both jars into your Minecraft `mods` folder.
5. Launch the Fabric profile.

## Mods Folder

On Windows:

```text
%AppData%\.minecraft\mods
```

## Optional

- Mod Menu for easier config access
- Shader mods such as Iris should work normally because Vital is client-side

## Build From Source

```powershell
gradle clean build
```

Output:

```text
build\libs\vital-1.0.0+26.1.1.jar
```
