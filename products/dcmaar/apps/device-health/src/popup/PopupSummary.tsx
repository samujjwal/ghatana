import React, { useState, useEffect } from 'react';
import browser from 'webextension-polyfill';
import { useConnectionStatus } from '../ui/hooks/useConnectionStatus';

const StatusIcon = ({ isConnected, isActive }: { isConnected: boolean; isActive: boolean }) => (
  <span className={`flex h-10 w-10 items-center justify-center rounded-full shadow-inner ${!isConnected ? 'bg-red-50 text-red-600' :
      isActive ? 'bg-emerald-50 text-emerald-600' :
        'bg-gray-50 text-gray-600'
    }`}>
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-6 w-6"
    >
      {!isConnected ? (
        <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
      ) : isActive ? (
        <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
      ) : (
        <circle cx="12" cy="12" r="10" />
      )}
    </svg>
  </span>
);

export const PopupSummary: React.FC = () => {
  const { data: connectionStatus, isLoading } = useConnectionStatus();
  const [isActive, setIsActive] = useState(true);
  const [captureStats, setCaptureStats] = useState({ eventsToday: 0, lastEvent: 'Never' });

  // Load capture state from storage
  useEffect(() => {
    const loadState = async () => {
      try {
        const result = await browser.storage.local.get(['captureActive', 'captureStats']);
        if (typeof result.captureActive === 'boolean') {
          setIsActive(result.captureActive);
        }
        if (result.captureStats && typeof result.captureStats === 'object') {
          setCaptureStats(result.captureStats as { eventsToday: number; lastEvent: string });
        }
      } catch (error) {
        console.error('Failed to load capture state:', error);
      }
    };
    void loadState();
  }, []);

  const toggleCapture = async () => {
    const newState = !isActive;
    setIsActive(newState);

    try {
      // Save state to storage
      await browser.storage.local.set({ captureActive: newState });

      // Notify background script
      await browser.runtime.sendMessage({
        type: 'TOGGLE_CAPTURE',
        payload: { active: newState },
      });
    } catch (error) {
      console.error('Failed to toggle capture:', error);
      // Revert on error
      setIsActive(!newState);
    }
  };

  const openDashboard = async () => {
    try {
      // Use browser.runtime.openOptionsPage which uses the manifest's options_page
      if (browser.runtime.openOptionsPage) {
        await browser.runtime.openOptionsPage();
      } else {
        // Fallback: manually open the options page
        const url = browser.runtime.getURL('src/options/options.html');
        await browser.tabs.create({ url });
      }
      window.close();
    } catch (error) {
      console.error('Failed to open dashboard:', error);
    }
  };

  const isConnected = connectionStatus?.isConnected ?? false;

  return (
    <div className="flex flex-col gap-4 p-1">
      <header className="flex items-center gap-3">
        <StatusIcon isConnected={isConnected} isActive={isActive} />
        <div className="flex-1">
          <h1 className="text-base font-semibold text-slate-900">DCMAAR Extension</h1>
          <p className="text-xs text-slate-500">
            {isLoading ? 'Checking...' :
              !isConnected ? 'Disconnected' :
                isActive ? 'Actively Capturing' : 'Paused'}
          </p>
        </div>
      </header>

      {/* Status & Stats */}
      <section className="grid gap-2 grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white px-3 py-2.5 shadow-sm">
          <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-500">Connection</p>
          <div className="mt-1 flex items-center gap-1.5">
            <div className={`h-2 w-2 rounded-full ${isConnected ? 'bg-emerald-500 animate-pulse' : 'bg-red-500'
              }`} />
            <p className="text-sm font-semibold text-slate-900">
              {isConnected ? 'Online' : 'Offline'}
            </p>
          </div>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white px-3 py-2.5 shadow-sm">
          <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-500">Events Today</p>
          <p className="mt-1 text-sm font-semibold text-slate-900">
            {captureStats.eventsToday.toLocaleString()}
          </p>
        </div>
      </section>

      {/* Capture Toggle */}
      <section className="rounded-lg border border-slate-200 bg-white px-3 py-3 shadow-sm">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-semibold text-slate-700">Event Capture</p>
            <p className="text-xs text-slate-500 mt-0.5">
              {isActive ? 'Capturing events from all pages' : 'Capture paused'}
            </p>
          </div>
          <button
            onClick={toggleCapture}
            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 ${isActive ? 'bg-blue-600' : 'bg-gray-200'
              }`}
          >
            <span
              className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${isActive ? 'translate-x-6' : 'translate-x-1'
                }`}
            />
          </button>
        </div>
      </section>

      {/* Last Event Info */}
      <section className="rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-3 py-2.5 text-xs text-slate-600 dark:text-slate-400">
        <p className="font-semibold text-slate-700 dark:text-slate-300">Last Event</p>
        <p className="mt-1">
          {captureStats.lastEvent}
        </p>
      </section>

      {/* Action Buttons */}
      <div className="flex flex-col gap-2">
        <button
          type="button"
          className="flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700 active:scale-[0.98]"
          onClick={openDashboard}
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <rect x="3" y="3" width="7" height="7" />
            <rect x="14" y="3" width="7" height="7" />
            <rect x="14" y="14" width="7" height="7" />
            <rect x="3" y="14" width="7" height="7" />
          </svg>
          Open Dashboard
        </button>
      </div>
    </div>
  );
};
