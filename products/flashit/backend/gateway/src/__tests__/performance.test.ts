/**
 * Performance tests for Flashit Web API
 * Tests response times, throughput, and resource usage under load
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { buildServer } from "../server";
import { prisma } from "../lib/prisma";

describe("Performance Tests", () => {
    const app = buildServer();

    let testUser: any;
    let authToken: string;
    let testSphere: any;
    let testMoments: any[] = [];

    beforeAll(async () => {
        await app.ready();
    });

    afterAll(async () => {
        await app.close();
    });

    beforeEach(async () => {
        // Create test user
        const userResponse = await app.inject({
            method: "POST",
            url: "/auth/register",
            payload: {
                email: `perf-test-${Date.now()}@example.com`,
                password: "TestPassword123!",
                displayName: "Performance Test User",
            },
        });

        const userBody = JSON.parse(userResponse.body);
        testUser = userBody.user;
        authToken = userBody.token;

        // Create test sphere
        const sphereResponse = await app.inject({
            method: "POST",
            url: "/api/spheres",
            headers: { authorization: `Bearer ${authToken}` },
            payload: {
                name: "Performance Test Sphere",
                description: "Sphere for performance testing",
                type: "PERSONAL",
                visibility: "PRIVATE",
            },
        });

        testSphere = JSON.parse(sphereResponse.body).sphere;

        // Create test moments for search performance
        for (let i = 0; i < 10; i++) {
            const momentResponse = await app.inject({
                method: "POST",
                url: "/api/moments",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    sphereId: testSphere.id,
                    content: {
                        text: `Performance test moment ${i} with searchable content about work meetings and project updates`,
                        type: "TEXT",
                    },
                    signals: {
                        emotions: ["focused", "productive"],
                        tags: ["work", "performance", `test-${i}`],
                        intent: "capture",
                    },
                },
            });

            testMoments.push(JSON.parse(momentResponse.body).moment);
        }
    });

    describe("API Response Times", () => {
        it("should respond to health checks within 50ms", async () => {
            const startTime = Date.now();

            const response = await app.inject({
                method: "GET",
                url: "/health",
            });

            const endTime = Date.now();
            const responseTime = endTime - startTime;

            expect(response.statusCode).toBe(200);
            expect(responseTime).toBeLessThan(50);
        });

        it("should handle authentication within 200ms", async () => {
            const startTime = Date.now();

            const response = await app.inject({
                method: "POST",
                url: "/auth/login",
                payload: {
                    email: testUser.email,
                    password: "TestPassword123!",
                },
            });

            const endTime = Date.now();
            const responseTime = endTime - startTime;

            expect(response.statusCode).toBe(200);
            expect(responseTime).toBeLessThan(200);
        });

        it("should create moments within 300ms", async () => {
            const startTime = Date.now();

            const response = await app.inject({
                method: "POST",
                url: "/api/moments",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    content: "Performance test moment creation",
                    sphereId: testSphere.id,
                    emotions: ["happy"],
                    tags: ["performance", "test"],
                    importance: 3,
                },
            });

            const endTime = Date.now();
            const responseTime = endTime - startTime;

            expect(response.statusCode).toBe(201);
            expect(responseTime).toBeLessThan(300);
        });

        it("should search moments within 500ms", async () => {
            const startTime = Date.now();

            const response = await app.inject({
                method: "GET",
                url: "/api/moments?search=work&limit=10",
                headers: { authorization: `Bearer ${authToken}` },
            });

            const endTime = Date.now();
            const responseTime = endTime - startTime;

            expect(response.statusCode).toBe(200);
            expect(responseTime).toBeLessThan(500);

            const results = JSON.parse(response.body);
            expect(results.moments).toBeDefined();
            expect(Array.isArray(results.moments)).toBe(true);
        });

        it("should list spheres within 200ms", async () => {
            const startTime = Date.now();

            const response = await app.inject({
                method: "GET",
                url: "/api/spheres",
                headers: { authorization: `Bearer ${authToken}` },
            });

            const endTime = Date.now();
            const responseTime = endTime - startTime;

            expect(response.statusCode).toBe(200);
            expect(responseTime).toBeLessThan(200);

            const spheres = JSON.parse(response.body);
            expect(Array.isArray(spheres)).toBe(true);
        });
    });

    describe("Concurrent Request Handling", () => {
        it("should handle 10 concurrent moment creations", async () => {
            const promises = [];
            const startTime = Date.now();

            // Create 10 concurrent requests
            for (let i = 0; i < 10; i++) {
                promises.push(
                    app.inject({
                        method: "POST",
                        url: "/api/moments",
                        headers: { authorization: `Bearer ${authToken}` },
                        payload: {
                            content: `Concurrent test moment ${i}`,
                            sphereId: testSphere.id,
                            emotions: ["focused"],
                            tags: ["concurrent", `test-${i}`],
                            importance: 3,
                        },
                    })
                );
            }

            const results = await Promise.all(promises);
            const endTime = Date.now();
            const totalTime = endTime - startTime;

            // All requests should succeed
            expect(results.length).toBe(10);
            results.forEach((response, index) => {
                expect(response.statusCode).toBe(201);
                const moment = JSON.parse(response.body);
                expect(moment.content).toContain(`Concurrent test moment`);
            });

            // Should complete within reasonable time (average < 500ms per request)
            expect(totalTime).toBeLessThan(5000); // 10 requests * 500ms each
        });

        it("should handle 20 concurrent search requests", async () => {
            const promises = [];
            const startTime = Date.now();

            // Create 20 concurrent search requests
            for (let i = 0; i < 20; i++) {
                promises.push(
                    app.inject({
                        method: "GET",
                        url: "/api/moments?search=test&limit=5",
                        headers: { authorization: `Bearer ${authToken}` },
                    })
                );
            }

            const results = await Promise.all(promises);
            const endTime = Date.now();
            const totalTime = endTime - startTime;

            // All requests should succeed
            expect(results.length).toBe(20);
            results.forEach((response) => {
                expect(response.statusCode).toBe(200);
                const data = JSON.parse(response.body);
                expect(data.moments).toBeDefined();
            });

            // Should complete within reasonable time
            expect(totalTime).toBeLessThan(3000); // 20 requests * 150ms each
        });

        it("should handle mixed concurrent operations", async () => {
            const promises = [];
            const startTime = Date.now();

            // Mix of different operations
            for (let i = 0; i < 5; i++) {
                // Create moments
                promises.push(
                    app.inject({
                        method: "POST",
                        url: "/api/moments",
                        headers: { authorization: `Bearer ${authToken}` },
                        payload: {
                            content: `Mixed test moment ${i}`,
                            sphereId: testSphere.id,
                            emotions: ["testing"],
                            tags: ["mixed", `test-${i}`],
                            importance: 3,
                        },
                    })
                );

                // Search moments
                promises.push(
                    app.inject({
                        method: "GET",
                        url: "/api/moments?search=mixed&limit=5",
                        headers: { authorization: `Bearer ${authToken}` },
                    })
                );

                // List spheres
                promises.push(
                    app.inject({
                        method: "GET",
                        url: "/api/spheres",
                        headers: { authorization: `Bearer ${authToken}` },
                    })
                );
            }

            const results = await Promise.all(promises);
            const endTime = Date.now();
            const totalTime = endTime - startTime;

            // All requests should succeed
            expect(results.length).toBe(15);
            results.forEach((response) => {
                expect([200, 201]).toContain(response.statusCode);
            });

            // Should complete within reasonable time
            expect(totalTime).toBeLessThan(3000);
        });
    });

    describe("Database Query Performance", () => {
        it("should handle large moment datasets efficiently", async () => {
            // Create additional moments for performance testing
            const additionalMoments = [];
            for (let i = 0; i < 100; i++) {
                const response = await app.inject({
                    method: "POST",
                    url: "/api/moments",
                    headers: { authorization: `Bearer ${authToken}` },
                    payload: {
                        content: `Large dataset test moment ${i} with detailed content for performance testing`,
                        sphereId: testSphere.id,
                        emotions: ["neutral"],
                        tags: ["performance", "large", `dataset-${i}`],
                        importance: Math.floor(Math.random() * 5) + 1,
                    },
                });
                additionalMoments.push(JSON.parse(response.body));
            }

            // Test pagination performance
            const startTime = Date.now();

            const response = await app.inject({
                method: "GET",
                url: "/api/moments?page=1&limit=50",
                headers: { authorization: `Bearer ${authToken}` },
            });

            const endTime = Date.now();
            const responseTime = endTime - startTime;

            expect(response.statusCode).toBe(200);
            expect(responseTime).toBeLessThan(300); // Should be fast even with 110+ moments

            const data = JSON.parse(response.body);
            expect(data.moments).toHaveLength(50);
            expect(data.total).toBeGreaterThanOrEqual(110);

            // Cleanup additional moments
            for (const moment of additionalMoments) {
                await prisma.moment.delete({
                    where: { id: moment.id },
                });
            }
        });

        it("should handle complex search queries efficiently", async () => {
            const startTime = Date.now();

            const response = await app.inject({
                method: "GET",
                url: "/api/moments?search=performance&tags=test&importance_min=3&limit=20",
                headers: { authorization: `Bearer ${authToken}` },
            });

            const endTime = Date.now();
            const responseTime = endTime - startTime;

            expect(response.statusCode).toBe(200);
            expect(responseTime).toBeLessThan(400); // Complex queries should still be fast

            const data = JSON.parse(response.body);
            expect(data.moments).toBeDefined();
            expect(Array.isArray(data.moments)).toBe(true);
        });

        it("should handle tag-based filtering efficiently", async () => {
            const startTime = Date.now();

            const response = await app.inject({
                method: "GET",
                url: "/api/moments?tags=work,performance&limit=10",
                headers: { authorization: `Bearer ${authToken}` },
            });

            const endTime = Date.now();
            const responseTime = endTime - startTime;

            expect(response.statusCode).toBe(200);
            expect(responseTime).toBeLessThan(200);

            const data = JSON.parse(response.body);
            expect(data.moments).toBeDefined();
        });
    });

    describe("Memory and Resource Usage", () => {
        it("should not leak memory during repeated operations", async () => {
            const initialMemory = process.memoryUsage();

            // Perform many operations
            for (let i = 0; i < 50; i++) {
                await app.inject({
                    method: "POST",
                    url: "/api/moments",
                    headers: { authorization: `Bearer ${authToken}` },
                    payload: {
                        content: `Memory test moment ${i}`,
                        sphereId: testSphere.id,
                        emotions: ["testing"],
                        tags: ["memory", `test-${i}`],
                        importance: 3,
                    },
                });

                await app.inject({
                    method: "GET",
                    url: "/api/moments?search=memory&limit=10",
                    headers: { authorization: `Bearer ${authToken}` },
                });
            }

            // Force garbage collection if available
            if (global.gc) {
                global.gc();
            }

            const finalMemory = process.memoryUsage();
            const memoryIncrease = finalMemory.heapUsed - initialMemory.heapUsed;

            // Memory increase should be reasonable (less than 50MB)
            expect(memoryIncrease).toBeLessThan(50 * 1024 * 1024);
        });

        it("should handle large request payloads efficiently", async () => {
            // Create moment with large content
            const largeContent = "Large content test ".repeat(1000); // ~20KB

            const startTime = Date.now();

            const response = await app.inject({
                method: "POST",
                url: "/api/moments",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    content: largeContent,
                    sphereId: testSphere.id,
                    emotions: ["testing"],
                    tags: ["large", "content"],
                    importance: 3,
                    metadata: {
                        largeField: "x".repeat(10000), // 10KB metadata
                    },
                },
            });

            const endTime = Date.now();
            const responseTime = endTime - startTime;

            expect(response.statusCode).toBe(201);
            expect(responseTime).toBeLessThan(500); // Should handle large payloads efficiently

            const moment = JSON.parse(response.body);
            expect(moment.content).toBe(largeContent);
        });
    });

    describe("Rate Limiting Performance", () => {
        it("should handle rate limiting efficiently", async () => {
            const promises = [];

            // Send requests rapidly to test rate limiting
            for (let i = 0; i < 600; i++) { // 600 requests should trigger rate limiting
                promises.push(
                    app.inject({
                        method: "GET",
                        url: "/api/moments?limit=1",
                        headers: { authorization: `Bearer ${authToken}` },
                    })
                );
            }

            const startTime = Date.now();
            const results = await Promise.allSettled(promises);
            const endTime = Date.now();
            const totalTime = endTime - startTime;

            // Count successful vs rate-limited requests
            const successful = results.filter(r =>
                r.status === 'fulfilled' && r.value.statusCode === 200
            ).length;
            const rateLimited = results.filter(r =>
                r.status === 'fulfilled' && r.value.statusCode === 429
            ).length;

            // Should have some successful requests and some rate-limited
            expect(successful).toBeGreaterThan(0);
            expect(rateLimited).toBeGreaterThan(0);

            // Should complete in reasonable time despite rate limiting
            expect(totalTime).toBeLessThan(10000);
        });
    });

    describe("Stress Testing", () => {
        it("should handle high load without degradation", async () => {
            const promises = [];
            const responseTimes = [];

            // Create high load
            for (let i = 0; i < 100; i++) {
                const startTime = Date.now();

                const promise = app.inject({
                    method: "GET",
                    url: "/api/moments?limit=10",
                    headers: { authorization: `Bearer ${authToken}` },
                }).then(response => {
                    const endTime = Date.now();
                    responseTimes.push(endTime - startTime);
                    return response;
                });

                promises.push(promise);
            }

            const results = await Promise.allSettled(promises);

            // Analyze results
            const successful = results.filter(r =>
                r.status === 'fulfilled' && r.value.statusCode === 200
            ).length;

            const avgResponseTime = responseTimes.reduce((a, b) => a + b, 0) / responseTimes.length;
            const maxResponseTime = Math.max(...responseTimes);

            // Should maintain good performance under load
            expect(successful).toBeGreaterThan(90); // At least 90% success rate
            expect(avgResponseTime).toBeLessThan(1000); // Average < 1s
            expect(maxResponseTime).toBeLessThan(5000); // Max < 5s
        });
    });
});
