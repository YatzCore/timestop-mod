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
  - `[seconds]` *(Optional)*: Duration in seconds (integer between 1 and 3600). If omitted or set to 0, runs indefinitely until manually stopped.
- **Example**:
  ```mcfunction
  /timestop start superhot 30
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

### `servermode`
Sets the server-wide operational policy for survival pocket watches.

```
/timestop servermode <global|bubble>
```
*(Aliases: `/timestop scope <global|bubble>`, `/timestop globalmode <true|false>`)*

- **Options**:
  - `bubble` *(Default)*: Survival pocket watches produce localized spherical bubbles scaled to their tier (Copper: 10m, Gilded: 16m, Diamond: 24m, Netherite: 32m). Dedicated server tick rate remains at 20 TPS for all players outside the sphere.
  - `global`: Forces all survival pocket watches to stop time across the entire server, identical to the Creative Clock.
- **Example**:
  ```mcfunction
  /timestop servermode bubble
  ```

---

### `status`
Outputs real-time diagnostics regarding the temporal engine state to chat.

```
/timestop status
```

- **Output Data**:
  - Current server watch policy (`GLOBAL` vs `BUBBLE`).
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
