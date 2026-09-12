# Own projectile capture — 1.3.11

The previous implementation deliberately skipped the defender's own projectiles. Orbital and Kinetic Barrier now accept the player's own shots, including TacZ RPG-7 rockets, M320 grenades, ordinary gun rounds and vanilla projectiles.

- Orbital interception uses the same swept check for vanilla and TacZ, before movement or stasis cancellation.
- Kinetic Barrier catches outgoing own shots in its forward field. Allied players' shots retain the existing exemption.
- Capture retrieves saved velocity from frozen projectiles and removes their old stasis record, preserving native launch speed.
- Captured projectiles do not tick their flight lifetime or explosive fuse.
- Released projectiles are marked with the releasing player's UUID so that player's orbit or barrier does not immediately catch the volley again. Other players can still capture them.
- An own-shot barrier volley fires along the player's look direction even in return-to-shooter mode.

Regression coverage uses the installed RPG-7, M320 and Glock gun definitions with real EntityKineticBullet construction. Both runes are checked with moving and actually suspended shots, 120 held ticks, and subsequent release. Equivalent vanilla-arrow cases run without TacZ.
