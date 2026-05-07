/**
 * Vector Memory Plugin - Semantic vector storage for data records.
 *
 * <p>This plugin implements the StoragePlugin SPI to provide vector-based
 * storage and retrieval for data records. It enables semantic similarity
 * search, allowing the system to find related records based on meaning
 * rather than exact matches.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.plugins.vector.VectorMemoryPlugin} - StoragePlugin implementation</li>
 *   <li>{@link com.ghatana.datacloud.plugins.vector.VectorRecord} - Record with embedding vector</li>
 *   <li>{@link com.ghatana.datacloud.plugins.vector.SimilaritySearch} - Semantic search operations</li>
 *   <li>{@link com.ghatana.datacloud.plugins.vector.VectorIndex} - Efficient similarity indexing</li>
 * </ul>
 *
 * <h2>Capabilities Provided</h2>
 * <ul>
 *   <li>Semantic similarity search</li>
 *   <li>K-nearest neighbors retrieval</li>
 *   <li>Embedding generation integration</li>
 *   <li>Tiered vector storage (hot/warm/cold)</li>
 * </ul>
 *
 * <h2>Integration with AI Brain</h2>
 * <p>The vector memory plugin serves as the semantic memory for the AI brain,
 * enabling:
 * <ul>
 *   <li>Context retrieval based on semantic relevance</li>
 *   <li>Pattern matching through embedding similarity</li>
 *   <li>Associative memory for related concepts</li>
 * </ul>
 *
 * @doc.type package
 * @doc.purpose Vector-based semantic storage for data records
 * @doc.layer plugin
 * @doc.pattern Adapter, Repository
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 * @see com.ghatana.datacloud.spi.StoragePlugin
 */
@org.jetbrains.annotations.ApiStatus.Experimental
package com.ghatana.datacloud.plugins.vector;
