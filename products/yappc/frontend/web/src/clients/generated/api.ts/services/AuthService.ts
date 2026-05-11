/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CapabilityRequest } from '../models/CapabilityRequest';
import type { CapabilityResponse } from '../models/CapabilityResponse';
import type { LoginRequest } from '../models/LoginRequest';
import type { LoginResponse } from '../models/LoginResponse';
import type { LogoutResponse } from '../models/LogoutResponse';
import type { RefreshTokenRequest } from '../models/RefreshTokenRequest';
import type { RefreshTokenResponse } from '../models/RefreshTokenResponse';
import type { TokenValidationResult } from '../models/TokenValidationResult';
import type { UserInfo } from '../models/UserInfo';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class AuthService {
    /**
     * Authenticate user and issue JWT
     * @param requestBody
     * @returns LoginResponse Authentication successful
     * @throws ApiError
     */
    public static login(
        requestBody: LoginRequest,
    ): CancelablePromise<LoginResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/login',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Exchange a refresh token for a new access token pair
     * @param requestBody
     * @returns RefreshTokenResponse Token refresh successful
     * @throws ApiError
     */
    public static refreshAuthToken(
        requestBody: RefreshTokenRequest,
    ): CancelablePromise<RefreshTokenResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/refresh',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Invalidate the current session
     * @param requestBody
     * @returns LogoutResponse Session invalidated
     * @throws ApiError
     */
    public static logout(
        requestBody: RefreshTokenRequest,
    ): CancelablePromise<LogoutResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/logout',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Validate the current JWT
     * @returns TokenValidationResult Token is valid
     * @throws ApiError
     */
    public static validateToken(): CancelablePromise<TokenValidationResult> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/auth/validate',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Return information about the authenticated user
     * @returns UserInfo Current user info
     * @throws ApiError
     */
    public static currentUser(): CancelablePromise<UserInfo> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/auth/me',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Get user capabilities
     * Returns the capabilities available to the current user based on their role and context
     * @param workspaceId Workspace ID to scope capabilities
     * @param projectId Project ID to scope capabilities
     * @returns CapabilityResponse User capabilities
     * @throws ApiError
     */
    public static getCapabilities(
        workspaceId?: string,
        projectId?: string,
    ): CancelablePromise<CapabilityResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/capabilities',
            query: {
                'workspaceId': workspaceId,
                'projectId': projectId,
            },
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Query user capabilities
     * Query capabilities for a specific context with optional correlation ID
     * @param requestBody
     * @returns CapabilityResponse User capabilities
     * @throws ApiError
     */
    public static queryCapabilities(
        requestBody: CapabilityRequest,
    ): CancelablePromise<CapabilityResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/capabilities',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
}
