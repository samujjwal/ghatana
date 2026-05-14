package com.ghatana.yappc.services.generate;

/**
 * Thread-safe mutable {@link AiHealthProvider} for use in unit tests.
 *
 * <p>Allows individual tests to flip the degradation flag without casting to
 * the concrete implementation class.
 *
 * <p><b>Test use only.</b> Do not use in production code.
 */
public final class MutableAiHealthProvider implements AiHealthProvider {

    private volatile boolean degraded;

    public MutableAiHealthProvider(boolean initialState) {
        this.degraded = initialState;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    @Override
    public boolean isDegraded() {
        return degraded;
    }
}
