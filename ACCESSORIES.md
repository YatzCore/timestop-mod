# Accessory integration

Equip any pocket watch in the Pocket Watch slot. Press **V** to toggle its selected time mode. Its tier passive, socketed rune, rune target filter, Creative Watch rune matrix, and death rewind work while worn. Press **J** (Open Watch Interface in Controls → Time Stop) to configure the equipped watch, including modes, runes, filters, Creative radius, matrix, and rewind exemption. J is unused in the six installed testing profiles and can be rebound. The GUI key prefers equipped watches, then falls back to hands and inventory. Shift + right-click still opens a held watch. Runes stay inside watches; loose runes are not accessories.

Held watches take priority (main hand, then offhand), followed by the highest-tier equipped watch, then the highest-tier inventory watch. For single socketed rune effects, offhand and main hand retain their existing priority, followed by equipped watches and inventory watches. Cosmetic Curios slots grant no effects. Removing the accessory immediately removes its passive effects. Cooldowns and durations use the existing watch rules.

| Minecraft | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| 1.20.1 | Curios 5.9.1+ | Same Forge JAR, Curios 5.9.1+ | Trinkets 3.7.2+ |
| 1.21.1 | No official Curios release available | Curios 9.2.2+ | Trinkets 3.10.0+ |

Install the matching API on both client and server. These are optional integrations, and the APIs are not bundled in Time Stop. Without them, watches continue to work through the standard hands and inventory. The Forge 1.21.1 build uses the common equipment interface, which returns no accessories because there is no supported API release for that loader/version.

Development runs include the matching API by default. Use `-PaccessoryRuntime=false` for runs without the optional API. Curios data defines one `watch` slot; Fabric defines one `charm/watch` Trinkets slot. Item tags accept all five watch tiers. The 1.21.1 resource processing converts vanilla item tag paths to `tags/item` while keeping accessory slot/entity data directories intact.

Sources: [Curios documentation](https://docs.illusivesoulworks.com/curios/getting-started), [Curios releases](https://modrinth.com/mod/curios/versions), [Trinkets documentation](https://github.com/emilyploszaj/trinkets/wiki/Quick-Start-Guide).

## Verification

Run the isolated API tests with Java 21 as `JAVA_HOME`:

```powershell
.\gradlew.bat -I integration/accessories/tests.init.gradle :fabric:runAccessoryTests :forge:runAccessoryTests --console=plain
.\gradlew.bat -I integration/accessories/tests.init.gradle -PaccessoryRuntime=false :fabric:runAccessoryTests :forge:runAccessoryTests --console=plain
```

The API-installed runs check all five watch tags, live equipped stacks, hand/equipment/inventory precedence, passive defense, socketed rune effects and target filters, Vector Control, Creative rune matrices, removal, activation/cooldown, and death-rewind consumption including rejection of a restored spent rune. Native Curios tests additionally check that cosmetic inventory cannot activate a watch. The API-absent runs check safe class loading and inventory fallback; accessory-specific assertions are skipped.

Tests use isolated directories under each loader's `build/accessory-test-run`. Test sources are excluded from release JARs. Gameplay, cosmetic exclusion, and absence checks passed for Fabric and forge on this Minecraft version. All production loader code compiled. On 1.21.1, Forge additionally passed dedicated server startup without Curios, and existing Common unit tests passed.

Rewind leaves equipped watches in their native accessory slots, retaining their live settings and socket. Inventory and cursor snapshots skip any historical copy of that same watch, preventing duplication when rewinding to before it was equipped. Other inventory items still rewind normally; spent Rewind runes stay consumed.

The isolated actual-client GUI check is `./gradlew.bat -I integration/accessories/tests.init.gradle :fabric:runAccessoryClientQa`. It simulates the registered J key with empty hands, clicks matrix/radius/exemption controls, verifies native slot synchronization and rewind, and saves screenshots in `fabric/build/accessory-client-qa/screenshots`.

Use the official Modrinth Trinkets 3.7.2 release (version ID AHxQGtuC) and separate Cardinal Components API 5.2.3. The dev.emi Maven build has an incorrectly mapped client shadow; the official release and its Cardinal Components dependency are the development runtime defaults. Both are optional and excluded from our release jars.

Client and server must use the updated watch-interface jars together. Watch-setting packets now carry a native slot target; Forge/NeoForge protocol versions were increased to reject older packet layouts.
