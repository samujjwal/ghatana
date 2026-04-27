import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { VisualBlockEditor } from './VisualBlockEditor';
import type { VisualCodeBlock } from '../types';

const blocks: VisualCodeBlock[] = [
  {
    id: 'block-1',
    type: 'function',
    label: 'Create handler',
    code: 'function handler() {}',
  },
];

describe('VisualBlockEditor', () => {
  it('renders block list and generated code preview', () => {
    render(
      <VisualBlockEditor
        blocks={blocks}
        targetLanguage="typescript"
        showCodePreview
      />
    );

    expect(screen.getByTestId('visual-block-editor')).toBeTruthy();
    expect(screen.getByTestId('visual-block-block-1')).toBeTruthy();
    expect(screen.getByTestId('visual-code-preview').textContent).toContain(
      'function handler() {}'
    );
  });

  it('updates block label through onBlocksChange', () => {
    const onBlocksChange = vi.fn();
    render(
      <VisualBlockEditor
        blocks={blocks}
        onBlocksChange={onBlocksChange}
        showCodePreview={false}
      />
    );

    fireEvent.change(screen.getByLabelText('Label for block-1'), {
      target: { value: 'Updated handler' },
    });

    expect(onBlocksChange).toHaveBeenCalledTimes(1);
    const updatedBlocks = onBlocksChange.mock.calls[0][0] as VisualCodeBlock[];
    expect(updatedBlocks[0].label).toBe('Updated handler');
  });
});
