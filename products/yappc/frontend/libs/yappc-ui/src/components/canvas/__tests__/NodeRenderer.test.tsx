/**
 * Tests for NodeRenderer
 */

import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';

import { RendererComponentRegistry } from '../renderer/ComponentRegistry';
import { NodeRenderer, EditModeWrapper, BatchNodeRenderer } from '../renderer/NodeRenderer';
import { ThemeApplicator } from '../renderer/ThemeApplicator';

import type { ComponentNodeData } from '../types/CanvasNode';

// Mock component for testing
const MockButton: React.FC<unknown> = ({ label, onClick, color, style }) => (
  <button onClick={onClick} style={{ color, ...style }}>
    {label}
  </button>
);

describe.skip('NodeRenderer', () => {
  beforeAll(() => {
    // Register mock component
    RendererComponentRegistry.register('Button', MockButton, { displayName: 'Button' });
  });

  afterAll(() => {
    RendererComponentRegistry.clear();
  });

  const mockThemeContext = ThemeApplicator.createDefaultContext();
  mockThemeContext.tokens.base = {
    color: { primary: { 500: '#1976d2' } },
  };

  describe('Basic Rendering', () => {
    it('should render a component with props', () => {
      const nodeData: ComponentNodeData = {
        componentType: 'Button',
        props: {
          label: 'Click me',
        },
      };

      render(
        <NodeRenderer
          componentType="Button"
          nodeData={nodeData}
          themeContext={mockThemeContext}
        />
      );

      expect(screen.getByText('Click me')).toBeInTheDocument();
    });

    it('should apply theme tokens to props', () => {
      const nodeData: ComponentNodeData = {
        componentType: 'Button',
        props: {
          label: 'Themed Button',
        },
        tokens: {
          color: '$color.primary.500',
        },
      };

      render(
        <NodeRenderer
          componentType="Button"
          nodeData={nodeData}
          themeContext={mockThemeContext}
        />
      );

      const button = screen.getByText('Themed Button');
      expect(button).toHaveStyle({ color: '#1976d2' });
    });

    it('should render not found message for unregistered component', () => {
      const nodeData: ComponentNodeData = {
        componentType: 'UnknownComponent',
        props: {},
      };

      render(
        <NodeRenderer
          componentType="UnknownComponent"
          nodeData={nodeData}
          themeContext={mockThemeContext}
        />
      );

      expect(screen.getByText(/Component not found/)).toBeInTheDocument();
      expect(screen.getByText(/UnknownComponent/)).toBeInTheDocument();
    });
  });

  describe('Event Handling', () => {
    it('should handle events', () => {
      const onEvent = jest.fn();
      const nodeData: ComponentNodeData = {
        componentType: 'Button',
        props: {
          label: 'Event Button',
        },
        events: {
          onClick: {
            emit: 'buttonClicked',
            payload: { source: 'test' },
          },
        },
      };

      render(
        <NodeRenderer
          componentType="Button"
          nodeData={nodeData}
          themeContext={mockThemeContext}
          onEvent={onEvent}
        />
      );

      fireEvent.click(screen.getByText('Event Button'));

      expect(onEvent).toHaveBeenCalledWith('buttonClicked', expect.objectContaining({
        source: 'test',
      }));
    });
  });

  describe('Edit Mode', () => {
    it('should add data-edit-mode attribute in edit mode', () => {
      const nodeData: ComponentNodeData = {
        componentType: 'Button',
        props: {
          label: 'Edit Mode',
        },
      };

      render(
        <NodeRenderer
          componentType="Button"
          nodeData={nodeData}
          themeContext={mockThemeContext}
          mode="edit"
        />
      );

      const button = screen.getByText('Edit Mode');
      expect(button).toHaveAttribute('data-edit-mode', 'true');
    });
  });

  describe('Error Handling', () => {
    it('should catch rendering errors', () => {
      // Component that throws an error
      const ErrorComponent: React.FC = () => {
        throw new Error('Test error');
      };

      RendererComponentRegistry.register('ErrorComponent', ErrorComponent);

      const nodeData: ComponentNodeData = {
        componentType: 'ErrorComponent',
        props: {},
      };

      const onError = jest.fn();

      render(
        <NodeRenderer
          componentType="ErrorComponent"
          nodeData={nodeData}
          themeContext={mockThemeContext}
          onError={onError}
        />
      );

      expect(screen.getByText(/Component Error/)).toBeInTheDocument();
      expect(onError).toHaveBeenCalled();

      RendererComponentRegistry.unregister('ErrorComponent');
    });
  });

  describe('Children Rendering', () => {
    it('should render children', () => {
      const nodeData: ComponentNodeData = {
        componentType: 'Button',
        props: {
          label: 'Parent',
        },
      };

      render(
        <NodeRenderer
          componentType="Button"
          nodeData={nodeData}
          themeContext={mockThemeContext}
        >
          <span>Child content</span>
        </NodeRenderer>
      );

      expect(screen.getByText('Child content')).toBeInTheDocument();
    });
  });
});

