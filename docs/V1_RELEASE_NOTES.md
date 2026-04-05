# Vital v1 Release Notes

Version line: `v1`  
Build version: `1.0.0+26.1.1`

## Release summary

Vital is a client-side Fabric mod for Minecraft Java Edition `26.1.1` that shows clean in-world target health and floating combat feedback without relying on chat spam or a server-side install.

## Included features in v1

- Heart-based target health display above living entities.
- Optional exact health numbers.
- Floating damage popups.
- Floating healing popups.
- Trigger mode: show after a recent hit.
- Trigger mode: show when looking directly at a target.
- Editable keybinds in Minecraft Controls.
- Dedicated keybind category label: `Vital`.
- JSON config file.
- Optional Mod Menu config screen.
- Client-side only behavior.

## Keybinds in v1

All keybinds are editable in Minecraft at:

`Options -> Controls -> Key Binds -> Vital`

Default actions:

- Toggle Vital
- Toggle target hearts
- Toggle damage popups
- Toggle healing popups
- Toggle hit-trigger mode
- Toggle look-target mode
- Toggle heart row
- Toggle HP numbers
- Open Vital settings

## Packaging note

For public uploads, keep the public release line as:

- Project name: `Vital`
- Public version label: `v1`
- File version: `1.0.0+26.1.1`
- Built jar: `vital-1.0.0+26.1.1.jar`

Only bump beyond `v1` when you intentionally release a feature update.
