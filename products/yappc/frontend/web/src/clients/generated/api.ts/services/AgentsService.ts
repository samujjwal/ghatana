/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AgentExecuteRequest } from '../models/AgentExecuteRequest';
import type { AgentPredictRequest } from '../models/AgentPredictRequest';
import type { AgentSearchRequest } from '../models/AgentSearchRequest';
import type { CopilotChatRequest } from '../models/CopilotChatRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class AgentsService {
    /**
     * List all registered agents
     * @returns any Agent list
     * @throws ApiError
     */
    public static listAgents(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/agents',
        });
    }
    /**
     * Health summary for all agents
     * @returns any Aggregated health status
     * @throws ApiError
     */
    public static agentsHealth(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/agents/health',
        });
    }
    /**
     * List all declared agent capabilities
     * @returns any Capability descriptors
     * @throws ApiError
     */
    public static agentsCapabilities(): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/agents/capabilities',
        });
    }
    /**
     * Find agents matching a capability
     * @param capability
     * @returns any Matching agents
     * @throws ApiError
     */
    public static agentsByCapability(
        capability: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/agents/by-capability/{capability}',
            path: {
                'capability': capability,
            },
        });
    }
    /**
     * Natural-language copilot chat backed by agent routing
     * @param requestBody
     * @returns any Chat response
     * @throws ApiError
     */
    public static copilotChat(
        requestBody: CopilotChatRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/agents/copilot/chat',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Search agents by capability or metadata
     * @param requestBody
     * @returns any Agent search results
     * @throws ApiError
     */
    public static searchAgents(
        requestBody: AgentSearchRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/agents/search',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Run a prediction via the best-matching agent
     * @param requestBody
     * @returns any Prediction result
     * @throws ApiError
     */
    public static predictAgent(
        requestBody: AgentPredictRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/agents/predict',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
            },
        });
    }
    /**
     * Get agent descriptor by name
     * @param name
     * @returns any Agent descriptor
     * @throws ApiError
     */
    public static getAgent(
        name: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/agents/{name}',
            path: {
                'name': name,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Health status for a specific agent
     * @param name
     * @returns any Agent health
     * @throws ApiError
     */
    public static agentHealth(
        name: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/agents/{name}/health',
            path: {
                'name': name,
            },
            errors: {
                404: `Resource not found`,
            },
        });
    }
    /**
     * Execute a specific agent by name
     * @param name
     * @param requestBody
     * @returns any Execution result
     * @throws ApiError
     */
    public static executeAgent(
        name: string,
        requestBody: AgentExecuteRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/agents/{name}/execute',
            path: {
                'name': name,
            },
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                400: `Bad request — invalid or missing parameters`,
                404: `Resource not found`,
            },
        });
    }
}
