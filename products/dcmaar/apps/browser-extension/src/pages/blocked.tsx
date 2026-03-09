/**
 * @fileoverview Educational Block Page
 * 
 * A child-friendly block page that explains why a site is blocked
 * and provides educational context instead of just "Access Denied".
 * 
 * @module pages/blocked
 */

import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom/client';
import '../styles/globals.css';

interface BlockInfo {
    url: string;
    reason: string;
    category?: string;
    policyName?: string;
}

/**
 * Get educational message based on category
 */
function getEducationalMessage(category?: string): {
    title: string;
    message: string;
    icon: string;
    tips: string[];
} {
    switch (category?.toLowerCase()) {
        case 'social':
            return {
                title: 'Social Media Break',
                message: 'Social media can be fun, but too much can affect your focus and mood.',
                icon: '💬',
                tips: [
                    'Try talking to friends in person instead!',
                    'Social media will still be there later.',
                    'Consider journaling your thoughts instead.',
                ],
            };
        case 'gaming':
            return {
                title: 'Gaming Pause',
                message: 'Games are exciting, but balance is important for your well-being.',
                icon: '🎮',
                tips: [
                    'Take a break and stretch your body!',
                    'Try a physical activity or sport.',
                    'Read a book or learn something new.',
                ],
            };
        case 'streaming':
            return {
                title: 'Video Break',
                message: 'Watching videos is relaxing, but your eyes and brain need breaks too.',
                icon: '📺',
                tips: [
                    'Rest your eyes by looking at something far away.',
                    'Try listening to music or a podcast instead.',
                    'Go outside and enjoy nature!',
                ],
            };
        case 'adult':
            return {
                title: 'Protected Content',
                message: 'This content isn\'t appropriate right now. Your safety matters!',
                icon: '🛡️',
                tips: [
                    'If you have questions, talk to a trusted adult.',
                    'There are lots of great sites to explore instead!',
                    'Your parents can help you find age-appropriate content.',
                ],
            };
        case 'shopping':
            return {
                title: 'Shopping Pause',
                message: 'Online shopping can wait! Focus on what\'s important right now.',
                icon: '🛒',
                tips: [
                    'Make a wishlist to review later with your parents.',
                    'Think about whether you really need something.',
                    'Save your allowance for something special!',
                ],
            };
        default:
            return {
                title: 'Site Blocked',
                message: 'This website is currently blocked to help you stay focused.',
                icon: '🚫',
                tips: [
                    'Focus on your current task or homework.',
                    'Take a break and do something active.',
                    'Ask your parent if you need access for a good reason.',
                ],
            };
    }
}

/**
 * Parse URL parameters
 */
function getBlockInfo(): BlockInfo {
    const params = new URLSearchParams(window.location.search);
    return {
        url: params.get('url') || 'Unknown',
        reason: params.get('reason') || 'This site is blocked',
        category: params.get('category') || undefined,
        policyName: params.get('policy') || undefined,
    };
}

/**
 * BlockedPage Component
 */
