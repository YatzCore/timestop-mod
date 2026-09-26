# Command Guide & Tutorials

This document provides a comprehensive reference for all commands in the **Ultimate Time Stop** mod, covering syntax, required permission levels, underlying engine behaviors, and real-world deployment scenarios.

---

## Command Categories

The mod registers two primary command trees:
1. **Administrative Engine Commands (`/timestop`)**: Require operator permissions (Permission Level 2). Used to manipulate world-level time dilation, configure server-wide watch policies, manage stasis exemptions, and inspect active engine states.
2. **Time Sync Commands (`/sync` or `/timesync`)**: Available to all players without operator permissions (Permission Level 0). Used in multiplayer to link players into a shared resonance network, granting mutual stasis immunity within localized temporal fields.

---

## 1. Administrative Commands (`/timestop`)

All commands under this category require Minecraft operator status (`/op`) or permission level 2.

### `pedestal affectplayers`
Inspects or configures player immunity for all pedestal fields server-wide.

```
/timestop pedestal affectplayers [true|false]
```

- **Arguments**:
  - `[true|false]` *(Optional)*: If omitted, displays the current setting.
    - `true` *(Default)*: Pedestal fields affect players according to normal owner, ally/sync (`/sync`), scoreboard team, and watch-resistance exemption rules.
    - `false`: Exempts all non-exempt players from pedestal fields entirely (players can move and interact freely, while mobs, fluids, and projectiles remain affected).
- **Persistence & Redstone**: The setting is saved with the world (`timestop_server_config`) and applies to active fields immediately. Handheld watches are unaffected.
- **Disarm Effect**: Executing `/timestop stop` disarms all loaded pedestals until their redstone power is toggled off and on again.
- **Example**:
  ```mcfunction
  /timestop pedestal affectplayers false
  /timestop pedestal affectplayers true
  ```

### `start`
Forces server-wide time distortion in the specified mode across all loaded dimensions.

```
/timestop start <mode> [seconds]
```

- **Arguments**:
  - `<mode>`: The temporal mode to activate.
    - `timestop`: Complete stasis. Freezes all non-exempt entities, projectile trajectories, block updates, fluid flow, falling blocks, weather cycles, and daylight progression.
    - `slowmotion`: Modulates server tick rate to 200 ms (5 TPS). Runs the entire world in uniform 0.25x slow motion.
    - `matrix`: Sets world tick rate to 5 TPS while applying transient attribute modifiers to the executing player (+300% movement speed, +300% attack speed).
    - `superhot`: Movement-scaled time dilation. Runs at 2 TPS when idle and dynamically scales up to 20 TPS based on player velocity, sprinting, jumping, and attacking.
    - `fastforward`: Accelerated simulation at 10 ms per tick (100 TPS). Daylight progression is rate-limited to 20 TPS to prevent graphical flickering.
    - `deceleration`: Projects an omnidirectional 6-meter bullet-dodge field centered on the player.
    - `rewind`: Rewinds the world timeline backwards for the specified duration (up to buffer capacity). Respects configured continuous or burst playback mode. When Rewind mode is disabled via `/timestop rewind include false`, starting rewind is blocked.
  - `[seconds]` *(Optional)*: Duration in seconds (1 to 3600 for standard modes; 1 to 60 for rewind). If omitted or set to 0, runs indefinitely (or for rewind, uses default configured buffer duration).
- **Example**:
  ```mcfunction
  /timestop start superhot 30
  /timestop start rewind 15
  ```

---

### `stop`
Immediately dissolves all active time distortions across the server.

```
/timestop stop
```

- **Execution Effects**:
  - Collapses all active localized player bubbles (`TemporalBubble`).
  - Resumes server-wide global time stop.
  - Discharges accumulated damage buffers on living entities and plays kinetic impact effects.
  - Re-launches frozen projectiles with accumulated kinetic bonuses.
  - Restores gravity and velocity to suspended falling blocks and primed TNT.
  - Strips transient Matrix speed attributes from initiators.
