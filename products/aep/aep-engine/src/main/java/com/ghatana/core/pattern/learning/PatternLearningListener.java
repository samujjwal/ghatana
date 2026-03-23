package com.ghatana.core.pattern.learning;

/**
 * Listener for pattern learning events.
 *
 * @doc.type interface
 * @doc.purpose Pattern learning event notifications
 * @doc.layer core
 * @doc.pattern Learning
 */
public interface PatternLearningListener {

    /**
     * Called when a learning event occurs.
     */
    void onLearningEvent(PatternLearningEvent event);
}
