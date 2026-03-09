/**
 * IDE Code Features - CodeGeneration, CodeCompletion Bridge
 * 
 * @deprecated Use CodeGenPanel, AutoComplete from @ghatana/yappc-canvas
 * @see /docs/LIBRARY_CONSOLIDATION_PLAN.md
 */

import React, { useEffect, useState, useCallback } from 'react';

// ============================================================================
// CodeGeneration
// ============================================================================

export interface CodeGenerationProps {
  /** Initial prompt or context */
  prompt?: string;
  /** Programming language */
  language?: string;
  /** Generation handler */
  onGenerate?: (prompt: string, language: string) => Promise<string>;
  /** Generated code */
  generatedCode?: string;
  /** Accept handler */
  onAccept?: (code: string) => void;
  /** Reject handler */
  onReject?: () => void;
  /** Additional CSS classes */
  className?: string;
  /** Generation in progress */
  isGenerating?: boolean;
}

/**
 * CodeGeneration - Bridge to Canvas AI Code Generation
 */
export const CodeGeneration: React.FC<CodeGenerationProps> = ({
  prompt = '',
  language = 'typescript',
  onGenerate,
  generatedCode,
  onAccept,
  onReject,
  className,
  isGenerating = false,
}) => {
  const [currentPrompt, setCurrentPrompt] = useState(prompt);
  const [generated, setGenerated] = useState(generatedCode || '');
  const [generating, setGenerating] = useState(isGenerating);

  useEffect(() => {
    console.warn(
      '[MIGRATION] CodeGeneration from @ghatana/yappc-ide is deprecated. ' +
      'Use CodeGenPanel or ComponentGeneratorDialog from @ghatana/yappc-canvas.'
    );
  }, []);

  const handleGenerate = useCallback(async () => {
    if (!currentPrompt.trim()) return;
    
    setGenerating(true);
    try {
      const code = await onGenerate?.(currentPrompt, language);
      if (code) {
        setGenerated(code);
      }
    } finally {
      setGenerating(false);
    }
  }, [currentPrompt, language, onGenerate]);

  return (
    <div className={`code-generation ${className || ''}`}>
      <div className="generation-input">
        <textarea
          className="prompt-input"
          value={currentPrompt}
          onChange={(e) => setCurrentPrompt(e.target.value)}
          placeholder="Describe the code you want to generate..."
          rows={4}
        />
        <select 
          className="language-select"
          value={language}
          onChange={(e) => {}}
        >
          <option value="typescript">TypeScript</option>
          <option value="javascript">JavaScript</option>
          <option value="python">Python</option>
          <option value="java">Java</option>
          <option value="go">Go</option>
          <option value="rust">Rust</option>
        </select>
        <button 
          className="generate-button"
          onClick={handleGenerate}
          disabled={generating || !currentPrompt.trim()}
        >
          {generating ? 'Generating...' : 'Generate Code'}
        </button>
      </div>

      {generated && (
        <div className="generated-code">
          <div className="code-header">
            <span className="code-label">Generated Code</span>
            <div className="code-actions">
              <button className="accept-button" onClick={() => onAccept?.(generated)}>
                ✓ Accept
              </button>
              <button className="reject-button" onClick={onReject}>
                ✗ Reject
              </button>
            </div>
          </div>
          <pre className="code-block">
            <code>{generated}</code>
          </pre>
        </div>
      )}
    </div>
  );
};

// ============================================================================
// CodeCompletion
// ============================================================================

export interface CodeCompletionProps {
  /** Current code content */
  code: string;
  /** Cursor position */
  cursorPosition: { line: number; column: number };
  /** Completion handler */
  onRequestCompletion?: (code: string, position: { line: number; column: number }) => Promise<CompletionItem[]>;
  /** Completion select handler */
  onSelectCompletion?: (item: CompletionItem) => void;
  /** Active completion items */
  completions?: CompletionItem[];
  /** Additional CSS classes */
  className?: string;
}

export interface CompletionItem {
  id: string;
  label: string;
  kind: 'keyword' | 'function' | 'variable' | 'class' | 'module' | 'property';
  detail?: string;
  documentation?: string;
  insertText?: string;
  sortText?: string;
}

/**
 * CodeCompletion - Bridge to Canvas AutoComplete System
 */
export const CodeCompletion: React.FC<CodeCompletionProps> = ({
  code,
  cursorPosition,
  onRequestCompletion,
  onSelectCompletion,
  completions: externalCompletions,
  className,
}) => {
  const [completions, setCompletions] = useState<CompletionItem[]>(externalCompletions || []);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [showCompletions, setShowCompletions] = useState(false);

  useEffect(() => {
    console.warn(
      '[MIGRATION] CodeCompletion from @ghatana/yappc-ide is deprecated. ' +
      'Use AutoComplete or code completion hooks from @ghatana/yappc-canvas.'
    );
  }, []);

  const handleRequestCompletion = useCallback(async () => {
    if (!onRequestCompletion) return;
    
    const items = await onRequestCompletion(code, cursorPosition);
    setCompletions(items);
    setShowCompletions(items.length > 0);
    setSelectedIndex(0);
  }, [code, cursorPosition, onRequestCompletion]);

  const handleSelect = useCallback((item: CompletionItem) => {
    onSelectCompletion?.(item);
    setShowCompletions(false);
  }, [onSelectCompletion]);

  return (
    <div className={`code-completion ${className || ''}`}>
      <button 
        className="trigger-completion"
        onClick={handleRequestCompletion}
        title="Trigger completion (Ctrl+Space)"
      >
        ⌄ Complete
      </button>

      {showCompletions && completions.length > 0 && (
        <div className="completion-list">
          {completions.map((item, index) => (
            <div
              key={item.id}
              className={`completion-item ${index === selectedIndex ? 'selected' : ''} ${item.kind}`}
              onClick={() => handleSelect(item)}
              onMouseEnter={() => setSelectedIndex(index)}
            >
              <span className="completion-icon">
                {item.kind === 'function' && 'ƒ'}
                {item.kind === 'class' && 'C'}
                {item.kind === 'variable' && 'V'}
                {item.kind === 'keyword' && 'K'}
                {item.kind === 'module' && 'M'}
                {item.kind === 'property' && 'P'}
              </span>
              <span className="completion-label">{item.label}</span>
              {item.detail && <span className="completion-detail">{item.detail}</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

// Re-export with Canvas prefix
export { CodeGeneration as CodeGenPanel };
export { CodeCompletion as AutoComplete };
