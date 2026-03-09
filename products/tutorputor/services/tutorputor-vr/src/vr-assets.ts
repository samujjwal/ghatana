/**
 * @doc.type service
 * @doc.purpose VR Asset management service with S3 integration
 * @doc.layer product
 * @doc.pattern Service
 */

import { PrismaClient } from '@prisma/client';
import { v4 as uuidv4 } from 'uuid';
import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  DeleteObjectCommand,
} from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import type {
  VRAssetService,
  TenantId,
  UserId,
  PaginatedResult,
  PaginationArgs,
} from '@ghatana/tutorputor-contracts/v1';
import type { VRAsset, VRAssetId, VRAssetType } from '@ghatana/tutorputor-contracts/v1';

export interface VRAssetServiceConfig {
  s3Bucket: string;
  s3Region: string;
  s3AccessKeyId: string;
  s3SecretAccessKey: string;
  cdnUrl?: string;
}

export class VRAssetServiceImpl implements VRAssetService {
  private s3Client: S3Client;
  private bucket: string;
  private cdnUrl?: string;

  constructor(
    private prisma: PrismaClient,
    private config: VRAssetServiceConfig
  ) {
    this.s3Client = new S3Client({
      region: config.s3Region,
      credentials: {
        accessKeyId: config.s3AccessKeyId,
        secretAccessKey: config.s3SecretAccessKey,
      },
    });
    this.bucket = config.s3Bucket;
    this.cdnUrl = config.cdnUrl;
  }

  async uploadAsset(args: {
    tenantId: TenantId;
    userId: UserId;
    file: {
      name: string;
      type: string;
      size: number;
      data: Buffer | Uint8Array;
    };
    metadata: {
      tags?: string[];
      isPublic?: boolean;
    };
  }): Promise<VRAsset> {
    const { tenantId, userId, file, metadata } = args;

    const assetId = uuidv4();
    const assetType = this.determineAssetType(file.type);
    const key = `${tenantId}/vr-assets/${assetId}/${file.name}`;

    // Upload to S3
    await this.s3Client.send(
      new PutObjectCommand({
        Bucket: this.bucket,
        Key: key,
        Body: file.data,
        ContentType: file.type,
        Metadata: {
          tenantId,
          userId,
          originalName: file.name,
        },
      })
    );

    const url = this.cdnUrl
      ? `${this.cdnUrl}/${key}`
      : `https://${this.bucket}.s3.amazonaws.com/${key}`;

    // Create database record
    const asset = await this.prisma.vRAsset.create({
      data: {
        id: assetId,
        tenantId,
        name: file.name,
        type: assetType,
        url,
        size: file.size,
        format: this.getFileExtension(file.name),
        s3Key: key,
        tags: metadata.tags || [],
        isPublic: metadata.isPublic ?? false,
        createdBy: userId,
      },
    });

    return this.mapToVRAsset(asset);
  }

  async getAsset(args: {
    tenantId: TenantId;
    assetId: VRAssetId;
  }): Promise<VRAsset | null> {
    const { tenantId, assetId } = args;

    const asset = await this.prisma.vRAsset.findFirst({
      where: {
        id: assetId,
        OR: [{ tenantId }, { isPublic: true }],
      },
    });

    return asset ? this.mapToVRAsset(asset) : null;
  }

