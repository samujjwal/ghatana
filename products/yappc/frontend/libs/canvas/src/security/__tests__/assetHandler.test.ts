/**
 * Tests for AssetHandler
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import {
  createAssetHandler,
  type AssetHandlerConfig,
  type AssetMetadata,

  AssetHandler} from '../assetHandler';

describe.skip('AssetHandler', () => {
  let handler: AssetHandler;
  let config: AssetHandlerConfig;

  beforeEach(() => {
    config = {
      secretKey: 'test-secret-key-123',
      baseUrl: 'https://assets.example.com',
      signatureTtl: 3600,
      trackMetadata: true,
    };
    handler = createAssetHandler(config);
  });

  afterEach(() => {
    handler.destroy();
  });

  describe('Signed URL Generation', () => {
    it('should generate signed URL', () => {
      const result = handler.generateSignedUrl('asset-123');

      expect(result.url).toContain('https://assets.example.com/assets/asset-123');
      expect(result.url).toContain('signature=');
      expect(result.url).toContain('expires=');
      expect(result.signature).toBeDefined();
      expect(result.expiresAt).toBeGreaterThan(Date.now());
      expect(result.assetId).toBe('asset-123');
    });

    it('should generate URL with custom TTL', () => {
      const ttl = 7200; // 2 hours
      const before = Date.now() + ttl * 1000;
      
      const result = handler.generateSignedUrl('asset-123', { ttl });
      
      expect(result.expiresAt).toBeGreaterThanOrEqual(before - 100);
      expect(result.expiresAt).toBeLessThanOrEqual(before + 100);
    });

    it('should include user ID in signature', () => {
      const result = handler.generateSignedUrl('asset-123', { userId: 'user-456' });

      expect(result.url).toContain('user=user-456');
    });

    it('should generate different signatures for different assets', () => {
      const result1 = handler.generateSignedUrl('asset-1');
      const result2 = handler.generateSignedUrl('asset-2');

      expect(result1.signature).not.toBe(result2.signature);
    });

    it('should track access when generating URL', () => {
      handler.storeMetadata({
        id: 'asset-123',
        type: 'image',
        filename: 'test.jpg',
        contentType: 'image/jpeg',
        size: 1024,
        uploadedAt: Date.now(),
        uploadedBy: 'user-1',
        storagePath: '/uploads/test.jpg',
      });

      handler.generateSignedUrl('asset-123');

      const updated = handler.getMetadata('asset-123');
      expect(updated!.accessCount).toBe(1);
      expect(updated!.lastAccessedAt).toBeDefined();
    });
  });

  describe('Signed URL Verification', () => {
    it('should verify valid signed URL', () => {
      const signed = handler.generateSignedUrl('asset-123');
      const result = handler.verifySignedUrl(signed.url);

      expect(result.valid).toBe(true);
      expect(result.assetId).toBe('asset-123');
    });

    it('should reject expired signature', () => {
      const signed = handler.generateSignedUrl('asset-123', { ttl: -10 });
      const result = handler.verifySignedUrl(signed.url);

      expect(result.valid).toBe(false);
      expect(result.reason).toContain('expired');
    });

    it('should reject invalid signature', () => {
      const signed = handler.generateSignedUrl('asset-123');
      const tamperedUrl = signed.url.replace(/signature=[^&]+/, 'signature=invalid');
      
      const result = handler.verifySignedUrl(tamperedUrl);

      expect(result.valid).toBe(false);
      expect(result.reason).toContain('Invalid signature');
    });

    it('should reject missing signature', () => {
      const url = 'https://assets.example.com/assets/asset-123';
      const result = handler.verifySignedUrl(url);

      expect(result.valid).toBe(false);
      expect(result.reason).toContain('Missing signature');
    });

    it('should reject user mismatch', () => {
      const signed = handler.generateSignedUrl('asset-123', { userId: 'user-1' });
      const result = handler.verifySignedUrl(signed.url, { userId: 'user-2' });

      expect(result.valid).toBe(false);
      expect(result.reason).toContain('User mismatch');
    });

    it('should log access attempts', () => {
      const signed = handler.generateSignedUrl('asset-123');
      handler.verifySignedUrl(signed.url, { userId: 'user-1', ipAddress: '192.168.1.1' });

      const logs = handler.getAccessLogs({ assetId: 'asset-123' });
      expect(logs.length).toBeGreaterThanOrEqual(1);
      expect(logs[0].granted).toBe(true);
      expect(logs[0].userId).toBe('user-1');
      expect(logs[0].ipAddress).toBe('192.168.1.1');
    });

    it('should log denied access', () => {
      const signed = handler.generateSignedUrl('asset-123', { ttl: -10 });
      handler.verifySignedUrl(signed.url);

      const logs = handler.getAccessLogs({ granted: false });
      expect(logs.length).toBeGreaterThan(0);
      expect(logs[0].granted).toBe(false);
      expect(logs[0].reason).toBeDefined();
    });
  });

  describe('Upload URL Validation', () => {
    it('should allow all domains when allowlist is empty', () => {
      const result = handler.validateUploadUrl('https://anywhere.com/file.jpg');
      expect(result.valid).toBe(true);
    });

    it('should validate against allowlist', () => {
      const handlerWithAllowlist = createAssetHandler({
        ...config,
        uploadAllowlist: ['cdn.example.com', 'storage.example.com'],
      });

      const result1 = handlerWithAllowlist.validateUploadUrl('https://cdn.example.com/file.jpg');
      expect(result1.valid).toBe(true);

      const result2 = handlerWithAllowlist.validateUploadUrl('https://evil.com/file.jpg');
      expect(result2.valid).toBe(false);
      expect(result2.reason).toContain('not in allowlist');

      handlerWithAllowlist.destroy();
    });

    it('should support wildcard subdomains', () => {
      const handlerWithWildcard = createAssetHandler({
        ...config,
        uploadAllowlist: ['*.example.com'],
      });

      const result1 = handlerWithWildcard.validateUploadUrl('https://cdn.example.com/file.jpg');
      expect(result1.valid).toBe(true);

      const result2 = handlerWithWildcard.validateUploadUrl('https://storage.example.com/file.jpg');
      expect(result2.valid).toBe(true);

      const result3 = handlerWithWildcard.validateUploadUrl('https://example.com/file.jpg');
      expect(result3.valid).toBe(true);

      const result4 = handlerWithWildcard.validateUploadUrl('https://evil.com/file.jpg');
      expect(result4.valid).toBe(false);

      handlerWithWildcard.destroy();
    });

    it('should reject invalid URL format', () => {
      const result = handler.validateUploadUrl('not-a-url');
      expect(result.valid).toBe(false);
      expect(result.reason).toContain('Invalid URL');
    });
  });

  describe('Upload Validation', () => {
    it('should validate allowed image upload', () => {
      const result = handler.validateUpload('photo.jpg', 'image/jpeg', 5 * 1024 * 1024);

      expect(result.valid).toBe(true);
      expect(result.contentType).toBe('image/jpeg');
      expect(result.assetType).toBe('image');
    });

    it('should reject disallowed content type', () => {
      const result = handler.validateUpload('script.js', 'application/javascript', 1024);

      expect(result.valid).toBe(false);
      expect(result.error).toContain('not allowed');
    });

    it('should reject oversized upload', () => {
      const result = handler.validateUpload('huge.jpg', 'image/jpeg', 20 * 1024 * 1024);

      expect(result.valid).toBe(false);
      expect(result.error).toContain('exceeds maximum');
    });

    it('should validate video upload', () => {
      const result = handler.validateUpload('video.mp4', 'video/mp4', 50 * 1024 * 1024);

      expect(result.valid).toBe(true);
      expect(result.assetType).toBe('video');
    });

    it('should validate audio upload', () => {
      const result = handler.validateUpload('song.mp3', 'audio/mpeg', 5 * 1024 * 1024);

      expect(result.valid).toBe(true);
      expect(result.assetType).toBe('audio');
    });

    it('should validate document upload', () => {
      const result = handler.validateUpload('doc.pdf', 'application/pdf', 10 * 1024 * 1024);

      expect(result.valid).toBe(true);
      expect(result.assetType).toBe('document');
    });

    it('should respect custom size limits', () => {
      const customHandler = createAssetHandler({
        ...config,
        maxSizes: { image: 1024 * 1024 }, // 1MB
      });

      const result = customHandler.validateUpload('large.jpg', 'image/jpeg', 2 * 1024 * 1024);
      expect(result.valid).toBe(false);

      customHandler.destroy();
    });
  });

  describe('Metadata Management', () => {
    it('should store and retrieve metadata', () => {
      handler.storeMetadata({
        id: 'asset-123',
        type: 'image',
        filename: 'test.jpg',
        contentType: 'image/jpeg',
        size: 1024,
        uploadedAt: Date.now(),
        uploadedBy: 'user-1',
        storagePath: '/uploads/test.jpg',
      });

      const retrieved = handler.getMetadata('asset-123');

      expect(retrieved).toBeDefined();
      expect(retrieved!.id).toBe('asset-123');
      expect(retrieved!.filename).toBe('test.jpg');
    });

    it('should update metadata', () => {
      handler.storeMetadata({
        id: 'asset-123',
        type: 'image',
        filename: 'test.jpg',
        contentType: 'image/jpeg',
        size: 1024,
        uploadedAt: Date.now(),
        uploadedBy: 'user-1',
        storagePath: '/uploads/test.jpg',
      });

      const updated = handler.updateMetadata('asset-123', { filename: 'renamed.jpg' });

      expect(updated).toBe(true);
      expect(handler.getMetadata('asset-123')!.filename).toBe('renamed.jpg');
    });

    it('should return false when updating non-existent asset', () => {
      const updated = handler.updateMetadata('non-existent', { filename: 'test.jpg' });
      expect(updated).toBe(false);
    });

    it('should delete metadata', () => {
      handler.storeMetadata({
        id: 'asset-123',
        type: 'image',
        filename: 'test.jpg',
        contentType: 'image/jpeg',
        size: 1024,
        uploadedAt: Date.now(),
        uploadedBy: 'user-1',
        storagePath: '/uploads/test.jpg',
      });

      const deleted = handler.deleteMetadata('asset-123');

      expect(deleted).toBe(true);
      expect(handler.getMetadata('asset-123')).toBeUndefined();
    });

    it('should return false when deleting non-existent asset', () => {
      const deleted = handler.deleteMetadata('non-existent');
      expect(deleted).toBe(false);
    });
  });

  describe('Asset Listing', () => {
    beforeEach(() => {
      const now = Date.now();
      handler.storeMetadata({
        id: 'img-1',
        type: 'image',
        filename: 'photo1.jpg',
        contentType: 'image/jpeg',
        size: 1024,
        uploadedAt: now - 1000,
        uploadedBy: 'user-1',
        storagePath: '/uploads/photo1.jpg',
      });

      handler.storeMetadata({
        id: 'vid-1',
        type: 'video',
        filename: 'video1.mp4',
        contentType: 'video/mp4',
        size: 2048,
        uploadedAt: now,
        uploadedBy: 'user-2',
        storagePath: '/uploads/video1.mp4',
      });

      handler.storeMetadata({
        id: 'img-2',
        type: 'image',
        filename: 'photo2.jpg',
        contentType: 'image/jpeg',
        size: 1536,
        uploadedAt: now + 1000,
        uploadedBy: 'user-1',
        storagePath: '/uploads/photo2.jpg',
      });
    });

    it('should list all assets', () => {
      const assets = handler.listAssets();
      expect(assets.length).toBe(3);
    });

    it('should filter by type', () => {
      const images = handler.listAssets({ type: 'image' });
      expect(images.length).toBe(2);
      expect(images.every(a => a.type === 'image')).toBe(true);
    });

    it('should filter by uploader', () => {
      const user1Assets = handler.listAssets({ uploadedBy: 'user-1' });
      expect(user1Assets.length).toBe(2);
      expect(user1Assets.every(a => a.uploadedBy === 'user-1')).toBe(true);
    });

    it('should filter by upload time', () => {
      const now = Date.now();
      const recent = handler.listAssets({ uploadedAfter: now - 500 });
      expect(recent.length).toBeGreaterThanOrEqual(2);
    });

    it('should combine filters', () => {
      const filtered = handler.listAssets({
        type: 'image',
        uploadedBy: 'user-1',
      });

      expect(filtered.length).toBe(2);
      expect(filtered.every(a => a.type === 'image' && a.uploadedBy === 'user-1')).toBe(true);
    });
  });

  describe('Access Logs', () => {
    it('should get all access logs', () => {
      const signed = handler.generateSignedUrl('asset-123');
      handler.verifySignedUrl(signed.url, { userId: 'user-1' });

      const logs = handler.getAccessLogs();
      expect(logs.length).toBeGreaterThan(0);
    });

    it('should filter logs by asset', () => {
      const signed1 = handler.generateSignedUrl('asset-1');
      const signed2 = handler.generateSignedUrl('asset-2');
      
      handler.verifySignedUrl(signed1.url);
      handler.verifySignedUrl(signed2.url);

      const logs = handler.getAccessLogs({ assetId: 'asset-1' });
      expect(logs.every(l => l.assetId === 'asset-1')).toBe(true);
    });

    it('should filter logs by user', () => {
      const signed = handler.generateSignedUrl('asset-123');
      handler.verifySignedUrl(signed.url, { userId: 'user-1' });

      const logs = handler.getAccessLogs({ userId: 'user-1' });
      expect(logs.every(l => l.userId === 'user-1')).toBe(true);
    });

    it('should filter logs by granted status', () => {
      const signed = handler.generateSignedUrl('asset-123', { ttl: -10 });
      handler.verifySignedUrl(signed.url);

      const denied = handler.getAccessLogs({ granted: false });
      expect(denied.every(l => !l.granted)).toBe(true);
    });

    it('should filter logs by time', () => {
      const before = Date.now();
      const signed = handler.generateSignedUrl('asset-123');
      handler.verifySignedUrl(signed.url);

      const recent = handler.getAccessLogs({ since: before });
      expect(recent.every(l => l.timestamp >= before)).toBe(true);
    });

    it('should clear access logs', () => {
      const signed = handler.generateSignedUrl('asset-123');
      handler.verifySignedUrl(signed.url);

      handler.clearAccessLogs();
      expect(handler.getAccessLogs()).toEqual([]);
    });
  });

  describe('Statistics', () => {
    beforeEach(() => {
      handler.storeMetadata({
        id: 'img-1',
        type: 'image',
        filename: 'photo1.jpg',
        contentType: 'image/jpeg',
        size: 1000,
        uploadedAt: Date.now(),
        uploadedBy: 'user-1',
        storagePath: '/uploads/photo1.jpg',
      });
      // Simulate accesses
      for (let i = 0; i < 5; i++) {
        handler.generateSignedUrl('img-1');
      }

      handler.storeMetadata({
        id: 'vid-1',
        type: 'video',
        filename: 'video1.mp4',
        contentType: 'video/mp4',
        size: 2000,
        uploadedAt: Date.now(),
        uploadedBy: 'user-1',
        storagePath: '/uploads/video1.mp4',
      });
      // Simulate accesses
      for (let i = 0; i < 3; i++) {
        handler.generateSignedUrl('vid-1');
      }
    });

    it('should calculate total assets', () => {
      const stats = handler.getStatistics();
      expect(stats.totalAssets).toBe(2);
    });

    it('should count assets by type', () => {
      const stats = handler.getStatistics();
      expect(stats.assetsByType.image).toBe(1);
      expect(stats.assetsByType.video).toBe(1);
    });

    it('should calculate total size', () => {
      const stats = handler.getStatistics();
      expect(stats.totalSize).toBe(3000);
    });

    it('should count total accesses', () => {
      const stats = handler.getStatistics();
      expect(stats.totalAccesses).toBe(8);
    });

    it('should count denied accesses', () => {
      const signed = handler.generateSignedUrl('asset-123', { ttl: -10 });
      handler.verifySignedUrl(signed.url);

      const stats = handler.getStatistics();
      expect(stats.deniedAccesses).toBeGreaterThan(0);
    });
  });

  describe('Cleanup', () => {
    it('should cleanup expired assets', () => {
      const now = Date.now();
      
      handler.storeMetadata({
        id: 'expired-1',
        type: 'image',
        filename: 'expired.jpg',
        contentType: 'image/jpeg',
        size: 1024,
        uploadedAt: now,
        uploadedBy: 'user-1',
        storagePath: '/uploads/expired.jpg',
        expiresAt: now - 1000, // Expired
      });

      handler.storeMetadata({
        id: 'active-1',
        type: 'image',
        filename: 'active.jpg',
        contentType: 'image/jpeg',
        size: 1024,
        uploadedAt: now,
        uploadedBy: 'user-1',
        storagePath: '/uploads/active.jpg',
        expiresAt: now + 10000, // Not expired
      });

      const cleaned = handler.cleanup();
      
      expect(cleaned).toBe(1);
      expect(handler.getMetadata('expired-1')).toBeUndefined();
      expect(handler.getMetadata('active-1')).toBeDefined();
    });

    it('should not cleanup assets without expiration', () => {
      handler.storeMetadata({
        id: 'permanent-1',
        type: 'image',
        filename: 'permanent.jpg',
        contentType: 'image/jpeg',
        size: 1024,
        uploadedAt: Date.now(),
        uploadedBy: 'user-1',
        storagePath: '/uploads/permanent.jpg',
      });

      const cleaned = handler.cleanup();
      
      expect(cleaned).toBe(0);
      expect(handler.getMetadata('permanent-1')).toBeDefined();
    });
  });

  describe('Edge Cases', () => {
    it('should handle missing metadata', () => {
      const metadata = handler.getMetadata('non-existent');
      expect(metadata).toBeUndefined();
    });

    it('should handle empty asset list', () => {
      const assets = handler.listAssets();
      expect(assets).toEqual([]);
    });

    it('should handle empty access logs', () => {
      const logs = handler.getAccessLogs();
      expect(logs).toEqual([]);
    });

    it('should handle zero statistics', () => {
      const stats = handler.getStatistics();
      expect(stats.totalAssets).toBe(0);
      expect(stats.totalSize).toBe(0);
      expect(stats.totalAccesses).toBe(0);
    });
  });
});
