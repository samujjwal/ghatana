import { memo, useState } from 'react';

/**
 * Help center with documentation, FAQs, and support.
 *
 * <p><b>Purpose</b><br>
 * Centralized help and documentation portal providing FAQs, guides,
 * troubleshooting, and support contact information.
 *
 * <p><b>Features</b><br>
 * - FAQ sections with search
 * - Getting started guides
 * - Troubleshooting guides
 * - API documentation links
 * - Contact support
 * - Knowledge base
 * - Quick tips
 *
 * <p><b>Props</b><br>
 * None - component manages its own state
 *
 * @doc.type page
 * @doc.purpose Help center and documentation
 * @doc.layer product
 * @doc.pattern Help Portal
 */

interface FAQItem {
    id: string;
    question: string;
    answer: string;
    category: string;
    views: number;
}

interface Guide {
    id: string;
    title: string;
    description: string;
    icon: string;
    readTime: number; // minutes
}

// Help content - static FAQ and guide data
// TODO: Integrate with API in production (CMS or help service)
const faqs: FAQItem[] = [
    {
        id: 'faq-1',
        category: 'Getting Started',
        question: 'How do I deploy a model to production?',
        answer:
            'Click on the model in the catalog, click "Deploy", select the target environment (staging/production), and confirm. The system will validate the model and deploy it.',
        views: 1243,
    },
    {
        id: 'faq-2',
        category: 'Getting Started',
        question: 'What is the expected latency for predictions?',
        answer:
            'Typical latency is 10-50ms depending on the model. You can view per-model latency metrics in the Model Details page. Latency above 100ms triggers alerts.',
        views: 987,
    },
    {
        id: 'faq-3',
        category: 'Troubleshooting',
        question: 'Model training failed with "Out of memory" error',
        answer:
            'This occurs when training data is too large. Solutions: (1) Reduce batch size in training config, (2) Use data sampling, or (3) Request more resources from the ops team.',
        views: 654,
    },
    {
        id: 'faq-4',
        category: 'Troubleshooting',
        question: 'How do I fix high latency in production?',
        answer:
            'Check the Performance tab in Analytics. Common causes: (1) Insufficient replicas (add more), (2) Slow upstream dependencies (check logs), (3) Model complexity (consider simpler model).',
        views: 876,
    },
    {
        id: 'faq-5',
        category: 'API',
        question: 'How do I authenticate API requests?',
        answer: 'Use Bearer token authentication in the Authorization header: Authorization: Bearer YOUR_API_KEY. Get keys from Settings > API Keys.',
        views: 1456,
    },
];

const guides: Guide[] = [
    {
        id: 'guide-1',
        title: 'Getting Started with Model Deployment',
        description: 'Learn how to deploy your first model to production',
        icon: '🚀',
        readTime: 5,
    },
    {
        id: 'guide-2',
        title: 'Understanding Model Metrics',
        description: 'Deep dive into accuracy, precision, recall, and F1 score',
        icon: '📊',
        readTime: 8,
    },
    {
        id: 'guide-3',
        title: 'Setting Up Alerts & Notifications',
        description: 'Configure automated alerts for SLA breaches and anomalies',
        icon: '🔔',
        readTime: 4,
    },
    {
        id: 'guide-4',
        title: 'API Integration Guide',
        description: 'Integrate the prediction API into your application',
        icon: '🔗',
        readTime: 10,
    },
];

