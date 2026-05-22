/**
 * Interaction Timeline View - displays interaction events over time.
 *
 * @doc.type component
 * @doc.purpose Visualize interaction events in chronological order
 * @doc.layer studio
 */

import React from "react";

interface InteractionEvent {
  eventId: string;
  timestamp: string;
  contractId: string;
  providerProductId: string;
  consumerProductId: string;
  status: "succeeded" | "denied" | "failed";
  durationMs?: number;
  reasonCode?: string;
}

interface InteractionTimelineViewProps {
  events: readonly InteractionEvent[];
  onEventClick?: (eventId: string) => void;
}

export function InteractionTimelineView({ events, onEventClick }: InteractionTimelineViewProps) {
  const sortedEvents = [...events].sort(
    (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  );

  return (
    <div className="interaction-timeline-view">
      <div className="timeline-header">
        <h2>Interaction Timeline</h2>
        <div className="timeline-stats">
          <span className="stat-item">
            <span className="count">{events.length}</span>
            <span className="label">Total Events</span>
          </span>
          <span className="stat-item succeeded">
            <span className="count">{events.filter((e) => e.status === "succeeded").length}</span>
            <span className="label">Succeeded</span>
          </span>
          <span className="stat-item denied">
            <span className="count">{events.filter((e) => e.status === "denied").length}</span>
            <span className="label">Denied</span>
          </span>
          <span className="stat-item failed">
            <span className="count">{events.filter((e) => e.status === "failed").length}</span>
            <span className="label">Failed</span>
          </span>
        </div>
      </div>

      <div className="timeline-container">
        <div className="timeline-line" />
        {sortedEvents.map((event, index) => (
          <div
            key={event.eventId}
            className={`timeline-event ${event.status}`}
            onClick={() => onEventClick?.(event.eventId)}
          >
            <div className="timeline-marker">
              <span className="marker-dot" />
              <span className="marker-time">
                {new Date(event.timestamp).toLocaleTimeString()}
              </span>
            </div>
            <div className="timeline-content">
              <div className="event-header">
                <span className="event-id">{event.eventId}</span>
                <span className={`status ${event.status}`}>{event.status}</span>
                {event.durationMs && (
                  <span className="duration">{event.durationMs}ms</span>
                )}
              </div>
              <div className="event-details">
                <div className="detail-row">
                  <span className="label">Contract:</span>
                  <span className="value">{event.contractId}</span>
                </div>
                <div className="detail-row">
                  <span className="label">Provider:</span>
                  <span className="value">{event.providerProductId}</span>
                </div>
                <div className="detail-row">
                  <span className="label">Consumer:</span>
                  <span className="value">{event.consumerProductId}</span>
                </div>
                {event.reasonCode && (
                  <div className="detail-row">
                    <span className="label">Reason:</span>
                    <span className="value reason">{event.reasonCode}</span>
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
