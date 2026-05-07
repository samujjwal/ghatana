package com.ghatana.orchestrator.config;

import java.time.Duration;

/**
 * Configuration for the Orchestrator component.
 *
 * <p>Controls pipeline refresh intervals, concurrency limits, cache timeout,
 * and periodic refresh behaviour. All properties have sensible production defaults.
 *
 * @doc.type class
 * @doc.purpose Orchestrator runtime configuration — refresh intervals, cache settings, concurrency limits
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public class OrchestratorConfig {

    private Duration refreshInterval = Duration.ofMinutes(5);
    private int maxConcurrentRefreshes = 5;
    private Duration cacheTimeout = Duration.ofMinutes(10);
    private boolean enablePeriodicRefresh = true;

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public int getMaxConcurrentRefreshes() {
        return maxConcurrentRefreshes;
    }

    public void setMaxConcurrentRefreshes(int maxConcurrentRefreshes) {
        this.maxConcurrentRefreshes = maxConcurrentRefreshes;
    }

    public Duration getCacheTimeout() {
        return cacheTimeout;
    }

    public void setCacheTimeout(Duration cacheTimeout) {
        this.cacheTimeout = cacheTimeout;
    }

    public boolean isEnablePeriodicRefresh() {
        return enablePeriodicRefresh;
    }

    public void setEnablePeriodicRefresh(boolean enablePeriodicRefresh) {
        this.enablePeriodicRefresh = enablePeriodicRefresh;
    }
}
