// @ts-nocheck
/**
 * @fileoverview Monaco Editor Code Splitting
 * Lazy-loaded code editor for performance optimization
 * 
 * @doc.type component
 * @doc.purpose Reduce initial bundle size by lazy-loading Monaco editor
 * @doc.layer presentation
 * @doc.pattern CodeSplitting
 * 
 * @example
 * ```tsx
 * const MonacoEditor = React.lazy(() => import('./LazyMonacoEditor'));
 * 
 * <Suspense fallback={<EditorSkeleton />}>
 *   <MonacoEditor
 *     value={code}
 *     language="typescript"
 *     onChange={setCode}
 *   />
 * </Suspense>
 * ```
 */

import React, { Suspense, lazy, useState, useEffect } from 'react';
import type * as Monaco from 'monaco-editor';

// ============================================================================
// Types
// ============================================================================

export interface LazyMonacoEditorProps {
  value: string;
  language?: string;
  theme?: 'vs-light' | 'vs-dark';
  height?: string | number;
  onChange?: (value: string) => void;
  onMount?: (editor: Monaco.editor.IStandaloneCodeEditor) => void;
  options?: Monaco.editor.IStandaloneEditorConstructionOptions;
}

// ============================================================================
// Loading Skeleton
// ============================================================================

const EditorSkeleton: React.FC<{ height?: string | number }> = ({ height = '400px' }) => (
  <div
    style={{
      height: typeof height === 'number' ? `${height}px` : height,
      background: '#1e1e1e',
      borderRadius: '4px',
      display: 'flex',
      flexDirection: 'column',
      padding: '16px',
      gap: '8px',
    }}
  >
    {/* Line numbers column */}
    <div style={{ display: 'flex', gap: '16px' }}>
      <div style={{ width: '40px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {Array.from({ length: 12 }).map((_, i) => (
          <div
            key={i}
            style={{
              height: '16px',
              background: '#2d2d2d',
              borderRadius: '2px',
              opacity: 0.5 - i * 0.03,
            }}
          />
        ))}
      </div>
      
      {/* Code lines */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {Array.from({ length: 12 }).map((_, i) => (
          <div
            key={i}
            style={{
              height: '16px',
              background: '#2d2d2d',
              borderRadius: '2px',
              width: `${Math.random() * 60 + 20}%`,
              animation: 'pulse 1.5s ease-in-out infinite',
              animationDelay: `${i * 0.1}s`,
            }}
          />
        ))}
      </div>
    </div>
    
    <style>{`
      @keyframes pulse {
        0%, 100% { opacity: 0.4; }
        50% { opacity: 0.7; }
      }
    `}</style>
  </div>
);

// ============================================================================
// Lazy Loaded Monaco Editor
// ============================================================================

/**
 * Dynamically import Monaco Editor
 * This splits Monaco into a separate chunk
 */
const MonacoEditorLazy = lazy(() => import('@monaco-editor/react'));

/**
 * Preload Monaco Editor
 * Call this when user hovers over code editor area or opens code-related panels
 * @doc.purpose Preload editor before user interaction
 */
export function preloadMonacoEditor(): void {
  // Start loading the chunk in background
  const preload = import('@monaco-editor/react');
  
  // Also preload common languages
  Promise.all([
    preload,
    // Preload will trigger webpack to load these chunks
    new Promise(resolve => setTimeout(resolve, 100)),
  ]);
}

// ============================================================================
// Main Component
// ============================================================================

/**
 * Lazy-loaded Monaco Editor Wrapper
 * @doc.purpose Code-split Monaco editor for performance
 */
export const LazyMonacoEditor: React.FC<LazyMonacoEditorProps> = ({
  value,
  language = 'typescript',
  theme = 'vs-dark',
  height = '400px',
  onChange,
  onMount,
  options = {},
}) => {
  const [isLoaded, setIsLoaded] = useState(false);

  useEffect(() => {
    // Track loading for analytics
    const startTime = performance.now();
    
    return () => {
      if (isLoaded) {
        const loadTime = performance.now() - startTime;
        console.debug(`[Monaco] Editor loaded in ${loadTime.toFixed(0)}ms`);
      }
    };
  }, [isLoaded]);

  const handleMount = (editor: Monaco.editor.IStandaloneCodeEditor, monaco: typeof Monaco) => {
    setIsLoaded(true);
    
    // Configure Monaco for optimal performance
    monaco.editor.defineTheme('yappc-dark', {
      base: 'vs-dark',
      inherit: true,
      rules: [],
      colors: {
        'editor.background': '#1a1a2e',
        'editor.foreground': '#e4e4e7',
      },
    });
    
    monaco.editor.setTheme('yappc-dark');
    
    onMount?.(editor);
  };

  const defaultOptions = {
    minimap: { enabled: false },
    fontSize: 14,
    fontFamily: 'JetBrains Mono, Fira Code, monospace',
    lineNumbers: 'on',
    roundedSelection: false,
    scrollBeyondLastLine: false,
    readOnly: false,
    automaticLayout: true,
    wordWrap: 'on',
    folding: true,
    renderWhitespace: 'selection',
    ...options,
  };

  return (
    <Suspense fallback={<EditorSkeleton height={height} />}>
      <div style={{ borderRadius: '4px', overflow: 'hidden' }}>
        <MonacoEditorLazy
          height={height}
          language={language}
          theme={theme}
          value={value}
          onChange={onChange}
          onMount={handleMount}
          options={defaultOptions}
          loading={<EditorSkeleton height={height} />}
        />
      </div>
    </Suspense>
  );
};

// ============================================================================
// Hook for Dynamic Loading
// ============================================================================

/**
 * Hook to manage Monaco Editor loading state
 * @doc.purpose Control when Monaco editor loads
 */
export function useMonacoLoader() {
  const [isReady, setIsReady] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const loadMonaco = async () => {
    if (isReady || isLoading) return;
    
    setIsLoading(true);
    
    try {
      await import('@monaco-editor/react');
      setIsReady(true);
    } catch (error) {
      console.error('[Monaco] Failed to load editor:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const preloadMonaco = () => {
    // Start preloading in background
    preloadMonacoEditor();
  };

  return {
    isReady,
    isLoading,
    loadMonaco,
    preloadMonaco,
  };
}

// ============================================================================
// Bundle Analysis Helper
// ============================================================================

/**
 * Monaco Editor Bundle Configuration
 * @doc.purpose Document expected bundle sizes
 */
export const MonacoBundleInfo = {
  // Main editor chunk (~800KB gzipped)
  mainChunk: {
    name: 'monaco-editor',
    size: '~800KB',
    gzip: '~250KB',
  },
  
  // Language workers (loaded on demand)
  languageWorkers: {
    typescript: '~150KB',
    javascript: '~100KB',
    css: '~50KB',
    html: '~50KB',
    json: '~30KB',
    python: '~100KB',
    java: '~120KB',
  },
  
  // Total with all languages: ~1.5MB
  // Typical usage: 1-2 languages = ~1MB
  
  optimization: {
    // Only load languages when needed
    onlyLoadVisible: true,
    
    // Disable features not used
    disableMinimap: true,
    disableSemanticValidation: false,
    disableSyntaxValidation: false,
    
    // Use lighter font
    customFont: 'JetBrains Mono',
  },
};

// ============================================================================
// Export
// ============================================================================

export default LazyMonacoEditor;
