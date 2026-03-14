/**
 * Concept Editor Component
 * 
 * Allows editing and management of concepts within a domain.
 * Supports adding relationships between concepts and domain associations.
 * 
 * @doc.type component
 * @doc.purpose Concept management within domain authoring
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from '../components/ui';
import { Input, Button, Spinner } from '@ghatana/design-system';
import { RichTextEditor } from './RichTextEditor';

interface Concept {
    id: string;
    domainId: string;
    conceptName: string;
    definition: string;
    keywords: string[];
    difficulty: 'BASIC' | 'INTERMEDIATE' | 'ADVANCED';
    relatedConceptIds: string[];
    createdAt: string;
    updatedAt: string;
}

interface ConceptEditorProps {
    domainId: string;
    onClose?: () => void;
}

interface CreateConceptInput {
    conceptName: string;
    definition: string;
    difficulty: 'BASIC' | 'INTERMEDIATE' | 'ADVANCED';
    keywords: string[];
    relatedConceptIds: string[];
}

export function ConceptEditor({ domainId, onClose }: ConceptEditorProps) {
    const queryClient = useQueryClient();
    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState<string | null>(null);
    const [formData, setFormData] = useState<CreateConceptInput>({
        conceptName: '',
        definition: '',
        difficulty: 'INTERMEDIATE',
        keywords: [],
        relatedConceptIds: [],
    });
    const [keywordInput, setKeywordInput] = useState('');

    // Fetch concepts for this domain
    const { data: conceptsData, isLoading } = useQuery({
        queryKey: ['concepts', domainId],
        queryFn: async () => {
            const res = await fetch(
                `/admin/api/v1/content/domains/${domainId}/concepts`
            );
            if (!res.ok) throw new Error('Failed to fetch concepts');
            const json = await res.json();
            return json.concepts as Concept[];
        },
    });

    // Create mutation
    const createMutation = useMutation({
        mutationFn: async (data: CreateConceptInput) => {
            const res = await fetch(
                `/admin/api/v1/content/domains/${domainId}/concepts`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data),
                }
            );
            if (!res.ok) throw new Error('Failed to create concept');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['concepts', domainId] });
            setShowForm(false);
            resetForm();
        },
    });

    // Update mutation
    const updateMutation = useMutation({
        mutationFn: async (data: CreateConceptInput) => {
            const res = await fetch(
                `/admin/api/v1/content/domains/${domainId}/concepts/${editingId}`,
                {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data),
                }
            );
            if (!res.ok) throw new Error('Failed to update concept');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['concepts', domainId] });
            setShowForm(false);
            setEditingId(null);
            resetForm();
        },
    });

    // Delete mutation
    const deleteMutation = useMutation({
        mutationFn: async (id: string) => {
            const res = await fetch(
                `/admin/api/v1/content/domains/${domainId}/concepts/${id}`,
                {
                    method: 'DELETE',
                }
            );
            if (!res.ok) throw new Error('Failed to delete concept');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['concepts', domainId] });
        },
    });

    const resetForm = () => {
        setFormData({
            conceptName: '',
            definition: '',
            difficulty: 'INTERMEDIATE',
            keywords: [],
            relatedConceptIds: [],
        });
        setKeywordInput('');
    };

    const handleAddKeyword = () => {
        if (keywordInput.trim()) {
            setFormData({
                ...formData,
                keywords: [...formData.keywords, keywordInput.trim()],
            });
            setKeywordInput('');
        }
    };

    const handleRemoveKeyword = (index: number) => {
        setFormData({
            ...formData,
            keywords: formData.keywords.filter((_, i) => i !== index),
        });
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (editingId) {
            updateMutation.mutate(formData);
        } else {
            createMutation.mutate(formData);
        }
    };

    const handleEdit = (concept: Concept) => {
        setEditingId(concept.id);
        setFormData({
            conceptName: concept.conceptName,
            definition: concept.definition,
            difficulty: concept.difficulty,
            keywords: concept.keywords,
            relatedConceptIds: concept.relatedConceptIds,
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
        <div className="space-y-4">
            <div className="flex justify-between items-center">
                <h2 className="text-2xl font-bold">Concepts</h2>
                <div className="flex gap-2">
                    <Button onClick={() => setShowForm(true)}>Add Concept</Button>
                    {onClose && (
                        <Button variant="outline" onClick={onClose}>
                            Close
                        </Button>
                    )}
                </div>
            </div>

            {showForm && (
                <Card className="p-4">
                    <h3 className="text-lg font-semibold mb-3">
                        {editingId ? 'Edit Concept' : 'Add New Concept'}
                    </h3>
                    <form onSubmit={handleSubmit} className="space-y-3">
                        <div>
                            <label className="block text-sm font-medium mb-1">
                                Concept Name
                            </label>
                            <Input
                                value={formData.conceptName}
                                onChange={(e) =>
                                    setFormData({ ...formData, conceptName: e.target.value })
                                }
                                placeholder="e.g., Newton's First Law"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-1">
                                Definition
                            </label>
                            <RichTextEditor
                                value={formData.definition}
                                onChange={(value) =>
                                    setFormData({ ...formData, definition: value })
                                }
                                placeholder="Detailed definition of the concept"
                                minHeight="150px"
                            />
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
                            <label className="block text-sm font-medium mb-1">Keywords</label>
                            <div className="flex gap-2 mb-2">
                                <Input
                                    value={keywordInput}
                                    onChange={(e) => setKeywordInput(e.target.value)}
                                    placeholder="Add a keyword"
                                    onKeyPress={(e) => {
                                        if (e.key === 'Enter') {
                                            e.preventDefault();
                                            handleAddKeyword();
                                        }
                                    }}
                                />
                                <Button type="button" onClick={handleAddKeyword}>
                                    Add
                                </Button>
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {formData.keywords.map((kw, i) => (
                                    <div
                                        key={i}
                                        className="bg-blue-100 text-blue-800 px-2 py-1 rounded text-sm flex items-center gap-1"
                                    >
                                        {kw}
                                        <button
                                            type="button"
                                            onClick={() => handleRemoveKeyword(i)}
                                            className="text-blue-600 hover:text-blue-800"
                                        >
                                            ×
                                        </button>
                                    </div>
                                ))}
                            </div>
                        </div>

                        <div className="flex gap-2">
                            <Button type="submit" disabled={updateMutation.isPending || createMutation.isPending}>
                                {editingId ? 'Update' : 'Create'}
                            </Button>
                            <Button type="button" variant="outline" onClick={handleCancel}>
                                Cancel
                            </Button>
                        </div>
                    </form>
                </Card>
            )}

            <div className="space-y-2">
                {conceptsData?.map((concept) => (
                    <Card key={concept.id} className="p-3">
                        <div className="flex justify-between items-start">
                            <div className="flex-1">
                                <h4 className="font-semibold">{concept.conceptName}</h4>
                                <p className="text-sm text-gray-600">{concept.definition}</p>
                                <div className="flex gap-2 mt-2">
                                    <span className="text-xs bg-gray-100 px-2 py-1 rounded">
                                        {concept.difficulty}
                                    </span>
                                    {concept.keywords.map((kw) => (
                                        <span
                                            key={kw}
                                            className="text-xs bg-blue-100 text-blue-800 px-2 py-1 rounded"
                                        >
                                            {kw}
                                        </span>
                                    ))}
                                </div>
                            </div>
                            <div className="flex gap-1">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => handleEdit(concept)}
                                >
                                    Edit
                                </Button>
                                <Button
                                    variant="destructive"
                                    size="sm"
                                    onClick={() => {
                                        if (
                                            window.confirm('Delete this concept?')
                                        ) {
                                            deleteMutation.mutate(concept.id);
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

export default ConceptEditor;
