/**
 * Storage Service
 * Abstraction layer for file storage supporting both local and cloud (S3) storage
 */

import { S3Client, PutObjectCommand, GetObjectCommand, DeleteObjectCommand, HeadObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { promises as fs } from 'fs';
import { join } from 'path';
import { randomUUID } from 'crypto';

export type StorageProvider = 'local' | 's3';

export interface StorageConfig {
  provider: StorageProvider;
  local?: {
    basePath: string;
    baseUrl: string;
  };
  s3?: {
    bucket: string;
    region: string;
    endpoint?: string; // For local S3 (MinIO, LocalStack)
    accessKeyId?: string;
    secretAccessKey?: string;
    forcePathStyle?: boolean; // Required for MinIO
  };
}

export interface UploadOptions {
  fileName: string;
  contentType: string;
  metadata?: Record<string, string>;
}

export interface PresignedUrlOptions {
  fileName: string;
  contentType: string;
  expiresIn?: number; // seconds
}

export class StorageService {
  private config: StorageConfig;
  private s3Client?: S3Client;

  constructor(config: StorageConfig) {
    this.config = config;

    if (config.provider === 's3' && config.s3) {
      this.s3Client = new S3Client({
        region: config.s3.region,
        endpoint: config.s3.endpoint,
        credentials: config.s3.accessKeyId && config.s3.secretAccessKey ? {
          accessKeyId: config.s3.accessKeyId,
          secretAccessKey: config.s3.secretAccessKey,
        } : undefined,
        forcePathStyle: config.s3.forcePathStyle,
      });
    }
  }

  /**
   * Generate a presigned URL for direct upload
   */
  async generatePresignedUrl(options: PresignedUrlOptions): Promise<{
    uploadUrl: string;
    fileKey: string;
    expiresIn: number;
  }> {
    const fileKey = this.generateFileKey(options.fileName);
    const expiresIn = options.expiresIn || 900; // 15 minutes default

    if (this.config.provider === 's3' && this.s3Client && this.config.s3) {
      const command = new PutObjectCommand({
        Bucket: this.config.s3.bucket,
        Key: fileKey,
        ContentType: options.contentType,
      });

      const uploadUrl = await getSignedUrl(this.s3Client, command, { expiresIn });

      return {
        uploadUrl,
        fileKey,
        expiresIn,
      };
    } else if (this.config.provider === 'local' && this.config.local) {
      // For local storage, return a URL to our upload endpoint
      return {
        uploadUrl: `${this.config.local.baseUrl}/api/upload/local/${fileKey}`,
        fileKey,
        expiresIn,
      };
    }

    throw new Error('Storage provider not configured');
  }

  /**
   * Upload a file directly
   */
  async uploadFile(fileKey: string, buffer: Buffer, options: UploadOptions): Promise<void> {
    if (this.config.provider === 's3' && this.s3Client && this.config.s3) {
      await this.s3Client.send(new PutObjectCommand({
        Bucket: this.config.s3.bucket,
        Key: fileKey,
        Body: buffer,
        ContentType: options.contentType,
        Metadata: options.metadata,
      }));
    } else if (this.config.provider === 'local' && this.config.local) {
      const filePath = join(this.config.local.basePath, fileKey);
      const dir = join(this.config.local.basePath, fileKey.split('/').slice(0, -1).join('/'));
      
      await fs.mkdir(dir, { recursive: true });
      await fs.writeFile(filePath, buffer);
    } else {
      throw new Error('Storage provider not configured');
    }
  }

  /**
   * Get a file
   */
  async getFile(fileKey: string): Promise<Buffer> {
    if (this.config.provider === 's3' && this.s3Client && this.config.s3) {
      const response = await this.s3Client.send(new GetObjectCommand({
        Bucket: this.config.s3.bucket,
        Key: fileKey,
      }));

      return Buffer.from(await response.Body!.transformToByteArray());
    } else if (this.config.provider === 'local' && this.config.local) {
      const filePath = join(this.config.local.basePath, fileKey);
      return await fs.readFile(filePath);
    }

    throw new Error('Storage provider not configured');
  }

  /**
   * Delete a file
   */
  async deleteFile(fileKey: string): Promise<void> {
    if (this.config.provider === 's3' && this.s3Client && this.config.s3) {
      await this.s3Client.send(new DeleteObjectCommand({
        Bucket: this.config.s3.bucket,
        Key: fileKey,
      }));
    } else if (this.config.provider === 'local' && this.config.local) {
      const filePath = join(this.config.local.basePath, fileKey);
      await fs.unlink(filePath);
    } else {
      throw new Error('Storage provider not configured');
    }
  }

  /**
   * Check if a file exists
   */
  async fileExists(fileKey: string): Promise<boolean> {
    try {
      if (this.config.provider === 's3' && this.s3Client && this.config.s3) {
        await this.s3Client.send(new HeadObjectCommand({
          Bucket: this.config.s3.bucket,
          Key: fileKey,
        }));
        return true;
      } else if (this.config.provider === 'local' && this.config.local) {
        const filePath = join(this.config.local.basePath, fileKey);
        await fs.access(filePath);
        return true;
      }
    } catch {
      return false;
    }

    return false;
  }

  /**
   * Get a public URL for a file
   */
  getPublicUrl(fileKey: string): string {
    if (this.config.provider === 's3' && this.config.s3) {
      if (this.config.s3.endpoint) {
        return `${this.config.s3.endpoint}/${this.config.s3.bucket}/${fileKey}`;
      }
      return `https://${this.config.s3.bucket}.s3.${this.config.s3.region}.amazonaws.com/${fileKey}`;
    } else if (this.config.provider === 'local' && this.config.local) {
      return `${this.config.local.baseUrl}/api/files/${fileKey}`;
    }

    throw new Error('Storage provider not configured');
  }

  /**
   * Generate a unique file key
   */
  private generateFileKey(fileName: string): string {
    const timestamp = Date.now();
    const uuid = randomUUID();
    const ext = fileName.split('.').pop();
    const baseName = fileName.split('.').slice(0, -1).join('.');
    const sanitizedName = baseName.replace(/[^a-zA-Z0-9-_]/g, '_');
    
    return `uploads/${timestamp}/${uuid}/${sanitizedName}.${ext}`;
  }
}

// Singleton instance
let storageService: StorageService | null = null;

export function getStorageService(): StorageService {
  if (!storageService) {
    const provider = (process.env.STORAGE_PROVIDER || 'local') as StorageProvider;
    
    const config: StorageConfig = {
      provider,
      local: provider === 'local' ? {
        basePath: process.env.LOCAL_STORAGE_PATH || '/tmp/flashit-uploads',
        baseUrl: process.env.API_BASE_URL || 'http://localhost:8000',
      } : undefined,
      s3: provider === 's3' ? {
        bucket: process.env.S3_BUCKET || 'flashit-media',
        region: process.env.AWS_REGION || 'us-east-1',
        endpoint: process.env.S3_ENDPOINT, // For MinIO/LocalStack
        accessKeyId: process.env.AWS_ACCESS_KEY_ID,
        secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
        forcePathStyle: process.env.S3_FORCE_PATH_STYLE === 'true',
      } : undefined,
    };

    storageService = new StorageService(config);
  }

  return storageService;
}
