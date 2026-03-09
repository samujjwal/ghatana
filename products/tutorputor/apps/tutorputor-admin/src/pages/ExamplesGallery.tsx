/**
 * Examples Gallery Page
 * 
 * Displays and manages content examples across domains.
 * Provides gallery view and CRUD operations for examples.
 * 
 * @doc.type component
 * @doc.purpose Gallery and management interface for content examples
 * @doc.layer product
 * @doc.pattern Page
 */

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from '../components/ui';
import { Input, Button, Spinner, TextArea } from '@ghatana/ui';

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

interface CreateExampleInput {
    title: string;
    exampleText: string;
    exampleType: 'TEXT' | 'PROBLEM' | 'SOLUTION' | 'REAL_WORLD';
    difficulty: 'BASIC' | 'INTERMEDIATE' | 'ADVANCED';
    tags: string[];
}

export function ExamplesGallery() {
    const queryClient = useQueryClient();
    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState<string | null>(null);
    const [selectedConceptId, setSelectedConceptId] = useState('');
    const [formData, setFormData] = useState<CreateExampleInput>({
        title: '',
        exampleText: '',
        exampleType: 'TEXT',
        difficulty: 'INTERMEDIATE',
        tags: [],
    });
    const [tagInput, setTagInput] = useState('');

    // Fetch examples
    const { data: examplesData, isLoading } = useQuery({
        queryKey: ['examples'],
        queryFn: async () => {
            const res = await fetch('/admin/api/v1/content/examples');
            if (!res.ok) throw new Error('Failed to fetch examples');
            const json = await res.json();
            return json.examples as ContentExample[];
        },
    });

    // Create mutation
    const createMutation = useMutation({
        mutationFn: async (data: CreateExampleInput) => {
            const res = await fetch(
                `/admin/api/v1/content/concepts/${selectedConceptId}/examples`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data),
                }
            );
            if (!res.ok) throw new Error('Failed to create example');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['examples'] });
            setShowForm(false);
            resetForm();
        },
    });

    // Update mutation
    const updateMutation = useMutation({
        mutationFn: async (data: CreateExampleInput) => {
            const res = await fetch(
                `/admin/api/v1/content/examples/${editingId}`,
                {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data),
                }
            );
            if (!res.ok) throw new Error('Failed to update example');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['examples'] });
            setShowForm(false);
            setEditingId(null);
            resetForm();
        },
    });

    // Delete mutation
    const deleteMutation = useMutation({
        mutationFn: async (id: string) => {
            const res = await fetch(`/admin/api/v1/content/examples/${id}`, {
                method: 'DELETE',
            });
            if (!res.ok) throw new Error('Failed to delete example');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['examples'] });
        },
    });

    const resetForm = () => {
        setFormData({
            title: '',
            exampleText: '',
            exampleType: 'TEXT',
            difficulty: 'INTERMEDIATE',
            tags: [],
        });
        setTagInput('');
        setSelectedConceptId('');
    };

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
        if (!selectedConceptId && !editingId) {
            alert('Please select a concept');
            return;
        }
        if (editingId) {
            updateMutation.mutate(formData);
        } else {
            createMutation.mutate(formData);
        }
    };

    const handleEdit = (example: ContentExample) => {
        setEditingId(example.id);
        setSelectedConceptId(example.conceptId);
        setFormData({
            title: example.title,
            exampleText: example.exampleText,
            exampleType: example.exampleType,
            difficulty: example.difficulty,
            tags: example.tags,
        });
        setShowForm(true);
    };

    const handleCancel = () => {
        setShowForm(false);
        setEditingId(null);
        resetForm();
    };

    if (isLoading) {
        return <Spinner />;
    }

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-3xl font-bold">Examples Gallery</h1>
                <Button onClick={() => setShowForm(true)}>Add New Example</Button>
            </div>

            {showForm && (
                <Card className="p-6">
                    <h2 className="text-xl font-semibold mb-4">
                        {editingId ? 'Edit Example' : 'Create New Example'}
                    </h2>
                    <form onSubmit={handleSubmit} className="space-y-4">
                        {!editingId && (
                            <div>
                                <label className="block text-sm font-medium mb-1">
                                    Concept ID
                                </label>
                                <Input
                                    value={selectedConceptId}
                                    onChange={(e) => setSelectedConceptId(e.target.value)}
                                    placeholder="Enter concept ID"
                                />
                            </div>
                        )}

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
                                rows={4}
                            />
                        </div>

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

                        <div className="flex gap-2">
                            <Button
                                type="submit"
                                disabled={
                                    createMutation.isPending || updateMutation.isPending
                                }
                            >
                                {editingId ? 'Update' : 'Create'}
                            </Button>
                            <Button type="button" variant="outline" onClick={handleCancel}>
                                Cancel
                            </Button>
                        </div>
                    </form>
                </Card>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {examplesData?.map((example) => (
                    <Card key={example.id} className="p-4 hover:shadow-lg transition-shadow">
                        <div className="space-y-2">
                            <div className="flex items-start justify-between">
                                <h3 className="font-semibold text-lg">{example.title}</h3>
                                <span
                                    className={`text-xs font-medium px-2 py-1 rounded ${example.status === 'PUBLISHED'
                                        ? 'bg-green-100 text-green-800'
                                        : 'bg-yellow-100 text-yellow-800'
                                        }`}
                                >
                                    {example.status}
                                </span>
                            </div>

                            <p className="text-sm text-gray-600 line-clamp-2">
                                {example.exampleText}
                            </p>

                            <div className="flex gap-2 flex-wrap">
                                <span className="text-xs bg-blue-100 text-blue-800 px-2 py-1 rounded">
                                    {example.exampleType}
                                </span>
                                <span className="text-xs bg-gray-100 text-gray-800 px-2 py-1 rounded">
                                    {example.difficulty}
                                </span>
                            </div>

                            {example.tags.length > 0 && (
                                <div className="flex flex-wrap gap-1">
                                    {example.tags.map((tag) => (
                                        <span
                                            key={tag}
                                            className="text-xs bg-purple-100 text-purple-800 px-1 py-0.5 rounded"
                                        >
                                            {tag}
                                        </span>
                                    ))}
                                </div>
                            )}

                            <div className="flex gap-1 pt-2">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => handleEdit(example)}
                                >
                                    Edit
                                </Button>
                                <Button
                                    variant="danger"
                                    size="sm"
                                    onClick={() => {
                                        if (window.confirm('Delete this example?')) {
                                            deleteMutation.mutate(example.id);
                                        }
                                    }}
                                    disabled={deleteMutation.isPending}
                                >
                                    Delete
                                </Button>
                            </div>
                        </div>
                    </Card>
                ))}
            </div>
        </div>
    );
}

export default ExamplesGallery;
