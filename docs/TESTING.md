# Verification

## Verified build: 1.4.1 (2026-10-09)

- Java 25 build and 59 automated tests cover existing features plus unique full-bundle combinations, equivalent stack identities, inventory coverage, small inventories, 8-second duration and speed limits, delay validation, wheel landing geometry, transfer acknowledgement and pacing, cancellation, disconnects, timeout without retry, partial-move rejection, and persisted Donkey settings.
- Real Minecraft client smoke exercises the Donkey menu, vanilla donkey-with-chest renderer, rotating bundle wheel, enable toggle, sound toggle, Dedsafio soundtrack registration, normal 8-second preview, and Classic preview at 2x. UI screenshots were visually inspected.
- Command tests cover bare-alias usage, player-name suggestions and prefix matching, manually entered offline names, one execution per target, changed/nested aliases, and disabling the command. Placeholder tests cover formatting, exact lore recognition, ordinary glass, unrelated lore, and persistent ON/OFF settings. The client smoke also toggles the new filter.
- Live transfers with the user's invsee plugin remain unverified. The queue is tested with simulated server acknowledgements; the preview does not transfer inventory.

### Donkey server acceptance

Type `/donkey ` and verify player suggestions. Change the alias, then toggle Donkey OFF and ON without reconnecting; verify the suggestions follow the current setting. Use a test target with known item counts. Confirm marked InvSee++ panes are absent with the filter ON, ordinary glass is retained, and the marked panes return with the filter OFF. An inventory containing only placeholders should report no eligible items. Run `/donkey <player>`, exclude any plugin controls, and select a color containing 15 occupied slots. Verify every color contains a distinct full combination, the chosen bundle matches the visible winner, and the normal spin lasts eight seconds. Repeat at 2x and confirm both the soundtrack and animation take four seconds. Verify full source-slot contents arrive, no source slot is selected twice, the remaining slots are unchanged, and subsequent moves wait 300 ms after confirmation. Repeat with fewer than 15 slots, two slots of identical items, equipment, a full staff inventory, a changing target inventory, read-only invsee permissions, cancellation, disconnect, and sounds OFF. Verify no interrupted queue resumes after reconnect.

## Historical verification: 1.2.0 (2026-09-27)

- `test build`: passed, **35 tests**, zero failures/errors.
- Real development client: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Mod Menu 21.0.0.
- Version 1.1.0 also passed the client menu/mixin smoke test with Xaero World Map **1.46.4** and its bundled XaeroLib **1.7.17** installed. This compatibility run was not repeated for 1.2.0; packet decoding/registration is unchanged. Third-party test jars are not distributed with PureFix.
- Runtime loaded the four mixin targets and reported `PUREFIX_SMOKE_PASS`. A foreign channel codec simulation confirmed observation preserves the original decoded payload and consumes each observation once.
- A five-byte world-ID fixture passed through PureFix's registered gameplay decoder. The 1.1.0 compatibility run also exercised XaeroLib's original `PacketPayload` with Xaero installed; both yielded the expected ID.
- Screenshots of the root menu, EnderFix, teleport editor, SpectateFix, and Server Saves were visually inspected. The vanilla pearl icon, input field, feature toggles, disabled offline Add Server action, and pagination rendered correctly.
- The 1.2.0 client smoke run loaded the new game-event hook, toggled MapModCompanion OFF and ON, and navigated the updated SpectateFix menu. Its screenshot was visually inspected.
- Built using JDK 26 with Java **25** bytecode target. Release packaging excludes the development smoke-test mod.
- Actual staff-server plugin behavior is **not verified**; see the acceptance checklist below.

## Automated checks

Run `./gradlew test build` (Windows: `gradlew.bat`). The JUnit suite covers:

- Exact screenshot lore, formatting codes, namespaced worlds, negative/decimal coordinates.
- Missing and duplicate labels, invalid numeric values, command injection characters, and coordinate bounds.
- Whole-command matching, namespaced command aliases, player placeholders, and required template placeholders.
- CoreProtect's coordinate length constraint, locale-independent decimals, address normalization, and CSV escaping.
- Save/reload across simulated client restarts, full-precision coordinates, previous-save backups, corrupt/unsupported JSON preservation, and write failures.
- Account/server isolation, hidden identifiers, manual selection, server-position readiness, reconnect eligibility, disabled recovery, one attempt per connection, and arrival confirmation.
- Xaero packet lengths, marker validation, signed IDs, discovery timeout, same-seed transitions, pre-respawn announcements, duplicates, disconnect cleanup, and persisted-network expectations.
- Exact Xaero ID recovery gating and migration of untouched default command lists while preserving customized lists.
- Spectator entry/exit sequencing, no clearing before spectator is observed, disconnect reset, old delay migration, toggle persistence, and remaining recovery guards with map detection OFF.

