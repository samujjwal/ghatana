/**
 * Genesis - Organization Creation Wizard
 *
 * The AI-powered onboarding flow for creating a new Virtual Organization.
 * Users describe their vision, and AI generates the initial structure.
 * Connected to backend API for real organization generation.
 *
 * @doc.type route
 * @doc.section CREATE
 */

import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { Card, Button } from '@/components/ui';
import {
    Sparkles, Building2, Users, Target, ArrowRight, Loader2,
    Check, AlertCircle, X, Plus, RefreshCw, Wand2, Trash2
} from 'lucide-react';
import { useGenerateOrganization, useMaterializeOrganization } from '@/hooks';
import type { GeneratedOrg, GenesisRequest } from '@/hooks/useOrganizationApi';

// Organization templates for quick start
const TEMPLATES = [
    {
        id: 'startup',
        name: 'Startup',
        description: 'Small, agile team focused on rapid iteration',
        icon: '🚀',
        departments: ['Engineering', 'Product'],
        agents: 5,
    },
    {
        id: 'scaleup',
        name: 'Scale-up',
        description: 'Growing org with specialized teams',
        icon: '📈',
        departments: ['Engineering', 'Product', 'QA', 'DevOps'],
        agents: 15,
    },
    {
        id: 'enterprise',
        name: 'Enterprise',
        description: 'Full-featured org with compliance & governance',
        icon: '🏢',
        departments: ['Engineering', 'Product', 'QA', 'DevOps', 'Security', 'Compliance'],
        agents: 30,
    },
];

const SUGGESTIONS = [
    { label: '+ Mobile Team', prompt: 'Add a mobile engineering team with iOS and Android developers' },
    { label: '+ Data Science', prompt: 'Add a data science department for analytics and ML' },
    { label: '+ QA Team', prompt: 'Add a QA department for testing' },
    { label: '+ DevOps', prompt: 'Add a DevOps team for infrastructure' },
];

