/**
 * New Performance Review Route
 *
 * Create a new performance review for an employee.
 *
 * @package @ghatana/software-org-web
 */

import { useSearchParams, useNavigate } from 'react-router';
import { PerformanceReviewForm } from '../../../components/team/PerformanceReviewForm';

export default function NewPerformanceReviewRoute() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();

    const employeeId = searchParams.get('employeeId') || '';
    const employeeName = searchParams.get('employeeName') || 'Unknown Employee';

    if (!employeeId) {
        return (
            <div className="p-6">
                <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                    <h3 className="text-red-800 dark:text-red-200 font-semibold">Missing Employee ID</h3>
                    <p className="text-red-700 dark:text-red-300 mt-2">
                        Employee ID is required to create a performance review.
                    </p>
                    <button
                        onClick={() => navigate('/team/performance-reviews')}
                        className="mt-4 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
                    >
                        Back to Reviews
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="p-6">
            <div className="max-w-5xl mx-auto">
                <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-6">
                    New Performance Review
                </h1>
                <p className="text-gray-600 dark:text-gray-400 mb-8">
                    Creating review for <span className="font-semibold">{employeeName}</span>
                </p>

                <PerformanceReviewForm
                    employeeId={employeeId}
                    employeeName={employeeName}
                    onSuccess={() => navigate('/team/performance-reviews')}
                    onCancel={() => navigate('/team/performance-reviews')}
                />
            </div>
        </div>
    );
}
