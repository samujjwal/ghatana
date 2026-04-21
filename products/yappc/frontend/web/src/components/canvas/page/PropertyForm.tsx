import {
  Box,
  Button,
  FormControl,
  FormControlLabel,
  InputLabel,
  Slider,
  Switch,
  TextField,
  Typography,
} from '@ghatana/design-system';
import { MenuItem } from '@ghatana/design-system';
import React, { useEffect, useMemo, useState } from 'react';

import { getSchemaForType } from './schemas';

import type { ComponentData } from './schemas';

interface PropertyFormProps {
  componentData: ComponentData;
  onUpdate: (data: ComponentData) => void;
  onCancel?: () => void;
}

type FieldType = 'text' | 'number' | 'boolean' | 'select';

interface FieldConfig {
  name: string;
  label: string;
  type: FieldType;
  options?: string[];
  min?: number;
  max?: number;
}

const FIELD_CONFIG: Record<ComponentData['type'], FieldConfig[]> = {
  button: [
    { name: 'label', label: 'Label', type: 'text' },
    { name: 'text', label: 'Text', type: 'text' },
    {
      name: 'variant',
      label: 'Variant',
      type: 'select',
      options: ['contained', 'outlined', 'text'],
    },
    {
      name: 'color',
      label: 'Color',
      type: 'select',
      options: ['primary', 'secondary', 'success', 'error', 'info', 'warning'],
    },
    {
      name: 'size',
      label: 'Size',
      type: 'select',
      options: ['small', 'medium', 'large'],
    },
    { name: 'disabled', label: 'Disabled', type: 'boolean' },
    { name: 'fullWidth', label: 'Full Width', type: 'boolean' },
  ],
  card: [
    { name: 'label', label: 'Label', type: 'text' },
    { name: 'title', label: 'Title', type: 'text' },
    { name: 'subtitle', label: 'Subtitle', type: 'text' },
    { name: 'content', label: 'Content', type: 'text' },
    { name: 'elevation', label: 'Elevation', type: 'number', min: 0, max: 24 },
    { name: 'showActions', label: 'Show Actions', type: 'boolean' },
  ],
  textfield: [
    { name: 'label', label: 'Label', type: 'text' },
    { name: 'placeholder', label: 'Placeholder', type: 'text' },
    {
      name: 'variant',
      label: 'Variant',
      type: 'select',
      options: ['outlined', 'filled', 'standard'],
    },
    {
      name: 'size',
      label: 'Size',
      type: 'select',
      options: ['small', 'medium'],
    },
    { name: 'required', label: 'Required', type: 'boolean' },
    { name: 'disabled', label: 'Disabled', type: 'boolean' },
    { name: 'fullWidth', label: 'Full Width', type: 'boolean' },
    { name: 'multiline', label: 'Multiline', type: 'boolean' },
    { name: 'rows', label: 'Rows', type: 'number', min: 1, max: 20 },
  ],
  typography: [
    { name: 'label', label: 'Label', type: 'text' },
    { name: 'text', label: 'Text', type: 'text' },
    {
      name: 'variant',
      label: 'Variant',
      type: 'select',
      options: [
        'h1',
        'h2',
        'h3',
        'h4',
        'h5',
        'h6',
        'subtitle1',
        'subtitle2',
        'body1',
        'body2',
        'caption',
        'overline',
      ],
    },
    {
      name: 'color',
      label: 'Color',
      type: 'select',
      options: ['primary', 'secondary', 'textPrimary', 'textSecondary', 'error'],
    },
    {
      name: 'align',
      label: 'Align',
      type: 'select',
      options: ['left', 'center', 'right', 'justify'],
    },
  ],
  box: [
    { name: 'label', label: 'Label', type: 'text' },
    { name: 'padding', label: 'Padding', type: 'number', min: 0, max: 10 },
    { name: 'margin', label: 'Margin', type: 'number', min: 0, max: 10 },
    { name: 'backgroundColor', label: 'Background Color', type: 'text' },
    {
      name: 'borderRadius',
      label: 'Border Radius',
      type: 'number',
      min: 0,
      max: 10,
    },
    {
      name: 'display',
      label: 'Display',
      type: 'select',
      options: ['block', 'flex', 'inline-flex', 'grid'],
    },
    {
      name: 'flexDirection',
      label: 'Flex Direction',
      type: 'select',
      options: ['row', 'column', 'row-reverse', 'column-reverse'],
    },
    {
      name: 'justifyContent',
      label: 'Justify Content',
      type: 'select',
      options: ['flex-start', 'center', 'flex-end', 'space-between', 'space-around'],
    },
    {
      name: 'alignItems',
      label: 'Align Items',
      type: 'select',
      options: ['flex-start', 'center', 'flex-end', 'stretch'],
    },
  ],
};

