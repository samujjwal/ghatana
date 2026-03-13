/**
 * EpisodeTimeline — chronological list of agent episode records.
 *
 * @doc.type component
 * @doc.purpose Display a scrollable timeline of agent episode records
 * @doc.layer frontend
 */
import React from 'react';
import type { EpisodeRecord } from '@/api/aep.api';

interface EpisodeTimelineProps {
  episodes: EpisodeRecord[];
  className?: string;
}

const OUTCOME_STYLES: Record<EpisodeRecord['outcome'], string> = {
  SUCCESS: 'text-green-700 bg-green-50 dark:text-green-300 dark:bg-green-950',
  FAILURE: 'text-red-700 bg-red-50 dark:text-red-300 dark:bg-red-950',
  TIMEOUT: 'text-yellow-700 bg-yellow-50 dark:text-yellow-300 dark:bg-yellow-950',
  CANCELLED: 'text-gray-500 bg-gray-100 dark:text-gray-400 dark:bg-gray-800',
};

function formatTs(iso: string) {
  return new Date(iso).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

export function EpisodeTimeline({ episodes, className = '' }: EpisodeTimelineProps) {
  if (episodes.length === 0) {
    return (
      <p className={['text-sm text-gray-400', className].join(' ')}>
        No episodes recorded yet.
      </p>
    );
  }

  return (
    <ol className={['space-y-2', className].join(' ')}>
      {episodes.map((ep) => (
        <li
          key={ep.id}
          className="flex items-start gap-3 rounded-lg border border-gray-100 dark:border-gray-800 bg-white dark:bg-gray-900 px-4 py-3 text-sm"
        >
          {/* Time */}
          <span className="flex-shrink-0 text-xs tabular-nums text-gray-400 pt-0.5 w-16">
            {formatTs(ep.timestamp)}
          </span>

          {/* Body */}
          <div className="flex-1 min-w-0 space-y-0.5">
            {ep.inputSummary && (
              <p className="text-gray-600 dark:text-gray-400 text-xs truncate">
                <span className="font-medium text-gray-700 dark:text-gray-300">IN:</span>{' '}
                {ep.inputSummary}
              </p>
            )}
            {ep.outputSummary && (
              <p className="text-gray-600 dark:text-gray-400 text-xs truncate">
                <span className="font-medium text-gray-700 dark:text-gray-300">OUT:</span>{' '}
                {ep.outputSummary}
              </p>
            )}
            <p className="text-xs text-gray-400 tabular-nums">
              {ep.latencyMs} ms · {ep.agentId}
            </p>
          </div>

          {/* Outcome */}
          <span
            className={[
              'flex-shrink-0 text-xs px-2 py-0.5 rounded-full font-medium',
              OUTCOME_STYLES[ep.outcome],
            ].join(' ')}
          >
            {ep.outcome}
          </span>
        </li>
      ))}
    </ol>
  );
}
