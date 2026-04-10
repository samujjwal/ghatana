/**
 * Mock for @monaco-editor/react package
 * Used in vitest environment where monaco-editor is not installed
 */
import React from 'react';
import { vi } from 'vitest';

interface EditorProps {
  value?: string;
  onChange?: (value: string | undefined) => void;
  language?: string;
  height?: string | number;
  theme?: string;
  options?: Record<string, unknown>;
  'data-testid'?: string;
}

const Editor = ({ value, onChange, 'data-testid': testId }: EditorProps) =>
  React.createElement('textarea', {
    'data-testid': testId ?? 'monaco-editor',
    value: value ?? '',
    onChange: (e: React.ChangeEvent<HTMLTextAreaElement>) => onChange?.(e.target.value),
    readOnly: !onChange,
  });

export default Editor;
export { Editor };
export const DiffEditor = vi.fn(() => null);
export const useMonaco = vi.fn(() => null);
export const loader = {
  init: vi.fn(() => Promise.resolve()),
  config: vi.fn(),
};
