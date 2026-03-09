import { ENGINEER_DEVSECOPS_FLOW, type DevSecOpsPhaseId, type FlowStep, resolveDevSecOpsRoute } from '@/config/devsecopsEngineerFlow';

function isInternalStep(step: FlowStep): boolean {
    return !step.route.startsWith('external:');
}

function findFirstInternalStepForPhase(phaseId: DevSecOpsPhaseId): FlowStep | undefined {
    return ENGINEER_DEVSECOPS_FLOW.steps.find(
        step => step.phaseId === phaseId && isInternalStep(step),
    );
}

export function getEngineerPhaseRouteForStory(
    phaseId: DevSecOpsPhaseId,
    storyId?: string,
): string | undefined {
    if (phaseId === 'intake') {
        const intakeStep = findFirstInternalStepForPhase('intake');
        if (intakeStep) {
            return resolveDevSecOpsRoute(intakeStep.route, { storyId });
        }
        return '/persona-dashboard';
    }

    if (phaseId === 'learn') {
        const learnStep = findFirstInternalStepForPhase('learn');
        if (learnStep) {
            return resolveDevSecOpsRoute(learnStep.route, { storyId });
        }
        const intakeStepFallback = findFirstInternalStepForPhase('intake');
        if (intakeStepFallback) {
            return resolveDevSecOpsRoute(intakeStepFallback.route, { storyId });
        }
        return '/persona-dashboard';
    }

    if (!storyId) {
        return undefined;
    }

    let effectivePhase: DevSecOpsPhaseId = phaseId;

    if (phaseId === 'build') {
        const buildStep = findFirstInternalStepForPhase('build');
        if (!buildStep || !isInternalStep(buildStep)) {
            effectivePhase = 'verify';
        }
    }

    const step = findFirstInternalStepForPhase(effectivePhase);
    if (!step) {
        return undefined;
    }

    return resolveDevSecOpsRoute(step.route, { storyId });
}
