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

package com.ghatana.datacloud.client;

import com.ghatana.datacloud.record.Record;
import io.activej.promise.Promise;

import java.util.function.Function;

/**
 * AI aspect for generating vector embeddings on data records.
 *
 * <p>This aspect runs in POST phase to generate embeddings after
 * records are persisted. Embeddings are stored in metadata for
 * later vector similarity searches.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * var aspect = EmbeddingAspect.builder()
 *     .embeddingService(openAIService)
 *     .textExtractor(record -> record.data().toString())
 *     .async(true)  // Don't block main operation
 *     .build();
 * }</pre>
 *
 * <h2>Context Attributes</h2>
 * <ul>
 *   <li>{@code embedding.vector} - The generated embedding vector</li>
 *   <li>{@code embedding.dimension} - Vector dimension</li>
 *   <li>{@code embedding.model} - Model used for embedding</li>
 * </ul>
 *
 * @see AIAspect
 * @see EmbeddingService
 * @doc.type class
 * @doc.purpose Embedding generation aspect
 * @doc.layer core
 * @doc.pattern Aspect, Decorator
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public final class EmbeddingAspect implements AIAspect<Record, Record> {

    /** Context key for embedding vector */
    public static final String ATTR_VECTOR = "embedding.vector";
    /** Context key for embedding dimension */
    public static final String ATTR_DIMENSION = "embedding.dimension";
    /** Context key for model name */
    public static final String ATTR_MODEL = "embedding.model";

    private final EmbeddingService embeddingService;
    private final Function<Record, String> textExtractor;
    private final int priority;
    private final boolean async;

    private EmbeddingAspect(Builder builder) {
        this.embeddingService = builder.embeddingService;
        this.textExtractor = builder.textExtractor;
        this.priority = builder.priority;
        this.async = builder.async;
    }

    @Override
    public String name() {
        return "embedding";
    }

    @Override
    public Phase phase() {
        return Phase.POST;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public boolean isAsync() {
        return async;
    }

    @Override
    public boolean isApplicable(OperationType operation, AIAspectContext context) {
        // Only embed on CREATE and UPDATE
        return operation == OperationType.CREATE || operation == OperationType.UPDATE;
    }

    @Override
    public Promise<Record> process(Record input, AIAspectContext context) {
        String text = textExtractor.apply(input);
        
        if (text == null || text.isBlank()) {
            return Promise.of(input);
        }

        return embeddingService.embed(text)
                .map(vector -> {
                    // Store embedding in context for downstream use
                    context.setAttribute(ATTR_VECTOR, vector);
                    context.setAttribute(ATTR_DIMENSION, embeddingService.dimension());
                    context.setAttribute(ATTR_MODEL, embeddingService.modelName());
                    
                    // Return input unchanged (embedding stored in context/metadata)
                    return input;
                });
    }

    /**
     * Creates a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for EmbeddingAspect.
     */
    public static final class Builder {
        private EmbeddingService embeddingService;
        private Function<Record, String> textExtractor = record -> record.data().toString();
        private int priority = 100;
        private boolean async = true; // Default to async to not block

        private Builder() {
        }

        /**
         * Sets the embedding service.
         *
         * @param service embedding service
         * @return this builder
         */
        public Builder embeddingService(EmbeddingService service) {
            this.embeddingService = service;
            return this;
        }

        /**
         * Sets the text extractor function.
         *
         * @param extractor function to extract text from record
         * @return this builder
         */
        public Builder textExtractor(Function<Record, String> extractor) {
            this.textExtractor = extractor;
            return this;
        }

        /**
         * Sets the priority.
         *
         * @param priority priority value
         * @return this builder
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Sets whether to run asynchronously.
         *
         * @param async true for async
         * @return this builder
         */
        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        /**
         * Builds the aspect.
         *
         * @return configured aspect
         */
        public EmbeddingAspect build() {
            if (embeddingService == null) {
                throw new IllegalStateException("EmbeddingService is required");
            }
            return new EmbeddingAspect(this);
        }
    }
}
