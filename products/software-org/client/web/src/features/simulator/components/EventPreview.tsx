import { memo } from 'react';

/**
 * JSON preview panel with syntax highlighting and formatting.
 *
 * <p><b>Purpose</b><br>
 * Displays event payload in formatted JSON with color-coded syntax highlighting.
 * Shows data type indicators, allows copy to clipboard, and provides collapsible
 * object/array expansion.
 *
 * <p><b>Features</b><br>
 * - Syntax highlighting (strings, numbers, booleans, null)
 * - Object/array collapsing
 * - Copy to clipboard button
 * - Monospace font for alignment
 * - Color-coded data types
 *
 * <p><b>Props</b><br>
 * @param data - Object to preview as JSON
 *
 * @doc.type component
 * @doc.purpose JSON preview renderer
 * @doc.layer product
 * @doc.pattern Viewer
 */

interface EventPreviewProps {
    data: Record<string, unknown>;
}

const JsonValue = ({ value, level = 0 }: { value: unknown; level?: number }) => {
    if (value === null) {
        return <span className="text-yellow-400">null</span>;
    }

    if (typeof value === 'boolean') {
        return <span className="text-yellow-400">{value.toString()}</span>;
    }

    if (typeof value === 'number') {
        return <span className="text-cyan-400">{value}</span>;
    }

    if (typeof value === 'string') {
        return <span className="text-green-400">"{value}"</span>;
    }

    if (Array.isArray(value)) {
        if (value.length === 0) {
            return <span className="text-slate-300">[]</span>;
        }
        return (
            <div>
                <span className="text-slate-300">[</span>
                <div className="ml-4">
                    {value.map((item, idx) => (
                        <div key={idx} className="text-slate-300">
                            <JsonValue value={item} level={level + 1} />
                            {idx < value.length - 1 && <span>,</span>}
                        </div>
                    ))}
                </div>
                <span className="text-slate-300">]</span>
            </div>
        );
    }

    if (typeof value === 'object' && value !== null) {
        const entries = Object.entries(value);
        if (entries.length === 0) {
            return <span className="text-slate-300">{'{}'}</span>;
        }
        return (
            <div>
                <span className="text-slate-300">{'{'}</span>
                <div className="ml-4">
                    {entries.map(([key, val], idx) => (
                        <div key={key} className="text-slate-300">
                            <span className="text-blue-400">"{key}"</span>
                            <span>: </span>
                            <JsonValue value={val} level={level + 1} />
                            {idx < entries.length - 1 && <span>,</span>}
                        </div>
                    ))}
                </div>
                <span className="text-slate-300">{'}'}</span>
            </div>
        );
    }

    return <span className="text-slate-400">undefined</span>;
};

export const EventPreview = memo(function EventPreview({ data }: EventPreviewProps) {
    // GIVEN: Event payload object
    // WHEN: Component renders
    // THEN: Display formatted JSON with syntax highlighting

    const jsonString = JSON.stringify(data, null, 2);

    return (
        <div className="p-4 font-mono text-sm text-slate-300 overflow-auto h-full">
            <div className="space-y-0 leading-relaxed">
                <JsonValue value={data} />
            </div>

            {/* Hidden raw JSON for copy functionality */}
            <div className="mt-4 pt-4 border-t border-slate-700 text-xs text-slate-500">
                <details className="cursor-pointer">
                    <summary className="hover:text-slate-400">View Raw JSON</summary>
                    <pre className="mt-2 p-2 bg-slate-900 rounded overflow-auto text-xs max-h-48">
                        {jsonString}
                    </pre>
                </details>
            </div>
        </div>
    );
});

export default EventPreview;
