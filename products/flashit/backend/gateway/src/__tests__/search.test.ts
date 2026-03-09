/**
 * Integration tests for Search API routes
 * Tests full-text search, filtering, pagination, and semantic search
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { buildServer } from "../server";
import { prisma } from "../lib/prisma";

describe("Search API Integration Tests", () => {
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
                email: `search-test-${Date.now()}@example.com`,
                password: "TestPassword123!",
                displayName: "Search Test User",
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
                name: "Search Test Sphere",
                description: "Sphere for testing search functionality",
                type: "PERSONAL",
                visibility: "PRIVATE",
            },
        });

        testSphere = JSON.parse(sphereResponse.body).sphere;

        // Create test moments with varied content
        const momentsData = [
            {
                content: "Important meeting with the development team about the new project timeline and deliverables",
                emotions: ["focused", "productive"],
                tags: ["work", "meeting", "project", "development"],
                importance: 5,
            },
            {
                content: "Quick coffee break discussion about weekend plans and travel ideas",
                emotions: ["relaxed", "happy"],
                tags: ["personal", "coffee", "weekend", "travel"],
                importance: 2,
            },
            {
                content: "Client presentation preparation for the quarterly business review and financial results",
                emotions: ["anxious", "focused"],
                tags: ["work", "client", "presentation", "quarterly"],
                importance: 4,
            },
            {
                content: "Team building activity with games and lunch at the office park",
                emotions: ["happy", "social"],
                tags: ["work", "team", "building", "social"],
                importance: 3,
            },
            {
                content: "Technical deep dive into the new architecture patterns and microservices design",
                emotions: ["excited", "curious"],
                tags: ["technical", "architecture", "microservices", "design"],
                importance: 4,
            },
            {
                content: "Phone call with family about holiday plans and gift shopping list",
                emotions: ["warm", "thoughtful"],
                tags: ["family", "holiday", "personal", "shopping"],
                importance: 3,
            },
            {
                content: "Code review session discussing performance optimization and security improvements",
                emotions: ["analytical", "collaborative"],
                tags: ["work", "code", "review", "performance", "security"],
                importance: 4,
            },
            {
                content: "Evening workout session at the gym with cardio and strength training",
                emotions: ["energetic", "determined"],
                tags: ["health", "workout", "gym", "exercise"],
                importance: 3,
            },
            {
                content: "Strategic planning meeting for next quarter's goals and objectives",
                emotions: ["strategic", "ambitious"],
                tags: ["work", "planning", "strategy", "goals"],
                importance: 5,
            },
            {
                content: "Reading technical articles about machine learning and artificial intelligence trends",
                emotions: ["curious", "inspired"],
                tags: ["learning", "technical", "ml", "ai", "reading"],
                importance: 2,
            },
        ];

        for (const momentData of momentsData) {
            const response = await app.inject({
                method: "POST",
                url: "/api/moments",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    sphereId: testSphere.id,
                    content: {
                        text: momentData.content,
                        type: "TEXT",
                    },
                    signals: {
                        emotions: momentData.emotions,
                        tags: momentData.tags,
                        intent: "capture",
                    },
                },
            });

            testMoments.push(JSON.parse(response.body).moment);
        }
    });

    describe("Basic Search Functionality", () => {
        it("should search moments by text content", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?search=meeting",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data).toHaveProperty('moments');
            expect(data).toHaveProperty('total');
            expect(data).toHaveProperty('page');
            expect(data).toHaveProperty('limit');
            expect(Array.isArray(data.moments)).toBe(true);

            // Should find moments containing "meeting"
            expect(data.total).toBeGreaterThan(0);
            data.moments.forEach((moment: any) => {
                expect(moment.content.toLowerCase()).toContain('meeting');
            });
        });

        it("should search with case insensitivity", async () => {
            const response1 = await app.inject({
                method: "GET",
                url: "/api/moments?search=WORK",
                headers: { authorization: `Bearer ${authToken}` },
            });

            const response2 = await app.inject({
                method: "GET",
                url: "/api/moments?search=work",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response1.statusCode).toBe(200);
            expect(response2.statusCode).toBe(200);

            const data1 = JSON.parse(response1.body);
            const data2 = JSON.parse(response2.body);

            // Should return same results regardless of case
            expect(data1.total).toBe(data2.total);
        });

        it("should handle empty search results gracefully", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?search=nonexistentterm",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data.moments).toHaveLength(0);
            expect(data.total).toBe(0);
        });

        it("should search partial words and phrases", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?search=develop",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            // Should find moments containing "development" or "develop"
            expect(data.total).toBeGreaterThan(0);
            data.moments.forEach((moment: any) => {
                expect(moment.content.toLowerCase()).toContain('develop');
            });
        });
    });

    describe("Tag-based Filtering", () => {
        it("should filter moments by single tag", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?tags=work",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data.total).toBeGreaterThan(0);
            data.moments.forEach((moment: any) => {
                expect(moment.tags).toContain('work');
            });
        });

        it("should filter moments by multiple tags (AND logic)", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?tags=work,meeting",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            // Should find moments with both "work" AND "meeting" tags
            data.moments.forEach((moment: any) => {
                expect(moment.tags).toContain('work');
                expect(moment.tags).toContain('meeting');
            });
        });

        it("should handle non-existent tag filters", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?tags=nonexistenttag",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data.moments).toHaveLength(0);
            expect(data.total).toBe(0);
        });
    });

    describe("Emotion-based Filtering", () => {
        it("should filter moments by single emotion", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?emotions=happy",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data.total).toBeGreaterThan(0);
            data.moments.forEach((moment: any) => {
                expect(moment.emotions).toContain('happy');
            });
        });

        it("should filter moments by multiple emotions", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?emotions=focused,productive",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            // Should find moments with both emotions
            data.moments.forEach((moment: any) => {
                expect(moment.emotions).toContain('focused');
                expect(moment.emotions).toContain('productive');
            });
        });
    });

    describe("Importance Filtering", () => {
        it("should filter moments by minimum importance", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?importance_min=4",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data.total).toBeGreaterThan(0);
            data.moments.forEach((moment: any) => {
                expect(moment.importance).toBeGreaterThanOrEqual(4);
            });
        });

        it("should filter moments by maximum importance", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?importance_max=2",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            data.moments.forEach((moment: any) => {
                expect(moment.importance).toBeLessThanOrEqual(2);
            });
        });

        it("should filter moments by importance range", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?importance_min=3&importance_max=4",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            data.moments.forEach((moment: any) => {
                expect(moment.importance).toBeGreaterThanOrEqual(3);
                expect(moment.importance).toBeLessThanOrEqual(4);
            });
        });
    });

    describe("Date Range Filtering", () => {
        it("should filter moments by start date", async () => {
            const today = new Date();
            const yesterday = new Date(today);
            yesterday.setDate(yesterday.getDate() - 1);

            const response = await app.inject({
                method: "GET",
                url: `/api/moments?start_date=${yesterday.toISOString()}`,
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            data.moments.forEach((moment: any) => {
                const momentDate = new Date(moment.createdAt);
                expect(momentDate.getTime()).toBeGreaterThanOrEqual(yesterday.getTime());
            });
        });

        it("should filter moments by end date", async () => {
            const today = new Date();
            const tomorrow = new Date(today);
            tomorrow.setDate(tomorrow.getDate() + 1);

            const response = await app.inject({
                method: "GET",
                url: `/api/moments?end_date=${tomorrow.toISOString()}`,
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            data.moments.forEach((moment: any) => {
                const momentDate = new Date(moment.createdAt);
                expect(momentDate.getTime()).toBeLessThanOrEqual(tomorrow.getTime());
            });
        });

        it("should filter moments by date range", async () => {
            const today = new Date();
            const yesterday = new Date(today);
            yesterday.setDate(yesterday.getDate() - 1);
            const tomorrow = new Date(today);
            tomorrow.setDate(tomorrow.getDate() + 1);

            const response = await app.inject({
                method: "GET",
                url: `/api/moments?start_date=${yesterday.toISOString()}&end_date=${tomorrow.toISOString()}`,
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            data.moments.forEach((moment: any) => {
                const momentDate = new Date(moment.createdAt);
                expect(momentDate.getTime()).toBeGreaterThanOrEqual(yesterday.getTime());
                expect(momentDate.getTime()).toBeLessThanOrEqual(tomorrow.getTime());
            });
        });
    });

    describe("Pagination", () => {
        it("should paginate results correctly", async () => {
            // First page
            const response1 = await app.inject({
                method: "GET",
                url: "/api/moments?limit=3&page=1",
                headers: { authorization: `Bearer ${authToken}` },
            });

            // Second page
            const response2 = await app.inject({
                method: "GET",
                url: "/api/moments?limit=3&page=2",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response1.statusCode).toBe(200);
            expect(response2.statusCode).toBe(200);

            const data1 = JSON.parse(response1.body);
            const data2 = JSON.parse(response2.body);

            expect(data1.moments).toHaveLength(3);
            expect(data2.moments).toHaveLength(3);
            expect(data1.page).toBe(1);
            expect(data2.page).toBe(2);

            // Should have different moments on different pages
            const momentIds1 = data1.moments.map((m: any) => m.id);
            const momentIds2 = data2.moments.map((m: any) => m.id);
            const overlappingIds = momentIds1.filter((id: string) => momentIds2.includes(id));
            expect(overlappingIds).toHaveLength(0);
        });

        it("should handle pagination beyond available data", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?page=100&limit=10",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data.moments).toHaveLength(0);
            expect(data.total).toBe(testMoments.length);
        });

        it("should respect custom limit sizes", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?limit=5",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data.moments).toHaveLength(5);
            expect(data.limit).toBe(5);
        });
    });

    describe("Sorting", () => {
        it("should sort by creation date (newest first by default)", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?limit=5",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            // Should be sorted by creation date (newest first)
            for (let i = 1; i < data.moments.length; i++) {
                const current = new Date(data.moments[i].createdAt);
                const previous = new Date(data.moments[i - 1].createdAt);
                expect(current.getTime()).toBeLessThanOrEqual(previous.getTime());
            }
        });

        it("should sort by importance when specified", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?sort=importance&order=desc",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            // Should be sorted by importance (highest first)
            for (let i = 1; i < data.moments.length; i++) {
                const current = data.moments[i].importance;
                const previous = data.moments[i - 1].importance;
                expect(current).toBeLessThanOrEqual(previous);
            }
        });
    });

    describe("Combined Search Filters", () => {
        it("should combine search text with tag filters", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?search=project&tags=work",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            data.moments.forEach((moment: any) => {
                expect(moment.content.toLowerCase()).toContain('project');
                expect(moment.tags).toContain('work');
            });
        });

        it("should combine multiple filters", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?search=meeting&tags=work&emotions=focused&importance_min=3",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            data.moments.forEach((moment: any) => {
                expect(moment.content.toLowerCase()).toContain('meeting');
                expect(moment.tags).toContain('work');
                expect(moment.emotions).toContain('focused');
                expect(moment.importance).toBeGreaterThanOrEqual(3);
            });
        });
    });

    describe("Search Performance", () => {
        it("should handle complex searches efficiently", async () => {
            const startTime = Date.now();

            const response = await app.inject({
                method: "GET",
                url: "/api/moments?search=work&tags=work,meeting&emotions=focused,productive&importance_min=3&limit=20",
                headers: { authorization: `Bearer ${authToken}` },
            });

            const endTime = Date.now();
            const responseTime = endTime - startTime;

            expect(response.statusCode).toBe(200);
            expect(responseTime).toBeLessThan(500); // Complex search should be fast
        });

        it("should handle large text searches efficiently", async () => {
            const longSearchTerm = "development team project timeline deliverables meeting presentation quarterly business review technical architecture microservices design code review performance optimization security improvements strategic planning goals objectives machine learning artificial intelligence";

            const startTime = Date.now();

            const response = await app.inject({
                method: "GET",
                url: `/api/moments?search=${encodeURIComponent(longSearchTerm)}`,
                headers: { authorization: `Bearer ${authToken}` },
            });

            const endTime = Date.now();
            const responseTime = endTime - startTime;

            expect(response.statusCode).toBe(200);
            expect(responseTime).toBeLessThan(300);
        });
    });

    describe("Search Error Handling", () => {
        it("should handle invalid search parameters", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?importance_min=invalid",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(400);
            const error = JSON.parse(response.body);
            expect(error.message).toBeDefined();
        });

        it("should handle invalid date formats", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?start_date=invalid-date",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(400);
            const error = JSON.parse(response.body);
            expect(error.message).toBeDefined();
        });

        it("should handle unauthorized search requests", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?search=test",
            });

            expect(response.statusCode).toBe(401);
        });
    });

    describe("Search Relevance", () => {
        it("should return relevant results for partial matches", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?search=dev",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            // Should find moments with "development" or "develop"
            expect(data.total).toBeGreaterThan(0);

            // Results should contain the search term (case insensitive)
            data.moments.forEach((moment: any) => {
                expect(moment.content.toLowerCase()).toContain('dev');
            });
        });

        it("should handle special characters in search", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/moments?search=quarterly+review",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            // Should handle URL-encoded characters
            if (data.total > 0) {
                data.moments.forEach((moment: any) => {
                    expect(moment.content.toLowerCase()).toContain('quarterly');
                });
            }
        });
    });
});
