/**
 * Event Wiring Panel Component
 *
 * UI for configuring event connections between components.
 * Allows creating, editing, and removing event handlers.
 *
 * @module canvas/events/EventWiringPanel
 */

import React, { useState } from 'react';

import type { ComponentNodeData } from '../types/CanvasNode';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface EventConnection {
  id: string;
  sourceEventName: string;
  targetEventName: string;
  payload?: Record<string, unknown>;
  middleware?: string[];
}

/**
 *
 */
export interface EventWiringPanelProps {
  /**
   * Current event configuration
   */
  events?: ComponentNodeData['events'];

  /**
   * Available event types for this component
   */
  availableEvents: string[];

  /**
   * Available target events (from event bus)
   */
  targetEvents: Array<{
    name: string;
    description?: string;
  }>;

  /**
   * Callback when events configuration changes
   */
  onChange: (events: ComponentNodeData['events']) => void;
}

// ============================================================================
// Event Wiring Panel Component
// ============================================================================

export const EventWiringPanel: React.FC<EventWiringPanelProps> = ({
  events = {},
  availableEvents,
  targetEvents,
  onChange,
}) => {
  const [selectedEvent, setSelectedEvent] = useState<string>('');
  const [editingEvent, setEditingEvent] = useState<string | null>(null);

  // Get event config for an event name
  const getEventConfig = (eventName: string) => {
    return events[eventName];
  };

  // Handle add new event handler
  const handleAddEvent = () => {
    if (!selectedEvent) return;

    const updatedEvents = {
      ...events,
      [selectedEvent]: {
        emit: '',
        payload: {},
      },
    };

    onChange(updatedEvents);
    setEditingEvent(selectedEvent);
    setSelectedEvent('');
  };

  // Handle remove event handler
  const handleRemoveEvent = (eventName: string) => {
    const updatedEvents = { ...events };
    delete updatedEvents[eventName];

    onChange(Object.keys(updatedEvents).length > 0 ? updatedEvents : undefined);
    if (editingEvent === eventName) {
      setEditingEvent(null);
    }
  };

  // Handle update event config
  const handleUpdateEvent = (
    eventName: string,
    updates: Partial<ComponentNodeData['events']>
  ) => {
    const updatedEvents = {
      ...events,
      [eventName]: {
        ...events[eventName],
        ...updates[eventName],
      },
    };

    onChange(updatedEvents);
  };

  const configuredEvents = Object.keys(events);
  const unconfiguredEvents = availableEvents.filter((e) => !configuredEvents.includes(e));

  return (
    <div
      style={{
        padding: 16,
        backgroundColor: '#fff',
        border: '1px solid #e0e0e0',
        borderRadius: 8,
      }}
    >
      {/* Header */}
      <div style={{ marginBottom: 16 }}>
        <h4 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Event Wiring</h4>
        <p style={{ margin: '4px 0 0', fontSize: 11, color: '#666' }}>
          Configure event handlers and emitters
        </p>
      </div>

      {/* Add Event Section */}
      {unconfiguredEvents.length > 0 && (
        <div
          style={{
            marginBottom: 16,
            padding: 12,
            backgroundColor: '#f5f5f5',
            borderRadius: 4,
          }}
        >
          <label style={{ display: 'block', marginBottom: 8, fontSize: 12, fontWeight: 500 }}>
            Add Event Handler
          </label>
          <div style={{ display: 'flex', gap: 8 }}>
            <select
              value={selectedEvent}
              onChange={(e) => setSelectedEvent(e.target.value)}
              style={{
                flex: 1,
                padding: '6px 8px',
                border: '1px solid #ccc',
                borderRadius: 4,
                fontSize: 13,
              }}
            >
              <option value="">Select event...</option>
              {unconfiguredEvents.map((eventName) => (
                <option key={eventName} value={eventName}>
                  {eventName}
                </option>
              ))}
            </select>
            <button
              onClick={handleAddEvent}
              disabled={!selectedEvent}
              style={{
                padding: '6px 16px',
                backgroundColor: selectedEvent ? '#1976d2' : '#ccc',
                color: '#fff',
                border: 'none',
                borderRadius: 4,
                cursor: selectedEvent ? 'pointer' : 'not-allowed',
                fontSize: 13,
                fontWeight: 500,
              }}
            >
              Add
            </button>
          </div>
        </div>
      )}

      {/* Configured Events */}
      {configuredEvents.length === 0 ? (
        <div
          style={{
            padding: 16,
            textAlign: 'center',
            color: '#999',
            fontSize: 12,
            backgroundColor: '#fafafa',
            borderRadius: 4,
          }}
        >
          No event handlers configured. Add one to get started.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {configuredEvents.map((eventName) => {
            const config = getEventConfig(eventName);
            const isEditing = editingEvent === eventName;

            return (
              <div
                key={eventName}
                style={{
                  padding: 12,
                  backgroundColor: isEditing ? '#e3f2fd' : '#f9f9f9',
                  border: `1px solid ${isEditing ? '#1976d2' : '#e0e0e0'}`,
                  borderRadius: 4,
                }}
              >
                {/* Event Header */}
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 600, fontFamily: 'monospace' }}>
                      {eventName}
                    </div>
                    <div style={{ fontSize: 11, color: '#666', marginTop: 2 }}>
                      Source Event
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 4 }}>
                    <button
                      onClick={() => setEditingEvent(isEditing ? null : eventName)}
                      style={{
                        padding: '4px 8px',
                        backgroundColor: isEditing ? '#1976d2' : '#e0e0e0',
                        color: isEditing ? '#fff' : '#000',
                        border: 'none',
                        borderRadius: 4,
                        cursor: 'pointer',
                        fontSize: 11,
                      }}
                    >
                      {isEditing ? 'Done' : 'Edit'}
                    </button>
                    <button
                      onClick={() => handleRemoveEvent(eventName)}
                      style={{
                        padding: '4px 8px',
                        backgroundColor: '#ef5350',
                        color: '#fff',
                        border: 'none',
                        borderRadius: 4,
                        cursor: 'pointer',
                        fontSize: 11,
                      }}
                    >
                      Remove
                    </button>
                  </div>
                </div>

                {isEditing && (
                  <>
                    {/* Target Event */}
                    <div style={{ marginBottom: 12 }}>
                      <label style={{ display: 'block', marginBottom: 4, fontSize: 11, fontWeight: 500 }}>
                        Emit To
                      </label>
                      <select
                        value={config?.emit || ''}
                        onChange={(e) =>
                          handleUpdateEvent(eventName, {
                            [eventName]: { ...config, emit: e.target.value },
                          })
                        }
                        style={{
                          width: '100%',
                          padding: '6px 8px',
                          border: '1px solid #ccc',
                          borderRadius: 4,
                          fontSize: 12,
                        }}
                      >
                        <option value="">Select target event...</option>
                        {targetEvents.map((target) => (
                          <option key={target.name} value={target.name}>
                            {target.name}
                            {target.description ? ` — ${target.description}` : ''}
                          </option>
                        ))}
                      </select>
                      <div style={{ marginTop: 4, fontSize: 10, color: '#666' }}>
                        The event to emit when {eventName} is triggered
                      </div>
                    </div>

                    {/* Payload Editor */}
                    <div>
                      <label style={{ display: 'block', marginBottom: 4, fontSize: 11, fontWeight: 500 }}>
                        Payload (JSON)
                      </label>
                      <textarea
                        value={JSON.stringify(config?.payload || {}, null, 2)}
                        onChange={(e) => {
                          try {
                            const payload = JSON.parse(e.target.value);
                            handleUpdateEvent(eventName, {
                              [eventName]: { ...config, payload },
                            });
                          } catch {
                            // Invalid JSON, ignore
                          }
                        }}
                        style={{
                          width: '100%',
                          minHeight: 80,
                          padding: '6px 8px',
                          border: '1px solid #ccc',
                          borderRadius: 4,
                          fontSize: 11,
                          fontFamily: 'monospace',
                          resize: 'vertical',
                        }}
                        placeholder='{\n  "key": "value"\n}'
                      />
                      <div style={{ marginTop: 4, fontSize: 10, color: '#666' }}>
                        Additional data to include with the event
                      </div>
                    </div>
                  </>
                )}

                {!isEditing && config?.emit && (
                  <div
                    style={{
                      padding: 8,
                      backgroundColor: '#fff',
                      borderRadius: 4,
                      fontSize: 11,
                    }}
                  >
                    <div style={{ marginBottom: 4 }}>
                      <strong>→ Emits:</strong>{' '}
                      <code
                        style={{
                          backgroundColor: '#f5f5f5',
                          padding: '2px 6px',
                          borderRadius: 2,
                          fontFamily: 'monospace',
                        }}
                      >
                        {config.emit}
                      </code>
                    </div>
                    {config.payload && Object.keys(config.payload).length > 0 && (
                      <div style={{ color: '#666' }}>
                        Payload: {Object.keys(config.payload).length} field
                        {Object.keys(config.payload).length !== 1 ? 's' : ''}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Info */}
      <div
        style={{
          marginTop: 16,
          padding: 12,
          backgroundColor: '#e8f5e9',
          borderRadius: 4,
          fontSize: 11,
          color: '#2e7d32',
        }}
      >
        <strong>💡 Tip:</strong> Connect components through events to create interactive UIs without
        tight coupling.
      </div>
    </div>
  );
};