  async listAssets(args: {
    tenantId: TenantId;
    type?: string;
    search?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<VRAsset>> {
    const { tenantId, type, search, pagination } = args;
    const { page = 1, limit = 20 } = pagination;

    const where: any = {
      OR: [{ tenantId }, { isPublic: true }],
    };

    if (type) {
      where.type = type;
    }

    if (search) {
      where.AND = [
        {
          OR: [
            { name: { contains: search, mode: 'insensitive' } },
            { tags: { hasSome: [search] } },
          ],
        },
      ];
    }

    const [assets, total] = await Promise.all([
      this.prisma.vRAsset.findMany({
        where,
        skip: (page - 1) * limit,
        take: limit,
        orderBy: { createdAt: 'desc' },
      }),
      this.prisma.vRAsset.count({ where }),
    ]);

    return {
      items: assets.map((a) => this.mapToVRAsset(a)),
      total,
      page,
      limit,
      hasMore: page * limit < total,
    };
  }

  async deleteAsset(args: {
    tenantId: TenantId;
    assetId: VRAssetId;
    userId: UserId;
  }): Promise<void> {
    const { tenantId, assetId } = args;

    const asset = await this.prisma.vRAsset.findFirst({
      where: {
        id: assetId,
        tenantId,
      },
    });

    if (!asset) {
      throw new Error('Asset not found');
    }

    // Delete from S3
    await this.s3Client.send(
      new DeleteObjectCommand({
        Bucket: this.bucket,
        Key: asset.s3Key,
      })
    );

    // Delete from database
    await this.prisma.vRAsset.delete({
      where: { id: assetId },
    });
  }

  async getDownloadUrl(args: {
    tenantId: TenantId;
    assetId: VRAssetId;
  }): Promise<{ url: string; expiresAt: string }> {
    const { tenantId, assetId } = args;

    const asset = await this.prisma.vRAsset.findFirst({
      where: {
        id: assetId,
        OR: [{ tenantId }, { isPublic: true }],
      },
    });

    if (!asset) {
      throw new Error('Asset not found');
    }

    // If public and CDN configured, use direct URL
    if (asset.isPublic && this.cdnUrl) {
      return {
        url: asset.url,
        expiresAt: new Date(Date.now() + 3600000).toISOString(),
      };
    }

    // Generate signed URL
    const command = new GetObjectCommand({
      Bucket: this.bucket,
      Key: asset.s3Key,
    });

    const url = await getSignedUrl(this.s3Client, command, { expiresIn: 3600 });
    const expiresAt = new Date(Date.now() + 3600000).toISOString();

    return { url, expiresAt };
  }

  async getUploadUrl(args: {
    tenantId: TenantId;
    userId: UserId;
    fileName: string;
    contentType: string;
  }): Promise<{ url: string; assetId: VRAssetId; expiresAt: string }> {
    const { tenantId, userId, fileName, contentType } = args;

    const assetId = uuidv4();
    const key = `${tenantId}/vr-assets/${assetId}/${fileName}`;

    const command = new PutObjectCommand({
      Bucket: this.bucket,
      Key: key,
      ContentType: contentType,
      Metadata: {
        tenantId,
        userId,
        originalName: fileName,
      },
    });

    const url = await getSignedUrl(this.s3Client, command, { expiresIn: 3600 });
    const expiresAt = new Date(Date.now() + 3600000).toISOString();

    // Create pending asset record
    await this.prisma.vRAsset.create({
      data: {
        id: assetId,
        tenantId,
        name: fileName,
        type: this.determineAssetType(contentType),
        url: `https://${this.bucket}.s3.amazonaws.com/${key}`,
        size: 0, // Will be updated after upload
        format: this.getFileExtension(fileName),
        s3Key: key,
        tags: [],
        isPublic: false,
        createdBy: userId,
        status: 'pending',
      },
    });

    return { url, assetId, expiresAt };
  }

  // ============================================
  // Private helper methods
  // ============================================

  private determineAssetType(mimeType: string): VRAssetType {
    if (mimeType.startsWith('model/') || mimeType.includes('gltf') || mimeType.includes('glb')) {
      return 'model';
    }
    if (mimeType.startsWith('image/')) {
      return 'texture';
    }
    if (mimeType.startsWith('audio/')) {
      return 'audio';
    }
    if (mimeType.startsWith('video/')) {
      return 'video';
    }
    if (mimeType.includes('javascript') || mimeType.includes('json')) {
      return 'script';
    }
    return 'model';
  }

  private getFileExtension(fileName: string): string {
    const parts = fileName.split('.');
    return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : '';
  }

  private mapToVRAsset(asset: any): VRAsset {
    return {
      id: asset.id,
      name: asset.name,
      type: asset.type,
      url: asset.url,
      size: asset.size,
      format: asset.format,
      thumbnailUrl: asset.thumbnailUrl,
      tags: asset.tags,
      isPublic: asset.isPublic,
      createdAt: asset.createdAt.toISOString(),
      createdBy: asset.createdBy,
    };
  }
}