- **Example**:
  ```mcfunction
  /timestop stop
  ```

---

### `toggle`
Toggles between running time and stopped time.

```
/timestop toggle [seconds]
```

- **Behavior**:
  - If time is currently stopped or any bubbles are active: executes `/timestop stop`.
  - If time is running normally: executes `/timestop start timestop [seconds]`.
- **Example**:
  ```mcfunction
  /timestop toggle 15
  ```

---

### `exempt`
Manages the server-wide player exemption whitelist for global time stops.

```
/timestop exempt <add|remove> <targets>
```

- **Subcommands**:
  - `add <targets>`: Whitelists target player(s). Whitelisted players can move, sprint, attack, and interact freely during global time stop.
  - `remove <targets>`: Revokes exemption, causing target players to be frozen during global time stop.
- **Underlying Mechanic**: Immediately broadcasts `TimeStopSyncPacket` to all connected clients to update local client prediction.
- **Example**:
  ```mcfunction
  /timestop exempt add @a[team=Staff]
  /timestop exempt remove PlayerName
  ```

---

### `scope`
Configures server-wide temporal sphere scoping policies across all pocket watches.

```
/timestop scope <global|sphere|watch>
```
*(Aliases: `/timestop servermode <global|bubble>`, `/timestop scope bubble`, `/timestop globalmode <true|false>`)*

- **Options**:
  - `watch` *(Default)*: Each pocket watch respects its own internal configuration. Creative Watches can toggle between Global and Sphere via Shift+Right-Click, while survival watches project localized spheres.
  - `sphere` (or `bubble`): Overrides all watches to project localized temporal spheres scaled to their tier, preventing global freezes even from Creative Watches.
  - `global`: Forces all watches of every tier to trigger server-wide global time distortion.
- **Querying State**:
  Running `/timestop scope` without arguments outputs the current scope policy.
- **Example**:
  ```mcfunction
  /timestop scope sphere
  /timestop scope watch
  ```

---

### `redirect`
Controls the server-wide projectile redirection policy for slapped projectiles and kinetic barrier volleys.

```
/timestop redirect <look|return>
```

- **Options**:
  - `look`: Struck projectiles and kinetic barrier volleys follow the player's crosshair aim direction. Seamlessly compatible with vanilla arrows/fireballs and native TACZ bullets/rockets.
  - `return` *(Default)*: Struck projectiles reflect back toward their original sender. (Note: Players equipping the *Rune of Vector Control* always redirect along their look aim regardless of this server setting).
- **Querying State**:
  Running `/timestop redirect` without arguments outputs the active redirection policy.
- **Example**:
  ```mcfunction
  /timestop redirect look
  /timestop redirect return
  ```

---

### `servermode`
Legacy alias for `/timestop scope`. Sets the server-wide operational policy for survival pocket watches.

```
/timestop servermode <global|bubble>
```

- **Options**:
  - `bubble` *(Default)*: Survival pocket watches produce localized spherical bubbles scaled to their tier (Copper: 10m, Gilded: 16m, Diamond: 24m, Netherite: 32m). Dedicated server tick rate remains at 20 TPS for all players outside the sphere.
  - `global`: Forces all survival pocket watches to stop time across the entire server, identical to the Creative Clock.
- **Example**:
  ```mcfunction
  /timestop servermode bubble
  ```

---

### `speed`
Inspects or calibrates temporal simulation multipliers and drag rates across all modes.

```
/timestop speed
/timestop speed reset
/timestop speed <fastforward|slowmotion|matrix|superhot|drag> [value]
```

