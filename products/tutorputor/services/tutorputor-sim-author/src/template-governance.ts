/**
 * Template Governance Service
 * 
 * @doc.type module
 * @doc.purpose Manage simulation template review and approval workflow
 * @doc.layer product
 * @doc.pattern Service
 */

import type { TutorPrismaClient } from "@ghatana/tutorputor-db";
import type { SimulationTemplate } from "@ghatana/tutorputor-contracts/v1/simulation/types";

/**
 * Template review status.
 */
export type TemplateReviewStatus = 'draft' | 'submitted' | 'approved' | 'rejected' | 'deprecated';

/**
 * Template governance metadata.
 */
export interface TemplateGovernanceMetadata {
    reviewStatus: TemplateReviewStatus;
    reviewerNotes?: string;
    lastValidatedAt?: number;
    approvedBy?: string;
    version: string;
    changelog?: string;
}

/**
 * Template submission request.
 */
export interface SubmitTemplateRequest {
    templateId: string;
    tenantId: string;
    userId: string;
    notes?: string;
}

/**
 * Template review request.
 */
export interface ReviewTemplateRequest {
    templateId: string;
    reviewerId: string;
    action: 'approve' | 'reject';
    notes?: string;
    requiredChanges?: string[];
}

/**
 * Template review result.
 */
export interface TemplateReviewResult {
    templateId: string;
    status: TemplateReviewStatus;
    reviewedBy: string;
    reviewedAt: number;
    notes?: string;
}

/**
 * Template Governance Service.
 */
export class TemplateGovernanceService {
    constructor(private prisma: TutorPrismaClient) { }

    /**
     * Submit a template for review.
     */
    async submitForReview(request: SubmitTemplateRequest): Promise<TemplateReviewResult> {
        const { templateId, tenantId, userId, notes } = request;

        // Fetch template
        const template = await this.prisma.simulationTemplate.findUnique({
            where: { id: templateId }
        });

        if (!template) {
            throw new Error(`Template ${templateId} not found`);
        }

        if (template.tenantId !== tenantId) {
            throw new Error('Unauthorized: Template belongs to different tenant');
        }

        // Parse manifest
        const manifest = JSON.parse(template.manifest);

        // Update governance metadata
        const governance: TemplateGovernanceMetadata = {
            reviewStatus: 'submitted',
            reviewerNotes: notes,
            lastValidatedAt: Date.now(),
            version: template.version,
            changelog: notes
        };

        // Update template
        await this.prisma.simulationTemplate.update({
            where: { id: templateId },
            data: {
                manifest: JSON.stringify({
                    ...manifest,
                    governance
                }),
                updatedAt: new Date()
            }
        });

        return {
            templateId,
            status: 'submitted',
            reviewedBy: userId,
            reviewedAt: Date.now(),
            notes
        };
    }

    /**
     * Review a template (approve or reject).
     */
    async reviewTemplate(request: ReviewTemplateRequest): Promise<TemplateReviewResult> {
        const { templateId, reviewerId, action, notes, requiredChanges } = request;

        // Fetch template
        const template = await this.prisma.simulationTemplate.findUnique({
            where: { id: templateId }
        });

        if (!template) {
            throw new Error(`Template ${templateId} not found`);
        }

        // Parse manifest
        const manifest = JSON.parse(template.manifest);

        // Check current status
        if (manifest.governance?.reviewStatus !== 'submitted') {
            throw new Error('Template must be in submitted status to review');
        }

        // Update governance metadata
        const newStatus: TemplateReviewStatus = action === 'approve' ? 'approved' : 'rejected';
        const governance: TemplateGovernanceMetadata = {
            ...manifest.governance,
            reviewStatus: newStatus,
            reviewerNotes: notes,
            lastValidatedAt: Date.now(),
            approvedBy: action === 'approve' ? reviewerId : undefined
        };

        // If rejected, add required changes
        if (action === 'reject' && requiredChanges) {
            governance.reviewerNotes = `${notes || ''}\n\nRequired changes:\n${requiredChanges.map(c => `- ${c}`).join('\n')}`;
        }

        // Update template
        await this.prisma.simulationTemplate.update({
            where: { id: templateId },
            data: {
                manifest: JSON.stringify({
                    ...manifest,
                    governance
                }),
                updatedAt: new Date()
            }
        });

        // If approved, also update lifecycle status
        if (action === 'approve') {
            await this.prisma.simulationTemplate.update({
                where: { id: templateId },
                data: {
                    manifest: JSON.stringify({
                        ...manifest,
                        governance,
                        lifecycle: {
                            ...manifest.lifecycle,
                            status: 'published',
                            publishedAt: Date.now()
                        }
                    }),
                    updatedAt: new Date()
                }
            });
        }

        return {
            templateId,
            status: newStatus,
            reviewedBy: reviewerId,
            reviewedAt: Date.now(),
            notes
        };
    }

