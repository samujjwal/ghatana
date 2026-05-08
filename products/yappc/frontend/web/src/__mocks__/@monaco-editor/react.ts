/**
 * Mock for @monaco-editor/react package
 * Used in vitest environment where monaco-editor is not installed
 */
import React from 'react';
import { vi } from 'vitest';

type MockCallback = (...args: unknown[]) => unknown;

const createMockCallback = (): MockCallback => vi.fn() as MockCallback;

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
export const DiffEditor = (): null => null;
export const useMonaco = (): null => null;

interface MonacoLoaderMock {
  init: () => Promise<void>;
  config: MockCallback;
}

export const loader: MonacoLoaderMock = {
  init: () => Promise.resolve(),
  config: createMockCallback(),
};
