# Orbital outgoing-shot correction — 1.3.12

Orbital Redirection no longer automatically collects the player's ordinary outgoing shots. Own projectiles remain collectible after time stop has actually suspended them. Incoming projectiles from other shooters and released-volley protection retain their behavior. The rule is shared by swept/proximity interception and impact interception; Kinetic Barrier behavior is unchanged.

Regression cases cover fresh and frozen RPG-7 rockets, M320 grenades, Glock rounds and vanilla arrows. Fresh own shots must fly clear without increasing the orbit count; frozen own shots must still be captured, held and released.
