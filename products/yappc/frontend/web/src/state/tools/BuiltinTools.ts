/**
 * Built-in Canvas Tools
 * 
 * Standard tools that come with the Canvas implementation.
 */

import React from 'react';

import type { CanvasTool, CanvasContext } from './ToolAPI';

// Selection Tool
export const SelectionTool: CanvasTool = {
  id: 'selection',
  name: 'Selection',
  description: 'Select and manipulate elements',
  icon: '🧭',
  category: 'selection',
  
  onActivate(_context: CanvasContext) {
    // Default tool - always active
  },
  
  shortcuts: [
    {
      key: 'v',
      description: 'Activate selection tool',
      action: (_context) => {
        // Tool activation handled by registry
      }
    },
    {
      key: 'Delete',
      description: 'Delete selected elements',
      action: (context) => {
        const selection = context.getSelection();
        selection.forEach(id => context.deleteElement(id));
        context.clearSelection();
      }
    },
    {
      key: 'a',
      ctrlKey: true,
      description: 'Select all elements',
      action: (context) => {
        const state = context.getCanvasState();
        const elementIds = state.elements.map(el => el.id);
        context.setSelection(elementIds);
      }
    }
  ]
};

// Accessibility Tool
export const AccessibilityTool: CanvasTool = {
  id: 'accessibility',
  name: 'Accessibility Audit',
  description: 'Check accessibility compliance',
  icon: '♿',
  category: 'analysis',
  
  renderPanel(context: CanvasContext) {
    const state = context.getCanvasState();
    const selection = context.getSelection();
    const elementsToCheck = selection.length > 0 
      ? state.elements.filter(el => selection.includes(el.id))
      : state.elements;
    
    const issues = elementsToCheck.flatMap(element => {
      const issues = [];
      
      // Check for missing alt text on images
      if (element.data.properties?.componentType === 'image' && !element.data.properties?.alt) {
        issues.push(`${element.data.name || element.id}: Missing alt text`);
      }
      
      // Check for proper heading hierarchy
      if (element.data.properties?.componentType === 'heading' && (element.data.properties?.level as number) > 1) {
        const prevHeadings = state.elements.filter(el => 
          el.data.properties?.componentType === 'heading' && 
          el.position.y < element.position.y
        );
        if (prevHeadings.length === 0) {
          issues.push(`${element.data.name || element.id}: Should start with H1`);
        }
      }
      
      // Check color contrast (simplified)
      if (element.style?.backgroundColor && element.style?.textColor) {
        // This is a simplified check - real implementation would use WCAG algorithms
        const bgColor = element.style.backgroundColor;
        const textColor = element.style.textColor;
        if (bgColor === textColor) {
          issues.push(`${element.data.name || element.id}: Poor color contrast`);
        }
      }
      
      return issues;
    });
    
    return React.createElement('div', {
      style: {
        padding: '1rem',
        backgroundColor: 'white',
        border: '1px solid #e0e0e0',
        borderRadius: '6px',
        maxHeight: '300px',
        overflow: 'auto'
      }
    }, [
      React.createElement('h3', { key: 'title', style: { margin: '0 0 1rem 0' } }, 'Accessibility Issues'),
      issues.length === 0 
        ? React.createElement('p', { key: 'no-issues', style: { color: '#4caf50', margin: 0 } }, '✅ No issues found')
        : React.createElement('ul', { key: 'issues', style: { margin: 0, paddingLeft: '1.5rem' } },
            issues.map((issue, index) => 
              React.createElement('li', { key: index, style: { color: '#f44336', marginBottom: '0.5rem' } }, issue)
            )
          )
    ]);
  },
  
  shortcuts: [
    {
      key: 'a',
      shiftKey: true,
      description: 'Run accessibility audit',
      action: (_context) => {
        // Tool activation handled by registry
      }
    }
  ]
};

