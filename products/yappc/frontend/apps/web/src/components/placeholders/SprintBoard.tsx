/**
 * SprintBoard Placeholder Component
 * 
 * Temporary placeholder until the actual component is implemented in @ghatana/yappc-ui
 */

import React from 'react';

interface SprintBoardProps {
  sprintId?: string;
  projectId?: string;
  sprint?: unknown;
  stories?: unknown[];
  filters?: {
    assignees: string[];
    types: string[];
    priorities: string[];
    labels: string[];
    search: string;
  };
  onStoryClick?: (storyId: string) => void;
  onStoryMove?: (storyId: string, fromColumn: string, toColumn: string) => void;
  onCreateStory?: (columnId: string) => void;
  className?: string;
}

export const SprintBoard: React.FC<SprintBoardProps> = ({
  sprint,
  stories = [],
  onStoryClick,
}) => {
  const columns = ['todo', 'in-progress', 'review', 'done'];

  return (
    <div className="bg-zinc-900 rounded-lg border border-zinc-800 p-6">
      <div className="mb-6">
        <h3 className="text-xl font-semibold text-white">
          {sprint?.name || 'Sprint Board'}
        </h3>
        {sprint && (
          <p className="text-sm text-zinc-400 mt-1">
            {sprint.startDate} - {sprint.endDate}
          </p>
        )}
      </div>

      <div className="grid grid-cols-4 gap-4">
        {columns.map((column) => (
          <div key={column} className="bg-zinc-800/50 rounded-lg p-4">
            <h4 className="text-sm font-semibold text-zinc-300 uppercase mb-3">
              {column.replace('-', ' ')}
            </h4>
            <div className="space-y-2">
              {stories
                .filter((story) => story.status === column)
                .map((story) => (
                  <div
                    key={story.id}
                    onClick={() => onStoryClick?.(story.id)}
                    className="p-3 bg-zinc-800 rounded border border-zinc-700 hover:border-violet-500 cursor-pointer transition-colors"
                  >
                    <div className="text-sm font-medium text-white mb-1">
                      {story.title}
                    </div>
                    <div className="flex items-center gap-2 text-xs text-zinc-400">
                      <span className={`px-2 py-0.5 rounded ${
                        story.priority === 'high' 
                          ? 'bg-red-500/20 text-red-400'
                          : story.priority === 'medium'
                          ? 'bg-yellow-500/20 text-yellow-400'
                          : 'bg-blue-500/20 text-blue-400'
                      }`}>
                        {story.priority}
                      </span>
                      <span>{story.points || 0} pts</span>
                    </div>
                  </div>
                ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
