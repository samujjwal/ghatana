/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ProductFamilyService {
    /**
     * Get product-family release readiness
     * @param productKey
     * @returns any Product-family release readiness
     * @throws ApiError
     */
    public static getProductFamilyReleaseReadiness(
        productKey: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/product-family/releases/{productKey}',
            path: {
                'productKey': productKey,
            },
        });
    }
    /**
     * List product-family assets
     * @returns any Product-family assets
     * @throws ApiError
     */
    public static listProductFamilyAssets(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/product-family/assets',
        });
    }
    /**
     * Promote product-family asset
     * @param assetId
     * @param requestBody
     * @returns any Product-family asset promotion recorded
     * @throws ApiError
     */
    public static promoteProductFamilyAsset(
        assetId: string,
        requestBody: Record<string, any>,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/product-family/assets/{assetId}/promotions',
            path: {
                'assetId': assetId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request â€” invalid or missing parameters`,
            },
        });
    }
    /**
     * List product-family documentation truth warnings
     * @returns any Product-family documentation truth warnings
     * @throws ApiError
     */
    public static listProductFamilyDocTruthWarnings(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/product-family/doc-truth',
        });
    }
    /**
     * List product-family reuse recommendations
     * @param targetProduct
     * @returns any Product-family reuse recommendations
     * @throws ApiError
     */
    public static listProductFamilyReuseRecommendations(
        targetProduct: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/product-family/reuse-recommendations/{targetProduct}',
            path: {
                'targetProduct': targetProduct,
            },
        });
    }
    /**
     * Get product-family Kernel timeline
     * @param productUnitId
     * @returns any Product-family Kernel timeline
     * @throws ApiError
     */
    public static getProductFamilyKernelTimeline(
        productUnitId: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/product-family/kernel-timeline/{productUnitId}',
            path: {
                'productUnitId': productUnitId,
            },
        });
    }
}

