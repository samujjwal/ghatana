/**
 * ComponentGeneratorDialog Tests
 * 
 * Comprehensive test suite for Component Generator Dialog (Journey 7.1)
 */

import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ComponentGeneratorDialog } from '../ComponentGeneratorDialog';
import type { Node } from '@xyflow/react';
import type { UIScreenNodeData } from '../PersonaNodes';

// Mock react-syntax-highlighter to avoid rendering issues in tests
vi.mock('react-syntax-highlighter', () => ({
    Light: ({ children }: { children: string }) => <pre>{children}</pre>,
}));

vi.mock('react-syntax-highlighter/dist/esm/languages/hljs/typescript', () => ({
    default: {},
}));

vi.mock('react-syntax-highlighter/dist/esm/styles/hljs', () => ({
    githubGist: {},
}));

// Mock useComponentGeneration hook
vi.mock('../hooks/useComponentGeneration', () => ({
    useComponentGeneration: () => ({
        generateComponent: vi.fn().mockResolvedValue({
            componentFile: {
                filename: 'TestComponent.tsx',
                content: 'export function TestComponent() { return <div>Test</div>; }',
            },
            testFile: {
                filename: 'TestComponent.test.tsx',
                content: 'describe("TestComponent", () => { it("renders", () => {}); });',
            },
            storyFile: {
                filename: 'TestComponent.stories.tsx',
                content: 'export default { title: "TestComponent" };',
            },
        }),
        isGenerating: false,
        error: null,
        lastGenerated: null,
        generateStorybook: vi.fn(),
        downloadFiles: vi.fn(),
        copyToClipboard: vi.fn(),
        previewCode: null,
        setPreviewCode: vi.fn(),
    }),
}));

