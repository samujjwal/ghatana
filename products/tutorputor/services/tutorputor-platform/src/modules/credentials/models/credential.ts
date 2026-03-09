/**
 * Credential Models
 *
 * Types and interfaces for simulation-based credentials and achievements.
 * Supports badges, certificates, and skill endorsements.
 */

import { z } from "zod";

// =============================================================================
// Enums and Constants
// =============================================================================

export const CredentialTypeValues = ["badge", "certificate", "skill", "achievement"] as const;
export type CredentialType = (typeof CredentialTypeValues)[number];

export const CredentialStatusValues = ["pending", "issued", "revoked", "expired"] as const;
export type CredentialStatus = (typeof CredentialStatusValues)[number];

export const AchievementCategoryValues = [
    "simulation_mastery",
    "domain_expertise",
    "learning_streak",
    "problem_solving",
    "collaboration",
    "speed_run",
    "perfectionist",
    "explorer",
] as const;
export type AchievementCategory = (typeof AchievementCategoryValues)[number];

export const SkillLevelValues = ["beginner", "intermediate", "advanced", "expert"] as const;
export type SkillLevel = (typeof SkillLevelValues)[number];

// =============================================================================
// Core Types
// =============================================================================

export interface Credential {
    id: string;
    type: CredentialType;
    status: CredentialStatus;
    userId: string;
    tenantId: string;

    // Credential details
    name: string;
    description: string;
    imageUrl?: string;
    metadata: CredentialMetadata;

    // Achievement specifics
    achievement?: AchievementDetails;
    skill?: SkillDetails;
    certificate?: CertificateDetails;

    // Verification
    issuer: IssuerInfo;
    verification: VerificationInfo;

    // Timestamps
    issuedAt: Date;
    expiresAt?: Date;
    revokedAt?: Date;
    createdAt: Date;
    updatedAt: Date;
}

export interface CredentialMetadata {
    category: AchievementCategory;
    points?: number;
    tier?: "bronze" | "silver" | "gold" | "platinum";
    rarity?: "common" | "uncommon" | "rare" | "epic" | "legendary";
    tags?: string[];
    customData?: Record<string, unknown>;
}

export interface AchievementDetails {
    simulationId?: string;
    simulationName?: string;
    domainPackId?: string;
    score?: number;
    maxScore?: number;
    completionTime?: number; // in seconds
    attempts?: number;
    criteria: AchievementCriteria[];
}

export interface AchievementCriteria {
    id: string;
    name: string;
    description: string;
    met: boolean;
    value?: number;
    threshold?: number;
}

export interface SkillDetails {
    skillId: string;
    skillName: string;
    level: SkillLevel;
    domain: string;
    subDomain?: string;
    endorsements?: SkillEndorsement[];
    assessmentResults?: AssessmentResult[];
}

export interface SkillEndorsement {
    endorserId: string;
    endorserName: string;
    endorserRole: string;
    endorsedAt: Date;
    comment?: string;
}

export interface AssessmentResult {
    assessmentId: string;
    score: number;
    maxScore: number;
    completedAt: Date;
}

export interface CertificateDetails {
    courseId?: string;
    courseName?: string;
    curriculum?: string;
    completionDate: Date;
    grade?: string;
    hoursCompleted?: number;
    instructorSignature?: string;
    organizationSignature?: string;
}

export interface IssuerInfo {
    id: string;
    name: string;
    url?: string;
    imageUrl?: string;
    type: "system" | "organization" | "instructor";
}

export interface VerificationInfo {
    type: "hash" | "signature" | "blockchain";
    hash?: string;
    signature?: string;
    publicKey?: string;
    verificationUrl?: string;
    chainId?: string;
    transactionId?: string;
}

// =============================================================================
// DTOs & Schemas
// =============================================================================

export interface IssueCredentialDTO {
    type: CredentialType;
    userId: string;
    tenantId: string;
    name: string;
    description: string;
    imageUrl?: string;
    metadata: Partial<CredentialMetadata>;
    achievement?: Partial<AchievementDetails>;
    skill?: Partial<SkillDetails>;
    certificate?: Partial<CertificateDetails>;
    expiresAt?: Date;
}


