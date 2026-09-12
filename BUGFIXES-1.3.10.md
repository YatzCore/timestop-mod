# Bug fixes — 1.3.10

- TacZ 1.1.8-hotfix bullets and explosive rounds now enter the rune impact pipeline. Canceled hits preserve the native round instead of consuming penetration or detonating its payload.
- Defensive runes inspect the full native flight segment. Redirection, Snatching, Phasing and Orbital capture catch fast rounds before they cross the player. Snatching returns the native ammunition item.
- Passive fields intercept fast rounds at their boundary and slow movement while preserving native gravity, drag and full-speed velocity. Orbital releases retain native projectile speed. Voltaic Ricochet accepts TacZ rounds; Volatile Stasis receives native impact events. Coin hits use swept collision checks.
- Superhot aggregates activity from current occupants separately for each bubble. Any active occupant advances time normally; an idle bubble runs at five percent speed. Departures, disconnects and stale reports cannot leave a bubble running. All players in Superhot can initiate movement. Client activity is stored and synchronized by bubble ID.
- Coin input checks the socketed Marksman rune and available charges. Transposition checks its rune and a valid target before flashing, including when both actions share a mouse button. Coin charge synchronization also works when a rune is socketed after login.

The network protocol changed to version 6. Multiplayer servers and clients must use the same updated mod.

Validation commands:

```text
./gradlew.bat -PgameTests -PtaczTests runGameTestServer
./gradlew.bat -PgameTests -PtestDirectory=build/gametest-core runGameTestServer
./gradlew.bat build
```

The opt-in tests use the installed TacZ JAR and stage a copy under build/taczDependency to tolerate launcher-added filename suffixes. Test classes are excluded from the release JAR. Automated server tests do not replace visual testing in a two-client multiplayer session.