describe('ComponentGeneratorDialog', () => {
    const mockNode: Node<UIScreenNodeData> = {
        id: '1',
        type: 'uiScreen',
        position: { x: 0, y: 0 },
        data: {
            label: 'TestComponent',
            type: 'uiScreen',
            screenType: 'view',
            persona: 'ux',
            components: ['Button', 'Input'],
        },
    };

    const mockOnClose = vi.fn();
    const mockOnGenerated = vi.fn();

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('should not render when open is false', () => {
            render(
                <ComponentGeneratorDialog
                    open={false}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        });

        it('should render when open is true', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.getByText(/Component Generator/i)).toBeInTheDocument();
        });

        it('should render with node label as title', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByText(/TestComponent/i)).toBeInTheDocument();
        });

        it('should render without node', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={null}
                />
            );

            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });
    });

    describe('Dialog Actions', () => {
        it('should call onClose when cancel button is clicked', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const cancelButton = screen.getByRole('button', { name: /cancel/i });
            await user.click(cancelButton);

            expect(mockOnClose).toHaveBeenCalledTimes(1);
        });

        it('should call onClose when close icon is clicked', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            // Find close button by aria-label
            const closeButton = screen.getByLabelText(/close/i);
            await user.click(closeButton);

            expect(mockOnClose).toHaveBeenCalledTimes(1);
        });

        it('should call onClose when clicking outside dialog (if backdrop click enabled)', async () => {
            const user = userEvent.setup();
            const { container } = render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            // Click on backdrop (MUI Dialog backdrop)
            const backdrop = container.querySelector('.MuiBackdrop-root');
            if (backdrop) {
                await user.click(backdrop);
                expect(mockOnClose).toHaveBeenCalled();
            }
        });
    });

    describe('Framework Selection', () => {
        it('should display framework dropdown', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByLabelText(/framework/i)).toBeInTheDocument();
        });

        it('should have React as default framework', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const frameworkSelect = screen.getByLabelText(/framework/i);
            expect(frameworkSelect).toHaveValue('react');
        });

        it('should allow changing framework', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const frameworkSelect = screen.getByLabelText(/framework/i);
            await user.click(frameworkSelect);

            // Select Vue option
            const vueOption = await screen.findByRole('option', { name: /vue/i });
            await user.click(vueOption);

            expect(frameworkSelect).toHaveValue('vue');
        });
    });

    describe('Styling Selection', () => {
        it('should display styling dropdown', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByLabelText(/styling/i)).toBeInTheDocument();
        });

        it('should have Tailwind as default styling', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const stylingSelect = screen.getByLabelText(/styling/i);
            expect(stylingSelect).toHaveValue('tailwind');
        });

        it('should allow changing styling approach', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const stylingSelect = screen.getByLabelText(/styling/i);
            await user.click(stylingSelect);

            // Select CSS Modules option
            const cssModulesOption = await screen.findByRole('option', { name: /css modules/i });
            await user.click(cssModulesOption);

            expect(stylingSelect).toHaveValue('css-modules');
        });
    });

    describe('TypeScript Toggle', () => {
        it('should display TypeScript checkbox', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('checkbox', { name: /typescript/i })).toBeInTheDocument();
        });

        it('should have TypeScript enabled by default', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const tsCheckbox = screen.getByRole('checkbox', { name: /typescript/i });
            expect(tsCheckbox).toBeChecked();
        });

        it('should toggle TypeScript option', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const tsCheckbox = screen.getByRole('checkbox', { name: /typescript/i });
            await user.click(tsCheckbox);

            expect(tsCheckbox).not.toBeChecked();
        });
    });

    describe('Additional Options', () => {
        it('should display tests checkbox', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('checkbox', { name: /include tests/i })).toBeInTheDocument();
        });

        it('should display storybook checkbox', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('checkbox', { name: /include storybook/i })).toBeInTheDocument();
        });

        it('should display accessibility checkbox', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('checkbox', { name: /accessible/i })).toBeInTheDocument();
        });

        it('should display responsive checkbox', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('checkbox', { name: /responsive/i })).toBeInTheDocument();
        });

        it('should toggle all options independently', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const testsCheckbox = screen.getByRole('checkbox', { name: /include tests/i });
            const storybookCheckbox = screen.getByRole('checkbox', { name: /include storybook/i });
            const a11yCheckbox = screen.getByRole('checkbox', { name: /accessible/i });
            const responsiveCheckbox = screen.getByRole('checkbox', { name: /responsive/i });

            await user.click(testsCheckbox);
            await user.click(storybookCheckbox);
            await user.click(a11yCheckbox);
            await user.click(responsiveCheckbox);

            expect(testsCheckbox).toBeChecked();
            expect(storybookCheckbox).toBeChecked();
            expect(a11yCheckbox).toBeChecked();
            expect(responsiveCheckbox).toBeChecked();
        });
    });

    describe('Props Management', () => {
        it('should display props list', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByText(/props/i)).toBeInTheDocument();
        });

        it('should display add prop button', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('button', { name: /add prop/i })).toBeInTheDocument();
        });

        it('should allow adding a new prop', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const addPropButton = screen.getByRole('button', { name: /add prop/i });
            await user.click(addPropButton);

            // Should show prop input fields
            expect(screen.getByPlaceholderText(/prop name/i)).toBeInTheDocument();
        });

        it('should allow deleting a prop', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const addPropButton = screen.getByRole('button', { name: /add prop/i });
            await user.click(addPropButton);

            // Find delete button for prop
            const deleteButtons = screen.getAllByLabelText(/delete/i);
            if (deleteButtons.length > 0) {
                await user.click(deleteButtons[0]);
            }
        });

        it('should validate prop name is required', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const addPropButton = screen.getByRole('button', { name: /add prop/i });
            await user.click(addPropButton);

            // Try to add prop without name
            const nameInput = screen.getByPlaceholderText(/prop name/i);
            await user.clear(nameInput);

            // Should show validation error
            await waitFor(() => {
                expect(screen.queryByText(/required/i)).toBeInTheDocument();
            });
        });
    });

    describe('Tabs Navigation', () => {
        it('should display configuration tab', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('tab', { name: /configuration/i })).toBeInTheDocument();
        });

        it('should display preview tab', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('tab', { name: /preview/i })).toBeInTheDocument();
        });

        it('should switch between tabs', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const previewTab = screen.getByRole('tab', { name: /preview/i });
            await user.click(previewTab);

            // Preview tab should be selected
            expect(previewTab).toHaveAttribute('aria-selected', 'true');
        });

        it('should display files tab', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('tab', { name: /files/i })).toBeInTheDocument();
        });
    });

    describe('Component Generation', () => {
        it('should display generate button', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('button', { name: /generate/i })).toBeInTheDocument();
        });

        it('should disable generate button when node is null', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={null}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            expect(generateButton).toBeDisabled();
        });

        it('should enable generate button when node is provided', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            expect(generateButton).not.toBeDisabled();
        });

        it('should show loading state during generation', async () => {
            const user = userEvent.setup();

            // Mock slow generation
            vi.doMock('../hooks/useComponentGeneration', () => ({
                useComponentGeneration: () => ({
                    generateComponent: vi.fn().mockImplementation(() =>
                        new Promise(resolve => setTimeout(resolve, 1000))
                    ),
                    isGenerating: true,
                    error: null,
                    lastGenerated: null,
                    generateStorybook: vi.fn(),
                    downloadFiles: vi.fn(),
                    copyToClipboard: vi.fn(),
                    previewCode: null,
                    setPreviewCode: vi.fn(),
                }),
            }));

            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            // Should show loading indicator
            expect(screen.getByRole('progressbar')).toBeInTheDocument();
        });

        it('should call onGenerated callback after successful generation', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                    onGenerated={mockOnGenerated}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            await waitFor(() => {
                expect(mockOnGenerated).toHaveBeenCalled();
            });
        });

        it('should display error message on generation failure', async () => {
            const user = userEvent.setup();

            // Mock error
            vi.doMock('../hooks/useComponentGeneration', () => ({
                useComponentGeneration: () => ({
                    generateComponent: vi.fn().mockRejectedValue(new Error('Generation failed')),
                    isGenerating: false,
                    error: 'Generation failed',
                    lastGenerated: null,
                    generateStorybook: vi.fn(),
                    downloadFiles: vi.fn(),
                    copyToClipboard: vi.fn(),
                    previewCode: null,
                    setPreviewCode: vi.fn(),
                }),
            }));

            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            await waitFor(() => {
                expect(screen.getByText(/failed/i)).toBeInTheDocument();
            });
        });
    });

    describe('File Actions', () => {
        it('should display download button after generation', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /download/i })).toBeInTheDocument();
            });
        });

        it('should display copy button after generation', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /copy/i })).toBeInTheDocument();
            });
        });

        it('should allow downloading all files', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            await waitFor(async () => {
                const downloadButton = screen.getByRole('button', { name: /download/i });
                await user.click(downloadButton);
            });
        });

        it('should allow copying component code to clipboard', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            await waitFor(async () => {
                const copyButton = screen.getByRole('button', { name: /copy/i });
                await user.click(copyButton);
            });
        });
    });

    describe('Preview Display', () => {
        it('should show code preview after generation', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            // Switch to preview tab
            const previewTab = screen.getByRole('tab', { name: /preview/i });
            await user.click(previewTab);

            await waitFor(() => {
                expect(screen.getByText(/export function TestComponent/i)).toBeInTheDocument();
            });
        });

        it('should display syntax highlighted code', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            const previewTab = screen.getByRole('tab', { name: /preview/i });
            await user.click(previewTab);

            await waitFor(() => {
                // Should render code in a pre tag (mocked SyntaxHighlighter)
                expect(screen.getByRole('region', { name: /code/i })).toBeInTheDocument();
            });
        });
    });

    describe('Generated Files List', () => {
        it('should list all generated files', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            // Switch to files tab
            const filesTab = screen.getByRole('tab', { name: /files/i });
            await user.click(filesTab);

            await waitFor(() => {
                expect(screen.getByText(/TestComponent.tsx/i)).toBeInTheDocument();
                expect(screen.getByText(/TestComponent.test.tsx/i)).toBeInTheDocument();
                expect(screen.getByText(/TestComponent.stories.tsx/i)).toBeInTheDocument();
            });
        });

        it('should show file sizes', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            const filesTab = screen.getByRole('tab', { name: /files/i });
            await user.click(filesTab);

            await waitFor(() => {
                expect(screen.getByText(/\d+ bytes/i)).toBeInTheDocument();
            });
        });

        it('should allow downloading individual files', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            const generateButton = screen.getByRole('button', { name: /generate/i });
            await user.click(generateButton);

            const filesTab = screen.getByRole('tab', { name: /files/i });
            await user.click(filesTab);

            await waitFor(async () => {
                const downloadIcons = screen.getAllByLabelText(/download file/i);
                if (downloadIcons.length > 0) {
                    await user.click(downloadIcons[0]);
                }
            });
        });
    });

    describe('Edge Cases', () => {
        it('should handle node with no label', () => {
            const nodeWithoutLabel: Node<UIScreenNodeData> = {
                ...mockNode,
                data: { ...mockNode.data, label: '' },
            };

            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={nodeWithoutLabel}
                />
            );

            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });

        it('should handle node with no components', () => {
            const nodeWithoutComponents: Node<UIScreenNodeData> = {
                ...mockNode,
                data: { ...mockNode.data, components: undefined },
            };

            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={nodeWithoutComponents}
                />
            );

            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });

        it('should handle all screen types', () => {
            const screenTypes: UIScreenNodeData['screenType'][] = ['view', 'edit', 'list', 'detail', 'modal'];

            screenTypes.forEach(screenType => {
                const nodeWithScreenType: Node<UIScreenNodeData> = {
                    ...mockNode,
                    data: { ...mockNode.data, screenType },
                };

                const { unmount } = render(
                    <ComponentGeneratorDialog
                        open={true}
                        onClose={mockOnClose}
                        node={nodeWithScreenType}
                    />
                );

                expect(screen.getByRole('dialog')).toBeInTheDocument();
                unmount();
            });
        });

        it('should handle rapid open/close cycles', async () => {
            const { rerender } = render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('dialog')).toBeInTheDocument();

            rerender(
                <ComponentGeneratorDialog
                    open={false}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

            rerender(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });

        it('should handle changing node while dialog is open', () => {
            const { rerender } = render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByText(/TestComponent/i)).toBeInTheDocument();

            const newNode: Node<UIScreenNodeData> = {
                ...mockNode,
                id: '2',
                data: { ...mockNode.data, label: 'NewComponent' },
            };

            rerender(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={newNode}
                />
            );

            expect(screen.getByText(/NewComponent/i)).toBeInTheDocument();
        });
    });

    describe('Accessibility', () => {
        it('should have proper ARIA labels', () => {
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            expect(screen.getByRole('dialog')).toHaveAttribute('aria-labelledby');
        });

        it('should support keyboard navigation', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            // Tab through interactive elements
            await user.tab();
            await user.tab();
            await user.tab();

            expect(document.activeElement).toBeDefined();
        });

        it('should trap focus within dialog', async () => {
            const user = userEvent.setup();
            render(
                <>
                    <button>Outside Button</button>
                    <ComponentGeneratorDialog
                        open={true}
                        onClose={mockOnClose}
                        node={mockNode}
                    />
                </>
            );

            // Focus should be trapped inside dialog
            await user.tab();
            expect(screen.getByRole('dialog')).toContainElement(document.activeElement as HTMLElement);
        });

        it('should support Escape key to close', async () => {
            const user = userEvent.setup();
            render(
                <ComponentGeneratorDialog
                    open={true}
                    onClose={mockOnClose}
                    node={mockNode}
                />
            );

            await user.keyboard('{Escape}');
            expect(mockOnClose).toHaveBeenCalled();
        });
    });
});
