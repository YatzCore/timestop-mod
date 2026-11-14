package com.timestop.prediction;

/** Implemented by optional native projectile mixins; never loads a gun mod. */
public interface NativePredictionPhysics {
    double timestop$predictionGravity();
    double timestop$predictionDrag();
}
