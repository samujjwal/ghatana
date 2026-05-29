/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.av;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Multimodal indexer for AV content.
 * 
 * P8.3: Connect multimodal/STT/vision outputs to Data Cloud search/catalog/context.
 * Indexes transcripts, frame embeddings, and detected objects for search and retrieval.
 * 
 * @doc.type interface
 * @doc.purpose Multimodal indexing for AV content search
 * @doc.layer product
 * @doc.pattern Service
 */
public interface AVMultimodalIndexer {

    /**
     * Indexes an AV asset for multimodal search.
     *
     * @param assetId the asset ID
     * @param tenantId the tenant ID
     * @param indexRequest the index request
     * @return a Promise that resolves to the index result
     */
    Promise<IndexResult> indexAsset(String assetId, String tenantId, IndexRequest indexRequest);

    /**
     * Searches AV content by text query.
     *
     * @param tenantId the tenant ID
     * @param query the search query
     * @param filters search filters
     * @return a Promise that resolves to search results
     */
    Promise<SearchResults> searchByText(String tenantId, String query, SearchFilters filters);

    /**
     * Searches AV content by visual similarity.
     *
     * @param tenantId the tenant ID
     * @param embedding the query embedding
     * @param filters search filters
     * @return a Promise that resolves to search results
     */
    Promise<SearchResults> searchByVisual(String tenantId, float[] embedding, SearchFilters filters);

    /**
     * Searches AV content by object detection.
     *
     * @param tenantId the tenant ID
     * @param objectLabel the object label to search for
     * @param filters search filters
     * @return a Promise that resolves to search results
     */
    Promise<SearchResults> searchByObject(String tenantId, String objectLabel, SearchFilters filters);

    /**
     * Deletes an asset from the multimodal index.
     *
     * @param assetId the asset ID
     * @param tenantId the tenant ID
     * @return a Promise that resolves when deletion is complete
     */
    Promise<Void> deleteFromIndex(String assetId, String tenantId);

    /**
     * Index request.
     *
     * @param transcript the transcript to index
     * @param frameEmbeddings frame embeddings
     * @param detectedObjects detected objects
     * @param sceneDescriptions scene descriptions
     * @param indexOptions indexing options
     */
    record IndexRequest(
            AVAsset.AVTranscript transcript,
            Map<Long, float[]> frameEmbeddings,
            List<AVIngestionService.ObjectDetectionResult.DetectedObject> detectedObjects,
            List<AVIngestionService.SceneDetectionResult.Scene> sceneDescriptions,
            IndexOptions indexOptions) {

        public IndexRequest(
                AVAsset.AVTranscript transcript,
                Map<Long, float[]> frameEmbeddings,
                List<AVIngestionService.ObjectDetectionResult.DetectedObject> detectedObjects,
                List<AVIngestionService.SceneDetectionResult.Scene> sceneDescriptions) {
            this(transcript, frameEmbeddings, detectedObjects, sceneDescriptions, new IndexOptions());
        }
    }

    /**
     * Index options.
     *
     * @param indexTranscript whether to index transcript
     * @param indexFrames whether to index frame embeddings
     * @param indexObjects whether to index detected objects
     * @param indexScenes whether to index scenes
     * @param embeddingModel embedding model to use
     */
    record IndexOptions(
            boolean indexTranscript,
            boolean indexFrames,
            boolean indexObjects,
            boolean indexScenes,
            String embeddingModel) {

        public IndexOptions() {
            this(true, true, true, true, "default");
        }
    }

    /**
     * Index result.
     *
     * @param success whether indexing succeeded
     * @param indexedFields fields that were indexed
     * @param indexId the index ID
     * @param error error message (if failed)
     */
    record IndexResult(
            boolean success,
            List<String> indexedFields,
            String indexId,
            String error) {

        public static IndexResult success(List<String> indexedFields, String indexId) {
            return new IndexResult(true, indexedFields, indexId, null);
        }

        public static IndexResult failed(String error) {
            return new IndexResult(false, List.of(), null, error);
        }
    }

    /**
     * Search filters.
     *
     * @param assetType asset type filter
     * @param format format filter
     * @param consented consent filter
     * @param dateRange date range filter
     * @param customFilters custom filters
     */
    record SearchFilters(
            AVAsset.AVAssetType assetType,
            AVAsset.AVAssetFormat format,
            Boolean consented,
            DateRange dateRange,
            Map<String, Object> customFilters) {

        public SearchFilters() {
            this(null, null, null, null, Map.of());
        }

        record DateRange(Instant start, Instant end) {}
    }

    /**
     * Search results.
     *
     * @param results the search results
     * @param total total number of results
     * @param query the query that was executed
     * @param searchTimeMs search time in milliseconds
     */
    record SearchResults(
            List<SearchResult> results,
            int total,
            String query,
            long searchTimeMs) {}

    /**
     * Individual search result.
     *
     * @param assetId the asset ID
     * @param assetType the asset type
     * @param title the asset title
     * @param score the relevance score
     * @param highlights matched text highlights
     * @param timestamp matched timestamp (for temporal search)
     * @param metadata result metadata
     */
    record SearchResult(
            String assetId,
            AVAsset.AVAssetType assetType,
            String title,
            double score,
            List<TextHighlight> highlights,
            Long timestamp,
            Map<String, Object> metadata) {

        record TextHighlight(
                String text,
                long startTimeMs,
                long endTimeMs,
                double score) {}
    }
}
