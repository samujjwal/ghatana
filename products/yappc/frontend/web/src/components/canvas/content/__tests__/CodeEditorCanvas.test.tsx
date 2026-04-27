import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { CodeEditorCanvas } from '../CodeEditorCanvas';

vi.mock('../../../../utils/canvasPersistence', () => ({
  useCanvasPersistence: () => ({
    save: vi.fn(),
    load: vi.fn(() => null),
    clear: vi.fn(),
  }),
}));

vi.mock('@ghatana/code-editor', () => ({
  CodeEditor: ({ value }: { value: string }) => (
    <div data-testid="mock-code-editor">{value}</div>
  ),
  CodeDiffViewer: ({ original, modified }: { original: string; modified: string }) => (
    <div
      data-testid="mock-code-diff-viewer"
      data-original={original}
      data-modified={modified}
    />
  ),
  VisualBlockEditor: () => <div data-testid="mock-visual-block-editor" />,
}));

describe('CodeEditorCanvas', () => {
  it('renders default editor mode with canonical code editor', () => {
    render(<CodeEditorCanvas />);

    fireEvent.click(screen.getByRole('button', { name: 'Start Coding' }));

    expect(screen.getByTestId('code-editor-canvas-content')).toBeTruthy();
    expect(screen.getByTestId('mock-code-editor')).toBeTruthy();
  });

  it('switches between editor, diff, and visual modes', () => {
    render(<CodeEditorCanvas />);

    fireEvent.click(screen.getByRole('button', { name: 'Start Coding' }));

    fireEvent.click(screen.getByTestId('code-editor-mode-diff'));
    expect(screen.getByTestId('mock-code-diff-viewer')).toBeTruthy();

    fireEvent.click(screen.getByTestId('code-editor-mode-visual'));
    expect(screen.getByTestId('mock-visual-block-editor')).toBeTruthy();

    fireEvent.click(screen.getByTestId('code-editor-mode-editor'));
    expect(screen.getByTestId('mock-code-editor')).toBeTruthy();
  });
});
