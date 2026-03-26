package com.ghatana.phr.observability;

import com.ghatana.kernel.observability.ExplainabilityContext;
import com.ghatana.kernel.observability.KernelTelemetryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component for PHRTelemetryManagerImpl
 *
 * @doc.type class
 * @doc.purpose Component for PHRTelemetryManagerImpl
 * @doc.layer product
 * @doc.pattern Manager
 */
public class PHRTelemetryManagerImpl implements KernelTelemetryManager {
    private static final Logger logger = LoggerFactory.getLogger(PHRTelemetryManagerImpl.class);
    private final Map<String, Double> metrics = new ConcurrentHashMap<>();
    private final Map<String, Long> counters = new ConcurrentHashMap<>();

    @Override
    public void recordMetric(String name, double value, String... tags) {
        String key = buildKey(name, tags);
        metrics.put(key, value);
        logger.debug("Metric recorded: {} = {}", key, value);
    }

    @Override
    public void recordEvent(Event event) {
        logger.info("Event recorded: type={}, source={}, timestamp={}", 
            event.getEventType(), event.getSource(), event.getTimestamp());
    }

    @Override
    public ExplainabilityContext createExplainabilityContext(AgentAction action) {
        return new PHRExplainabilityContext(
            java.util.UUID.randomUUID().toString(),
            action.getAgentId(),
            "phr-model-default"
        );
    }

    @Override
    public Timer startTimer(String name, String... tags) {
        return new PHRTimer(name, tags);
    }

    @Override
    public void incrementCounter(String name, long increment, String... tags) {
        String key = buildKey(name, tags);
        counters.merge(key, increment, Long::sum);
        logger.debug("Counter incremented: {} by {}", key, increment);
    }

    @Override
    public void recordGauge(String name, double value, String... tags) {
        String key = buildKey(name, tags);
        metrics.put(key, value);
        logger.debug("Gauge recorded: {} = {}", key, value);
    }

    @Override
    public void recordHistogram(String name, double value, String... tags) {
        String key = buildKey(name, tags);
        metrics.put(key, value);
        logger.debug("Histogram recorded: {} = {}", key, value);
    }

    private String buildKey(String name, String... tags) {
        if (tags.length == 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name);
        for (int i = 0; i < tags.length; i += 2) {
            if (i + 1 < tags.length) {
                sb.append(",").append(tags[i]).append("=").append(tags[i + 1]);
            }
        }
        return sb.toString();
    }

    private class PHRTimer implements Timer {
        private final String name;
        private final String[] tags;
        private final long startTime;

        public PHRTimer(String name, String... tags) {
            this.name = name;
            this.tags = tags;
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public void stop() {
            long duration = System.currentTimeMillis() - startTime;
            recordMetric(name + ".duration", duration, tags);
            logger.debug("Timer stopped: {} = {}ms", name, duration);
        }

        @Override
        public long getElapsedMillis() {
            return System.currentTimeMillis() - startTime;
        }
    }
}
