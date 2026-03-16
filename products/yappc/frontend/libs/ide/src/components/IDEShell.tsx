/**
 * @ghatana/yappc-ide - IDE Shell Component
 * 
 * Main IDE container component with layout management.
 * 
 * @doc.type component
 * @doc.purpose Main IDE shell container
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useRef, useEffect } from 'react';
import { useAtom } from 'jotai';
import {
  ideExplorerVisibleAtom,
  ideTerminalVisibleAtom,
  ideProblemsVisibleAtom,
  ideSearchVisibleAtom,
  ideSourceControlVisibleAtom,
  ideRunVisibleAtom,
  ideExtensionsVisibleAtom,
} from '../state/atoms';
import { FileExplorer } from './FileExplorer';
import { TabBar } from './TabBar';
import { EditorPanel } from './EditorPanel';
import { StatusBar } from './StatusBar';

/**
 * IDE Shell Props
 */
export interface IDEShellProps {
  className?: string;
  initialWidth?: number;
  initialHeight?: number;
  showStatusBar?: boolean;
  onResize?: (width: number, height: number) => void;
}

/**
 * Resizable Panel Component
 */
interface ResizablePanelProps {
  children: React.ReactNode;
  defaultWidth: number;
  minWidth: number;
  maxWidth: number;
  position: 'left' | 'right';
  onResize?: (width: number) => void;
}

