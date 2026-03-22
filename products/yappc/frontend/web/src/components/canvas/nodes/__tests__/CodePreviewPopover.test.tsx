/**
 * Unit Tests for CodePreviewPopover Component
 * 
 * @doc.type test
 * @doc.purpose Unit tests for code preview UI
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CodePreviewPopover } from '../CodePreviewPopover';
import type { CodeAssociation } from '@/hooks/useCodeAssociations';

describe('CodePreviewPopover', () => {
    const mockAssociations: CodeAssociation[] = [
        {
            id: 'assoc-1',
            artifactId: 'art-1',
            codeArtifactId: 'code-1',
            relationship: 'IMPLEMENTATION',
            createdAt: '2026-01-17T00:00:00Z',
            updatedAt: '2026-01-17T00:00:00Z',
            codeArtifact: {
                id: 'code-1',
                title: 'UserService.ts',
                description: 'User management service',
                content: 'export class UserService {\n  async getUser(id: string) {\n    return db.users.findUnique({ where: { id } });\n  }\n}',
                format: 'typescript',
                type: 'CODE',
            },
        },
        {
            id: 'assoc-2',
            artifactId: 'art-1',
            codeArtifactId: 'code-2',
            relationship: 'TEST',
            createdAt: '2026-01-17T00:00:00Z',
            updatedAt: '2026-01-17T00:00:00Z',
            codeArtifact: {
                id: 'code-2',
                title: 'UserService.test.ts',
                description: 'Tests for UserService',
                content: 'describe("UserService", () => {\n  it("should get user by id", async () => {\n    const user = await service.getUser("123");\n    expect(user).toBeDefined();\n  });\n});',
                format: 'typescript',
                type: 'TEST',
            },
        },
    ];

    const mockAnchorEl = document.createElement('div');

    it('renders nothing when no associations', () => {
        const { container } = render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={[]}
                onClose={() => { }}
            />
        );

        expect(container.firstChild).toBeNull();
    });

    it('renders popover with associations list', () => {
        render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={mockAssociations}
                onClose={() => { }}
            />
        );

        expect(screen.getByText('Linked Code')).toBeInTheDocument();
        expect(screen.getByText('UserService.ts')).toBeInTheDocument();
        expect(screen.getByText('UserService.test.ts')).toBeInTheDocument();
    });

    it('displays first association by default', () => {
        render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={mockAssociations}
                onClose={() => { }}
            />
        );

        expect(screen.getByText('User management service')).toBeInTheDocument();
        expect(screen.getByText(/export class UserService/)).toBeInTheDocument();
    });

    it('switches association when clicking list item', async () => {
        render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={mockAssociations}
                onClose={() => { }}
            />
        );

        // Click on test association
        fireEvent.click(screen.getByText('UserService.test.ts'));

        await waitFor(() => {
            expect(screen.getByText('Tests for UserService')).toBeInTheDocument();
            expect(screen.getByText(/describe\("UserService"/)).toBeInTheDocument();
        });
    });

    it('shows relationship chip with correct color', () => {
        render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={mockAssociations}
                onClose={() => { }}
            />
        );

        const implementationChip = screen.getByText('IMPLEMENTATION');
        expect(implementationChip).toBeInTheDocument();
        expect(implementationChip.closest('.MuiChip-root')).toHaveClass('MuiChip-colorPrimary');
    });

    it('calls onClose when close button clicked', () => {
        const onClose = jest.fn();

        render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={mockAssociations}
                onClose={onClose}
            />
        );

        const closeButton = screen.getByRole('button', { name: /close/i });
        fireEvent.click(closeButton);

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onOpenCode when open editor button clicked', () => {
        const onOpenCode = jest.fn();

        render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={mockAssociations}
                onClose={() => { }}
                onOpenCode={onOpenCode}
            />
        );

        const openButton = screen.getByText('Open Full Editor');
        fireEvent.click(openButton);

        expect(onOpenCode).toHaveBeenCalledWith('code-1');
    });

    it('calls onDeleteAssociation and switches to next', async () => {
        const onDeleteAssociation = jest.fn();

        render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={mockAssociations}
                onClose={() => { }}
                onDeleteAssociation={onDeleteAssociation}
            />
        );

        // Delete first association
        const deleteButton = screen.getByTitle('Remove association');
        fireEvent.click(deleteButton);

        expect(onDeleteAssociation).toHaveBeenCalledWith('assoc-1');

        // Should show second association
        await waitFor(() => {
            expect(screen.getByText('Tests for UserService')).toBeInTheDocument();
        });
    });

    it('calls onClose when deleting last association', () => {
        const onClose = jest.fn();
        const onDeleteAssociation = jest.fn();

        render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={[mockAssociations[0]]}
                onClose={onClose}
                onDeleteAssociation={onDeleteAssociation}
            />
        );

        const deleteButton = screen.getByTitle('Remove association');
        fireEvent.click(deleteButton);

        expect(onDeleteAssociation).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
    });

    it('shows "No code content available" when content is missing', () => {
        const noContentAssociation: CodeAssociation = {
            ...mockAssociations[0],
            codeArtifact: {
                ...mockAssociations[0].codeArtifact!,
                content: undefined,
            },
        };

        render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={[noContentAssociation]}
                onClose={() => { }}
            />
        );

        expect(screen.getByText('No code content available')).toBeInTheDocument();
    });

    it('displays code format badge', () => {
        render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={mockAssociations}
                onClose={() => { }}
            />
        );

        expect(screen.getByText('typescript')).toBeInTheDocument();
        expect(screen.getByText('CODE')).toBeInTheDocument();
    });

    it('renders all relationship types with correct icons', () => {
        const allTypes: CodeAssociation[] = [
            { ...mockAssociations[0], relationship: 'IMPLEMENTATION' },
            { ...mockAssociations[0], id: 'assoc-2', relationship: 'TEST' },
            { ...mockAssociations[0], id: 'assoc-3', relationship: 'DOCUMENTATION' },
            { ...mockAssociations[0], id: 'assoc-4', relationship: 'MOCK' },
        ];

        render(
            <CodePreviewPopover
                anchorEl={mockAnchorEl}
                associations={allTypes}
                onClose={() => { }}
            />
        );

        // Verify all relationship chips are present
        expect(screen.getAllByText(/IMPLEMENTATION|TEST|DOCUMENTATION|MOCK/)).toHaveLength(4);
    });
});
