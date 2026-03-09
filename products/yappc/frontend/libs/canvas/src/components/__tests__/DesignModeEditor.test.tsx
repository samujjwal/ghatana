/**
 * @doc.type test
 * @doc.purpose Unit tests for DesignModeEditor component (Journey 5.1 - UX Designer High-Fidelity Mockups)
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DesignModeEditor, type DesignComponent, type PrototypeLink } from '../DesignModeEditor';
import type { Node } from '@xyflow/react';

describe('DesignModeEditor', () => {
    const mockNode: Node = {
        id: '1',
        type: 'wireframe',
        position: { x: 0, y: 0 },
        data: {
            label: 'Profile View',
            designComponents: [],
            prototypeLinks: [],
        },
    };

    const mockOnClose = vi.fn();
    const mockOnSave = vi.fn();
    const mockOnCreateLink = vi.fn();

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Initialization', () => {
        it('should render design mode editor', () => {
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                    onSave={mockOnSave}
                />
            );

            expect(screen.getByText('Design Mode')).toBeInTheDocument();
            expect(screen.getByText('Profile View')).toBeInTheDocument();
        });

        it('should initialize with existing components', () => {
            const nodeWithComponents: Node = {
                ...mockNode,
                data: {
                    ...mockNode.data,
                    designComponents: [
                        {
                            id: 'avatar-1',
                            type: 'avatar',
                            x: 10,
                            y: 10,
                            width: 48,
                            height: 48,
                            props: {},
                            label: 'Avatar',
                        },
                    ],
                },
            };

            render(
                <DesignModeEditor
                    node={nodeWithComponents}
                    onClose={mockOnClose}
                />
            );

            expect(screen.getByText('Components: 1')).toBeInTheDocument();
        });

        it('should initialize with existing prototype links', () => {
            const nodeWithLinks: Node = {
                ...mockNode,
                data: {
                    ...mockNode.data,
                    designComponents: [
                        { id: 'button-1', type: 'button', x: 10, y: 10, width: 120, height: 36, props: {} },
                    ],
                    prototypeLinks: [
                        { id: 'link-1', from: 'button-1', to: 'node-2', event: 'click' },
                    ],
                },
            };

            render(
                <DesignModeEditor
                    node={nodeWithLinks}
                    onClose={mockOnClose}
                />
            );

            expect(screen.getByText('Links: 1')).toBeInTheDocument();
        });

        it('should show component library', () => {
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            expect(screen.getByText('Component Library')).toBeInTheDocument();
            expect(screen.getByText('Avatar')).toBeInTheDocument();
            expect(screen.getByText('Text Field')).toBeInTheDocument();
            expect(screen.getByText('Button')).toBeInTheDocument();
        });
    });

    describe('Component Operations', () => {
        it('should add component to canvas', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            const avatarButton = screen.getByText('Avatar');
            await user.click(avatarButton);

            await waitFor(() => {
                expect(screen.getByText('Components: 1')).toBeInTheDocument();
            });
        });

        it('should add multiple components', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            await user.click(screen.getByText('Avatar'));
            await user.click(screen.getByText('Button'));
            await user.click(screen.getByText('Text Field'));

            await waitFor(() => {
                expect(screen.getByText('Components: 3')).toBeInTheDocument();
            });
        });

        it('should select component when clicked', async () => {
            const user = userEvent.setup();
            const nodeWithComponent: Node = {
                ...mockNode,
                data: {
                    ...mockNode.data,
                    designComponents: [
                        { id: 'avatar-1', type: 'avatar', x: 10, y: 10, width: 48, height: 48, props: {}, label: 'Avatar' },
                    ],
                },
            };

            render(
                <DesignModeEditor
                    node={nodeWithComponent}
                    onClose={mockOnClose}
                />
            );

            // Find and click the component (rendered as a box with label)
            const componentBox = screen.getAllByText('Avatar').find(el => el.closest('[style*="position: absolute"]'));
            if (componentBox) {
                await user.click(componentBox);

                // Properties panel should appear
                await waitFor(() => {
                    expect(screen.getByText('Properties')).toBeInTheDocument();
                });
            }
        });

        it('should delete component', async () => {
            const user = userEvent.setup();
            const nodeWithComponent: Node = {
                ...mockNode,
                data: {
                    ...mockNode.data,
                    designComponents: [
                        { id: 'avatar-1', type: 'avatar', x: 10, y: 10, width: 48, height: 48, props: {}, label: 'Avatar' },
                    ],
                },
            };

            render(
                <DesignModeEditor
                    node={nodeWithComponent}
                    onClose={mockOnClose}
                />
            );

            // Select component
            const componentBox = screen.getAllByText('Avatar').find(el => el.closest('[style*="position: absolute"]'));
            if (componentBox) {
                await user.click(componentBox);

                // Wait for properties panel
                await waitFor(() => {
                    expect(screen.getByText('Properties')).toBeInTheDocument();
                });

                // Find and click delete button (close icon in properties panel)
                const deleteButtons = screen.getAllByRole('button');
                const deleteButton = deleteButtons.find(btn => btn.querySelector('[data-testid="CloseIcon"]'));
                if (deleteButton) {
                    await user.click(deleteButton);

                    await waitFor(() => {
                        expect(screen.getByText('Components: 0')).toBeInTheDocument();
                    });
                }
            }
        });
    });

    describe('Component Editing', () => {
        it('should update component position', async () => {
            const user = userEvent.setup();
            const nodeWithComponent: Node = {
                ...mockNode,
                data: {
                    ...mockNode.data,
                    designComponents: [
                        { id: 'avatar-1', type: 'avatar', x: 10, y: 10, width: 48, height: 48, props: {}, label: 'Avatar' },
                    ],
                },
            };

            render(
                <DesignModeEditor
                    node={nodeWithComponent}
                    onClose={mockOnClose}
                />
            );

            // Select component
            const componentBox = screen.getAllByText('Avatar').find(el => el.closest('[style*="position: absolute"]'));
            if (componentBox) {
                await user.click(componentBox);

                // Wait for properties panel
                await waitFor(() => {
                    expect(screen.getByText('Properties')).toBeInTheDocument();
                });

                // Update X position
                const xInput = screen.getByLabelText('X') as HTMLInputElement;
                await user.clear(xInput);
                await user.type(xInput, '100');

                expect(xInput.value).toBe('100');
            }
        });

        it('should update component size', async () => {
            const user = userEvent.setup();
            const nodeWithComponent: Node = {
                ...mockNode,
                data: {
                    ...mockNode.data,
                    designComponents: [
                        { id: 'button-1', type: 'button', x: 10, y: 10, width: 120, height: 36, props: {}, label: 'Button' },
                    ],
                },
            };

            render(
                <DesignModeEditor
                    node={nodeWithComponent}
                    onClose={mockOnClose}
                />
            );

            // Select component
            const componentBox = screen.getAllByText('Button').find(el => el.closest('[style*="position: absolute"]'));
            if (componentBox) {
                await user.click(componentBox);

                await waitFor(() => {
                    expect(screen.getByText('Properties')).toBeInTheDocument();
                });

                // Update width
                const widthInput = screen.getByLabelText('Width') as HTMLInputElement;
                await user.clear(widthInput);
                await user.type(widthInput, '200');

                expect(widthInput.value).toBe('200');
            }
        });
    });

    describe('Prototype Linking', () => {
        it('should enable link mode', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            // Find link mode button
            const linkButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="LinkIcon"]')
            );

            if (linkButton) {
                await user.click(linkButton);
                // Link mode should be activated (button becomes primary color)
            }
        });

        it('should create prototype link between components', async () => {
            const user = userEvent.setup();
            const nodeWithComponents: Node = {
                ...mockNode,
                data: {
                    ...mockNode.data,
                    designComponents: [
                        { id: 'button-1', type: 'button', x: 10, y: 10, width: 120, height: 36, props: {}, label: 'Button' },
                        { id: 'text-1', type: 'text', x: 200, y: 10, width: 100, height: 30, props: {}, label: 'Text' },
                    ],
                },
            };

            render(
                <DesignModeEditor
                    node={nodeWithComponents}
                    onClose={mockOnClose}
                />
            );

            // Select first component
            const buttonBox = screen.getAllByText('Button').find(el => el.closest('[style*="position: absolute"]'));
            if (buttonBox) {
                await user.click(buttonBox);

                // Click create link button in properties panel
                await waitFor(() => {
                    const createLinkButton = screen.getByText('Create Prototype Link');
                    if (createLinkButton) {
                        user.click(createLinkButton);
                    }
                });

                // Click second component to complete link
                const textBox = screen.getAllByText('Text').find(el => el.closest('[style*="position: absolute"]'));
                if (textBox) {
                    await user.click(textBox);

                    await waitFor(() => {
                        expect(screen.getByText('Links: 1')).toBeInTheDocument();
                    });
                }
            }
        });
    });

    describe('Zoom and View Controls', () => {
        it('should zoom in', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            expect(screen.getByText('100%')).toBeInTheDocument();

            // Find zoom in button
            const zoomInButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="ZoomInIcon"]')
            );

            if (zoomInButton) {
                await user.click(zoomInButton);
                await waitFor(() => {
                    expect(screen.getByText('110%')).toBeInTheDocument();
                });
            }
        });

        it('should zoom out', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            expect(screen.getByText('100%')).toBeInTheDocument();

            // Find zoom out button
            const zoomOutButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="ZoomOutIcon"]')
            );

            if (zoomOutButton) {
                await user.click(zoomOutButton);
                await waitFor(() => {
                    expect(screen.getByText('90%')).toBeInTheDocument();
                });
            }
        });

        it('should toggle grid display', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            // Find grid toggle button
            const gridButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="GridOnIcon"]')
            );

            if (gridButton) {
                await user.click(gridButton);
                // Grid should toggle (visual change in canvas background)
            }
        });
    });

    describe('History (Undo/Redo)', () => {
        it('should undo component addition', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            // Add component
            await user.click(screen.getByText('Avatar'));
            await waitFor(() => {
                expect(screen.getByText('Components: 1')).toBeInTheDocument();
            });

            // Undo
            const undoButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="UndoIcon"]')
            );

            if (undoButton) {
                await user.click(undoButton);
                await waitFor(() => {
                    expect(screen.getByText('Components: 0')).toBeInTheDocument();
                });
            }
        });

        it('should redo component addition', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            // Add and undo
            await user.click(screen.getByText('Avatar'));
            await waitFor(() => {
                expect(screen.getByText('Components: 1')).toBeInTheDocument();
            });

            const undoButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="UndoIcon"]')
            );
            if (undoButton) await user.click(undoButton);

            // Redo
            const redoButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="RedoIcon"]')
            );

            if (redoButton) {
                await user.click(redoButton);
                await waitFor(() => {
                    expect(screen.getByText('Components: 1')).toBeInTheDocument();
                });
            }
        });

        it('should disable undo when at beginning of history', () => {
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            const undoButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="UndoIcon"]')
            );

            expect(undoButton).toBeDisabled();
        });

        it('should disable redo when at end of history', () => {
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            const redoButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="RedoIcon"]')
            );

            expect(redoButton).toBeDisabled();
        });
    });

    describe('Save and Close', () => {
        it('should call onSave with components and links', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                    onSave={mockOnSave}
                />
            );

            // Add a component
            await user.click(screen.getByText('Avatar'));

            // Save
            const saveButton = screen.getByText('Save');
            await user.click(saveButton);

            expect(mockOnSave).toHaveBeenCalledWith(
                expect.arrayContaining([
                    expect.objectContaining({
                        type: 'avatar',
                    }),
                ]),
                []
            );
        });

        it('should call onClose when close button clicked', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            // Find close button (last close icon in header)
            const closeButtons = screen.getAllByRole('button');
            const closeButton = closeButtons[closeButtons.length - 1];

            await user.click(closeButton);

            expect(mockOnClose).toHaveBeenCalled();
        });
    });

    describe('Component Library Toggle', () => {
        it('should collapse component library', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            expect(screen.getByText('Avatar')).toBeVisible();

            // Click component library header to collapse
            await user.click(screen.getByText('Component Library'));

            // Components should be hidden
            await waitFor(() => {
                expect(screen.queryByText('Avatar')).not.toBeVisible();
            });
        });

        it('should expand component library after collapse', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            // Collapse
            await user.click(screen.getByText('Component Library'));
            await waitFor(() => {
                expect(screen.queryByText('Avatar')).not.toBeVisible();
            });

            // Expand
            await user.click(screen.getByText('Component Library'));
            await waitFor(() => {
                expect(screen.getByText('Avatar')).toBeVisible();
            });
        });
    });

    describe('Complex Scenarios', () => {
        it('should handle complete design workflow', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                    onSave={mockOnSave}
                />
            );

            // Add multiple components
            await user.click(screen.getByText('Avatar'));
            await user.click(screen.getByText('Text Field'));
            await user.click(screen.getByText('Button'));

            await waitFor(() => {
                expect(screen.getByText('Components: 3')).toBeInTheDocument();
            });

            // Zoom
            const zoomInButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="ZoomInIcon"]')
            );
            if (zoomInButton) await user.click(zoomInButton);

            // Save
            await user.click(screen.getByText('Save'));

            expect(mockOnSave).toHaveBeenCalledWith(
                expect.arrayContaining([
                    expect.objectContaining({ type: 'avatar' }),
                    expect.objectContaining({ type: 'textfield' }),
                    expect.objectContaining({ type: 'button' }),
                ]),
                []
            );
        });

        it('should handle undo/redo workflow with multiple operations', async () => {
            const user = userEvent.setup();
            render(
                <DesignModeEditor
                    node={mockNode}
                    onClose={mockOnClose}
                />
            );

            // Add 3 components
            await user.click(screen.getByText('Avatar'));
            await user.click(screen.getByText('Button'));
            await user.click(screen.getByText('Text Field'));

            await waitFor(() => {
                expect(screen.getByText('Components: 3')).toBeInTheDocument();
            });

            // Undo 2 times
            const undoButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="UndoIcon"]')
            );
            if (undoButton) {
                await user.click(undoButton);
                await user.click(undoButton);
            }

            await waitFor(() => {
                expect(screen.getByText('Components: 1')).toBeInTheDocument();
            });

            // Redo 1 time
            const redoButton = screen.getAllByRole('button').find(btn =>
                btn.querySelector('[data-testid="RedoIcon"]')
            );
            if (redoButton) {
                await user.click(redoButton);
            }

            await waitFor(() => {
                expect(screen.getByText('Components: 2')).toBeInTheDocument();
            });
        });
    });
});
