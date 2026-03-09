/**
 * Integration tests for Privacy API routes
 * Tests GDPR compliance, data export, and deletion functionality
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { buildServer } from "../server";
import { prisma } from "../lib/prisma";

describe("Privacy API Integration Tests", () => {
    const app = buildServer();

    let testUser: any;
    let authToken: string;
    let testMoments: any[] = [];
    let testSpheres: any[] = [];

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
                email: `privacy-test-${Date.now()}@example.com`,
                password: "TestPassword123!",
                displayName: "Privacy Test User",
            },
        });

        const userBody = JSON.parse(userResponse.body);
        testUser = userBody.user;
        authToken = userBody.token;

        // Create test spheres
        for (let i = 0; i < 3; i++) {
            const sphereResponse = await app.inject({
                method: "POST",
                url: "/api/spheres",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    name: `Test Sphere ${i}`,
                    description: `Test sphere for privacy testing ${i}`,
                    type: "PERSONAL",
                    visibility: "PRIVATE",
                },
            });
            testSpheres.push(JSON.parse(sphereResponse.body).sphere);
        }

        // Create test moments with various data types
        for (let i = 0; i < 5; i++) {
            const momentResponse = await app.inject({
                method: "POST",
                url: "/api/moments",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    sphereId: testSpheres[i % 3].id,
                    content: {
                        text: `Test moment content ${i} with sensitive data`,
                        type: "TEXT",
                    },
                    signals: {
                        emotions: ["happy", "thoughtful"],
                        tags: ["test", "privacy", `tag${i}`],
                        intent: "capture",
                    },
                },
            });
            testMoments.push(JSON.parse(momentResponse.body).moment);
        }
    });

    describe("GDPR Data Export", () => {
        it("should export all user data in JSON format", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/privacy/export",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            expect(response.headers['content-type']).toMatch(/application\/json/);
            expect(response.headers['content-disposition']).toContain('attachment');

            const exportData = JSON.parse(response.body);

            // Verify export structure
            expect(exportData).toHaveProperty('user');
            expect(exportData).toHaveProperty('spheres');
            expect(exportData).toHaveProperty('moments');
            expect(exportData).toHaveProperty('exportedAt');
            expect(exportData).toHaveProperty('version');

            // Verify user data
            expect(exportData.user.email).toBe(testUser.email);
            expect(exportData.user.displayName).toBe(testUser.displayName);
            expect(exportData.user).not.toHaveProperty('password'); // Password should not be exported

            // Verify spheres data
            expect(exportData.spheres).toHaveLength(3);
            exportData.spheres.forEach((sphere: any) => {
                expect(sphere).toHaveProperty('id');
                expect(sphere).toHaveProperty('name');
                expect(sphere).toHaveProperty('description');
                expect(sphere).toHaveProperty('createdAt');
                expect(sphere).toHaveProperty('updatedAt');
            });

            // Verify moments data
            expect(exportData.moments).toHaveLength(5);
            exportData.moments.forEach((moment: any) => {
                expect(moment).toHaveProperty('id');
                expect(moment).toHaveProperty('content');
                expect(moment).toHaveProperty('sphereId');
                expect(moment).toHaveProperty('emotions');
                expect(moment).toHaveProperty('tags');
                expect(moment).toHaveProperty('importance');
                expect(moment).toHaveProperty('createdAt');
                expect(moment).toHaveProperty('updatedAt');
            });
        });

        it("should export data in CSV format when requested", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/privacy/export?format=csv",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            expect(response.headers['content-type']).toMatch(/text\/csv/);
            expect(response.headers['content-disposition']).toContain('attachment');

            // Verify CSV structure
            const csvLines = response.body.split('\n');
            expect(csvLines.length).toBeGreaterThan(1); // Header + data rows

            // Check CSV headers
            const headers = csvLines[0].split(',');
            expect(headers).toContain('id');
            expect(headers).toContain('email');
            expect(headers).toContain('displayName');
            expect(headers).toContain('createdAt');
        });

        it("should return 401 for unauthorized export requests", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/privacy/export",
            });

            expect(response.statusCode).toBe(401);
        });
    });

    describe("GDPR Data Deletion", () => {
        it("should delete all user data and account", async () => {
            // Verify data exists before deletion
            const momentsBefore = await prisma.moment.findMany({
                where: { userId: testUser.id },
            });
            expect(momentsBefore).toHaveLength(5);

            // Request deletion
            const response = await app.inject({
                method: "DELETE",
                url: "/api/privacy/delete",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    confirmation: true,
                    password: "TestPassword123!",
                },
            });

            expect(response.statusCode).toBe(200);

            const result = JSON.parse(response.body);
            expect(result).toHaveProperty('deleted');
            expect(result).toHaveProperty('deletedAt');
            expect(result.deleted).toBe(true);

            // Verify data is deleted
            const momentsAfter = await prisma.moment.findMany({
                where: { userId: testUser.id },
            });
            expect(momentsAfter).toHaveLength(0);

            const userAfter = await prisma.user.findUnique({
                where: { id: testUser.id },
            });
            expect(userAfter).toBeNull();

            // Verify spheres are deleted
            const spheresAfter = await prisma.sphere.findMany({
                where: { userId: testUser.id },
            });
            expect(spheresAfter).toHaveLength(0);
        });

        it("should require password confirmation for deletion", async () => {
            const response = await app.inject({
                method: "DELETE",
                url: "/api/privacy/delete",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    confirmation: true,
                    password: "wrongpassword",
                },
            });

            expect(response.statusCode).toBe(400);
            const body = JSON.parse(response.body);
            expect(body.error).toContain('password');
        });

        it("should require explicit confirmation for deletion", async () => {
            const response = await app.inject({
                method: "DELETE",
                url: "/api/privacy/delete",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    password: "TestPassword123!",
                },
            });

            expect(response.statusCode).toBe(400);
            const body = JSON.parse(response.body);
            expect(body.error).toContain('confirmation');
        });
    });

    describe("Data Access Logs", () => {
        it("should log all data access attempts", async () => {
            // Perform some data access operations
            await app.inject({
                method: "GET",
                url: "/api/moments",
                headers: { authorization: `Bearer ${authToken}` },
            });

            await app.inject({
                method: "GET",
                url: "/api/privacy/export",
                headers: { authorization: `Bearer ${authToken}` },
            });

            // Check access logs
            const response = await app.inject({
                method: "GET",
                url: "/api/privacy/access-logs",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const logs = JSON.parse(response.body);

            expect(logs).toHaveProperty('logs');
            expect(logs).toHaveProperty('total');
            expect(Array.isArray(logs.logs)).toBe(true);
            expect(logs.total).toBeGreaterThan(0);

            // Verify log structure
            if (logs.logs.length > 0) {
                const log = logs.logs[0];
                expect(log).toHaveProperty('id');
                expect(log).toHaveProperty('userId');
                expect(log).toHaveProperty('action');
                expect(log).toHaveProperty('resource');
                expect(log).toHaveProperty('ipAddress');
                expect(log).toHaveProperty('userAgent');
                expect(log).toHaveProperty('timestamp');
            }
        });

        it("should paginate access logs correctly", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/privacy/access-logs?page=1&limit=10",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const logs = JSON.parse(response.body);

            expect(logs).toHaveProperty('logs');
            expect(logs).toHaveProperty('total');
            expect(logs).toHaveProperty('page');
            expect(logs).toHaveProperty('limit');
            expect(logs).toHaveProperty('totalPages');

            expect(logs.page).toBe(1);
            expect(logs.limit).toBe(10);
            expect(logs.logs.length).toBeLessThanOrEqual(10);
        });
    });

    describe("Data Retention", () => {
        it("should respect data retention policies", async () => {
            // Create old moment data
            const oldDate = new Date();
            oldDate.setFullYear(oldDate.getFullYear() - 2); // 2 years ago

            await prisma.moment.create({
                data: {
                    userId: testUser.id,
                    sphereId: testSpheres[0].id,
                    content: "Old moment that should be retained",
                    emotions: ["nostalgic"],
                    tags: ["old", "test"],
                    importance: 3,
                    createdAt: oldDate,
                    updatedAt: oldDate,
                },
            });

            // Check retention status
            const response = await app.inject({
                method: "GET",
                url: "/api/privacy/retention-status",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const status = JSON.parse(response.body);

            expect(status).toHaveProperty('retentionPolicy');
            expect(status).toHaveProperty('dataAge');
            expect(status).toHaveProperty('eligibleForDeletion');
            expect(status).toHaveProperty('scheduledDeletion');

            expect(status.retentionPolicy.maxAge).toBeGreaterThan(0);
            expect(typeof status.eligibleForDeletion).toBe('boolean');
        });
    });

    describe("Privacy Settings", () => {
        it("should allow users to manage privacy preferences", async () => {
            const privacySettings = {
                dataRetention: {
                    moments: "5years",
                    analytics: "2years",
                    auditLogs: "1year",
                },
                sharing: {
                    allowAnalytics: false,
                    allowMarketing: false,
                    allowResearch: true,
                },
                encryption: {
                    encryptAtRest: true,
                    encryptInTransit: true,
                },
            };

            const response = await app.inject({
                method: "PUT",
                url: "/api/privacy/settings",
                headers: { authorization: `Bearer ${authToken}` },
                payload: privacySettings,
            });

            expect(response.statusCode).toBe(200);
            const settings = JSON.parse(response.body);

            expect(settings.dataRetention.moments).toBe("5years");
            expect(settings.sharing.allowAnalytics).toBe(false);
            expect(settings.encryption.encryptAtRest).toBe(true);
        });

        it("should retrieve current privacy settings", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/privacy/settings",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const settings = JSON.parse(response.body);

            expect(settings).toHaveProperty('dataRetention');
            expect(settings).toHaveProperty('sharing');
            expect(settings).toHaveProperty('encryption');
            expect(settings).toHaveProperty('updatedAt');
        });
    });

    describe("Consent Management", () => {
        it("should track user consent for data processing", async () => {
            const consentData = {
                dataProcessing: true,
                analytics: false,
                marketing: false,
                research: true,
                cookies: true,
                timestamp: new Date().toISOString(),
                ipAddress: "127.0.0.1",
                userAgent: "test-agent",
            };

            const response = await app.inject({
                method: "POST",
                url: "/api/privacy/consent",
                headers: { authorization: `Bearer ${authToken}` },
                payload: consentData,
            });

            expect(response.statusCode).toBe(200);
            const consent = JSON.parse(response.body);

            expect(consent.dataProcessing).toBe(true);
            expect(consent.analytics).toBe(false);
            expect(consent).toHaveProperty('id');
            expect(consent).toHaveProperty('recordedAt');
        });

        it("should retrieve consent history", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/privacy/consent-history",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const history = JSON.parse(response.body);

            expect(history).toHaveProperty('consents');
            expect(Array.isArray(history.consents)).toBe(true);

            if (history.consents.length > 0) {
                const consent = history.consents[0];
                expect(consent).toHaveProperty('id');
                expect(consent).toHaveProperty('dataProcessing');
                expect(consent).toHaveProperty('analytics');
                expect(consent).toHaveProperty('marketing');
                expect(consent).toHaveProperty('research');
                expect(consent).toHaveProperty('recordedAt');
            }
        });
    });
});
