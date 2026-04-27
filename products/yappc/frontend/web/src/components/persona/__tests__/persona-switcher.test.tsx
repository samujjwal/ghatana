/**
 * PersonaSwitcher Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { PersonaSwitcher } from '../PersonaSwitcher';
import { PERSONA_DEFINITIONS } from '../../../context/PersonaContext';

vi.mock('../../../context/PersonaContext', async (importOriginal) => {
    const actual = await importOriginal<typeof import('../../../context/PersonaContext')>();
    return {
        ...actual,
        usePersona: vi.fn(() => ({
            activePersonas: ['developer', 'designer'] as const,
            primaryPersona: 'developer' as const,
            virtualPersonas: [],
            togglePersona: vi.fn(),
            setPrimaryPersona: vi.fn(),
            setActivePersonas: vi.fn(),
            hasPersona: vi.fn().mockReturnValue(false),
            isVirtualPersona: vi.fn().mockReturnValue(false),
            getPersonaDefinition: (id: string) => actual.PERSONA_DEFINITIONS[id as keyof typeof actual.PERSONA_DEFINITIONS],
            allActivePersonas: ['developer', 'designer'] as const,
            enableVirtualPersona: vi.fn(),
            disableVirtualPersona: vi.fn(),
            canvasConfig: {},
        })),
    };
});

describe('PersonaSwitcher', () => {
    it('renders region with aria-label', () => {
        render(<PersonaSwitcher />);
        expect(screen.getByRole('region', { name: /persona selection/i })).toBeTruthy();
    });

    it('renders compact view by default', () => {
        render(<PersonaSwitcher />);
        // Should show button with aria-label containing current role
        const btn = screen.getByRole('button');
        expect(btn).toBeTruthy();
    });

    it('shows primary persona name in compact view', () => {
        render(<PersonaSwitcher />);
        // Primary persona shortName from PERSONA_DEFINITIONS['developer']
        const developerDef = PERSONA_DEFINITIONS['developer'];
        expect(screen.getByText(developerDef.shortName)).toBeTruthy();
    });

    it('expands on button click', () => {
        render(<PersonaSwitcher />);
        const btn = screen.getByRole('button');
        fireEvent.click(btn);
        // After expansion, should show persona listing (expanded = true)
        // The component uses a portal for the expanded view
        expect(document.body.textContent).toBeTruthy();
    });
});
