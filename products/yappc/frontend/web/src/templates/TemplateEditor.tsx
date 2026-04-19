/**
 * Template Editor
 *
 * Template customization and editing UI.
 *
 * @packageDocumentation
 */

import { Edit as EditIcon, Save as SaveIcon, Eye as EyeIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  Button,
  TextField,
  Paper,
  Divider,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@ghatana/design-system';
import React, { useState, useCallback } from 'react';

import type { PageConfig } from '@yappc/config-schema';

import { ConfigEditor } from '../components/config-editor/ConfigEditor';

/**
 * @doc.type component
 * @doc.purpose Template customization and editing UI
 * @doc.layer product
 * @doc.pattern Editor Component
 */
interface TemplateEditorProps {
  template: PageConfig;
  onSave?: (template: PageConfig) => void;
  onCancel?: () => void;
  readOnly?: boolean;
}

export const TemplateEditor: React.FC<TemplateEditorProps> = ({
  template,
  onSave,
  onCancel,
  readOnly = false,
}) => {
  const [editedTemplate, setEditedTemplate] = useState<PageConfig>(template);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [hasChanges, setHasChanges] = useState(false);

  const handleTemplateChange = useCallback((newConfig: PageConfig) => {
    setEditedTemplate(newConfig);
    setHasChanges(JSON.stringify(newConfig) !== JSON.stringify(template));
  }, [template]);

  const handleSave = useCallback(() => {
    onSave?.(editedTemplate);
  }, [editedTemplate, onSave]);

  const handlePreview = useCallback(() => {
    setPreviewOpen(true);
  }, []);

  return (
    <Box data-testid="template-editor" className="h-full flex flex-col">
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <EditIcon size={16} />
          <Typography variant="h6">Template Editor</Typography>
        </Stack>

        <Stack direction="row" spacing={1}>
          <Button
            variant="outlined"
            startIcon={<EyeIcon size={14} />}
            onClick={handlePreview}
          >
            Preview
          </Button>
          {!readOnly && (
            <>
              <Button variant="outlined" onClick={onCancel}>
                Cancel
              </Button>
              <Button
                variant="contained"
                startIcon={<SaveIcon size={14} />}
                onClick={handleSave}
                disabled={!hasChanges}
              >
                Save Template
              </Button>
            </>
          )}
        </Stack>
      </Stack>

      <Divider />

      <Box className="flex-1 mt-4">
        <ConfigEditor
          value={editedTemplate}
          onChange={handleTemplateChange}
          readOnly={readOnly}
        />
      </Box>

      <Dialog open={previewOpen} onClose={() => setPreviewOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Template Preview</DialogTitle>
        <DialogContent>
          <Box className="p-4">
            <Typography variant="body2" color="textSecondary">
              This would render the actual preview of the template using ComponentRenderer.
              For now, this is a placeholder.
            </Typography>
            <Paper variant="outlined" className="mt-4 p-4">
              <Typography variant="subtitle2" gutterBottom>
                {editedTemplate.title}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                Route: {editedTemplate.route}
              </Typography>
              <Typography variant="caption" color="textSecondary" display="block">
                Components: {editedTemplate.components?.length || 0}
              </Typography>
            </Paper>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPreviewOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
