/**
 * Moment-related types shared across Flashit applications
 */

export type ContentType = 'TEXT' | 'VOICE' | 'VIDEO' | 'IMAGE' | 'MIXED';

export interface MomentContent {
    text: string;
    transcript?: string;
    type: ContentType;
}

export interface MomentSignals {
    emotions?: string[];
    tags?: string[];
    intent?: string;
    sentimentScore?: number;
    importance?: number;
    entities?: string[];
}

export interface Moment {
    id: string;
    userId: string;
    sphereId: string;
    contentText: string;
    contentTranscript: string | null;
    contentType: ContentType;
    emotions: string[];
    tags: string[];
    intent: string | null;
    sentimentScore: number | null;
    importance: number | null;
    entities: string[];
    capturedAt: string;
    ingestedAt: string;
    updatedAt: string;
    deletedAt: string | null;
    metadata: Record<string, unknown> | null;
    version: number;
}

export interface CreateMomentRequest {
    sphereId?: string;
    content: MomentContent;
    signals?: MomentSignals;
    metadata?: Record<string, unknown>;
    capturedAt?: string;
}

export interface UpdateMomentRequest {
    content?: Partial<MomentContent>;
    signals?: Partial<MomentSignals>;
    metadata?: Record<string, unknown>;
}

export interface SearchMomentsRequest {
    sphereIds?: string[];
    query?: string;
    tags?: string[];
    emotions?: string[];
    startDate?: string;
    endDate?: string;
    limit?: number;
    cursor?: string;
}

export interface SearchMomentsResponse {
    moments: Moment[];
    nextCursor: string | null;
    total: number;
}

export interface ClassifySphereRequest {
    content: MomentContent;
    signals?: MomentSignals;
}

export interface ClassifySphereResponse {
    sphereId: string;
    confidence: number;
    reasoning: string;
}
