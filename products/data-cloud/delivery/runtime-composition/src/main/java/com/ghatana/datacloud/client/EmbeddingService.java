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

import io.activej.promise.Promise;

/**
 * Service provider interface for generating vector embeddings.
 *
 * <p>Implementations should connect to embedding services like:
 * <ul>
 *   <li>OpenAI Embeddings API</li>
 *   <li>Hugging Face models</li>
 *   <li>Local embedding models (e.g., sentence-transformers)</li>
 *   <li>Azure OpenAI</li>
 *   <li>Cohere</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EmbeddingService service = new OpenAIEmbeddingService(apiKey);
 * Promise<float[]> embedding = service.embed("Hello, world!");
 * }</pre>
 *
 * @see EmbeddingAspect
 * @doc.type interface
 * @doc.purpose Embedding generation SPI
 * @doc.layer core
 * @doc.pattern Strategy, Service Provider
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface EmbeddingService {

    /**
     * Generates an embedding vector for the given text.
     *
     * @param text the text to embed
     * @return promise of embedding vector
     */
    Promise<float[]> embed(String text);

    /**
     * Generates embeddings for multiple texts in a batch.
     *
     * @param texts texts to embed
     * @return promise of embedding vectors
     */
    Promise<float[][]> embedBatch(String... texts);

    /**
     * Returns the embedding dimension.
     *
     * @return vector dimension (e.g., 1536 for OpenAI ada-002)
     */
    int dimension();

    /**
     * Returns the model name.
     *
     * @return model identifier
     */
    String modelName();

    /**
     * Returns true if the service is healthy.
     *
     * @return promise of health status
     */
    Promise<Boolean> isHealthy();
}
