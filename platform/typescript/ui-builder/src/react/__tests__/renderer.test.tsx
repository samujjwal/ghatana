/**
 * @ghatana/ui-builder/react renderer test suite
 * Tests for React renderer from BuilderDocument
 *
 * @test.type integration-browser
 * @test.execution 1-10s
 * @test.infra jsdom
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  createNodeId,
  createDocumentId,
  type BuilderDocument,
  type ComponentInstance,
} from '../../core/types';
import { ComponentRenderer } from '../ComponentRenderer';

// Mock components for testing
const MockButton = ({ label, disabled = false, onClick, children, ...props }: any) => (
  <button disabled={disabled} onClick={onClick} data-testid="mock-button" {...props}>
    {label || children}
  </button>
);

const MockTextField = ({ value, placeholder, onChange, ...props }: any) => (
  <input
    type="text"
    value={value}
    placeholder={placeholder}
    onChange={onChange}
    data-testid="mock-textfield"
    {...props}
  />
);

const MockCard = ({ title, header, content, ...props }: any) => (
  <div data-testid="mock-card" {...props}>
    <div data-testid="card-header">{header}</div>
    <div data-testid="card-content">{content}</div>
  </div>
);

const componentRegistry = {
  Button: MockButton,
  TextField: MockTextField,
  Card: MockCard,
};

describe('@ghatana/ui-builder/react - Renderer', () => {
  describe('Component Rendering', () => {
    it('should render Button component with correct label', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'Button',
        props: { label: 'Click Me' },
        slots: {},
        bindings: [],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [instance.id],
        nodes: new Map([[instance.id, instance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      render(
        <ComponentRenderer
          instance={instance}
          document={document}
          componentRegistry={componentRegistry}
        />
      );

      const button = screen.getByTestId('mock-button');
      expect(button).toBeInTheDocument();
      expect(button).toHaveTextContent('Click Me');
      expect(button).toHaveAttribute('data-builder-id', instance.id);
      expect(button).toHaveAttribute('data-builder-contract', 'Button');
    });

    it('should render TextField component with correct props', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'TextField',
        props: { value: 'test', placeholder: 'Enter text' },
        slots: {},
        bindings: [],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [instance.id],
        nodes: new Map([[instance.id, instance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      render(
        <ComponentRenderer
          instance={instance}
          document={document}
          componentRegistry={componentRegistry}
        />
      );

      const input = screen.getByTestId('mock-textfield');
      expect(input).toBeInTheDocument();
      expect(input).toHaveValue('test');
      expect(input).toHaveAttribute('placeholder', 'Enter text');
    });

    it('should render unregistered component as generic fallback', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'UnregisteredComponent',
        props: { text: 'Test' },
        slots: {},
        bindings: [],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [instance.id],
        nodes: new Map([[instance.id, instance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      render(
        <ComponentRenderer
          instance={instance}
          document={document}
          componentRegistry={componentRegistry}
        />
      );

      const fallback = screen.getByText('Unregistered component: UnregisteredComponent');
      expect(fallback).toBeInTheDocument();
      expect(fallback.closest('[data-builder-generic="true"]')).toBeInTheDocument();
    });
  });

  describe('Data Binding Resolution', () => {
    it('should resolve data binding and display bound value', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'TextField',
        props: {},
        slots: {},
        bindings: [
          {
            id: 'binding-1',
            type: 'data',
            source: 'dataSource.value',
            target: 'value',
          },
        ],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [instance.id],
        nodes: new Map([[instance.id, instance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      const bindingContext = {
        dataSource: {
          value: 'Bound Value',
        },
      };

      render(
        <ComponentRenderer
          instance={instance}
          document={document}
          bindingContext={bindingContext}
          componentRegistry={componentRegistry}
        />
      );

      const input = screen.getByTestId('mock-textfield');
      expect(input).toHaveValue('Bound Value');
    });

    it('should resolve nested data binding paths', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'TextField',
        props: {},
        slots: {},
        bindings: [
          {
            id: 'binding-1',
            type: 'data',
            source: 'user.profile.name',
            target: 'value',
          },
        ],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [instance.id],
        nodes: new Map([[instance.id, instance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      const bindingContext = {
        user: {
          profile: {
            name: 'John Doe',
          },
        },
      };

      render(
        <ComponentRenderer
          instance={instance}
          document={document}
          bindingContext={bindingContext}
          componentRegistry={componentRegistry}
        />
      );

      const input = screen.getByTestId('mock-textfield');
      expect(input).toHaveValue('John Doe');
    });

    it('should handle missing data binding gracefully', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'TextField',
        props: {},
        slots: {},
        bindings: [
          {
            id: 'binding-1',
            type: 'data',
            source: 'nonexistent.path',
            target: 'value',
          },
        ],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [instance.id],
        nodes: new Map([[instance.id, instance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      render(
        <ComponentRenderer
          instance={instance}
          document={document}
          bindingContext={{}}
          componentRegistry={componentRegistry}
        />
      );

      const input = screen.getByTestId('mock-textfield');
      expect(input).toBeInTheDocument();
      // Value should be undefined/empty since binding couldn't be resolved
      expect(input).toHaveValue('');
    });
  });

  describe('Event Binding Execution', () => {
    it('should resolve event binding and attach handler', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'Button',
        props: {},
        slots: {},
        bindings: [
          {
            id: 'binding-1',
            type: 'event',
            source: 'onClick',
            target: 'onClick',
          },
        ],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [instance.id],
        nodes: new Map([[instance.id, instance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      let handleClickCalled = false;
      const eventContext = {
        onClick: () => {
          handleClickCalled = true;
        },
      };

      render(
        <ComponentRenderer
          instance={instance}
          document={document}
          eventContext={eventContext}
          componentRegistry={componentRegistry}
        />
      );

      const button = screen.getByTestId('mock-button');
      button.click();

      expect(handleClickCalled).toBe(true);
    });

    it('should handle missing event handler gracefully', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'Button',
        props: {},
        slots: {},
        bindings: [
          {
            id: 'binding-1',
            type: 'event',
            source: 'onClick',
            target: 'onClick',
          },
        ],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [instance.id],
        nodes: new Map([[instance.id, instance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      render(
        <ComponentRenderer
          instance={instance}
          document={document}
          eventContext={{}}
          componentRegistry={componentRegistry}
        />
      );

      const button = screen.getByTestId('mock-button');
      // Should not throw when clicking with no handler
      expect(() => button.click()).not.toThrow();
    });
  });

  describe('Slot Rendering', () => {
    it('should render slot children in correct locations', () => {
      const buttonId = createNodeId();
      const textId = createNodeId();
      const cardId = createNodeId();

      const buttonInstance: ComponentInstance = {
        id: buttonId,
        contractName: 'Button',
        props: { label: 'Action' },
        slots: {},
        bindings: [],
        metadata: {},
      };

      const textInstance: ComponentInstance = {
        id: textId,
        contractName: 'TextField',
        props: { value: 'Text' },
        slots: {},
        bindings: [],
        metadata: {},
      };

      const cardInstance: ComponentInstance = {
        id: cardId,
        contractName: 'Card',
        props: { title: 'Test Card' },
        slots: {
          header: [buttonId],
          content: [textId],
        },
        bindings: [],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [cardId],
        nodes: new Map([
          [cardId, cardInstance],
          [buttonId, buttonInstance],
          [textId, textInstance],
        ]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      render(
        <ComponentRenderer
          instance={cardInstance}
          document={document}
          componentRegistry={componentRegistry}
        />
      );

      const card = screen.getByTestId('mock-card');
      const button = screen.getByTestId('mock-button');
      const input = screen.getByTestId('mock-textfield');

      expect(card).toBeInTheDocument();
      expect(button).toBeInTheDocument();
      expect(input).toBeInTheDocument();
      expect(button).toHaveTextContent('Action');
      expect(input).toHaveValue('Text');
    });

    it('should render empty slots gracefully', () => {
      const cardId = createNodeId();

      const cardInstance: ComponentInstance = {
        id: cardId,
        contractName: 'Card',
        props: { title: 'Empty Card' },
        slots: {
          header: [],
          content: [],
        },
        bindings: [],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [cardId],
        nodes: new Map([[cardId, cardInstance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      render(
        <ComponentRenderer
          instance={cardInstance}
          document={document}
          componentRegistry={componentRegistry}
        />
      );

      const card = screen.getByTestId('mock-card');
      expect(card).toBeInTheDocument();
      // Should render without errors even with empty slots
    });

    it('should handle missing slot children gracefully', () => {
      const cardId = createNodeId();
      const missingId = createNodeId();

      const cardInstance: ComponentInstance = {
        id: cardId,
        contractName: 'Card',
        props: { title: 'Card with Missing Child' },
        slots: {
          header: [missingId], // This child doesn't exist in nodes
          content: [],
        },
        bindings: [],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [cardId],
        nodes: new Map([[cardId, cardInstance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      render(
        <ComponentRenderer
          instance={cardInstance}
          document={document}
          componentRegistry={componentRegistry}
        />
      );

      const card = screen.getByTestId('mock-card');
      expect(card).toBeInTheDocument();
      // Should render without errors even with missing children
    });
  });

  describe('Accessibility Attributes', () => {
    it('should add data-builder-id and data-builder-contract attributes', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'Button',
        props: { label: 'Test' },
        slots: {},
        bindings: [],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [instance.id],
        nodes: new Map([[instance.id, instance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      render(
        <ComponentRenderer
          instance={instance}
          document={document}
          componentRegistry={componentRegistry}
        />
      );

      const button = screen.getByTestId('mock-button');
      expect(button).toHaveAttribute('data-builder-id', instance.id);
      expect(button).toHaveAttribute('data-builder-contract', 'Button');
    });

    it('should apply custom className when provided', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'Button',
        props: { label: 'Test' },
        slots: {},
        bindings: [],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [instance.id],
        nodes: new Map([[instance.id, instance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      render(
        <ComponentRenderer
          instance={instance}
          document={document}
          componentRegistry={componentRegistry}
          className="custom-class"
        />
      );

      const button = screen.getByTestId('mock-button');
      expect(button).toHaveClass('custom-class');
    });
  });

  describe('Combined Features', () => {
    it('should render with both data bindings and slots', () => {
      const buttonId = createNodeId();
      const cardId = createNodeId();

      const buttonInstance: ComponentInstance = {
        id: buttonId,
        contractName: 'Button',
        props: {},
        slots: {},
        bindings: [
          {
            id: 'binding-1',
            type: 'data',
            source: 'buttonLabel',
            target: 'label',
          },
        ],
        metadata: {},
      };

      const cardInstance: ComponentInstance = {
        id: cardId,
        contractName: 'Card',
        props: {},
        slots: {
          header: [buttonId],
        },
        bindings: [],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [cardId],
        nodes: new Map([
          [cardId, cardInstance],
          [buttonId, buttonInstance],
        ]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      const bindingContext = {
        buttonLabel: 'Dynamic Label',
      };

      render(
        <ComponentRenderer
          instance={cardInstance}
          document={document}
          bindingContext={bindingContext}
          componentRegistry={componentRegistry}
        />
      );

      const button = screen.getByTestId('mock-button');
      expect(button).toBeInTheDocument();
      expect(button).toHaveTextContent('Dynamic Label');
    });

    it('should render with both event bindings and data bindings', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'Button',
        props: {},
        slots: {},
        bindings: [
          {
            id: 'binding-1',
            type: 'data',
            source: 'buttonLabel',
            target: 'label',
          },
          {
            id: 'binding-2',
            type: 'event',
            source: 'onClick',
            target: 'onClick',
          },
        ],
        metadata: {},
      };

      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [instance.id],
        nodes: new Map([[instance.id, instance]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      let handleClickCalled = false;
      const bindingContext = {
        buttonLabel: 'Click Me',
      };
      const eventContext = {
        onClick: () => {
          handleClickCalled = true;
        },
      };

      render(
        <ComponentRenderer
          instance={instance}
          document={document}
          bindingContext={bindingContext}
          eventContext={eventContext}
          componentRegistry={componentRegistry}
        />
      );

      const button = screen.getByTestId('mock-button');
      expect(button).toHaveTextContent('Click Me');
      
      button.click();
      expect(handleClickCalled).toBe(true);
    });
  });
});
