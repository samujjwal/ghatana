/**
 * @fileoverview Guidance context for surfacing action playbooks globally.
 */

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { createPortal } from 'react-dom';
import { ActionGuidancePanel } from '../components/guidance/ActionGuidancePanel';
import { getPlaybook } from '../../analytics/guidance/ActionPlaybooks';

type Severity = 'warning' | 'critical';

export interface OpenGuidanceOptions {
  metric: string;
  severity?: Severity;
  currentValue?: number;
  source?: 'alert' | 'metric-card' | 'insight' | 'other';
}

interface GuidanceState {
  isOpen: boolean;
  metric?: string;
  severity: Severity;
  currentValue?: number;
  source?: OpenGuidanceOptions['source'];
}

interface GuidanceContextValue {
  openGuidance: (options: OpenGuidanceOptions) => void;
  closeGuidance: () => void;
  state: GuidanceState;
}

const DEFAULT_STATE: GuidanceState = {
  isOpen: false,
  severity: 'critical',
};

const GuidanceContext = createContext<GuidanceContextValue>({
  openGuidance: () => undefined,
  closeGuidance: () => undefined,
  state: DEFAULT_STATE,
});

const GuidanceOverlay: React.FC<{
  state: GuidanceState;
  onClose: () => void;
}> = ({ state, onClose }) => {
  const { isOpen, metric, severity, currentValue } = state;

  const heading = useMemo(() => {
    if (!metric) return 'Guided Remediation';
    const playbook = getPlaybook(metric, severity);
    return playbook ? `${playbook.metric.toUpperCase()} Fix Guide` : `Guidance for ${metric}`;
  }, [metric, severity]);

  if (!isOpen || typeof document === 'undefined') {
    return null;
  }

  return createPortal(
    <div className="fixed inset-0 z-[1200] flex items-start justify-center bg-slate-900/50 p-4 sm:p-8">
      <div className="relative mt-6 w-full max-w-3xl overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">{heading}</h2>
            {metric && (
              <p className="text-xs uppercase tracking-wide text-slate-400">
                Metric: {metric.toUpperCase()} • Severity: {severity}
              </p>
            )}
          </div>
          <button
            onClick={onClose}
            className="rounded-md border border-slate-200 px-3 py-1 text-sm text-slate-600 hover:bg-slate-100"
          >
            Close
          </button>
        </div>
        <div className="max-h-[80vh] overflow-y-auto px-6 py-6">
          {metric ? (
            <ActionGuidancePanel
              metric={metric}
              severity={severity}
              currentValue={currentValue}
              onCompleteAction={() => {
                // No-op hook points for future analytics
              }}
            />
          ) : (
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-6 text-sm text-slate-600">
              Select a metric to view the playbook guidance.
            </div>
          )}
        </div>
      </div>
    </div>,
    document.body
  );
};

export const GuidanceProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [state, setState] = useState<GuidanceState>(DEFAULT_STATE);

  const openGuidance = useCallback((options: OpenGuidanceOptions) => {
    setState({
      isOpen: true,
      metric: options.metric,
      severity: options.severity ?? 'critical',
      currentValue: options.currentValue,
      source: options.source,
    });
  }, []);

  const closeGuidance = useCallback(() => {
    setState((prev) => ({
      ...prev,
      isOpen: false,
    }));
  }, []);

  useEffect(() => {
    if (typeof document === 'undefined') {
      return;
    }
    if (state.isOpen) {
      const { style } = document.body;
      const previousOverflow = style.overflow;
      style.overflow = 'hidden';
      return () => {
        style.overflow = previousOverflow;
      };
    }
    return;
  }, [state.isOpen]);

  const value = useMemo<GuidanceContextValue>(
    () => ({
      openGuidance,
      closeGuidance,
      state,
    }),
    [closeGuidance, openGuidance, state]
  );

  return (
    <GuidanceContext.Provider value={value}>
      {children}
      <GuidanceOverlay state={state} onClose={closeGuidance} />
    </GuidanceContext.Provider>
  );
};

export const useGuidance = (): GuidanceContextValue => {
  return useContext(GuidanceContext);
};
