/**
 * Create Content View
 *
 * Create content view for the Unified Content Studio.
 *
 * @doc.type component
 * @doc.purpose Content creation form for content studio
 * @doc.layer product
 * @doc.pattern View Component
 */

import { Sparkles } from "lucide-react";
import { Button } from "@ghatana/design-system";

export interface GenerationRequest {
  topic: string;
  gradeLevel: string;
  subject: string;
  includeRealWorldUseCases: boolean;
  includePracticeWorksheets: boolean;
  includeQuizzes: boolean;
}

export interface CreateContentViewProps {
  generationRequest: GenerationRequest;
  isGenerating: boolean;
  onRequestChange: (request: GenerationRequest) => void;
  onGenerate: () => void;
  onCancel: () => void;
}

export function CreateContentView({
  generationRequest,
  isGenerating,
  onRequestChange,
  onGenerate,
  onCancel,
}: CreateContentViewProps) {
  return (
    <div className="space-y-6">
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Generate Comprehensive Content
        </h3>
        <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">
          Create comprehensive educational content with real-world use cases,
          practice worksheets, and quizzes.
        </p>

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              Topic
            </label>
            <input
              type="text"
              value={generationRequest.topic}
              onChange={(e) =>
                onRequestChange({
                  ...generationRequest,
                  topic: e.target.value,
                })
              }
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
              placeholder="e.g., Photosynthesis, Fractions, World War II"
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                Grade Level
              </label>
              <select
                value={generationRequest.gradeLevel}
                onChange={(e) =>
                  onRequestChange({
                    ...generationRequest,
                    gradeLevel: e.target.value,
                  })
                }
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
              >
                <option value="">Select grade level</option>
                <option value="Grade 1">Grade 1</option>
                <option value="Grade 2">Grade 2</option>
                <option value="Grade 3">Grade 3</option>
                <option value="Grade 4">Grade 4</option>
                <option value="Grade 5">Grade 5</option>
                <option value="Grade 6">Grade 6</option>
                <option value="Grade 7">Grade 7</option>
                <option value="Grade 8">Grade 8</option>
                <option value="Grade 9">Grade 9</option>
                <option value="Grade 10">Grade 10</option>
                <option value="Grade 11">Grade 11</option>
                <option value="Grade 12">Grade 12</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                Subject
              </label>
              <select
                value={generationRequest.subject}
                onChange={(e) =>
                  onRequestChange({
                    ...generationRequest,
                    subject: e.target.value,
                  })
                }
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
              >
                <option value="">Select subject</option>
                <option value="Mathematics">Mathematics</option>
                <option value="Science">Science</option>
                <option value="English">English</option>
                <option value="History">History</option>
                <option value="Geography">Geography</option>
                <option value="Physics">Physics</option>
                <option value="Chemistry">Chemistry</option>
                <option value="Biology">Biology</option>
                <option value="Computer Science">Computer Science</option>
              </select>
            </div>
          </div>

          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={onCancel}>
              Cancel
            </Button>
            <Button
              onClick={onGenerate}
              disabled={
                !generationRequest.topic ||
                !generationRequest.gradeLevel ||
                !generationRequest.subject ||
                isGenerating
              }
              className="bg-gradient-to-r from-purple-500 to-blue-500 hover:from-purple-600 hover:to-blue-600"
            >
              {isGenerating ? (
                <>
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                  Generating...
                </>
              ) : (
                <>
                  <Sparkles className="h-4 w-4 mr-2" />
                  Generate Content
                </>
              )}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
