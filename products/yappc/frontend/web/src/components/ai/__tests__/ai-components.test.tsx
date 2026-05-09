/**
 * Unit tests for AI overlay and action components:
 * - AILabelOverlay
 * - NextBestAction
 * - CommandInput
 * - RecentProjectsStrip
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { AILabelOverlay } from '../AILabelOverlay';
import { NextBestAction, type NextAction } from '../NextBestAction';
import { CommandInput } from '../CommandInput';
import { RecentProjectsStrip } from '../RecentProjectsStrip';
import type { Project } from '../../../state/atoms/workspaceAtom';
import type { LifecyclePhase } from '../../../shared/types/lifecycle';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeAction(overrides: Partial<NextAction> = {}): NextAction {
    return {
        id: 'action-1',
        title: 'Deploy to staging',
        description: 'Deploy the current build to the staging environment',
        type: 'recommended',
        impact: 'high',
        onAction: vi.fn(),
        ...overrides,
    };
}

function makeProject(overrides: Partial<Project> = {}): Project {
    return {
        id: 'proj-1',
        name: 'Test Project',
        type: 'web',
        lifecyclePhase: 'PLAN' as LifecyclePhase,
        ...overrides,
    } as Project;
}

// ---------------------------------------------------------------------------
// AILabelOverlay
// ---------------------------------------------------------------------------

describe('AILabelOverlay', () => {
    it('renders AI label', () => {
        render(<AILabelOverlay />);
        expect(screen.getByTestId('ai-label')).toBeTruthy();
        expect(screen.getByText('AI')).toBeTruthy();
    });

    it('shows sparkle icon by default', () => {
        const { container } = render(<AILabelOverlay showIcon />);
        // The sparkle icon renders as an svg inside the label
        expect(container.querySelector('svg')).toBeTruthy();
    });

    it('hides icon when showIcon is false', () => {
        const { container } = render(<AILabelOverlay showIcon={false} />);
        // svg (sparkle) should not be present
        expect(container.querySelector('svg')).toBeNull();
    });

    it('applies sm size style', () => {
        render(<AILabelOverlay size="sm" />);
        const label = screen.getByTestId('ai-label');
        expect(label.className).toContain('px-1.5');
    });

    it('applies lg size style', () => {
        render(<AILabelOverlay size="lg" />);
        const label = screen.getByTestId('ai-label');
        expect(label.className).toContain('px-3');
    });

    it('applies subtle variant style', () => {
        render(<AILabelOverlay variant="subtle" />);
        const label = screen.getByTestId('ai-label');
        expect(label.className).toContain('bg-info-bg/50');
    });

    it('applies emphasis variant style', () => {
        render(<AILabelOverlay variant="emphasis" />);
        const label = screen.getByTestId('ai-label');
        expect(label.className).toContain('bg-info-bg');
    });

    it('applies border variant style', () => {
        render(<AILabelOverlay variant="border" />);
        const label = screen.getByTestId('ai-label');
        expect(label.className).toContain('bg-white');
    });

    it('sets tooltip via title attribute', () => {
        render(<AILabelOverlay tooltip="AI-generated content" />);
        const label = screen.getByTestId('ai-label');
        expect(label.title).toBe('AI-generated content');
    });

    it('wraps in button when onClick provided', () => {
        const handleClick = vi.fn();
        render(<AILabelOverlay onClick={handleClick} />);
        const btn = screen.getByRole('button');
        fireEvent.click(btn);
        expect(handleClick).toHaveBeenCalledTimes(1);
    });

    it('does not render a button when onClick is absent', () => {
        render(<AILabelOverlay />);
        expect(screen.queryByRole('button')).toBeNull();
    });
});

// ---------------------------------------------------------------------------
// NextBestAction
// ---------------------------------------------------------------------------

describe('NextBestAction', () => {
    it('renders action title', () => {
        render(<NextBestAction action={makeAction()} />);
        expect(screen.getByText('Deploy to staging')).toBeTruthy();
    });

    it('renders action description', () => {
        render(<NextBestAction action={makeAction()} />);
        expect(screen.getByText('Deploy the current build to the staging environment')).toBeTruthy();
    });

    it('renders action type chip', () => {
        render(<NextBestAction action={makeAction({ type: 'immediate' })} />);
        expect(screen.getByText('immediate')).toBeTruthy();
    });

    it('renders impact chip when showImpact is true', () => {
        render(<NextBestAction action={makeAction({ impact: 'high' })} showImpact />);
        expect(screen.getByText(/high impact/i)).toBeTruthy();
    });

    it('hides impact chip when showImpact is false', () => {
        render(<NextBestAction action={makeAction({ impact: 'high' })} showImpact={false} />);
        expect(screen.queryByText(/high impact/i)).toBeNull();
    });

    it('renders estimated time when provided and showEstimatedTime is true', () => {
        const action = makeAction({ estimatedTime: '5 mins' });
        render(<NextBestAction action={action} showEstimatedTime />);
        expect(screen.getByText('5 mins')).toBeTruthy();
    });

    it('hides estimated time when showEstimatedTime is false', () => {
        const action = makeAction({ estimatedTime: '5 mins' });
        render(<NextBestAction action={action} showEstimatedTime={false} />);
        expect(screen.queryByText('5 mins')).toBeNull();
    });

    it('calls onAction when Take Action button clicked', () => {
        const onAction = vi.fn();
        render(<NextBestAction action={makeAction({ onAction })} />);
        fireEvent.click(screen.getByRole('button', { name: /take action/i }));
        expect(onAction).toHaveBeenCalledTimes(1);
    });

    it('renders Dismiss button when onDismiss is provided', () => {
        const onDismiss = vi.fn();
        render(<NextBestAction action={makeAction({ onDismiss })} />);
        const dismissBtn = screen.getByRole('button', { name: /dismiss/i });
        fireEvent.click(dismissBtn);
        expect(onDismiss).toHaveBeenCalledTimes(1);
    });

    it('does not render Dismiss button when onDismiss is absent', () => {
        render(<NextBestAction action={makeAction({ onDismiss: undefined })} />);
        expect(screen.queryByRole('button', { name: /dismiss/i })).toBeNull();
    });
});

// ---------------------------------------------------------------------------
// CommandInput
// ---------------------------------------------------------------------------

describe('CommandInput', () => {
    it('renders the input with default placeholder', () => {
        render(<CommandInput onSubmit={vi.fn()} autoFocus={false} />);
        const input = screen.getByLabelText('AI command input');
        expect(input).toBeTruthy();
    });

    it('renders custom placeholder', () => {
        const commandPlaceholder = 'Type a command...';
        render(<CommandInput onSubmit={vi.fn()} placeholder={commandPlaceholder} autoFocus={false} />);
        expect(screen.getByPlaceholderText('Type a command...')).toBeTruthy();
    });

    it('submit button is disabled when input is empty', () => {
        render(<CommandInput onSubmit={vi.fn()} autoFocus={false} />);
        const btn = screen.getByRole('button', { name: /submit command/i });
        expect(btn).toBeDisabled();
    });

    it('submit button is enabled when input has text', () => {
        render(<CommandInput onSubmit={vi.fn()} autoFocus={false} />);
        const input = screen.getByLabelText('AI command input');
        fireEvent.change(input, { target: { value: 'build a button' } });
        const btn = screen.getByRole('button', { name: /submit command/i });
        expect(btn).not.toBeDisabled();
    });

    it('calls onSubmit with current value on submit button click', () => {
        const onSubmit = vi.fn();
        render(<CommandInput onSubmit={onSubmit} autoFocus={false} />);
        const input = screen.getByLabelText('AI command input');
        fireEvent.change(input, { target: { value: 'create a form' } });
        fireEvent.click(screen.getByRole('button', { name: /submit command/i }));
        expect(onSubmit).toHaveBeenCalledWith('create a form');
    });

    it('calls onSubmit on Enter key press', () => {
        const onSubmit = vi.fn();
        render(<CommandInput onSubmit={onSubmit} autoFocus={false} />);
        const input = screen.getByLabelText('AI command input');
        fireEvent.change(input, { target: { value: 'build app' } });
        fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' });
        expect(onSubmit).toHaveBeenCalledWith('build app');
    });

    it('does not submit on Shift+Enter', () => {
        const onSubmit = vi.fn();
        render(<CommandInput onSubmit={onSubmit} autoFocus={false} />);
        const input = screen.getByLabelText('AI command input');
        fireEvent.change(input, { target: { value: 'hello' } });
        fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', shiftKey: true });
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it('disables input and submit when isProcessing', () => {
        render(<CommandInput onSubmit={vi.fn()} isProcessing autoFocus={false} />);
        const input = screen.getByLabelText('AI command input');
        expect(input).toBeDisabled();
        const btn = screen.getByRole('button', { name: /submit command/i });
        expect(btn).toBeDisabled();
    });

    it('shows processing indicator when isProcessing', () => {
        render(<CommandInput onSubmit={vi.fn()} isProcessing autoFocus={false} />);
        expect(screen.getByText(/AI is thinking/i)).toBeTruthy();
    });

    it('shows clear button when input has text', () => {
        render(<CommandInput onSubmit={vi.fn()} autoFocus={false} />);
        const input = screen.getByLabelText('AI command input');
        fireEvent.change(input, { target: { value: 'hello' } });
        expect(screen.getByRole('button', { name: /clear input/i })).toBeTruthy();
    });

    it('clears input when clear button clicked', () => {
        render(<CommandInput onSubmit={vi.fn()} autoFocus={false} />);
        const input = screen.getByLabelText('AI command input') as HTMLInputElement;
        fireEvent.change(input, { target: { value: 'hello' } });
        fireEvent.click(screen.getByRole('button', { name: /clear input/i }));
        expect(input.value).toBe('');
    });

    it('supports controlled mode via value + onChange', () => {
        const onChange = vi.fn();
        render(<CommandInput onSubmit={vi.fn()} value="controlled" onChange={onChange} autoFocus={false} />);
        const input = screen.getByLabelText('AI command input') as HTMLInputElement;
        expect(input.value).toBe('controlled');
        fireEvent.change(input, { target: { value: 'updated' } });
        expect(onChange).toHaveBeenCalledWith('updated');
    });
});

// ---------------------------------------------------------------------------
// RecentProjectsStrip
// ---------------------------------------------------------------------------

describe('RecentProjectsStrip', () => {
    it('renders nothing when projects list is empty', () => {
        const { container } = render(
            <MemoryRouter>
                <RecentProjectsStrip projects={[]} />
            </MemoryRouter>,
        );
        expect(container.firstChild).toBeNull();
    });

    it('renders "Recent Projects" header', () => {
        render(
            <MemoryRouter>
                <RecentProjectsStrip projects={[makeProject()]} />
            </MemoryRouter>,
        );
        expect(screen.getByText('Recent Projects')).toBeTruthy();
    });

    it('renders project names', () => {
        const projects = [
            makeProject({ id: 'p1', name: 'Alpha' }),
            makeProject({ id: 'p2', name: 'Beta' }),
        ];
        render(
            <MemoryRouter>
                <RecentProjectsStrip projects={projects} />
            </MemoryRouter>,
        );
        expect(screen.getByText('Alpha')).toBeTruthy();
        expect(screen.getByText('Beta')).toBeTruthy();
    });

    it('limits rendered projects to maxItems', () => {
        const projects = Array.from({ length: 8 }, (_, i) =>
            makeProject({ id: `p${i}`, name: `Project ${i}` }),
        );
        render(
            <MemoryRouter>
                <RecentProjectsStrip projects={projects} maxItems={3} />
            </MemoryRouter>,
        );
        // Only 3 project names rendered
        const links = screen.getAllByRole('link');
        expect(links).toHaveLength(3);
    });

    it('renders link to project route', () => {
        render(
            <MemoryRouter>
                <RecentProjectsStrip projects={[makeProject({ id: 'proj-42' })]} />
            </MemoryRouter>,
        );
        const link = screen.getByRole('link');
        expect(link.getAttribute('href')).toContain('proj-42');
    });

    it('shows lifecycle phase badge', () => {
        render(
            <MemoryRouter>
                <RecentProjectsStrip projects={[makeProject({ lifecyclePhase: 'GENERATE' as LifecyclePhase })]} />
            </MemoryRouter>,
        );
        // Phase label text (formatted from lifecycle helpers)
        expect(screen.getAllByText(/generate/i).length).toBeGreaterThan(0);
    });

    it('shows AI health score when provided', () => {
        render(
            <MemoryRouter>
                <RecentProjectsStrip projects={[makeProject({ aiHealthScore: 92 } as Project)]} />
            </MemoryRouter>,
        );
        expect(screen.getByText('92%')).toBeTruthy();
    });

    it('shows first AI next action preview', () => {
        render(
            <MemoryRouter>
                <RecentProjectsStrip
                    projects={[makeProject({ aiNextActions: ['Write tests', 'Deploy'] } as Project)]}
                />
            </MemoryRouter>,
        );
        expect(screen.getByText('Write tests')).toBeTruthy();
    });

    it('shows project type label', () => {
        render(
            <MemoryRouter>
                <RecentProjectsStrip projects={[makeProject({ type: 'mobile' })]} />
            </MemoryRouter>,
        );
        expect(screen.getByText('mobile')).toBeTruthy();
    });
});
