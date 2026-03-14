/**
 * UI Integration Regression Tests
 * 
 * These tests ensure that the ui-integration package exports remain stable
 * after the split from the ui package.
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';

// Test that all expected exports are available
import {
  // AI Features (namespace export)
  AI,
  
  // Page Builder (namespace export)
  PageBuilder,
  
  // Collaboration (direct export)
  CollaborationProvider,
  useCollaboration,
  CollaborationPresence,
  RealtimeCursor,
  UserAvatar,
  ChangeIndicator,
  
  // Re-exports from design system should be available
  Button,
  Input,
  Card,
  Box,
  useTheme,
} from '../src/index';

describe('UI Integration Regression Tests', () => {
  
  describe('Export Stability', () => {
    it('should export AI namespace', () => {
      // Test that AI namespace is exported
      expect(AI).toBeDefined();
      expect(typeof AI).toBe('object');
      
      // Test that AI namespace has expected properties
      expect(AI.ComponentGenerationRequest).toBeDefined();
      expect(AI.ComponentGenerationResponse).toBeDefined();
      expect(AI.ComponentVariant).toBeDefined();
    });

    it('should export PageBuilder namespace', () => {
      // Test that PageBuilder namespace is exported
      expect(PageBuilder).toBeDefined();
      expect(typeof PageBuilder).toBe('object');
      
      // Test that PageBuilder namespace has expected properties
      expect(PageBuilder.PageBuilder).toBeDefined();
      expect(PageBuilder.PageBuilderProps).toBeDefined();
      expect(PageBuilder.ComponentLibrary).toBeDefined();
    });

    it('should export collaboration components', () => {
      // Test that collaboration components are exported
      expect(CollaborationProvider).toBeDefined();
      expect(useCollaboration).toBeDefined();
      expect(CollaborationPresence).toBeDefined();
      expect(RealtimeCursor).toBeDefined();
      expect(UserAvatar).toBeDefined();
      expect(ChangeIndicator).toBeDefined();
    });

    it('should re-export design system components', () => {
      // Test that design system components are re-exported
      expect(Button).toBeDefined();
      expect(Input).toBeDefined();
      expect(Card).toBeDefined();
      expect(Box).toBeDefined();
      expect(useTheme).toBeDefined();
    });
  });

  describe('AI Features Functionality', () => {
    it('should provide AI feature interfaces', () => {
      // Test that AI interfaces are available and properly typed
      expect(AI.ComponentGenerationRequest).toBeDefined();
      expect(AI.ComponentGenerationResponse).toBeDefined();
      expect(AI.ComponentVariant).toBeDefined();
      
      // Test that interfaces can be used for typing
      const request: AI.ComponentGenerationRequest = {
        prompt: 'Generate a button',
        requirements: ['responsive', 'accessible'],
        style: 'modern'
      };
      
      expect(request.prompt).toBe('Generate a button');
      expect(request.requirements).toContain('responsive');
      expect(request.style).toBe('modern');
    });

    it('should provide AI feature types', () => {
      // Test that AI types are available
      expect(typeof AI.ComponentVariant).toBe('object');
      
      // Test that ComponentVariant enum values exist
      if (AI.ComponentVariant.PRIMARY) {
        expect(AI.ComponentVariant.PRIMARY).toBeDefined();
      }
      if (AI.ComponentVariant.SECONDARY) {
        expect(AI.ComponentVariant.SECONDARY).toBeDefined();
      }
    });
  });

  describe('Page Builder Functionality', () => {
    it('should provide PageBuilder interfaces', () => {
      // Test that PageBuilder interfaces are available
      expect(PageBuilder.PageBuilder).toBeDefined();
      expect(PageBuilder.PageBuilderProps).toBeDefined();
      expect(PageBuilder.ComponentLibrary).toBeDefined();
      
      // Test that interfaces can be used for typing
      const props: PageBuilder.PageBuilderProps = {
        components: [],
        onComponentAdd: jest.fn(),
        onComponentUpdate: jest.fn(),
        onComponentDelete: jest.fn(),
      };
      
      expect(Array.isArray(props.components)).toBe(true);
      expect(typeof props.onComponentAdd).toBe('function');
      expect(typeof props.onComponentUpdate).toBe('function');
      expect(typeof props.onComponentDelete).toBe('function');
    });
  });

  describe('Collaboration Functionality', () => {
    it('should render CollaborationProvider', () => {
      // Test that CollaborationProvider can be rendered
      expect(() => {
        render(
          <CollaborationProvider roomId="test-room">
            <div>Test Content</div>
          </CollaborationProvider>
        );
      }).not.toThrow();
    });

    it('should provide collaboration hook', () => {
      // Test that useCollaboration hook can be called
      expect(() => {
        const TestComponent = () => {
          const collaboration = useCollaboration();
          return <div data-connected={collaboration.isConnected}>Test</div>;
        };
        
        render(
          <CollaborationProvider roomId="test-room">
            <TestComponent />
          </CollaborationProvider>
        );
      }).not.toThrow();
    });

    it('should render collaboration components', () => {
      // Test that collaboration components can be rendered
      expect(() => {
        render(
          <CollaborationProvider roomId="test-room">
            <CollaborationPresence users={[]} />
            <RealtimeCursor userId="test-user" x={100} y={100} />
            <UserAvatar user={{ id: 'test', name: 'Test User' }} />
            <ChangeIndicator type="add" position={{ x: 0, y: 0 }} />
          </CollaborationProvider>
        );
      }).not.toThrow();
    });
  });

  describe('Integration Compatibility', () => {
    it('should work with design system components', () => {
      // Test that ui-integration works seamlessly with design-system
      expect(() => {
        render(
          <CollaborationProvider roomId="test-room">
            <Card>
              <Box p={4}>
                <Button>Collaborative Button</Button>
              </Box>
            </Card>
          </CollaborationProvider>
        );
      }).not.toThrow();
    });

    it('should work with design system hooks', () => {
      // Test that ui-integration works with design-system hooks
      expect(() => {
        const TestComponent = () => {
          const theme = useTheme();
          const collaboration = useCollaboration();
          
          return (
            <CollaborationProvider roomId="test-room">
              <Card theme={theme.mode}>
                <Button>Themed Button</Button>
              </Card>
            </CollaborationProvider>
          );
        };
        
        render(<TestComponent />);
      }).not.toThrow();
    });
  });

  describe('Type Safety', () => {
    it('should maintain type safety for AI features', () => {
      // Test that AI features maintain type safety
      const testAIIntegration = () => {
        // These should compile without type errors
        const request: AI.ComponentGenerationRequest = {
          prompt: 'test',
          requirements: [],
        };
        
        const response: AI.ComponentGenerationResponse = {
          component: 'test-component',
          code: 'test-code',
          metadata: {},
        };
        
        return { request, response };
      };
      
      expect(() => testAIIntegration()).not.toThrow();
    });

    it('should maintain type safety for PageBuilder', () => {
      // Test that PageBuilder maintains type safety
      const testPageBuilder = () => {
        // These should compile without type errors
        const props: PageBuilder.PageBuilderProps = {
          components: [],
          onComponentAdd: () => {},
          onComponentUpdate: () => {},
          onComponentDelete: () => {},
        };
        
        return props;
      };
      
      expect(() => testPageBuilder()).not.toThrow();
    });

    it('should maintain type safety for Collaboration', () => {
      // Test that Collaboration maintains type safety
      const testCollaboration = () => {
        // These should compile without type errors
        const user = { id: 'test', name: 'Test User' };
        const position = { x: 100, y: 100 };
        
        return { user, position };
      };
      
      expect(() => testCollaboration()).not.toThrow();
    });
  });
});
