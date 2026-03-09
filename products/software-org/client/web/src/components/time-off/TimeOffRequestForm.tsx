/**
 * Time Off Request Form Component
 *
 * @doc.type component
 * @doc.purpose Form for submitting time-off requests with conflict detection
 * @doc.layer presentation
 * @doc.pattern Form Component
 *
 * Features:
 * - Date range selection
 * - Time-off type selection (vacation, sick, etc.)
 * - Real-time conflict detection
 * - Automatic days calculation
 * - Approval routing based on duration
 */

import { useState, useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { currentUserAtom } from '../../atoms/user';

type TimeOffType = 'VACATION' | 'SICK' | 'PERSONAL' | 'BEREAVEMENT' | 'UNPAID';

interface TimeOffRequest {
    userId: string;
    type: TimeOffType;
    startDate: string;
    endDate: string;
    reason?: string;
    notes?: string;
}

type SubmitTimeOffRequest = Omit<TimeOffRequest, 'notes'>;

interface Conflict {
    id: string;
    startDate: string;
    endDate: string;
    type: string;
    status: string;
}

export function TimeOffRequestForm({ onSuccess }: { onSuccess?: () => void }) {
    const [currentUser] = useAtom(currentUserAtom);
    const queryClient = useQueryClient();

    const [formData, setFormData] = useState<TimeOffRequest>({
        userId: currentUser?.id || '',
        type: 'VACATION',
        startDate: '',
        endDate: '',
        reason: '',
        notes: '',
    });

    const [daysCount, setDaysCount] = useState(0);

    // Calculate days when dates change
    useEffect(() => {
        if (formData.startDate && formData.endDate) {
            const start = new Date(formData.startDate);
            const end = new Date(formData.endDate);
            const days = Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1;
            setDaysCount(days > 0 ? days : 0);
        } else {
            setDaysCount(0);
        }
    }, [formData.startDate, formData.endDate]);

    // Check for conflicts
    const { data: conflictsData, isLoading: checkingConflicts } = useQuery({
        queryKey: ['time-off-conflicts', formData.userId, formData.startDate, formData.endDate],
        queryFn: async () => {
            if (!formData.startDate || !formData.endDate || !formData.userId) {
                return { hasConflicts: false, conflicts: [] };
            }

            const params = new URLSearchParams({
                userId: formData.userId,
                startDate: formData.startDate,
                endDate: formData.endDate,
            });

            const response = await fetch(`/api/v1/time-off/conflicts?${params}`);
            if (!response.ok) throw new Error('Failed to check conflicts');
            return response.json();
        },
        enabled: !!formData.startDate && !!formData.endDate && !!formData.userId,
    });

    // Submit mutation
    const submitRequest = useMutation({
        mutationFn: async (data: TimeOffRequest) => {
            const payload: SubmitTimeOffRequest = {
                userId: data.userId,
                type: data.type,
                startDate: data.startDate,
                endDate: data.endDate,
                reason: data.reason?.trim() || data.notes?.trim() || undefined,
            };
            const response = await fetch('/api/v1/time-off', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to submit request');
            }
            return response.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['time-off'] });
            // Reset form
            setFormData({
                userId: currentUser?.id || '',
                type: 'VACATION',
                startDate: '',
                endDate: '',
                reason: '',
                notes: '',
            });
            onSuccess?.();
        },
    });

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (conflictsData?.hasConflicts) {
            alert('Please resolve conflicts before submitting');
            return;
        }
        submitRequest.mutate(formData);
    };

    const hasConflicts = conflictsData?.hasConflicts || false;
    const conflicts: Conflict[] = conflictsData?.conflicts || [];

    return (
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
            <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-6">
                Request Time Off
            </h2>

            <form onSubmit={handleSubmit} className="space-y-6">
                {/* Type Selection */}
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                        Type <span className="text-red-500">*</span>
                    </label>
                    <select
                        value={formData.type}
                        onChange={(e) => setFormData({ ...formData, type: e.target.value as TimeOffType })}
                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                        required
                    >
                        <option value="VACATION">Vacation</option>
                        <option value="SICK">Sick Leave</option>
                        <option value="PERSONAL">Personal Day</option>
                        <option value="BEREAVEMENT">Bereavement</option>
                        <option value="UNPAID">Unpaid Leave</option>
                    </select>
                </div>

                {/* Date Range */}
                <div className="grid grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Start Date <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="date"
                            value={formData.startDate}
                            onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                            min={new Date().toISOString().split('T')[0]}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                            required
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            End Date <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="date"
                            value={formData.endDate}
                            onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
                            min={formData.startDate || new Date().toISOString().split('T')[0]}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                            required
                        />
                    </div>
                </div>

                {/* Days Count */}
                {daysCount > 0 && (
                    <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3">
                        <p className="text-blue-800 dark:text-blue-200 text-sm font-medium">
                            Total Days: {daysCount} {daysCount === 1 ? 'day' : 'days'}
                        </p>
                    </div>
                )}

                {/* Conflicts Warning */}
                {checkingConflicts && (
                    <div className="bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg p-3">
                        <p className="text-gray-600 dark:text-gray-400 text-sm">Checking for conflicts...</p>
                    </div>
                )}

                {hasConflicts && conflicts.length > 0 && (
                    <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                        <h3 className="text-red-800 dark:text-red-200 font-semibold mb-2">
                            ⚠️ Scheduling Conflicts
                        </h3>
                        <p className="text-red-600 dark:text-red-300 text-sm mb-3">
                            You have existing time-off requests that overlap with these dates:
                        </p>
                        <ul className="space-y-2">
                            {conflicts.map((conflict) => (
                                <li key={conflict.id} className="text-sm text-red-700 dark:text-red-300">
                                    • {conflict.type} from {new Date(conflict.startDate).toLocaleDateString()} to{' '}
                                    {new Date(conflict.endDate).toLocaleDateString()} ({conflict.status})
                                </li>
                            ))}
                        </ul>
                    </div>
                )}

                {/* Reason */}
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                        Reason
                    </label>
                    <input
                        type="text"
                        value={formData.reason}
                        onChange={(e) => setFormData({ ...formData, reason: e.target.value })}
                        placeholder="e.g., Family vacation, Medical appointment"
                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                    />
                </div>

                {/* Notes */}
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                        Additional Notes
                    </label>
                    <textarea
                        value={formData.notes}
                        onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
                        rows={3}
                        placeholder="Any additional information..."
                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                    />
                </div>

                {/* Error Message */}
                {submitRequest.isError && (
                    <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3">
                        <p className="text-red-800 dark:text-red-200 text-sm">
                            {submitRequest.error instanceof Error
                                ? submitRequest.error.message
                                : 'Failed to submit request'}
                        </p>
                    </div>
                )}

                {/* Success Message */}
                {submitRequest.isSuccess && (
                    <div className="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-3">
                        <p className="text-green-800 dark:text-green-200 text-sm">
                            ✓ Time-off request submitted successfully!
                        </p>
                    </div>
                )}

                {/* Submit Button */}
                <div className="flex justify-end gap-3">
                    <button
                        type="button"
                        onClick={() => {
                            setFormData({
                                userId: currentUser?.id || '',
                                type: 'VACATION',
                                startDate: '',
                                endDate: '',
                                reason: '',
                                notes: '',
                            });
                        }}
                        className="px-4 py-2 bg-gray-200 dark:bg-gray-700 text-gray-900 dark:text-white rounded-md text-sm font-medium hover:bg-gray-300 dark:hover:bg-gray-600 transition-colors"
                    >
                        Reset
                    </button>
                    <button
                        type="submit"
                        disabled={submitRequest.isPending || hasConflicts || !formData.startDate || !formData.endDate}
                        className="px-6 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white rounded-md text-sm font-medium transition-colors"
                    >
                        {submitRequest.isPending ? 'Submitting...' : 'Submit Request'}
                    </button>
                </div>
            </form>
        </div>
    );
}
