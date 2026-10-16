# Design decisions

## Client-only boundary

PureFix observes outgoing commands, login/respawn identifiers, server position updates, clientbound Xaero payloads, and client container contents. Four mixins hook command/world/position handling, container action clicks, payload decoding, and main-thread payload delivery. Fabric provides client initialization, networking registration, lifecycle events, ticks, and a local command. PureFix installs no server code, sends no plugin identification requests, and does not connect to external services.

## Why JSON rather than CSV

Settings, server profiles, per-profile dimension maps, deletion suppression, and UUID-scoped pending returns are related structured data. A single versioned JSON snapshot can commit them together. A CSV export makes server mappings portable without introducing separate files that could disagree after a crash. Critical writes happen synchronously before a spectate command, not asynchronously after it. The data set is small; this prioritizes durability over speculative micro-optimization.

The writer first validates the snapshot, forces a temporary UTF-8 file, copies the last valid main file to `.bak`, and atomically replaces the main file if supported. Malformed input is preserved rather than overwritten. A failed critical save cancels the outgoing spectate command. Concurrent clients sharing one config directory are unsupported.

## Xaero research and independent identification

[Xaero's Minimap's published documentation](https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap) explains that server-side installation can provide more reliable subworld identification, while its client-only fallback uses world-spawn information. Its settings/translations describe separate subworlds and explicitly connecting subworlds for teleportation; see the [author's translation repository](https://github.com/thexaero/xaero-minimap-translations/blob/main/en_us.lang). Its published file organization uses address/world separation and waypoint data rather than a relational CSV database.

PureFix uses its own JSON format and prioritizes the `xaeroworldmap:main` protocol documented by [MapModCompanion](https://github.com/turikhay/MapModCompanion/wiki/How-it-works). A supported world announcement is exactly five bytes: marker zero, then a signed big-endian 32-bit ID. Zero and negative IDs are valid. Other packet types are ignored. A compatible announcement does not prove which plugin sent it.

Without Xaero installed, a clientbound Fabric payload codec and receiver advertise the channel. With Xaero installed, PureFix leaves its codec and receiver alone. A codec wrapper inspects a duplicate buffer and associates the observation with the original decoded payload using a bounded identity map; it never replaces that payload. A main-thread listener consumes the observation before Fabric dispatches the packet. No network buffer is retained.

`WorldIdentity` invalidates confirmed IDs on login/respawn even when seeds match. Announcements settle for four ticks; only a recent, uncommitted announcement can cross a respawn boundary, accommodating MapModCompanion's pre-respawn packet ordering. Duplicate messages do not continually postpone readiness. After 60 ticks an unknown network may use the obfuscated-seed fallback. A network previously observed sending IDs instead waits for one. This expectation is stored across restarts.

Profiles use address plus identity; new returns also persist their identity. A return with a Xaero identity requires an exact match even after manual profile selection. IDs can differ between dimensions in one SMP: the protocol does not establish backend membership, so cross-ID recovery remains pending. Seed-to-Xaero profile migration is manual because identical seeds cannot prove equivalence. Provider collisions, stale server announcements, and transitions invisible to every available signal cannot be solved purely on the client. Bukkit world names still require mappings.

## EnderFix guards

The [InventoryRollbackPlus backup menu source](https://github.com/TechnicallyCoded/Inventory-Rollback-Plus/blob/main/src/main/java/me/danjono/inventoryrollback/gui/menu/MainInventoryBackupMenu.java) puts the teleport action at index 49. Recognition also requires a tracked restore command, the initial menu signature, the configured backup title, a 54-slot chest, the pearl item, and its configured item name. The player's own inventory slots cannot match. Valid interception cancels the original plugin click before dispatching the replacement command. Slot actions unrelated to that recognized pearl pass through unchanged.

Lore parsing is independent of Minecraft objects for testing. It strips legacy formatting codes, splits labels at the first colon, requires one world and one coordinate per axis, and rejects unsafe world tokens and non-finite or unreasonable coordinates. Templates require all four fields and disallow unknown placeholders or multiline commands.

## SpectateFix lifecycle

`Idle -> persist origin -> active -> disconnect -> eligible on join -> ready on matching backend -> command sent -> new server position at origin -> remove record`.

Repeated commands preserve the original saved session. The connection mixin observes `handleGameEvent` after vanilla applies `CHANGE_GAME_MODE`; `MultiPlayerGameMode.isSpectator()` reads the authoritative local mode used for spectator behavior. Once spectator mode was observed for an armed session, a subsequent non-spectator mode clears that session's record. Rendering and F1 are never consulted. This is constant work per relevant packet, with no inventory scan, recurring mode poll, or extra network request. The implementation was checked against the Minecraft 26.3 client bytecode. [Fabric's networking guide](https://docs.fabricmc.net/develop/networking) describes the packet-driven client/server model.

Login/disconnect do not synthesize an exit. Respawn may establish that a tracked session entered spectator mode, but a respawn alone never confirms exit. A rejected entry can retain a return until manually forgotten; camera-only or inventory-only spectate plugins are not covered by the game-mode detector. Failed automatic teleports retain the record. Attempt tracking is memory-only and resets on reconnect; a pending return saved during the current session is not eligible for automatic recovery until a later join. Manual Retry explicitly makes it eligible again. Disabling a feature never erases its pending data.

MapModCompanion detection defaults ON. When OFF, identity uses the current seed immediately, bypassing discovery and the exact-Xaero-ID requirement. A profile may match a seed observed while its Xaero ID was confirmed; multiple matches remain ambiguous and need manual selection. Other recovery gates remain active. Turning detection back ON restores primary Xaero detection. This does not alter packet registration or disable the server plugin.

Identity, account, dimension, readiness, and retry gates are covered by `RecoveryPolicyTest`. Server command replies are not parsed as success because they vary by plugin/version/language. Arrival instead requires a new server correction and proximity in the original dimension/profile. This proves observed arrival, not the server's internal plugin state.

The [CoreProtect implementation](https://github.com/PlayPro/CoreProtect/blob/master/src/main/java/net/coreprotect/command/TeleportCommand.java) accepts separate world/X/Y/Z arguments, rejects coordinate strings with 12 or more characters, throttles teleports, and may choose a safe nearby destination. Command formatting and the one-attempt policy account for these constraints.


## Donkey inventory lifecycle

The client alias arms a ten-second wait for the configured chest title. A stable snapshot of the top container slots is captured; the staff inventory is never in the draw. Review permits excluding plugin controls. SecureRandom creates bundles of distinct occupied source slots, then chooses one bundle uniformly from the displayed sectors. Sectors can overlap. A canonical multiset of full-stack identities prevents duplicate combinations, including reordered stacks and equivalent contents in different source slots. Coverage bundles are generated first; additional random bundles fill up to eight sectors, or more if small bundle sizes require them. Bounded random sampling is followed by enumeration over item-type counts, avoiding an unbounded retry loop when only a few combinations exist. With no alternatives, only one sector is shown. Item counts and components remain intact.

One spin lasts 8000 / speed milliseconds, where speed is between 1 and 2. The eight-second Ogg recording plays at the same speed through Minecraft's UI sound pitch; the multiplier stays within its supported range. The classic sound style uses sector crossings and a shortened minimum click interval. The preview and live wheel share the grouping rules, duration calculation, and audio helper. Audio stops when the wheel completes or is removed.

Transfers use one ordinary QUICK_MOVE packet at a time with no local predicted inventory changes. Server-observed source removal and an increased matching staff inventory count acknowledge the full stack. The next click waits the configured interval after acknowledgement. A ten-second timeout stops rather than retries; slot contents, menu identity, connection, available capacity, and cursor state are checked. This is not an atomic server transaction: plugins can reject or partially apply clicks, and already-sent clicks can complete after cancellation. The transient draw is not persisted or replayed. No losing inventory is deleted.


### Donkey command completion and InvSee++ placeholders

Donkey registers literal alias tokens plus a Brigadier player-name argument through Fabric's client command callback. Completion uses the client's online-name provider. Alias paths have dynamic requirements; settings changes register the new path and refresh Fabric's combined command tree. Old paths become unavailable, and internal backend commands do not execute client aliases recursively. The existing outgoing-command hook remains a fallback.

InvSee++ marks its placeholder panes and icons with the lore line `InvSee++ placeholder`: see the [upstream placeholder builder](https://github.com/Jannyboy11/InvSee-plus-plus/blob/master/InvSee%2B%2B_Common/src/main/java/com/janboerman/invsee/spigot/internal/placeholder/Placeholders.java). PureFix removes formatting and matches that full line before capturing candidate slots. It does not filter by material or slot position. Custom server placeholders that remove/change the marker still need manual exclusion. The supplied video shows the standard marker on the pane tooltips.
