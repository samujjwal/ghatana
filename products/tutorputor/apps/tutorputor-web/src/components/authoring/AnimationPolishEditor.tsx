/**
 * Animation & Polish Editor
 * 
 * @doc.type component
 * @doc.purpose Phase 4 of authoring flow - Visual design and accessibility
 * @doc.layer product
 * @doc.pattern Editor
 */

import { useState } from "react";

interface AnimationPolishEditorProps {
    manifest: any;
    onComplete: (updatedManifest: any) => void;
    onBack: () => void;
}

export function AnimationPolishEditor({ manifest, onComplete, onBack }: AnimationPolishEditorProps) {
    const [activeTab, setActiveTab] = useState<'timeline' | 'visual' | 'accessibility' | 'assessment'>('timeline');
    const [editedManifest, setEditedManifest] = useState({
        ...manifest,
        timeline: manifest.timeline || { duration: 5000, keyframes: [] },
        visualDesign: manifest.visualDesign || { theme: 'modern_education' },
        accessibility: manifest.accessibility || {
            screenReaderNarration: true,
            reducedMotion: false,
            highContrast: false
        }
    });

    const themes = [
        { value: 'modern_education', label: 'Modern Education', preview: 'bg-blue-500' },
        { value: 'dark_mode', label: 'Dark Mode', preview: 'bg-gray-900' },
        { value: 'high_contrast', label: 'High Contrast', preview: 'bg-black' },
        { value: 'colorful', label: 'Colorful', preview: 'bg-gradient-to-r from-purple-500 to-pink-500' }
    ];

    const addKeyframe = () => {
        const newKeyframe = {
            time: 0,
            entities: {}
        };
        setEditedManifest({
            ...editedManifest,
            timeline: {
                ...editedManifest.timeline,
                keyframes: [...editedManifest.timeline.keyframes, newKeyframe]
            }
        });
    };

    return (
        <div className="animation-polish-editor max-w-6xl mx-auto p-6">
            <div className="mb-8">
                <h1 className="text-3xl font-bold mb-2">Animation & Polish</h1>
                <p className="text-gray-600">Add visual design, animations, and accessibility features</p>
            </div>

            {/* Tabs */}
            <div className="border-b mb-6">
                <div className="flex gap-4">
                    <button
                        onClick={() => setActiveTab('timeline')}
                        className={`px-4 py-2 font-medium border-b-2 transition-colors ${activeTab === 'timeline'
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                    >
                        Timeline
                    </button>
                    <button
                        onClick={() => setActiveTab('visual')}
                        className={`px-4 py-2 font-medium border-b-2 transition-colors ${activeTab === 'visual'
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                    >
                        Visual Design
                    </button>
                    <button
                        onClick={() => setActiveTab('accessibility')}
                        className={`px-4 py-2 font-medium border-b-2 transition-colors ${activeTab === 'accessibility'
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                    >
                        Accessibility
                    </button>
                    <button
                        onClick={() => setActiveTab('assessment')}
                        className={`px-4 py-2 font-medium border-b-2 transition-colors ${activeTab === 'assessment'
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                    >
                        Assessment
                    </button>
                </div>
            </div>

            {/* Timeline Tab */}
            {activeTab === 'timeline' && (
                <div className="space-y-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <h3 className="font-semibold text-lg">Animation Timeline</h3>
                            <p className="text-sm text-gray-600">Define keyframes and animation sequences</p>
                        </div>
                        <button
                            onClick={addKeyframe}
                            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                        >
                            + Add Keyframe
                        </button>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Total Duration (ms)
                        </label>
                        <input
                            type="number"
                            className="w-full px-4 py-2 border rounded-md"
                            value={editedManifest.timeline.duration}
                            onChange={(e) => setEditedManifest({
                                ...editedManifest,
                                timeline: { ...editedManifest.timeline, duration: Number(e.target.value) }
                            })}
                        />
                    </div>

                    <div className="border rounded-lg p-4 bg-gray-50">
                        <div className="flex items-center justify-between mb-4">
                            <span className="text-sm font-medium">Keyframes</span>
                            <span className="text-xs text-gray-600">{editedManifest.timeline.keyframes.length} total</span>
                        </div>
                        {editedManifest.timeline.keyframes.length === 0 ? (
                            <div className="text-center py-8 text-gray-500">
                                <p>No keyframes yet. Add your first keyframe to start animating.</p>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {editedManifest.timeline.keyframes.map((kf: any, idx: number) => (
                                    <div key={idx} className="bg-white border rounded-lg p-3">
                                        <div className="flex items-center justify-between">
                                            <span className="font-medium">Keyframe {idx + 1}</span>
                                            <span className="text-sm text-gray-600">{kf.time}ms</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Easing Function
                        </label>
                        <select className="w-full px-4 py-2 border rounded-md">
                            <option value="linear">Linear</option>
                            <option value="ease-in">Ease In</option>
                            <option value="ease-out">Ease Out</option>
                            <option value="ease-in-out">Ease In-Out</option>
                            <option value="cubic-bezier">Custom Cubic Bezier</option>
                        </select>
                    </div>
                </div>
            )}

            {/* Visual Design Tab */}
            {activeTab === 'visual' && (
                <div className="space-y-6">
                    <div>
                        <h3 className="font-semibold text-lg mb-4">Theme Selection</h3>
                        <div className="grid grid-cols-2 gap-4">
                            {themes.map(theme => (
                                <button
                                    key={theme.value}
                                    onClick={() => setEditedManifest({
                                        ...editedManifest,
                                        visualDesign: { ...editedManifest.visualDesign, theme: theme.value }
                                    })}
                                    className={`p-4 border-2 rounded-lg transition-all ${editedManifest.visualDesign.theme === theme.value
                                            ? 'border-blue-500 bg-blue-50'
                                            : 'border-gray-200 hover:border-gray-300'
                                        }`}
                                >
                                    <div className={`w-full h-20 rounded-md mb-3 ${theme.preview}`} />
                                    <div className="font-medium">{theme.label}</div>
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-6">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Primary Color
                            </label>
                            <div className="flex gap-2">
                                <input
                                    type="color"
                                    className="w-16 h-10 border rounded cursor-pointer"
                                    defaultValue="#2563eb"
                                />
                                <input
                                    type="text"
                                    className="flex-1 px-3 py-2 border rounded-md"
                                    defaultValue="#2563eb"
                                />
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Secondary Color
                            </label>
                            <div className="flex gap-2">
                                <input
                                    type="color"
                                    className="w-16 h-10 border rounded cursor-pointer"
                                    defaultValue="#64748b"
                                />
                                <input
                                    type="text"
                                    className="flex-1 px-3 py-2 border rounded-md"
                                    defaultValue="#64748b"
                                />
                            </div>
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Font Family
                        </label>
                        <select className="w-full px-4 py-2 border rounded-md">
                            <option value="Inter">Inter</option>
                            <option value="Roboto">Roboto</option>
                            <option value="Open Sans">Open Sans</option>
                            <option value="Lato">Lato</option>
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Animation Duration (ms)
                        </label>
                        <input
                            type="number"
                            className="w-full px-4 py-2 border rounded-md"
                            defaultValue="300"
                            step="50"
                        />
                    </div>
                </div>
            )}

            {/* Accessibility Tab */}
            {activeTab === 'accessibility' && (
                <div className="space-y-6">
                    <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                        <h4 className="font-medium text-blue-900 mb-2">♿ WCAG 2.1 Level AA Compliance</h4>
                        <p className="text-sm text-blue-800">
                            Configure accessibility features to ensure your simulation is usable by all learners
                        </p>
                    </div>

                    <div className="space-y-4">
                        <label className="flex items-center gap-3 p-4 border rounded-lg cursor-pointer hover:bg-gray-50">
                            <input
                                type="checkbox"
                                className="w-5 h-5"
                                checked={editedManifest.accessibility.screenReaderNarration}
                                onChange={(e) => setEditedManifest({
                                    ...editedManifest,
                                    accessibility: { ...editedManifest.accessibility, screenReaderNarration: e.target.checked }
                                })}
                            />
                            <div className="flex-1">
                                <div className="font-medium">Screen Reader Narration</div>
                                <div className="text-sm text-gray-600">Provide audio descriptions for visual content</div>
                            </div>
                        </label>

                        <label className="flex items-center gap-3 p-4 border rounded-lg cursor-pointer hover:bg-gray-50">
                            <input
                                type="checkbox"
                                className="w-5 h-5"
                                checked={editedManifest.accessibility.reducedMotion}
                                onChange={(e) => setEditedManifest({
                                    ...editedManifest,
                                    accessibility: { ...editedManifest.accessibility, reducedMotion: e.target.checked }
                                })}
                            />
                            <div className="flex-1">
                                <div className="font-medium">Reduced Motion Support</div>
                                <div className="text-sm text-gray-600">Respect prefers-reduced-motion preference</div>
                            </div>
                        </label>

                        <label className="flex items-center gap-3 p-4 border rounded-lg cursor-pointer hover:bg-gray-50">
                            <input
                                type="checkbox"
                                className="w-5 h-5"
                                checked={editedManifest.accessibility.highContrast}
                                onChange={(e) => setEditedManifest({
                                    ...editedManifest,
                                    accessibility: { ...editedManifest.accessibility, highContrast: e.target.checked }
                                })}
                            />
                            <div className="flex-1">
                                <div className="font-medium">High Contrast Mode</div>
                                <div className="text-sm text-gray-600">Ensure 4.5:1 contrast ratio for text</div>
                            </div>
                        </label>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Alt Text for Simulation
                        </label>
                        <textarea
                            className="w-full px-4 py-3 border rounded-md"
                            rows={3}
                            placeholder="Describe the simulation for screen reader users"
                            defaultValue={editedManifest.accessibility.altText || ''}
                        />
                    </div>

                    <div>
                        <h4 className="font-medium mb-3">Keyboard Shortcuts</h4>
                        <div className="space-y-2">
                            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                                <span className="text-sm">Play/Pause</span>
                                <kbd className="px-2 py-1 bg-white border rounded text-xs font-mono">Space</kbd>
                            </div>
                            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                                <span className="text-sm">Reset</span>
                                <kbd className="px-2 py-1 bg-white border rounded text-xs font-mono">R</kbd>
                            </div>
                            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                                <span className="text-sm">Step Forward</span>
                                <kbd className="px-2 py-1 bg-white border rounded text-xs font-mono">→</kbd>
                            </div>
                            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                                <span className="text-sm">Step Backward</span>
                                <kbd className="px-2 py-1 bg-white border rounded text-xs font-mono">←</kbd>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Assessment Tab */}
            {activeTab === 'assessment' && (
                <div className="space-y-6">
                    <div>
                        <h3 className="font-semibold text-lg mb-4">Embedded Assessment</h3>
                        <p className="text-sm text-gray-600 mb-4">
                            Add formative assessment questions to check student understanding
                        </p>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Assessment Type
                        </label>
                        <select className="w-full px-4 py-2 border rounded-md">
                            <option value="formative">Formative (During simulation)</option>
                            <option value="summative">Summative (After simulation)</option>
                            <option value="diagnostic">Diagnostic (Before simulation)</option>
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Grading Strategy
                        </label>
                        <select className="w-full px-4 py-2 border rounded-md">
                            <option value="ecd">Evidence-Centered Design (ECD)</option>
                            <option value="completion">Completion-based</option>
                            <option value="accuracy">Accuracy-based</option>
                            <option value="efficiency">Efficiency-based</option>
                            <option value="hybrid">Hybrid (Multiple methods)</option>
                        </select>
                    </div>

                    <div className="border rounded-lg p-4">
                        <div className="flex items-center justify-between mb-4">
                            <h4 className="font-medium">Questions</h4>
                            <button className="px-3 py-1 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700">
                                + Add Question
                            </button>
                        </div>
                        <div className="text-center py-8 text-gray-500">
                            <p>No questions added yet</p>
                        </div>
                    </div>

                    <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                        <h4 className="font-medium text-yellow-900 mb-2">💡 Assessment Best Practices</h4>
                        <ul className="text-sm text-yellow-800 space-y-1">
                            <li>• Align questions with learning objectives</li>
                            <li>• Provide immediate, specific feedback</li>
                            <li>• Use varied question types</li>
                            <li>• Consider cognitive load</li>
                        </ul>
                    </div>
                </div>
            )}

            {/* Navigation */}
            <div className="flex justify-between items-center mt-8 pt-6 border-t">
                <button
                    onClick={onBack}
                    className="px-6 py-2 border rounded-md hover:bg-gray-50"
                >
                    Back
                </button>
                <button
                    onClick={() => onComplete(editedManifest)}
                    className="px-8 py-3 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                >
                    Continue to Review
                </button>
            </div>
        </div>
    );
}
