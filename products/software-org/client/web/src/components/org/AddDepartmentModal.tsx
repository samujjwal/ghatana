/**
 * Add Department Modal
 *
 * Modal for creating a new department in the organization structure.
 * Uses Drawer component for consistent admin UX.
 *
 * @doc.type component
 * @doc.purpose Create new department
 * @doc.layer product
 * @doc.pattern Form Modal
 */

import { useState } from 'react';
import { Building2, Loader2 } from 'lucide-react';
import { Drawer, FormField, Input, Textarea, Select } from '@/components/admin';
import { Button } from '@/components/ui';
import { useCreateOrgDepartment, orgQueryKeys } from '@/hooks';
import { useQueryClient } from '@tanstack/react-query';

interface AddDepartmentModalProps {
    isOpen: boolean;
    onClose: () => void;
    tenantId: string;
    onSuccess?: () => void;
}

const DEPARTMENT_TYPES = [
    { value: 'engineering', label: 'Engineering' },
    { value: 'product', label: 'Product' },
    { value: 'design', label: 'Design' },
    { value: 'operations', label: 'Operations' },
    { value: 'security', label: 'Security' },
    { value: 'devops', label: 'DevOps' },
    { value: 'qa', label: 'Quality Assurance' },
    { value: 'support', label: 'Support' },
    { value: 'data', label: 'Data & Analytics' },
    { value: 'other', label: 'Other' },
];

export function AddDepartmentModal({
    isOpen,
    onClose,
    tenantId,
    onSuccess,
}: AddDepartmentModalProps) {
    const queryClient = useQueryClient();
    const createDepartment = useCreateOrgDepartment();

    const [formData, setFormData] = useState({
        name: '',
        type: 'engineering',
        description: '',
    });
    const [errors, setErrors] = useState<Record<string, string>>({});

    const validateForm = () => {
        const newErrors: Record<string, string> = {};

        if (!formData.name.trim()) {
            newErrors.name = 'Department name is required';
        } else if (formData.name.length < 2) {
            newErrors.name = 'Name must be at least 2 characters';
        }

        if (!formData.type) {
            newErrors.type = 'Department type is required';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!validateForm()) {
            return;
        }

        try {
            await createDepartment.mutateAsync({
                name: formData.name.trim(),
                type: formData.type,
                description: formData.description.trim() || undefined,
                organizationId: tenantId || undefined,
                status: 'active',
            });

            // Invalidate and refetch departments
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.departments() });

            // Reset form and close
            setFormData({ name: '', type: 'engineering', description: '' });
            setErrors({});
            onSuccess?.();
            onClose();
        } catch (error) {
            console.error('Failed to create department:', error);
            const isApiNotImplemented = (error as any)?.response?.status === 404;
            setErrors({
                submit: isApiNotImplemented
                    ? 'This feature requires backend API implementation. The endpoint /api/v1/org/departments is not yet available.'
                    : error instanceof Error
                        ? error.message
                        : 'Failed to create department',
            });
        }
    };

    const handleClose = () => {
        setFormData({ name: '', type: 'engineering', description: '' });
        setErrors({});
        onClose();
    };

    return (
        <Drawer isOpen={isOpen} onClose={handleClose} title="Add Department" size="md">
            <form onSubmit={handleSubmit} className="space-y-6">
                {/* Header Icon */}
                <div className="flex items-center gap-3 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
                    <div className="p-3 bg-blue-100 dark:bg-blue-900/50 rounded-lg">
                        <Building2 className="w-6 h-6 text-blue-600 dark:text-blue-400" />
                    </div>
                    <div>
                        <h3 className="font-medium text-gray-900 dark:text-white">
                            New Department
                        </h3>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            Create a department to organize your agents
                        </p>
                    </div>
                </div>

                {/* Form Fields */}
                <FormField
                    label="Department Name"
                    name="name"
                    required
                    error={errors.name}
                    helpText="A descriptive name for the department"
                >
                    <Input
                        id="name"
                        name="name"
                        type="text"
                        placeholder="e.g., Platform Engineering"
                        value={formData.name}
                        onChange={(e) =>
                            setFormData((prev) => ({ ...prev, name: e.target.value }))
                        }
                        error={!!errors.name}
                        autoFocus
                    />
                </FormField>

                <FormField
                    label="Department Type"
                    name="type"
                    required
                    error={errors.type}
                    helpText="Select the function of this department"
                >
                    <Select
                        id="type"
                        name="type"
                        value={formData.type}
                        onChange={(e) =>
                            setFormData((prev) => ({ ...prev, type: e.target.value }))
                        }
                        options={DEPARTMENT_TYPES}
                        error={!!errors.type}
                    />
                </FormField>

                <FormField
                    label="Description"
                    name="description"
                    helpText="Optional description of the department's responsibilities"
                >
                    <Textarea
                        id="description"
                        name="description"
                        placeholder="What does this department do?"
                        value={formData.description}
                        onChange={(e) =>
                            setFormData((prev) => ({ ...prev, description: e.target.value }))
                        }
                        rows={3}
                    />
                </FormField>

                {/* Error Message */}
                {errors.submit && (
                    <div className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
                        <p className="text-sm text-red-600 dark:text-red-400">{errors.submit}</p>
                    </div>
                )}

                {/* Actions */}
                <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                    <Button
                        type="button"
                        variant="ghost"
                        onClick={handleClose}
                        disabled={createDepartment.isPending}
                    >
                        Cancel
                    </Button>
                    <Button
                        type="submit"
                        disabled={createDepartment.isPending}
                        className="flex items-center gap-2"
                    >
                        {createDepartment.isPending ? (
                            <>
                                <Loader2 className="w-4 h-4 animate-spin" />
                                Creating...
                            </>
                        ) : (
                            <>
                                <Building2 className="w-4 h-4" />
                                Create Department
                            </>
                        )}
                    </Button>
                </div>
            </form>
        </Drawer>
    );
}

export default AddDepartmentModal;
