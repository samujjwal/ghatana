/**
 * P1-051: Correlation ID display component.
 *
 * Displays the current request correlation ID for debugging and support.
 * Allows users to copy the ID for error reporting.
 *
 * @doc.type component
 * @doc.purpose Correlation ID display for debugging (P1-051)
 * @doc.layer frontend
 */
import { useState } from 'react';
import { Copy, Check } from 'lucide-react';

interface CorrelationIdDisplayProps {
  correlationId: string;
  showLabel?: boolean;
  compact?: boolean;
}

export function CorrelationIdDisplay({
  correlationId,
  showLabel = true,
  compact = false,
}: CorrelationIdDisplayProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(correlationId);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (compact) {
    return (
      <button
        onClick={handleCopy}
        className="inline-flex items-center gap-1 text-xs text-gray-500 hover:text-gray-700 transition-colors"
        title={`Copy correlation ID: ${correlationId}`}
      >
        <span className="font-mono">{correlationId.slice(0, 8)}...</span>
        {copied ? <Check className="w-3 h-3 text-green-600" /> : <Copy className="w-3 h-3" />}
      </button>
    );
  }

  return (
    <div className="flex items-center gap-2">
      {showLabel && (
        <span className="text-sm font-medium text-gray-600">Correlation ID:</span>
      )}
      <button
        onClick={handleCopy}
        className="flex items-center gap-2 px-3 py-1.5 bg-gray-50 hover:bg-gray-100 rounded-md border border-gray-200 transition-colors group"
        title="Click to copy correlation ID"
      >
        <code className="text-sm font-mono text-gray-700">{correlationId}</code>
        {copied ? (
          <Check className="w-4 h-4 text-green-600" />
        ) : (
          <Copy className="w-4 h-4 text-gray-400 group-hover:text-gray-600" />
        )}
      </button>
    </div>
  );
}
