/**
 * @doc.type test
 * @doc.purpose Component tests for RequirementWireframer (Journey 21.1 - Product Designer Requirement to Wireframe)
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RequirementWireframer } from '../RequirementWireframer';

// Mock the hook
vi.mock('../hooks/useRequirementWireframer', () => ({
    useRequirementWireframer: vi.fn(() => ({
        userStory: '',
        setUserStory: vi.fn(),
        parsedStory: null,
        validation: null,
        elements: [],
        rules: [],
        flow: [],
        flowSimulation: null,
        parseStory: vi.fn(),
        clearStory: vi.fn(),
        addElement: vi.fn(),
        updateElement: vi.fn(),
        deleteElement: vi.fn(),
        addRule: vi.fn(),
        updateRule: vi.fn(),
        deleteRule: vi.fn(),
        addFlowStep: vi.fn(),
        updateFlowStep: vi.fn(),
        deleteFlowStep: vi.fn(),
        reorderFlow: vi.fn(),
        startSimulation: vi.fn(),
        pauseSimulation: vi.fn(),
        resetSimulation: vi.fn(),
        nextStep: vi.fn(),
        previousStep: vi.fn(),
        setSimulationSpeed: vi.fn(),
        exportAsJSON: vi.fn(() => '{}'),
        exportAsMarkdown: vi.fn(() => '# Export'),
    })),
}));

describe('RequirementWireframer', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('should render the component', () => {
            render(<RequirementWireframer />);

            expect(screen.getByText('Requirement to Wireframe')).toBeInTheDocument();
            expect(screen.getByLabelText(/user story/i)).toBeInTheDocument();
        });

        it('should render input panel', () => {
            render(<RequirementWireframer />);

            expect(screen.getByText('Input & Validation')).toBeInTheDocument();
            expect(screen.getByRole('button', { name: /parse story/i })).toBeInTheDocument();
            expect(screen.getByRole('button', { name: /clear/i })).toBeInTheDocument();
        });

        it('should render wireframe details panel', () => {
            render(<RequirementWireframer />);

            expect(screen.getByText('Wireframe Details')).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /elements/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /rules/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /flow/i })).toBeInTheDocument();
        });

        it('should render export button', () => {
            render(<RequirementWireframer />);

            expect(screen.getByRole('button', { name: /export/i })).toBeInTheDocument();
        });
    });

    describe('User Story Input', () => {
        it('should allow typing in user story field', async () => {
            const user = userEvent.setup();
            const mockSetUserStory = vi.fn();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                setUserStory: mockSetUserStory,
            });

            render(<RequirementWireframer />);

            const input = screen.getByLabelText(/user story/i);
            await user.type(input, 'As a user, I want to search');

            expect(mockSetUserStory).toHaveBeenCalled();
        });

        it('should parse story when Parse button clicked', async () => {
            const user = userEvent.setup();
            const mockParseStory = vi.fn();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                parseStory: mockParseStory,
            });

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('button', { name: /parse story/i }));

            expect(mockParseStory).toHaveBeenCalled();
        });

        it('should clear story when Clear button clicked', async () => {
            const user = userEvent.setup();
            const mockClearStory = vi.fn();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                clearStory: mockClearStory,
            });

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('button', { name: /clear/i }));

            expect(mockClearStory).toHaveBeenCalled();
        });
    });

    describe('Validation Display', () => {
        it('should show validation errors', () => {
            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                validation: {
                    valid: false,
                    errors: ['User story cannot be empty'],
                    warnings: [],
                },
            });

            render(<RequirementWireframer />);

            expect(screen.getByText(/user story cannot be empty/i)).toBeInTheDocument();
        });

        it('should show validation warnings', () => {
            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                validation: {
                    valid: true,
                    errors: [],
                    warnings: ['Story might be too short'],
                },
            });

            render(<RequirementWireframer />);

            expect(screen.getByText(/story might be too short/i)).toBeInTheDocument();
        });

        it('should show success message for valid story', () => {
            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                validation: {
                    valid: true,
                    errors: [],
                    warnings: [],
                },
            });

            render(<RequirementWireframer />);

            expect(screen.getByText(/user story is valid/i)).toBeInTheDocument();
        });
    });

    describe('Parsed Information Display', () => {
        it('should display parsed story details', () => {
            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                parsedStory: {
                    title: 'User: Search Products',
                    description: 'As a user, I want to search products',
                    actor: 'user',
                    goal: 'search products',
                    elements: [],
                    rules: [],
                    flow: [],
                },
            });

            render(<RequirementWireframer />);

            expect(screen.getByText(/user: search products/i)).toBeInTheDocument();
            expect(screen.getByText(/actor:/i)).toBeInTheDocument();
            expect(screen.getByText(/user/i)).toBeInTheDocument();
        });

        it('should display acceptance criteria', () => {
            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                parsedStory: {
                    title: 'Test',
                    description: 'Test story',
                    actor: 'user',
                    goal: 'do something',
                    elements: [],
                    rules: [],
                    flow: [],
                },
            });

            render(<RequirementWireframer />);

            expect(screen.getByText(/acceptance criteria/i)).toBeInTheDocument();
        });

        it('should display complexity estimation', () => {
            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                parsedStory: {
                    title: 'Test',
                    description: 'Test story',
                    actor: 'user',
                    goal: 'do something',
                    elements: [],
                    rules: [],
                    flow: [],
                },
            });

            render(<RequirementWireframer />);

            expect(screen.getByText(/complexity/i)).toBeInTheDocument();
        });
    });

    describe('Elements Tab', () => {
        it('should display elements in grid', () => {
            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                elements: [
                    {
                        id: 'elem-1',
                        type: 'component',
                        label: 'Search Bar',
                        description: 'Search input',
                    },
                    {
                        id: 'elem-2',
                        type: 'screen',
                        label: 'Results Page',
                    },
                ],
            });

            render(<RequirementWireframer />);

            expect(screen.getByText('Search Bar')).toBeInTheDocument();
            expect(screen.getByText('Results Page')).toBeInTheDocument();
        });

        it('should show element type badges', () => {
            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                elements: [
                    {
                        id: 'elem-1',
                        type: 'component',
                        label: 'Search Bar',
                    },
                ],
            });

            render(<RequirementWireframer />);

            expect(screen.getByText(/component/i)).toBeInTheDocument();
        });

        it('should show add element button', () => {
            render(<RequirementWireframer />);

            expect(screen.getByRole('button', { name: /add element/i })).toBeInTheDocument();
        });

        it('should allow deleting elements', async () => {
            const user = userEvent.setup();
            const mockDeleteElement = vi.fn();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                elements: [
                    {
                        id: 'elem-1',
                        type: 'component',
                        label: 'Search Bar',
                    },
                ],
                deleteElement: mockDeleteElement,
            });

            render(<RequirementWireframer />);

            const deleteButtons = screen.getAllByLabelText(/delete/i);
            await user.click(deleteButtons[0]);

            expect(mockDeleteElement).toHaveBeenCalledWith('elem-1');
        });

        it('should show empty state when no elements', () => {
            render(<RequirementWireframer />);

            expect(screen.getByText(/no ui elements yet/i)).toBeInTheDocument();
        });
    });

    describe('Rules Tab', () => {
        it('should switch to rules tab', async () => {
            const user = userEvent.setup();

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('tab', { name: /rules/i }));

            expect(screen.getByRole('tab', { name: /rules/i })).toHaveAttribute('aria-selected', 'true');
        });

        it('should display business rules', async () => {
            const user = userEvent.setup();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                rules: [
                    {
                        id: 'rule-1',
                        type: 'validation',
                        description: 'Email must be valid',
                        condition: 'user enters email',
                        action: 'validate format',
                    },
                ],
            });

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('tab', { name: /rules/i }));

            expect(screen.getByText('Email must be valid')).toBeInTheDocument();
        });

        it('should show rule type badges', async () => {
            const user = userEvent.setup();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                rules: [
                    {
                        id: 'rule-1',
                        type: 'validation',
                        description: 'Test rule',
                    },
                ],
            });

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('tab', { name: /rules/i }));

            expect(screen.getByText(/validation/i)).toBeInTheDocument();
        });

        it('should show applied elements', async () => {
            const user = userEvent.setup();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                elements: [
                    {
                        id: 'elem-1',
                        type: 'component',
                        label: 'Email Field',
                    },
                ],
                rules: [
                    {
                        id: 'rule-1',
                        type: 'validation',
                        description: 'Email must be valid',
                        appliesTo: ['elem-1'],
                    },
                ],
            });

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('tab', { name: /rules/i }));

            expect(screen.getByText(/email field/i)).toBeInTheDocument();
        });

        it('should allow deleting rules', async () => {
            const user = userEvent.setup();
            const mockDeleteRule = vi.fn();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                rules: [
                    {
                        id: 'rule-1',
                        type: 'validation',
                        description: 'Test rule',
                    },
                ],
                deleteRule: mockDeleteRule,
            });

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('tab', { name: /rules/i }));

            const deleteButtons = screen.getAllByLabelText(/delete/i);
            await user.click(deleteButtons[0]);

            expect(mockDeleteRule).toHaveBeenCalledWith('rule-1');
        });
    });

    describe('Flow Tab', () => {
        it('should switch to flow tab', async () => {
            const user = userEvent.setup();

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('tab', { name: /flow/i }));

            expect(screen.getByRole('tab', { name: /flow/i })).toHaveAttribute('aria-selected', 'true');
        });

        it('should display flow steps in stepper', async () => {
            const user = userEvent.setup();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                flow: [
                    {
                        id: 'flow-1',
                        order: 1,
                        label: 'User opens page',
                    },
                    {
                        id: 'flow-2',
                        order: 2,
                        label: 'User enters search',
                    },
                ],
            });

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('tab', { name: /flow/i }));

            expect(screen.getByText('User opens page')).toBeInTheDocument();
            expect(screen.getByText('User enters search')).toBeInTheDocument();
        });

        it('should show simulation controls', async () => {
            const user = userEvent.setup();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                flow: [
                    {
                        id: 'flow-1',
                        order: 1,
                        label: 'Step 1',
                    },
                ],
            });

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('tab', { name: /flow/i }));

            expect(screen.getByRole('button', { name: /play/i })).toBeInTheDocument();
        });

        it('should start simulation', async () => {
            const user = userEvent.setup();
            const mockStartSimulation = vi.fn();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                flow: [
                    {
                        id: 'flow-1',
                        order: 1,
                        label: 'Step 1',
                    },
                ],
                startSimulation: mockStartSimulation,
            });

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('tab', { name: /flow/i }));
            await user.click(screen.getByRole('button', { name: /play/i }));

            expect(mockStartSimulation).toHaveBeenCalled();
        });

        it('should pause simulation', async () => {
            const user = userEvent.setup();
            const mockPauseSimulation = vi.fn();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                flow: [
                    {
                        id: 'flow-1',
                        order: 1,
                        label: 'Step 1',
                    },
                ],
                flowSimulation: {
                    currentStep: 0,
                    isPlaying: true,
                    speed: 2000,
                    completed: new Set(),
                },
                pauseSimulation: mockPauseSimulation,
            });

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('tab', { name: /flow/i }));
            await user.click(screen.getByRole('button', { name: /pause/i }));

            expect(mockPauseSimulation).toHaveBeenCalled();
        });

        it('should reset simulation', async () => {
            const user = userEvent.setup();
            const mockResetSimulation = vi.fn();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                flow: [
                    {
                        id: 'flow-1',
                        order: 1,
                        label: 'Step 1',
                    },
                ],
                flowSimulation: {
                    currentStep: 0,
                    isPlaying: false,
                    speed: 2000,
                    completed: new Set(),
                },
                resetSimulation: mockResetSimulation,
            });

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('tab', { name: /flow/i }));
            await user.click(screen.getByRole('button', { name: /reset/i }));

            expect(mockResetSimulation).toHaveBeenCalled();
        });
    });

    describe('Export', () => {
        it('should open export dialog', async () => {
            const user = userEvent.setup();

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('button', { name: /export/i }));

            expect(screen.getByText(/export wireframe/i)).toBeInTheDocument();
        });

        it('should allow selecting JSON format', async () => {
            const user = userEvent.setup();

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('button', { name: /export/i }));

            const jsonButton = screen.getByRole('button', { name: /json/i });
            await user.click(jsonButton);

            expect(jsonButton).toHaveAttribute('aria-pressed', 'true');
        });

        it('should allow selecting Markdown format', async () => {
            const user = userEvent.setup();

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('button', { name: /export/i }));

            const markdownButton = screen.getByRole('button', { name: /markdown/i });
            await user.click(markdownButton);

            expect(markdownButton).toHaveAttribute('aria-pressed', 'true');
        });

        it('should close export dialog', async () => {
            const user = userEvent.setup();

            render(<RequirementWireframer />);

            await user.click(screen.getByRole('button', { name: /export/i }));
            await user.click(screen.getByRole('button', { name: /close/i }));

            expect(screen.queryByText(/export wireframe/i)).not.toBeInTheDocument();
        });
    });

    describe('Complex Workflows', () => {
        it('should handle full wireframing workflow', async () => {
            const user = userEvent.setup();
            const mockSetUserStory = vi.fn();
            const mockParseStory = vi.fn();

            vi.mocked(require('../hooks/useRequirementWireframer').useRequirementWireframer).mockReturnValue({
                ...require('../hooks/useRequirementWireframer').useRequirementWireframer(),
                setUserStory: mockSetUserStory,
                parseStory: mockParseStory,
                parsedStory: {
                    title: 'User: Search Products',
                    description: 'Test',
                    actor: 'user',
                    goal: 'search',
                    elements: [],
                    rules: [],
                    flow: [],
                },
                elements: [
                    {
                        id: 'elem-1',
                        type: 'component',
                        label: 'Search Bar',
                    },
                ],
                flow: [
                    {
                        id: 'flow-1',
                        order: 1,
                        label: 'User searches',
                    },
                ],
            });

            render(<RequirementWireframer />);

            // Type story
            const input = screen.getByLabelText(/user story/i);
            await user.type(input, 'As a user, I want to search');

            // Parse story
            await user.click(screen.getByRole('button', { name: /parse story/i }));

            // View elements
            expect(screen.getByText('Search Bar')).toBeInTheDocument();

            // View flow
            await user.click(screen.getByRole('tab', { name: /flow/i }));
            expect(screen.getByText('User searches')).toBeInTheDocument();

            // Export
            await user.click(screen.getByRole('button', { name: /export/i }));
            expect(screen.getByText(/export wireframe/i)).toBeInTheDocument();

            expect(mockSetUserStory).toHaveBeenCalled();
            expect(mockParseStory).toHaveBeenCalled();
        });
    });
});
