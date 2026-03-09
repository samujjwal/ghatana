/**
 * Data Source Node Component
 *
 * Visual representation of data sources on the canvas.
 * Shows schema, status, and connection points for data bindings.
 *
 * @module canvas/visualization/DataSourceNode
 */

import React, { useState } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface DataSourceNodeProps {
  /**
   * Data source ID
   */
  id: string;

  /**
   * Data source name
   */
  name: string;

  /**
   * Data source type (API, State, LocalStorage, etc.)
   */
  type: 'api' | 'state' | 'localStorage' | 'query' | 'custom';

  /**
   * Available fields
   */
  fields: Array<{
    name: string;
    type: string;
  }>;

  /**
   * Connection status
   */
  status?: 'connected' | 'disconnected' | 'loading' | 'error';

  /**
   * Number of consumers
   */
  consumerCount?: number;

  /**
   * Whether node is selected
   */
  isSelected?: boolean;

  /**
   * Callback when node is clicked
   */
  onClick?: () => void;

  /**
   * Callback when field is clicked (for binding)
   */
  onFieldClick?: (fieldName: string) => void;
}

// ============================================================================
// Data Source Node Component
// ============================================================================

export const DataSourceNode: React.FC<DataSourceNodeProps> = ({
  id,
  name,
  type,
  fields,
  status = 'connected',
  consumerCount = 0,
  isSelected = false,
  onClick,
  onFieldClick,
}) => {
  const [isExpanded, setIsExpanded] = useState(true);

  // Get icon based on type
  const getTypeIcon = () => {
    switch (type) {
      case 'api':
        return '🌐';
      case 'state':
        return '📦';
      case 'localStorage':
        return '💾';
      case 'query':
        return '🔍';
      case 'custom':
        return '⚙️';
      default:
        return '📊';
    }
  };

  // Get status color
  const getStatusColor = () => {
    switch (status) {
      case 'connected':
        return '#4caf50';
      case 'disconnected':
        return '#9e9e9e';
      case 'loading':
        return '#ff9800';
      case 'error':
        return '#f44336';
      default:
        return '#9e9e9e';
    }
  };

  return (
    <div
      onClick={onClick}
      style={{
        width: 280,
        backgroundColor: '#fff',
        border: `2px solid ${isSelected ? '#1976d2' : '#e0e0e0'}`,
        borderRadius: 8,
        boxShadow: isSelected ? '0 4px 12px rgba(25, 118, 210, 0.3)' : '0 2px 8px rgba(0,0,0,0.1)',
        cursor: 'pointer',
        transition: 'all 0.2s',
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: 12,
          backgroundColor: '#f5f5f5',
          borderBottom: '1px solid #e0e0e0',
          borderRadius: '6px 6px 0 0',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
          <span style={{ fontSize: 20, marginRight: 8 }}>{getTypeIcon()}</span>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 14, fontWeight: 600 }}>{name}</div>
            <div style={{ fontSize: 11, color: '#666', textTransform: 'capitalize' }}>
              {type}
            </div>
          </div>
          <div
            style={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              backgroundColor: getStatusColor(),
            }}
            title={status}
          />
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11 }}>
          <span style={{ color: '#666' }}>
            {consumerCount} consumer{consumerCount !== 1 ? 's' : ''}
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
              fontSize: 12,
              color: '#1976d2',
            }}
          >
            {isExpanded ? 'Collapse' : 'Expand'}
          </button>
        </div>
      </div>

      {/* Fields */}
      {isExpanded && (
        <div style={{ padding: 8 }}>
          {fields.length === 0 ? (
            <div style={{ padding: 16, textAlign: 'center', color: '#999', fontSize: 12 }}>
              No fields available
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {fields.map((field) => (
                <button
                  key={field.name}
                  onClick={(e) => {
                    e.stopPropagation();
                    onFieldClick?.(field.name);
                  }}
                  style={{
                    padding: '8px 12px',
                    backgroundColor: '#fafafa',
                    border: '1px solid #e0e0e0',
                    borderRadius: 4,
                    cursor: 'pointer',
                    textAlign: 'left',
                    transition: 'background-color 0.2s',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = '#e3f2fd';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = '#fafafa';
                  }}
                >
                  <div style={{ fontSize: 13, fontWeight: 500 }}>{field.name}</div>
                  <div style={{ fontSize: 11, color: '#666' }}>{field.type}</div>
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
          right: -8,
          top: '50%',
          transform: 'translateY(-50%)',
          width: 16,
          height: 16,
          borderRadius: '50%',
          backgroundColor: getStatusColor(),
          border: '2px solid #fff',
          boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
        }}
        title="Drag to connect"
      />
    </div>
  );
};