- **Subcommands**:
  - `/timestop speed`: Displays all current speed multipliers and their valid configurable ranges.
  - `reset`: Restores all mode speeds to their default calibrations.
  - `fastforward [value]`: Sets fast forward multiplier (valid: `1.1` – `50.0x`, default: `5.0x` / 100 TPS).
  - `slowmotion [value]`: Sets slow motion multiplier (valid: `0.01` – `0.99x`, default: `0.25x` / 5 TPS).
  - `matrix [value]`: Sets matrix world multiplier (valid: `0.01` – `0.99x`, default: `0.25x` / 5 TPS).
  - `superhot [value]`: Sets superhot idle rate (valid: `0.005` – `0.80x`, default: `0.10x` / 2 TPS).
  - `drag [value]`: Sets deceleration field drag coefficient (valid: `0.001` – `0.95x`, default: `0.05x`).
- **Network Sync**: Automatically synchronizes to all connected clients via `SyncSpeedConfigPacket`.
- **Examples**:
  ```mcfunction
  /timestop speed
  /timestop speed slowmotion 0.10
  /timestop speed fastforward 10.0
  /timestop speed reset
  ```

---

### `rewind include` & `allowrewind`
Controls whether **Rewind Mode** (`TimeMode.REWIND`) is included on watches and available on the server.

```
/timestop rewind include <true|false>
/timestop rewind allow <true|false>
/timestop rewind on|off
/timestop rewind enable|disable
/timestop allowrewind [true|false]
```

- **Default**: `true` (ON / Included).
- **When Disabled / Excluded (`false`, `off`, `disable`)**:
  - Rewind mode immediately disappears from all compatible pocket watches (Diamond, Netherite, Creative).
  - The Shift+Right-Click Mode Selection GUI dynamically collapses from 4 rows to 3 rows, removing the Rewind card.
  - Watch tooltips immediately hide Rewind mode, falling back to Slow Motion if previously selected.
  - Active rewind sessions and local rewind runners are immediately halted.
  - `/timestop start rewind` and `/timestop rewind` commands are blocked.
- **When Re-enabled / Allowed (`true`, `on`, `enable`)**:
  - Rewind mode immediately reappears on watches and inside the Mode Selection GUI.
  - Watches that were set to Rewind before it was disabled immediately restore Rewind mode.
- **Persistence**: Saved with the world NBT (`timestop_server_config`).
- **Network Sync**: Automatically broadcast via `SyncRewindAllowedPacket` to all connected clients and sent upon player login.
- **Example**:
  ```mcfunction
  /timestop rewind include false
  /timestop rewind on
  /timestop allowrewind true
  ```

---

### `rewind`
Triggers an immediate burst or continuous timeline rewind across the current scope.

```
/timestop rewind [seconds]
/timestop rewind mode <burst|continuous>
/timestop rewind buffer [seconds]
/timestop rewind buffer <reset|clear>
/timestop rewind ondeath [true|false]
/timestop rewind status
```

- **Subcommands**:
  - `[seconds]`: Triggers a rewind of the specified duration (up to buffer capacity).
  - `mode <burst|continuous>`: Toggles between instant block/entity rollback (`burst`) and smooth reverse frame playback (`continuous`).
  - `buffer [seconds]`: Configures timeline memory recording buffer (default: 30s).
  - `buffer reset`: Resets timeline buffer to 30 seconds default.
  - `buffer clear`: Wipes current timeline memory frames.
  - `ondeath [true|false]`: Toggles automatic death rewind rescue for all players without consuming runes.
  - `status`: Displays recorded frames, memory usage, buffer capacity, and watch inclusion state.

---

### `buffer`
Administrative shorthand for timeline buffer management (equivalent to `/timestop rewind buffer`).

```
/timestop buffer
/timestop buffer reset
/timestop buffer clear
/timestop buffer <seconds>
```

- **Subcommands**:
  - `/timestop buffer`: Inspects recorded frames, capacity, memory consumption, and watch inclusion status.
  - `reset`: Cancels active rewinds, flushes recorded frames, and resets capacity to default 30 seconds.
  - `clear`: Clears all recorded timeline frames while preserving configured capacity.
  - `<seconds>`: Sets buffer recording capacity (1 to 60 seconds).
