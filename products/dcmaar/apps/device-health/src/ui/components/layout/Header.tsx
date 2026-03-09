import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { StatusBadge } from '@ghatana/dcmaar-shared-ui-tailwind';
import { 
    ArrowPathIcon,
    CheckCircleIcon,
    PauseCircleIcon,
    BoltIcon,
    Cog6ToothIcon
} from '@heroicons/react/20/solid';
import { HeaderProps } from './types';
import { useConnectionStatus } from '../../hooks/useConnectionStatus';
import { useAnalyticsContext } from '../../context/AnalyticsContext';
import { TimeRangeSelector } from '../filters/TimeRangeSelector';

type CaptureState = 'active' | 'paused';

const MenuIcon = () => (
    <svg
        viewBox="0 0 24 24"
        className="h-5 w-5"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.8}
        strokeLinecap="round"
    >
        <path d="M4 7h16" />
        <path d="M4 12h16" />
        <path d="M4 17h16" />
    </svg>
);

const SettingsIcon = () => (
    <svg
        viewBox="0 0 24 24"
        className="h-5 w-5"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
    >
        <path d="m19.4 15-.9 1.6a1.5 1.5 0 0 1-1.3.7h-1.1a1.5 1.5 0 0 0-1.4.9l-.2.6a1.5 1.5 0 0 1-2.8 0l-.2-.6a1.5 1.5 0 0 0-1.4-.9H8.8a1.5 1.5 0 0 1-1.3-.7L6.6 15a1.5 1.5 0 0 1 0-1.4l.5-.9a1.5 1.5 0 0 0 0-1.4l-.5-.9a1.5 1.5 0 0 1 0-1.4l.9-1.6a1.5 1.5 0 0 1 1.3-.7h1.1a1.5 1.5 0 0 0 1.4-.9l.2-.6a1.5 1.5 0 0 1 2.8 0l.2.6a1.5 1.5 0 0 0 1.4.9h1.1a1.5 1.5 0 0 1 1.3.7l.9 1.6a1.5 1.5 0 0 1 0 1.4l-.5.9a1.5 1.5 0 0 0 0 1.4l.5.9a1.5 1.5 0 0 1 0 1.4Z" />
        <circle cx="12" cy="12" r="2.5" />
    </svg>
);

const IconButton: React.FC<React.ButtonHTMLAttributes<HTMLButtonElement>> = ({ children, className = '', ...props }) => (
    <button
        className={`inline-flex h-8 w-8 items-center justify-center rounded-lg border border-gray-200 bg-white text-gray-700 shadow-sm transition hover:border-gray-300 hover:bg-gray-50 hover:text-gray-900 active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-1 ${className}`}
        {...props}
    >
        {children}
    </button>
);

