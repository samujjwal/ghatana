/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.agent.stream;

import com.ghatana.agent.AgentConfig;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration for a {@link StreamProcessorAgent}.
 *
 * <p>Controls checkpointing, watermarking, windowing, backpressure, and
 * event type declarations for stateful stream processing agents.
 *
 * @doc.type record
 * @doc.purpose Configuration for stream processor agents
 * @doc.layer platform
 * @doc.pattern ValueObject
 *
 * @author Ghatana AI Platform
 * @since 2.1.0
 */
@Value
@Builder(toBuilder = true)
public class StreamProcessorAgentConfig {

    /**
     * Stream processing strategy subtype.
     * Default: {@link StreamProcessorSubtype#TRANSFORMATION}.
     */
    @Builder.Default
    StreamProcessorSubtype subtype = StreamProcessorSubtype.TRANSFORMATION;

    /**
     * Event types this processor consumes (e.g., {@code "event.raw.*"}).
     * Supports glob patterns for topic matching.
     */
    @Builder.Default
    java.util.List<String> inputEventTypes = java.util.List.of();

    /**
     * Event types this processor produces (e.g., {@code "event.enriched.*"}).
     */
    @Builder.Default
    java.util.List<String> outputEventTypes = java.util.List.of();

    /**
     * Checkpoint interval. Processor state is persisted at this frequency.
     * Set to {@code Duration.ZERO} to disable checkpointing (stateless processors).
     * Default: 30 seconds.
     */
    @Builder.Default
    Duration checkpointInterval = Duration.ofSeconds(30);

    /**
     * Watermark lag tolerance for out-of-order event handling.
     * Events arriving later than this are dropped or sent to dead-letter.
     * Default: 5 seconds.
     */
    @Builder.Default
    Duration watermarkLag = Duration.ofSeconds(5);

    /**
     * Maximum number of events to buffer in-flight before applying backpressure.
     * Default: 1000.
     */
    @Builder.Default
    int maxBufferSize = 1000;

    /**
     * Maximum batch size for bulk event processing.
     * 1 = one-at-a-time (lowest latency), higher values improve throughput.
     * Default: 100.
     */
    @Builder.Default
    int processingBatchSize = 100;

    /**
     * Processing timeout per event batch.
     * Default: 10 seconds.
     */
    @Builder.Default
    Duration batchTimeout = Duration.ofSeconds(10);

    /**
     * Additional processor-specific parameters (window size, join keys, etc.).
     */
    @Builder.Default
    Map<String, Object> processorParams = Map.of();

    /**
     * Creates a {@link StreamProcessorAgentConfig} from a generic {@link AgentConfig}.
     *
     * @param config the raw agent configuration
     * @return a typed StreamProcessorAgentConfig
     */
    public static StreamProcessorAgentConfig from(AgentConfig config) {
        Map<String, Object> props = config.getProperties();
        StreamProcessorAgentConfigBuilder builder = StreamProcessorAgentConfig.builder();

        Object subtype = props.get("subtype");
        if (subtype instanceof String s) {
            builder.subtype(StreamProcessorSubtype.valueOf(s.toUpperCase(Locale.ROOT)));
        }

        Object checkpointMsObj = props.get("checkpointIntervalMs");
        if (checkpointMsObj instanceof Number n) {
            builder.checkpointInterval(Duration.ofMillis(n.longValue()));
        }

        Object bufferSizeObj = props.get("maxBufferSize");
        if (bufferSizeObj instanceof Number n) {
            builder.maxBufferSize(n.intValue());
        }

        Object batchSizeObj = props.get("processingBatchSize");
        if (batchSizeObj instanceof Number n) {
            builder.processingBatchSize(n.intValue());
        }

        return builder.build();
    }
}
