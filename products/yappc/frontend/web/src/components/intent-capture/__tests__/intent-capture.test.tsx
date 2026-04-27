/**
 * Intent Capture Component Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { RequirementList } from '../RequirementList';

const mockRequirements = [
    {
        id: '1',
        title: 'User authentication',
        description: 'Users must be able to log in',
        type: 'functional',
        priority: 'high',
        status: 'open',
    },
    {
        id: '2',
        title: 'Performance requirement',
        description: 'Response within 200ms',
        type: 'non-functional',
        priority: 'medium',
        status: 'in-progress',
    },
];

describe('RequirementList', () => {
    it('renders empty state when no requirements', () => {
        render(<RequirementList requirements={[]} />);
        expect(screen.getByText(/no requirements/i)).toBeTruthy();
    });

    it('renders list of requirements', () => {
        render(<RequirementList requirements={mockRequirements} />);
        expect(screen.getByText('User authentication')).toBeTruthy();
        expect(screen.getByText('Performance requirement')).toBeTruthy();
    });

    it('filters by search term', () => {
        render(<RequirementList requirements={mockRequirements} />);
        const searchInput = screen.getByRole('textbox');
        fireEvent.change(searchInput, { target: { value: 'authentication' } });
        expect(screen.getByText('User authentication')).toBeTruthy();
        expect(screen.queryByText('Performance requirement')).toBeNull();
    });

    it('calls onRequirementSelect when item clicked', () => {
        const onSelect = vi.fn();
        render(<RequirementList requirements={mockRequirements} onRequirementSelect={onSelect} />);
        fireEvent.click(screen.getByText('User authentication'));
        expect(onSelect).toHaveBeenCalledWith(mockRequirements[0]);
    });
});
