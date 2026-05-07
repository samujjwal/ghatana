import { describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { render } from '@/test-utils/test-utils';
import { ValidationPanel, type ValidationIssue } from '../ValidationPanel';

describe('ValidationPanel', () => {
  it('renders the empty validation state with an accessible design-system icon', () => {
    const { container } = render(<ValidationPanel issues={[]} />);

    expect(screen.getByLabelText('Validation passed')).toBeInTheDocument();
    expect(screen.getByText('No issues found')).toBeInTheDocument();
    expect(container).not.toHaveTextContent('✅');
  });

  it('renders severity and suggestion icons without raw emoji status indicators', () => {
    const issues: ValidationIssue[] = [
      {
        id: 'issue-1',
        type: 'typescript',
        severity: 'error',
        message: 'Property is missing',
        file: 'src/App.tsx',
        line: 12,
        suggestion: 'Add the required prop before previewing.',
      },
      {
        id: 'issue-2',
        type: 'eslint',
        severity: 'warning',
        message: 'Unused variable',
      },
      {
        id: 'issue-3',
        type: 'test',
        severity: 'success',
        message: 'Unit suite passed',
      },
      {
        id: 'issue-4',
        type: 'ai',
        severity: 'info',
        message: 'Review recommended',
      },
    ];

    const onIssueClick = vi.fn();
    const { container } = render(<ValidationPanel issues={issues} onIssueClick={onIssueClick} />);

    expect(screen.getByLabelText('Error')).toBeInTheDocument();
    expect(screen.getByLabelText('Warning')).toBeInTheDocument();
    expect(screen.getByLabelText('Passed')).toBeInTheDocument();
    expect(screen.getByLabelText('Information')).toBeInTheDocument();
    expect(screen.getByLabelText('Suggestion')).toBeInTheDocument();
    expect(container).not.toHaveTextContent('❌');
    expect(container).not.toHaveTextContent('⚠️');
    expect(container).not.toHaveTextContent('✅');
    expect(container).not.toHaveTextContent('💡');

    fireEvent.click(screen.getByText('Property is missing'));
    expect(onIssueClick).toHaveBeenCalledWith(issues[0]);
  });
});
