/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Knowledge Module
 */
package com.ghatana.yappc.knowledge.di;

import com.ghatana.agent.memory.retrieval.RetrievalPipeline;
import com.ghatana.yappc.knowledge.retrieval.YappcBM25Retriever;
import com.ghatana.yappc.knowledge.retrieval.YappcDenseVectorRetriever;
import io.activej.inject.annotation.Named;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ActiveJ DI module for YAPPC Knowledge subsystem.
 *
 * <p>Provides bindings for semantic search and memory retrieval:
 * <ul>
 *   <li>{@link YappcBM25Retriever} — Sparse lexical retrieval via PostgreSQL FTS
 *   <li>{@link YappcDenseVectorRetriever} — Dense semantic retrieval via DataCloud vector store
 * </ul>
 *
 * <p>All IO-bound operations use {@code Promise.ofBlocking(executor, ...)}
 * per Golden Rule #3 (never block the event loop).</p>
 *
 * @doc.type class
 * @doc.purpose DI module for knowledge/memory retrieval services
 * @doc.layer product
 * @doc.pattern Module
 *
 * @since 2.4.0
 */
public class KnowledgeModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeModule.class);

    @Override
    protected void configure() {
        logger.info("Configuring YAPPC Knowledge Module DI bindings");
    }

    /**
     * Provides BM25 lexical retriever backed by PostgreSQL full-text search.
     *
     * <p>Implements sparse term-based semantic search using tsvector/tsquery
     * with BM25-like scoring via ts_rank_cd().</p>
     *
     * @param dataSource JDBC data source
     * @param executor blocking executor for JDBC queries
     * @return BM25 retriever implementing RetrievalPipeline
     */
    @Provides
    @Named("bm25")
    RetrievalPipeline bm25Retriever(DataSource dataSource, Executor executor) {
        logger.info("Creating YappcBM25Retriever (PostgreSQL FTS)");
        return new YappcBM25Retriever(dataSource, executor);
    }

    /**
     * Provides dense vector retriever backed by DataCloud vector store.
     *
     * <p>Implements semantic search using dense embeddings and vector similarity matching.
     * Framework ready for embedding service + DataCloud vector store integration.</p>
     *
     * @param executor blocking executor for vector store queries
     * @return Dense vector retriever implementing RetrievalPipeline
     */
    @Provides
    @Named("dense-vector")
    RetrievalPipeline denseVectorRetriever(Executor executor) {
        logger.info("Creating YappcDenseVectorRetriever (DataCloud vector store)");
        return new YappcDenseVectorRetriever(null, executor); // EntityRepository injected at runtime
    }

    /**
     * Provides a blocking executor for all knowledge subsystem IO operations.
     *
     * @return CachedThreadPool executor
     */
    @Provides
    Executor knowledgeExecutor() {
        logger.info("Creating knowledge subsystem executor");
        return Executors.newCachedThreadPool(runnable -> {
            Thread t = new Thread(runnable, "yappc-knowledge-" + System.nanoTime());
            t.setDaemon(false);
            return t;
        });
    }
}
