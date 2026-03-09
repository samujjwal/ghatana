import { describe, it, expect } from 'vitest';
import { WORKSPACE_PRESETS, getWorkspacePreset, getWorkspaceOnboarding, getWorkspaceTip } from '@/config/workspacePresets';
import type { PersonaId } from '@/shared/types/org';

const PERSONAS: PersonaId[] = ['engineer', 'lead', 'sre', 'security', 'admin', 'viewer'];

describe('workspacePresets configuration', () => {
    it('defines presets for all personas', () => {
        expect(Object.keys(WORKSPACE_PRESETS).sort()).toEqual(PERSONAS.sort());

        for (const personaId of PERSONAS) {
            const preset = WORKSPACE_PRESETS[personaId];
            expect(preset).toBeDefined();
            expect(preset.personaId).toBe(personaId);
            expect(preset.displayName).toMatch(/Workspace/i);
            expect(preset.onboarding.dismissalKey).toContain(`softwareOrg.workspace.${personaId}.onboarding.dismissed`);
            expect(preset.workflowSteps.length).toBeGreaterThan(0);
            expect(preset.metricHighlights.length).toBeGreaterThan(0);
        }
    });

    it('getWorkspacePreset returns preset for known persona and undefined for unknown', () => {
        const engineer = getWorkspacePreset('engineer');
        expect(engineer).toBeDefined();
        expect(engineer?.personaId).toBe('engineer');

        const unknown = getWorkspacePreset('unknown-persona');
        expect(unknown).toBeUndefined();
    });

    it('getWorkspaceOnboarding returns onboarding configuration', () => {
        const onboarding = getWorkspaceOnboarding('admin');
        expect(onboarding).toBeDefined();
        expect(onboarding?.title).toMatch(/Admin Workspace/i);
        expect(onboarding?.primaryCtaHref).toBeTruthy();
    });

    it('getWorkspaceTip returns a tip from the preset list or a default tip', () => {
        const personaId: PersonaId = 'security';
        const preset = getWorkspacePreset(personaId)!;

        const tip = getWorkspaceTip(personaId);
        expect(typeof tip).toBe('string');
        expect(tip.length).toBeGreaterThan(0);
        expect(preset.tips.includes(tip) || tip === 'Explore the platform to discover all available features.').toBe(true);
    });
});
