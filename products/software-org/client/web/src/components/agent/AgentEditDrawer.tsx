/**
 * Agent Edit Drawer
 *
 * Comprehensive drawer for editing all agent properties including
 * capabilities, personality, model configuration, and system prompt.
 *
 * @doc.type component
 * @doc.purpose Edit agent properties
 * @doc.layer product
 * @doc.pattern Form Drawer
 */

import { useState, useEffect } from 'react';
import {
    Bot,
    Loader2,
    Zap,
    Brain,
    Settings,
    FileText,
    Shield,
    Plus,
    X,
    Save,
} from 'lucide-react';
import { Drawer, FormField, Input, Textarea, Select } from '@/components/admin';
import { Button } from '@/components/ui';

// Agent data types
interface AgentPersonality {
    temperature: number;
    creativity: number;
    assertiveness: number;
}

interface AgentModel {
    id: string;
    provider?: string;
    maxTokens: number;
}

export interface AgentData {
    id: string;
    name: string;
    role: string;
    description?: string;
    department?: string;
    status?: string;
    capabilities: string[];
    personality?: AgentPersonality;
    model?: AgentModel;
    systemPrompt?: string;
    permissions?: string[];
}

interface AgentEditDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    agent: AgentData | null;
    onSave: (agent: AgentData) => Promise<void>;
    isLoading?: boolean;
}

type EditTab = 'general' | 'capabilities' | 'personality' | 'model' | 'prompt' | 'permissions';

const TABS: { id: EditTab; label: string; icon: React.ElementType }[] = [
    { id: 'general', label: 'General', icon: Bot },
    { id: 'capabilities', label: 'Capabilities', icon: Zap },
    { id: 'personality', label: 'Personality', icon: Brain },
    { id: 'model', label: 'Model', icon: Settings },
    { id: 'prompt', label: 'System Prompt', icon: FileText },
    { id: 'permissions', label: 'Permissions', icon: Shield },
];

const MODEL_OPTIONS = [
    { value: 'gpt-4-turbo', label: 'GPT-4 Turbo' },
    { value: 'gpt-4', label: 'GPT-4' },
    { value: 'gpt-3.5-turbo', label: 'GPT-3.5 Turbo' },
    { value: 'claude-3-opus', label: 'Claude 3 Opus' },
    { value: 'claude-3-sonnet', label: 'Claude 3 Sonnet' },
    { value: 'claude-3-haiku', label: 'Claude 3 Haiku' },
];

const ROLE_OPTIONS = [
    { value: 'automation', label: 'Automation' },
    { value: 'analysis', label: 'Analysis' },
    { value: 'monitoring', label: 'Monitoring' },
    { value: 'security', label: 'Security' },
    { value: 'deployment', label: 'Deployment' },
    { value: 'testing', label: 'Testing' },
    { value: 'support', label: 'Support' },
    { value: 'triage', label: 'Triage' },
];

const CAPABILITY_SUGGESTIONS = [
    'compile', 'package', 'deploy', 'rollback', 'health-check',
    'vulnerability-scan', 'sast', 'dast', 'dependency-audit',
    'incident-analysis', 'context-gathering', 'severity-assessment',
    'code-review', 'test-generation', 'documentation', 'monitoring',
];

const PERMISSION_SUGGESTIONS = [
    'read:code', 'write:code', 'read:artifacts', 'write:artifacts',
    'execute:builds', 'execute:deploys', 'execute:rollbacks',
    'read:logs', 'read:metrics', 'read:incidents', 'write:reports',
    'read:dependencies', 'execute:scans', 'write:assessments',
];

function SliderField({
    label,
    value,
    onChange,
    helpText,
}: {
    label: string;
    value: number;
    onChange: (value: number) => void;
    helpText?: string;
}) {
    return (
        <div className="space-y-2">
            <div className="flex justify-between items-center">
                <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
                    {label}
                </label>
                <span className="text-sm font-medium text-blue-600 dark:text-blue-400">
                    {(value * 100).toFixed(0)}%
                </span>
            </div>
            <input
                type="range"
                min="0"
                max="100"
                value={value * 100}
                onChange={(e) => onChange(parseInt(e.target.value) / 100)}
                className="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-lg appearance-none cursor-pointer accent-blue-600"
            />
            {helpText && (
                <p className="text-xs text-gray-500 dark:text-gray-400">{helpText}</p>
            )}
        </div>
    );
}

