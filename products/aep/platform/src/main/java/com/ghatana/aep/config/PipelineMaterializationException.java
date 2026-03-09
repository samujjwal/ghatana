/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.aep.config;

/**
 * Thrown when a {@link PipelineMaterializer} cannot convert a
 * {@link com.ghatana.aep.domain.pipeline.PipelineSpec} into a runtime
 * {@link com.ghatana.core.pipeline.Pipeline}.
 *
 * @doc.type class
 * @doc.purpose Pipeline materialization exception
 * @doc.layer product
 *
 * @author Ghatana AI Platform
 * @since 3.0.0
 */
public class PipelineMaterializationException extends RuntimeException {

    public PipelineMaterializationException(String message) {
        super(message);
    }

    public PipelineMaterializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
