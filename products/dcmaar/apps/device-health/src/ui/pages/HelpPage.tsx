import React, { useState } from 'react';
import { Card, Badge } from '@ghatana/dcmaar-shared-ui-tailwind';

interface FAQItem {
    question: string;
    answer: string;
}

interface DocSection {
    id: string;
    title: string;
    content: string;
}

export const HelpPage: React.FC = () => {
    const [expandedFAQ, setExpandedFAQ] = useState<number | null>(null);

    const docSections: DocSection[] = [
        {
            id: 'getting-started',
            title: 'Getting Started',
            content: `
        Welcome to DCMAAR! Follow these steps to get started:
        
        1. Install the extension from your browser's addon marketplace
        2. Click the DCMAAR icon in your toolbar
        3. Navigate to Configuration to connect to your data source
        4. Start monitoring your application's connection and events
        
        That's it! Your data will begin streaming automatically.
      `,
        },
        {
            id: 'configuration',
            title: 'Configuration Guide',
            content: `
        DCMAAR supports multiple connection types:
        
        • IPC Socket: Best for local connections (/tmp/dcmaar.sock)
        • TCP: For network connections (localhost:8000)
        • HTTP: For web-based connections (http://localhost:3000)
        
        Choose the appropriate type for your setup and enter the address.
        Use the "Test Connection" button to verify connectivity.
      `,
        },
        {
            id: 'monitoring',
            title: 'Monitoring & Metrics',
            content: `
        The Metrics page provides real-time insights:
        
        • Total Events: Count of all events processed
        • Event Types: Breakdown by event category
        • Performance: Latency statistics (avg, p95, p99)
        • Time Ranges: View metrics for different periods (1h, 24h, 7d, 30d)
        
        Use the time range selector to analyze trends over time.
      `,
        },
        {
            id: 'troubleshooting',
            title: 'Troubleshooting',
            content: `
        Common issues and solutions:
        
        Connection Failed:
        • Verify the source address is correct
        • Check that the service is running
        • Try Test Connection to diagnose
        
        No Events Appearing:
        • Ensure data collection is enabled in Settings
        • Check Configuration page for proper setup
        • View browser console for detailed logs (enable Verbose Logging)
        
        Slow Performance:
        • Reduce the polling frequency in Settings
        • Enable compression for storage
        • Clear old events periodically
      `,
        },
    ];

    const faqs: FAQItem[] = [
        {
            question: 'How often are events collected?',
            answer: 'Events are collected in real-time as they occur. Recent activity is updated every 5 seconds, and metrics are refreshed every 30 seconds.',
        },
        {
            question: 'Where are my events stored?',
            answer: 'Events are stored in your browser\'s local storage or IndexedDB depending on your configuration. They are never sent to external servers unless explicitly configured.',
        },
        {
            question: 'Can I export my data?',
            answer: 'You can export event data from the Dashboard page. Events are stored in your browser\'s storage and can be accessed programmatically.',
        },
        {
            question: 'How much storage does DCMAAR use?',
            answer: 'By default, DCMAAR stores up to 1MB of events. You can adjust this limit in the Configuration page. Older events are automatically pruned when the limit is reached.',
        },
        {
            question: 'Is my data private?',
            answer: 'Yes. DCMAAR runs entirely in your browser. No data is sent to external services unless you explicitly configure a remote sink.',
        },
        {
            question: 'How do I report bugs?',
            answer: 'Please report bugs on our GitHub repository or contact support. Include your browser version, DCMAAR version, and steps to reproduce.',
        },
        {
            question: 'Can I use DCMAAR offline?',
            answer: 'Yes, DCMAAR can operate in offline mode. It will store events locally and sync when connectivity is restored.',
        },
        {
            question: 'How do I uninstall DCMAAR?',
            answer: 'Use your browser\'s extension management page to remove DCMAAR. All stored data will be deleted (depending on your browser\'s privacy settings).',
        },
    ];

    return (
        <div className="p-6 space-y-6 max-w-4xl">
            {/* Documentation Sections */}
            <div className="space-y-3">
                <h2 className="text-lg font-semibold text-gray-900">Documentation</h2>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    {docSections.map((section) => (
                        <Card
                            key={section.id}
                            className="transition-shadow hover:shadow-md"
                        >
                            <div className="flex items-start gap-3">
                                <span className="inline-flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-xl bg-primary-50 text-sm font-semibold text-primary-600">
                                    {section.title
                                        .split(' ')
                                        .map((word) => word[0])
                                        .join('')
                                        .slice(0, 2)
                                        .toUpperCase()}
                                </span>
                                <div className="flex-1">
                                    <h3 className="font-semibold text-gray-900">{section.title}</h3>
                                    <p className="mt-2 text-xs text-gray-600 line-clamp-3 whitespace-pre-line">
                                        {section.content.split('\n')[0]}
                                    </p>
                                </div>
                            </div>
                        </Card>
                    ))}
                </div>
            </div>

            {/* Quick Start */}
            <Card title="Quick Start" description="Get up and running in 3 steps">
                <div className="space-y-4">
                    {[
                        { step: 1, title: 'Install', desc: 'Add the DCMAAR extension to your browser' },
                        { step: 2, title: 'Configure', desc: 'Set up your data source in the Configuration page' },
                        { step: 3, title: 'Monitor', desc: 'View your data in the Dashboard and Metrics pages' },
                    ].map((item) => (
                        <div key={item.step} className="flex gap-4">
                            <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-primary-100 font-semibold text-primary-600">
                                {item.step}
                            </div>
                            <div>
                                <p className="font-semibold text-gray-900">{item.title}</p>
                                <p className="text-sm text-gray-600 mt-1">{item.desc}</p>
                            </div>
                        </div>
                    ))}
                </div>
            </Card>

            {/* FAQ Section */}
            <Card title="Frequently Asked Questions">
                <div className="space-y-2">
                    {faqs.map((faq, idx) => (
                        <div key={idx} className="border border-gray-200 rounded-lg">
                            <button
                                onClick={() => setExpandedFAQ(expandedFAQ === idx ? null : idx)}
                                className="w-full px-4 py-3 flex items-center justify-between hover:bg-gray-50 transition-colors"
                            >
                                <span className="font-medium text-gray-900 text-left">{faq.question}</span>
                                <span className="text-gray-500">{expandedFAQ === idx ? '−' : '+'}</span>
                            </button>
                            {expandedFAQ === idx && (
                                <div className="px-4 py-3 bg-gray-50 border-t border-gray-200 text-sm text-gray-700">
                                    {faq.answer}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            </Card>

            {/* Resources */}
            <Card title="Resources & Support" description="Additional help and information">
                <div className="space-y-3">
                    {[
                        { title: 'GitHub Repository', url: 'https://github.com/yappc/dcmaar' },
                        { title: 'API Documentation', url: '#' },
                        { title: 'Community Forum', url: '#' },
                        { title: 'Report an Issue', url: '#' },
                        { title: 'Request a Feature', url: '#' },
                    ].map((resource) => (
                        <a
                            key={resource.title}
                            href={resource.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex items-center justify-between rounded-lg border border-gray-200 p-3 text-sm text-gray-700 transition-colors hover:border-primary-200 hover:bg-primary-50"
                        >
                            <span>{resource.title}</span>
                            <span className="flex items-center gap-1 text-primary-600">
                                View
                                <svg
                                    viewBox="0 0 24 24"
                                    className="h-4 w-4"
                                    fill="none"
                                    stroke="currentColor"
                                    strokeWidth={1.6}
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                >
                                    <path d="M7 17 17 7" />
                                    <path d="M7 7h10v10" />
                                </svg>
                            </span>
                        </a>
                    ))}
                </div>
            </Card>

            {/* Version & Build Info */}
            <Card title="About DCMAAR">
                <div className="space-y-3 text-sm">
                    <div className="flex justify-between">
                        <span className="text-gray-600">Version</span>
                        <Badge variant="info" label="1.0.0" />
                    </div>
                    <div className="flex justify-between">
                        <span className="text-gray-600">Status</span>
                        <Badge variant="success" label="Active" />
                    </div>
                    <div className="flex justify-between">
                        <span className="text-gray-600">Build</span>
                        <span className="font-mono text-gray-700">dcmaar-2024-01</span>
                    </div>
                    <div className="pt-3 border-t border-gray-200">
                        <p className="text-xs text-gray-600">
                            DCMAAR is a powerful extension for monitoring and analyzing your application's data flow in real-time.
                        </p>
                    </div>
                </div>
            </Card>
        </div>
    );
};
