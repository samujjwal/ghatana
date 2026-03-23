/**
 * Module Metadata Form Component
 * 
 * Form for basic module metadata (title, description, domain, etc.)
 * 
 * @doc.type component
 * @doc.purpose Module metadata form
 * @doc.layer product
 * @doc.pattern Component
 */

import { Card, Text, Input, Select } from "@/components/ui";
import { DOMAIN_OPTIONS, DIFFICULTY_OPTIONS } from "../constants";

interface ModuleMetadataFormProps {
    draft: {
        title: string;
        description: string;
        domain: string;
        difficulty: string;
        estimatedTimeMinutes: number;
    };
    onChange: (updates: Partial<{
        title: string;
        description: string;
        domain: string;
        difficulty: string;
        estimatedTimeMinutes: number;
    }>) => void;
}

export function ModuleMetadataForm({ draft, onChange }: ModuleMetadataFormProps) {
    return (
        <Card className="p-6 mb-6">
            <Text className="text-lg font-medium text-gray-900 mb-4">Basic Information</Text>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                        Module Title
                    </label>
                    <Input
                        value={draft.title}
                        onChange={(e) => onChange({ title: e.target.value })}
                        placeholder="Enter module title"
                        className="w-full"
                    />
                </div>
                
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                        Domain
                    </label>
                    <Select
                        value={draft.domain}
                        onChange={(e) => onChange({ domain: e.target.value })}
                        className="w-full"
                    >
                        {DOMAIN_OPTIONS.map((domain) => (
                            <option key={domain} value={domain}>
                                {domain.replace("_", " ").replace(/\b\w/g, l => l.toUpperCase())}
                            </option>
                        ))}
                    </Select>
                </div>
                
                <div className="md:col-span-2">
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                        Description
                    </label>
                    <textarea
                        value={draft.description}
                        onChange={(e) => onChange({ description: e.target.value })}
                        placeholder="Describe what students will learn in this module"
                        rows={3}
                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                    />
                </div>
                
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                        Difficulty Level
                    </label>
                    <Select
                        value={draft.difficulty}
                        onChange={(e) => onChange({ difficulty: e.target.value })}
                        className="w-full"
                    >
                        {DIFFICULTY_OPTIONS.map((difficulty) => (
                            <option key={difficulty} value={difficulty}>
                                {difficulty.charAt(0).toUpperCase() + difficulty.slice(1)}
                            </option>
                        ))}
                    </Select>
                </div>
                
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                        Estimated Time (minutes)
                    </label>
                    <Input
                        type="number"
                        value={draft.estimatedTimeMinutes}
                        onChange={(e) => onChange({ estimatedTimeMinutes: parseInt(e.target.value) || 30 })}
                        min="1"
                        max="480"
                        className="w-full"
                    />
                </div>
            </div>
        </Card>
    );
}
