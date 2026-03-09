/**
 * Template Library Dialog Component
 * Feature 1.4: Document Management - UI Component
 * 
 * Provides UI for browsing, filtering, and loading document templates.
 * Integrates with historyManager utilities for template operations.
 */

import { X as CloseIcon, CloudUpload as UploadIcon, Trash2 as DeleteIcon, Pencil as EditIcon } from 'lucide-react';
import {
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Typography,
} from '@ghatana/ui';
import { CardMedia, TextField } from '@ghatana/ui';
import { SelectTailwind as Select, SelectOption } from '@ghatana/yappc-ui';
import { useCallback, useMemo, useState } from 'react';

import type { DocumentTemplate } from '../history/historyManager';

/**
 *
 */
export interface TemplateLibraryDialogProps<T = unknown> {
  open: boolean;
  onClose: () => void;
  templates: DocumentTemplate<T>[];
  onLoadTemplate: (template: DocumentTemplate<T>) => void;
  onSaveAsTemplate?: (name: string, description: string, category: string, tags: string[]) => void;
  onDeleteTemplate?: (templateId: string) => void;
  onEditTemplate?: (templateId: string, updates: Partial<DocumentTemplate<T>>) => void;
  currentState?: T;
  categories?: string[];
}

/**
 * Template Library Dialog
 * 
 * Main UI component for managing document templates.
 * Features:
 * - Grid view of templates with previews
 * - Search and category filtering
 * - Save current canvas as template
 * - Load template into canvas
 * - Edit/delete existing templates
 */
