/**
 * Code Preview Component
 *
 * Displays generated code with syntax highlighting and copy functionality.
 *
 * @module canvas/codegen/CodePreview
 */

import React, { useState, useMemo } from 'react';

import { CodeGenerator, GeneratedCode } from './CodeGenerator';

import type { CodeGenerationOptions } from './CodeGenerator';
import type { ComponentNodeData } from '../types/CanvasNode';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface CodePreviewProps {
  /**
   * Component type
   */
  componentType: string;

  /**
   * Node data
   */
  nodeData: ComponentNodeData;

  /**
   * Generation options
   */
  options?: CodeGenerationOptions;

  /**
   * Show options panel
   */
  showOptions?: boolean;

  /**
   * Default language for syntax highlighting
   */
  language?: 'typescript' | 'javascript';
}

// ============================================================================
// Code Preview Component
// ============================================================================

export const CodePreview: React.FC<CodePreviewProps> = ({
  componentType,
  nodeData,
  options: initialOptions,
  showOptions = true,
  language = 'typescript',
}) => {
  const [options, setOptions] = useState<CodeGenerationOptions>(
    initialOptions || {
      typescript: language === 'typescript',
      includeComments: true,
      includeImports: true,
      style: 'functional',
      indent: 2,
      includeDataBinding: true,
      includeEvents: true,
      includeValidation: true,
    }
  );

  const [copied, setCopied] = useState(false);
  const [activeTab, setActiveTab] = useState<'full' | 'component' | 'imports'>('full');

  // Generate code
  const generatedCode = useMemo(() => {
    return CodeGenerator.generateFile(componentType, nodeData, options);
  }, [componentType, nodeData, options]);

  const generated = useMemo(() => {
    return CodeGenerator.generateComponent(componentType, nodeData, options);
  }, [componentType, nodeData, options]);

  // Handle copy to clipboard
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(generatedCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy code:', err);
    }
  };

  // Handle download
  const handleDownload = () => {
    const blob = new Blob([generatedCode], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `Generated${componentType}.${options.typescript ? 'tsx' : 'jsx'}`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  // Get display code based on active tab
  const getDisplayCode = () => {
    switch (activeTab) {
      case 'full':
        return generatedCode;
      case 'component':
        return generated.component;
      case 'imports':
        return generated.imports.join('\n');
      default:
        return generatedCode;
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: '#fff',
        border: '1px solid #e0e0e0',
        borderRadius: 8,
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: 16,
          borderBottom: '1px solid #e0e0e0',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <div>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Generated Code</h3>
          <p style={{ margin: '4px 0 0', fontSize: 12, color: '#666' }}>
            {componentType} Component
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button
            onClick={handleCopy}
            style={{
              padding: '6px 12px',
              backgroundColor: copied ? '#4caf50' : '#1976d2',
              color: '#fff',
              border: 'none',
              borderRadius: 4,
              cursor: 'pointer',
              fontSize: 13,
              fontWeight: 500,
            }}
          >
            {copied ? '✓ Copied' : '📋 Copy'}
          </button>
          <button
            onClick={handleDownload}
            style={{
              padding: '6px 12px',
              backgroundColor: '#757575',
              color: '#fff',
              border: 'none',
              borderRadius: 4,
              cursor: 'pointer',
              fontSize: 13,
              fontWeight: 500,
            }}
          >
            ⬇ Download
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div
        style={{
          display: 'flex',
          borderBottom: '1px solid #e0e0e0',
          backgroundColor: '#f5f5f5',
        }}
      >
        {(['full', 'component', 'imports'] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            style={{
              padding: '8px 16px',
              backgroundColor: activeTab === tab ? '#fff' : 'transparent',
              border: 'none',
              borderBottom: activeTab === tab ? '2px solid #1976d2' : '2px solid transparent',
              cursor: 'pointer',
              fontSize: 13,
              fontWeight: activeTab === tab ? 600 : 400,
              textTransform: 'capitalize',
            }}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Code Display */}
      <div style={{ flex: 1, overflow: 'auto', position: 'relative' }}>
        <pre
          style={{
            margin: 0,
            padding: 16,
            fontSize: 13,
            fontFamily: 'monospace',
            lineHeight: 1.6,
            backgroundColor: '#1e1e1e',
            color: '#d4d4d4',
            overflow: 'auto',
            height: '100%',
          }}
        >
          <code>{getDisplayCode()}</code>
        </pre>
      </div>

      {/* Options Panel */}
      {showOptions && (
        <div
          style={{
            padding: 16,
            borderTop: '1px solid #e0e0e0',
            backgroundColor: '#f5f5f5',
          }}
        >
          <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 12 }}>
            Generation Options
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <label style={{ display: 'flex', alignItems: 'center', fontSize: 12 }}>
              <input
                type="checkbox"
                checked={options.typescript}
                onChange={(e) => setOptions({ ...options, typescript: e.target.checked })}
                style={{ marginRight: 6 }}
              />
              TypeScript
            </label>
            <label style={{ display: 'flex', alignItems: 'center', fontSize: 12 }}>
              <input
                type="checkbox"
                checked={options.includeComments}
                onChange={(e) => setOptions({ ...options, includeComments: e.target.checked })}
                style={{ marginRight: 6 }}
              />
              Comments
            </label>
            <label style={{ display: 'flex', alignItems: 'center', fontSize: 12 }}>
              <input
                type="checkbox"
                checked={options.includeImports}
                onChange={(e) => setOptions({ ...options, includeImports: e.target.checked })}
                style={{ marginRight: 6 }}
              />
              Imports
            </label>
            <label style={{ display: 'flex', alignItems: 'center', fontSize: 12 }}>
              <input
                type="checkbox"
                checked={options.includeDataBinding}
                onChange={(e) => setOptions({ ...options, includeDataBinding: e.target.checked })}
                style={{ marginRight: 6 }}
              />
              Data Binding
            </label>
            <label style={{ display: 'flex', alignItems: 'center', fontSize: 12 }}>
              <input
                type="checkbox"
                checked={options.includeEvents}
                onChange={(e) => setOptions({ ...options, includeEvents: e.target.checked })}
                style={{ marginRight: 6 }}
              />
              Events
            </label>
            <label style={{ display: 'flex', alignItems: 'center', fontSize: 12 }}>
              <input
                type="checkbox"
                checked={options.includeValidation}
                onChange={(e) => setOptions({ ...options, includeValidation: e.target.checked })}
                style={{ marginRight: 6 }}
              />
              Validation
            </label>
          </div>
        </div>
      )}

      {/* Stats */}
      <div
        style={{
          padding: '8px 16px',
          borderTop: '1px solid #e0e0e0',
          backgroundColor: '#f9f9f9',
          fontSize: 11,
          color: '#666',
          display: 'flex',
          justifyContent: 'space-between',
        }}
      >
        <span>{generatedCode.split('\n').length} lines</span>
        <span>{generatedCode.length} characters</span>
        <span>{generated.imports.length} imports</span>
      </div>
    </div>
  );
};