- **Example**:
  ```mcfunction
  /timestop buffer 45
  /timestop buffer reset
  ```

---

### `status`
Outputs real-time diagnostics regarding the temporal engine state to chat.

```
/timestop status
```

- **Output Data**:
  - Active watch scope policy (`GLOBAL`, `SPHERE`, or `WATCH`).
  - Active projectile redirection policy (`LOOK` vs `RETURN`).
  - Active localized bubbles (owner, watch tier, radius in meters, time mode, and remaining duration).
  - Global time stop status and remaining duration ticks.
- **Example**:
  ```mcfunction
  /timestop status
  ```

---

## 2. Time Sync Commands (`/sync`, `/timesync`)

These commands allow players in multiplayer survival to establish temporal resonance with each other. Active Resonators (and vanilla scoreboard teammates) can freely move, attack, and shoot within each other's localized time spheres without freezing.

Available to all players (Permission Level 0). `/sync`, `/timesync`, and `/timestop sync` are functional aliases.

### Command Reference Table

| Syntax | Description |
| :--- | :--- |
| `/sync add <player>` | Sends a Time Sync request. Expires automatically after 60 seconds. |
| `/sync <player>` | Shorthand alias for `/sync add <player>`. |
| `/sync accept <player>` | Accepts a pending Time Sync invitation. Mutual field resonance is established immediately. |
| `/sync decline <player>` | Declines an incoming Time Sync invitation. |
| `/sync remove <name>` | Disconnects an active Resonator by username (supports tab-completion for online and offline players). |
| `/sync list` | Displays active Resonators with online status and pending incoming requests with clickable action buttons. |
| `/sync clear` | Disconnects all active Resonators for the executing player. |
| `/sync help` | Displays the in-game command reference. |

---

## 3. Practical Implementation Tutorials & Use Cases

### Tutorial 1: Dedicated SMP Server Configuration
**Goal**: Allow survival players to craft and use pocket watches without freezing other players across the world or destabilizing server tick rates.

1. Verify server mode is set to localized bubble mode:
   ```mcfunction
   /timestop servermode bubble
   ```
2. In this mode, each player's watch generates an isolated spatial sphere centered on their coordinates.
3. Dedicated server TPS remains at 20 TPS across all dimensions, regardless of how many players activate slow-motion, matrix, or time-stop fields.
4. If an admin needs to pause the entire server for an announcement or maintenance:
   ```mcfunction
   /timestop start timestop
   ```

---

### Tutorial 2: Cooperative Dungeon Raids & Boss Encounters
**Goal**: Coordinate team combat during stasis without teammates freezing each other.

1. Prior to entering the dungeon, party members establish mutual resonance:
   ```mcfunction
   /sync add Teammate1
   /sync add Teammate2
   ```
2. Teammate1 and Teammate2 accept via chat or by executing:
   ```mcfunction
   /sync accept LeaderName
   ```
3. When the party engages a dungeon boss, the leader activates `TIME_STOP`.
4. Hostile mobs, falling hazards, and incoming projectiles freeze in mid-air.
5. Because party members are registered Resonators, they remain fully mobile inside the sphere. Teammates can:
   - Reposition behind vulnerable targets.
   - Left-click suspended arrows to redirect them back toward enemies.
   - Right-click suspended projectiles to pluck them into their inventory.
   - Strike primed TNT or falling blocks to assign accumulated kinetic launch vectors.
6. When the time stop expires or collapses, all accumulated damage, status effects, and kinetic vectors discharge simultaneously.

---

### Tutorial 3: Custom Minigames & Arena Duels
**Goal**: Create an arena with customized time dilation rules using command blocks.

- **Superhot Arena**:
  Place a repeating command block outside the arena:
  ```mcfunction
  /timestop start superhot 120
  ```
  In this mode, time only moves when fighters move or swing. Standing still pauses all projectile flight and enemy animations.

