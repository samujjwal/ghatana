/**
 * Collaborative Editor Component for Flashit Web
 * Rich text editor with real-time collaboration
 *
 * @doc.type component
 * @doc.purpose Collaborative text editor with CRDT
 * @doc.layer product
 * @doc.pattern CollaborativeEditor
 */

import React, { useRef, useEffect, useState, useCallback } from 'react';
import { useCollaborativeEdit, Collaborator } from '../hooks/useCollaborativeEdit';

// ============================================================================
// Types & Interfaces
// ============================================================================

export interface CollaborativeEditorProps {
  documentId: string;
  userId: string;
  userName?: string;
  placeholder?: string;
  readOnly?: boolean;
  onSave?: (text: string) => void;
  className?: string;
}

// ============================================================================
// Collaborator Cursor Component
// ============================================================================

interface CollaboratorCursorProps {
  collaborator: Collaborator;
  position: { top: number; left: number };
}

function CollaboratorCursor({ collaborator, position }: CollaboratorCursorProps): JSX.Element {
  return (
    <div
      className="collaborator-cursor"
      style={{
        position: 'absolute',
        top: position.top,
        left: position.left,
        pointerEvents: 'none',
        zIndex: 100,
      }}
    >
      <div
        className="cursor-line"
        style={{
          width: '2px',
          height: '20px',
          backgroundColor: collaborator.color,
        }}
      />
      <div
        className="cursor-label"
        style={{
          backgroundColor: collaborator.color,
          color: 'white',
          padding: '2px 6px',
          borderRadius: '4px',
          fontSize: '12px',
          whiteSpace: 'nowrap',
          marginTop: '2px',
        }}
      >
        {collaborator.name}
      </div>
    </div>
  );
}

// ============================================================================
// Collaborative Editor Component
// ============================================================================

/**
 * CollaborativeEditor component
 */