function TagInput({
    label,
    tags,
    onChange,
    suggestions,
    placeholder,
}: {
    label: string;
    tags: string[];
    onChange: (tags: string[]) => void;
    suggestions?: string[];
    placeholder?: string;
}) {
    const [inputValue, setInputValue] = useState('');
    const [showSuggestions, setShowSuggestions] = useState(false);

    const filteredSuggestions = suggestions?.filter(
        (s) =>
            !tags.includes(s) &&
            s.toLowerCase().includes(inputValue.toLowerCase())
    );

    const addTag = (tag: string) => {
        if (tag && !tags.includes(tag)) {
            onChange([...tags, tag]);
        }
        setInputValue('');
        setShowSuggestions(false);
    };

    const removeTag = (tag: string) => {
        onChange(tags.filter((t) => t !== tag));
    };

    return (
        <div className="space-y-2">
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                {label}
            </label>
            <div className="relative">
                <div className="flex flex-wrap gap-2 p-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 min-h-[42px]">
                    {tags.map((tag) => (
                        <span
                            key={tag}
                            className="inline-flex items-center gap-1 px-2 py-1 text-sm bg-blue-100 dark:bg-blue-900/50 text-blue-700 dark:text-blue-300 rounded-md"
                        >
                            {tag}
                            <button
                                type="button"
                                onClick={() => removeTag(tag)}
                                className="hover:text-blue-900 dark:hover:text-blue-100"
                            >
                                <X className="w-3 h-3" />
                            </button>
                        </span>
                    ))}
                    <input
                        type="text"
                        value={inputValue}
                        onChange={(e) => {
                            setInputValue(e.target.value);
                            setShowSuggestions(true);
                        }}
                        onFocus={() => setShowSuggestions(true)}
                        onKeyDown={(e) => {
                            if (e.key === 'Enter' && inputValue) {
                                e.preventDefault();
                                addTag(inputValue);
                            }
                        }}
                        placeholder={tags.length === 0 ? placeholder : ''}
                        className="flex-1 min-w-[120px] bg-transparent outline-none text-sm text-gray-900 dark:text-gray-100"
                    />
                </div>
                {showSuggestions && filteredSuggestions && filteredSuggestions.length > 0 && (
                    <div className="absolute z-10 w-full mt-1 bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-lg shadow-lg max-h-40 overflow-y-auto">
                        {filteredSuggestions.slice(0, 8).map((suggestion) => (
                            <button
                                key={suggestion}
                                type="button"
                                onClick={() => addTag(suggestion)}
                                className="w-full px-3 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-slate-700 text-gray-900 dark:text-gray-100"
                            >
                                <Plus className="w-3 h-3 inline mr-2 text-gray-400" />
                                {suggestion}
                            </button>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}

export function AgentEditDrawer({
    isOpen,
    onClose,
    agent,
    onSave,
    isLoading = false,
}: AgentEditDrawerProps) {
    const [activeTab, setActiveTab] = useState<EditTab>('general');
    const [formData, setFormData] = useState<AgentData | null>(null);
    const [isSaving, setIsSaving] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});

    // Initialize form data when agent changes
    useEffect(() => {
        if (agent) {
            setFormData({
                ...agent,
                personality: agent.personality || {
                    temperature: 0.5,
                    creativity: 0.5,
                    assertiveness: 0.5,
                },
                model: agent.model || {
                    id: 'gpt-4-turbo',
                    provider: 'OpenAI',
                    maxTokens: 4096,
                },
                capabilities: agent.capabilities || [],
                permissions: agent.permissions || [],
                systemPrompt: agent.systemPrompt || '',
            });
        }
    }, [agent]);

    const handleSave = async () => {
        if (!formData) return;

        // Validate
        const newErrors: Record<string, string> = {};
        if (!formData.name?.trim()) {
            newErrors.name = 'Name is required';
        }
        if (!formData.role?.trim()) {
            newErrors.role = 'Role is required';
        }

        if (Object.keys(newErrors).length > 0) {
            setErrors(newErrors);
            setActiveTab('general');
            return;
        }

        setIsSaving(true);
        try {
            await onSave(formData);
            onClose();
        } catch (error) {
            setErrors({
                submit: error instanceof Error ? error.message : 'Failed to save agent',
            });
        } finally {
            setIsSaving(false);
        }
    };

    const handleClose = () => {
        setErrors({});
        setActiveTab('general');
        onClose();
    };

    if (!formData) return null;

    return (
        <Drawer isOpen={isOpen} onClose={handleClose} title={`Edit Agent: ${agent?.name}`} size="lg">
            <div className="flex flex-col h-full">
                {/* Tab Navigation */}
                <div className="flex gap-1 p-1 bg-gray-100 dark:bg-slate-800 rounded-lg mb-6 overflow-x-auto">
                    {TABS.map((tab) => {
                        const Icon = tab.icon;
                        return (
                            <button
                                key={tab.id}
                                type="button"
                                onClick={() => setActiveTab(tab.id)}
                                className={`flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium whitespace-nowrap transition-colors ${activeTab === tab.id
                                        ? 'bg-white dark:bg-slate-700 text-blue-600 dark:text-blue-400 shadow-sm'
                                        : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
                                    }`}
                            >
                                <Icon className="w-4 h-4" />
                                {tab.label}
                            </button>
                        );
                    })}
                </div>

                {/* Tab Content */}
                <div className="flex-1 overflow-y-auto space-y-6">
                    {/* General Tab */}
                    {activeTab === 'general' && (
                        <div className="space-y-4">
                            <FormField
                                label="Agent Name"
                                name="name"
                                required
                                error={errors.name}
                            >
                                <Input
                                    value={formData.name}
                                    onChange={(e) =>
                                        setFormData({ ...formData, name: e.target.value })
                                    }
                                    error={!!errors.name}
                                />
                            </FormField>

                            <FormField label="Role" name="role" required error={errors.role}>
                                <Select
                                    value={formData.role}
                                    onChange={(e) =>
                                        setFormData({ ...formData, role: e.target.value })
                                    }
                                    options={ROLE_OPTIONS}
                                    error={!!errors.role}
                                />
                            </FormField>

                            <FormField label="Description" name="description">
                                <Textarea
                                    value={formData.description || ''}
                                    onChange={(e) =>
                                        setFormData({ ...formData, description: e.target.value })
                                    }
                                    rows={3}
                                    placeholder="What does this agent do?"
                                />
                            </FormField>
                        </div>
                    )}

                    {/* Capabilities Tab */}
                    {activeTab === 'capabilities' && (
                        <div className="space-y-4">
                            <div className="p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
                                <p className="text-sm text-blue-700 dark:text-blue-300">
                                    Define what actions this agent can perform. Capabilities determine
                                    what tasks can be assigned to this agent.
                                </p>
                            </div>
                            <TagInput
                                label="Capabilities"
                                tags={formData.capabilities}
                                onChange={(capabilities) =>
                                    setFormData({ ...formData, capabilities })
                                }
                                suggestions={CAPABILITY_SUGGESTIONS}
                                placeholder="Add capabilities..."
                            />
                        </div>
                    )}

                    {/* Personality Tab */}
                    {activeTab === 'personality' && (
                        <div className="space-y-6">
                            <div className="p-4 bg-purple-50 dark:bg-purple-900/20 rounded-lg">
                                <p className="text-sm text-purple-700 dark:text-purple-300">
                                    Adjust the agent's behavioral parameters to fine-tune how it
                                    responds and makes decisions.
                                </p>
                            </div>
                            <SliderField
                                label="Temperature"
                                value={formData.personality?.temperature || 0.5}
                                onChange={(temperature) =>
                                    setFormData({
                                        ...formData,
                                        personality: { ...formData.personality!, temperature },
                                    })
                                }
                                helpText="Higher values make output more random, lower values more deterministic"
                            />
                            <SliderField
                                label="Creativity"
                                value={formData.personality?.creativity || 0.5}
                                onChange={(creativity) =>
                                    setFormData({
                                        ...formData,
                                        personality: { ...formData.personality!, creativity },
                                    })
                                }
                                helpText="How inventive and novel the agent's responses should be"
                            />
                            <SliderField
                                label="Assertiveness"
                                value={formData.personality?.assertiveness || 0.5}
                                onChange={(assertiveness) =>
                                    setFormData({
                                        ...formData,
                                        personality: { ...formData.personality!, assertiveness },
                                    })
                                }
                                helpText="How confidently the agent makes decisions and recommendations"
                            />
                        </div>
                    )}

                    {/* Model Tab */}
                    {activeTab === 'model' && (
                        <div className="space-y-4">
                            <div className="p-4 bg-emerald-50 dark:bg-emerald-900/20 rounded-lg">
                                <p className="text-sm text-emerald-700 dark:text-emerald-300">
                                    Configure the underlying AI model that powers this agent.
                                </p>
                            </div>
                            <FormField label="Model" name="modelId">
                                <Select
                                    value={formData.model?.id || 'gpt-4-turbo'}
                                    onChange={(e) =>
                                        setFormData({
                                            ...formData,
                                            model: { ...formData.model!, id: e.target.value },
                                        })
                                    }
                                    options={MODEL_OPTIONS}
                                />
                            </FormField>
                            <FormField
                                label="Max Tokens"
                                name="maxTokens"
                                helpText="Maximum number of tokens in the response"
                            >
                                <Input
                                    type="number"
                                    value={formData.model?.maxTokens || 4096}
                                    onChange={(e) =>
                                        setFormData({
                                            ...formData,
                                            model: {
                                                ...formData.model!,
                                                maxTokens: parseInt(e.target.value) || 4096,
                                            },
                                        })
                                    }
                                    min={256}
                                    max={128000}
                                />
                            </FormField>
                        </div>
                    )}

                    {/* System Prompt Tab */}
                    {activeTab === 'prompt' && (
                        <div className="space-y-4">
                            <div className="p-4 bg-amber-50 dark:bg-amber-900/20 rounded-lg">
                                <p className="text-sm text-amber-700 dark:text-amber-300">
                                    The system prompt defines the agent's core behavior, personality,
                                    and constraints. This is sent with every interaction.
                                </p>
                            </div>
                            <FormField label="System Prompt" name="systemPrompt">
                                <Textarea
                                    value={formData.systemPrompt || ''}
                                    onChange={(e) =>
                                        setFormData({ ...formData, systemPrompt: e.target.value })
                                    }
                                    rows={12}
                                    placeholder="You are a helpful AI agent that..."
                                    className="font-mono text-sm"
                                />
                            </FormField>
                        </div>
                    )}

                    {/* Permissions Tab */}
                    {activeTab === 'permissions' && (
                        <div className="space-y-4">
                            <div className="p-4 bg-red-50 dark:bg-red-900/20 rounded-lg">
                                <p className="text-sm text-red-700 dark:text-red-300">
                                    Control what resources and actions this agent has access to.
                                    Be careful when granting write or execute permissions.
                                </p>
                            </div>
                            <TagInput
                                label="Permissions"
                                tags={formData.permissions || []}
                                onChange={(permissions) =>
                                    setFormData({ ...formData, permissions })
                                }
                                suggestions={PERMISSION_SUGGESTIONS}
                                placeholder="Add permissions..."
                            />
                        </div>
                    )}
                </div>

                {/* Error Message */}
                {errors.submit && (
                    <div className="mt-4 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
                        <p className="text-sm text-red-600 dark:text-red-400">{errors.submit}</p>
                    </div>
                )}

                {/* Actions */}
                <div className="flex items-center justify-end gap-3 pt-6 mt-6 border-t border-gray-200 dark:border-slate-700">
                    <Button
                        type="button"
                        variant="ghost"
                        onClick={handleClose}
                        disabled={isSaving}
                    >
                        Cancel
                    </Button>
                    <Button
                        type="button"
                        onClick={handleSave}
                        disabled={isSaving || isLoading}
                        className="flex items-center gap-2"
                    >
                        {isSaving ? (
                            <>
                                <Loader2 className="w-4 h-4 animate-spin" />
                                Saving...
                            </>
                        ) : (
                            <>
                                <Save className="w-4 h-4" />
                                Save Changes
                            </>
                        )}
                    </Button>
                </div>
            </div>
        </Drawer>
    );
}

export default AgentEditDrawer;