export const CredentialMetadataSchema = z.object({
    category: z.enum(AchievementCategoryValues),
    points: z.number().optional(),
    tier: z.enum(["bronze", "silver", "gold", "platinum"]).optional(),
    rarity: z.enum(["common", "uncommon", "rare", "epic", "legendary"]).optional(),
    tags: z.array(z.string()).optional(),
    customData: z.record(z.string(), z.unknown()).optional(),
});

export const IssueCredentialDTOSchema = z.object({
    type: z.enum(CredentialTypeValues),
    userId: z.string().min(1),
    tenantId: z.string().min(1),
    name: z.string().min(1).max(200),
    description: z.string().min(1).max(2000),
    imageUrl: z.string().url().optional(),
    metadata: CredentialMetadataSchema.partial(),
    achievement: z.object({
        simulationId: z.string().optional(),
        simulationName: z.string().optional(),
        domainPackId: z.string().optional(),
        score: z.number().optional(),
        maxScore: z.number().optional(),
        completionTime: z.number().optional(),
        attempts: z.number().optional(),
        criteria: z.array(
            z.object({
                id: z.string(),
                name: z.string(),
                description: z.string(),
                met: z.boolean(),
                value: z.number().optional(),
                threshold: z.number().optional(),
            })
        ).optional(),
    }).optional(),
    skill: z.object({
        skillId: z.string(),
        skillName: z.string(),
        level: z.enum(SkillLevelValues),
        domain: z.string(),
        subDomain: z.string().optional(),
    }).optional(),
    certificate: z.object({
        courseId: z.string().optional(),
        courseName: z.string().optional(),
        curriculum: z.string().optional(),
        completionDate: z.date().or(z.string().transform((s) => new Date(s))),
        grade: z.string().optional(),
        hoursCompleted: z.number().optional(),
    }).optional(),
    expiresAt: z.date().or(z.string().transform((s) => new Date(s))).optional(),
});

export const CredentialFilterSchema = z.object({
    userId: z.string().optional(),
    tenantId: z.string().optional(),
    type: z.enum(CredentialTypeValues).optional(),
    status: z.enum(CredentialStatusValues).optional(),
    category: z.enum(AchievementCategoryValues).optional(),
    simulationId: z.string().optional(),
    domainPackId: z.string().optional(),
    issuedAfter: z.date().or(z.string().transform((s) => new Date(s))).optional(),
    issuedBefore: z.date().or(z.string().transform((s) => new Date(s))).optional(),
    page: z.number().int().positive().default(1),
    limit: z.number().int().positive().max(100).default(20),
});

export type CredentialFilter = z.infer<typeof CredentialFilterSchema>;

// Summary Types
export interface CredentialSummary {
    total: number;
    valid: number;
    expired: number;
    revoked: number;
    byType: {
        badges: number;
        achievements: number;
        skills: number;
        certificates: number;
    };
    totalPoints: number;
    byTier: {
        bronze: number;
        silver: number;
        gold: number;
        platinum: number;
    };
}

// =============================================================================
// Function Logic
// =============================================================================