export const Header: React.FC<HeaderProps> = ({ onMenuClick, onPageChange }) => {
    const location = useLocation();
    const [captureState, setCaptureState] = useState<CaptureState>('active');
    const [isMounted, setIsMounted] = useState(false);
    const [showRefreshMenu, setShowRefreshMenu] = useState(false);
    const { data: connectionStatus, refetch: refetchConnection } = useConnectionStatus();
    const { 
        timeRange, 
        setTimeRange, 
        autoRefresh, 
        setAutoRefresh, 
        refreshInterval, 
        setRefreshInterval,
        triggerRefresh 
    } = useAnalyticsContext();

    // Check if current page should show analytics controls
    const showAnalyticsControls = !['/settings', '/help', '/accessibility'].includes(location.pathname);

    // Refresh interval options
    const REFRESH_INTERVALS = [
        { label: 'Off', value: 0 },
        { label: '5 seconds', value: 5000 },
        { label: '10 seconds', value: 10000 },
        { label: '30 seconds', value: 30000 },
        { label: '1 minute', value: 60000 },
    ];

    // Close refresh menu when clicking outside
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            const target = event.target as HTMLElement;
            if (showRefreshMenu && !target.closest('.refresh-menu-container')) {
                setShowRefreshMenu(false);
            }
        };

        if (showRefreshMenu) {
            document.addEventListener('mousedown', handleClickOutside);
            return () => document.removeEventListener('mousedown', handleClickOutside);
        }
    }, [showRefreshMenu]);

    // Load saved capture state on component mount
    useEffect(() => {
        const loadCaptureState = async () => {
            try {
                const result = await chrome.storage.local.get('captureState');
                if (result.captureState) {
                    setCaptureState(result.captureState);
                } else {
                    setCaptureState('active'); // Default to active
                }
                setIsMounted(true);
            } catch (error) {
                console.error('Failed to load capture state:', error);
                setCaptureState('active');
                setIsMounted(true);
            }
        };
        loadCaptureState();
    }, []);

    // Toggle capture state
    const toggleCaptureState = async () => {
        const newState: CaptureState = captureState === 'active' ? 'paused' : 'active';
        setCaptureState(newState);
        
        try {
            await chrome.storage.local.set({ captureState: newState });
            // Send message to background to toggle event capture
            await chrome.runtime.sendMessage({ 
                type: 'TOGGLE_CAPTURE', 
                payload: { active: newState === 'active' } 
            });
        } catch (error) {
            console.error('Failed to toggle capture state:', error);
        }
    };

    // Handle reconnect action
    const handleReconnect = async () => {
        try {
            await chrome.runtime.sendMessage({ type: 'RECONNECT' });
            // Wait a moment then refetch connection status
            setTimeout(() => {
                refetchConnection();
            }, 1000);
        } catch (error) {
            console.error('Failed to reconnect:', error);
        }
    };

    // Determine the overall status
    const isOnline = connectionStatus?.isConnected ?? false;
    const isActive = captureState === 'active';
    
    // Determine status badge state
    const badgeStatus = isOnline ? 'online' : 'offline';
    const isPulsing = isOnline && isActive;

    // Determine status text and colors
    const getStatusText = () => {
        if (!isOnline) return 'Offline';
        return isActive ? 'Active' : 'Paused';
    };

    const getStatusColor = () => {
        if (!isOnline) return 'text-red-700';
        return isActive ? 'text-emerald-700' : 'text-gray-700';
    };

    const getStatusDescription = () => {
        if (!isOnline) return 'Not connected to background service';
        return isActive ? 'Capturing events' : 'Event capture paused';
    };

    const getBorderColor = () => {
        if (!isOnline) return 'border-red-200';
        return isActive ? 'border-emerald-200' : 'border-gray-200';
    };

    return (
        <header className="sticky top-0 z-20 border-b border-gray-200 bg-white/98 backdrop-blur-sm">
            <div className="flex w-full items-center justify-between gap-3 px-4 py-3">
                <div className="flex items-center gap-3">
                    <IconButton onClick={onMenuClick} aria-label="Toggle navigation">
                        <MenuIcon />
                    </IconButton>
                    <div>
                        <h1 className="text-base font-semibold text-gray-900">
                            DCMAAR Extension
                        </h1>
                        <p className="text-xs text-gray-600">
                            Monitor and manage event capture
                        </p>
                    </div>
                </div>

                <div className="flex items-center gap-3 flex-1 justify-end">
                    {/* Time Range Selector - only on analytics pages */}
                    {showAnalyticsControls && (
                        <div className="hidden lg:block">
                            <TimeRangeSelector
                                value={timeRange}
                                onChange={setTimeRange}
                            />
                        </div>
                    )}

                    {/* Status display and toggle */}
                    {!isMounted ? (
                        <div className="h-8 w-32 bg-gray-200 rounded-lg animate-pulse"></div>
                    ) : (
                        <button
                            onClick={toggleCaptureState}
                            disabled={!isOnline}
                            className={`flex items-center gap-2 rounded-lg border ${getBorderColor()} bg-white px-3 py-1.5 shadow-sm transition-all ${
                                isOnline 
                                    ? 'cursor-pointer hover:bg-gray-50 hover:shadow-md active:scale-95' 
                                    : 'cursor-not-allowed opacity-60'
                            }`}
                            title={isOnline ? (isActive ? 'Click to pause event capture' : 'Click to resume event capture') : 'Extension is offline'}
                            aria-label={isOnline ? (isActive ? 'Pause event capture' : 'Resume event capture') : 'Extension offline'}
                        >
                            <StatusBadge 
                                status={badgeStatus} 
                                pulse={isPulsing}
                                size="sm"
                                className="flex-shrink-0"
                            />
                            <div className="flex items-center gap-2 min-w-0">
                                {isOnline && (
                                    isActive ? (
                                        <CheckCircleIcon className="h-4 w-4 text-emerald-600 flex-shrink-0" />
                                    ) : (
                                        <PauseCircleIcon className="h-4 w-4 text-gray-600 flex-shrink-0" />
                                    )
                                )}
                                <div className="flex flex-col min-w-0">
                                    <span className={`text-xs font-medium ${getStatusColor()}`}>
                                        {getStatusText()}
                                    </span>
                                    <span className="text-[10px] text-gray-500 truncate">
                                        {getStatusDescription()}
                                    </span>
                                </div>
                            </div>
                        </button>
                    )}

                    {/* Reconnect button - only show when offline */}
                    {!isOnline && (
                        <button
                            onClick={handleReconnect}
                            className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-medium text-white shadow-sm transition hover:bg-blue-700 active:scale-95"
                            aria-label="Reconnect to background service"
                            title="Reconnect to background service"
                        >
                            <BoltIcon className="h-4 w-4" />
                            Reconnect
                        </button>
                    )}

                    {/* Refresh button with dropdown - only show when online and on analytics pages */}
                    {isOnline && showAnalyticsControls && (
                        <div className="relative refresh-menu-container">
                            <IconButton 
                                aria-label="Refresh and auto-refresh settings"
                                onClick={() => setShowRefreshMenu(!showRefreshMenu)}
                                className={`hover:bg-blue-50 hover:text-blue-600 ${autoRefresh && refreshInterval > 0 ? 'text-blue-600' : ''}`}
                            >
                                <ArrowPathIcon className={`h-5 w-5 ${autoRefresh && refreshInterval > 0 ? 'animate-spin-slow' : ''}`} />
                            </IconButton>
                            
                            {/* Refresh dropdown menu */}
                            {showRefreshMenu && (
                                <div className="absolute right-0 mt-2 w-48 bg-white border border-gray-200 rounded-lg shadow-lg z-50">
                                    <div className="p-2">
                                        <button
                                            onClick={() => {
                                                triggerRefresh();
                                                setShowRefreshMenu(false);
                                            }}
                                            className="w-full text-left px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded"
                                        >
                                            Refresh Now
                                        </button>
                                        <div className="border-t border-gray-200 my-2"></div>
                                        <div className="px-3 py-1 text-xs font-medium text-gray-500">
                                            Auto Refresh
                                        </div>
                                        {REFRESH_INTERVALS.map((interval) => (
                                            <button
                                                key={interval.value}
                                                onClick={() => {
                                                    if (interval.value === 0) {
                                                        setAutoRefresh(false);
                                                    } else {
                                                        setAutoRefresh(true);
                                                        setRefreshInterval(interval.value);
                                                    }
                                                }}
                                                className={`w-full text-left px-3 py-2 text-sm rounded ${
                                                    (interval.value === 0 && !autoRefresh) || 
                                                    (interval.value === refreshInterval && autoRefresh)
                                                        ? 'bg-blue-50 text-blue-700 font-medium'
                                                        : 'text-gray-700 hover:bg-gray-100'
                                                }`}
                                            >
                                                {interval.label}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    )}

                    {/* Refresh button for non-analytics pages */}
                    {isOnline && !showAnalyticsControls && (
                        <IconButton 
                            aria-label="Refresh extension"
                            onClick={() => window.location.reload()}
                            className="hover:bg-blue-50 hover:text-blue-600"
                        >
                            <ArrowPathIcon className="h-5 w-5" />
                        </IconButton>
                    )}

                    {/* Settings button */}
                    <IconButton 
                        aria-label="Open settings"
                        onClick={() => onPageChange('/settings')}
                        className="hover:bg-blue-50 hover:text-blue-600"
                    >
                        <SettingsIcon />
                    </IconButton>
                </div>
            </div>
        </header>
    );
};
