/**
 * Quick Actions Component
 * 
 * Quick action buttons for common Content Studio operations
 */

import { Button } from '@ghatana/ui';
import { Sparkles, TrendingUp, Plus } from 'lucide-react';

interface QuickActionsProps {
    onCreateContent: () => void;
    onViewAnalytics: () => void;
}

export function QuickActions({ onCreateContent, onViewAnalytics }: QuickActionsProps) {
    return (
        <div className="bg-gradient-to-r from-purple-500 to-blue-600 rounded-2xl shadow-xl p-6 text-white">
            <div className="flex items-start justify-between">
                <div className="flex-1">
                    <h2 className="text-2xl font-bold mb-2">Create Your Next Learning Experience</h2>
                    <p className="text-purple-100 mb-4 max-w-2xl">
                        Generate comprehensive educational content with AI-powered real-world use cases, practice worksheets, and interactive quizzes.
                    </p>
                    <div className="flex gap-3">
                        <Button
                            onClick={onCreateContent}
                            className="bg-white text-purple-600 hover:bg-purple-50"
                        >
                            <Plus className="h-4 w-4 mr-2" />
                            Create Content
                        </Button>
                        <Button
                            onClick={onViewAnalytics}
                            variant="outline"
                            className="border-white text-white hover:bg-white/20"
                        >
                            <TrendingUp className="h-4 w-4 mr-2" />
                            View Analytics
                        </Button>
                    </div>
                </div>
                <div className="hidden lg:block">
                    <Sparkles className="h-16 w-16 text-purple-200" />
                </div>
            </div>
        </div>
    );
}
