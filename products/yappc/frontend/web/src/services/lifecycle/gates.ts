/**
 * Gate Service
 * 
 * Service for checking FOW stage gates and validating readiness.
 * Implements real gate logic based on artifacts and conditions.
 * 
 * @doc.type service
 * @doc.purpose Gate checking and validation
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import { FOWStage, FOWStageConfig, FOW_STAGE_CONFIGS, GateDefinition, GateResult, ArtifactType } from '@/types/fow-stages';
import { Artifact } from './api';

// ============================================================================
// Gate Service
// ============================================================================

export class GateService {
    /**
     * Check if a FOW stage gate can be passed
     */
    async checkGate(stage: FOWStage, artifacts: Artifact[]): Promise<GateResult> {
        const config = FOW_STAGE_CONFIGS[stage];
        const gate = config.gate;

        // Check required artifacts
        const artifactChecks = gate.requiredArtifacts.map(requirement => {
            const matchingArtifacts = artifacts.filter(artifact => {
                // Check type
                if (artifact.type !== requirement.type) return false;

                // Check status if required
                if (requirement.requiredStatus && artifact.status !== requirement.requiredStatus) {
                    return false;
                }

                return true;
            });

            return {
                type: requirement.type,
                required: requirement.minCount,
                current: matchingArtifacts.length,
                met: matchingArtifacts.length >= requirement.minCount,
            };
        });

        // Check custom conditions
        const failedConditions: string[] = [];
        if (gate.conditions) {
            for (const condition of gate.conditions) {
                try {
                    const result = await condition.check();
                    if (!result) {
                        failedConditions.push(condition.description);
                    }
                } catch (error) {
                    console.error(`Gate condition check failed: ${condition.id}`, error);
                    failedConditions.push(condition.description);
                }
            }
        }

        // Calculate overall readiness
        const totalRequirements = artifactChecks.length + (gate.conditions?.length || 0);
        const metRequirements = artifactChecks.filter(check => check.met).length;
        const metConditions = (gate.conditions?.length || 0) - failedConditions.length;
        const readiness = ((metRequirements + metConditions) / totalRequirements) * 100;

        // Can proceed if all requirements met
        const canProceed = readiness === 100;

        // Build missing artifacts list
        const missingArtifacts = artifactChecks
            .filter(check => !check.met)
            .map(check => ({
                type: check.type,
                required: check.required,
                current: check.current,
            }));

        return {
            readiness: Math.round(readiness),
            canProceed,
            missingArtifacts,
            failedConditions,
        };
    }

    /**
     * Check if an artifact satisfies gate requirements
     */
    doesArtifactSatisfyGate(artifact: Artifact, stage: FOWStage): boolean {
        const config = FOW_STAGE_CONFIGS[stage];
        const requirements = config.gate.requiredArtifacts;

        return requirements.some(req => {
            if (artifact.type !== req.type) return false;
            if (req.requiredStatus && artifact.status !== req.requiredStatus) return false;
            return true;
        });
    }

    /**
     * Get missing artifacts for a stage
     */
    getMissingArtifacts(stage: FOWStage, artifacts: Artifact[]): ArtifactType[] {
        const config = FOW_STAGE_CONFIGS[stage];
        const requirements = config.gate.requiredArtifacts;

        return requirements
            .filter(req => {
                const count = artifacts.filter(a =>
                    a.type === req.type &&
                    (!req.requiredStatus || a.status === req.requiredStatus)
                ).length;
                return count < req.minCount;
            })
            .map(req => req.type);
    }

    /**
     * Get percentage complete for a specific artifact type
     */
    getArtifactProgress(stage: FOWStage, artifactType: ArtifactType, artifacts: Artifact[]): number {
        const config = FOW_STAGE_CONFIGS[stage];
        const requirement = config.gate.requiredArtifacts.find(req => req.type === artifactType);

        if (!requirement) return 100; // Not required

        const count = artifacts.filter(a =>
            a.type === artifactType &&
            (!requirement.requiredStatus || a.status === requirement.requiredStatus)
        ).length;

        return Math.min(100, (count / requirement.minCount) * 100);
    }

    /**
     * Validate if transition to next stage is allowed
     */
    async canTransitionToStage(currentStage: FOWStage, targetStage: FOWStage, artifacts: Artifact[]): Promise<{
        allowed: boolean;
        reason?: string;
        gateResult?: GateResult;
    }> {
        // Can't skip stages
        if (targetStage !== currentStage + 1) {
            return {
                allowed: false,
                reason: 'Cannot skip stages. Must progress sequentially.',
            };
        }

        // Check if current stage gate is passed
        const gateResult = await this.checkGate(currentStage, artifacts);

        if (!gateResult.canProceed) {
            return {
                allowed: false,
                reason: `Current stage gate not passed. Readiness: ${gateResult.readiness}%`,
                gateResult,
            };
        }

        return {
            allowed: true,
            gateResult,
        };
    }

    /**
     * Get next required action for a stage
     */
    getNextRequiredAction(stage: FOWStage, artifacts: Artifact[]): {
        action: 'create-artifact' | 'approve-artifact' | 'complete-condition' | 'none';
        artifactType?: ArtifactType;
        description: string;
    } {
        const config = FOW_STAGE_CONFIGS[stage];
        const requirements = config.gate.requiredArtifacts;

        // Find first missing artifact
        for (const req of requirements) {
            const matchingArtifacts = artifacts.filter(a => a.type === req.type);

            // Not enough artifacts
            if (matchingArtifacts.length < req.minCount) {
                return {
                    action: 'create-artifact',
                    artifactType: req.type,
                    description: `Create ${req.type} (${matchingArtifacts.length}/${req.minCount})`,
                };
            }

            // Need approval
            if (req.requiredStatus === 'approved') {
                const approvedCount = matchingArtifacts.filter(a => a.status === 'approved').length;
                if (approvedCount < req.minCount) {
                    return {
                        action: 'approve-artifact',
                        artifactType: req.type,
                        description: `Approve ${req.type} (${approvedCount}/${req.minCount})`,
                    };
                }
            }
        }

        // Check conditions
        if (config.gate.conditions && config.gate.conditions.length > 0) {
            return {
                action: 'complete-condition',
                description: 'Complete additional gate conditions',
            };
        }

        return {
            action: 'none',
            description: 'All requirements met',
        };
    }
}

// ============================================================================
// Singleton instance
// ============================================================================

export const gateService = new GateService();
