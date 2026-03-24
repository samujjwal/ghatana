/**
 * OpenAPIService Tests
 * 
 * Tests for OpenAPI 3.0 specification generation
 * 
 * @doc.type test
 * @doc.purpose OpenAPIService tests
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect } from 'vitest';
import { OpenAPIService } from '../OpenAPIService';
import type { Node } from '@xyflow/react';
import type { APINodeData } from '../OpenAPIService';

describe('OpenAPIService', () => {
    describe('generateSpec', () => {
        it('should generate basic OpenAPI spec', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        label: 'Get Users',
                        apiPath: '/users',
                        method: 'GET',
                        authentication: 'none',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes);

            expect(spec.openapi).toBe('3.0.3');
            expect(spec.info.title).toBe('Generated API');
            expect(spec.paths['/users']).toBeDefined();
            expect(spec.paths['/users'].get).toBeDefined();
        });

        it('should handle multiple endpoints', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/users',
                        method: 'GET',
                    },
                },
                {
                    id: '2',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/users',
                        method: 'POST',
                    },
                },
                {
                    id: '3',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/users/{id}',
                        method: 'GET',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes);

            expect(Object.keys(spec.paths)).toHaveLength(2);
            expect(spec.paths['/users'].get).toBeDefined();
            expect(spec.paths['/users'].post).toBeDefined();
            expect(spec.paths['/users/{id}'].get).toBeDefined();
        });

        it('should infer path parameters', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/users/{userId}/posts/{postId}',
                        method: 'GET',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes);
            const endpoint = spec.paths['/users/{userId}/posts/{postId}'].get as unknown;

            expect(endpoint.parameters).toBeDefined();
            expect(endpoint.parameters).toHaveLength(2);
            expect(endpoint.parameters[0].name).toBe('userId');
            expect(endpoint.parameters[0].in).toBe('path');
            expect(endpoint.parameters[0].required).toBe(true);
            expect(endpoint.parameters[1].name).toBe('postId');
        });

        it('should add request body for POST/PUT/PATCH', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/users',
                        method: 'POST',
                        requestSchema: '{"type":"object","properties":{"name":{"type":"string"}}}',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes);
            const endpoint = spec.paths['/users'].post as unknown;

            expect(endpoint.requestBody).toBeDefined();
            expect(endpoint.requestBody.content['application/json']).toBeDefined();
            expect(endpoint.requestBody.content['application/json'].schema.type).toBe('object');
        });

        it('should add security schemes for authenticated endpoints', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/users',
                        method: 'GET',
                        authentication: 'jwt',
                    },
                },
                {
                    id: '2',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/admin',
                        method: 'GET',
                        authentication: 'apiKey',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes, { includeSchemas: true });

            expect(spec.components?.securitySchemes).toBeDefined();
            expect(spec.components?.securitySchemes?.jwt).toBeDefined();
            expect(spec.components?.securitySchemes?.apiKey).toBeDefined();
            expect((spec.components?.securitySchemes?.jwt as unknown).type).toBe('http');
            expect((spec.components?.securitySchemes?.apiKey as unknown).type).toBe('apiKey');
        });

        it('should apply custom options', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/test',
                        method: 'GET',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes, {
                title: 'Custom API',
                version: '2.0.0',
                description: 'Custom description',
                serverUrl: 'https://api.example.com',
            });

            expect(spec.info.title).toBe('Custom API');
            expect(spec.info.version).toBe('2.0.0');
            expect(spec.info.description).toBe('Custom description');
            expect(spec.servers?.[0].url).toBe('https://api.example.com');
        });

        it('should include examples when requested', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/users',
                        method: 'POST',
                        requestSchema: '{"type":"object","properties":{"name":{"type":"string"}}}',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes, { includeExamples: true });
            const endpoint = spec.paths['/users'].post as unknown;

            expect(endpoint.requestBody.content['application/json'].schema.example).toBeDefined();
        });

        it('should skip nodes without path or method', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/users',
                        method: 'GET',
                    },
                },
                {
                    id: '2',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        // Missing method
                        apiPath: '/incomplete',
                    },
                },
                {
                    id: '3',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        // Missing path
                        method: 'GET',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes);

            expect(Object.keys(spec.paths)).toHaveLength(1);
            expect(spec.paths['/users']).toBeDefined();
        });
    });

    describe('toYAML', () => {
        it('should convert spec to YAML', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/test',
                        method: 'GET',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes);
            const yaml = OpenAPIService.toYAML(spec);

            expect(yaml).toContain('openapi: 3.0.3');
            expect(yaml).toContain('/test:');
            expect(yaml).toContain('get:');
        });

        it('should produce valid YAML format', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/users',
                        method: 'GET',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes);
            const yaml = OpenAPIService.toYAML(spec);

            expect(yaml).not.toContain('undefined');
            expect(yaml).not.toContain('null');
            expect(yaml.split('\n').length).toBeGreaterThan(10);
        });
    });

    describe('toJSON', () => {
        it('should convert spec to JSON', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/test',
                        method: 'GET',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes);
            const json = OpenAPIService.toJSON(spec);

            expect(() => JSON.parse(json)).not.toThrow();
            const parsed = JSON.parse(json);
            expect(parsed.openapi).toBe('3.0.3');
            expect(parsed.paths['/test'].get).toBeDefined();
        });

        it('should support pretty and compact formatting', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/test',
                        method: 'GET',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes);
            const pretty = OpenAPIService.toJSON(spec, true);
            const compact = OpenAPIService.toJSON(spec, false);

            expect(pretty.length).toBeGreaterThan(compact.length);
            expect(pretty).toContain('\n');
            expect(compact).not.toContain('\n');
        });
    });

    describe('validate', () => {
        it('should validate correct spec', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/users',
                        method: 'GET',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes);
            const result = OpenAPIService.validate(spec);

            expect(result.valid).toBe(true);
            expect(result.errors).toHaveLength(0);
        });

        it('should detect missing openapi version', () => {
            const spec = {
                info: { title: 'Test', version: '1.0.0' },
                paths: {},
            } as unknown;

            const result = OpenAPIService.validate(spec);

            expect(result.valid).toBe(false);
            expect(result.errors).toContain('Missing openapi version');
        });

        it('should detect missing info.title', () => {
            const spec = {
                openapi: '3.0.3',
                info: { version: '1.0.0' },
                paths: {},
            } as unknown;

            const result = OpenAPIService.validate(spec);

            expect(result.valid).toBe(false);
            expect(result.errors).toContain('Missing info.title');
        });

        it('should detect missing info.version', () => {
            const spec = {
                openapi: '3.0.3',
                info: { title: 'Test' },
                paths: {},
            } as unknown;

            const result = OpenAPIService.validate(spec);

            expect(result.valid).toBe(false);
            expect(result.errors).toContain('Missing info.version');
        });

        it('should detect missing paths', () => {
            const spec = {
                openapi: '3.0.3',
                info: { title: 'Test', version: '1.0.0' },
                paths: {},
            } as unknown;

            const result = OpenAPIService.validate(spec);

            expect(result.valid).toBe(false);
            expect(result.errors).toContain('No paths defined');
        });

        it('should detect invalid path format', () => {
            const spec = {
                openapi: '3.0.3',
                info: { title: 'Test', version: '1.0.0' },
                paths: {
                    'users': { get: {} }, // Missing leading slash
                },
            } as unknown;

            const result = OpenAPIService.validate(spec);

            expect(result.valid).toBe(false);
            expect(result.errors.some((e) => e.includes('Path must start with /'))).toBe(true);
        });

        it('should detect invalid HTTP method', () => {
            const spec = {
                openapi: '3.0.3',
                info: { title: 'Test', version: '1.0.0' },
                paths: {
                    '/users': { invalid: {} },
                },
            } as unknown;

            const result = OpenAPIService.validate(spec);

            expect(result.valid).toBe(false);
            expect(result.errors.some((e) => e.includes('Invalid HTTP method'))).toBe(true);
        });
    });

    describe('example generation', () => {
        it('should generate examples for different types', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/test',
                        method: 'POST',
                        requestSchema: JSON.stringify({
                            type: 'object',
                            properties: {
                                name: { type: 'string' },
                                age: { type: 'integer' },
                                active: { type: 'boolean' },
                                tags: { type: 'array', items: { type: 'string' } },
                            },
                        }),
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes, { includeExamples: true });
            const endpoint = spec.paths['/test'].post as unknown;
            const example = endpoint.requestBody.content['application/json'].schema.example;

            expect(example.name).toBe('string');
            expect(example.age).toBe(42);
            expect(example.active).toBe(true);
            expect(Array.isArray(example.tags)).toBe(true);
        });
    });

    describe('security schemes', () => {
        it('should generate JWT security scheme', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/secure',
                        method: 'GET',
                        authentication: 'jwt',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes, { includeSchemas: true });
            const jwtScheme = spec.components?.securitySchemes?.jwt as unknown;

            expect(jwtScheme.type).toBe('http');
            expect(jwtScheme.scheme).toBe('bearer');
            expect(jwtScheme.bearerFormat).toBe('JWT');
        });

        it('should generate API key security scheme', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/secure',
                        method: 'GET',
                        authentication: 'apiKey',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes, { includeSchemas: true });
            const apiKeyScheme = spec.components?.securitySchemes?.apiKey as unknown;

            expect(apiKeyScheme.type).toBe('apiKey');
            expect(apiKeyScheme.in).toBe('header');
            expect(apiKeyScheme.name).toBe('X-API-Key');
        });

        it('should generate OAuth2 security scheme', () => {
            const nodes: Node<APINodeData>[] = [
                {
                    id: '1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {
                        apiPath: '/secure',
                        method: 'GET',
                        authentication: 'oauth2',
                    },
                },
            ];

            const spec = OpenAPIService.generateSpec(nodes, { includeSchemas: true });
            const oauth2Scheme = spec.components?.securitySchemes?.oauth2 as unknown;

            expect(oauth2Scheme.type).toBe('oauth2');
            expect(oauth2Scheme.flows).toBeDefined();
            expect(oauth2Scheme.flows.authorizationCode).toBeDefined();
        });
    });
});
