/**
 * Simulation Data Seeding Page
 * 
 * Admin page for seeding simulation and visualization data across all domains.
 * Provides UI for one-click data population and verification.
 * 
 * @doc.type React Component
 * @doc.purpose Admin page for database seeding
 * @doc.layer product
 */

'use client';

import { useState, useEffect } from 'react';
import { seedAllDomains } from '../../utils/seedSimulationData';

interface SeedingProgress {
    status: 'idle' | 'loading' | 'success' | 'error';
    message: string;
    domainsCreated: number;
    conceptsCreated: number;
    simulationsCreated: number;
    visualizationsCreated: number;
}

interface DomainResult {
    domain: string;
    domainId: string;
    concepts: Array<{ id: string; name: string }>;
}

export default function SimulationSeedingPage() {
    const [progress, setProgress] = useState<SeedingProgress>({
        status: 'idle',
        message: 'Ready to seed simulation data',
        domainsCreated: 0,
        conceptsCreated: 0,
        simulationsCreated: 0,
        visualizationsCreated: 0
    });
    const [results, setResults] = useState<DomainResult[]>([]);
    const [token, setToken] = useState<string>('');
    const [showInstructions, setShowInstructions] = useState(false);
    const [seedMode, setSeedMode] = useState<'skip-duplicates' | 'force-clean'>('skip-duplicates');

    useEffect(() => {
        // Try to get token from localStorage or environment
        const storedToken = localStorage.getItem('authToken');
        if (storedToken) {
            setToken(storedToken);
        }
    }, []);

    const handleSeedAll = async () => {
        // Simple test to verify button click works
        alert(`✅ Button clicked! Starting seeding (${seedMode})...`);

        console.log('🌱 Seed button clicked - starting seeding process...');
        console.log('Mode:', seedMode);
        console.log('Token:', token ? 'Present' : 'Missing');

        setProgress({
            status: 'loading',
            message: `Starting seeding process (${seedMode === 'skip-duplicates' ? 'skip duplicates' : 'force clean & reseed'})...`,
            domainsCreated: 0,
            conceptsCreated: 0,
            simulationsCreated: 0,
            visualizationsCreated: 0
        });

        try {
            console.log('Calling seedAllDomains...');
            const seedResults = await seedAllDomains(token || undefined, seedMode);
            console.log('Seeding results received:', seedResults);

            // Calculate totals
            const totalDomains = seedResults.length;
            const totalConcepts = seedResults.reduce((sum: number, r: any) => sum + r.concepts.length, 0);
            const totalSimulations = totalConcepts; // Each concept gets a simulation
            const totalVisualizations = totalConcepts; // Each concept gets a visualization

            console.log(`Seeding complete: ${totalDomains} domains, ${totalConcepts} concepts`);

            setResults(seedResults);
            const modeLabel = seedMode === 'skip-duplicates' ? '(skipped duplicates)' : '(cleaned & reseeded)';
            setProgress({
                status: 'success',
                message: `✅ Successfully seeded all data! ${modeLabel}`,
                domainsCreated: totalDomains,
                conceptsCreated: totalConcepts,
                simulationsCreated: totalSimulations,
                visualizationsCreated: totalVisualizations
            });
        } catch (error) {
            console.error('❌ Seeding error:', error);
            const errorMessage = error instanceof Error ? error.message : String(error);
            setProgress({
                status: 'error',
                message: `❌ Seeding failed: ${errorMessage}`,
                domainsCreated: 0,
                conceptsCreated: 0,
                simulationsCreated: 0,
                visualizationsCreated: 0
            });
        }
    };

    const handleReset = () => {
        setProgress({
            status: 'idle',
            message: 'Ready to seed simulation data',
            domainsCreated: 0,
            conceptsCreated: 0,
            simulationsCreated: 0,
            visualizationsCreated: 0
        });
        setResults([]);
    };

    return (
        <div className="w-full max-w-4xl mx-auto p-6 space-y-6">
            {/* Header */}
            <div className="space-y-2">
                <h1 className="text-3xl font-bold">🌱 Simulation Data Seeding</h1>
                <p className="text-gray-600">
                    Populate the database with comprehensive simulation and visualization examples
                    across all domains (Physics, Chemistry, Biology, Mathematics, Economics).
                </p>
            </div>

            {/* Status Card */}
            <div className="bg-gradient-to-r from-blue-50 to-indigo-50 border-2 border-indigo-200">
                <div className="space-y-4">
                    <div className="flex items-center justify-between">
                        <h2 className="text-xl font-semibold">Status</h2>
                        <span
                            className={`px-3 py-1 rounded-full text-sm font-medium ${progress.status === 'idle'
                                ? 'bg-gray-200 text-gray-800'
                                : progress.status === 'loading'
                                    ? 'bg-blue-200 text-blue-800'
                                    : progress.status === 'success'
                                        ? 'bg-green-200 text-green-800'
                                        : 'bg-red-200 text-red-800'
                                }`}
                        >
                            {progress.status === 'idle'
                                ? 'Ready'
                                : progress.status === 'loading'
                                    ? 'Seeding...'
                                    : progress.status === 'success'
                                        ? 'Complete'
                                        : 'Error'}
                        </span>
                    </div>
                    <p className="text-gray-700">{progress.message}</p>

                    {/* Progress Stats */}
                    {progress.status !== 'idle' && (
                        <div className="grid grid-cols-4 gap-4 mt-4">
                            <div className="bg-white p-3 rounded-lg">
                                <div className="text-2xl font-bold text-blue-600">
                                    {progress.domainsCreated}
                                </div>
                                <div className="text-sm text-gray-600">Domains</div>
                            </div>
                            <div className="bg-white p-3 rounded-lg">
                                <div className="text-2xl font-bold text-indigo-600">
                                    {progress.conceptsCreated}
                                </div>
                                <div className="text-sm text-gray-600">Concepts</div>
                            </div>
                            <div className="bg-white p-3 rounded-lg">
                                <div className="text-2xl font-bold text-purple-600">
                                    {progress.simulationsCreated}
                                </div>
                                <div className="text-sm text-gray-600">Simulations</div>
                            </div>
                            <div className="bg-white p-3 rounded-lg">
                                <div className="text-2xl font-bold text-pink-600">
                                    {progress.visualizationsCreated}
                                </div>
                                <div className="text-sm text-gray-600">Visualizations</div>
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* Auth Token Input (if needed) */}
            {!token && (
                <div className="bg-yellow-50 border-2 border-yellow-200">
                    <div className="space-y-3">
                        <h3 className="font-semibold text-yellow-900">⚠️ Authentication Token</h3>
                        <input
                            type="password"
                            placeholder="Paste your authentication token here (optional)"
                            value={token}
                            onChange={(e) => setToken(e.target.value)}
                            className="w-full px-3 py-2 border border-yellow-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-yellow-500"
                        />
                        <p className="text-sm text-yellow-800">
                            Token is required if your API endpoints are protected. Check your browser's
                            localStorage for 'authToken'.
                        </p>
                    </div>
                </div>
            )}

            {/* Seeding Mode Selection */}
            <div className="bg-blue-50 border-2 border-blue-200">
                <div className="space-y-4">
                    <h3 className="font-semibold text-blue-900">⚙️ Seeding Mode</h3>
                    <div className="space-y-3">
                        <label className="flex items-center gap-3 p-3 rounded-lg border-2 border-blue-300 cursor-pointer hover:bg-blue-100" style={{ backgroundColor: seedMode === 'skip-duplicates' ? '#dbeafe' : 'white' }}>
                            <input
                                type="radio"
                                name="seedMode"
                                value="skip-duplicates"
                                checked={seedMode === 'skip-duplicates'}
                                onChange={(e) => setSeedMode(e.target.value as 'skip-duplicates' | 'force-clean')}
                                className="w-4 h-4"
                            />
                            <div>
                                <div className="font-medium text-blue-900">📋 Skip Duplicates</div>
                                <div className="text-sm text-blue-700">Reuse existing domains if they already exist. Safe for re-running.</div>
                            </div>
                        </label>

                        <label className="flex items-center gap-3 p-3 rounded-lg border-2 border-red-300 cursor-pointer hover:bg-red-100" style={{ backgroundColor: seedMode === 'force-clean' ? '#fee2e2' : 'white' }}>
                            <input
                                type="radio"
                                name="seedMode"
                                value="force-clean"
                                checked={seedMode === 'force-clean'}
                                onChange={(e) => setSeedMode(e.target.value as 'skip-duplicates' | 'force-clean')}
                                className="w-4 h-4"
                            />
                            <div>
                                <div className="font-medium text-red-900">🗑️ Force Clean & Reseed</div>
                                <div className="text-sm text-red-700">Delete all existing domains and recreate from scratch. WARNING: Destructive!</div>
                            </div>
                        </label>
                    </div>
                </div>
            </div>

            {/* Action Buttons */}
            <div className="flex gap-3">
                <button
                    onClick={handleSeedAll}
                    disabled={progress.status === 'loading'}
                    className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2 rounded-lg font-semibold transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                    type="button"
                >
                    {progress.status === 'loading' ? '⏳ Seeding...' : '🌱 Seed All Data'}
                </button>

                {progress.status !== 'idle' && (
                    <button
                        onClick={handleReset}
                        className="bg-gray-600 hover:bg-gray-700 text-white px-6 py-2 rounded-lg font-semibold transition-all"
                        type="button"
                    >
                        ↺ Reset
                    </button>
                )}

                <button
                    onClick={() => setShowInstructions(!showInstructions)}
                    className="bg-gray-500 hover:bg-gray-600 text-white px-6 py-2 rounded-lg font-semibold transition-all"
                    type="button"
                >
                    {showInstructions ? '▲ Hide' : '▼ Show'} Instructions
                </button>
            </div>

            {/* Instructions */}
            {showInstructions && (
                <div className="bg-gray-50 border-2 border-gray-200">
                    <div className="space-y-4">
                        <h3 className="font-semibold text-gray-900">📖 Instructions</h3>
                        <div className="prose prose-sm max-w-none">
                            <ol className="space-y-2 text-sm text-gray-700 list-decimal list-inside">
                                <li>Click "Seed All Data" to create all domains, concepts, simulations, and visualizations</li>
                                <li>Wait for the seeding process to complete - this may take a few seconds</li>
                                <li>Check the status display to see how many items were created</li>
                                <li>View the results below to see domain IDs and concept IDs</li>
                                <li>Visit the Concept Management page to see simulations and visualizations rendered</li>
                            </ol>
                            <div className="mt-4 p-3 bg-blue-50 border-l-4 border-blue-500 text-sm text-blue-800">
                                <strong>💡 Tip:</strong> After seeding, navigate to the Concept Management page to view
                                simulations and visualizations for each domain.
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Results */}
            {results.length > 0 && (
                <div className="border-2 border-green-200 bg-green-50">
                    <div className="space-y-4">
                        <h3 className="text-xl font-semibold text-green-900">✅ Seeding Results</h3>

                        {results.map((result) => (
                            <div key={result.domainId} className="bg-white p-4 rounded-lg border border-green-200">
                                <div className="flex items-start justify-between mb-3">
                                    <div>
                                        <h4 className="font-semibold text-gray-900">{result.domain}</h4>
                                        <p className="text-sm text-gray-600 font-mono">{result.domainId}</p>
                                    </div>
                                </div>

                                <div className="space-y-2">
                                    {result.concepts.map((concept) => (
                                        <div
                                            key={concept.id}
                                            className="flex items-start justify-between text-sm bg-gray-50 p-2 rounded"
                                        >
                                            <span className="text-gray-900">
                                                └─ <strong>{concept.name}</strong>
                                            </span>
                                            <code className="text-gray-600 font-mono text-xs">{concept.id}</code>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        ))}

                        {/* Navigation Action */}
                        <div className="mt-4 p-4 bg-indigo-50 border-l-4 border-indigo-500 rounded">
                            <div className="flex items-center justify-between">
                                <p className="text-sm text-indigo-800">
                                    <strong>🎯 Next Step:</strong> View your seeded domains and their visualizations.
                                </p>
                                <a
                                    href="/content/domains"
                                    className="inline-block bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg font-semibold transition-colors"
                                >
                                    👁️ See All Data →
                                </a>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* What Gets Created Section */}
            <div className="border-2 border-purple-200 bg-purple-50">
                <div className="space-y-4">
                    <h3 className="text-lg font-semibold text-purple-900">📊 What Gets Created</h3>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <h4 className="font-semibold text-purple-800 mb-2">🌐 Domains (5)</h4>
                            <ul className="text-sm text-gray-700 space-y-1">
                                <li>✓ Physics 101</li>
                                <li>✓ Chemistry Fundamentals</li>
                                <li>✓ Biology Essentials</li>
                                <li>✓ Mathematics Fundamentals</li>
                                <li>✓ Economics 101</li>
                            </ul>
                        </div>

                        <div>
                            <h4 className="font-semibold text-purple-800 mb-2">💡 Concepts (7)</h4>
                            <ul className="text-sm text-gray-700 space-y-1">
                                <li>✓ Kinematics (physics)</li>
                                <li>✓ Molecular Structure (chemistry)</li>
                                <li>✓ Mitosis (biology)</li>
                                <li>✓ Functions (mathematics)</li>
                                <li>✓ Supply & Demand (economics)</li>
                                <li>✓ Market Equilibrium (math application)</li>
                            </ul>
                        </div>

                        <div>
                            <h4 className="font-semibold text-purple-800 mb-2">🎮 Simulations (7)</h4>
                            <ul className="text-sm text-gray-700 space-y-1">
                                <li>✓ physics-2D (Projectile Motion)</li>
                                <li>✓ chemistry-interactive (H₂O Bonding)</li>
                                <li>✓ biology-interactive (Cell Division)</li>
                                <li>✓ mathematics-interactive (2x Functions)</li>
                                <li>✓ + Visualizations for each</li>
                            </ul>
                        </div>

                        <div>
                            <h4 className="font-semibold text-purple-800 mb-2">📈 Visualization Types</h4>
                            <ul className="text-sm text-gray-700 space-y-1">
                                <li>✓ graph-2d (Function plots)</li>
                                <li>✓ graph-3d (3D surfaces)</li>
                                <li>✓ chart (Bar charts)</li>
                                <li>✓ molecule (Molecular structure)</li>
                                <li>✓ diagram (Process flows)</li>
                            </ul>
                        </div>
                    </div>
                </div>
            </div>

            {/* Troubleshooting */}
            <div className="border-2 border-orange-200 bg-orange-50">
                <div className="space-y-3">
                    <h3 className="font-semibold text-orange-900">🔧 Troubleshooting</h3>
                    <div className="space-y-2 text-sm text-orange-800">
                        <div>
                            <strong>❌ "Failed to create domain"</strong>
                            <p className="ml-4 text-gray-700">
                                Check that the API is running on port 3200 and your authentication token is valid.
                            </p>
                        </div>
                        <div>
                            <strong>❌ "HTTP 404"</strong>
                            <p className="ml-4 text-gray-700">
                                The domain or concept creation failed. Check browser console for error details.
                            </p>
                        </div>
                        <div>
                            <strong>⚠️ "Empty results"</strong>
                            <p className="ml-4 text-gray-700">
                                Check that all API endpoints are properly implemented and database is accessible.
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
