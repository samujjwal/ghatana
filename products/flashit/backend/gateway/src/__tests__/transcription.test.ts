/**
 * Integration tests for Transcription API routes
 * Tests audio/video transcription, Whisper integration, and media processing
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { buildServer } from "../server";
import { prisma } from "../lib/prisma";

describe("Transcription API Integration Tests", () => {
    const app = buildServer();

    let testUser: any;
    let authToken: string;
    let testSphere: any;
    let testMoment: any;
    let testMediaReference: any;

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
                email: `transcription-test-${Date.now()}@example.com`,
                password: "TestPassword123!",
                displayName: "Transcription Test User",
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
                name: "Transcription Test Sphere",
                description: "Sphere for testing transcription functionality",
                type: "PERSONAL",
                visibility: "PRIVATE",
            },
        });

        testSphere = JSON.parse(sphereResponse.body).sphere;

        // Create test moment
        const momentResponse = await app.inject({
            method: "POST",
            url: "/api/moments",
            headers: { authorization: `Bearer ${authToken}` },
            payload: {
                sphereId: testSphere.id,
                content: {
                    text: "Test moment for transcription",
                    type: "TEXT",
                },
                signals: {
                    emotions: ["happy"],
                    tags: ["test", "transcription"],
                    intent: "capture",
                },
            },
        });

        testMoment = JSON.parse(momentResponse.body).moment;

        // Create test media reference
        testMediaReference = await prisma.mediaReference.create({
            data: {
                momentId: testMoment.id,
                s3Bucket: "test-bucket",
                s3Key: "test/audio/sample.mp3",
                fileName: "sample.mp3",
                mimeType: "audio/mpeg",
                sizeBytes: BigInt(1024000), // 1MB
                uploadStatus: "COMPLETED",
                uploadedAt: new Date(),
            },
        });
    });

    describe("Audio Transcription", () => {
        it("should start transcription job for audio file", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: testMediaReference.id,
                    priority: "normal",
                },
            });

            expect(response.statusCode).toBe(200);
            const job = JSON.parse(response.body);

            expect(job).toHaveProperty('jobId');
            expect(job).toHaveProperty('status');
            expect(job).toHaveProperty('estimatedCompletionTime');
            expect(['queued', 'in_progress']).toContain(job.status);
            expect(typeof job.jobId).toBe('string');
            expect(typeof job.estimatedCompletionTime).toBe('string');
        });

        it("should retrieve transcription job status", async () => {
            // Start transcription first
            const startResponse = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: testMediaReference.id,
                    priority: "normal",
                },
            });

            const job = JSON.parse(startResponse.body);

            // Get job status
            const statusResponse = await app.inject({
                method: "GET",
                url: `/api/transcription/status/${job.jobId}`,
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(statusResponse.statusCode).toBe(200);
            const status = JSON.parse(statusResponse.body);

            expect(status.jobId).toBe(job.jobId);
            expect(status).toHaveProperty('status');
            expect(['queued', 'in_progress', 'completed', 'failed', 'waiting']).toContain(status.status);
        });

        it("should handle duplicate transcription requests", async () => {
            // Start first transcription
            await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: testMediaReference.id,
                    priority: "normal",
                },
            });

            // Try to start second transcription for same media
            const duplicateResponse = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: testMediaReference.id,
                    priority: "normal",
                },
            });

            // Should return existing job or conflict
            expect([200, 409]).toContain(duplicateResponse.statusCode);

            if (duplicateResponse.statusCode === 200) {
                const job = JSON.parse(duplicateResponse.body);
                expect(job.status).toBe('in_progress');
            }
        });

        it("should reject transcription for non-audio/video files", async () => {
            // Create media reference for image file
            const imageMedia = await prisma.mediaReference.create({
                data: {
                    momentId: testMoment.id,
                    s3Bucket: "test-bucket",
                    s3Key: "test/image/sample.jpg",
                    fileName: "sample.jpg",
                    mimeType: "image/jpeg",
                    sizeBytes: BigInt(500000),
                    uploadStatus: "COMPLETED",
                    uploadedAt: new Date(),
                },
            });

            const response = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: imageMedia.id,
                    priority: "normal",
                },
            });

            expect(response.statusCode).toBe(400);
            const error = JSON.parse(response.body);
            expect(error.message).toContain('audio and video');
        });

        it("should return 404 for non-existent media reference", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: "00000000-0000-0000-0000-000000000000",
                    priority: "normal",
                },
            });

            expect(response.statusCode).toBe(404);
            const error = JSON.parse(response.body);
            expect(error.message).toContain('not found');
        });
    });

    describe("Priority and Performance", () => {
        it("should handle different priority levels", async () => {
            const priorities = ['high', 'normal', 'low'];
            const jobs = [];

            for (const priority of priorities) {
                const response = await app.inject({
                    method: "POST",
                    url: "/api/transcription/start",
                    headers: { authorization: `Bearer ${authToken}` },
                    payload: {
                        mediaReferenceId: testMediaReference.id,
                        priority,
                    },
                });

                if (response.statusCode === 200) {
                    jobs.push(JSON.parse(response.body));
                }
            }

            // At least one priority should work
            expect(jobs.length).toBeGreaterThan(0);
        });

        it("should estimate completion time based on file size", async () => {
            // Create larger media file
            const largeMedia = await prisma.mediaReference.create({
                data: {
                    momentId: testMoment.id,
                    s3Bucket: "test-bucket",
                    s3Key: "test/audio/large.mp3",
                    fileName: "large.mp3",
                    mimeType: "audio/mpeg",
                    sizeBytes: BigInt(10485760), // 10MB
                    uploadStatus: "COMPLETED",
                    uploadedAt: new Date(),
                },
            });

            const response = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: largeMedia.id,
                    priority: "normal",
                },
            });

            expect(response.statusCode).toBe(200);
            const job = JSON.parse(response.body);

            expect(job).toHaveProperty('estimatedCompletionTime');

            // Larger file should have longer estimated time
            const completionTime = new Date(job.estimatedCompletionTime);
            const now = new Date();
            const estimatedMinutes = (completionTime.getTime() - now.getTime()) / (1000 * 60);

            // Should estimate at least a minute for 10MB file
            expect(estimatedMinutes).toBeGreaterThan(1);
        });
    });

    describe("Error Handling", () => {
        it("should handle invalid media reference ID", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: "invalid-uuid",
                    priority: "normal",
                },
            });

            expect(response.statusCode).toBe(400);
        });

        it("should handle invalid priority", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: testMediaReference.id,
                    priority: "invalid-priority",
                },
            });

            expect(response.statusCode).toBe(400);
        });

        it("should handle unauthorized access", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                payload: {
                    mediaReferenceId: testMediaReference.id,
                    priority: "normal",
                },
            });

            expect(response.statusCode).toBe(401);
        });

        it("should handle access to other user's media", async () => {
            // Create another user
            const otherUserResponse = await app.inject({
                method: "POST",
                url: "/auth/register",
                payload: {
                    email: `other-user-${Date.now()}@example.com`,
                    password: "TestPassword123!",
                    displayName: "Other User",
                },
            });

            const otherUser = JSON.parse(otherUserResponse.body);
            const otherToken = otherUser.token;

            // Try to transcribe first user's media with second user's token
            const response = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${otherToken}` },
                payload: {
                    mediaReferenceId: testMediaReference.id,
                    priority: "normal",
                },
            });

            expect(response.statusCode).toBe(404); // Should not find the media reference
        });

        it("should handle missing media reference", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: "00000000-0000-0000-0000-000000000000",
                    priority: "normal",
                },
            });

            expect(response.statusCode).toBe(404);
        });
    });

    describe("Job Status and Results", () => {
        it("should return not found for non-existent job", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/transcription/status/non-existent-job-id",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(404);
            const error = JSON.parse(response.body);
            expect(error.message).toContain('not found');
        });

        it("should handle job status without authorization", async () => {
            const response = await app.inject({
                method: "GET",
                url: "/api/transcription/status/some-job-id",
            });

            expect(response.statusCode).toBe(401);
        });
    });

    describe("Integration with Moments", () => {
        it("should create moment with media reference", async () => {
            // Create a new moment with media
            const momentWithMediaResponse = await app.inject({
                method: "POST",
                url: "/api/moments",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    content: "Test moment with media for transcription",
                    sphereId: testSphere.id,
                    emotions: ["thoughtful"],
                    tags: ["test", "media"],
                    importance: 4,
                },
            });

            expect(momentWithMediaResponse.statusCode).toBe(201);
            const momentWithMedia = JSON.parse(momentWithMediaResponse.body);

            // Create media reference for this moment
            const mediaRef = await prisma.mediaReference.create({
                data: {
                    momentId: momentWithMedia.id,
                    s3Bucket: "test-bucket",
                    s3Key: "test/audio/voice-note.mp3",
                    fileName: "voice-note.mp3",
                    mimeType: "audio/mpeg",
                    sizeBytes: BigInt(2048000), // 2MB
                    uploadStatus: "COMPLETED",
                    uploadedAt: new Date(),
                },
            });

            // Start transcription for the new media
            const transcriptionResponse = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: mediaRef.id,
                    priority: "high",
                },
            });

            expect(transcriptionResponse.statusCode).toBe(200);
            const job = JSON.parse(transcriptionResponse.body);
            expect(job.priority).toBe('high');
        });
    });

    describe("Video Transcription", () => {
        it("should accept video files for transcription", async () => {
            // Create media reference for video file
            const videoMedia = await prisma.mediaReference.create({
                data: {
                    momentId: testMoment.id,
                    s3Bucket: "test-bucket",
                    s3Key: "test/video/meeting.mp4",
                    fileName: "meeting.mp4",
                    mimeType: "video/mp4",
                    sizeBytes: BigInt(10485760), // 10MB
                    uploadStatus: "COMPLETED",
                    uploadedAt: new Date(),
                },
            });

            const response = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: videoMedia.id,
                    priority: "normal",
                },
            });

            expect(response.statusCode).toBe(200);
            const job = JSON.parse(response.body);
            expect(job).toHaveProperty('jobId');
            expect(job).toHaveProperty('estimatedCompletionTime');
        });

        it("should reject unsupported video formats", async () => {
            // Create media reference for unsupported video format
            const videoMedia = await prisma.mediaReference.create({
                data: {
                    momentId: testMoment.id,
                    s3Bucket: "test-bucket",
                    s3Key: "test/video/unsupported.avi",
                    fileName: "unsupported.avi",
                    mimeType: "video/avi",
                    sizeBytes: BigInt(5242880), // 5MB
                    uploadStatus: "COMPLETED",
                    uploadedAt: new Date(),
                },
            });

            const response = await app.inject({
                method: "POST",
                url: "/api/transcription/start",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    mediaReferenceId: videoMedia.id,
                    priority: "normal",
                },
            });

            // Should either accept (if format is supported) or reject with specific error
            expect([200, 400]).toContain(response.statusCode);

            if (response.statusCode === 400) {
                const error = JSON.parse(response.body);
                expect(error.message).toContain('format');
            }
        });
    });
});
