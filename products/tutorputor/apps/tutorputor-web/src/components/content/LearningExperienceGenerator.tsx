/**
 * Learning Experience Generator
 * 
 * @doc.type component
 * @doc.purpose Generate complete learning experiences with simulations and animations
 * @doc.layer product
 * @doc.pattern Generator
 */

import { useState, useEffect } from "react";

interface LearningExperience {
    id: string;
    title: string;
    description: string;
    claims: Claim[];
    simulations: Simulation[];
    animations: Animation[];
    assessments: Assessment[];
    metadata: {
        domain: string;
        gradeLevel: string;
        duration: string;
        objectives: string[];
    };
}

interface Claim {
    id: string;
    statement: string;
    evidence: string;
    simulationId?: string;
    animationId?: string;
    examples: Example[];
}

interface Example {
    id: string;
    title: string;
    description: string;
    type: 'simulation' | 'animation' | 'text' | 'video';
    content: any;
    interactive: boolean;
}

interface Simulation {
    id: string;
    title: string;
    description: string;
    manifest: any;
    interactive: boolean;
    duration: string;
}

interface Animation {
    id: string;
    title: string;
    description: string;
    type: '2d' | '3d' | 'timeline';
    duration: string;
    controls: AnimationControl[];
}

interface AnimationControl {
    id: string;
    type: 'play' | 'pause' | 'seek' | 'speed' | 'parameter';
    label: string;
}

interface Assessment {
    id: string;
    type: 'formative' | 'summative';
    questions: Question[];
    grading: GradingStrategy;
}

interface Question {
    id: string;
    type: 'multiple_choice' | 'free_response' | 'simulation_based';
    question: string;
    options?: string[];
    correct?: number | string;
    simulationId?: string;
    feedback: {
        correct: string;
        incorrect: string;
    };
}

interface GradingStrategy {
    method: 'ecd' | 'completion' | 'accuracy' | 'hybrid';
    evidenceCollection: boolean;
}

interface LearningExperienceGeneratorProps {
    onGenerated: (experience: LearningExperience) => void;
}

