/**
 * Bridge Status Panel Component
 * Displays bridge connection status in extension popup
 */

import React, { useState } from 'react';

type ConnectionState = 'connecting' | 'connected' | 'disconnected' | 'reconnecting' | 'error';

export interface BridgeStats {
  state: ConnectionState;
  clientId: string;
  reconnectAttempt: number;
  isHandshakeComplete: boolean;
  heartbeat?: {
    sequence: number;
    lastSent?: Date;
    lastReceived?: Date;
    missedCount: number;
    isAlive: boolean;
  };
  telemetry?: {
    queueLength: number;
    currentBatchSize: number;
    lastFlushLatency: number;
  };
}

export interface BridgeStatusPanelProps {
  stats: BridgeStats;
  onReconnect?: () => void;
  onDisconnect?: () => void;
}

const STATE_COLORS: Record<ConnectionState, string> = {
  connected: 'bg-green-500',
  connecting: 'bg-yellow-500',
  reconnecting: 'bg-orange-500',
  disconnected: 'bg-red-500',
  error: 'bg-red-700',
};

const STATE_LABELS: Record<ConnectionState, string> = {
  connected: 'Connected',
  connecting: 'Connecting...',
  reconnecting: 'Reconnecting...',
  disconnected: 'Disconnected',
  error: 'Error',
};

export const BridgeStatusPanel: React.FC<BridgeStatusPanelProps> = ({
  stats,
  onReconnect,
  onDisconnect,
}) => {
  const [expanded, setExpanded] = useState(false);

  const stateColor = STATE_COLORS[stats.state];
  const stateLabel = STATE_LABELS[stats.state];

  return (
    <div className="border border-gray-300 rounded-lg p-4 bg-white shadow-sm">
      {/* Header */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <div className={`w-3 h-3 rounded-full ${stateColor} animate-pulse`} />
          <span className="font-semibold text-gray-800">Bridge Status</span>
        </div>
        <button
          onClick={() => setExpanded(!expanded)}
          className="text-sm text-blue-600 hover:text-blue-800"
        >
          {expanded ? 'Hide Details' : 'Show Details'}
        </button>
      </div>

      {/* Status */}
      <div className="mb-3">
        <div className="text-sm text-gray-600">
          Status: <span className="font-medium text-gray-900">{stateLabel}</span>
        </div>
        {stats.reconnectAttempt > 0 && (
          <div className="text-xs text-orange-600 mt-1">
            Reconnect attempt {stats.reconnectAttempt}
          </div>
        )}
      </div>

      {/* Expanded Details */}
      {expanded && (
        <div className="space-y-3 border-t border-gray-200 pt-3">
          {/* Client Info */}
          <div>
            <div className="text-xs font-semibold text-gray-700 mb-1">Client Info</div>
            <div className="text-xs text-gray-600 space-y-1">
              <div>ID: {stats.clientId.slice(0, 8)}...</div>
              <div>Handshake: {stats.isHandshakeComplete ? '✓ Complete' : '✗ Pending'}</div>
            </div>
          </div>

          {/* Heartbeat */}
          {stats.heartbeat && (
            <div>
              <div className="text-xs font-semibold text-gray-700 mb-1">Heartbeat</div>
              <div className="text-xs text-gray-600 space-y-1">
                <div>Sequence: {stats.heartbeat.sequence}</div>
                <div>Missed: {stats.heartbeat.missedCount}</div>
                <div>
                  Status: {stats.heartbeat.isAlive ? '✓ Alive' : '✗ Dead'}
                </div>
                {stats.heartbeat.lastReceived && (
                  <div>
                    Last: {new Date(stats.heartbeat.lastReceived).toLocaleTimeString()}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Telemetry */}
          {stats.telemetry && (
            <div>
              <div className="text-xs font-semibold text-gray-700 mb-1">Telemetry</div>
              <div className="text-xs text-gray-600 space-y-1">
                <div>Queue: {stats.telemetry.queueLength} items</div>
                <div>Batch Size: {stats.telemetry.currentBatchSize}</div>
                <div>Latency: {stats.telemetry.lastFlushLatency}ms</div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Actions */}
      <div className="flex gap-2 mt-3 pt-3 border-t border-gray-200">
        {stats.state === 'disconnected' && onReconnect && (
          <button
            onClick={onReconnect}
            className="flex-1 px-3 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
          >
            Reconnect
          </button>
        )}
        {stats.state === 'connected' && onDisconnect && (
          <button
            onClick={onDisconnect}
            className="flex-1 px-3 py-1.5 text-sm bg-red-600 text-white rounded hover:bg-red-700 transition-colors"
          >
            Disconnect
          </button>
        )}
      </div>
    </div>
  );
};
