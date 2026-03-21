import React from 'react';
import { useAtom } from 'jotai';
import { selectedToolAtom } from '../state/canvasAtoms';

import './CanvasToolbar.css';

export type ToolType = 'select' | 'draw' | 'frame' | 'text' | 'sticky';

/**
 * CanvasToolbar - Toolbar for canvas tools
 */
export function CanvasToolbar() {
  const [selectedTool, setSelectedTool] = useAtom(selectedToolAtom);

  const tools: { id: ToolType; label: string; icon: string }[] = [
    { id: 'select', label: 'Select', icon: '↖' },
    { id: 'draw', label: 'Draw', icon: '✎' },
    { id: 'frame', label: 'Frame', icon: '□' },
    { id: 'text', label: 'Text', icon: 'T' },
    { id: 'sticky', label: 'Sticky', icon: '📌' },
  ];

  return (
    <div className="canvas-toolbar" data-testid="canvas-toolbar">
      {tools.map((tool) => (
        <button
          key={tool.id}
          className={`canvas-toolbar__button ${selectedTool === tool.id ? 'active' : ''}`}
          onClick={() => setSelectedTool(tool.id)}
          title={tool.label}
          data-testid={`tool-${tool.id}`}
        >
          <span className="canvas-toolbar__icon">{tool.icon}</span>
          <span className="canvas-toolbar__label">{tool.label}</span>
        </button>
      ))}
    </div>
  );
}

export default CanvasToolbar;
