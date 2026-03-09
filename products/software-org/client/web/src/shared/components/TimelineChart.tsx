import { Card } from "@/components/ui";

/**
 * Event Timeline Chart Component
 *
 * <p><b>Purpose</b><br>
 * Displays chronological event timeline with event type markers and scrubber.
 * Allows users to navigate through events and inspect details.
 *
 * <p><b>Features</b><br>
 * - Timeline visualization with event markers
 * - Scrubber for time navigation
 * - Event type color coding
 * - Time range display
 * - Event density visualization
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <TimelineChart
 *   events={[
 *     { timestamp: 1700000000, type: 'deployment' },
 *     { timestamp: 1700010000, type: 'incident' }
 *   ]}
 *   onTimeChange={(time) => console.log(time)}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Event timeline visualization
 * @doc.layer product
 * @doc.pattern Organism
 */
interface TimelineEvent {
    timestamp: number;
    type: "feature" | "test" | "deploy" | "incident";
    label?: string;
}

interface TimelineChartProps {
    events: TimelineEvent[];
    onTimeChange?: (timestamp: number) => void;
    height?: number;
}

const eventTypeColors = {
    feature: "bg-blue-500",
    test: "bg-green-500",
    deploy: "bg-purple-500",
    incident: "bg-red-500",
};

export function TimelineChart({
    events,
    onTimeChange,
    height = 200,
}: TimelineChartProps) {

    if (events.length === 0) {
        return (
            <Card padded={false}>
                <div
                    className="w-full bg-slate-100 dark:bg-slate-900 rounded-lg flex items-center justify-center text-slate-500 dark:text-neutral-400"
                    style={{ height }}
                >
                    Loading timeline...
                </div>
            </Card>
        );
    }

    const minTime = Math.min(...events.map((e) => e.timestamp));
    const maxTime = Math.max(...events.map((e) => e.timestamp));
    const timeRange = maxTime - minTime || 1;

    // Group events by hour for density visualization
    const eventDensity = Array(24)
        .fill(0)
        .map((_, hourIndex) => {
            const hourStart = minTime + (hourIndex / 24) * timeRange;
            const hourEnd = minTime + ((hourIndex + 1) / 24) * timeRange;
            return events.filter(
                (e) => e.timestamp >= hourStart && e.timestamp < hourEnd
            ).length;
        });

    const maxDensity = Math.max(...eventDensity, 1);

    return (
        <Card padded={false}>
            <div className="w-full space-y-4">
                {/* Timeline header */}
                <div className="flex justify-between items-center px-4">
                    <p className="text-sm font-medium text-slate-600 dark:text-neutral-400">
                        Timeline: {events.length} events
                    </p>
                    <div className="flex gap-3 text-xs">
                        {Object.entries(eventTypeColors).map(([type, color]) => (
                            <div key={type} className="flex items-center gap-1">
                                <div className={`w-2 h-2 rounded-full ${color}`} />
                                <span className="capitalize text-slate-600 dark:text-neutral-400">
                                    {type}
                                </span>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Density bars */}
                <div className="flex gap-0.5 items-end px-4 h-12 bg-slate-50 dark:bg-slate-900/50 rounded-lg">
                    {eventDensity.map((density, idx) => (
                        <div
                            key={idx}
                            className="flex-1 bg-slate-300 dark:bg-neutral-700 rounded-t transition-all hover:bg-slate-400 dark:hover:bg-slate-600 cursor-pointer"
                            style={{ height: `${(density / maxDensity) * 100}%` }}
                            title={`${density} events`}
                        />
                    ))}
                </div>

                {/* Scrubber */}
                <div className="relative px-4">
                    <div className="h-12 bg-gradient-to-r from-slate-100 to-slate-50 dark:from-slate-800 dark:to-slate-900 rounded-lg border border-slate-200 dark:border-neutral-600 relative overflow-hidden">
                        {/* Event markers */}
                        <div className="absolute inset-0 flex items-center">
                            {events.slice(0, 10).map((event, idx) => {
                                const position = ((event.timestamp - minTime) / timeRange) * 100;
                                return (
                                    <div
                                        key={idx}
                                        className="absolute top-1/2 -translate-y-1/2 w-2 h-2 rounded-full -translate-x-1/2 cursor-pointer hover:w-3 hover:h-3 transition-all"
                                        style={{
                                            left: `${position}%`,
                                            backgroundColor:
                                                eventTypeColors[event.type as keyof typeof eventTypeColors],
                                        }}
                                        onClick={() => onTimeChange?.(event.timestamp)}
                                        title={event.label || event.type}
                                    />
                                );
                            })}
                        </div>

                        {/* Scrubber thumb */}
                        <input
                            type="range"
                            min={minTime}
                            max={maxTime}
                            defaultValue={minTime}
                            onChange={(e) => onTimeChange?.(Number(e.target.value))}
                            className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
                        />
                        <div className="pointer-events-none absolute inset-0 flex items-center">
                            <div className="h-1 w-full bg-gradient-to-r from-blue-400 to-blue-500" />
                        </div>
                    </div>
                </div>

                {/* Time labels */}
                <div className="flex justify-between text-xs text-slate-500 dark:text-slate-500 px-4">
                    <span>
                        {new Date(minTime * 1000).toLocaleTimeString([], {
                            hour: "2-digit",
                            minute: "2-digit",
                        })}
                    </span>
                    <span>
                        {new Date(maxTime * 1000).toLocaleTimeString([], {
                            hour: "2-digit",
                            minute: "2-digit",
                        })}
                    </span>
                </div>
            </div>
        </Card>
    );
}

export default TimelineChart;