// Code Export Tool
export const CodeExportTool: CanvasTool = {
  id: 'code-export',
  name: 'Code Export',
  description: 'Export canvas as code',
  icon: '💻',
  category: 'export',
  
  renderPanel(context: CanvasContext) {
    const generateCode = () => {
      const state = context.getCanvasState();
      
      // Generate React JSX code
      const components = state.elements.map(element => {
        const props = Object.entries(element.data)
          .filter(([key, value]) => key !== 'name' && value !== undefined)
          .map(([key, value]) => `${key}="${value}"`)
          .join(' ');
        
        const style = element.style ? 
          `style={{${Object.entries(element.style).map(([k, v]) => `${k}: '${v}'`).join(', ')}}}` : '';
        
        return `  <${element.type}${props ? ` ${  props}` : ''}${style ? ` ${  style}` : ''} />`;
      }).join('\n');
      
      return `import React from 'react';\n\nexport default function GeneratedComponent() {\n  return (\n    <div>\n${components}\n    </div>\n  );\n}`;
    };
    
    const [code, setCode] = React.useState('');
    
    React.useEffect(() => {
      setCode(generateCode());
    }, [context.getCanvasState()]);
    
    const copyToClipboard = () => {
      navigator.clipboard.writeText(code);
    };
    
    return React.createElement('div', {
      style: {
        padding: '1rem',
        backgroundColor: 'white',
        border: '1px solid #e0e0e0',
        borderRadius: '6px',
        width: '400px'
      }
    }, [
      React.createElement('div', { 
        key: 'header',
        style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }
      }, [
        React.createElement('h3', { key: 'title', style: { margin: 0 } }, 'Generated Code'),
        React.createElement('button', {
          key: 'copy',
          onClick: copyToClipboard,
          style: {
            padding: '0.25rem 0.5rem',
            fontSize: '0.75rem',
            border: '1px solid #e0e0e0',
            borderRadius: '4px',
            cursor: 'pointer'
          }
        }, 'Copy')
      ]),
      React.createElement('pre', {
        key: 'code',
        style: {
          backgroundColor: '#f5f5f5',
          padding: '1rem',
          borderRadius: '4px',
          fontSize: '0.75rem',
          overflow: 'auto',
          maxHeight: '300px',
          margin: 0
        }
      }, code)
    ]);
  },
  
  shortcuts: [
    {
      key: 'e',
      ctrlKey: true,
      description: 'Export as code',
      action: (_context) => {
        // Tool activation handled by registry
      }
    }
  ]
};

// Layout Tool with Auto-arrangement
export const LayoutTool: CanvasTool = {
  id: 'layout',
  name: 'Auto Layout',
  description: 'Automatically arrange elements',
  icon: '📐',
  category: 'custom',
  
  renderToolbar(context: CanvasContext) {
    const applyGridLayout = () => {
      const state = context.getCanvasState();
      const selection = context.getSelection();
      const elements = selection.length > 0 
        ? state.elements.filter(el => selection.includes(el.id))
        : state.elements;
      
      const cols = Math.ceil(Math.sqrt(elements.length));
      const cellSize = 150;
      
      elements.forEach((element, index) => {
        const row = Math.floor(index / cols);
        const col = index % cols;
        context.updateElement(element.id, {
          position: { x: col * cellSize + 50, y: row * cellSize + 50 }
        });
      });
    };
    
    const applyCircularLayout = () => {
      const state = context.getCanvasState();
      const selection = context.getSelection();
      const elements = selection.length > 0 
        ? state.elements.filter(el => selection.includes(el.id))
        : state.elements;
      
      const centerX = 300;
      const centerY = 300;
      const radius = 200;
      
      elements.forEach((element, index) => {
        const angle = (index / elements.length) * 2 * Math.PI;
        context.updateElement(element.id, {
          position: {
            x: centerX + Math.cos(angle) * radius,
            y: centerY + Math.sin(angle) * radius
          }
        });
      });
    };
    
    return React.createElement('div', {
      style: { display: 'flex', gap: '0.5rem' }
    }, [
      React.createElement('button', {
        key: 'grid',
        onClick: applyGridLayout,
        style: {
          padding: '0.25rem 0.5rem',
          fontSize: '0.75rem',
          border: '1px solid #e0e0e0',
          borderRadius: '4px',
          cursor: 'pointer'
        }
      }, '⊞ Grid'),
      React.createElement('button', {
        key: 'circular',
        onClick: applyCircularLayout,
        style: {
          padding: '0.25rem 0.5rem',
          fontSize: '0.75rem',
          border: '1px solid #e0e0e0',
          borderRadius: '4px',
          cursor: 'pointer'
        }
      }, '○ Circle')
    ]);
  },
  
  shortcuts: [
    {
      key: 'g',
      shiftKey: true,
      description: 'Apply grid layout',
      action: (_context) => {
        // Implementation in toolbar
      }
    }
  ]
};