export function TemplateLibraryDialog<T = unknown>({
  open,
  onClose,
  templates,
  onLoadTemplate,
  onSaveAsTemplate,
  onDeleteTemplate,
  onEditTemplate,
  currentState,
  categories = ['General', 'Architecture', 'Flowchart', 'UML', 'Network'],
}: TemplateLibraryDialogProps<T>) {
  const defaultCategory = categories[0] ?? 'General';

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [showSaveDialog, setShowSaveDialog] = useState(false);
  const [newTemplateName, setNewTemplateName] = useState('');
  const [newTemplateDescription, setNewTemplateDescription] = useState('');
  const [newTemplateCategory, setNewTemplateCategory] = useState(defaultCategory);
  const [newTemplateTags, setNewTemplateTags] = useState('');

  const filteredTemplates = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();

    return templates.filter((template) => {
      const matchesSearch =
        query.length === 0 ||
        template.name.toLowerCase().includes(query) ||
        template.description?.toLowerCase().includes(query) ||
        template.tags?.some((tag) => tag.toLowerCase().includes(query));

      const matchesCategory = selectedCategory === 'all' || template.category === selectedCategory;

      return matchesSearch && matchesCategory;
    });
  }, [templates, searchQuery, selectedCategory]);

  const resetSaveForm = useCallback(() => {
    setNewTemplateName('');
    setNewTemplateDescription('');
    setNewTemplateCategory(defaultCategory);
    setNewTemplateTags('');
  }, [defaultCategory]);

  const handleSaveDialogChange = useCallback((nextOpen: boolean) => {
    if (!nextOpen) {
      resetSaveForm();
    }
    setShowSaveDialog(nextOpen);
  }, [resetSaveForm]);

  const handleSaveTemplate = useCallback(() => {
    if (!onSaveAsTemplate || !newTemplateName.trim()) {
      return;
    }

    const tags = newTemplateTags
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean);

    onSaveAsTemplate(
      newTemplateName.trim(),
      newTemplateDescription.trim(),
      newTemplateCategory,
      tags,
    );

    handleSaveDialogChange(false);
  }, [handleSaveDialogChange, newTemplateCategory, newTemplateDescription, newTemplateName, newTemplateTags, onSaveAsTemplate]);

  const handleLoadTemplate = useCallback((template: DocumentTemplate<T>) => {
    onLoadTemplate(template);
    onClose();
  }, [onClose, onLoadTemplate]);

  return (
    <>
      <Dialog
        open={open && !showSaveDialog}
        onClose={onClose}
        fullWidth
        maxWidth="lg"
      >
        <DialogTitle>
          <div className="flex items-center justify-between gap-3">
            <Typography variant="h6" className="text-grey-900">
              Template Library
            </Typography>
            <div className="flex items-center gap-2">
              {onSaveAsTemplate && currentState && (
                <Button
                  variant="contained"
                  size="small"
                  startIcon={<UploadIcon size={16} />}
                  onClick={() => setShowSaveDialog(true)}
                  data-testid="save-as-template-btn"
                >
                  Save as Template
                </Button>
              )}
              <IconButton
                size="small"
                aria-label="Close template library"
                onClick={onClose}
              >
                <CloseIcon size={16} />
              </IconButton>
            </div>
          </div>
        </DialogTitle>

        <DialogContent dividers>
          <div className="flex flex-col gap-6">
            <div className="flex flex-col gap-4 xl:flex-row xl:items-end">
              <div className="w-full">
                <TextField
                  label="Search"
                  placeholder="Search templates..."
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.currentTarget.value)}
                  data-testid="template-search-input"
                />
              </div>
              <div className="w-full xl:w-64">
                <Select
                  label="Category"
                  value={selectedCategory}
                  onChange={(value) => setSelectedCategory(value ?? 'all')}
                  data-testid="category-filter-select"
                >
                  <SelectOption value="all">All Categories</SelectOption>
                  {categories.map((category) => (
                    <SelectOption key={category} value={category}>
                      {category}
                    </SelectOption>
                  ))}
                </Select>
              </div>
            </div>

            <Typography variant="body2" className="text-grey-600">
              {filteredTemplates.length} template{filteredTemplates.length === 1 ? '' : 's'} found
            </Typography>

            {filteredTemplates.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-grey-300 py-12 text-center">
                <Typography variant="h6" className="text-grey-600">
                  No templates found
                </Typography>
                <Typography variant="body2" className="text-grey-600">
                  {searchQuery || selectedCategory !== 'all'
                    ? 'Try adjusting your filters'
                    : 'Save your first template to get started'}
                </Typography>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                {filteredTemplates.map((template) => (
                  <Card
                    key={template.id}
                    className="flex h-full flex-col overflow-hidden transition-shadow hover:shadow-lg"
                    data-testid={`template-card-${template.id}`}
                  >
                    {template.preview && (
                      <CardMedia
                        component="img"
                        image={template.preview}
                        alt={template.name}
                        className="h-[144px] object-cover"
                      />
                    )}

                    <CardContent className="flex flex-1 flex-col gap-3">
                      <Typography variant="h6" className="truncate">
                        {template.name}
                      </Typography>
                      <Typography
                        variant="body2"
                        className="text-grey-600"
                        style={{
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical',
                          overflow: 'hidden',
                        }}
                      >
                        {template.description || 'No description'}
                      </Typography>

                      <div className="flex flex-wrap gap-2">
                        {template.category && (
                          <Chip
                            label={template.category}
                            size="small"
                            variant="outlined"
                            color="primary"
                          />
                        )}
                        {template.tags?.slice(0, 3).map((tag) => (
                          <Chip key={tag} label={tag} size="small" variant="outlined" />
                        ))}
                        {template.tags && template.tags.length > 3 && (
                          <Chip label={`+${template.tags.length - 3}`} size="small" variant="outlined" />
                        )}
                      </div>
                    </CardContent>

                    <CardActions className="flex items-center justify-between border-t border-grey-200 px-4 py-3">
                      <Button
                        variant="contained"
                        size="small"
                        onClick={() => handleLoadTemplate(template)}
                        data-testid={`load-template-btn-${template.id}`}
                      >
                        Load
                      </Button>

                      <div className="flex items-center gap-2">
                        {onEditTemplate && (
                          <IconButton
                            size="small"
                            aria-label={`Edit template ${template.name}`}
                            onClick={() => onEditTemplate(template.id, {})}
                            data-testid={`edit-template-btn-${template.id}`}
                          >
                            <EditIcon size={16} />
                          </IconButton>
                        )}

                        {onDeleteTemplate && (
                          <IconButton
                            size="small"
                            color="error"
                            aria-label={`Delete template ${template.name}`}
                            onClick={() => onDeleteTemplate(template.id)}
                            data-testid={`delete-template-btn-${template.id}`}
                          >
                            <DeleteIcon size={16} />
                          </IconButton>
                        )}
                      </div>
                    </CardActions>
                  </Card>
                ))}
              </div>
            )}
          </div>
        </DialogContent>
      </Dialog>

      <Dialog
        open={showSaveDialog}
        onClose={() => handleSaveDialogChange(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Save as Template</DialogTitle>
        <DialogContent dividers>
          <div className="flex flex-col gap-4 pt-2">
            <TextField
              label="Template Name"
              value={newTemplateName}
              onChange={(event) => setNewTemplateName(event.currentTarget.value)}
              required
              autoFocus
              data-testid="template-name-input"
            />
            <TextField
              label="Description"
              value={newTemplateDescription}
              onChange={(event) => setNewTemplateDescription(event.currentTarget.value)}
              placeholder="Add a brief description"
              data-testid="template-description-input"
            />
            <Select
              label="Category"
              value={newTemplateCategory}
              onChange={(value) => setNewTemplateCategory(value ?? defaultCategory)}
              data-testid="template-category-select"
            >
              {categories.map((category) => (
                <SelectOption key={category} value={category}>
                  {category}
                </SelectOption>
              ))}
            </Select>
            <TextField
              label="Tags"
              value={newTemplateTags}
              onChange={(event) => setNewTemplateTags(event.currentTarget.value)}
              placeholder="Comma-separated tags"
              helperText="Separate tags with commas"
              data-testid="template-tags-input"
            />
          </div>
        </DialogContent>
        <DialogActions>
          <Button
            variant="text"
            size="small"
            color="inherit"
            onClick={() => handleSaveDialogChange(false)}
          >
            Cancel
          </Button>
          <Button
            variant="contained"
            size="small"
            onClick={handleSaveTemplate}
            disabled={!newTemplateName.trim()}
            data-testid="save-template-confirm-btn"
          >
            Save Template
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
