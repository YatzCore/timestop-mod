# Time Stop 1.3.8: stray projectile lines

Investigation found two concrete faults in BulletTracerRenderer that fit the reported thin white line after redirection:

- Camera-relative vertices bypassed the render event pose matrix. Camera rotation therefore placed the tracer incorrectly relative to its projectile.
- Zero-velocity projectiles fell back to old motion or a synthetic look-direction velocity of 1.5, creating a six-block white trail even while stationary. Redirection rotates that synthetic trail.

Applied the event pose matrix to every tracer vertex, used interpolated projectile positions, and removed stationary-motion fallbacks. Native TacZ bullets are excluded from our additional tracer overlay so their own renderer remains responsible for bullet visuals. No changes to TacZ's JAR, bullet physics, gun sounds, or animations.

Verification: Gradle release build and resource validation passed; packaged JSON and refmap checked. This is a client rendering change; no graphical reproduction or visual confirmation was available, so confirmation of the exact reported artifact still requires in-game testing.

Installed 1.3.8 in the profile's mods directory; previous JAR and source snapshot backed up in ../mod-backups/2026-09-10-before-1.3.8/. Restart Minecraft to load the update.
