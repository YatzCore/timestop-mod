package com.timestop.prediction;

import net.minecraft.world.entity.Entity;

/** Read-only access to the fractional local entity tick budget. */
public interface PredictionTickClock {
    float timestop$predictionPhase(Entity entity);
}