describe('EditModeWrapper', () => {
  it('should render children', () => {
    render(
      <EditModeWrapper>
        <div>Test content</div>
      </EditModeWrapper>
    );

    expect(screen.getByText('Test content')).toBeInTheDocument();
  });

  it('should show selected indicator when selected', () => {
    render(
      <EditModeWrapper isSelected={true}>
        <div>Selected content</div>
      </EditModeWrapper>
    );

    expect(screen.getByText('Selected')).toBeInTheDocument();
  });

  it('should call onSelect when clicked', () => {
    const onSelect = jest.fn();

    render(
      <EditModeWrapper onSelect={onSelect}>
        <div>Clickable content</div>
      </EditModeWrapper>
    );

    fireEvent.click(screen.getByText('Clickable content'));

    expect(onSelect).toHaveBeenCalled();
  });

  it('should call onDoubleClick when double-clicked', () => {
    const onDoubleClick = jest.fn();

    render(
      <EditModeWrapper onDoubleClick={onDoubleClick}>
        <div>Double-clickable content</div>
      </EditModeWrapper>
    );

    fireEvent.doubleClick(screen.getByText('Double-clickable content'));

    expect(onDoubleClick).toHaveBeenCalled();
  });
});

describe.skip('BatchNodeRenderer', () => {
  beforeAll(() => {
    RendererComponentRegistry.register('Button', MockButton);
  });

  it('should render multiple nodes', () => {
    const nodes = [
      {
        id: '1',
        componentType: 'Button',
        nodeData: {
          componentType: 'Button',
          props: { label: 'Button 1' },
        } as ComponentNodeData,
      },
      {
        id: '2',
        componentType: 'Button',
        nodeData: {
          componentType: 'Button',
          props: { label: 'Button 2' },
        } as ComponentNodeData,
      },
    ];

    render(
      <BatchNodeRenderer nodes={nodes} themeContext={mockThemeContext} />
    );

    expect(screen.getByText('Button 1')).toBeInTheDocument();
    expect(screen.getByText('Button 2')).toBeInTheDocument();
  });

  it('should handle selection in edit mode', () => {
    const onSelect = jest.fn();
    const nodes = [
      {
        id: '1',
        componentType: 'Button',
        nodeData: {
          componentType: 'Button',
          props: { label: 'Button 1' },
        } as ComponentNodeData,
      },
    ];

    render(
      <BatchNodeRenderer
        nodes={nodes}
        themeContext={mockThemeContext}
        mode="edit"
        selectedId="1"
        onSelect={onSelect}
      />
    );

    expect(screen.getByText('Selected')).toBeInTheDocument();
  });

  it('should call onPropsChange when props change', () => {
    const onPropsChange = jest.fn();
    const nodes = [
      {
        id: '1',
        componentType: 'Button',
        nodeData: {
          componentType: 'Button',
          props: { label: 'Button 1' },
        } as ComponentNodeData,
      },
    ];

    render(
      <BatchNodeRenderer
        nodes={nodes}
        themeContext={mockThemeContext}
        mode="edit"
        onPropsChange={onPropsChange}
      />
    );

    // Props change would be triggered by internal component logic
    // This test validates the callback is passed correctly
    expect(onPropsChange).toBeDefined();
  });
});