- **Exempting Referees / Spectators**:
  To allow spectators to fly around without triggering Superhot time progression or being affected by stasis:
  ```mcfunction
  /timestop exempt add RefereeName
  ```
  *(Note: Players in Spectator mode (`/gamemode spectator`) are automatically exempt by default).*

---

### Tutorial 4: Technical Farm Optimization & Diagnostics
**Goal**: Accelerate long-term farm tick testing or inspect falling entity mechanics.

- **Farm Simulation Acceleration**:
  Run the server simulation at 5x speed (100 TPS) for 60 seconds to rapidly test crop growth, mob spawners, and hopper throughput:
  ```mcfunction
  /timestop start fastforward 60
  ```
- **Trajectory & Kinetic Vector Inspection**:
  Freeze flying projectiles, falling sand, or anvils mid-flight to examine hitboxes and angles:
  ```mcfunction
  /timestop start timestop
  ```
  Strike the suspended entity to stack velocity, then unfreeze to inspect the resulting trajectory:
  ```mcfunction
  /timestop stop
  ```

---

### Tutorial 5: Cinematic Recording & Machinima
**Goal**: Film cinematic camera movements around frozen battlefields.

1. Switch the camera operator to Spectator mode:
   ```mcfunction
   /gamemode spectator CameraOperator
   ```
2. Freeze the scene at the desired moment:
   ```mcfunction
   /timestop start timestop
   ```
3. Use shader packs and smooth camera controls to pan around suspended explosions, arrows, and mobs.
4. Resume time when finished:
   ```mcfunction
   /timestop stop
   ```

---

## 4. Item Identifier Reference (`/give`)

For testing, server shops, or map-making, all temporal pocket watches, runes, and ammunition can be acquired via standard `/give` commands.

### Pocket Watches
| Item Name | Item Identifier | Command Example |
| :--- | :--- | :--- |
| **Copper Watch** (Tier 1) | `timestop:copper_watch` | `/give @s timestop:copper_watch` |
| **Golden Watch** (Tier 2) | `timestop:chronos_watch` | `/give @s timestop:chronos_watch` |
| **Diamond Watch** (Tier 3) | `timestop:diamond_watch` | `/give @s timestop:diamond_watch` |
| **Netherite Watch** (Tier 4) | `timestop:netherite_watch` | `/give @s timestop:netherite_watch` |
| **Creative Watch** (Tier 5) | `timestop:creative_watch` | `/give @s timestop:creative_watch` |

### Temporal Runes
| Rune Name | Item Identifier | Command Example |
| :--- | :--- | :--- |
| **Blank Temporal Rune** | `timestop:blank_rune` | `/give @s timestop:blank_rune` |
| **Rune of Redirection** | `timestop:rune_deflection` | `/give @s timestop:rune_deflection` |
| **Rune of Snatching** | `timestop:rune_snatching` | `/give @s timestop:rune_snatching` |
| **Rune of Phasing** | `timestop:rune_phasing` | `/give @s timestop:rune_phasing` |
| **Rune of Kinetic Amplification** | `timestop:rune_kinetic` | `/give @s timestop:rune_kinetic` |
| **Rune of Chrono-Vampirism** | `timestop:rune_vampirism` | `/give @s timestop:rune_vampirism` |
| **Rune of Volatile Stasis** | `timestop:rune_volatile` | `/give @s timestop:rune_volatile` |
| **Rune of the Tachyon** | `timestop:rune_tachyon` | `/give @s timestop:rune_tachyon` |
| **Rune of the Dead Eye** | `timestop:rune_deadeye` | `/give @s timestop:rune_deadeye` |
| **Rune of Voltaic Ricochet** | `timestop:rune_ricochet` | `/give @s timestop:rune_ricochet` |
| **Rune of Orbital Redirection** | `timestop:rune_orbital` | `/give @s timestop:rune_orbital` |
| **Rune of Spatial Transposition** | `timestop:rune_transposition` | `/give @s timestop:rune_transposition` |
| **Rune of the Kinetic Barrier** | `timestop:rune_barrier` | `/give @s timestop:rune_barrier` |
| **Rune of Vector Control** | `timestop:rune_vector` | `/give @s timestop:rune_vector` |
| **Rune of Rewind (Auto Death Rewind)** | `timestop:rune_rewind` | `/give @s timestop:rune_rewind` |
| **Rune of the Marksman (+RICOSHOT)** *(TACZ)* | `timestop:rune_coin` | `/give @s timestop:rune_coin` |
| **Chrono Coin** *(TACZ)* | `timestop:chrono_coin` | `/give @s timestop:chrono_coin` |


