import '@testing-library/jest-dom';
import React from 'react';
import { vi } from 'vitest';

vi.mock('@monaco-editor/react', () => {
  const MockMonacoEditor = (props: Record<string, unknown>) =>
    React.createElement(
      'div',
      {
        'data-testid': 'monaco-editor',
        'data-language': props.language,
        'data-theme': props.theme,
        'data-value': props.value,
        onClick: () => {
          const onChange = props.onChange;
          if (typeof onChange === 'function') {
            onChange('new value');
          }
        },
      },
      'Monaco Editor Bundle',
    );

  MockMonacoEditor.displayName = 'MockMonacoEditor';
  return { default: MockMonacoEditor };
});
