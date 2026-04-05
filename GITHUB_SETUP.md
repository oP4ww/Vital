# GitHub Setup for Vital v1

## Upload the project
1. On your GitHub repo page, click **Add file** -> **Upload files**.
2. Upload **the contents of this folder**, not the zip itself.
3. Commit directly to `main`.

## Fix the README
If your repo already has a wrong file like `READMEmd`, delete it and keep only `README.md`.

## Wiki setup
1. Go to **Settings** -> **General**.
2. Make sure **Wikis** is enabled.
3. Open the **Wiki** tab.
4. Create the `Home` page and paste `wiki/Home.md`.
5. Create the other pages using the matching files from the `wiki/` folder.
6. Create the sidebar by editing `_Sidebar` and paste `wiki/_Sidebar.md`.

## GitHub Releases automation
This bundle already includes `.github/workflows/release.yml`.
To publish automatically:
1. Push the repo.
2. Create and push a tag like `v1.0.0`.
3. GitHub Actions will build the mod and attach the jar to a GitHub Release.

## Recommended repo layout
- `README.md`
- `LICENSE`
- `build.gradle`
- `gradle.properties`
- `settings.gradle`
- `src/`
- `docs/`
- `wiki/`
- `.github/workflows/release.yml`
