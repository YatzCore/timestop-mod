# Time Prediction â€” Minecraft 1.20.1

Time Prediction is available on **Netherite and Creative watches**, on Forge and Fabric.
Select it in the watch interface (Shift + Right-Click, or **J** for an equipped watch),
then activate with Right-Click or **V**.

The field slows affected movement to **25% speed** while you retain normal controls.
Server and client clocks stay at **20 TPS**; navigation and movement/look/jump controls
continue every tick between slowed AI decisions. Movement is integrated continuously rather
than freezing entities for three ticks between jumps. Global mode advances world time
and weather separately at the selected rate. Watch duration, cooldown, local radius,
global scope, and existing exemptions continue to apply. Only the activating player
sees the forecast.

Right-click a nearby in-flight arrow or throwable to catch it into your inventory while Prediction is active. Left-click redirects it. Catching respects reach and solid cover; recovered tipped arrows retain their potion data.

Mob ground routes now use body-sized terrain collisions and native step selection. Planned jumps to higher navigation nodes, captured jump boosts, and falling are integrated separately from route steering, and arrow contacts use those same future body positions. Navigation and AI can still change after a forecast.

## Reading the forecast

Version **1.7.5** keeps the white holographic style: distance-based moving light pulses,
soft path edges, faint scan bands and model outlines. Landing reticles follow the block
surface normal. Near-camera geometry fades rather than appearing as oversized markers.

- **Splash areas** use the potion’s color and a four-block boundary. Eligible future bodies
  are highlighted using native distance falloff, direct-hit intensity, duration and vanilla
  immunity rules. Native splash search also has a vertical bound; walls do not stop splash.
  Water previews include sensitive/burning mobs and axolotl rehydration. Empty/awkward
  potions do not invent effects. Modded effect-application overrides remain uncertain:
  forecasting never dispatches gameplay application events.
- **Lingering areas** show the initial three-block cloud footprint. They do not claim
  who will enter it later, how it will shrink, or when a later effect will apply.
- **Blast areas** show potential entity-damage reach, not guaranteed damage or block
  destruction. Large fireballs use their actual power; small fireballs have no invented
  explosion. Charged/configured creeper power is respected. Terrain exposure is sampled
  against future body boxes using currently loaded collision geometry.
- **Imminent hazards** show primed TNT and swelling creepers within three real seconds.
  Conditional creeper areas are dashed and retract when swelling stops. TNT uses native
  skipped-tick credit, gravity, collision/friction and standard fluid pushes; creeper fuses
  retain the current continuous living-tick clock. Forecasting does not change either clock.
  TNT paths stop at unloaded geometry, modded callback terrain, cobwebs, bubble columns
  or berry bushes instead of inventing an exact detonation after unsupported physics.
- **Clutter limits** retain twelve paths and eight projectile silhouettes. All future mob,
  teleport, contact and effect bodies share a twelve-body ceiling. At most two effect areas
  are drawn, prioritizing danger to the player and held aim; affected-body highlights are
  limited to two. No extra prediction text or fullscreen effect is added. Cues fade on
  activation, snapshot expiry and outcome cancellation, and clear when prediction ends.


- **Animated white trails** follow a mob's currently planned navigation route, including
  turns over the next six seconds. Moving light pulses show the route direction.
- **Translucent white silhouettes** use the actual entity model to display future poses
  along that route. Two fixed time offsets keep the near and farther future readable.
- **Complete white arcs** show the remaining loaded flight of arrows, tridents, snowballs,
  eggs, pearls, potions, experience bottles, fireballs, and supported TaCZ bullets.
  A white landing ring marks the first predicted impact. A warm gold arc and brighter
  future body mark an intercept with a mob at its forecast position; incoming threats
  also have a warm tint. Turning targets and between-tick crossings use swept collision.
- **Projectile silhouettes** add translucent arrow shapes (or tridents and throwable item models)
  at two future positions, including on your held weapon preview before release. A shared
  limit of eight projectile silhouettes keeps the world view clean.
