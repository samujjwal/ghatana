/**
 * Event Node Component
 *
 * Visual representation of event emitters and listeners on the canvas.
 * Shows event types, handlers, and connection points.
 *
 * @module canvas/events/EventNode
 */

import React, { useState } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface EventHandler {
  id: string;
  name: string;
  description?: string;
  payload?: Record<string, unknown>;
}

/**
 *
 */
export interface EventNodeProps {
  /**
   * Node ID
   */
  id: string;

  /**
   * Node type (emitter or listener)
   */
  nodeType: 'emitter' | 'listener';

  /**
   * Component/node name
   */
  name: string;

  /**
   * Event type/name
   */
  eventName: string;

  /**
   * Event handlers
   */
  handlers: EventHandler[];

  /**
   * Connection count
   */
  connectionCount?: number;

  /**
   * Whether node is selected
   */
  isSelected?: boolean;

  /**
   * Whether node has errors
   */
  hasError?: boolean;

  /**
   * Error message
   */
  errorMessage?: string;

  /**
   * Callback when node is clicked
   */
  onClick?: () => void;

  /**
   * Callback when handler is clicked
   */
  onHandlerClick?: (handlerId: string) => void;
}

// ============================================================================
// Event Node Component
// ============================================================================

export const EventNode: React.FC<EventNodeProps> = ({
  id,
  nodeType,
  name,
  eventName,
  handlers,
  connectionCount = 0,
  isSelected = false,
  hasError = false,
  errorMessage,
  onClick,
  onHandlerClick,
}) => {
  const [isExpanded, setIsExpanded] = useState(true);

  const isEmitter = nodeType === 'emitter';
  const bgColor = isEmitter ? '#e3f2fd' : '#f3e5f5';
  const borderColor = hasError ? '#f44336' : isSelected ? '#1976d2' : isEmitter ? '#1976d2' : '#9c27b0';
  const icon = isEmitter ? '📡' : '🎯';

  return (
    <div
      onClick={onClick}
      style={{
        width: 260,
        backgroundColor: '#fff',
        border: `2px solid ${borderColor}`,
        borderRadius: 8,
        boxShadow: isSelected
          ? `0 4px 12px ${isEmitter ? 'rgba(25, 118, 210, 0.3)' : 'rgba(156, 39, 176, 0.3)'}`
          : '0 2px 8px rgba(0,0,0,0.1)',
        cursor: 'pointer',
        transition: 'all 0.2s',
        position: 'relative',
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: 12,
          backgroundColor: bgColor,
          borderBottom: `1px solid ${borderColor}`,
          borderRadius: '6px 6px 0 0',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
          <span style={{ fontSize: 18, marginRight: 8 }}>{icon}</span>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 13, fontWeight: 600 }}>{name}</div>
            <div style={{ fontSize: 11, color: '#666' }}>
              {isEmitter ? 'Emitter' : 'Listener'}
            </div>
          </div>
          {hasError && (
            <div
              style={{
                width: 20,
                height: 20,
                borderRadius: '50%',
                backgroundColor: '#f44336',
                color: '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 12,
                fontWeight: 'bold',
              }}
              title={errorMessage}
            >
              !
            </div>
          )}
        </div>

        {/* Event Name */}
        <div
          style={{
            padding: '4px 8px',
            backgroundColor: '#fff',
            borderRadius: 4,
            fontSize: 12,
            fontWeight: 500,
            fontFamily: 'monospace',
            marginBottom: 8,
          }}
        >
          {eventName}
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11 }}>
          <span style={{ color: '#666' }}>
            {connectionCount} connection{connectionCount !== 1 ? 's' : ''}
          </span>
          <button
            onClick={(e) => {
              e.stopPropagation();
              setIsExpanded(!isExpanded);
            }}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              fontSize: 11,
              color: isEmitter ? '#1976d2' : '#9c27b0',
            }}
          >
            {isExpanded ? 'Collapse' : 'Expand'}
          </button>
        </div>
      </div>

      {/* Handlers */}
      {isExpanded && (
        <div style={{ padding: 8 }}>
          {handlers.length === 0 ? (
            <div style={{ padding: 12, textAlign: 'center', color: '#999', fontSize: 11 }}>
              No handlers configured
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {handlers.map((handler) => (
                <button
                  key={handler.id}
                  onClick={(e) => {
                    e.stopPropagation();
                    onHandlerClick?.(handler.id);
                  }}
                  style={{
                    padding: '8px 10px',
                    backgroundColor: '#fafafa',
                    border: '1px solid #e0e0e0',
                    borderRadius: 4,
                    cursor: 'pointer',
                    textAlign: 'left',
                    transition: 'background-color 0.2s',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = bgColor;
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = '#fafafa';
                  }}
                >
                  <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 2 }}>
                    {handler.name}
                  </div>
                  {handler.description && (
                    <div style={{ fontSize: 10, color: '#666' }}>{handler.description}</div>
                  )}
                  {handler.payload && Object.keys(handler.payload).length > 0 && (
                    <div style={{ fontSize: 9, color: '#999', marginTop: 4 }}>
                      Payload: {Object.keys(handler.payload).length} field
                      {Object.keys(handler.payload).length !== 1 ? 's' : ''}
                    </div>
                  )}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Connection Point */}
      <div
        style={{
          position: 'absolute',
          [isEmitter ? 'right' : 'left']: -8,
          top: '50%',
          transform: 'translateY(-50%)',
          width: 16,
          height: 16,
          borderRadius: '50%',
          backgroundColor: hasError ? '#f44336' : isEmitter ? '#1976d2' : '#9c27b0',
          border: '2px solid #fff',
          boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
        }}
        title={isEmitter ? 'Emits events' : 'Listens for events'}
      />
    </div>
  );
};
