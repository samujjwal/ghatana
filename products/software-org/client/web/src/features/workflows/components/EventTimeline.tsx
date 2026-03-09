import { memo } from 'react';
import { useWorkflowEvents } from '@/hooks/useWorkflowEvents';

/**
 * Horizontal timeline with scrubber for event playback control.
 *
 * <p><b>Purpose</b><br>
 * Displays events chronologically in a strip at bottom of workflow canvas.
 * Users can scrub through time to replay historical events or jump to specific events.
 *
 * <p><b>Features</b><br>
 * - Event markers colored by type (feature/test/deploy/incident)
 * - Interactive scrubber for time navigation
 * - Playback speed indicator
 * - Event detail on click
 *
 * <p><b>Props</b><br>
 * @param workflowId - Workflow identifier
 * @param playbackSpeed - Animation speed multiplier
 * @param onEventClick - Callback when event marker clicked
 *
 * @doc.type component
 * @doc.purpose Event timeline visualization with scrubber
 * @doc.layer product
 * @doc.pattern Chart
 */
interface EventTimelineProps {
    workflowId: string;
    playbackSpeed: number;
    onEventClick: (eventId: string) => void;
}

const EVENT_TYPES = {
    feature: { color: '#8b5cf6', label: 'Feature' },
    test: { color: '#06b6d4', label: 'Test' },
    build: { color: '#10b981', label: 'Build' },
    deploy: { color: '#f59e0b', label: 'Deploy' },
    incident: { color: '#ef4444', label: 'Incident' },
};

export const EventTimeline = memo(function EventTimeline({
    onEventClick,
}: EventTimelineProps) {
    // GIVEN: EventTimeline component with list of events
    // WHEN: User clicks event marker or drags scrubber
    // THEN: Update time context or open event details

    // Fetch real workflow events from API
    const { data: allEvents = [] } = useWorkflowEvents({ refetchInterval: 10000 });

    // Map API events to timeline format (handle undefined type)
    const timelineEvents = allEvents.map((event: any, idx: number) => ({
        id: event.id || `evt-${idx}`,
        type: (event.type || 'feature') as keyof typeof EVENT_TYPES,
        timestamp: event.timestamp,
    }));

    return (
        <div className="w-full h-full flex flex-col bg-slate-950 border-t border-slate-700 p-4">
            {/* Timeline Header */}
            <div className="flex justify-between items-center mb-3">
                <span className="text-xs text-slate-400">Event Timeline ({timelineEvents.length})</span>
                <div className="flex gap-2 text-xs text-slate-400">
                    <span>10:00</span>
                    <span>|</span>
                    <span>12:00</span>
                    <span>|</span>
                    <span>14:00</span>
                    <span>|</span>
                    <span>15:00</span>
                </div>
            </div>

            {/* Timeline Track with Events */}
            <svg className="w-full flex-1 bg-gradient-to-r from-slate-900 to-slate-800 rounded" viewBox="0 0 1000 60">
                {/* Hour markers */}
                <g strokeWidth="0.5" stroke="#475569">
                    {[0, 200, 400, 600, 800, 1000].map((x) => (
                        <line key={`marker-${x}`} x1={x} y1="0" x2={x} y2="60" />
                    ))}
                </g>

                {/* Event markers */}
                {timelineEvents.length > 0 ? (
                    timelineEvents.map((event, idx) => {
                        const x = (idx / Math.max(timelineEvents.length, 1)) * 1000;
                        const eventType: keyof typeof EVENT_TYPES = (event.type || 'feature');
                        const eventColor = EVENT_TYPES[eventType].color;

                        return (
                            <g
                                key={event.id}
                                onClick={() => onEventClick(event.id)}
                                style={{ cursor: 'pointer' }}
                            >
                                {/* Event marker */}
                                <circle cx={x} cy="30" r="5" fill={eventColor} className="hover:r-7 transition-all" />
                                {/* Label */}
                                <text
                                    x={x}
                                    y="50"
                                    textAnchor="middle"
                                    className="text-xs fill-slate-400"
                                    fontSize="10"
                                >
                                    {eventType}
                                </text>
                            </g>
                        );
                    })
                ) : (
                    <text x="500" y="30" textAnchor="middle" className="text-xs fill-slate-500">
                        No events yet
                    </text>
                )}

                {/* Scrubber line (current time indicator) */}
                <line x1="500" y1="0" x2="500" y2="60" stroke="#60a5fa" strokeWidth="2" />
            </svg>

            {/* Legend */}
            <div className="flex gap-4 mt-3 text-xs">
                {Object.entries(EVENT_TYPES).map(([key, { color, label }]) => (
                    <div key={key} className="flex items-center gap-1">
                        <div className="w-3 h-3 rounded-full" style={{ backgroundColor: color }} />
                        <span className="text-slate-400">{label}</span>
                    </div>
                ))}
            </div>
        </div>
    );
});

export default EventTimeline;
