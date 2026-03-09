/**
 * Evidence Entity
 *
 * Proof of compliance control implementation or satisfaction.
 * Supports document uploads, screenshots, audit logs, etc.
 *
 * @see EvidenceUploadService for upload operations
 */

export enum EvidenceType {
  DOCUMENT = 'DOCUMENT',
  SCREENSHOT = 'SCREENSHOT',
  LOG = 'LOG',
  CERTIFICATE = 'CERTIFICATE',
  AUDIT_RESULT = 'AUDIT_RESULT',
  POLICY = 'POLICY',
  PROCEDURE = 'PROCEDURE',
}

export enum EvidenceStatus {
  PENDING_REVIEW = 'PENDING_REVIEW',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
  EXPIRED = 'EXPIRED',
}

export interface IEvidence {
  id: string;
  controlId: string;
  type: EvidenceType;
  title: string;
  description?: string;
  fileUrl: string;
  fileName: string;
  fileSize: number;
  uploadedBy: string;
  uploadedAt: Date;
  status: EvidenceStatus;
  reviewedBy?: string;
  reviewedAt?: Date;
  reviewComment?: string;
  expiresAt?: Date;
}

/**
 * Evidence entity class
 *
 * GIVEN: Evidence uploaded by user
 * WHEN: Associated with control
 * THEN: Status tracked, reviewed, approved for compliance
 *
 * Database: PostgreSQL table 'evidence'
 * File storage: S3/GCS with versioning
 */
export class Evidence implements IEvidence {
  id: string;
  controlId: string;
  type: EvidenceType;
  title: string;
  description?: string;
  fileUrl: string;
  fileName: string;
  fileSize: number;
  uploadedBy: string;
  uploadedAt: Date;
  status: EvidenceStatus;
  reviewedBy?: string;
  reviewedAt?: Date;
  reviewComment?: string;
  expiresAt?: Date;

  constructor(
    id: string,
    controlId: string,
    type: EvidenceType,
    title: string,
    fileUrl: string,
    fileName: string,
    fileSize: number,
    uploadedBy: string
  ) {
    this.id = id;
    this.controlId = controlId;
    this.type = type;
    this.title = title;
    this.fileUrl = fileUrl;
    this.fileName = fileName;
    this.fileSize = fileSize;
    this.uploadedBy = uploadedBy;
    this.uploadedAt = new Date();
    this.status = EvidenceStatus.PENDING_REVIEW;
  }

  /**
   * Approve evidence
   */
  approve(reviewer: string, comment?: string): void {
    this.status = EvidenceStatus.APPROVED;
    this.reviewedBy = reviewer;
    this.reviewedAt = new Date();
    this.reviewComment = comment;
  }

  /**
   * Reject evidence
   */
  reject(reviewer: string, comment: string): void {
    this.status = EvidenceStatus.REJECTED;
    this.reviewedBy = reviewer;
    this.reviewedAt = new Date();
    this.reviewComment = comment;
  }

  /**
   * Set expiration
   */
  setExpiration(daysValid: number): void {
    this.expiresAt = new Date(Date.now() + daysValid * 24 * 60 * 60 * 1000);
  }

  /**
   * Is evidence expired?
   */
  isExpired(): boolean {
    if (!this.expiresAt) return false;
    return this.expiresAt < new Date();
  }

  /**
   * Mark as expired
   */
  markExpired(): void {
    this.status = EvidenceStatus.EXPIRED;
  }

  /**
   * Validate evidence
   */
  validate(): boolean {
    if (!this.id || !this.controlId || !this.title || !this.fileUrl) {
      throw new Error('Invalid evidence: required fields missing');
    }
    if (this.fileSize <= 0) {
      throw new Error('Invalid evidence: file size must be positive');
    }
    return true;
  }

  /**
   * Convert to JSON
   */
  toJSON(): Record<string, unknown> {
    return {
      id: this.id,
      controlId: this.controlId,
      type: this.type,
      title: this.title,
      description: this.description,
      fileUrl: this.fileUrl,
      fileName: this.fileName,
      fileSize: this.fileSize,
      uploadedBy: this.uploadedBy,
      uploadedAt: this.uploadedAt.toISOString(),
      status: this.status,
      reviewedBy: this.reviewedBy,
      reviewedAt: this.reviewedAt?.toISOString(),
      reviewComment: this.reviewComment,
      expiresAt: this.expiresAt?.toISOString(),
    };
  }

  /**
   * Create from JSON
   */
  static fromJSON(data: Partial<Evidence>): Evidence {
    const evidence = new Evidence(
      data.id || '',
      data.controlId || '',
      data.type || EvidenceType.DOCUMENT,
      data.title || '',
      data.fileUrl || '',
      data.fileName || '',
      data.fileSize || 0,
      data.uploadedBy || ''
    );
    if (data.description) evidence.description = data.description;
    if (data.status) evidence.status = data.status;
    if (data.reviewedBy) evidence.reviewedBy = data.reviewedBy;
    if (data.reviewedAt) evidence.reviewedAt = new Date(data.reviewedAt);
    if (data.reviewComment) evidence.reviewComment = data.reviewComment;
    if (data.expiresAt) evidence.expiresAt = new Date(data.expiresAt);
    return evidence;
  }
}
