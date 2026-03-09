/**
 * PrototypeLinkTool Tests
 * 
 * Comprehensive test suite for Prototype Link Tool (Journey 5.1)
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { PrototypeLinkTool, type PrototypeLink } from '../PrototypeLinkTool';

describe('PrototypeLinkTool', () => {
    const mockComponents = [
        { id: 'comp1', label: 'Button 1', type: 'button' },
        { id: 'comp2', label: 'Input 1', type: 'textfield' },
    ];

    const mockNodes = [
        { id: 'node1', label: 'Home Screen', type: 'screen' },
        { id: 'node2', label: 'Profile Screen', type: 'screen' },
    ];

    const mockLinks: PrototypeLink[] = [
        {
            id: 'link1',
            fromComponentId: 'comp1',
            fromComponentLabel: 'Button 1',
            toNodeId: 'node1',
            toNodeLabel: 'Home Screen',
            event: 'click',
            transition: 'slide',
            duration: 300,
        },
    ];

    const mockOnCreateLink = vi.fn();
    const mockOnUpdateLink = vi.fn();
    const mockOnDeleteLink = vi.fn();
    const mockOnClose = vi.fn();

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('should render successfully', () => {
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );
            expect(screen.getByText('Prototype Links')).toBeInTheDocument();
        });

        it('should render close button when onClose provided', () => {
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                    onClose={mockOnClose}
                />
            );
            expect(screen.getByLabelText(/close/i)).toBeInTheDocument();
        });
    });

    describe('Link Creation', () => {
        it('should show New Link button', () => {
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );
            expect(screen.getByRole('button', { name: /new link/i })).toBeInTheDocument();
        });

        it('should show create form when clicking New Link', async () => {
            const user = userEvent.setup();
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            await user.click(screen.getByRole('button', { name: /new link/i }));
            expect(screen.getByText('Create Link')).toBeInTheDocument();
        });

        it('should allow creating a new link', async () => {
            const user = userEvent.setup();
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            await user.click(screen.getByRole('button', { name: /new link/i }));

            // Select from component
            const fromSelect = screen.getByLabelText(/from component/i);
            await user.click(fromSelect);
            await user.click(screen.getByText('Button 1'));

            // Select to node
            const toSelect = screen.getByLabelText(/to screen/i);
            await user.click(toSelect);
            await user.click(screen.getByText('Home Screen'));

            // Click Create
            await user.click(screen.getByRole('button', { name: /^create$/i }));

            expect(mockOnCreateLink).toHaveBeenCalledWith(
                expect.objectContaining({
                    fromComponentId: 'comp1',
                    toNodeId: 'node1',
                    event: 'click',
                })
            );
        });

        it('should disable Create button when fields are empty', async () => {
            const user = userEvent.setup();
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            await user.click(screen.getByRole('button', { name: /new link/i }));

            const createButton = screen.getByRole('button', { name: /^create$/i });
            expect(createButton).toBeDisabled();
        });
    });

    describe('Link List', () => {
        it('should display existing links', () => {
            render(
                <PrototypeLinkTool
                    links={mockLinks}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            expect(screen.getByText(/button 1.*home screen/i)).toBeInTheDocument();
        });

        it('should show empty state when no links', () => {
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            expect(screen.getByText(/no prototype links yet/i)).toBeInTheDocument();
        });

        it('should display link count', () => {
            render(
                <PrototypeLinkTool
                    links={mockLinks}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            expect(screen.getByText(/existing links \(1\)/i)).toBeInTheDocument();
        });
    });

    describe('Link Editing', () => {
        it('should show edit button for each link', () => {
            render(
                <PrototypeLinkTool
                    links={mockLinks}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            expect(screen.getByLabelText(/edit/i)).toBeInTheDocument();
        });

        it('should populate form when editing a link', async () => {
            const user = userEvent.setup();
            render(
                <PrototypeLinkTool
                    links={mockLinks}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            await user.click(screen.getByLabelText(/edit/i));

            expect(screen.getByText('Edit Link')).toBeInTheDocument();
            expect(screen.getByDisplayValue('comp1')).toBeInTheDocument();
        });
    });

    describe('Link Deletion', () => {
        it('should show delete button for each link', () => {
            render(
                <PrototypeLinkTool
                    links={mockLinks}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            expect(screen.getByLabelText(/delete/i)).toBeInTheDocument();
        });

        it('should call onDeleteLink when clicking delete', async () => {
            const user = userEvent.setup();
            render(
                <PrototypeLinkTool
                    links={mockLinks}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            await user.click(screen.getByLabelText(/delete/i));

            expect(mockOnDeleteLink).toHaveBeenCalledWith('link1');
        });
    });

    describe('Event Types', () => {
        it('should support click event', async () => {
            const user = userEvent.setup();
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            await user.click(screen.getByRole('button', { name: /new link/i }));

            const eventSelect = screen.getByLabelText(/^event$/i);
            await user.click(eventSelect);

            expect(screen.getByText('Click')).toBeInTheDocument();
        });

        it('should display event type badges for links', () => {
            render(
                <PrototypeLinkTool
                    links={mockLinks}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            expect(screen.getByText('Click')).toBeInTheDocument();
        });
    });

    describe('Transitions', () => {
        it('should support transition types', async () => {
            const user = userEvent.setup();
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            await user.click(screen.getByRole('button', { name: /new link/i }));

            const transitionSelect = screen.getByLabelText(/transition/i);
            await user.click(transitionSelect);

            expect(screen.getByText('Slide')).toBeInTheDocument();
            expect(screen.getByText('Fade')).toBeInTheDocument();
        });

        it('should display transition badges for non-instant links', () => {
            render(
                <PrototypeLinkTool
                    links={mockLinks}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            expect(screen.getByText(/slide \(300ms\)/i)).toBeInTheDocument();
        });
    });

    describe('Link Mode', () => {
        it('should show alert when link mode is active', () => {
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                    linkModeActive={true}
                />
            );

            expect(screen.getByText(/click a target node/i)).toBeInTheDocument();
        });

        it('should auto-populate from component in link mode', () => {
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                    linkModeActive={true}
                    activeComponentId="comp1"
                />
            );

            expect(screen.getByText('Create Link')).toBeInTheDocument();
        });
    });

    describe('Edge Cases', () => {
        it('should handle empty components array', () => {
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={[]}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            expect(screen.getByText('Prototype Links')).toBeInTheDocument();
        });

        it('should handle empty nodes array', () => {
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={[]}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            expect(screen.getByText('Prototype Links')).toBeInTheDocument();
        });

        it('should handle form cancel', async () => {
            const user = userEvent.setup();
            render(
                <PrototypeLinkTool
                    links={[]}
                    components={mockComponents}
                    nodes={mockNodes}
                    onCreateLink={mockOnCreateLink}
                    onUpdateLink={mockOnUpdateLink}
                    onDeleteLink={mockOnDeleteLink}
                />
            );

            await user.click(screen.getByRole('button', { name: /new link/i }));
            await user.click(screen.getByRole('button', { name: /cancel/i }));

            expect(screen.queryByText('Create Link')).not.toBeInTheDocument();
        });
    });
});