- **Enemy pre-release previews** show a dashed arc while a skeleton draws its bow or
  a pillager charges/readies its crossbow. They use the native muzzle, shot speed and current
  central aim without firing, consuming ammunition or advancing random spread. Up to three
  prepared enemy shots share the existing projectile path budget. The preview updates if
  aim changes and disappears when the charge is cancelled. It estimates a shot released now;
  random spread, movement before release, and custom modded archer behavior can change it.
- **Arrow redirection** works with Left-Click in Prediction as well as the existing
  temporal modes. It returns the arrow to its shooter, or follows your crosshair with
  Vector Control / redirect-to-look enabled. Reflected ownership is synchronized so
  client flight uses the same exemption and speed as the server. Arrow clicks use the custom packet so they
  cannot trigger vanilla's invalid-arrow-attack disconnect. Distance and wall checks apply.
- **Enderman tells** show a lavender destination ring, dotted link, and translucent body
  when a watched Enderman selects a valid teleport. Prediction adds a 0.6-second windup
  before that actual jump; teleports remain immediate outside Prediction. Random
  destinations cannot be known before selection. An obstructed destination can cancel
  the reserved jump. Arrows/tridents account for Endermen normally dodging them.
- **Held weapon previews** show the full shot arc. An undrawn bow previews full charge;
  drawing it updates the preview to the current charge. Charged crossbows and thrown
  tridents retain their charge/Riptide restrictions.

The forecast prioritizes nearby threats and your focus. At most eight projectile paths,
three movement paths (up to two silhouettes each), and one aimed shot appear together.
Up to two intercept poses and four selected teleport tells add focused event markers.
No panel or arrival-time text is added. Paths and silhouettes are depth tested against
the world. The usual bubble shell is hidden; the existing timer remains the status HUD.

Navigation is the mob's actual **current plan**, not knowledge of every future AI decision.
It may change when the mob reacts. Routes forecast at most six seconds; players and
mobs without navigation use a three-second continued-motion estimate. Intercepts
beyond an unfinished movement forecast are omitted rather than falsely treating its
last projected point as a permanent stop. Navigation routes contain at most 24 copied waypoints.
Own projectiles stay tracked out to 256 blocks from you, including outside a local
bubble and beyond the client entity render distance. Server-generated flight geometry
keeps those arcs drawable without a client entity or loaded client terrain. Held aim
previews follow render-time player position/rotation as you move. Existing impact
anchors never translate with camera or entity interpolation.

Projectile arcs continue through server-loaded terrain, up to 256 blocks of remaining
flight and 60 real seconds;
they stop at collision or the loaded-world boundary. Unknown projectile physics uses a
shorter estimate. Forecasts do not generate chunks or simulate live entities.

TaCZ aimed previews use base gun-pack ballistics; spread, recoil, attachments and future
target decisions can change the shot. Forecasting never fires a weapon or consumes ammo.
Riptide tridents and crossbow fireworks do not receive an aimed preview in this release.

## Configuration and commands

`speed_multipliers.predictionRate` in `config/timestop.json` defaults to `0.25`.
It is independent of Slow Motion's speed. Use:

```text
/timestop start prediction [seconds]
/timestop speed prediction
/timestop speed prediction 0.25
```

Existing command permissions apply. A console-triggered field has no player forecast owner.
Pedestal modes and the Minecraft 1.21.1 version are unchanged. Forge servers and clients
must use the same new network protocol (**17** on Forge).

## Development verification

```powershell
.\gradlew.bat :fabric:runGametest
.\gradlew.bat -I integration/readiness/deceleration-native.init.gradle :forge:runDecelerationTests
.\gradlew.bat :fabric:runPredictionVisual
.\gradlew.bat -I integration/readiness/prediction-visual.init.gradle :forge:runPredictionVisual
```

The optional native suite and Forge visual run use the real TaCZ jar; override its path with
`-PdecelerationTaczJar=...`. Each visual run creates its own world under the loader's
`build/prediction-visual-run`, captures screenshots, and checks movement, aimed forecasts,
the path cap, owner controls, and cleanup. It never opens a normal gameplay save.
Test classes are excluded from release jars.
