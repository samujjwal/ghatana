/**
 * Example Editor Component
 * 
 * Specialized component for editing individual content examples.
 * Provides detailed form with rich editing capabilities and preview.
 * 
 * @doc.type component
 * @doc.purpose Individual example editing with detailed configuration
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from '../components/ui';
import { Input, Button, TextArea } from '@ghatana/ui';

interface ContentExample {
    id: string;
    conceptId: string;
    title: string;
    exampleText: string;
    exampleType: 'TEXT' | 'PROBLEM' | 'SOLUTION' | 'REAL_WORLD';
    difficulty: 'BASIC' | 'INTERMEDIATE' | 'ADVANCED';
    tags: string[];
    status: 'DRAFT' | 'PUBLISHED';
    createdAt: string;
    updatedAt: string;
}

interface ExampleEditorProps {
    example: ContentExample;
    onClose?: () => void;
    onSave?: (example: ContentExample) => void;
}

interface UpdateExampleInput {
    title: string;
    exampleText: string;
    exampleType: 'TEXT' | 'PROBLEM' | 'SOLUTION' | 'REAL_WORLD';
    difficulty: 'BASIC' | 'INTERMEDIATE' | 'ADVANCED';
    tags: string[];
}

export function ExampleEditor({
    example,
    onClose,
    onSave,
}: ExampleEditorProps) {
    const queryClient = useQueryClient();
    const [formData, setFormData] = useState<UpdateExampleInput>({
        title: example.title,
        exampleText: example.exampleText,
        exampleType: example.exampleType,
        difficulty: example.difficulty,
        tags: example.tags,
    });
    const [tagInput, setTagInput] = useState('');
    const [showPreview, setShowPreview] = useState(false);

    // Update mutation
    const updateMutation = useMutation({
        mutationFn: async (data: UpdateExampleInput) => {
            const res = await fetch(`/admin/api/v1/content/examples/${example.id}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data),
            });
            if (!res.ok) throw new Error('Failed to update example');
            return res.json();
        },
        onSuccess: (result) => {
            queryClient.invalidateQueries({ queryKey: ['examples'] });
            queryClient.invalidateQueries({
                queryKey: ['examples', example.id],
            });
            if (onSave) {
                onSave(result);
            }
        },
    });

    const handleAddTag = () => {
        if (tagInput.trim()) {
            setFormData({
                ...formData,
                tags: [...formData.tags, tagInput.trim()],
            });
            setTagInput('');
        }
    };

    const handleRemoveTag = (index: number) => {
        setFormData({
            ...formData,
            tags: formData.tags.filter((_, i) => i !== index),
        });
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        updateMutation.mutate(formData);
    };

    return (
        <Card className="p-6">
            <div className="flex justify-between items-center mb-4">
                <h2 className="text-2xl font-bold">Edit Example</h2>
                <div className="flex gap-2">
                    {!showPreview && (
                        <Button
                            variant="outline"
                            onClick={() => setShowPreview(true)}
                        >
                            Preview
                        </Button>
                    )}
                    {onClose && (
                        <Button variant="outline" onClick={onClose}>
                            Close
                        </Button>
                    )}
                </div>
            </div>

            {showPreview ? (
                <div className="space-y-4">
                    <Card className="p-4 bg-gray-50">
                        <div className="space-y-3">
                            <div>
                                <p className="text-xs text-gray-500 mb-1">TITLE</p>
                                <h3 className="text-xl font-semibold">{formData.title}</h3>
                            </div>

                            <div>
                                <p className="text-xs text-gray-500 mb-1">CONTENT</p>
                                <p className="text-gray-700 whitespace-pre-wrap">
                                    {formData.exampleText}
                                </p>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <p className="text-xs text-gray-500 mb-1">TYPE</p>
                                    <p className="font-medium">{formData.exampleType}</p>
                                </div>
                                <div>
                                    <p className="text-xs text-gray-500 mb-1">DIFFICULTY</p>
                                    <p className="font-medium">{formData.difficulty}</p>
                                </div>
                            </div>

                            {formData.tags.length > 0 && (
                                <div>
                                    <p className="text-xs text-gray-500 mb-1">TAGS</p>
                                    <div className="flex flex-wrap gap-2">
                                        {formData.tags.map((tag) => (
                                            <span
                                                key={tag}
                                                className="bg-purple-100 text-purple-800 px-2 py-1 rounded text-sm"
                                            >
                                                {tag}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    </Card>

                    <Button
                        variant="outline"
                        onClick={() => setShowPreview(false)}
                        className="w-full"
                    >
                        Back to Edit
                    </Button>
                </div>
            ) : (
                <form onSubmit={handleSubmit} className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium mb-1">Title</label>
                        <Input
                            value={formData.title}
                            onChange={(e) =>
                                setFormData({ ...formData, title: e.target.value })
                            }
                            placeholder="Example title"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium mb-1">
                            Example Text
                        </label>
                        <TextArea
                            value={formData.exampleText}
                            onChange={(e) =>
                                setFormData({ ...formData, exampleText: e.target.value })
                            }
                            placeholder="Detailed example content"
                            rows={6}
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium mb-1">
                                Example Type
                            </label>
                            <select
                                value={formData.exampleType}
                                onChange={(e) =>
                                    setFormData({
                                        ...formData,
                                        exampleType: e.target.value as any,
                                    })
                                }
                                className="w-full p-2 border rounded"
                            >
                                <option value="TEXT">Text</option>
                                <option value="PROBLEM">Problem</option>
                                <option value="SOLUTION">Solution</option>
                                <option value="REAL_WORLD">Real World</option>
                            </select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-1">
                                Difficulty
                            </label>
                            <select
                                value={formData.difficulty}
                                onChange={(e) =>
                                    setFormData({
                                        ...formData,
                                        difficulty: e.target.value as any,
                                    })
                                }
                                className="w-full p-2 border rounded"
                            >
                                <option value="BASIC">Basic</option>
                                <option value="INTERMEDIATE">Intermediate</option>
                                <option value="ADVANCED">Advanced</option>
                            </select>
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium mb-1">Tags</label>
                        <div className="flex gap-2 mb-2">
                            <Input
                                value={tagInput}
                                onChange={(e) => setTagInput(e.target.value)}
                                placeholder="Add a tag"
                                onKeyPress={(e) => {
                                    if (e.key === 'Enter') {
                                        e.preventDefault();
                                        handleAddTag();
                                    }
                                }}
                            />
                            <Button type="button" onClick={handleAddTag}>
                                Add
                            </Button>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {formData.tags.map((tag, i) => (
                                <div
                                    key={i}
                                    className="bg-purple-100 text-purple-800 px-2 py-1 rounded text-sm flex items-center gap-1"
                                >
                                    {tag}
                                    <button
                                        type="button"
                                        onClick={() => handleRemoveTag(i)}
                                        className="text-purple-600 hover:text-purple-800"
                                    >
                                        ×
                                    </button>
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="flex gap-2 pt-4 border-t">
                        <Button
                            type="submit"
                            disabled={updateMutation.isPending}
                            className="flex-1"
                        >
                            Save Changes
                        </Button>
                        {onClose && (
                            <Button
                                type="button"
                                variant="outline"
                                onClick={onClose}
                                className="flex-1"
                            >
                                Cancel
                            </Button>
                        )}
                    </div>
                </form>
            )}

            {updateMutation.error && (
                <div className="mt-4 p-3 bg-red-100 text-red-800 rounded">
                    {(updateMutation.error as Error).message}
                </div>
            )}
        </Card>
    );
}

export default ExampleEditor;
