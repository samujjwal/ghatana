import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ValidationPanel, type ValidationIssue } from '../ValidationPanel';

// =============================================================================
// Helpers
// =============================================================================

function makeIssue(overrides: Partial<ValidationIssue> = {}): ValidationIssue {
  return {
    id: 'issue-1',
    severity: 'error',
    message: 'Something went wrong',
    ...overrides,
  };
}

function makeProps(
  overrides: Partial<{
    issues: ValidationIssue[];
    onResolve: (id: string) => void;
    onIgnore: (id: string) => void;
    className: string;
  }> = {}
) {
  return {
    onResolve: vi.fn(),
    onIgnore: vi.fn(),
    ...overrides,
  };
}

// =============================================================================
// Tests
// =============================================================================

describe('ValidationPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ---------------------------------------------------------------------------
  // Empty state
  // ---------------------------------------------------------------------------

  it('renders the Validation header', () => {
    render(<ValidationPanel {...makeProps()} />);
    expect(screen.getByText('Validation')).toBeDefined();
  });

  it('shows empty state when no issues are provided', () => {
    render(<ValidationPanel {...makeProps()} />);
    expect(screen.getByText('No issues found')).toBeDefined();
    expect(screen.getByText('All validations passed')).toBeDefined();
  });

  it('does not show severity badges in the header when there are no issues', () => {
    render(<ValidationPanel {...makeProps()} />);
    expect(screen.queryByText(/error/i)).toBeNull();
    expect(screen.queryByText(/warning/i)).toBeNull();
    expect(screen.queryByText(/info/i)).toBeNull();
  });

  // ---------------------------------------------------------------------------
  // Header severity badges
  // ---------------------------------------------------------------------------

  it('shows singular "1 error" badge when there is exactly one error', () => {
    const issues = [makeIssue({ id: '1', severity: 'error' })];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByText('1 error')).toBeDefined();
  });

  it('shows plural "2 errors" badge when there are two errors', () => {
    const issues = [
      makeIssue({ id: '1', severity: 'error' }),
      makeIssue({ id: '2', severity: 'error' }),
    ];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByText('2 errors')).toBeDefined();
  });

  it('shows warning badge with count', () => {
    const issues = [makeIssue({ id: '1', severity: 'warning' })];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByText('1 warning')).toBeDefined();
  });

  it('shows info badge with count', () => {
    const issues = [makeIssue({ id: '1', severity: 'info' })];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByText('1 info')).toBeDefined();
  });

  it('shows all three severity badges when mixed issues are provided', () => {
    const issues = [
      makeIssue({ id: '1', severity: 'error' }),
      makeIssue({ id: '2', severity: 'warning' }),
      makeIssue({ id: '3', severity: 'info' }),
    ];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByText('1 error')).toBeDefined();
    expect(screen.getByText('1 warning')).toBeDefined();
    expect(screen.getByText('1 info')).toBeDefined();
  });

  // ---------------------------------------------------------------------------
  // Issue row rendering
  // ---------------------------------------------------------------------------

  it('renders issue message as the title text when no title is provided', () => {
    const issues = [makeIssue({ message: 'Missing required field', title: undefined })];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByText('Missing required field')).toBeDefined();
  });

  it('renders issue title when provided', () => {
    const issues = [
      makeIssue({ title: 'Auth Error', message: 'Token validation failed' }),
    ];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByText('Auth Error')).toBeDefined();
  });

  it('shows message as subtitle when title is different from message', () => {
    const issues = [
      makeIssue({ title: 'Auth Error', message: 'Token validation failed' }),
    ];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByText('Token validation failed')).toBeDefined();
  });

  it('renders description when provided', () => {
    const issues = [
      makeIssue({ description: 'Check your API key configuration' }),
    ];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByText('Check your API key configuration')).toBeDefined();
  });

  it('shows the correct severity label badge on each row', () => {
    const issues = [makeIssue({ severity: 'warning', message: 'Low memory' })];
    render(<ValidationPanel {...makeProps({ issues })} />);
    // The severity label badge inside the row
    const badges = screen.getAllByText('Warning');
    expect(badges.length).toBeGreaterThan(0);
  });

  it('renders multiple issue rows', () => {
    const issues = [
      makeIssue({ id: '1', message: 'First issue' }),
      makeIssue({ id: '2', message: 'Second issue', severity: 'warning' }),
    ];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByText('First issue')).toBeDefined();
    expect(screen.getByText('Second issue')).toBeDefined();
  });

  // ---------------------------------------------------------------------------
  // Action buttons
  // ---------------------------------------------------------------------------

  it('renders Fix button when onResolve is provided', () => {
    const issues = [makeIssue()];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByTitle('Mark as resolved')).toBeDefined();
  });

  it('calls onResolve with the issue id when Fix is clicked', () => {
    const onResolve = vi.fn();
    const issues = [makeIssue({ id: 'issue-abc' })];
    render(<ValidationPanel {...makeProps({ issues, onResolve })} />);
    fireEvent.click(screen.getByTitle('Mark as resolved'));
    expect(onResolve).toHaveBeenCalledWith('issue-abc');
  });

  it('renders Ignore button when onIgnore is provided', () => {
    const issues = [makeIssue()];
    render(<ValidationPanel {...makeProps({ issues })} />);
    expect(screen.getByTitle('Ignore this issue')).toBeDefined();
  });

  it('calls onIgnore with the issue id when Ignore is clicked', () => {
    const onIgnore = vi.fn();
    const issues = [makeIssue({ id: 'issue-xyz' })];
    render(<ValidationPanel {...makeProps({ issues, onIgnore })} />);
    fireEvent.click(screen.getByTitle('Ignore this issue'));
    expect(onIgnore).toHaveBeenCalledWith('issue-xyz');
  });

  it('does not render Fix button when onResolve is not provided', () => {
    const issues = [makeIssue()];
    render(<ValidationPanel issues={issues} onIgnore={vi.fn()} />);
    expect(screen.queryByTitle('Mark as resolved')).toBeNull();
  });

  it('does not render Ignore button when onIgnore is not provided', () => {
    const issues = [makeIssue()];
    render(<ValidationPanel issues={issues} onResolve={vi.fn()} />);
    expect(screen.queryByTitle('Ignore this issue')).toBeNull();
  });
});