function BlockedPage() {
    const [blockInfo, setBlockInfo] = useState<BlockInfo>({ url: '', reason: '' });
    const [showRequestForm, setShowRequestForm] = useState(false);
    const [requestReason, setRequestReason] = useState('');
    const [requestSent, setRequestSent] = useState(false);

    useEffect(() => {
        setBlockInfo(getBlockInfo());
    }, []);

    const educational = getEducationalMessage(blockInfo.category);

    const handleRequestAccess = () => {
        // Send message to background script
        chrome.runtime.sendMessage({
            type: 'REQUEST_ACCESS',
            payload: {
                url: blockInfo.url,
                reason: requestReason,
                timestamp: Date.now(),
            },
        }, () => {
            setRequestSent(true);
        });
    };

    const handleGoBack = () => {
        if (window.history.length > 1) {
            window.history.back();
        } else {
            window.close();
        }
    };

    return (
        <div className="min-h-screen bg-gradient-to-br from-blue-100 via-purple-100 to-pink-100 dark:from-gray-900 dark:via-gray-800 dark:to-gray-900 flex items-center justify-center p-4">
            <div className="max-w-lg w-full">
                {/* Main Card */}
                <div className="bg-white dark:bg-gray-800 rounded-3xl shadow-xl overflow-hidden">
                    {/* Header */}
                    <div className="bg-gradient-to-r from-blue-500 to-purple-600 p-8 text-center text-white">
                        <div className="text-6xl mb-4">{educational.icon}</div>
                        <h1 className="text-2xl font-bold mb-2">{educational.title}</h1>
                        <p className="text-blue-100">{educational.message}</p>
                    </div>

                    {/* Content */}
                    <div className="p-6 space-y-6">
                        {/* Blocked URL */}
                        <div className="bg-gray-50 dark:bg-gray-700 rounded-xl p-4">
                            <p className="text-sm text-gray-500 dark:text-gray-400 mb-1">Blocked Site</p>
                            <p className="font-mono text-sm text-gray-700 dark:text-gray-300 truncate">
                                {blockInfo.url}
                            </p>
                            {blockInfo.policyName && (
                                <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">
                                    Policy: {blockInfo.policyName}
                                </p>
                            )}
                        </div>

                        {/* Tips */}
                        <div>
                            <h3 className="font-semibold text-gray-900 dark:text-white mb-3 flex items-center gap-2">
                                <span>💡</span> What You Can Do Instead
                            </h3>
                            <ul className="space-y-2">
                                {educational.tips.map((tip, index) => (
                                    <li
                                        key={index}
                                        className="flex items-start gap-2 text-gray-600 dark:text-gray-400"
                                    >
                                        <span className="text-green-500 mt-0.5">✓</span>
                                        {tip}
                                    </li>
                                ))}
                            </ul>
                        </div>

                        {/* Request Access */}
                        {!showRequestForm && !requestSent && (
                            <div className="text-center pt-4 border-t border-gray-200 dark:border-gray-700">
                                <p className="text-sm text-gray-500 dark:text-gray-400 mb-3">
                                    Need this site for homework or a project?
                                </p>
                                <button
                                    onClick={() => setShowRequestForm(true)}
                                    className="text-blue-500 hover:text-blue-600 font-medium text-sm"
                                >
                                    Request Access from Parent →
                                </button>
                            </div>
                        )}

                        {showRequestForm && !requestSent && (
                            <div className="pt-4 border-t border-gray-200 dark:border-gray-700 space-y-3">
                                <textarea
                                    value={requestReason}
                                    onChange={(e) => setRequestReason(e.target.value)}
                                    placeholder="Explain why you need access to this site..."
                                    rows={3}
                                    className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-700 text-gray-900 dark:text-white text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
                                />
                                <div className="flex gap-2">
                                    <button
                                        onClick={() => setShowRequestForm(false)}
                                        className="flex-1 py-2 text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-xl text-sm font-medium transition-colors"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        onClick={handleRequestAccess}
                                        disabled={!requestReason.trim()}
                                        className="flex-1 py-2 bg-blue-500 hover:bg-blue-600 disabled:bg-gray-400 text-white rounded-xl text-sm font-medium transition-colors disabled:cursor-not-allowed"
                                    >
                                        Send Request
                                    </button>
                                </div>
                            </div>
                        )}

                        {requestSent && (
                            <div className="text-center pt-4 border-t border-gray-200 dark:border-gray-700">
                                <div className="text-4xl mb-2">✅</div>
                                <p className="text-green-600 dark:text-green-400 font-medium">
                                    Request sent to your parent!
                                </p>
                                <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                                    They'll review it and let you know.
                                </p>
                            </div>
                        )}
                    </div>

                    {/* Footer */}
                    <div className="px-6 pb-6">
                        <button
                            onClick={handleGoBack}
                            className="w-full py-3 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 rounded-xl font-medium transition-colors"
                        >
                            ← Go Back
                        </button>
                    </div>
                </div>

                {/* Guardian Branding */}
                <div className="text-center mt-6">
                    <p className="text-sm text-gray-500 dark:text-gray-400 flex items-center justify-center gap-2">
                        <span>🛡️</span>
                        Protected by Guardian
                    </p>
                </div>
            </div>
        </div>
    );
}

// Mount the app
const container = document.getElementById('root');
if (container) {
    const root = ReactDOM.createRoot(container);
    root.render(
        <React.StrictMode>
            <BlockedPage />
        </React.StrictMode>
    );
}

export default BlockedPage;
