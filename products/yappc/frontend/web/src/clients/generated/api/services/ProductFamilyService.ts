/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ProductFamilyService {
    /**
     * Get product release readiness
     * @param productKey
     * @returns any Data Cloud-backed release readiness read model
     * @throws ApiError
     */
    public static getProductFamilyReleaseReadiness(
        productKey: string,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/product-family/releases/{productKey}',
            path: {
                'productKey': productKey,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
                503: `Data Cloud read model unavailable`,
            },
        });
    }
    /**
     * List reusable product-family assets
     * @param search
     * @param product
     * @param domain
     * @param type
     * @param maturity
     * @param reuseMode
     * @param compatibility
     * @returns any Product-family asset registry read model
     * @throws ApiError
     */
    public static listProductFamilyAssets(
        search?: string,
        product?: string,
        domain?: string,
        type?: string,
        maturity?: string,
        reuseMode?: string,
        compatibility?: string,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/product-family/assets',
            query: {
                'search': search,
                'product': product,
                'domain': domain,
                'type': type,
                'maturity': maturity,
                'reuseMode': reuseMode,
                'compatibility': compatibility,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
                503: `Data Cloud read model unavailable`,
            },
        });
    }
    /**
     * Promote a reusable product-family asset
     * @param assetId
     * @param requestBody
     * @returns any Asset promotion result
     * @throws ApiError
     */
    public static promoteProductFamilyAsset(
        assetId: string,
        requestBody: {
            targetState: 'hardened' | 'production' | 'shared-package' | 'plugin' | 'template' | 'schema';
            promotionTarget?: string;
            evidenceRefs?: Array<any>;
            reason?: string;
        },
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/yappc/product-family/assets/{assetId}/promotions',
            path: {
                'assetId': assetId,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                404: `Resource not found`,
                409: `Promotion transition is not allowed`,
                503: `Data Cloud read model unavailable`,
            },
        });
    }
    /**
     * List doc, registry, and code truth warnings
     * @returns any Doc-truth warning read model
     * @throws ApiError
     */
    public static listProductFamilyDocTruthWarnings(): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/product-family/doc-truth',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                503: `Data Cloud read model unavailable`,
            },
        });
    }
    /**
     * List guided reusable-asset recommendations
     * @param targetProduct
     * @returns any Guided reuse recommendations for a target product
     * @throws ApiError
     */
    public static listProductFamilyReuseRecommendations(
        targetProduct: string,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/product-family/reuse-recommendations/{targetProduct}',
            path: {
                'targetProduct': targetProduct,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
                503: `Data Cloud read model unavailable`,
            },
        });
    }
    /**
     * Get Kernel lifecycle timeline and rollback visibility
     * @param productUnitId
     * @returns any Kernel public-event lifecycle timeline read model
     * @throws ApiError
     */
    public static getProductFamilyKernelTimeline(
        productUnitId: string,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/yappc/product-family/kernel-timeline/{productUnitId}',
            path: {
                'productUnitId': productUnitId,
            },
            errors: {
                400: `Bad request — invalid or missing parameters`,
                503: `Data Cloud read model unavailable`,
            },
        });
    }
}