const ResizablePanel: React.FC<ResizablePanelProps> = ({
  children,
  defaultWidth,
  minWidth,
  maxWidth,
  position,
  onResize,
}) => {
  const [width, setWidth] = useState(defaultWidth);
  const [isResizing, setIsResizing] = useState(false);

  const handleMouseDown = (e: React.MouseEvent) => {
    e.preventDefault();
    setIsResizing(true);
  };

  React.useEffect(() => {
    if (!isResizing) return;

    const handleMouseMove = (e: MouseEvent) => {
      const newWidth = position === 'left'
        ? e.clientX
        : window.innerWidth - e.clientX;

      const clampedWidth = Math.max(minWidth, Math.min(maxWidth, newWidth));
      setWidth(clampedWidth);
      onResize?.(clampedWidth);
    };

    const handleMouseUp = () => {
      setIsResizing(false);
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isResizing, position, minWidth, maxWidth, onResize]);

  return (
    <div className="relative flex" style={{ width }}>
      <div className="flex-1 overflow-hidden">{children}</div>
      <div
        className={`
          absolute top-0 ${position === 'left' ? 'right-0' : 'left-0'} bottom-0 w-1
          cursor-col-resize hover:bg-blue-500 transition-colors
          ${isResizing ? 'bg-blue-500' : 'bg-transparent'}
        `}
        onMouseDown={handleMouseDown}
      />
    </div>
  );
};

/**
 * IDE Shell Component
 * 
 * @doc.param props - Component props
 * @doc.returns IDE shell component
 */
export const IDEShell: React.FC<IDEShellProps> = ({
  className = '',
  initialWidth = 1200,
  initialHeight = 800,
  showStatusBar = true,
}) => {
  const [explorerVisible, setExplorerVisible] = useAtom(ideExplorerVisibleAtom);
  const [terminalVisible, setTerminalVisible] = useAtom(ideTerminalVisibleAtom);
  const [problemsVisible, setProblemsVisible] = useAtom(ideProblemsVisibleAtom);
  const [searchVisible, setSearchVisible] = useAtom(ideSearchVisibleAtom);
  const [sourceControlVisible, setSourceControlVisible] = useAtom(ideSourceControlVisibleAtom);
  const [runVisible, setRunVisible] = useAtom(ideRunVisibleAtom);
  const [extensionsVisible, setExtensionsVisible] = useAtom(ideExtensionsVisibleAtom);
  const [explorerWidth, setExplorerWidth] = useState(250);

  // Accessibility refs for panel focus management
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const sourceControlCloseRef = useRef<HTMLButtonElement | null>(null);
  const runCloseRef = useRef<HTMLButtonElement | null>(null);
  const extensionsCloseRef = useRef<HTMLButtonElement | null>(null);

  // Focus the primary control when a panel opens
  useEffect(() => {
    if (searchVisible) {
      searchInputRef.current?.focus();
    }
  }, [searchVisible]);

  useEffect(() => {
    if (sourceControlVisible) {
      sourceControlCloseRef.current?.focus();
    }
  }, [sourceControlVisible]);

  useEffect(() => {
    if (runVisible) {
      runCloseRef.current?.focus();
    }
  }, [runVisible]);

  useEffect(() => {
    if (extensionsVisible) {
      extensionsCloseRef.current?.focus();
    }
  }, [extensionsVisible]);

  return (
    <div
      className={`flex flex-col bg-gray-100 dark:bg-gray-900 ${className}`}
      style={{ width: initialWidth, height: initialHeight }}
    >
      {/* Top Menu Bar */}
      <div className="flex items-center justify-between px-4 py-2 bg-gray-800 text-white">
        <div className="flex items-center gap-4">
          <h1 className="text-sm font-semibold">YAPPC IDE</h1>
          <nav className="flex items-center gap-2 text-xs">
            <button className="px-2 py-1 hover:bg-gray-700 rounded">File</button>
            <button className="px-2 py-1 hover:bg-gray-700 rounded">Edit</button>
            <button className="px-2 py-1 hover:bg-gray-700 rounded">View</button>
            <button className="px-2 py-1 hover:bg-gray-700 rounded">Go</button>
            <button className="px-2 py-1 hover:bg-gray-700 rounded">Run</button>
            <button className="px-2 py-1 hover:bg-gray-700 rounded">Terminal</button>
            <button className="px-2 py-1 hover:bg-gray-700 rounded">Help</button>
          </nav>
        </div>
        <div className="flex items-center gap-2 text-xs">
          <span className="text-gray-400">v0.1.0</span>
        </div>
      </div>

      {/* Activity Bar */}
      <div className="flex flex-1 overflow-hidden">
        <div className="flex flex-col w-12 bg-gray-800 border-r border-gray-700">
          <button
            className={`p-3 hover:bg-gray-700 ${explorerVisible ? 'bg-gray-700' : ''}`}
            onClick={() => setExplorerVisible(!explorerVisible)}
            title="Explorer"
            aria-label="Toggle explorer"
          >
            📁
          </button>
          <button
            className={`p-3 hover:bg-gray-700 ${searchVisible ? 'bg-gray-700' : ''}`}
            title="Search"
            aria-label="Search"
            onClick={() => setSearchVisible(!searchVisible)}
            aria-pressed={searchVisible}
          >
            🔍
          </button>
          <button
            className={`p-3 hover:bg-gray-700 ${sourceControlVisible ? 'bg-gray-700' : ''}`}
            title="Source Control"
            aria-label="Source control"
            onClick={() => setSourceControlVisible(!sourceControlVisible)}
            aria-pressed={sourceControlVisible}
          >
            🔀
          </button>
          <button
            className={`p-3 hover:bg-gray-700 ${runVisible ? 'bg-gray-700' : ''}`}
            title="Run and Debug"
            aria-label="Run and debug"
            onClick={() => setRunVisible(!runVisible)}
            aria-pressed={runVisible}
          >
            ▶️
          </button>
          <button
            className={`p-3 hover:bg-gray-700 ${extensionsVisible ? 'bg-gray-700' : ''}`}
            title="Extensions"
            aria-label="Extensions"
            onClick={() => setExtensionsVisible(!extensionsVisible)}
            aria-pressed={extensionsVisible}
          >
            🧩
          </button>
        </div>

        {/* Main Content Area */}
        <div className="relative flex flex-1 overflow-hidden">
          {/* Auxiliary Panels (placeholders) */}
          {searchVisible && (
            <div className="absolute left-16 top-4 z-40 bg-white dark:bg-gray-800 border rounded p-3 shadow-md w-80" role="dialog" aria-label="Search panel">
              <div className="flex items-center justify-between">
                <strong>Search</strong>
                <button className="text-sm" onClick={() => setSearchVisible(false)} aria-label="Close search">✕</button>
              </div>
              <div className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                <input
                  ref={searchInputRef}
                  aria-label="Search files"
                  placeholder="Search files..."
                  className="w-full px-2 py-1 border rounded bg-gray-50 dark:bg-gray-900"
                />
              </div>
            </div>
          )}

          {sourceControlVisible && (
            <div className="absolute left-16 top-24 z-40 bg-white dark:bg-gray-800 border rounded p-3 shadow-md w-80" role="dialog" aria-label="Source control panel">
              <div className="flex items-center justify-between">
                <strong>Source Control</strong>
                <button ref={sourceControlCloseRef} className="text-sm" onClick={() => setSourceControlVisible(false)} aria-label="Close source control">✕</button>
              </div>
              <div className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                <p className="mb-2 font-medium">Changes</p>
                <ul className="space-y-1">
                  <li className="flex items-center gap-2 px-2 py-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer">
                    <span className="text-yellow-500" title="Modified">M</span>
                    <span className="truncate">src/index.ts</span>
                  </li>
                  <li className="flex items-center gap-2 px-2 py-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer">
                    <span className="text-green-500" title="Added">A</span>
                    <span className="truncate">src/components/App.tsx</span>
                  </li>
                </ul>
                <button
                  className="mt-3 w-full px-3 py-1.5 text-xs bg-blue-600 text-white rounded hover:bg-blue-700"
                  onClick={() => console.log('commit')}
                >
                  Commit
                </button>
              </div>
            </div>
          )}

          {runVisible && (
            <div className="absolute right-16 top-4 z-40 bg-white dark:bg-gray-800 border rounded p-3 shadow-md w-72" role="dialog" aria-label="Run and Debug panel">
              <div className="flex items-center justify-between">
                <strong>Run & Debug</strong>
                <button ref={runCloseRef} className="text-sm" onClick={() => setRunVisible(false)} aria-label="Close run debug">✕</button>
              </div>
              <div className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                <div className="flex gap-2 mb-3">
                  <button
                    className="flex-1 px-2 py-1 text-xs bg-green-600 text-white rounded hover:bg-green-700"
                    onClick={() => console.log('run')}
                  >
                    ▶ Run
                  </button>
                  <button
                    className="flex-1 px-2 py-1 text-xs bg-yellow-600 text-white rounded hover:bg-yellow-700"
                    onClick={() => console.log('debug')}
                  >
                    🐞 Debug
                  </button>
                </div>
                <p className="text-xs font-medium mb-1">Breakpoints</p>
                <p className="text-xs text-gray-400">No breakpoints set</p>
                <p className="mt-2 text-xs font-medium">Call Stack</p>
                <p className="text-xs text-gray-400">No active session</p>
              </div>
            </div>
          )}

          {extensionsVisible && (
            <div className="absolute right-16 top-24 z-40 bg-white dark:bg-gray-800 border rounded p-3 shadow-md w-72" role="dialog" aria-label="Extensions panel">
              <div className="flex items-center justify-between">
                <strong>Extensions</strong>
                <button ref={extensionsCloseRef} className="text-sm" onClick={() => setExtensionsVisible(false)} aria-label="Close extensions">✕</button>
              </div>
              <div className="mt-2">
                <input
                  placeholder="Search extensions…"
                  className="w-full px-2 py-1 text-xs border rounded bg-gray-50 dark:bg-gray-900 dark:border-gray-600 mb-2"
                />
                <ul className="space-y-2 text-sm">
                  {[
                    { name: 'Prettier', description: 'Code formatter', installed: true },
                    { name: 'ESLint', description: 'Linting for JS/TS', installed: true },
                    { name: 'GitLens', description: 'Git supercharged', installed: false },
                  ].map((ext) => (
                    <li key={ext.name} className="flex items-start gap-2">
                      <div className="flex-1">
                        <p className="font-medium text-gray-800 dark:text-gray-200">{ext.name}</p>
                        <p className="text-xs text-gray-500">{ext.description}</p>
                      </div>
                      <button
                        className={`shrink-0 px-2 py-0.5 text-xs rounded ${
                          ext.installed
                            ? 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300'
                            : 'bg-blue-600 text-white hover:bg-blue-700'
                        }`}
                        onClick={() => console.log(ext.installed ? 'uninstall' : 'install', ext.name)}
                      >
                        {ext.installed ? 'Installed' : 'Install'}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          )}

          {/* Sidebar (Explorer) */}
          {explorerVisible && (
            <ResizablePanel
              defaultWidth={explorerWidth}
              minWidth={200}
              maxWidth={600}
              position="left"
              onResize={setExplorerWidth}
            >
              <FileExplorer showFileSize />
            </ResizablePanel>
          )}

          {/* Editor Area */}
          <div className="flex flex-col flex-1 overflow-hidden">
            {/* Tab Bar */}
            <TabBar />

            {/* Editor */}
            <div className="flex-1 overflow-hidden">
              <EditorPanel showMinimap showLineNumbers />
            </div>

            {/* Bottom Panel (Terminal/Problems) */}
            {(terminalVisible || problemsVisible) && (
              <div className="h-48 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900">
                <div className="flex items-center gap-2 px-2 py-1 border-b border-gray-200 dark:border-gray-700">
                  <button
                    className={`px-3 py-1 text-xs ${terminalVisible ? 'bg-gray-200 dark:bg-gray-700' : ''}`}
                    onClick={() => setTerminalVisible(!terminalVisible)}
                  >
                    Terminal
                  </button>
                  <button
                    className={`px-3 py-1 text-xs ${problemsVisible ? 'bg-gray-200 dark:bg-gray-700' : ''}`}
                    onClick={() => setProblemsVisible(!problemsVisible)}
                  >
                    Problems
                  </button>
                  <button
                    className="ml-auto px-2 py-1 text-xs hover:bg-gray-200 dark:hover:bg-gray-700"
                    onClick={() => {
                      setTerminalVisible(false);
                      setProblemsVisible(false);
                    }}
                  >
                    ✕
                  </button>
                </div>
                <div className="p-4 text-sm text-gray-600 dark:text-gray-400">
                  {terminalVisible && <div>Terminal panel (coming soon)</div>}
                  {problemsVisible && <div>Problems panel (coming soon)</div>}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Status Bar */}
      {showStatusBar && <StatusBar />}
    </div>
  );
};

export default IDEShell;
