/**
 * Template library component.
 *
 * <p><b>Purpose</b><br>
 * Displays workflow templates for quick workflow creation.
 * Provides search, filtering, and template instantiation.
 *
 * <p><b>Architecture</b><br>
 * - Template browsing
 * - Search and filtering
 * - Template instantiation
 * - Category organization
 *
 * @doc.type component
 * @doc.purpose Workflow template library
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React, { useEffect, useState, useMemo } from 'react';
import { useSetAtom } from 'jotai';
import { loadWorkflowAtom } from '../stores/workflow.store';
import { workflowClient } from '../../../lib/api/workflow-client';
import type { WorkflowTemplate } from '../types/workflow.types';

/**
 * TemplateLibrary component props.
 *
 * @doc.type interface
 */
export interface TemplateLibraryProps {
  onTemplateApplied?: (templateId: string) => void;
}

/**
 * TemplateLibrary component.
 *
 * Displays workflow templates for quick creation.
 *
 * @param props component props
 * @returns JSX element
 *
 * @doc.type function
 */
export const TemplateLibrary: React.FC<TemplateLibraryProps> = ({ onTemplateApplied }) => {
  const loadWorkflow = useSetAtom(loadWorkflowAtom);

  const [templates, setTemplates] = useState<WorkflowTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  /**
   * Fetches templates.
   */
  useEffect(() => {
    const fetchTemplates = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await workflowClient.getTemplates();
        setTemplates(response.templates);
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Failed to fetch templates';
        setError(message);
      } finally {
        setLoading(false);
      }
    };

    fetchTemplates();
  }, []);

  /**
   * Filtered templates.
   */
  const filteredTemplates = useMemo(() => {
    return templates.filter((template) => {
      const matchesSearch =
        template.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        template.description.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesCategory = !selectedCategory || template.category === selectedCategory;
      return matchesSearch && matchesCategory;
    });
  }, [templates, searchTerm, selectedCategory]);

  /**
   * Unique categories.
   */
  const categories = useMemo(
    () => [...new Set(templates.map((t) => t.category))],
    [templates]
  );

  /**
   * Handles template apply.
   */
  const handleApply = (template: WorkflowTemplate) => {
    loadWorkflow(template.workflow);
    onTemplateApplied?.(template.id);
  };

  return (
    <div className="flex flex-col h-full bg-white border-l border-gray-200">
      {/* Header */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="font-semibold text-gray-900 mb-3">Templates</h3>

        {/* Search */}
        <input
          type="text"
          placeholder="Search templates..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {/* Categories */}
      <div className="px-4 py-2 border-b border-gray-200 flex flex-wrap gap-2 overflow-x-auto">
        <button
          onClick={() => setSelectedCategory(null)}
          className={`px-3 py-1 text-xs rounded-full whitespace-nowrap transition-colors ${
            selectedCategory === null
              ? 'bg-blue-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          All
        </button>
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setSelectedCategory(cat)}
            className={`px-3 py-1 text-xs rounded-full whitespace-nowrap transition-colors ${
              selectedCategory === cat
                ? 'bg-blue-500 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-4">
        {loading && (
          <div className="text-center py-8 text-gray-500">
            <p className="text-sm">Loading templates...</p>
          </div>
        )}

        {error && (
          <div className="p-3 bg-red-50 rounded-lg border border-red-200">
            <p className="text-xs text-red-700">{error}</p>
          </div>
        )}

        {!loading && filteredTemplates.length === 0 && !error && (
          <div className="text-center py-8 text-gray-500">
            <p className="text-sm">No templates found</p>
          </div>
        )}

        {/* Templates */}
        <div className="grid grid-cols-1 gap-3">
          {filteredTemplates.map((template) => (
            <div
              key={template.id}
              onClick={() => setSelectedId(selectedId === template.id ? null : template.id)}
              className={`p-3 rounded-lg border-2 cursor-pointer transition-all ${
                selectedId === template.id
                  ? 'border-blue-500 bg-blue-50'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <div className="flex items-start gap-2">
                {template.preview && (
                  <img
                    src={template.preview}
                    alt={template.name}
                    className="w-12 h-12 rounded object-cover"
                  />
                )}
                <div className="flex-1 min-w-0">
                  <h4 className="font-medium text-sm text-gray-900">{template.name}</h4>
                  <p className="text-xs text-gray-600 mt-1 line-clamp-2">
                    {template.description}
                  </p>
                  <div className="flex flex-wrap gap-1 mt-2">
                    {template.tags?.slice(0, 3).map((tag) => (
                      <span
                        key={tag}
                        className="px-2 py-0.5 text-xs bg-gray-100 text-gray-700 rounded"
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              {/* Details */}
              {selectedId === template.id && (
                <div className="mt-3 pt-3 border-t border-gray-200">
                  <div className="text-xs text-gray-600 mb-3">
                    <p>
                      <strong>Nodes:</strong> {template.workflow.nodes.length}
                    </p>
                    <p>
                      <strong>Edges:</strong> {template.workflow.edges.length}
                    </p>
                  </div>

                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleApply(template);
                    }}
                    className="w-full px-3 py-2 text-sm font-medium text-white bg-blue-500 hover:bg-blue-600 rounded"
                  >
                    Use Template
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default TemplateLibrary;
