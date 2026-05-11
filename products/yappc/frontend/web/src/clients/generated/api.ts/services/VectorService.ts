/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { RagChatRequest } from '../models/RagChatRequest';
import type { RagRequest } from '../models/RagRequest';
import type { VectorIndexRequest } from '../models/VectorIndexRequest';
import type { VectorSearchRequest } from '../models/VectorSearchRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class VectorService {
    /**
     * Semantic vector search
     * @param requestBody
     * @returns any Search results
     * @throws ApiError
     */
    public static vectorSearch(
        requestBody: VectorSearchRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/vector/search',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Hybrid keyword + semantic vector search
     * @param requestBody
     * @returns any Hybrid search results
     * @throws ApiError
     */
    public static vectorSearchHybrid(
        requestBody: VectorSearchRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/vector/search/hybrid',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Find documents similar to a stored document
     * @param id
     * @returns any Similar documents
     * @throws ApiError
     */
    public static vectorSimilar(
        id: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/vector/similar/{id}',
            path: {
                'id': id,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Index a single document
     * @param requestBody
     * @returns any Document indexed
     * @throws ApiError
     */
    public static vectorIndex(
        requestBody: VectorIndexRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/vector/index',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Batch-index multiple documents
     * @param requestBody
     * @returns any Batch index result
     * @throws ApiError
     */
    public static vectorIndexBatch(
        requestBody: Array<VectorIndexRequest>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/vector/index/batch',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Remove a document from the vector index
     * @param id
     * @returns void
     * @throws ApiError
     */
    public static vectorIndexDelete(
        id: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/api/v1/vector/index/{id}',
            path: {
                'id': id,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Retrieval-augmented generation (single turn)
     * @param requestBody
     * @returns any RAG response
     * @throws ApiError
     */
    public static vectorRag(
        requestBody: RagRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/vector/rag',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Retrieval-augmented generation (multi-turn chat)
     * @param requestBody
     * @returns any RAG chat response
     * @throws ApiError
     */
    public static vectorRagChat(
        requestBody: RagChatRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/vector/rag/chat',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
}