export function CollaborativeEditor({
  documentId,
  userId,
  userName = 'Anonymous',
  placeholder = 'Start typing...',
  readOnly = false,
  onSave,
  className = '',
}: CollaborativeEditorProps): JSX.Element {
  const editorRef = useRef<HTMLTextAreaElement>(null);
  const [cursorPosition, setCursorPosition] = useState(0);
  const [selection, setSelection] = useState<{ start: number; end: number } | null>(null);

  const {
    text,
    collaborators,
    isConnected,
    isSyncing,
    insert,
    delete: deleteText,
    setText,
    updateCursor,
    updateSelection,
    undo,
    redo,
    saveDocument,
  } = useCollaborativeEdit({
    documentId,
    userId,
  });

  /**
   * Handle text change
   */
  const handleChange = useCallback(
    (event: React.ChangeEvent<HTMLTextAreaElement>) => {
      const newText = event.target.value;
      const oldText = text;

      if (newText.length > oldText.length) {
        // Text was inserted
        const insertPos = event.target.selectionStart - (newText.length - oldText.length);
        const insertedText = newText.substring(insertPos, event.target.selectionStart);
        insert(insertPos, insertedText);
      } else if (newText.length < oldText.length) {
        // Text was deleted
        const deletePos = event.target.selectionStart;
        const deleteLength = oldText.length - newText.length;
        deleteText(deletePos, deleteLength);
      } else {
        // Text was replaced (shouldn't normally happen with single character input)
        setText(newText);
      }
    },
    [text, insert, deleteText, setText]
  );

  /**
   * Handle cursor/selection change
   */
  const handleSelectionChange = useCallback(() => {
    if (!editorRef.current) return;

    const start = editorRef.current.selectionStart;
    const end = editorRef.current.selectionEnd;

    setCursorPosition(start);

    if (start === end) {
      updateCursor(start);
      setSelection(null);
    } else {
      updateSelection(start, end);
      setSelection({ start, end });
    }
  }, [updateCursor, updateSelection]);

  /**
   * Handle keyboard shortcuts
   */
  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
      // Cmd/Ctrl + Z for undo
      if ((event.metaKey || event.ctrlKey) && event.key === 'z' && !event.shiftKey) {
        event.preventDefault();
        undo();
      }
      
      // Cmd/Ctrl + Shift + Z or Cmd/Ctrl + Y for redo
      if (
        ((event.metaKey || event.ctrlKey) && event.key === 'z' && event.shiftKey) ||
        ((event.metaKey || event.ctrlKey) && event.key === 'y')
      ) {
        event.preventDefault();
        redo();
      }

      // Cmd/Ctrl + S for save
      if ((event.metaKey || event.ctrlKey) && event.key === 's') {
        event.preventDefault();
        saveDocument();
        if (onSave) {
          onSave(text);
        }
      }
    },
    [undo, redo, saveDocument, onSave, text]
  );

  /**
   * Update textarea value when text changes
   */
  useEffect(() => {
    if (editorRef.current && editorRef.current.value !== text) {
      const start = editorRef.current.selectionStart;
      const end = editorRef.current.selectionEnd;
      
      editorRef.current.value = text;
      
      // Restore cursor position
      editorRef.current.setSelectionRange(start, end);
    }
  }, [text]);

  /**
   * Calculate cursor positions for collaborators
   */
  const calculateCursorPosition = (textPosition: number): { top: number; left: number } => {
    if (!editorRef.current) return { top: 0, left: 0 };

    // This is a simplified version - in production, you'd want to use
    // a proper library to calculate exact cursor positions
    const lines = text.substring(0, textPosition).split('\n');
    const lineHeight = 24; // Approximate line height
    const charWidth = 8; // Approximate character width
    
    const top = (lines.length - 1) * lineHeight;
    const left = (lines[lines.length - 1].length) * charWidth;

    return { top, left };
  };

  return (
    <div className={`collaborative-editor ${className}`}>
      {/* Status Bar */}
      <div className="editor-status-bar">
        <div className="status-left">
          <span className={`connection-status ${isConnected ? 'connected' : 'disconnected'}`}>
            {isConnected ? '● Connected' : '○ Disconnected'}
          </span>
          {isSyncing && <span className="syncing-status">Syncing...</span>}
        </div>

        <div className="status-right">
          {/* Collaborators */}
          <div className="collaborators-list">
            {collaborators.map((collaborator) => (
              <div
                key={collaborator.id}
                className="collaborator-avatar"
                style={{ backgroundColor: collaborator.color }}
                title={collaborator.name}
              >
                {collaborator.name.charAt(0).toUpperCase()}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Editor Container */}
      <div className="editor-container">
        <textarea
          ref={editorRef}
          className="editor-textarea"
          placeholder={placeholder}
          readOnly={readOnly}
          onChange={handleChange}
          onSelect={handleSelectionChange}
          onKeyDown={handleKeyDown}
          spellCheck={true}
        />

        {/* Collaborator Cursors */}
        {collaborators.map((collaborator) => {
          if (collaborator.cursor === undefined) return null;
          const position = calculateCursorPosition(collaborator.cursor);
          return (
            <CollaboratorCursor
              key={collaborator.id}
              collaborator={collaborator}
              position={position}
            />
          );
        })}

        {/* Collaborator Selections */}
        {collaborators.map((collaborator) => {
          if (!collaborator.selection) return null;
          // In production, render selection highlights
          return null;
        })}
      </div>

      <style jsx>{`
        .collaborative-editor {
          display: flex;
          flex-direction: column;
          height: 100%;
          background: white;
          border-radius: 8px;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
          overflow: hidden;
        }

        .editor-status-bar {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 0.75rem 1rem;
          background: #f9fafb;
          border-bottom: 1px solid #e5e7eb;
        }

        .status-left,
        .status-right {
          display: flex;
          align-items: center;
          gap: 1rem;
        }

        .connection-status {
          font-size: 0.875rem;
          font-weight: 500;
        }

        .connection-status.connected {
          color: #10b981;
        }

        .connection-status.disconnected {
          color: #ef4444;
        }

        .syncing-status {
          font-size: 0.875rem;
          color: #6b7280;
          font-style: italic;
        }

        .collaborators-list {
          display: flex;
          gap: 0.5rem;
        }

        .collaborator-avatar {
          width: 32px;
          height: 32px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          color: white;
          font-size: 0.875rem;
          font-weight: 600;
          border: 2px solid white;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
        }

        .editor-container {
          position: relative;
          flex: 1;
          overflow: hidden;
        }

        .editor-textarea {
          width: 100%;
          height: 100%;
          padding: 1.5rem;
          border: none;
          outline: none;
          resize: none;
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
          font-size: 1rem;
          line-height: 1.5;
          color: #1f2937;
        }

        .editor-textarea::placeholder {
          color: #9ca3af;
        }

        .editor-textarea:read-only {
          background: #f9fafb;
          cursor: not-allowed;
        }

        @media (max-width: 640px) {
          .editor-status-bar {
            padding: 0.5rem;
          }

          .status-left,
          .status-right {
            gap: 0.5rem;
          }

          .collaborator-avatar {
            width: 28px;
            height: 28px;
            font-size: 0.75rem;
          }

          .editor-textarea {
            padding: 1rem;
            font-size: 0.9375rem;
          }
        }
      `}</style>
    </div>
  );
}

export default CollaborativeEditor;
