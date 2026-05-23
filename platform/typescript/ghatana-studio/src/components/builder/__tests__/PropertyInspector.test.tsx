import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { ComponentInstance, NodeId } from '@ghatana/ui-builder';

import { PropertyInspector } from '../PropertyInspector';

function makeInstance(props: Record<string, unknown>): ComponentInstance {
  return {
    id: 'node-1' as NodeId,
    contractName: 'Button',
    props,
    slots: {},
    bindings: [],
    metadata: {
      name: 'Button',
    },
  };
}

describe('PropertyInspector', () => {
  it('rejects malformed JSON-like edits without mutating the selected property', () => {
    const onPropertyUpdate = vi.fn();
    render(
      <PropertyInspector
        selectedInstance={makeInstance({ variant: 'solid' })}
        onPropertyUpdate={onPropertyUpdate}
      />,
    );

    fireEvent.click(screen.getByTestId('builder-property-value-variant'));
    fireEvent.change(screen.getByTestId('builder-property-input-variant'), {
      target: { value: '{"mode":' },
    });
    fireEvent.click(screen.getByTestId('builder-property-save-variant'));

    expect(screen.getByTestId('builder-property-validation-variant')).toHaveTextContent('Enter valid JSON');
    expect(screen.getByTestId('builder-property-input-variant')).toHaveAttribute('aria-invalid', 'true');
    expect(onPropertyUpdate).not.toHaveBeenCalled();
  });

  it('still accepts plain text edits that are not JSON-like', () => {
    const onPropertyUpdate = vi.fn();
    render(
      <PropertyInspector
        selectedInstance={makeInstance({ variant: 'solid' })}
        onPropertyUpdate={onPropertyUpdate}
      />,
    );

    fireEvent.click(screen.getByTestId('builder-property-value-variant'));
    fireEvent.change(screen.getByTestId('builder-property-input-variant'), {
      target: { value: 'outline' },
    });
    fireEvent.click(screen.getByTestId('builder-property-save-variant'));

    expect(onPropertyUpdate).toHaveBeenCalledWith('node-1', 'variant', 'outline');
  });
});