### Clockwork Pedestals (1.5.0+)
| Item Name | Item Identifier | Command Example |
| :--- | :--- | :--- |
| **Copper Pedestal** (Tier 1) | `timestop:copper_pedestal` | `/give @s timestop:copper_pedestal` |
| **Golden Pedestal** (Tier 2) | `timestop:golden_pedestal` | `/give @s timestop:golden_pedestal` |
| **Diamond Pedestal** (Tier 3) | `timestop:diamond_pedestal` | `/give @s timestop:diamond_pedestal` |
| **Netherite Pedestal** (Tier 4) | `timestop:netherite_pedestal` | `/give @s timestop:netherite_pedestal` |
| **Creative Pedestal** (Tier 5) | `timestop:creative_pedestal` | `/give @s timestop:creative_pedestal` |

---

### Ruined Observatories & Acropolis Citadel (1.6.0+)

#### Natural Ruin Discovery (`/locate structure`)
Use `/locate structure` to find natural ruins across their dedicated biomes:
- `/locate structure timestop:ruined_observatory_acropolis`: 70x70 Mountaintop Citadel Sanctuary (Stony Peaks, Jagged Peaks, Frozen Peaks).
- `/locate structure timestop:ruined_observatory_highland`: Windswept Hills, Gravelly Hills, Meadows, Stony Peaks.
- `/locate structure timestop:ruined_observatory_forest`: Forests, Birch Forests, Old Growth Birch, Dark Forests.
- `/locate structure timestop:ruined_observatory_cherry`: Cherry Groves (cherry wood, blossoms, pink petals).
- `/locate structure timestop:ruined_observatory_floral`: Flower Forests (birch timber, azalea, flower beds).
- `/locate structure timestop:ruined_observatory_windswept`: Windswept Forests (dark oak/spruce alpine timber, wind-scoured stone).

#### Procedural Worldgen Placement (`/place structure`)
`/place structure <id> ~ ~ ~` generates the structure with terrain adaptation, bounds checking, and procedural foundations:
```mcfunction
/place structure timestop:ruined_observatory_acropolis ~ ~ ~
/place structure timestop:ruined_observatory_highland ~ ~ ~
/place structure timestop:ruined_observatory_forest ~ ~ ~
/place structure timestop:ruined_observatory_cherry ~ ~ ~
/place structure timestop:ruined_observatory_floral ~ ~ ~
/place structure timestop:ruined_observatory_windswept ~ ~ ~
```

#### Deterministic Template Placement (`/place template`)
For testing, inspection, or map building without terrain validation:
```mcfunction
/place template timestop:ruined_observatory/highland ~ ~ ~ none none 1.0 42
/place template timestop:ruined_observatory/forest ~ ~ ~ none none 1.0 42
/place template timestop:ruined_observatory/cherry ~ ~ ~ none none 1.0 42
/place template timestop:ruined_observatory/floral ~ ~ ~ none none 1.0 42
/place template timestop:ruined_observatory/windswept ~ ~ ~ none none 1.0 42
```
See [placement details and editable resources](OBSERVATORY.md).

