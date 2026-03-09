/**
 * Media-related types shared across Flashit applications
 */

export type MediaType = 'AUDIO' | 'VIDEO' | 'IMAGE';
export type UploadStatus = 'PENDING' | 'UPLOADING' | 'COMPLETED' | 'FAILED';

export interface MediaReference {
    id: string;
    momentId: string;
    s3Bucket: string;
    s3Key: string;
    fileName: string;
    mimeType: string;
    sizeBytes: number;
    uploadStatus: UploadStatus;
    uploadedAt: string | null;
    expiresAt: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface UploadUrlRequest {
    fileName: string;
    mimeType: string;
    sizeBytes: number;
}

export interface UploadUrlResponse {
    uploadUrl: string;
    mediaReferenceId: string;
    expiresAt: string;
}

export interface ProgressiveUploadInitRequest {
    fileName: string;
    mimeType: string;
    totalSize: number;
    chunkSize: number;
}

export interface ProgressiveUploadInitResponse {
    uploadId: string;
    mediaReferenceId: string;
    chunkSize: number;
    totalChunks: number;
}

export interface ProgressiveUploadChunkRequest {
    uploadId: string;
    chunkIndex: number;
    data: Blob | Buffer;
}

export interface ProgressiveUploadCompleteRequest {
    uploadId: string;
}
