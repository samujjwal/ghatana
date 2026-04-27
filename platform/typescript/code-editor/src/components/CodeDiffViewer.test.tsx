import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { CodeDiffViewer } from './CodeDiffViewer';

vi.mock('@monaco-editor/react', () => {
  return {
    DiffEditor: ({
      original,
      modified,
      language,
      theme,
    }: {
      original: string;
      modified: string;
      language: string;
      theme: string;
    }) => (
      <div
        data-testid="diff-editor"
        data-original={original}
        data-modified={modified}
        data-language={language}
        data-theme={theme}
      />
    ),
  };
});

describe('CodeDiffViewer', () => {
  it('renders original and modified code in diff editor', () => {
    render(
      <CodeDiffViewer
        original="const oldValue = 1;"
        modified="const newValue = 2;"
        language="typescript"
      />
    );

    const editor = screen.getByTestId('diff-editor');
    expect(editor.getAttribute('data-original')).toBe('const oldValue = 1;');
    expect(editor.getAttribute('data-modified')).toBe('const newValue = 2;');
    expect(editor.getAttribute('data-language')).toBe('typescript');
  });
});
