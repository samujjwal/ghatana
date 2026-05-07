import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PhaseContextPanel } from '../PhaseContextPanel';
import type { LifecyclePhase } from '../../../shared/types/lifecycle';
import { LifecycleArtifactKind } from '../../../shared/types/lifecycle-artifacts';
import { generatePhaseAISuggestions } from '../../../services/ai/PhaseAIPromptService';

vi.mock('../../../services/ai/PhaseAIPromptService', async (importOriginal) => {
    const actual = await importOriginal<typeof import('../../../services/ai/PhaseAIPromptService')>();
    return {
        ...actual,
        generatePhaseAISuggestions: vi.fn(),
    };
});

const mockedGeneratePhaseAISuggestions = vi.mocked(generatePhaseAISuggestions);

describe('PhaseContextPanel', () => {
    beforeEach(() => {
        mockedGeneratePhaseAISuggestions.mockReset();
        mockedGeneratePhaseAISuggestions.mockResolvedValue([
            {
                type: 'create',
                priority: 'high',
                title: 'Draft requirements',
                description: 'Create the first requirement artifact.',
                artifactKind: LifecycleArtifactKind.REQUIREMENTS,
                reasoning: 'Requirements are needed for this phase.',
                estimatedEffort: '20 minutes',
            },
        ]);
    });

    it('uses design-system buttons for expand/collapse while preserving accessible state', async () => {
        render(
            <PhaseContextPanel
                phase={'SHAPE' as LifecyclePhase}
                existingArtifacts={[]}
            />,
        );

        const toggleButton = screen.getByRole('button', { name: /design & plan phase guide/i });
        expect(toggleButton).toHaveClass('gh-button');
        expect(toggleButton).toHaveAttribute('aria-expanded', 'true');

        fireEvent.click(toggleButton);

        expect(toggleButton).toHaveAttribute('aria-expanded', 'false');
        await waitFor(() => expect(mockedGeneratePhaseAISuggestions).toHaveBeenCalledOnce());
    });

    it('renders suggestion actions as design-system buttons and forwards action metadata', async () => {
        const onActionClick = vi.fn();

        render(
            <PhaseContextPanel
                phase={'SHAPE' as LifecyclePhase}
                existingArtifacts={[]}
                onActionClick={onActionClick}
            />,
        );

        const suggestionButton = await screen.findByRole('button', { name: /draft requirements/i });
        expect(suggestionButton).toHaveClass('gh-button');

        fireEvent.click(suggestionButton);

        expect(onActionClick).toHaveBeenCalledWith('Draft requirements', LifecycleArtifactKind.REQUIREMENTS);
    });
});