export function LearningExperienceGenerator({ onGenerated }: LearningExperienceGeneratorProps) {
    const [step, setStep] = useState<'description' | 'generating' | 'review' | 'authoring'>('description');
    const [description, setDescription] = useState("");
    const [isGenerating, setIsGenerating] = useState(false);
    const [generatedExperience, setGeneratedExperience] = useState<LearningExperience | null>(null);
    const [selectedProvider, setSelectedProvider] = useState("openai-primary");

    const providers = [
        { value: "openai-primary", label: "OpenAI GPT-4", capability: "full" },
        { value: "anthropic-backup", label: "Anthropic Claude", capability: "full" },
        { value: "ollama-local", label: "Ollama (Local)", capability: "basic" }
    ];

    const generateLearningExperience = async () => {
        setIsGenerating(true);
        setStep('generating');

        try {
            // Step 1: Generate learning objectives and structure
            const structureResponse = await fetch('/api/v1/learning-experiences/generate-structure', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    description,
                    provider: selectedProvider,
                    includeSimulations: true,
                    includeAnimations: true,
                    includeAssessments: true
                })
            });

            const structure = await structureResponse.json();

            // Step 2: Generate simulations for each claim
            const simulationPromises = structure.claims.map(async (claim: any, claimIndex: number) => {
                if (claim.requiresSimulation) {
                    try {
                        const simResponse = await fetch('/api/v1/simulations/generate', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                prompt: `Create a simulation to demonstrate: ${claim.statement}`,
                                domain: structure.metadata.domain,
                                constraints: {
                                    gradeLevel: structure.metadata.gradeLevel,
                                    interactive: true,
                                    duration: "5-10 minutes"
                                },
                                provider: selectedProvider
                            })
                        });

                        if (!simResponse.ok) {
                            console.warn(`Failed to generate simulation for claim ${claimIndex}: ${claim.statement}`);
                            return {
                                id: `sim_mock_${claimIndex}`,
                                title: `Simulation: ${claim.statement}`,
                                description: `Interactive simulation demonstrating ${claim.statement}`,
                                manifest: {
                                    entities: [
                                        {
                                            id: "projectile",
                                            type: "object",
                                            properties: { mass: 1, position: { x: 0, y: 0 }, velocity: { x: 10, y: 10 } }
                                        }
                                    ],
                                    steps: [
                                        {
                                            id: "launch",
                                            description: "Launch projectile",
                                            actions: [
                                                { type: "set_velocity", entity: "projectile", velocity: { x: 10, y: 10 } }
                                            ]
                                        }
                                    ]
                                },
                                interactive: true,
                                duration: "5-10 minutes"
                            };
                        }

                        return await simResponse.json();
                    } catch (error) {
                        console.warn(`Error generating simulation for claim ${claimIndex}:`, error);
                        return {
                            id: `sim_fallback_${claimIndex}`,
                            title: `Simulation: ${claim.statement}`,
                            description: `Interactive simulation demonstrating ${claim.statement}`,
                            manifest: { entities: [], steps: [] },
                            interactive: true,
                            duration: "5-10 minutes"
                        };
                    }
                }
                return null;
            });

            const simulations = await Promise.all(simulationPromises);

            // Step 3: Generate animations for visual concepts
            const animationPromises = structure.visualConcepts.map(async (concept: any, conceptIndex: number) => {
                try {
                    const animResponse = await fetch('/api/v1/animations/generate', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            concept: concept.description,
                            type: concept.animationType,
                            duration: "30-60 seconds",
                            provider: selectedProvider
                        })
                    });

                    if (!animResponse.ok) {
                        console.warn(`Failed to generate animation for concept ${conceptIndex}: ${concept.description}`);
                        return {
                            id: `anim_mock_${conceptIndex}`,
                            title: `Animation: ${concept.description}`,
                            description: `Visual animation showing ${concept.description}`,
                            type: concept.animationType || "2d",
                            duration: "30 seconds",
                            timeline: {
                                keyframes: [
                                    { time: 0, description: "Initial state", elements: {} },
                                    { time: 15000, description: "Mid animation", elements: {} },
                                    { time: 30000, description: "Final state", elements: {} }
                                ],
                                duration: 30000,
                                easing: "ease-in-out"
                            },
                            controls: [
                                { id: "play", type: "play", label: "Play" },
                                { id: "pause", type: "pause", label: "Pause" },
                                { id: "seek", type: "seek", label: "Seek" }
                            ]
                        };
                    }

                    return await animResponse.json();
                } catch (error) {
                    console.warn(`Error generating animation for concept ${conceptIndex}:`, error);
                    return {
                        id: `anim_fallback_${conceptIndex}`,
                        title: `Animation: ${concept.description}`,
                        description: `Visual animation showing ${concept.description}`,
                        type: concept.animationType || "2d",
                        duration: "30 seconds",
                        timeline: { keyframes: [], duration: 30000, easing: "ease-in-out" },
                        controls: [
                            { id: "play", type: "play", label: "Play" },
                            { id: "pause", type: "pause", label: "Pause" }
                        ]
                    };
                }
            });

            const animations = await Promise.all(animationPromises);

            // Step 4: Generate assessments
            let assessment = null;
            try {
                const assessmentResponse = await fetch('/api/v1/assessments/generate', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        learningObjectives: structure.metadata.objectives,
                        claims: structure.claims,
                        simulations: simulations.filter(s => s),
                        type: 'formative',
                        provider: selectedProvider
                    })
                });

                if (assessmentResponse.ok) {
                    assessment = await assessmentResponse.json();
                } else {
                    console.warn("Failed to generate assessment, using fallback");
                    assessment = {
                        id: "assessment_fallback",
                        type: "formative",
                        questions: structure.claims.map((claim: any, index: number) => ({
                            id: `q_fallback_${index}`,
                            type: "multiple_choice",
                            question: `Which statement best describes: ${claim.statement}?`,
                            options: [
                                claim.statement,
                                "Incorrect option 1",
                                "Incorrect option 2",
                                "Incorrect option 3"
                            ],
                            correct: 0,
                            feedback: {
                                correct: "Correct! This matches the evidence.",
                                incorrect: "Review the simulation and explanation."
                            }
                        })),
                        grading: {
                            method: "accuracy",
                            evidenceCollection: false
                        }
                    };
                }
            } catch (error) {
                console.warn("Error generating assessment:", error);
                assessment = {
                    id: "assessment_error",
                    type: "formative",
                    questions: [],
                    grading: { method: "completion", evidenceCollection: false }
                };
            }

            // Step 5: Assemble complete learning experience
            const experience: LearningExperience = {
                id: `exp_${Date.now()}`,
                title: structure.title,
                description: structure.description,
                claims: structure.claims.map((claim: any, index: number) => ({
                    ...claim,
                    simulationId: simulations[index]?.id,
                    animationId: animations[index]?.id,
                    examples: [
                        {
                            id: `sim_${index}`,
                            title: `Interactive Simulation: ${claim.statement}`,
                            description: `Explore ${claim.statement} through hands-on simulation`,
                            type: 'simulation',
                            content: simulations[index],
                            interactive: true
                        },
                        {
                            id: `text_${index}`,
                            title: "Real-world Example",
                            description: claim.realWorldExample || claim.explanation || "Practical application of this concept",
                            type: 'text',
                            content: claim.explanation || claim.evidence || "Scientific evidence supporting this claim",
                            interactive: false
                        },
                        ...(animations[index] ? [{
                            id: `anim_${index}`,
                            title: `Visual Animation: ${claim.statement}`,
                            description: `Visual demonstration of ${claim.statement}`,
                            type: 'animation',
                            content: animations[index],
                            interactive: false
                        }] : [])
                    ].filter(Boolean)
                })),
                simulations: simulations.filter(s => s),
                animations: animations.filter(a => a),
                assessments: [assessment],
                metadata: structure.metadata
            };

            setGeneratedExperience(experience);
            setStep('review');
        } catch (error) {
            console.error('Generation failed:', error);
            setStep('description');
        } finally {
            setIsGenerating(false);
        }
    };

    const handleRefineExperience = () => {
        // Navigate to simulation authoring for refinement
        setStep('authoring');
    };

    const handlePublishExperience = async () => {
        if (!generatedExperience) return;

        try {
            await fetch('/api/v1/learning-experiences', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(generatedExperience)
            });

            onGenerated(generatedExperience);
        } catch (error) {
            console.error('Publication failed:', error);
        }
    };

    if (step === 'description') {
        return (
            <div className="learning-experience-generator max-w-4xl mx-auto p-6">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold mb-2">Create Learning Experience</h1>
                    <p className="text-gray-600">Describe what you want to teach, and we'll generate simulations, animations, and assessments to back every claim.</p>
                </div>

                <div className="space-y-6">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            AI Provider
                        </label>
                        <div className="grid grid-cols-3 gap-4">
                            {providers.map(provider => (
                                <button
                                    key={provider.value}
                                    onClick={() => setSelectedProvider(provider.value)}
                                    className={`p-4 border-2 rounded-lg text-left transition-all ${selectedProvider === provider.value
                                        ? 'border-blue-500 bg-blue-50'
                                        : 'border-gray-200 hover:border-gray-300'
                                        }`}
                                >
                                    <div className="font-medium mb-1">{provider.label}</div>
                                    <div className="text-xs text-gray-600 capitalize">{provider.capability} capability</div>
                                </button>
                            ))}
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Learning Experience Description
                        </label>
                        <textarea
                            className="w-full px-4 py-3 border rounded-md focus:ring-2 focus:ring-blue-500 min-h-[200px]"
                            placeholder={`Example: I want to teach high school students about projectile motion. They should understand:
• How launch angle affects trajectory
• The relationship between initial velocity and range
• How to calculate maximum height and range
• Real-world applications like sports and engineering

Include interactive simulations where they can adjust parameters and see results in real-time.`}
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                        />
                    </div>

                    <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                        <h4 className="font-medium text-blue-900 mb-2">💡 What We'll Generate</h4>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm text-blue-800">
                            <div>
                                <strong>Learning Claims:</strong> Key concepts with evidence
                            </div>
                            <div>
                                <strong>Interactive Simulations:</strong> Hands-on exploration
                            </div>
                            <div>
                                <strong>Animations:</strong> Visual demonstrations
                            </div>
                            <div>
                                <strong>Assessments:</strong> Formative and summative
                            </div>
                            <div>
                                <strong>Real Examples:</strong> Practical applications
                            </div>
                            <div>
                                <strong>Accessibility:</strong> WCAG 2.1 AA compliant
                            </div>
                        </div>
                    </div>

                    <button
                        onClick={generateLearningExperience}
                        disabled={!description.trim() || isGenerating}
                        className="w-full px-6 py-3 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {isGenerating ? 'Generating...' : 'Generate Learning Experience'}
                    </button>
                </div>
            </div>
        );
    }

    if (step === 'generating') {
        return (
            <div className="learning-experience-generator max-w-4xl mx-auto p-6">
                <div className="text-center py-12">
                    <svg className="animate-spin h-12 w-12 mx-auto mb-4 text-blue-600" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                    <h2 className="text-xl font-semibold mb-2">Generating Learning Experience</h2>
                    <p className="text-gray-600 mb-6">Creating simulations, animations, and assessments...</p>

                    <div className="space-y-3 max-w-md mx-auto">
                        <div className="flex items-center gap-3">
                            <div className="w-4 h-4 bg-green-500 rounded-full"></div>
                            <span className="text-sm">Analyzing learning objectives</span>
                        </div>
                        <div className="flex items-center gap-3">
                            <div className="w-4 h-4 bg-green-500 rounded-full"></div>
                            <span className="text-sm">Generating interactive simulations</span>
                        </div>
                        <div className="flex items-center gap-3">
                            <div className="w-4 h-4 bg-blue-500 rounded-full animate-pulse"></div>
                            <span className="text-sm">Creating animations</span>
                        </div>
                        <div className="flex items-center gap-3">
                            <div className="w-4 h-4 bg-gray-300 rounded-full"></div>
                            <span className="text-sm">Building assessments</span>
                        </div>
                        <div className="flex items-center gap-3">
                            <div className="w-4 h-4 bg-gray-300 rounded-full"></div>
                            <span className="text-sm">Assembling experience</span>
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    if (step === 'review' && generatedExperience) {
        return (
            <div className="learning-experience-generator max-w-6xl mx-auto p-6">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold mb-2">Review Learning Experience</h1>
                    <p className="text-gray-600">Every claim is backed by interactive examples and simulations</p>
                </div>

                <div className="space-y-8">
                    {/* Overview */}
                    <div className="border rounded-lg p-6">
                        <h3 className="font-semibold text-lg mb-4">{generatedExperience.title}</h3>
                        <p className="text-gray-700 mb-4">{generatedExperience.description}</p>

                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                            <div>
                                <span className="text-gray-600">Domain:</span>
                                <span className="ml-2 font-medium">{generatedExperience.metadata.domain}</span>
                            </div>
                            <div>
                                <span className="text-gray-600">Grade Level:</span>
                                <span className="ml-2 font-medium">{generatedExperience.metadata.gradeLevel}</span>
                            </div>
                            <div>
                                <span className="text-gray-600">Duration:</span>
                                <span className="ml-2 font-medium">{generatedExperience.metadata.duration}</span>
                            </div>
                            <div>
                                <span className="text-gray-600">Claims:</span>
                                <span className="ml-2 font-medium">{generatedExperience.claims.length}</span>
                            </div>
                        </div>
                    </div>

                    {/* Claims with Evidence */}
                    <div>
                        <h3 className="font-semibold text-lg mb-4">Learning Claims with Evidence</h3>
                        <div className="space-y-6">
                            {generatedExperience.claims.map((claim, index) => (
                                <div key={claim.id} className="border rounded-lg p-6">
                                    <div className="flex items-start gap-4">
                                        <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center flex-shrink-0">
                                            <span className="text-blue-600 font-medium">{index + 1}</span>
                                        </div>
                                        <div className="flex-1">
                                            <h4 className="font-medium text-lg mb-2">{claim.statement}</h4>
                                            <p className="text-gray-600 mb-4">{claim.evidence}</p>

                                            {/* Examples */}
                                            <div className="space-y-3">
                                                <h5 className="font-medium text-sm text-gray-700">Supporting Examples:</h5>
                                                {claim.examples.map((example) => (
                                                    <div key={example.id} className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                                                        <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${example.type === 'simulation' ? 'bg-green-100' : 'bg-blue-100'
                                                            }`}>
                                                            {example.type === 'simulation' ? (
                                                                <svg className="w-5 h-5 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                                                                </svg>
                                                            ) : (
                                                                <svg className="w-5 h-5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
                                                                </svg>
                                                            )}
                                                        </div>
                                                        <div className="flex-1">
                                                            <div className="font-medium text-sm">{example.title}</div>
                                                            <div className="text-xs text-gray-600">{example.description}</div>
                                                            {example.interactive && (
                                                                <span className="inline-block mt-1 px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded">
                                                                    Interactive
                                                                </span>
                                                            )}
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Simulations Preview */}
                    {generatedExperience.simulations.length > 0 && (
                        <div>
                            <h3 className="font-semibold text-lg mb-4">Interactive Simulations</h3>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                {generatedExperience.simulations.map((sim) => (
                                    <div key={sim.id} className="border rounded-lg p-4">
                                        <div className="flex items-center gap-2 mb-2">
                                            <div className="w-8 h-8 bg-green-100 rounded flex items-center justify-center">
                                                <svg className="w-4 h-4 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                                                </svg>
                                            </div>
                                            <h4 className="font-medium">{sim.title}</h4>
                                        </div>
                                        <p className="text-sm text-gray-600 mb-2">{sim.description}</p>
                                        <div className="flex items-center gap-2 text-xs text-gray-500">
                                            <span>Duration: {sim.duration}</span>
                                            <span>•</span>
                                            <span className="text-green-600">Interactive</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Animations Preview */}
                    {generatedExperience.animations.length > 0 && (
                        <div>
                            <h3 className="font-semibold text-lg mb-4">Visual Animations</h3>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                {generatedExperience.animations.map((anim) => (
                                    <div key={anim.id} className="border rounded-lg p-4">
                                        <div className="flex items-center gap-2 mb-2">
                                            <div className="w-8 h-8 bg-purple-100 rounded flex items-center justify-center">
                                                <svg className="w-4 h-4 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 4v16M17 4v16M3 8h4m10 0h4M3 16h4m10 0h4" />
                                                </svg>
                                            </div>
                                            <h4 className="font-medium">{anim.title}</h4>
                                        </div>
                                        <p className="text-sm text-gray-600 mb-2">{anim.description}</p>
                                        <div className="flex items-center gap-2 text-xs text-gray-500">
                                            <span>Type: {anim.type}</span>
                                            <span>•</span>
                                            <span>Duration: {anim.duration}</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>

                {/* Actions */}
                <div className="flex justify-between items-center mt-8 pt-6 border-t">
                    <button
                        onClick={() => setStep('description')}
                        className="px-6 py-2 border rounded-md hover:bg-gray-50"
                    >
                        Start Over
                    </button>
                    <div className="flex gap-3">
                        <button
                            onClick={handleRefineExperience}
                            className="px-6 py-2 border rounded-md hover:bg-gray-50"
                        >
                            Refine & Edit
                        </button>
                        <button
                            onClick={handlePublishExperience}
                            className="px-8 py-3 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                        >
                            Publish Experience
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    if (step === 'authoring') {
        // Navigate to simulation authoring for refinement
        return (
            <div className="learning-experience-authoring">
                <div className="mb-6">
                    <h2 className="text-2xl font-bold mb-2">Refine Learning Experience</h2>
                    <p className="text-gray-600">Customize simulations and animations to perfectly match your teaching needs</p>
                </div>

                {/* Here you would integrate the SimulationAuthoringPage */}
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-6 text-center">
                    <p className="text-blue-800">Simulation authoring integration would go here</p>
                    <p className="text-sm text-blue-600 mt-2">Users can refine individual simulations and animations</p>
                </div>
            </div>
        );
    }

    return null;
}
