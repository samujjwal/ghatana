/**
 * 404 Not Found Route
 * 
 * Displays a user-friendly 404 page when a route is not found.
 * Provides navigation options to help users get back on track.
 * 
 * @doc.type route
 * @doc.purpose 404 error handling
 * @doc.layer product
 * @doc.pattern Route Component
 */

import { Link } from 'react-router';
import { Home, Search, ArrowLeft as ArrowBack } from 'lucide-react';

export default function NotFoundRoute() {
    return (
        <div className="flex flex-col items-center justify-center min-h-screen bg-bg-default px-4">
            <div className="text-center max-w-md">
                {/* 404 Icon */}
                <div className="mb-6">
                    <div className="w-24 h-24 mx-auto rounded-full bg-grey-100 dark:bg-grey-800 flex items-center justify-center">
                        <Search className="w-12 h-12 text-grey-400" />
                    </div>
                </div>

                {/* Error Message */}
                <h1 className="text-4xl font-bold text-text-primary mb-2">404</h1>
                <h2 className="text-xl font-semibold text-text-primary mb-4">
                    Page Not Found
                </h2>
                <p className="text-text-secondary mb-8">
                    The page you're looking for doesn't exist or has been moved.
                    Let's get you back on track.
                </p>

                {/* Navigation Options */}
                <div className="flex flex-col sm:flex-row gap-3 justify-center">
                    <Link
                        to="/app"
                        className="inline-flex items-center justify-center gap-2 px-6 py-3 bg-primary-600 text-white rounded-lg font-medium hover:bg-primary-700 transition-colors no-underline"
                    >
                        <Home className="w-5 h-5" />
                        Go to Dashboard
                    </Link>
                    <button
                        onClick={() => window.history.back()}
                        className="inline-flex items-center justify-center gap-2 px-6 py-3 bg-grey-100 dark:bg-grey-800 text-text-primary rounded-lg font-medium hover:bg-grey-200 dark:hover:bg-grey-700 transition-colors"
                    >
                        <ArrowBack className="w-5 h-5" />
                        Go Back
                    </button>
                </div>

                {/* Help Text */}
                <p className="mt-8 text-sm text-text-secondary">
                    Need help?{' '}
                    <Link
                        to="/app"
                        className="text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
                    >
                        Contact support
                    </Link>
                </p>
            </div>
        </div>
    );
}
