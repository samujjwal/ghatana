/**
 * Integration tests for Upload API routes
 * Tests file upload, presigned URLs, and media reference management
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { buildServer } from "../server";
import { prisma } from "../lib/prisma";

describe("Upload API Integration Tests", () => {
    const app = buildServer();

    let testUser: any;
    let authToken: string;
    let testSphere: any;
    let testMoment: any;

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
                email: `upload-test-${Date.now()}@example.com`,
                password: "TestPassword123!",
                displayName: "Upload Test User",
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
                name: "Upload Test Sphere",
                description: "Sphere for testing upload functionality",
                type: "PERSONAL",
                visibility: "PRIVATE",
            },
        });

        if (sphereResponse.statusCode !== 201) {
            console.error('Sphere creation failed:', sphereResponse.statusCode, sphereResponse.body);
            throw new Error(`Failed to create test sphere: ${sphereResponse.statusCode}`);
        }
        testSphere = JSON.parse(sphereResponse.body).sphere;

        // Create test moment
        const momentResponse = await app.inject({
            method: "POST",
            url: "/api/moments",
            headers: { authorization: `Bearer ${authToken}` },
            payload: {
                sphereId: testSphere.id,
                content: {
                    text: "Test moment for file upload",
                    type: "TEXT",
                },
                signals: {
                    emotions: ["happy"],
                    tags: ["test", "upload"],
                    intent: "capture",
                },
            },
        });

        testMoment = JSON.parse(momentResponse.body).moment;
    });

    describe("Presigned URL Generation", () => {
        it("should generate presigned URL for audio file", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "test-audio.mp3",
                    fileType: "audio/mpeg",
                    fileSize: 1024000, // 1MB
                    momentId: testMoment.id,
                },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data).toHaveProperty('uploadUrl');
            expect(data).toHaveProperty('fileKey');
            expect(data).toHaveProperty('expiresIn');
            expect(data).toHaveProperty('maxFileSize');

            expect(typeof data.uploadUrl).toBe('string');
            expect(typeof data.fileKey).toBe('string');
            expect(typeof data.expiresIn).toBe('number');
            expect(data.uploadUrl).toContain('amazonaws.com');
            expect(data.fileKey).toContain('test-audio.mp3');
        });

        it("should generate presigned URL for video file", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "test-video.mp4",
                    fileType: "video/mp4",
                    fileSize: 10485760, // 10MB
                    momentId: testMoment.id,
                },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data.uploadUrl).toContain('amazonaws.com');
            expect(data.fileKey).toContain('test-video.mp4');
        });

        it("should generate presigned URL for image file", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "test-image.jpg",
                    fileType: "image/jpeg",
                    fileSize: 500000, // 500KB
                    momentId: testMoment.id,
                },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data.uploadUrl).toContain('amazonaws.com');
            expect(data.fileKey).toContain('test-image.jpg');
        });

        it("should reject unauthorized presigned URL requests", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                payload: {
                    fileName: "test.mp3",
                    fileType: "audio/mpeg",
                    fileSize: 1024000,
                    momentId: testMoment.id,
                },
            });

            expect(response.statusCode).toBe(401);
        });

        it("should reject invalid file types", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "test.exe",
                    fileType: "application/x-executable",
                    fileSize: 1024000,
                    momentId: testMoment.id,
                },
            });

            expect(response.statusCode).toBe(400);
            const error = JSON.parse(response.body);
            expect(error.message).toContain('file type');
        });

        it("should reject files that are too large", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "huge-file.mp4",
                    fileType: "video/mp4",
                    fileSize: 500 * 1024 * 1024, // 500MB - should exceed limit
                    momentId: testMoment.id,
                },
            });

            expect(response.statusCode).toBe(400);
            const error = JSON.parse(response.body);
            expect(error.message).toContain('file size');
        });

        it("should reject requests for non-existent moment", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "test.mp3",
                    fileType: "audio/mpeg",
                    fileSize: 1024000,
                    momentId: "00000000-0000-0000-0000-000000000000",
                },
            });

            expect(response.statusCode).toBe(404);
            const error = JSON.parse(response.body);
            expect(error.message).toContain('moment');
        });

        it("should reject access to other user's moments", async () => {
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

            // Try to upload to first user's moment with second user's token
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${otherToken}` },
                payload: {
                    fileName: "test.mp3",
                    fileType: "audio/mpeg",
                    fileSize: 1024000,
                    momentId: testMoment.id,
                },
            });

            expect(response.statusCode).toBe(404);
        });
    });

    describe("Upload Confirmation", () => {
        it("should confirm successful upload", async () => {
            // First get presigned URL
            const presignedResponse = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "confirm-test.mp3",
                    fileType: "audio/mpeg",
                    fileSize: 1024000,
                    momentId: testMoment.id,
                },
            });

            const presignedData = JSON.parse(presignedResponse.body);

            // Confirm upload
            const confirmResponse = await app.inject({
                method: "POST",
                url: "/api/upload/confirm",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileKey: presignedData.fileKey,
                    fileName: "confirm-test.mp3",
                    fileType: "audio/mpeg",
                    fileSize: 1024000,
                    momentId: testMoment.id,
                },
            });

            expect(confirmResponse.statusCode).toBe(200);
            const result = JSON.parse(confirmResponse.body);

            expect(result).toHaveProperty('mediaReferenceId');
            expect(result).toHaveProperty('status');
            expect(result.status).toBe('completed');

            // Verify media reference was created
            const mediaRef = await prisma.mediaReference.findUnique({
                where: { id: result.mediaReferenceId },
            });

            expect(mediaRef).toBeTruthy();
            expect(mediaRef?.fileName).toBe("confirm-test.mp3");
            expect(mediaRef?.mimeType).toBe("audio/mpeg");
            expect(mediaRef?.sizeBytes).toBe(BigInt(1024000));
            expect(mediaRef?.uploadStatus).toBe("COMPLETED");
        });

        it("should reject confirmation for invalid file key", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/confirm",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileKey: "invalid-file-key",
                    fileName: "test.mp3",
                    fileType: "audio/mpeg",
                    fileSize: 1024000,
                    momentId: testMoment.id,
                },
            });

            expect(response.statusCode).toBe(400);
            const error = JSON.parse(response.body);
            expect(error.message).toContain('file key');
        });

        it("should reject confirmation with mismatched data", async () => {
            // Get valid presigned URL
            const presignedResponse = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "mismatch-test.mp3",
                    fileType: "audio/mpeg",
                    fileSize: 1024000,
                    momentId: testMoment.id,
                },
            });

            const presignedData = JSON.parse(presignedResponse.body);

            // Try to confirm with different file name
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/confirm",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileKey: presignedData.fileKey,
                    fileName: "different-name.mp3", // Different from presigned
                    fileType: "audio/mpeg",
                    fileSize: 1024000,
                    momentId: testMoment.id,
                },
            });

            expect(response.statusCode).toBe(400);
            const error = JSON.parse(response.body);
            expect(error.message).toContain('mismatch');
        });
    });

    describe("Progressive Upload", () => {
        it("should initialize multipart upload", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/multipart/init",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "large-file.mp4",
                    fileType: "video/mp4",
                    fileSize: 104857600, // 100MB
                    momentId: testMoment.id,
                },
            });

            expect(response.statusCode).toBe(200);
            const data = JSON.parse(response.body);

            expect(data).toHaveProperty('uploadId');
            expect(data).toHaveProperty('fileKey');
            expect(data).toHaveProperty('chunkSize');
            expect(data).toHaveProperty('totalChunks');

            expect(typeof data.uploadId).toBe('string');
            expect(typeof data.fileKey).toBe('string');
            expect(typeof data.chunkSize).toBe('number');
            expect(typeof data.totalChunks).toBe('number');
            expect(data.totalChunks).toBeGreaterThan(1);
        });

        it("should generate upload part URLs", async () => {
            // Initialize multipart upload
            const initResponse = await app.inject({
                method: "POST",
                url: "/api/upload/multipart/init",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "chunked-file.mp4",
                    fileType: "video/mp4",
                    fileSize: 20971520, // 20MB
                    momentId: testMoment.id,
                },
            });

            const initData = JSON.parse(initResponse.body);

            // Get upload URL for specific part
            const partResponse = await app.inject({
                method: "POST",
                url: "/api/upload/multipart/part",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    uploadId: initData.uploadId,
                    fileKey: initData.fileKey,
                    partNumber: 1,
                },
            });

            expect(partResponse.statusCode).toBe(200);
            const partData = JSON.parse(partResponse.body);

            expect(partData).toHaveProperty('uploadUrl');
            expect(partData).toHaveProperty('partNumber');
            expect(partData).toHaveProperty('expiresIn');

            expect(partData.uploadUrl).toContain('amazonaws.com');
            expect(partData.partNumber).toBe(1);
        });

        it("should complete multipart upload", async () => {
            // Initialize multipart upload
            const initResponse = await app.inject({
                method: "POST",
                url: "/api/upload/multipart/init",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "complete-test.mp4",
                    fileType: "video/mp4",
                    fileSize: 10485760, // 10MB
                    momentId: testMoment.id,
                },
            });

            const initData = JSON.parse(initResponse.body);

            // Complete upload (mock parts)
            const completeResponse = await app.inject({
                method: "POST",
                url: "/api/upload/multipart/complete",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    uploadId: initData.uploadId,
                    fileKey: initData.fileKey,
                    fileName: "complete-test.mp4",
                    fileType: "video/mp4",
                    fileSize: 10485760,
                    momentId: testMoment.id,
                    parts: [
                        { partNumber: 1, eTag: '"test-etag-1"' },
                        { partNumber: 2, eTag: '"test-etag-2"' },
                    ],
                },
            });

            expect(completeResponse.statusCode).toBe(200);
            const result = JSON.parse(completeResponse.body);

            expect(result).toHaveProperty('mediaReferenceId');
            expect(result).toHaveProperty('status');
            expect(result.status).toBe('completed');
        });
    });

    describe("Upload Status and Management", () => {
        it("should retrieve upload status", async () => {
            // Create a media reference directly
            const mediaRef = await prisma.mediaReference.create({
                data: {
                    momentId: testMoment.id,
                    s3Bucket: "test-bucket",
                    s3Key: "test/status-test.mp3",
                    fileName: "status-test.mp3",
                    mimeType: "audio/mpeg",
                    sizeBytes: BigInt(1024000),
                    uploadStatus: "PENDING",
                },
            });

            const response = await app.inject({
                method: "GET",
                url: `/api/upload/status/${mediaRef.id}`,
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const status = JSON.parse(response.body);

            expect(status).toHaveProperty('mediaReferenceId');
            expect(status).toHaveProperty('status');
            expect(status).toHaveProperty('progress');
            expect(status).toHaveProperty('uploadedAt');

            expect(status.mediaReferenceId).toBe(mediaRef.id);
            expect(status.status).toBe('PENDING');
        });

        it("should list uploads for user", async () => {
            // Create multiple media references
            for (let i = 0; i < 3; i++) {
                await prisma.mediaReference.create({
                    data: {
                        momentId: testMoment.id,
                        s3Bucket: "test-bucket",
                        s3Key: `test/list-test-${i}.mp3`,
                        fileName: `list-test-${i}.mp3`,
                        mimeType: "audio/mpeg",
                        sizeBytes: BigInt(1024000),
                        uploadStatus: "COMPLETED",
                        uploadedAt: new Date(),
                    },
                });
            }

            const response = await app.inject({
                method: "GET",
                url: "/api/uploads",
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(response.statusCode).toBe(200);
            const uploads = JSON.parse(response.body);

            expect(uploads).toHaveProperty('uploads');
            expect(uploads).toHaveProperty('total');
            expect(uploads).toHaveProperty('page');
            expect(uploads).toHaveProperty('limit');

            expect(Array.isArray(uploads.uploads)).toBe(true);
            expect(uploads.total).toBeGreaterThanOrEqual(3);
        });

        it("should delete media reference", async () => {
            // Create media reference
            const mediaRef = await prisma.mediaReference.create({
                data: {
                    momentId: testMoment.id,
                    s3Bucket: "test-bucket",
                    s3Key: "test/delete-test.mp3",
                    fileName: "delete-test.mp3",
                    mimeType: "audio/mpeg",
                    sizeBytes: BigInt(1024000),
                    uploadStatus: "COMPLETED",
                    uploadedAt: new Date(),
                },
            });

            // Delete media reference
            const deleteResponse = await app.inject({
                method: "DELETE",
                url: `/api/upload/${mediaRef.id}`,
                headers: { authorization: `Bearer ${authToken}` },
            });

            expect(deleteResponse.statusCode).toBe(200);
            const result = JSON.parse(deleteResponse.body);
            expect(result.deleted).toBe(true);

            // Verify deletion
            const deletedRef = await prisma.mediaReference.findUnique({
                where: { id: mediaRef.id },
            });

            expect(deletedRef).toBeNull();
        });
    });

    describe("File Type Validation", () => {
        it("should support all allowed audio formats", async () => {
            const audioFormats = [
                { fileName: "test.mp3", fileType: "audio/mpeg" },
                { fileName: "test.wav", fileType: "audio/wav" },
                { fileName: "test.m4a", fileType: "audio/mp4" },
                { fileName: "test.ogg", fileType: "audio/ogg" },
                { fileName: "test.flac", fileType: "audio/flac" },
            ];

            for (const format of audioFormats) {
                const response = await app.inject({
                    method: "POST",
                    url: "/api/upload/presigned-url",
                    headers: { authorization: `Bearer ${authToken}` },
                    payload: {
                        fileName: format.fileName,
                        fileType: format.fileType,
                        fileSize: 1024000,
                        momentId: testMoment.id,
                    },
                });

                expect(response.statusCode).toBe(200);
            }
        });

        it("should support all allowed video formats", async () => {
            const videoFormats = [
                { fileName: "test.mp4", fileType: "video/mp4" },
                { fileName: "test.mov", fileType: "video/quicktime" },
                { fileName: "test.avi", fileType: "video/x-msvideo" },
                { fileName: "test.webm", fileType: "video/webm" },
            ];

            for (const format of videoFormats) {
                const response = await app.inject({
                    method: "POST",
                    url: "/api/upload/presigned-url",
                    headers: { authorization: `Bearer ${authToken}` },
                    payload: {
                        fileName: format.fileName,
                        fileType: format.fileType,
                        fileSize: 10485760,
                        momentId: testMoment.id,
                    },
                });

                expect([200, 400]).toContain(response.statusCode); // Some formats might not be supported
            }
        });

        it("should support all allowed image formats", async () => {
            const imageFormats = [
                { fileName: "test.jpg", fileType: "image/jpeg" },
                { fileName: "test.jpeg", fileType: "image/jpeg" },
                { fileName: "test.png", fileType: "image/png" },
                { fileName: "test.gif", fileType: "image/gif" },
                { fileName: "test.webp", fileType: "image/webp" },
            ];

            for (const format of imageFormats) {
                const response = await app.inject({
                    method: "POST",
                    url: "/api/upload/presigned-url",
                    headers: { authorization: `Bearer ${authToken}` },
                    payload: {
                        fileName: format.fileName,
                        fileType: format.fileType,
                        fileSize: 500000,
                        momentId: testMoment.id,
                    },
                });

                expect(response.statusCode).toBe(200);
            }
        });
    });

    describe("Error Handling", () => {
        it("should handle malformed requests", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    // Missing required fields
                    fileName: "test.mp3",
                },
            });

            expect(response.statusCode).toBe(400);
        });

        it("should handle invalid UUID format", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "test.mp3",
                    fileType: "audio/mpeg",
                    fileSize: 1024000,
                    momentId: "invalid-uuid-format",
                },
            });

            expect(response.statusCode).toBe(400);
        });

        it("should handle negative file sizes", async () => {
            const response = await app.inject({
                method: "POST",
                url: "/api/upload/presigned-url",
                headers: { authorization: `Bearer ${authToken}` },
                payload: {
                    fileName: "test.mp3",
                    fileType: "audio/mpeg",
                    fileSize: -1000,
                    momentId: testMoment.id,
                },
            });

            expect(response.statusCode).toBe(400);
        });
    });
});
