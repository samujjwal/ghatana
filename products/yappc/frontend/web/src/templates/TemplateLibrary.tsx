/**
 * Template Library
 *
 * Template marketplace UI for pre-built page patterns.
 *
 * @packageDocumentation
 */

import { LayoutTemplate as TemplateIcon, Search as SearchIcon, Filter as FilterIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  TextField,
  Button,
  Card,
  CardContent,
  CardActions,
  Grid,
  Chip,
  InputAdornment,
} from '@ghatana/design-system';
import React, { useState, useCallback } from 'react';

import type { PageConfig } from '@yappc/config-schema';

import { dashboardTemplate } from './dashboard';
import { formTemplate } from './form';
import { tableTemplate } from './table';

/**
 * @doc.type component
 * @doc.purpose Template marketplace UI for pre-built page patterns
 * @doc.layer product
 * @doc.pattern Container Component
 */
interface TemplateLibraryProps {
  onSelectTemplate?: (template: PageConfig) => void;
  onUseTemplate?: (template: PageConfig) => void;
}

interface Template {
  id: string;
  name: string;
  description: string;
  category: string;
  config: PageConfig;
  tags: string[];
}

export const TemplateLibrary: React.FC<TemplateLibraryProps> = ({ onSelectTemplate, onUseTemplate }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(null);

  const templates: Template[] = [
    {
      id: 'dashboard',
      name: 'Dashboard',
      description: 'Analytics dashboard with charts and statistics cards',
      category: 'dashboard',
      config: dashboardTemplate,
      tags: ['analytics', 'charts', 'statistics'],
    },
    {
      id: 'form',
      name: 'Form',
      description: 'Data entry form with validation',
      category: 'form',
      config: formTemplate,
      tags: ['form', 'input', 'validation'],
    },
    {
      id: 'table',
      name: 'Table',
      description: 'Data table with search and pagination',
      category: 'table',
      config: tableTemplate,
      tags: ['table', 'data', 'list'],
    },
  ];

  const categories = ['all', 'dashboard', 'form', 'table'];

  const filteredTemplates = templates.filter((template) => {
    const matchesSearch =
      searchQuery === '' ||
      template.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      template.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      template.tags.some((tag) => tag.toLowerCase().includes(searchQuery.toLowerCase()));

    const matchesCategory = selectedCategory === 'all' || template.category === selectedCategory;

    return matchesSearch && matchesCategory;
  });

  const handleSelectTemplate = useCallback(
    (template: Template) => {
      setSelectedTemplate(template);
      onSelectTemplate?.(template.config);
    },
    [onSelectTemplate]
  );

  const handleUseTemplate = useCallback(() => {
    if (selectedTemplate) {
      onUseTemplate?.(selectedTemplate.config);
    }
  }, [selectedTemplate, onUseTemplate]);

  return (
    <Box data-testid="template-library" className="p-4">
      <Stack direction="row" alignItems="center" spacing={1} mb={3}>
        <TemplateIcon size={16} />
        <Typography variant="h6">Template Library</Typography>
      </Stack>

      <Stack direction="row" spacing={2} mb={4}>
        <TextField
          placeholder="Search templates..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon size={16} />
              </InputAdornment>
            ),
          }}
          fullWidth
          size="small"
        />

        <Stack direction="row" spacing={1}>
          {categories.map((category) => (
            <Chip
              key={category}
              label={category.charAt(0).toUpperCase() + category.slice(1)}
              onClick={() => setSelectedCategory(category)}
              variant={selectedCategory === category ? 'filled' : 'outlined'}
              size="small"
            />
          ))}
        </Stack>
      </Stack>

      <Grid container spacing={3}>
        {filteredTemplates.map((template) => (
          <Grid item xs={12} sm={6} md={4} key={template.id}>
            <Card
              variant={selectedTemplate?.id === template.id ? 'outlined' : 'elevation'}
              sx={{
                cursor: 'pointer',
                borderColor: selectedTemplate?.id === template.id ? 'primary.main' : 'divider',
                borderWidth: selectedTemplate?.id === template.id ? 2 : 1,
              }}
              onClick={() => handleSelectTemplate(template)}
              data-testid={`template-card-${template.id}`}
            >
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  {template.name}
                </Typography>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {template.description}
                </Typography>
                <Stack direction="row" spacing={1} mt={2}>
                  {template.tags.map((tag) => (
                    <Chip key={tag} label={tag} size="small" variant="outlined" />
                  ))}
                </Stack>
              </CardContent>
              <CardActions>
                <Button size="small" onClick={() => handleSelectTemplate(template)}>
                  Preview
                </Button>
                <Button size="small" variant="contained" onClick={() => onUseTemplate?.(template.config)}>
                  Use Template
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>

      {filteredTemplates.length === 0 && (
        <Box textAlign="center" py={8}>
          <Typography color="textSecondary">No templates found matching your search</Typography>
        </Box>
      )}
    </Box>
  );
};
