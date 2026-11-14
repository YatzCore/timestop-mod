# Time variants — Minecraft 1.21.1 / 1.8.4

Open the watch interface with J or Shift + right-click a held watch. The Slow Motion and Fast Forward cards show a second line, **World · Combat** and **World · Self**; the underlined word is the variant that card will use.

- Click **World**, **Combat** or **Self** to pick that variant directly.
- Scroll over the card, right-click it, or press its number again while it is selected to switch variants; the watch stays open so you can see the change.
- Clicking the card itself selects the variant you used last on that card (remembered on the watch). Shift + its number picks the other variant.
- Without opening the watch, press **B** (Controls → Ultimate Time Stop → Switch Time Variant) to flip the selected Slow Motion or Fast Forward between its variants. B changes the same watch that V activates (held first, then worn, then inventory). The action bar confirms the choice.

Selection is saved on the watch and applies on its next activation. Both variants inherit the base mode's watch-tier access, duration, cooldown and local/global scope. Card tooltips show the current Speed Calibration values.

## Combat Slow Motion

Select the Combat variant of Slow Motion, then activate with V. Mob movement and decision/attack timers, plus projectile flight (including your own shots), run at the Slow Motion calibration rate, default 0.25x. Player movement and the server/network clock remain at 20 TPS. Blocks, furnaces, crops, daylight, weather, dropped items and primed TNT keep normal timing. The continuous movement system keeps steering responsive between slower AI decisions; it does not add Prediction overlays. Living status/fuse counters retain the continuous living-tick clock used by Prediction.

## Self Acceleration

Select the Self variant of Fast Forward, then activate with V. The activating player's personal clock runs at the Fast Forward calibration (default 5x). Helpful and harmful processes both speed up:

- movement (input scaled through native travel and collision) and falling (jump height is unchanged; fall speed is capped below the server's movement check, about 2x normal terminal speed),
- mining speed and the delay between broken blocks,
- the delay between placing blocks / using items,
- eating, drinking and other item use (bows, crossbows, shields, spyglasses),
- attack recharge and item cooldowns such as ender pearls and chorus fruit,
- status-effect timers, so Regeneration heals and Poison hurts faster and effects run out sooner,
- hunger, saturation, natural healing and starvation,
- breath: you run out of air and drown faster underwater, and recover air faster above it,
- burning (fire damage comes faster and the fire burns out sooner), freezing in powder snow, and the short hit-immunity window after taking damage.

The server clock is not changed and no attributes or abilities are modified. Other players, mobs, projectiles and world processes remain at normal speed. Fall damage depends on height, so it is unchanged. Everything returns to normal when the field ends. Passengers and spectators are not boosted.

## Fast Forward and your personal clock

Inside your own (watch) Fast Forward field your game already runs at the Fast Forward rate on your side, and the server now runs your personal clock at the same rate, so eating, attack recharge, cooldowns, effects, healing, hunger, breath, burning, freezing and mining agree between client and server. In global Fast Forward the whole server runs faster, so everything about you is already accelerated. Stationary (pedestal) Fast Forward fields do not change players.

## Casual acceleration

Open H / watch Settings and enable **Casual acceleration** (Visuals & FX tab, bottom). While Self Acceleration or your own Fast Forward field speeds you up, harmful processes keep their normal speed: drowning, burning, freezing, harmful effects (Poison, Wither, Hunger…), starvation and the hit-immunity window. Helpful ones (healing, Regeneration and other beneficial effects, eating, attack recharge, cooldowns, mining, movement, recovering air) still speed up. Healing still costs hunger as usual. This is your own setting (saved in your client config and sent to the server when you join); it does not affect other players. In global Fast Forward the whole server clock runs faster, so Casual mode cannot slow harmful effects there.

## Swimming during Time Stop

Open H / watch Settings, then enable **Swim in stopped time** at the bottom of Visuals & FX. This disables the existing frozen-liquid walking surface so a free player can enter and swim through stopped water. Fluid simulation remains stopped. Turn it off to restore walking on stopped liquids. Native breathing and fluid hazards remain in effect.

This is a server-wide collision setting, persisted as mechanics.enableWaterWalkingInStasis (swimming ON means false). Only the singleplayer host or a level-2 operator can change it. It is synchronized to connected clients and on joining. Existing installations retain their previous choice; the default remains walking.

## Calibration and commands

H → Speed Calibration labels the shared sliders Fast / Self Speed and Slow / Combat. Existing speed commands still apply:

- /timestop speed slowmotion 0.25
- /timestop speed fastforward 5
- /timestop start combatslow [seconds]
- /timestop start selfacceleration [seconds]

Existing operator command permissions apply. Forge protocol is 6; NeoForge payload version is 1.8.3-casual-acceleration. Use matching client/server jars for your loader (1.8.3 required on both sides: this version adds the Casual acceleration setting). Fabric, Forge and NeoForge 1.21.1 share these features. Minecraft 1.20.1 has the same features in its own 1.8.3 build.