export default function GenesisPage() {
    const navigate = useNavigate();
    const [step, setStep] = useState<'vision' | 'preview' | 'materialize'>('vision');
    const [vision, setVision] = useState('');
    const [orgName, setOrgName] = useState('');
    const [selectedTemplate, setSelectedTemplate] = useState<string | null>(null);

    // We keep the original API result and a local editable copy
    const [originalGeneratedOrg, setOriginalGeneratedOrg] = useState<GeneratedOrg | null>(null);
    const [previewOrg, setPreviewOrg] = useState<GeneratedOrg | null>(null);

    const [refinementInput, setRefinementInput] = useState('');

    // API mutations
    const generateMutation = useGenerateOrganization();
    const materializeMutation = useMaterializeOrganization();

    // Generate organization with AI
    const handleGenerate = async (overrideVision?: string) => {
        const template = TEMPLATES.find(t => t.id === selectedTemplate);
        const visionToUse = overrideVision || vision || 'Build great software';

        const request: GenesisRequest = {
            name: orgName || 'My Organization',
            vision: visionToUse,
            template: (selectedTemplate as GenesisRequest['template']) || 'startup',
            departments: template?.departments,
            agentCount: template?.agents,
            options: {
                enableCompliance: selectedTemplate === 'enterprise',
                enableSecurity: selectedTemplate === 'enterprise' || selectedTemplate === 'scaleup',
                enableAI: true,
            },
        };

        try {
            const result = await generateMutation.mutateAsync(request);
            setOriginalGeneratedOrg(result);
            setPreviewOrg(JSON.parse(JSON.stringify(result))); // Deep copy for editing
            setStep('preview');
        } catch (error) {
            console.error('Failed to generate organization:', error);
            // Fallback to mock if API fails
            const mockGenerated: GeneratedOrg = {
                id: 'generated-' + Date.now(),
                name: orgName || 'My Organization',
                namespace: (orgName || 'my-org').toLowerCase().replace(/\s+/g, '-'),
                vision: visionToUse,
                departments: template?.departments.map(dept => ({
                    name: dept,
                    type: dept.toUpperCase().replace(/\s+/g, '_'),
                    agents: [
                        { name: `${dept} Lead`, role: 'lead' },
                        { name: `${dept} Engineer 1`, role: 'engineer' },
                    ],
                })) || [],
                norms: [
                    'Code reviews must be completed within 24 hours',
                    'All deployments require QA approval',
                    'No production deployments on Fridays',
                ],
                estimatedAgentCount: template?.agents || 5,
            };
            setOriginalGeneratedOrg(mockGenerated);
            setPreviewOrg(JSON.parse(JSON.stringify(mockGenerated)));
            setStep('preview');
        }
    };

    // Handle refinement (re-generate with new instructions)
    const handleRefine = async (additionalInstruction: string) => {
        if (!additionalInstruction.trim()) return;

        const newVision = `${vision}\n\nRefinement: ${additionalInstruction}`;
        setVision(newVision); // Update state so user sees history if they go back
        setRefinementInput(''); // Clear input
        await handleGenerate(newVision);
    };

    // Client-side edits
    const removeDepartment = (deptIndex: number) => {
        if (!previewOrg) return;
        const newOrg = { ...previewOrg };
        newOrg.departments.splice(deptIndex, 1);
        setPreviewOrg(newOrg);
    };

    const removeAgent = (deptIndex: number, agentIndex: number) => {
        if (!previewOrg) return;
        const newOrg = { ...previewOrg };
        newOrg.departments[deptIndex].agents.splice(agentIndex, 1);
        setPreviewOrg(newOrg);
    };

    const resetPreview = () => {
        if (originalGeneratedOrg) {
            setPreviewOrg(JSON.parse(JSON.stringify(originalGeneratedOrg)));
        }
    };

    // Materialize the organization
    const handleMaterialize = async () => {
        if (!previewOrg) return;

        setStep('materialize');

        try {
            await materializeMutation.mutateAsync(previewOrg);
            // Navigate to dashboard after successful creation
            setTimeout(() => navigate('/dashboard'), 1500);
        } catch (error) {
            console.error('Failed to materialize organization:', error);
            // Navigate anyway in demo mode
            setTimeout(() => navigate('/dashboard'), 1500);
        }
    };

    return (
        <div className="min-h-screen bg-gradient-to-br from-slate-50 to-blue-50 dark:from-slate-900 dark:to-slate-800 flex items-center justify-center p-8">
            <div className="max-w-5xl w-full">
                {/* Header */}
                <div className="text-center mb-10">
                    <div className="inline-flex items-center gap-2 px-4 py-2 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded-full text-sm font-medium mb-4">
                        <Sparkles className="w-4 h-4" />
                        AI-Powered Setup
                    </div>
                    <h1 className="text-4xl font-bold text-slate-900 dark:text-white mb-4">
                        Create Your Virtual Organization
                    </h1>
                    <p className="text-lg text-slate-600 dark:text-slate-300">
                        {step === 'vision' ? 'Describe your vision and let AI architect your team' : 'Review and refine your organization structure'}
                    </p>
                </div>

                {/* Step: Vision Input */}
                {step === 'vision' && (
                    <Card className="p-8 shadow-xl border-0 bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm">
                        <div className="space-y-8">
                            {/* Organization Name */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">
                                    <Building2 className="w-4 h-4 inline mr-2" />
                                    Organization Name
                                </label>
                                <input
                                    type="text"
                                    value={orgName}
                                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setOrgName(e.target.value)}
                                    placeholder="Acme Corp"
                                    className="w-full px-4 py-3 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all"
                                />
                            </div>

                            {/* Vision/Mission */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">
                                    <Target className="w-4 h-4 inline mr-2" />
                                    Describe Your Vision
                                </label>
                                <textarea
                                    value={vision}
                                    onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setVision(e.target.value)}
                                    placeholder="We're building a platform that helps developers ship faster..."
                                    rows={4}
                                    className="w-full px-4 py-3 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all"
                                />
                            </div>

                            {/* Template Selection */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-3">
                                    <Users className="w-4 h-4 inline mr-2" />
                                    Choose a Template
                                </label>
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    {TEMPLATES.map(template => (
                                        <button
                                            key={template.id}
                                            onClick={() => setSelectedTemplate(template.id)}
                                            className={`p-6 rounded-xl border-2 text-left transition-all duration-200 hover:scale-[1.02] ${selectedTemplate === template.id
                                                    ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 shadow-md'
                                                    : 'border-slate-200 dark:border-slate-700 hover:border-blue-300 bg-white dark:bg-slate-900'
                                                }`}
                                        >
                                            <div className="text-3xl mb-3">{template.icon}</div>
                                            <div className="font-bold text-slate-900 dark:text-white mb-1">
                                                {template.name}
                                            </div>
                                            <div className="text-sm text-slate-500 dark:text-slate-400 mb-3">
                                                {template.description}
                                            </div>
                                            <div className="flex items-center gap-2 text-xs font-medium text-slate-400 bg-slate-100 dark:bg-slate-800 px-2 py-1 rounded-md w-fit">
                                                <Users className="w-3 h-3" />
                                                {template.agents} agents
                                            </div>
                                        </button>
                                    ))}
                                </div>
                            </div>

                            {/* Generate Button */}
                            <Button
                                onClick={() => handleGenerate()}
                                disabled={!selectedTemplate || generateMutation.isPending}
                                className="w-full py-6 text-lg font-semibold rounded-xl shadow-lg hover:shadow-xl transition-all flex items-center justify-center gap-2 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700"
                            >
                                {generateMutation.isPending ? (
                                    <>
                                        <Loader2 className="w-5 h-5 animate-spin" />
                                        Architecting Organization...
                                    </>
                                ) : (
                                    <>
                                        <Sparkles className="w-5 h-5" />
                                        Generate Organization
                                        <ArrowRight className="w-5 h-5" />
                                    </>
                                )}
                            </Button>

                            {generateMutation.isError && (
                                <div className="p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl flex items-center gap-3 text-red-700 dark:text-red-400">
                                    <AlertCircle className="w-5 h-5" />
                                    Something went wrong. Please try again.
                                </div>
                            )}
                        </div>
                    </Card>
                )}

                {/* Step: Preview Generated Org */}
                {step === 'preview' && previewOrg && (
                    <div className="space-y-6">
                        {/* Refinement Bar */}
                        <Card className="p-4 bg-white/90 dark:bg-slate-800/90 backdrop-blur sticky top-4 z-10 shadow-lg border-blue-100 dark:border-blue-900">
                            <div className="flex flex-col md:flex-row gap-4 items-center">
                                <div className="flex-1 w-full">
                                    <div className="relative">
                                        <Wand2 className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-blue-500" />
                                        <input
                                            type="text"
                                            value={refinementInput}
                                            onChange={(e) => setRefinementInput(e.target.value)}
                                            onKeyDown={(e) => e.key === 'Enter' && handleRefine(refinementInput)}
                                            placeholder="Refine with AI (e.g., 'Add a mobile team', 'Remove QA', 'Make it flatter')..."
                                            className="w-full pl-10 pr-4 py-2 border border-slate-200 dark:border-slate-700 rounded-lg bg-slate-50 dark:bg-slate-900 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                                        />
                                    </div>
                                </div>
                                <div className="flex gap-2 w-full md:w-auto overflow-x-auto pb-2 md:pb-0">
                                    {SUGGESTIONS.map((s, i) => (
                                        <button
                                            key={i}
                                            onClick={() => handleRefine(s.prompt)}
                                            className="px-3 py-1.5 text-xs font-medium bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-300 rounded-full hover:bg-blue-100 dark:hover:bg-blue-900/50 transition-colors whitespace-nowrap border border-blue-100 dark:border-blue-800"
                                        >
                                            {s.label}
                                        </button>
                                    ))}
                                </div>
                                <Button
                                    onClick={() => handleRefine(refinementInput)}
                                    disabled={!refinementInput.trim() || generateMutation.isPending}
                                    size="sm"
                                    className="whitespace-nowrap"
                                >
                                    {generateMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Refine'}
                                </Button>
                            </div>
                        </Card>

                        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                            {/* Main Content: Departments */}
                            <div className="lg:col-span-2 space-y-4">
                                <div className="flex items-center justify-between">
                                    <h3 className="font-semibold text-slate-700 dark:text-slate-300 flex items-center gap-2">
                                        <Building2 className="w-4 h-4" />
                                        Departments & Agents
                                    </h3>
                                    <button
                                        onClick={resetPreview}
                                        className="text-xs text-slate-500 hover:text-blue-500 flex items-center gap-1"
                                    >
                                        <RefreshCw className="w-3 h-3" /> Reset Changes
                                    </button>
                                </div>

                                <div className="grid grid-cols-1 gap-4">
                                    {previewOrg.departments.map((dept, deptIndex) => (
                                        <Card key={deptIndex} className="p-5 border-l-4 border-l-blue-500 hover:shadow-md transition-shadow group relative">
                                            <button
                                                onClick={() => removeDepartment(deptIndex)}
                                                className="absolute top-3 right-3 text-slate-300 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity"
                                                title="Remove Department"
                                            >
                                                <Trash2 className="w-4 h-4" />
                                            </button>

                                            <div className="mb-4">
                                                <h4 className="text-lg font-bold text-slate-900 dark:text-white">{dept.name}</h4>
                                                <span className="text-xs font-medium px-2 py-0.5 bg-slate-100 dark:bg-slate-800 text-slate-500 rounded">
                                                    {dept.type}
                                                </span>
                                            </div>

                                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                                {dept.agents.map((agent, agentIndex) => (
                                                    <div
                                                        key={agentIndex}
                                                        className="flex items-center justify-between p-2 rounded-lg bg-slate-50 dark:bg-slate-900/50 border border-slate-100 dark:border-slate-800 group/agent"
                                                    >
                                                        <div className="flex items-center gap-3">
                                                            <div className="w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900/50 flex items-center justify-center text-blue-600 dark:text-blue-400 text-xs font-bold">
                                                                {agent.name.charAt(0)}
                                                            </div>
                                                            <div>
                                                                <div className="text-sm font-medium text-slate-900 dark:text-white">{agent.name}</div>
                                                                <div className="text-xs text-slate-500">{agent.role}</div>
                                                            </div>
                                                        </div>
                                                        <button
                                                            onClick={() => removeAgent(deptIndex, agentIndex)}
                                                            className="text-slate-300 hover:text-red-500 opacity-0 group-hover/agent:opacity-100 transition-opacity"
                                                        >
                                                            <X className="w-4 h-4" />
                                                        </button>
                                                    </div>
                                                ))}
                                            </div>
                                        </Card>
                                    ))}
                                </div>
                            </div>

                            {/* Sidebar: Summary & Actions */}
                            <div className="space-y-6">
                                <Card className="p-6 sticky top-24">
                                    <h3 className="font-semibold text-slate-900 dark:text-white mb-4">Organization Summary</h3>

                                    <div className="space-y-4 mb-6">
                                        <div className="flex justify-between text-sm">
                                            <span className="text-slate-500">Name</span>
                                            <span className="font-medium text-slate-900 dark:text-white">{previewOrg.name}</span>
                                        </div>
                                        <div className="flex justify-between text-sm">
                                            <span className="text-slate-500">Departments</span>
                                            <span className="font-medium text-slate-900 dark:text-white">{previewOrg.departments.length}</span>
                                        </div>
                                        <div className="flex justify-between text-sm">
                                            <span className="text-slate-500">Total Agents</span>
                                            <span className="font-medium text-slate-900 dark:text-white">
                                                {previewOrg.departments.reduce((acc, d) => acc + d.agents.length, 0)}
                                            </span>
                                        </div>
                                    </div>

                                    <div className="space-y-3">
                                        <Button
                                            onClick={handleMaterialize}
                                            disabled={materializeMutation.isPending}
                                            className="w-full py-6 text-lg font-semibold shadow-lg hover:shadow-xl transition-all flex items-center justify-center gap-2 bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-700 hover:to-emerald-700"
                                        >
                                            {materializeMutation.isPending ? (
                                                <>
                                                    <Loader2 className="w-5 h-5 animate-spin" />
                                                    Creating...
                                                </>
                                            ) : (
                                                <>
                                                    <Check className="w-5 h-5" />
                                                    Confirm & Create
                                                </>
                                            )}
                                        </Button>

                                        <Button
                                            variant="outline"
                                            onClick={() => setStep('vision')}
                                            className="w-full"
                                        >
                                            Back to Vision
                                        </Button>
                                    </div>

                                    <div className="mt-6 pt-6 border-t border-slate-100 dark:border-slate-800">
                                        <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">
                                            Operating Norms
                                        </h4>
                                        <ul className="space-y-2">
                                            {previewOrg.norms.slice(0, 3).map((norm, i) => (
                                                <li key={i} className="text-xs text-slate-600 dark:text-slate-400 flex items-start gap-2">
                                                    <Check className="w-3 h-3 text-green-500 mt-0.5 flex-shrink-0" />
                                                    {norm}
                                                </li>
                                            ))}
                                            {previewOrg.norms.length > 3 && (
                                                <li className="text-xs text-slate-400 italic">
                                                    + {previewOrg.norms.length - 3} more norms
                                                </li>
                                            )}
                                        </ul>
                                    </div>
                                </Card>
                            </div>
                        </div>
                    </div>
                )}

                {/* Step: Materializing */}
                {step === 'materialize' && (
                    <Card className="p-12 text-center max-w-xl mx-auto shadow-2xl">
                        <div className="relative w-24 h-24 mx-auto mb-8">
                            <div className="absolute inset-0 border-4 border-blue-100 rounded-full animate-pulse"></div>
                            <div className="absolute inset-0 border-4 border-t-blue-500 rounded-full animate-spin"></div>
                            <Sparkles className="absolute inset-0 m-auto w-8 h-8 text-blue-500 animate-bounce" />
                        </div>
                        <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-4">
                            Materializing Your Organization
                        </h2>
                        <p className="text-lg text-slate-600 dark:text-slate-400 mb-8">
                            Our AI is provisioning databases, setting up agent runtimes, and configuring communication channels...
                        </p>
                        <div className="flex justify-center gap-2">
                            <div className="w-2 h-2 bg-blue-500 rounded-full animate-bounce [animation-delay:-0.3s]"></div>
                            <div className="w-2 h-2 bg-blue-500 rounded-full animate-bounce [animation-delay:-0.15s]"></div>
                            <div className="w-2 h-2 bg-blue-500 rounded-full animate-bounce"></div>
                        </div>
                    </Card>
                )}
            </div>
        </div>
    );
}
