/**
 * Placeholder Route Module
 *
 * Generic placeholder page for routes that are not yet implemented.
 * Shows a "Coming Soon" message with navigation back to dashboard.
 */

import { Link, useLocation } from 'react-router';
import { MainLayout } from '@/app/Layout';
import { ArrowLeft, Construction } from 'lucide-react';

export default function PlaceholderRoute() {
    const location = useLocation();
    const pageName = location.pathname.split('/').filter(Boolean).pop() || 'page';
    const displayName = pageName
        .split('-')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');

    return (
        <MainLayout>
            <div className="min-h-screen bg-gray-50 flex items-center justify-center">
                <div className="max-w-md w-full bg-white shadow-lg rounded-lg p-8 text-center">
                    <div className="flex justify-center mb-6">
                        <div className="bg-yellow-100 rounded-full p-4">
                            <Construction className="h-12 w-12 text-yellow-600" />
                        </div>
                    </div>
                    <h1 className="text-2xl font-bold text-gray-900 mb-2">
                        {displayName}
                    </h1>
                    <p className="text-gray-600 mb-6">
                        This page is under construction and will be available soon.
                    </p>
                    <Link
                        to="/config"
                        className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500"
                    >
                        <ArrowLeft className="-ml-1 mr-2 h-5 w-5" />
                        Back to Dashboard
                    </Link>
                </div>
            </div>
        </MainLayout>
    );
}
