export interface DataExportRequest {
    id: string;
    tenantId: string;
    userId: string;
    userEmail: string;
    requestType: 'export' | 'deletion';
    status: DataRequestStatus;
    requestedAt: Date;
    processedAt?: Date;
    completedAt?: Date;
    expiresAt?: Date;
    downloadUrl?: string;
    errorMessage?: string;
    metadata?: Record<string, unknown>;
}

export type DataRequestStatus =
    | 'pending'
    | 'processing'
    | 'completed'
    | 'failed'
    | 'cancelled';

export interface DataExportResult {
    requestId: string;
    status: DataRequestStatus;
    downloadUrl?: string;
    expiresAt?: Date;
    files?: ExportedFile[];
}

export interface ExportedFile {
    name: string;
    type: string;
    sizeBytes: number;
    recordCount: number;
}

export interface DataDeletionResult {
    requestId: string;
    status: DataRequestStatus;
    deletedAt?: Date;
    retainedData?: RetainedDataInfo[];
    errorMessage?: string;
}

export interface RetainedDataInfo {
    dataType: string;
    reason: string;
    retentionPeriod: string;
}

export interface ComplianceReport {
    reportType: 'gdpr' | 'coppa' | 'ferpa' | 'ccpa';
    generatedAt: Date;
    tenantId: string;
    data: {
        totalUsers: number;
        activeDataRequests: number;
        completedRequests: number;
        averageResponseTime: number;
        consentSettings: Record<string, boolean>;
        dataRetentionPolicies: DataRetentionPolicy[];
    };
}

export interface DataRetentionPolicy {
    dataType: string;
    retentionDays: number;
    purgeStrategy: 'delete' | 'anonymize';
    lastPurgeAt?: Date;
}

export interface ConsentRecord {
    id: string;
    userId: string;
    tenantId: string;
    consentType: string;
    granted: boolean;
    grantedAt?: Date;
    revokedAt?: Date;
    ipAddress?: string;
    userAgent?: string;
}