`./gradlew runSmokeClient` runs a separate development-only mod against the real Minecraft client. It opens the Mod Menu config factory, exercises mixin loading and payload observation, navigates the themed root/EnderFix/editor/SpectateFix/Server Saves/help screens, executes Reset and checks the resulting defaults, captures screenshots, and exits with `PUREFIX_SMOKE_PASS` in the log on success. The task checks that marker and fails if it is absent or a failure marker is present. It does not authenticate or join a public server.

## Multiplayer acceptance checklist

A real server with the user's spectate plugin, InventoryRollbackPlus, CoreProtect, proxy topology, and staff permissions is required for these checks. Automated unit and UI checks do not replace them.

1. Start with a separate test account/config folder. Join backend A; confirm the captured address/name/ID. Visit backend B; record whether its ID differs. For shared IDs, create both profiles and manually select them.
2. Check the conventional world mappings against the server's Bukkit world names. Configure every custom world used for staff work.
3. Run `/inventoryrollback restore <test-player>`, open the initial 9-slot menu, then a backup. Left-click the action pearl. Verify exactly one CoreProtect teleport and no InventoryRollbackPlus teleport click. Repeat with right-click.
4. Verify the screenshot example produces `co teleport #world_nether -145.6 128 308.7`. Click normal inventory pearls and modified/invalid action pearls; verify unrelated items pass through and invalid recognized action pearls are blocked.
5. Disable EnderFix and confirm the original InventoryRollbackPlus behavior. Add an alias and change the teleport template; remove the alias and confirm it no longer arms detection. Test sound OFF.
6. In the Nether at a safe known location, run `/s <test-player>`, disconnect, and restart the client. Join the matching backend and verify a single automatic return. Check that the JSON record disappears only after arrival.
7. Repeat with `/spectate <test-player>`, including F1. Retarget and verify the original coordinates remain. Have the observed player disconnect; when the server restores survival, confirm the return clears. Bare exit commands alone must not clear it; spectator-to-other-mode confirmation must. Plugins that never change game mode need manual Forget.
8. Disconnect from A and join B or a hub with a different profile. Confirm A's pending return remains and no teleport is sent there. With shared identifiers, confirm manual profile selection is necessary.
9. Switch Minecraft accounts and verify the other account cannot consume the original account's return. Disable SpectateFix, reconnect, then re-enable and retry.
10. Remove teleport permission, simulate authentication delay, or set a wrong world mapping. Confirm the return remains saved and there is no automatic command loop; restore access and Retry.
11. Verify CoreProtect's safe-location adjustment. If arrival differs by more than two blocks, confirm the retained return can be forgotten after inspection.
12. Edit/delete server profiles, check deletion suppression, inspect exported CSV, and exercise list pagination at different GUI scales.
13. With MapModCompanion configured, check the chat diagnostic, visit worlds with different IDs, and reconnect while a return is pending. Confirm a saved Xaero return never runs on a different ID, even with identical seeds. Check with and without Xaero World Map installed.
14. Verify packet-before-respawn and delayed-announcement behavior on the actual proxy. On an address previously seen sending IDs, a missing new ID must leave recovery pending. With an unknown unsupported server, verify seed fallback after the discovery period.
15. Confirm the in-game SpectateFix icon reflects the logged-in skin and the offline menu uses Steve. Test all three default restore aliases, customized patterns, Reset cancellation, and confirmed Reset.
16. While waiting for a world ID, verify an initial entry is blocked with a settings hint and villager sound. Once a session is tracked or staff is already spectating, verify repeated/bare commands pass through. Toggle detection OFF and check immediate seed fallback, manual selection for ambiguous profiles, and retained account/profile guards. Toggle ON to restore ID checks.
17. Verify disconnect and a forced client termination preserve the already-written return. Rejoining in survival must not itself count as a normal spectate exit. Verify the 0-tick delay still waits for identity and a server position, and a custom delay remains editable.

## Limits of available verification

The repository's automated tests and development smoke test can run locally. No credentials, authenticated staff server, or the user's exact proxy/plugin configuration were supplied, so real-server plugin behavior and invisible backend collisions remain unverified until the acceptance checklist is performed.