export const HelpCenter = memo(function HelpCenter() {
    // GIVEN: User seeking help
    // WHEN: Viewing help center
    // THEN: Display FAQs, guides, and support options

    const [_searchQuery, setSearchQuery] = useState('');
    const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
    const [expandedFAQ, setExpandedFAQ] = useState<string | null>(null);

    const categories = ['All', 'Getting Started', 'Troubleshooting', 'API', 'Settings'];
    const filteredFAQs =
        selectedCategory === null || selectedCategory === 'All'
            ? faqs
            : faqs.filter((faq: FAQItem) => faq.category === selectedCategory);

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 p-4">
            <div className="max-w-6xl mx-auto">
                {/* Hero Section */}
                <div className="bg-gradient-to-r from-blue-600 to-purple-600 dark:from-blue-900 dark:to-purple-900 rounded-lg p-12 mb-8 text-center">
                    <h1 className="text-4xl font-bold text-white mb-4">How can we help?</h1>
                    <div className="relative max-w-2xl mx-auto">
                        <input
                            type="text"
                            placeholder="Search documentation, FAQs, guides..."
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full px-6 py-3 bg-white dark:bg-neutral-800 rounded-lg text-slate-900 dark:text-neutral-100 placeholder-slate-500 dark:placeholder-slate-400 border border-transparent focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                        />
                        <span className="absolute right-4 top-3 text-slate-400">🔍</span>
                    </div>
                </div>

                {/* Quick Links */}
                <div className="grid grid-cols-4 gap-4 mb-12">
                    {[
                        { icon: '📚', label: 'Documentation', desc: 'Complete API docs' },
                        { icon: '🎯', label: 'Tutorials', desc: 'Step-by-step guides' },
                        { icon: '💬', label: 'Community', desc: 'Forums & chat' },
                        { icon: '📞', label: 'Support', desc: 'Contact us' },
                    ].map((link) => (
                        <button
                            key={link.label}
                            className="bg-white dark:bg-neutral-800 hover:bg-slate-50 dark:hover:bg-slate-700 border border-slate-200 dark:border-neutral-600 rounded-lg p-4 transition-colors text-left shadow-sm"
                        >
                            <div className="text-3xl mb-2">{link.icon}</div>
                            <div className="font-semibold text-slate-900 dark:text-neutral-100">{link.label}</div>
                            <div className="text-xs text-slate-600 dark:text-neutral-400">{link.desc}</div>
                        </button>
                    ))}
                </div>

                {/* Main Content Grid */}
                <div className="grid grid-cols-3 gap-6">
                    {/* Guides */}
                    <div className="col-span-2">
                        <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-6">📖 Guides & Tutorials</h2>
                        <div className="space-y-3 mb-12">
                            {guides.map((guide: Guide) => (
                                <button
                                    key={guide.id}
                                    className="w-full bg-white dark:bg-neutral-800 hover:bg-slate-50 dark:hover:bg-slate-700 border border-slate-200 dark:border-neutral-600 rounded-lg p-4 transition-colors text-left group shadow-sm"
                                >
                                    <div className="flex items-start justify-between">
                                        <div className="flex items-start gap-4 flex-1">
                                            <span className="text-3xl flex-shrink-0">{guide.icon}</span>
                                            <div>
                                                <h3 className="font-semibold text-slate-900 dark:text-neutral-100 group-hover:text-blue-600 dark:group-hover:text-blue-400">{guide.title}</h3>
                                                <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">{guide.description}</p>
                                            </div>
                                        </div>
                                        <span className="text-xs text-slate-500 flex-shrink-0">⏱ {guide.readTime}m read</span>
                                    </div>
                                </button>
                            ))}
                        </div>

                        {/* FAQs */}
                        <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-6">❓ Frequently Asked Questions</h2>

                        {/* Category Filter */}
                        <div className="flex gap-2 mb-6 flex-wrap">
                            {categories.map((cat) => (
                                <button
                                    key={cat}
                                    onClick={() => setSelectedCategory(cat === 'All' ? null : cat)}
                                    className={`px-4 py-2 rounded text-sm font-medium transition-colors ${(cat === 'All' && selectedCategory === null) || selectedCategory === cat
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-700'
                                        }`}
                                >
                                    {cat}
                                </button>
                            ))}
                        </div>

                        {/* FAQ List */}
                        <div className="space-y-3">
                            {filteredFAQs.map((faq) => (
                                <div
                                    key={faq.id}
                                    className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg overflow-hidden shadow-sm"
                                >
                                    <button
                                        onClick={() => setExpandedFAQ(expandedFAQ === faq.id ? null : faq.id)}
                                        className="w-full px-4 py-3 text-left hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors flex items-start justify-between"
                                    >
                                        <div className="flex-1">
                                            <h3 className="font-medium text-slate-900 dark:text-neutral-100">{faq.question}</h3>
                                            <div className="flex gap-2 mt-2 text-xs">
                                                <span className="text-slate-500">{faq.category}</span>
                                                <span className="text-slate-400 dark:text-slate-600">•</span>
                                                <span className="text-slate-400 dark:text-slate-600">{faq.views} views</span>
                                            </div>
                                        </div>
                                        <span className={`text-slate-400 text-xl flex-shrink-0 ${expandedFAQ === faq.id ? 'rotate-180' : ''}`}>
                                            ▼
                                        </span>
                                    </button>

                                    {expandedFAQ === faq.id && (
                                        <div className="px-4 py-4 bg-slate-50 dark:bg-slate-900 border-t border-slate-200 dark:border-neutral-600 text-slate-700 dark:text-neutral-300 text-sm leading-relaxed">
                                            {faq.answer}
                                            <div className="mt-4 flex gap-2">
                                                <button className="text-xs bg-blue-600 hover:bg-blue-500 text-white px-3 py-1 rounded">
                                                    👍 Helpful
                                                </button>
                                                <button className="text-xs bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-700 dark:text-neutral-300 px-3 py-1 rounded">
                                                    👎 Not helpful
                                                </button>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Sidebar */}
                    <div className="col-span-1 space-y-6">
                        {/* Still Need Help? */}
                        <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-6 shadow-sm">
                            <div className="text-2xl mb-3">💬</div>
                            <h3 className="font-bold text-slate-900 dark:text-neutral-100 mb-2">Still need help?</h3>
                            <p className="text-sm text-slate-600 dark:text-neutral-400 mb-4">
                                Our support team is here to assist you 24/7.
                            </p>
                            <button className="w-full px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded text-sm">
                                Contact Support
                            </button>
                        </div>

                        {/* Recent Articles */}
                        <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-6 shadow-sm">
                            <h3 className="font-bold text-slate-900 dark:text-neutral-100 mb-4">🔥 Trending</h3>
                            <div className="space-y-3">
                                {[
                                    'How to debug model latency',
                                    'Best practices for CI/CD',
                                    'Scaling for production',
                                    'Security best practices',
                                ].map((article, idx) => (
                                    <button
                                        key={idx}
                                        className="w-full text-left px-3 py-2 rounded hover:bg-slate-100 dark:hover:bg-slate-700 text-sm text-slate-700 dark:text-neutral-300 transition-colors"
                                    >
                                        {article}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Resource Links */}
                        <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-6 shadow-sm">
                            <h3 className="font-bold text-slate-900 dark:text-neutral-100 mb-4">📚 Resources</h3>
                            <div className="space-y-2">
                                {[
                                    { label: 'API Reference', icon: '📖' },
                                    { label: 'GitHub Examples', icon: '🐙' },
                                    { label: 'Status Page', icon: '📊' },
                                    { label: 'Changelog', icon: '📝' },
                                ].map((resource) => (
                                    <button
                                        key={resource.label}
                                        className="w-full flex items-center gap-2 px-3 py-2 rounded hover:bg-slate-100 dark:hover:bg-slate-700 text-sm text-slate-700 dark:text-neutral-300 transition-colors"
                                    >
                                        <span>{resource.icon}</span>
                                        <span>{resource.label}</span>
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
});

export default HelpCenter;
