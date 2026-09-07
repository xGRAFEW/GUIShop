# CLAUDE.md

Guidance for Claude Code (or any future session) working in this repository.

## What this is

GUIShop — a Bukkit/Spigot/Paper/**Folia** shop plugin (Maven project, Java 17).
Repo: https://github.com/xGRAFEW/GUIShop (fork of https://github.com/pablo67340/GUIShop)
Main branch used for PRs: `bukkit`.

## Build

No local Maven is installed in this environment by default. If `mvn` is not on PATH, download a
portable copy instead of trying to install anything system-wide:

```bash
curl -sL -o maven.zip https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip
unzip -q maven.zip
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
export PATH="$(pwd)/apache-maven-3.9.9/bin:$PATH"
```

(`dlcdn.apache.org` 404s for old versions — use `archive.apache.org` instead.)

Then from the project root:

```bash
mvn -B clean package
```

Output: `target/GUIShop-<version>-shaded.jar` is renamed over `target/GUIShop-<version>.jar` by the
shade plugin (that's the one to ship). `target/original-GUIShop-<version>.jar` is the unshaded jar.

A first-time build downloads from ~10 repos (Spigot, Paper, jitpack, codemc, dmulloy2, etc.) and can
get killed by the sandbox's memory limits if other heavy processes (Minecraft clients, IDEs) are
running. Check free RAM first (`systeminfo` on Windows) if the build gets silently killed.

## Testing — always use the real Folia test server

There is a dedicated local test server at:

```
C:\Users\ACER\Desktop\Project\Survival SMP Folia 26.2 test
```

It runs **Canvas** (a Folia fork) on Minecraft/Bukkit API version **26.2** — Mojang's newer
date-based versioning (`26.2`, not `1.21.x`). Launch script: `_start.bat` (just `java ... -jar
canvas.jar nogui`, JDK 25 at `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`). It has no
attached console/stdin when started from a background shell, so there is no clean "type `stop` in
console" path available to an agent — use `taskkill`/`Stop-Process -Force` and accept that as the
stop method (world autosave still runs periodically; there are normally no players connected on
this instance since it's test-only — verify `server_console.log` for "joined the game" before
force-killing).

Workflow for testing a build:
1. Build the jar (see above).
2. Stop the running server process if one is up (`tasklist | grep java`, then force-stop it — the
   plugin jar is locked by the JVM while running and can't be replaced/deleted otherwise).
3. Copy `target/GUIShop-<version>.jar` into `<test server>/plugins/`, removing any older
   `GUIShop-*.jar` first (stale versions left in `plugins/` will both get loaded and Bukkit will
   throw a duplicate-plugin error).
4. Start the server (replicate `_start.bat`'s java args; redirect stdout/stderr to
   `server_console.log` so logs can be grepped).
5. Wait for `Done (` in `server_console.log`, then grep for `GUIShop|Exception|ERROR` to confirm a
   clean enable with no stack traces, and check `plugins/GUIShop/` got (re)populated with its config
   files.
6. There is no RCON enabled and no interactive console access from a background shell, so
   click-driven GUI testing needs a real Minecraft client connected — state clearly when that
   wasn't done rather than claiming full functional coverage.

The server also carries ~25 other plugins (LuckPerms, WorldGuard, MMOItems, zEssentials, Vault,
packetevents, etc.) — useful for catching real integration issues (e.g. GUIShop's internal economy
vs. zEssentials' economy, both registering with Vault).

## Folia support — current state

The plugin already has a full FoliaLib-based scheduler abstraction — this is not a from-scratch
port, just keep it consistent when adding new code:

- `src/com/pablo67340/guishop/util/SchedulerUtil.java` wraps `com.tcoded:FoliaLib` (shaded/relocated
  to `com.pablo67340.guishop.shade.folialib`). **Never call `Bukkit.getScheduler()` /
  `getServer().getScheduler()` / `new BukkitRunnable()` directly anywhere in this codebase** — always
  go through `SchedulerUtil`.
- Use `SchedulerUtil.runAtEntity*` for anything tied to a specific player/entity (inventory clicks,
  delayed reopen, etc.) — this is what Folia's regionized threading requires. `runTask`/`runTaskAsync`
  are for global/non-entity work (e.g. running a console command, periodic log flush).
- `resources/plugin.yml` declares `folia-supported: true`.
- `pom.xml` `supported.platforms` = `Paper/Spigot/Folia/Bukkit`.

Dependency versions are pinned to match this Mojang/Spigot versioning generation:
- `org.spigotmc:spigot-api` → `26.2-R0.1-SNAPSHOT` (was `1.21.10-R0.1-SNAPSHOT`; check
  `https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/maven-metadata.xml`
  for the latest `26.x` build before bumping further).
- `com.github.retrooper:packetevents-spigot` → `2.13.0` (matches the version already deployed on the
  test server's `plugins/`).
- Added `com.google.code.findbugs:jsr305:3.0.2` (`provided` scope) — the newer spigot-api snapshot no
  longer transitively pulls in `javax.annotation.Nullable`, which `messages/Message.java`,
  `messages/MessageSystem.java`, and `messages/PlaceholderMessage.java` import directly. If a future
  spigot-api bump breaks compilation again with `package javax.annotation does not exist`, this is
  the dependency to check first.

If you bump `spigot-api`/`packetevents` again in the future, rebuild and redeploy to the test server
per the workflow above before assuming it still works — new Bukkit/Paper API snapshots have broken
this project's compile before (see git history: "crash 26.x fix").

## Project layout

```
src/com/pablo67340/guishop/
  GUIShop.java          — main plugin class (onEnable/onDisable, reload())
  api/                   — public API surface for other plugins (e.g. container-selling hooks)
  commands/              — command executors/tab completers
  config/                — config.yml-backed settings
  definition/            — data model (Item, MenuItem, MenuPage, CommandsMode, ...)
  economy/               — internal economy (Vault provider) + dynamic pricing
  gui/                   — inventory GUI listener
  handler/
  listenable/            — Menu/Shop/Sell listeners — the actual shop click logic
  listenable/editor/     — in-game chat-driven item editor
  messages/              — message templating system
  statistics/            — player transaction stats + PlaceholderAPI expansion
  util/                  — SchedulerUtil, ConfigManager, LogUtil, MiscUtils, RowChart
  worth/                 — PacketEvents-based item-worth lore display
resources/               — plugin.yml, config.yml, menu.yml, shops/, etc. (filtered into target/classes)
wiki/                    — GitHub wiki source, mirrored into GitHub Wiki separately
```

## Release process

See `HANDOFF.md` for the current release checklist and where things stand. In short: bump
`<version>` in `pom.xml` (it's the only hardcoded version string in the repo — plugin.yml pulls
`${project.version}`), build, test on the Folia server above, commit, push, then create a GitHub
Release on `xGRAFEW/GUIShop` with the shaded jar attached.
