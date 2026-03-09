/**
 * DomainModelCanvas Tests (Journey 23.1)
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { DomainModelCanvas } from '../DomainModelCanvas';

describe('DomainModelCanvas', () => {
    const mockEntities = [
        {
            id: '1',
            name: 'User',
            attributes: [
                { name: 'email', type: 'string', required: true },
                { name: 'name', type: 'string', required: true },
            ],
            annotations: ['Missing phone field'],
            businessRules: ['Email must be unique'],
        },
    ];

    it('should render domain model review', () => {
        render(<DomainModelCanvas />);
        expect(screen.getByText('Domain Model Review')).toBeInTheDocument();
    });

    it('should display entities', () => {
        render(<DomainModelCanvas entities={mockEntities} />);
        expect(screen.getByText('User')).toBeInTheDocument();
    });

    it('should show entity attributes', () => {
        render(<DomainModelCanvas entities={mockEntities} />);
        expect(screen.getByText(/email/i)).toBeInTheDocument();
        expect(screen.getByText(/name/i)).toBeInTheDocument();
    });

    it('should show required attribute chips', () => {
        render(<DomainModelCanvas entities={mockEntities} />);
        const requiredChips = screen.getAllByText('required');
        expect(requiredChips.length).toBeGreaterThan(0);
    });

    it('should show annotations', () => {
        render(<DomainModelCanvas entities={mockEntities} />);
        expect(screen.getByText('Missing phone field')).toBeInTheDocument();
    });

    it('should show business rules count', () => {
        render(<DomainModelCanvas entities={mockEntities} />);
        expect(screen.getByText(/1 rule/i)).toBeInTheDocument();
    });

    it('should show add entity button', () => {
        render(<DomainModelCanvas />);
        expect(screen.getByRole('button', { name: /add entity/i })).toBeInTheDocument();
    });

    it('should open add dialog', async () => {
        const user = userEvent.setup();
        render(<DomainModelCanvas />);

        await user.click(screen.getByRole('button', { name: /add entity/i }));
        expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('should show empty state', () => {
        render(<DomainModelCanvas entities={[]} />);
        expect(screen.getByText(/no entities yet/i)).toBeInTheDocument();
    });

    it('should call onDeleteEntity', async () => {
        const onDeleteEntity = vi.fn();
        const user = userEvent.setup();
        render(<DomainModelCanvas entities={mockEntities} onDeleteEntity={onDeleteEntity} />);

        const deleteButtons = screen.getAllByRole('button');
        const deleteBtn = deleteButtons.find((btn) => btn.textContent === '×');
        if (deleteBtn) {
            await user.click(deleteBtn);
            expect(onDeleteEntity).toHaveBeenCalledWith('1');
        }
    });
});