    /**
     * Get templates pending review.
     */
    async getPendingReviews(tenantId: string): Promise<SimulationTemplate[]> {
        const templates = await this.prisma.simulationTemplate.findMany({
            where: {
                tenantId
            }
        });

        // Filter for submitted status
        return templates
            .map(t => ({
                ...t,
                manifest: JSON.parse(t.manifest)
            }))
            .filter(t => t.manifest.governance?.reviewStatus === 'submitted')
            .map(t => t.manifest as SimulationTemplate);
    }

    /**
     * Get review history for a template.
     */
    async getReviewHistory(templateId: string): Promise<TemplateReviewResult[]> {
        // This would require a separate review history table
        // For now, return the current status
        const template = await this.prisma.simulationTemplate.findUnique({
            where: { id: templateId }
        });

        if (!template) {
            return [];
        }

        const manifest = JSON.parse(template.manifest);
        const governance = manifest.governance;

        if (!governance) {
            return [];
        }

        return [{
            templateId,
            status: governance.reviewStatus,
            reviewedBy: governance.approvedBy || 'unknown',
            reviewedAt: governance.lastValidatedAt || Date.now(),
            notes: governance.reviewerNotes
        }];
    }

    /**
     * Deprecate a template.
     */
    async deprecateTemplate(templateId: string, reason: string): Promise<void> {
        const template = await this.prisma.simulationTemplate.findUnique({
            where: { id: templateId }
        });

        if (!template) {
            throw new Error(`Template ${templateId} not found`);
        }

        const manifest = JSON.parse(template.manifest);

        // Update governance metadata
        const governance: TemplateGovernanceMetadata = {
            ...manifest.governance,
            reviewStatus: 'deprecated',
            reviewerNotes: reason,
            lastValidatedAt: Date.now()
        };

        // Update template
        await this.prisma.simulationTemplate.update({
            where: { id: templateId },
            data: {
                manifest: JSON.stringify({
                    ...manifest,
                    governance,
                    lifecycle: {
                        ...manifest.lifecycle,
                        status: 'archived'
                    }
                }),
                updatedAt: new Date()
            }
        });
    }

    /**
     * Get approved templates for a domain.
     */
    async getApprovedTemplates(
        tenantId: string,
        domain?: string
    ): Promise<SimulationTemplate[]> {
        const where: any = {
            tenantId
        };

        if (domain) {
            where.domain = domain;
        }

        const templates = await this.prisma.simulationTemplate.findMany({
            where
        });

        // Filter for approved status
        return templates
            .map(t => ({
                ...t,
                manifest: JSON.parse(t.manifest)
            }))
            .filter(t => t.manifest.governance?.reviewStatus === 'approved')
            .map(t => t.manifest as SimulationTemplate);
    }

    /**
     * Create a new version of a template.
     */
    async createTemplateVersion(
        templateId: string,
        userId: string,
        changes: Partial<SimulationTemplate>,
        changelog: string
    ): Promise<string> {
        const parentTemplate = await this.prisma.simulationTemplate.findUnique({
            where: { id: templateId }
        });

        if (!parentTemplate) {
            throw new Error(`Template ${templateId} not found`);
        }

        const parentManifest = JSON.parse(parentTemplate.manifest);

        // Increment version
        const versionParts = parentTemplate.version.split('.');
        const newVersion = `${versionParts[0]}.${parseInt(versionParts[1]) + 1}.0`;

        // Create new template
        const newManifest = {
            ...parentManifest,
            ...changes,
            version: newVersion,
            governance: {
                reviewStatus: 'draft' as const,
                version: newVersion,
                changelog
            }
        };

        const newTemplate = await this.prisma.simulationTemplate.create({
            data: {
                tenantId: parentTemplate.tenantId,
                authorId: userId,
                manifest: JSON.stringify(newManifest),
                domain: parentTemplate.domain,
                title: parentTemplate.title,
                version: newVersion,
                parentVersionId: templateId,
                createdAt: new Date(),
                updatedAt: new Date()
            }
        });

        return newTemplate.id;
    }
}

/**
 * Create template governance service.
 */
export function createTemplateGovernanceService(
    prisma: TutorPrismaClient
): TemplateGovernanceService {
    return new TemplateGovernanceService(prisma);
}
