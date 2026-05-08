import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { InlineCodePanel } from '../InlineCodePanel';

describe('InlineCodePanel', () => {
  it('renders editor actions as shared UI buttons and invokes callbacks', () => {
    const onFormat = vi.fn();
    const onRun = vi.fn();
    const onAIFix = vi.fn();
    const onToggle = vi.fn();

    render(
      <InlineCodePanel
        code="const value = 1;"
        fileName="example.ts"
        onFormat={onFormat}
        onRun={onRun}
        onAIFix={onAIFix}
        onToggle={onToggle}
      />
    );

    const format = screen.getByRole('button', { name: /format/i });
    const run = screen.getByRole('button', { name: /run/i });

    expect(format).toHaveClass('inline-flex');
    expect(run).toHaveClass('inline-flex');

    fireEvent.click(format);
    fireEvent.click(run);
    fireEvent.click(screen.getByRole('button', { name: /ai fix/i }));
    fireEvent.click(screen.getByRole('button', { name: /hide code panel/i }));

    expect(onFormat).toHaveBeenCalled();
    expect(onRun).toHaveBeenCalled();
    expect(onAIFix).toHaveBeenCalled();
    expect(onToggle).toHaveBeenCalled();
  });

  it('edits code through the shared textarea primitive', () => {
    const onCodeChange = vi.fn();

    render(<InlineCodePanel code="old" onCodeChange={onCodeChange} />);

    const editor = screen.getByRole('textbox', { name: /code editor/i });

    expect(editor).toHaveClass('font-mono');

    fireEvent.change(editor, { target: { value: 'new code' } });

    expect(onCodeChange).toHaveBeenCalledWith('new code');
  });

  it('minimizes and expands the panel with shared UI buttons', () => {
    render(<InlineCodePanel code="" fileName="example.ts" />);

    fireEvent.click(screen.getByTitle(/minimize/i));

    const expand = screen.getByRole('button', { name: /expand/i });

    expect(expand).toHaveClass('inline-flex');
    expect(screen.getByText('example.ts')).toBeInTheDocument();

    fireEvent.click(expand);

    expect(screen.getByLabelText(/code editor panel/i)).toBeInTheDocument();
  });
});
