# HANDOFF

Running log of session progress on GUIShop. Newest entry on top. See `CLAUDE.md` for durable
project/build/test knowledge — this file is for "what happened and what's next."

---

## 2026-09-08 — 9.6.0: remove the internal economy, fix the Vault load-order bug

**Symptom reported:** buying 1 item worked, buying 32 or 64 always said "not enough money" even
though `/money` showed 1M. `/bal` showed no money at all.

**Root cause (confirmed from the real server's `plugins/GUIShop/Logs/`):** GUIShop's *internal*
economy was registering itself with Vault at `ServicePriority.High`, so it overrode CMI. The player
was actually spending GUIShop's own `starting-balance: 1000.0`, which 5 purchases at $200 drained to
exactly $0 (`transaction.log` shows the 5 buys; `debug.log` shows `getBalance: ... = 0.0`). Setting
`economy.yml` → `enabled: false` did not help, because `onEnable()` did `return;` when Vault had no
economy yet — and GUIShop enables *before* CMI, so the lookup always failed and the whole plugin
went dead.

**What was done:**
1. Removed the internal economy entirely: `EconomyManager`, `EconomyConfig`, `GUIShopEconomy`,
   `EconomyCommands`, `resources/economy.yml`, the `/bal /balance /money /pay /send /togglepay`
   dynamic command registration, `/gs eco`, the `guishop.economy.*` permissions, and
   `wiki/Internal-Economy.md`. GUIShop now *only* spends the Vault economy's balance.
   `%guishop_balance*%` placeholders now read Vault instead.
2. Replaced the fatal `onEnable()` bail-out with `resolveEconomy(boolean)` — a lazy hook that
   retries once a second for 40s and logs `Economy hooked: <name> (via Vault)`. Listeners and
   commands are now always registered regardless of load order. `reload()` re-hooks too.
3. `MiscUtils.setupEconomy()` no longer requires a Vault *Permission* provider (it used to return
   false and kill the economy when only the permission lookup was missing). Permission checks go
   through new null-safe `MiscUtils.playerHas/has` helpers that fall back to Bukkit's own check.
4. Added economy plugins to `softdepend` so Bukkit prefers to enable them first.

**Also fixed on the test server (not a code change):** `plugins/` had both `Vault-1.7.4.jar` and
`VaultUnlocked-2.20.2.jar`, which Bukkit logged as `Ambiguous plugin name 'Vault'` and resolved to
VaultUnlocked. Moved VaultUnlocked to `_removed_plugins/`. With only Vault 1.7.3-CMI present the
enable order becomes Vault → CMI → `[Vault][Economy] CMI Economy hooked.` → GUIShop.

**Not verified by an agent:** the actual in-game click-to-buy-64 flow — that needs a real client.
The balance source and the quantity math (`buyPrice × quantity`) were both verified by reading code
and logs.

---

## 2026-09-07 — Folia 26.2 compatibility bump + release 9.4.5

**Goal:** make sure GUIShop runs cleanly on Folia 26.2 (tested via the Canvas-based local test
server), then commit/push/release to `xGRAFEW/GUIShop`.

**Starting state:** the plugin already had solid Folia groundwork from prior work (see commit
`f4b1a6b "crash 26.x fix"` and `d4593c0 "9.4.4"`) — `SchedulerUtil` wrapping FoliaLib,
`folia-supported: true` in plugin.yml, entity-affinity scheduling in the GUI click handlers. This
session was about catching up the *dependency versions* to the actual 26.2 API generation and
verifying against the real test server, not a from-scratch Folia port.

**What was done:**
1. No Maven was installed locally — downloaded a portable Apache Maven 3.9.9 to the scratchpad temp
   dir and used that (`archive.apache.org`, not `dlcdn.apache.org` which 404s for pinned versions).
2. Baseline build (before any changes) succeeded at 9.4.4 — confirmed nothing was already broken.
3. Bumped `pom.xml`:
   - `org.spigotmc:spigot-api` `1.21.10-R0.1-SNAPSHOT` → `26.2-R0.1-SNAPSHOT`
   - `com.github.retrooper:packetevents-spigot` `2.7.0` → `2.13.0`
   - `mc.version.max` property `1.21.11` → `26.2`
   - added `com.google.code.findbugs:jsr305:3.0.2` (provided) — the newer spigot-api no longer pulls
     in `javax.annotation.Nullable` transitively, which broke compilation in 3 files under
     `messages/`.
   - version `9.4.4` → `9.4.5`
4. Rebuilt — succeeded (`target/GUIShop-9.4.5.jar`, shaded).
5. Deployed to the real test server (`C:\Users\ACER\Desktop\Project\Survival SMP Folia 26.2 test`,
   Canvas 26.2-931-HEAD, ~25 other plugins installed):
   - Had to free RAM first — the machine had multiple Minecraft game clients open eating ~4-5GB
     each, leaving only ~258MB free, which got the first `mvn` build OOM-killed. User approved
     closing the Minecraft clients (not the test server) to free memory.
   - The test server process has no attached console/stdin (it was already running headless when
     found), so there's no clean "type `stop`" path — used `Stop-Process -Force` / `taskkill /F` to
     restart it. No players were connected either time (checked `server_console.log` for join
     messages first).
   - Verified via `server_console.log`: GUIShop 9.4.5 loads and enables with **no exceptions**,
     `plugins/GUIShop/` config folder populated correctly, PlaceholderAPI expansion registered,
     internal economy registered with Vault, `ProfitMultiplier` successfully hooked GUIShop's sell
     events. Watched the log for ~20s post-boot with no delayed thread-affinity errors
     (`IllegalStateException` / "expected region thread" patterns some Folia bugs throw).
6. **Not tested:** actual in-game GUI interaction (opening the shop menu, buying/selling via
   clicks) — no Minecraft client was connected during this session, only server-side log
   verification. If something is Folia-broken specifically in a *player-triggered* code path that
   doesn't fire during idle server startup, this session would not have caught it.

**Result:** confirmed working — GUIShop 9.4.5 builds against and boots cleanly on Folia 26.2.

**Next steps left for a future session (not yet done as of this entry):**
- [ ] Commit the `pom.xml`/`CLAUDE.md`/`HANDOFF.md` changes
- [ ] Push to `origin/bukkit`
- [ ] Create a GitHub Release on `xGRAFEW/GUIShop` (tag `9.4.5`) with `GUIShop-9.4.5.jar` attached
- [ ] Ideally: connect a real client to the Folia test server and click through Buy/Sell/Menu at
  least once to close the "in-game GUI never actually tested" gap noted above
- [ ] Consider whether GUIShop's internal economy silently overriding zEssentials' economy (both
  register with Vault; GUIShop wins because it registers at `ServicePriority.High`) is intended for
  this particular test server's plugin set — it's existing/documented behavior
  (`economy.yml` → `enabled: false` to opt out), not something this session changed, just flagging
  it was observed in the boot log again.
