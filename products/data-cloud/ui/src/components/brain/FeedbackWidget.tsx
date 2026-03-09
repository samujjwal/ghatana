/**
 * Feedback Widget Component
 *
 * Allows users to provide corrections and feedback to the AI system.
 * Part of Journey 3: Teaching the Brain (Feedback & Learning)
 *
 * @doc.type component
 * @doc.purpose User feedback and correction interface
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { X, Send, CheckCircle, AlertCircle } from 'lucide-react';
import { brainService, FeedbackEvent } from '../../api/brain.service';
import { Button } from '../common/Button';

interface FeedbackWidgetProps {
  eventType: string;
  incorrectValue?: string;
  context?: Record<string, any>;
  onClose?: () => void;
  onSubmitted?: () => void;
}

export function FeedbackWidget({
  eventType,
  incorrectValue,
  context = {},
  onClose,
  onSubmitted,
}: FeedbackWidgetProps) {
  const queryClient = useQueryClient();
  const [correctValue, setCorrectValue] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  const [notes, setNotes] = useState('');
  const [showSuccess, setShowSuccess] = useState(false);

  const submitFeedback = useMutation({
    mutationFn: (feedback: FeedbackEvent) => brainService.submitFeedback(feedback),
    onSuccess: () => {
      setShowSuccess(true);
      queryClient.invalidateQueries({ queryKey: ['learning-signals'] });
      setTimeout(() => {
        onSubmitted?.();
        onClose?.();
      }, 2000);
    },
  });

  const handleAddTag = () => {
    if (tagInput.trim() && !tags.includes(tagInput.trim())) {
      setTags([...tags, tagInput.trim()]);
      setTagInput('');
    }
  };

  const handleRemoveTag = (tag: string) => {
    setTags(tags.filter((t) => t !== tag));
  };

  const handleSubmit = () => {
    if (!correctValue.trim()) return;

    const feedback: FeedbackEvent = {
      eventType,
      correctValue: correctValue.trim(),
      incorrectValue,
      context: {
        ...context,
        notes: notes.trim() || undefined,
      },
      tags,
    };

    submitFeedback.mutate(feedback);
  };

  if (showSuccess) {
    return (
      <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
        <div className="bg-white rounded-lg p-8 max-w-md w-full mx-4 text-center">
          <div className="flex justify-center mb-4">
            <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center">
              <CheckCircle className="h-10 w-10 text-green-600" />
            </div>
          </div>
          <h3 className="text-xl font-bold text-gray-900 mb-2">
            Feedback Received!
          </h3>
          <p className="text-gray-600">
            The system is learning from your correction. Thank you for helping improve accuracy.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <div className="flex items-center gap-3">
            <AlertCircle className="h-6 w-6 text-orange-500" />
            <h2 className="text-xl font-bold text-gray-900">
              Provide Feedback
            </h2>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <X className="h-6 w-6" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-6">
          {/* Event Type */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Event Type
            </label>
            <div className="px-3 py-2 bg-gray-50 border border-gray-200 rounded text-sm text-gray-900">
              {eventType}
            </div>
          </div>

          {/* Incorrect Value */}
          {incorrectValue && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                System's Classification
              </label>
              <div className="px-3 py-2 bg-red-50 border border-red-200 rounded text-sm text-red-900">
                {incorrectValue}
              </div>
            </div>
          )}

          {/* Correct Value */}
          <div>
            <label
              htmlFor="correctValue"
              className="block text-sm font-medium text-gray-700 mb-1"
            >
              Correct Value <span className="text-red-500">*</span>
            </label>
            <input
              id="correctValue"
              type="text"
              value={correctValue}
              onChange={(e) => setCorrectValue(e.target.value)}
              placeholder="Enter the correct classification"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
            />
          </div>

          {/* Tags */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Tags (Optional)
            </label>
            <div className="flex gap-2 mb-2">
              <input
                type="text"
                value={tagInput}
                onChange={(e) => setTagInput(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleAddTag()}
                placeholder="Add a tag (e.g., holiday-shopping)"
                className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
              />
              <Button variant="outline" size="md" onClick={handleAddTag}>
                Add
              </Button>
            </div>
            {tags.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {tags.map((tag) => (
                  <span
                    key={tag}
                    className="inline-flex items-center gap-1 px-3 py-1 bg-blue-100 text-blue-700 rounded-full text-sm"
                  >
                    {tag}
                    <button
                      onClick={() => handleRemoveTag(tag)}
                      className="hover:text-blue-900"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>

          {/* Notes */}
          <div>
            <label
              htmlFor="notes"
              className="block text-sm font-medium text-gray-700 mb-1"
            >
              Additional Notes (Optional)
            </label>
            <textarea
              id="notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Provide any additional context or explanation..."
              rows={4}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
            />
          </div>

          {/* Info Banner */}
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
            <div className="flex gap-3">
              <AlertCircle className="h-5 w-5 text-blue-600 mt-0.5" />
              <div className="flex-1">
                <h4 className="text-sm font-semibold text-blue-900 mb-1">
                  How Your Feedback Helps
                </h4>
                <p className="text-sm text-blue-800">
                  Your correction will generate a learning signal that adjusts the system's
                  confidence scores and reflex rules. This helps prevent similar errors in the future.
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 p-6 border-t border-gray-200 bg-gray-50">
          <Button variant="outline" size="md" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            size="md"
            onClick={handleSubmit}
            disabled={!correctValue.trim()}
            isLoading={submitFeedback.isPending}
          >
            <Send className="h-4 w-4" />
            Submit Feedback
          </Button>
        </div>
      </div>
    </div>
  );
}

export default FeedbackWidget;