export function createCredential(dto: IssueCredentialDTO): Credential {
    const now = new Date();
    const id = `cred_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;

    const verificationHash = generateVerificationHash(id, dto.userId, dto.tenantId, now);

    return {
        id,
        type: dto.type,
        status: "issued",
        userId: dto.userId,
        tenantId: dto.tenantId,
        name: dto.name,
        description: dto.description,
        imageUrl: dto.imageUrl,
        metadata: {
            category: dto.metadata.category || "simulation_mastery",
            points: dto.metadata.points,
            tier: dto.metadata.tier,
            rarity: dto.metadata.rarity,
            tags: dto.metadata.tags,
            customData: dto.metadata.customData,
        },
        achievement: dto.achievement ? {
            simulationId: dto.achievement.simulationId,
            simulationName: dto.achievement.simulationName,
            domainPackId: dto.achievement.domainPackId,
            score: dto.achievement.score,
            maxScore: dto.achievement.maxScore,
            completionTime: dto.achievement.completionTime,
            attempts: dto.achievement.attempts,
            criteria: dto.achievement.criteria || [],
        } : undefined,
        skill: dto.skill ? {
            skillId: dto.skill.skillId || "",
            skillName: dto.skill.skillName || "",
            level: dto.skill.level || "beginner",
            domain: dto.skill.domain || "",
            subDomain: dto.skill.subDomain,
            endorsements: [],
            assessmentResults: [],
        } : undefined,
        certificate: dto.certificate ? {
            courseId: dto.certificate.courseId,
            courseName: dto.certificate.courseName,
            curriculum: dto.certificate.curriculum,
            completionDate: dto.certificate.completionDate instanceof Date ? dto.certificate.completionDate : new Date(dto.certificate.completionDate || now),
            grade: dto.certificate.grade,
            hoursCompleted: dto.certificate.hoursCompleted,
        } : undefined,
        issuer: {
            id: "tutorputor_system",
            name: "Tutorputor",
            type: "system",
        },
        verification: {
            type: "hash",
            hash: verificationHash,
            verificationUrl: `https://verify.tutorputor.com/credentials/${id}`,
        },
        issuedAt: now,
        expiresAt: dto.expiresAt,
        createdAt: now,
        updatedAt: now,
    };
}

function generateVerificationHash(credentialId: string, userId: string, tenantId: string, timestamp: Date): string {
    const data = `${credentialId}:${userId}:${tenantId}:${timestamp.toISOString()}`;
    let hash = 0;
    for (let i = 0; i < data.length; i++) {
        const char = data.charCodeAt(i);
        hash = (hash << 5) - hash + char;
        hash = hash & hash;
    }
    return Math.abs(hash).toString(16).padStart(16, "0");
}

export function isCredentialValid(credential: Credential): boolean {
    if (credential.status !== "issued") return false;
    if (credential.expiresAt && credential.expiresAt < new Date()) return false;
    return true;
}

export function calculateCredentialValue(credential: Credential): number {
    let value = 0;
    if (credential.type === "badge") value = 10;
    else if (credential.type === "achievement") value = 25;
    else if (credential.type === "skill") value = 50;
    else if (credential.type === "certificate") value = 100;

    if (credential.metadata.tier === "silver") value *= 1.5;
    else if (credential.metadata.tier === "gold") value *= 2;
    else if (credential.metadata.tier === "platinum") value *= 3;

    if (credential.metadata.rarity === "uncommon") value *= 1.25;
    else if (credential.metadata.rarity === "rare") value *= 1.5;
    else if (credential.metadata.rarity === "epic") value *= 2;
    else if (credential.metadata.rarity === "legendary") value *= 3;

    if (credential.metadata.points) value += credential.metadata.points;
    return Math.round(value);
}

export function getCredentialSummary(credentials: Credential[]): CredentialSummary {
    const valid = credentials.filter(isCredentialValid);

    return {
        total: credentials.length,
        valid: valid.length,
        expired: credentials.filter((c) => c.expiresAt && c.expiresAt < new Date()).length,
        revoked: credentials.filter((c) => c.status === "revoked").length,
        byType: {
            badges: credentials.filter((c) => c.type === "badge").length,
            achievements: credentials.filter((c) => c.type === "achievement").length,
            skills: credentials.filter((c) => c.type === "skill").length,
            certificates: credentials.filter((c) => c.type === "certificate").length,
        },
        totalPoints: valid.reduce((sum, c) => sum + calculateCredentialValue(c), 0),
        byTier: {
            bronze: credentials.filter((c) => c.metadata.tier === "bronze").length,
            silver: credentials.filter((c) => c.metadata.tier === "silver").length,
            gold: credentials.filter((c) => c.metadata.tier === "gold").length,
            platinum: credentials.filter((c) => c.metadata.tier === "platinum").length,
        },
    };
}