function coerceFieldValue(
  field: FieldConfig,
  rawValue: string | boolean,
): string | number | boolean {
  if (field.type === 'boolean') {
    return typeof rawValue === 'boolean' ? rawValue : rawValue === 'true';
  }

  if (field.type === 'number') {
    if (typeof rawValue === 'boolean' || rawValue === '') {
      return field.min ?? 0;
    }

    const parsed = Number(rawValue);
    if (Number.isNaN(parsed)) {
      return field.min ?? 0;
    }

    if (typeof field.min === 'number' && parsed < field.min) {
      return field.min;
    }

    if (typeof field.max === 'number' && parsed > field.max) {
      return field.max;
    }

    return parsed;
  }

  return typeof rawValue === 'string' ? rawValue : String(rawValue);
}

function getFieldValue(
  componentData: ComponentData,
  fieldName: string,
): string | number | boolean {
  const value = componentData[fieldName as keyof ComponentData];

  if (
    typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean'
  ) {
    return value;
  }

  return '';
}

export const PropertyForm: React.FC<PropertyFormProps> = ({
  componentData,
  onUpdate,
  onCancel,
}) => {
  const schema = getSchemaForType(componentData.type);
  const fields = useMemo(() => FIELD_CONFIG[componentData.type], [componentData.type]);
  const [draft, setDraft] = useState<ComponentData>(componentData);
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    setDraft(componentData);
    setValidationErrors({});
  }, [componentData]);

  if (!schema) {
    return (
      <Box p={2}>
        <Typography color="error">
          No schema found for type: {componentData.type}
        </Typography>
      </Box>
    );
  }

  const isDirty = JSON.stringify(draft) !== JSON.stringify(componentData);

  const handleFieldChange = (field: FieldConfig, rawValue: string | boolean) => {
    const nextValue = coerceFieldValue(field, rawValue);
    setDraft((current) => ({
      ...current,
      [field.name]: nextValue,
    }));
  };

  const handleFormSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const result = schema.safeParse(draft);
    if (!result.success) {
      setValidationErrors(
        Object.fromEntries(
          result.error.issues.map((issue) => [issue.path.join('.'), issue.message]),
        ),
      );
      return;
    }

    setValidationErrors({});
    onUpdate(result.data as ComponentData);
  };

  const renderField = (field: FieldConfig) => {
    const fieldValue = getFieldValue(draft, field.name);
    const errorMessage = validationErrors[field.name];

    if (field.type === 'select') {
      return (
        <FormControl fullWidth size="small">
          <TextField
            select
            label={field.label}
            value={String(fieldValue)}
            fullWidth
            size="small"
            errorMessage={errorMessage}
            onChange={(event) => {
              handleFieldChange(field, String(event.target.value));
            }}
            data-testid={`property-${field.name.toLowerCase()}`}
          >
            {(field.options ?? []).map((value) => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </TextField>
        </FormControl>
      );
    }

    if (field.type === 'boolean') {
      return (
        <FormControlLabel
          control={
            <Switch
              checked={Boolean(fieldValue)}
              onChange={(event) => {
                handleFieldChange(field, event.target.checked);
              }}
            />
          }
          label={field.label}
        />
      );
    }

    if (field.type === 'number') {
      return (
        <Box>
          <Typography variant="caption" gutterBottom>
            {field.label}: {fieldValue}
          </Typography>
          <Slider
            min={field.min ?? 0}
            max={field.max ?? 100}
            value={typeof fieldValue === 'number' ? fieldValue : field.min ?? 0}
            size="sm"
            onChange={(event) => {
              handleFieldChange(field, event.target.value);
            }}
          />
        </Box>
      );
    }

    return (
      <TextField
        label={field.label}
        value={String(fieldValue)}
        fullWidth
        size="small"
        errorMessage={errorMessage}
        data-testid={`property-${field.name.toLowerCase()}`}
        onChange={(event) => {
          handleFieldChange(field, event.target.value);
        }}
      />
    );
  };

  return (
    <form onSubmit={handleFormSubmit} className="max-h-[70vh] overflow-y-auto p-4">
      <Typography variant="h6" gutterBottom>
        Edit {componentData.type}
      </Typography>

      {fields.map((field) => (
        <Box key={field.name} mb={2}>
          {renderField(field)}
        </Box>
      ))}

      <div className="mt-3 flex gap-2">
        <Button type="submit" variant="contained" disabled={!isDirty} fullWidth>
          Apply
        </Button>
        {onCancel && (
          <Button type="button" variant="outlined" onClick={onCancel} fullWidth>
            Cancel
          </Button>
        )}
      </div>
    </form>
  );
};
