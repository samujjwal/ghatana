/**
 * StagePalette — Draggable panel listing available stage types
 * and connectors for drag-and-drop onto the pipeline canvas.
 *
 * @doc.type component
 * @doc.purpose Drag-and-drop palette for pipeline building
 * @doc.layer frontend
 */
import React, { useState, type DragEvent } from 'react';
import clsx from 'clsx';
import {
  STAGE_PALETTE,
  CONNECTOR_PALETTE,
  type PaletteItem,
  type ConnectorPaletteItem,
} from '@/types/pipeline.types';

// ─── Drag helpers ────────────────────────────────────────────────────

function onDragStart(e: DragEvent, nodeType: string, payload: string) {
  e.dataTransfer.setData('application/reactflow-type', nodeType);
  e.dataTransfer.setData('application/reactflow-data', payload);
  e.dataTransfer.effectAllowed = 'move';
}

// ─── Stage Item ──────────────────────────────────────────────────────

function StageItem({ item }: { item: PaletteItem }) {
  return (
    <div
      draggable
      onDragStart={(e) =>
        onDragStart(e, 'stage', JSON.stringify(item))
      }
      className="flex items-center gap-2 px-3 py-2 rounded-md border border-gray-200
                 bg-white hover:bg-gray-50 cursor-grab active:cursor-grabbing
                 transition-colors select-none"
    >
      <span className="text-lg flex-shrink-0">{getStageEmoji(item.kind)}</span>
      <div className="min-w-0">
        <p className="text-sm font-medium truncate">{item.label}</p>
        <p className="text-[11px] text-gray-500 truncate">{item.description}</p>
      </div>
    </div>
  );
}

// ─── Connector Item ──────────────────────────────────────────────────

function ConnectorItem({ item }: { item: ConnectorPaletteItem }) {
  return (
    <div
      draggable
      onDragStart={(e) =>
        onDragStart(e, 'connector', JSON.stringify(item))
      }
      className="flex items-center gap-2 px-3 py-2 rounded-md border border-gray-200
                 bg-white hover:bg-gray-50 cursor-grab active:cursor-grabbing
                 transition-colors select-none"
    >
      <span className="text-lg flex-shrink-0">🔌</span>
      <div className="min-w-0">
        <p className="text-sm font-medium truncate">{item.label}</p>
        <p className="text-[11px] text-gray-500 truncate">
          {item.direction} · {item.description}
        </p>
      </div>
    </div>
  );
}

// ─── Main Palette ────────────────────────────────────────────────────

export function StagePalette() {
  const [tab, setTab] = useState<'stages' | 'connectors'>('stages');

  return (
    <aside
      className="w-64 border-r border-gray-200 bg-gray-50 flex flex-col overflow-hidden"
      data-testid="stage-palette"
    >
      {/* Tab header */}
      <div className="flex border-b border-gray-200">
        <button
          className={clsx(
            'flex-1 py-2 text-xs font-semibold transition-colors',
            tab === 'stages' ? 'bg-white text-blue-600 border-b-2 border-blue-600' : 'text-gray-500',
          )}
          onClick={() => setTab('stages')}
        >
          Stages
        </button>
        <button
          className={clsx(
            'flex-1 py-2 text-xs font-semibold transition-colors',
            tab === 'connectors'
              ? 'bg-white text-blue-600 border-b-2 border-blue-600'
              : 'text-gray-500',
          )}
          onClick={() => setTab('connectors')}
        >
          Connectors
        </button>
      </div>

      {/* Item list */}
      <div className="flex-1 overflow-y-auto p-2 space-y-1.5">
        {tab === 'stages'
          ? STAGE_PALETTE.map((item) => <StageItem key={item.id} item={item} />)
          : CONNECTOR_PALETTE.map((item) => <ConnectorItem key={item.id} item={item} />)}
      </div>

      {/* Help hint */}
      <div className="px-3 py-2 text-[10px] text-gray-400 border-t border-gray-200">
        Drag items onto the canvas to build your pipeline
      </div>
    </aside>
  );
}

// ─── Helpers ─────────────────────────────────────────────────────────

function getStageEmoji(kind: string): string {
  const map: Record<string, string> = {
    ingestion: '📥',
    validation: '✅',
    transformation: '🔀',
    enrichment: '✨',
    analysis: '📊',
    persistence: '💾',
    custom: '🧩',
  };
  return map[kind] ?? '🧩';
}
