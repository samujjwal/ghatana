/**
 * Performance Review Form Component
 *
 * Comprehensive form for conducting employee performance reviews.
 * Includes goal tracking, competency ratings, feedback, and next steps.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';

interface Goal {
    id?: string;
    title: string;
    description: string;
    category: 'TECHNICAL' | 'LEADERSHIP' | 'PROCESS' | 'GROWTH';
    rating: number;
    comment: string;
    completed: boolean;
}

interface Competency {
    name: string;
    rating: number;
    comment: string;
}

interface PerformanceReviewFormProps {
    reviewId?: string;
    employeeId: string;
    employeeName: string;
    onSuccess?: () => void;
    onCancel?: () => void;
}

export const PerformanceReviewForm: React.FC<PerformanceReviewFormProps> = ({
    reviewId,
    employeeId,
    employeeName,
    onSuccess,
    onCancel,
}) => {
    const queryClient = useQueryClient();
    const navigate = useNavigate();

    // Fetch existing review if editing
    const { data: existingReview } = useQuery({
        queryKey: ['/api/v1/performance-reviews', reviewId],
        queryFn: async () => {
            if (!reviewId) return null;
            const response = await fetch(`/api/v1/performance-reviews/${reviewId}`);
            if (!response.ok) throw new Error('Failed to fetch review');
            return response.json();
        },
        enabled: !!reviewId,
    });

    // Form state
    const [goals, setGoals] = useState<Goal[]>(
        existingReview?.metadata?.goals || [
            { title: '', description: '', category: 'TECHNICAL', rating: 3, comment: '', completed: false },
        ]
    );

    const [competencies, setCompetencies] = useState<Competency[]>(
        existingReview?.metadata?.competencies || [
            { name: 'Technical Skills', rating: 3, comment: '' },
            { name: 'Communication', rating: 3, comment: '' },
            { name: 'Leadership', rating: 3, comment: '' },
            { name: 'Problem Solving', rating: 3, comment: '' },
            { name: 'Collaboration', rating: 3, comment: '' },
        ]
    );

    const [overallRating, setOverallRating] = useState<number>(
        existingReview?.overallRating || 0
    );
    const [strengths, setStrengths] = useState<string>(
        existingReview?.metadata?.strengths || ''
    );
    const [improvements, setImprovements] = useState<string>(
        existingReview?.metadata?.improvements || ''
    );
    const [careerDevelopment, setCareerDevelopment] = useState<string>(
        existingReview?.metadata?.careerDevelopment || ''
    );
    const [promotionRecommended, setPromotionRecommended] = useState<boolean>(
        existingReview?.metadata?.promotionRecommended || false
    );
    const [salaryAdjustment, setSalaryAdjustment] = useState<number>(
        existingReview?.metadata?.salaryAdjustment || 0
    );
    const [nextReviewDate, setNextReviewDate] = useState<string>(
        existingReview?.metadata?.nextReviewDate || ''
    );

    // Calculate automatic overall rating
    const calculateOverallRating = () => {
        const goalAvg = goals.length > 0
            ? goals.reduce((sum, g) => sum + g.rating, 0) / goals.length
            : 0;
        const compAvg = competencies.length > 0
            ? competencies.reduce((sum, c) => sum + c.rating, 0) / competencies.length
            : 0;
        return Number(((goalAvg + compAvg) / 2).toFixed(1));
    };

    // Auto-calculate on changes
    React.useEffect(() => {
        if (overallRating === 0 || overallRating === calculateOverallRating()) {
            setOverallRating(calculateOverallRating());
        }
    }, [goals, competencies]);

    // Save/Submit mutations
    const saveDraft = useMutation({
        mutationFn: async (data: any) => {
            const url = reviewId
                ? `/api/v1/performance-reviews/${reviewId}`
                : '/api/v1/performance-reviews';
            const method = reviewId ? 'PUT' : 'POST';

            const response = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ...data, status: 'IN_PROGRESS' }),
            });
            if (!response.ok) throw new Error('Failed to save review');
            return response.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['/api/v1/performance-reviews'] });
            alert('Draft saved successfully');
        },
    });

    const submitReview = useMutation({
        mutationFn: async (data: any) => {
            const url = reviewId
                ? `/api/v1/performance-reviews/${reviewId}/submit`
                : '/api/v1/performance-reviews';
            const method = 'POST';

            const response = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ...data, status: 'SUBMITTED' }),
            });
            if (!response.ok) throw new Error('Failed to submit review');
            return response.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['/api/v1/performance-reviews'] });
            onSuccess?.();
            navigate('/team/performance-reviews');
        },
    });

    const handleAddGoal = () => {
        setGoals([
            ...goals,
            { title: '', description: '', category: 'TECHNICAL', rating: 3, comment: '', completed: false },
        ]);
    };

    const handleRemoveGoal = (index: number) => {
        setGoals(goals.filter((_, i) => i !== index));
    };

    const handleGoalChange = (index: number, field: keyof Goal, value: any) => {
        const newGoals = [...goals];
        newGoals[index] = { ...newGoals[index], [field]: value };
        setGoals(newGoals);
    };

    const handleCompetencyChange = (index: number, field: keyof Competency, value: any) => {
        const newCompetencies = [...competencies];
        newCompetencies[index] = { ...newCompetencies[index], [field]: value };
        setCompetencies(newCompetencies);
    };

    const handleSaveDraft = () => {
        const reviewData = {
            employeeId,
            reviewDate: new Date().toISOString(),
            overallRating,
            metadata: {
                goals,
                competencies,
                strengths,
                improvements,
                careerDevelopment,
                promotionRecommended,
                salaryAdjustment,
                nextReviewDate,
            },
        };
        saveDraft.mutate(reviewData);
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();

        // Validation
        if (goals.some(g => !g.title)) {
            alert('Please complete all goal titles');
            return;
        }
        if (!strengths.trim() || !improvements.trim()) {
            alert('Please provide both strengths and areas for improvement');
            return;
        }

        const reviewData = {
            employeeId,
            reviewDate: new Date().toISOString(),
            overallRating,
            metadata: {
                goals,
                competencies,
                strengths,
                improvements,
                careerDevelopment,
                promotionRecommended,
                salaryAdjustment,
                nextReviewDate,
            },
        };

        submitReview.mutate(reviewData);
    };

    return (
        <form onSubmit={handleSubmit} className="max-w-5xl mx-auto p-6">
            {/* Header */}
            <div className="mb-8">
                <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100">
                    Performance Review: {employeeName}
                </h1>
                <p className="text-gray-600 dark:text-gray-400 mt-2">
                    Complete all sections and submit for 1:1 discussion
                </p>
            </div>

            {/* Goals Section */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 mb-6">
                <div className="flex items-center justify-between mb-6">
                    <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100">
                        📋 Goals ({goals.length})
                    </h2>
                    <button
                        type="button"
                        onClick={handleAddGoal}
                        className="px-4 py-2 text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-md transition-colors text-sm font-medium"
                    >
                        + Add Goal
                    </button>
                </div>

                <div className="space-y-6">
                    {goals.map((goal, index) => (
                        <div
                            key={index}
                            className="border border-gray-200 dark:border-gray-700 rounded-lg p-4"
                        >
                            <div className="flex items-start justify-between mb-4">
                                <div className="flex-1 space-y-4">
                                    <input
                                        type="text"
                                        placeholder="Goal title"
                                        value={goal.title}
                                        onChange={(e) => handleGoalChange(index, 'title', e.target.value)}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                        required
                                    />

                                    <textarea
                                        placeholder="Goal description"
                                        value={goal.description}
                                        onChange={(e) => handleGoalChange(index, 'description', e.target.value)}
                                        rows={2}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    />

                                    <div className="grid grid-cols-2 gap-4">
                                        <select
                                            value={goal.category}
                                            onChange={(e) => handleGoalChange(index, 'category', e.target.value)}
                                            className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                        >
                                            <option value="TECHNICAL">Technical</option>
                                            <option value="LEADERSHIP">Leadership</option>
                                            <option value="PROCESS">Process</option>
                                            <option value="GROWTH">Growth</option>
                                        </select>

                                        <label className="flex items-center gap-2">
                                            <input
                                                type="checkbox"
                                                checked={goal.completed}
                                                onChange={(e) => handleGoalChange(index, 'completed', e.target.checked)}
                                                className="w-4 h-4 text-blue-600"
                                            />
                                            <span className="text-sm text-gray-700 dark:text-gray-300">
                                                Completed
                                            </span>
                                        </label>
                                    </div>

                                    {/* Rating */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Rating: {goal.rating}/5
                                        </label>
                                        <div className="flex gap-2">
                                            {[1, 2, 3, 4, 5].map((rating) => (
                                                <button
                                                    key={rating}
                                                    type="button"
                                                    onClick={() => handleGoalChange(index, 'rating', rating)}
                                                    className={`text-3xl ${rating <= goal.rating
                                                            ? 'text-yellow-400'
                                                            : 'text-gray-300 dark:text-gray-600'
                                                        }`}
                                                >
                                                    ★
                                                </button>
                                            ))}
                                        </div>
                                    </div>

                                    <textarea
                                        placeholder="Comments on this goal..."
                                        value={goal.comment}
                                        onChange={(e) => handleGoalChange(index, 'comment', e.target.value)}
                                        rows={2}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    />
                                </div>

                                {goals.length > 1 && (
                                    <button
                                        type="button"
                                        onClick={() => handleRemoveGoal(index)}
                                        className="ml-4 text-red-500 hover:text-red-700"
                                    >
                                        ✕
                                    </button>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            {/* Competencies Section */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 mb-6">
                <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-6">
                    🎯 Competencies
                </h2>

                <div className="space-y-6">
                    {competencies.map((comp, index) => (
                        <div key={index} className="border-b border-gray-200 dark:border-gray-700 pb-4 last:border-0">
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                {comp.name}: {comp.rating}/5
                            </label>
                            <div className="flex gap-2 mb-3">
                                {[1, 2, 3, 4, 5].map((rating) => (
                                    <button
                                        key={rating}
                                        type="button"
                                        onClick={() => handleCompetencyChange(index, 'rating', rating)}
                                        className={`text-3xl ${rating <= comp.rating
                                                ? 'text-yellow-400'
                                                : 'text-gray-300 dark:text-gray-600'
                                            }`}
                                    >
                                        ★
                                    </button>
                                ))}
                            </div>
                            <textarea
                                placeholder={`Comments on ${comp.name.toLowerCase()}...`}
                                value={comp.comment}
                                onChange={(e) => handleCompetencyChange(index, 'comment', e.target.value)}
                                rows={2}
                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                            />
                        </div>
                    ))}
                </div>
            </div>

            {/* Overall Rating */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 mb-6">
                <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-6">
                    ⭐ Overall Rating
                </h2>

                <div className="mb-4">
                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">
                        Auto-calculated: {calculateOverallRating()}/5.0
                    </p>
                    <p className="text-sm text-gray-600 dark:text-gray-400">
                        You can override this rating below
                    </p>
                </div>

                <div className="flex gap-2 mb-4">
                    {[1, 2, 3, 4, 5].map((rating) => (
                        <button
                            key={rating}
                            type="button"
                            onClick={() => setOverallRating(rating)}
                            className={`text-5xl ${rating <= overallRating
                                    ? 'text-yellow-400'
                                    : 'text-gray-300 dark:text-gray-600'
                                }`}
                        >
                            ★
                        </button>
                    ))}
                </div>

                <div className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                    {overallRating}/5.0
                </div>
            </div>

            {/* Written Feedback */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 mb-6">
                <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-6">
                    ✍️ Written Feedback
                </h2>

                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Strengths *
                        </label>
                        <textarea
                            value={strengths}
                            onChange={(e) => setStrengths(e.target.value)}
                            rows={4}
                            required
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                            placeholder="What are this employee's key strengths?"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Areas for Improvement *
                        </label>
                        <textarea
                            value={improvements}
                            onChange={(e) => setImprovements(e.target.value)}
                            rows={4}
                            required
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                            placeholder="What areas should this employee focus on improving?"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Career Development Suggestions
                        </label>
                        <textarea
                            value={careerDevelopment}
                            onChange={(e) => setCareerDevelopment(e.target.value)}
                            rows={4}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                            placeholder="What opportunities or development paths would you recommend?"
                        />
                    </div>
                </div>
            </div>

            {/* Next Steps */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 mb-6">
                <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-6">
                    🎯 Next Steps
                </h2>

                <div className="space-y-4">
                    <label className="flex items-center gap-3">
                        <input
                            type="checkbox"
                            checked={promotionRecommended}
                            onChange={(e) => setPromotionRecommended(e.target.checked)}
                            className="w-5 h-5 text-blue-600"
                        />
                        <span className="text-gray-700 dark:text-gray-300 font-medium">
                            Recommend for Promotion
                        </span>
                    </label>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Salary Adjustment (%)
                        </label>
                        <input
                            type="number"
                            value={salaryAdjustment}
                            onChange={(e) => setSalaryAdjustment(Number(e.target.value))}
                            min="0"
                            max="50"
                            step="0.5"
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Next Review Date
                        </label>
                        <input
                            type="date"
                            value={nextReviewDate}
                            onChange={(e) => setNextReviewDate(e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                        />
                    </div>
                </div>
            </div>

            {/* Actions */}
            <div className="flex gap-4">
                <button
                    type="button"
                    onClick={handleSaveDraft}
                    disabled={saveDraft.isPending}
                    className="px-6 py-3 bg-gray-200 dark:bg-gray-700 text-gray-900 dark:text-gray-100 rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600 transition-colors font-medium"
                >
                    {saveDraft.isPending ? 'Saving...' : 'Save Draft'}
                </button>

                <button
                    type="submit"
                    disabled={submitReview.isPending}
                    className="flex-1 px-6 py-3 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors font-medium disabled:opacity-50"
                >
                    {submitReview.isPending ? 'Submitting...' : 'Submit Review'}
                </button>

                <button
                    type="button"
                    onClick={onCancel || (() => navigate('/team/performance-reviews'))}
                    className="px-6 py-3 bg-gray-200 dark:bg-gray-700 text-gray-900 dark:text-gray-100 rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600 transition-colors font-medium"
                >
                    Cancel
                </button>
            </div>
        </form>
    );
};

export default PerformanceReviewForm;
