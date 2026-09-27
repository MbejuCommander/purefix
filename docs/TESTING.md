# Verification

## Verified build: 1.1.0 (2026-09-27)

- `test build`: passed, **29 tests**, zero failures/errors.
- Real development client: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Mod Menu 21.0.0.
- The same client menu/mixin smoke test also passed with Xaero World Map **1.46.4** and its bundled XaeroLib **1.7.17** installed. Third-party test jars are not distributed with PureFix.
- Runtime loaded the four mixin targets and reported `PUREFIX_SMOKE_PASS`. A foreign channel codec simulation confirmed observation preserves the original decoded payload and consumes each observation once.
- A five-byte world-ID fixture also passed through the actual registered gameplay decoder in both configurations: XaeroLib's original `PacketPayload` with Xaero installed, and PureFix's `WorldPayload` without it. Both yielded the expected ID.
- Screenshots of the root menu, EnderFix, teleport editor, SpectateFix, and Server Saves were visually inspected. The vanilla pearl icon, input field, feature toggles, disabled offline Add Server action, and pagination rendered correctly.
- Built using JDK 26 with Java **25** bytecode target. The release jar was inspected: client-only metadata, version 1.1.0, new networking/UI classes, and no development smoke-test classes.
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

`./gradlew runSmokeClient` runs a separate development-only mod against the real Minecraft client. It opens the Mod Menu config factory, exercises mixin loading and payload observation, navigates the themed root/EnderFix/editor/SpectateFix/Server Saves/help screens, executes Reset and checks the resulting defaults, captures screenshots, and exits with `PUREFIX_SMOKE_PASS` in the log on success. The task checks that marker and fails if it is absent or a failure marker is present. It does not authenticate or join a public server.

## Multiplayer acceptance checklist

A real server with the user's spectate plugin, InventoryRollbackPlus, CoreProtect, proxy topology, and staff permissions is required for these checks. Automated unit and UI checks do not replace them.

1. Start with a separate test account/config folder. Join backend A; confirm the captured address/name/ID. Visit backend B; record whether its ID differs. For shared IDs, create both profiles and manually select them.
2. Check the conventional world mappings against the server's Bukkit world names. Configure every custom world used for staff work.
3. Run `/inventoryrollback restore <test-player>`, open the initial 9-slot menu, then a backup. Left-click the action pearl. Verify exactly one CoreProtect teleport and no InventoryRollbackPlus teleport click. Repeat with right-click.
4. Verify the screenshot example produces `co teleport #world_nether -145.6 128 308.7`. Click normal inventory pearls and modified/invalid action pearls; verify unrelated items pass through and invalid recognized action pearls are blocked.
5. Disable EnderFix and confirm the original InventoryRollbackPlus behavior. Add an alias and change the teleport template; remove the alias and confirm it no longer arms detection. Test sound OFF.
6. In the Nether at a safe known location, run `/s <test-player>`, disconnect, and restart the client. Join the matching backend and verify a single automatic return. Check that the JSON record disappears only after arrival.
7. Repeat with `/spectate <test-player>`. Before disconnecting, exit using the server's actual exit command; confirm no reconnect return is sent. Verify whether repeated targeted commands toggle or retarget on this server.
8. Disconnect from A and join B or a hub with a different profile. Confirm A's pending return remains and no teleport is sent there. With shared identifiers, confirm manual profile selection is necessary.
9. Switch Minecraft accounts and verify the other account cannot consume the original account's return. Disable SpectateFix, reconnect, then re-enable and retry.
10. Remove teleport permission, simulate authentication delay, or set a wrong world mapping. Confirm the return remains saved and there is no automatic command loop; restore access and Retry.
11. Verify CoreProtect's safe-location adjustment. If arrival differs by more than two blocks, confirm the retained return can be forgotten after inspection.
12. Edit/delete server profiles, check deletion suppression, inspect exported CSV, and exercise list pagination at different GUI scales.
13. With MapModCompanion configured, check the chat diagnostic, visit worlds with different IDs, and reconnect while a return is pending. Confirm a saved Xaero return never runs on a different ID, even with identical seeds. Check with and without Xaero World Map installed.
14. Verify packet-before-respawn and delayed-announcement behavior on the actual proxy. On an address previously seen sending IDs, a missing new ID must leave recovery pending. With an unknown unsupported server, verify seed fallback after the discovery period.
15. Confirm the in-game SpectateFix icon reflects the logged-in skin and the offline menu uses Steve. Test all three default restore aliases, customized patterns, Reset cancellation, and confirmed Reset.

## Limits of available verification

The repository's automated tests and development smoke test can run locally. No credentials, authenticated staff server, or the user's exact proxy/plugin configuration were supplied, so real-server plugin behavior and invisible backend collisions remain unverified until the acceptance checklist is performed.
