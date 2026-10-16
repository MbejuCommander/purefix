# Changelog

## 1.4.1 - 2026-10-09

- Set the default alias to `/donkey {player}` and the default transfer interval to 300 ms, including blank interval input. Migrate previous default values once while preserving custom values.

- Register the configurable Donkey alias with the client command tree, including player-name completion and usage feedback for a missing target.
- Refresh completions when the alias changes or Donkey is toggled. Internal server commands bypass the client alias, including when alias and inventory command share a name.
- Add **InvSee++ placeholder filter**, default ON. Detect the plugin's exact lore marker and exclude placeholders before forming roulette bundles. Keep ordinary glass panes eligible and allow the filter to be disabled.
- Stop with a clear message when filtering leaves no eligible items.
- Retain the level-up notice after all winning slots are confirmed transferred, controlled by Roulette sounds.
- Add command parsing/completion, alias lifecycle, placeholder recognition, migration, and filter-toggle checks.


## 1.4.0 - 2026-10-09

- Replace individual-slot spins with one winning color containing a complete random bundle of 15 occupied slots by default.
- Prevent duplicate full combinations regardless of ordering or equivalent source stacks; retain overlap between different bundles. Cover eligible contents and adapt the number of sectors when fewer combinations exist or smaller bundles need more sectors.
- Draw bundle items in each colored sector and show their full-size contents in the side panel on hover.
- Set normal spin duration to 8 seconds. Add 1x-2x speed control that accelerates the animation and soundtrack together.
- Include the supplied Dedsafio roulette audio as the default sound style, with the previous Minecraft sounds available as Classic. Stop owned audio on cancellation.
- Keep sequential full-slot transfers and their independently configured interval. Add combination, coverage, speed, persistence, and client preview checks.


## 1.3.0 - 2026-10-09

- Add Donkey with a vanilla chest-carrying donkey icon, configurable enable switch, and offline roulette preview.
- Capture an invsee chest inventory through the editable `/shame {player}` client alias and `/invsee {player}` server command.
- Draw 15 distinct occupied slots by default, transferring each winning slot’s full contents. Add editable winner count, title recognition, and per-slot exclusions.
- Animate item icons on a colorful wheel with slowing clicks, XP reveals, and a completion sound. Roulette sounds default ON and can be disabled.
- Transfer winners sequentially with server confirmation and a configurable 2000 ms interval; blank input restores the default. Stop on changed inventories, insufficient space, cancellation, disconnect, or missing acknowledgement.
- Add selection, timing, cancellation, timeout, and configuration tests plus real-client Donkey UI smoke coverage.


## 1.2.0 - 2026-09-27

- Replace command-based exit confirmation with server game-mode events. A tracked session clears after spectator mode changes to a different mode, including when the observed player disconnects and the server restores survival.
- Keep the original return when spectate commands are repeated or targets change. Bare exit commands pass through. Already tracked sessions and spectator-mode commands are not blocked by missing world IDs.
- Detect mode changes independently of F1 and spectator-menu visibility, without inventory scans or polling timers. Disconnects/crashes retain the previously saved origin.
- Set the default join delay to 0 ticks. Migrate the old 10-tick default once; retain other configured delays.
- Add MapModCompanion detection ON/OFF, default ON. OFF uses the seed fallback immediately, including known profile associations, and disables the Xaero-specific recovery check.
- Add setting instructions and the villager negative-response sound to the waiting-for-world-ID message.
- Remove obsolete exit-command settings, update the guide, and add lifecycle, migration, and fallback regression coverage.

## 1.1.0 - 2026-09-27

### Added

- Xaero World Map world-ID reception on `xaeroworldmap:main`, including use without the Xaero client and passive observation when Xaero is installed.
- Primary Xaero world identity for SpectateFix, persisted identity on new returns, transition invalidation, and exact-ID recovery checks.
- SpectateFix **Check MapModCompanion** button with an English chat report distinguishing protocol compatibility from proof of a particular plugin.
- `/irp restore {player}` in the three default restore patterns, plus a confirmed **Reset** action. Untouched old defaults migrate; customized lists remain intact.
- Minecraft-styled framed menus, dashboard cards, vanilla item icons, ON/OFF badges, and the current player's face for SpectateFix with a Steve fallback.
- Protocol, transition, migration, and recovery regression tests; expanded client smoke coverage.

### Behavior and limitations

- Unknown networks get a 60-tick discovery period before seed fallback. Networks previously seen sending Xaero IDs wait for a fresh ID instead of silently falling back.
- World IDs do not expose SMP names or Bukkit world names. Different dimensions can have different IDs; recovery waits for the saved world ID rather than inferring backend membership.
- Existing seed profiles are preserved and are not automatically merged with Xaero profiles. Bind profiles only after verifying the current world.
- A compatible packet cannot establish the exact server plugin's identity. Real staff-server and proxy behavior still needs the acceptance checks in `docs/TESTING.md`.

## 1.0.0 - 2026-09-27

### Added

- Client-only Fabric support for Minecraft 26.3, Java 25+, and Fabric Loader 0.19.5+.
- English Mod Menu configuration and an in-game `/purefix` command.
- Server Saves with automatic capture enabled by default, editable names/addresses/subservers, manual selection, world mappings, deletion suppression, and CSV export.
- EnderFix command arming, initial 9-slot menu validation, backup-menu/pearl recognition, safe lore parsing, click replacement, editable teleport templates, and optional vanilla teleport sound.
- SpectateFix durable pre-command saves, UUID/profile isolation, reconnect recovery, server-position readiness, arrival confirmation, and manual Retry/Forget controls.
- Configurable entry/exit command lists, join delay, feature toggles, and sound toggles.
- Versioned JSON storage with forced writes, atomic replacement where available, previous-save backup, and corruption protection.
- CoreProtect-compatible decimal formatting while preserving full coordinates in storage.
- Unit tests, a separate development-client smoke test, installation guide, design notes, and multiplayer acceptance checklist.

### Known limitations

- Proxy backend names and Bukkit world names are not universally exposed to clients. Seed-based subserver detection is heuristic; hidden/shared identifiers require manual profiles/selection.
- Command detection cannot confirm a server spectate plugin's internal toggle state or permission result.
- EnderFix recognizes the English InventoryRollbackPlus layout shown in the supplied screenshots. Custom lore layouts require adaptation.
- Full end-to-end verification with the user's authenticated proxy, permissions, and plugins remains a server-side acceptance step.
