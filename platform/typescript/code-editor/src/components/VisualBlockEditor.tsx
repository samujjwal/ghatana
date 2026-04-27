/**
 * Visual Block Editor
 *
 * Lightweight block-based editor that generates deterministic code previews.
 *
 * @doc.type component
 * @doc.purpose Visual-to-code editing bridge for low-code authoring
 * @doc.layer platform
 * @doc.pattern React Component
 */

import React, { useMemo } from 'react';

import type {
  VisualCodeEditorProps,
  VisualCodeBlock,
  CodeLanguage,
} from '../types';

const INDENT = '  ';

function renderBlock(block: VisualCodeBlock, depth: number): string {
  const indent = INDENT.repeat(depth);
  const safeLabel = block.label.trim() || block.type;
  const ownCode = block.code?.trim();

  if (ownCode) {
    return `${indent}${ownCode}`;
  }

  return `${indent}// ${block.type}: ${safeLabel}`;
}

function generateCode(blocks: VisualCodeBlock[], language: CodeLanguage): string {
  const header = `// Generated from visual blocks (${language})`;
  const lines: string[] = [header, ''];

  const visit = (items: VisualCodeBlock[], depth: number): void => {
    for (const block of items) {
      lines.push(renderBlock(block, depth));
      if (block.children && block.children.length > 0) {
        visit(block.children, depth + 1);
      }
    }
  };

  visit(blocks, 0);
  return lines.join('\n');
}

function updateBlockById(
  blocks: VisualCodeBlock[],
  id: string,
  updater: (block: VisualCodeBlock) => VisualCodeBlock
): VisualCodeBlock[] {
  return blocks.map((block) => {
    if (block.id === id) {
      return updater(block);
    }
    if (!block.children || block.children.length === 0) {
      return block;
    }
    return {
      ...block,
      children: updateBlockById(block.children, id, updater),
    };
  });
}

export function VisualBlockEditor({
  blocks,
  onBlocksChange,
  showCodePreview = true,
  targetLanguage = 'typescript',
  height = '420px',
  className,
}: VisualCodeEditorProps): React.ReactElement {
  const generatedCode = useMemo(
    () => generateCode(blocks, targetLanguage),
    [blocks, targetLanguage]
  );

  const handleLabelChange = (id: string, value: string): void => {
    if (!onBlocksChange) {
      return;
    }
    onBlocksChange(
      updateBlockById(blocks, id, (block) => ({
        ...block,
        label: value,
      }))
    );
  };

  return (
    <div
      className={className}
      style={{ height }}
      data-testid="visual-block-editor"
    >
      <div className="grid h-full grid-cols-1 gap-3 md:grid-cols-2">
        <section className="overflow-auto rounded-md border border-gray-200 bg-white p-3">
          <h3 className="mb-2 text-sm font-semibold text-gray-800">Blocks</h3>
          {blocks.length === 0 ? (
            <p className="text-sm text-gray-500">No blocks added yet.</p>
          ) : (
            <ul className="space-y-2">
              {blocks.map((block) => (
                <li
                  key={block.id}
                  className="rounded border border-gray-200 p-2"
                  data-testid={`visual-block-${block.id}`}
                >
                  <div className="mb-1 text-xs font-medium uppercase tracking-wide text-gray-500">
                    {block.type}
                  </div>
                  <input
                    value={block.label}
                    onChange={(event) =>
                      handleLabelChange(block.id, event.target.value)
                    }
                    className="w-full rounded border border-gray-300 px-2 py-1 text-sm"
                    aria-label={`Label for ${block.id}`}
                  />
                </li>
              ))}
            </ul>
          )}
        </section>

        {showCodePreview && (
          <section className="overflow-auto rounded-md border border-gray-200 bg-slate-900 p-3 text-slate-100">
            <h3 className="mb-2 text-sm font-semibold">Generated Code</h3>
            <pre className="whitespace-pre-wrap text-xs" data-testid="visual-code-preview">
              {generatedCode}
            </pre>
          </section>
        )}
      </div>
    </div>
  );
}

export default VisualBlockEditor;