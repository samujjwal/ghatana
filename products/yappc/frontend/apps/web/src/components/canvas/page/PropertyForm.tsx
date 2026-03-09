import { zodResolver } from '@hookform/resolvers/zod';
import {
  Box,
  Select,
  FormControl,
  InputLabel,
  Switch,
  FormControlLabel,
  Slider,
  Typography,
  Button,
  Stack,
} from '@ghatana/ui';
import { TextField, MenuItem } from '@ghatana/ui';
import React, { useEffect } from 'react';
import { useForm, Controller } from 'react-hook-form';

import { getSchemaForType } from './schemas';

import type { ComponentData} from './schemas';
import type { z } from 'zod';


/**
 *
 */
interface PropertyFormProps {
  componentData: ComponentData;
  onUpdate: (data: ComponentData) => void;
  onCancel?: () => void;
}

export const PropertyForm: React.FC<PropertyFormProps> = ({
  componentData,
  onUpdate,
  onCancel,
}) => {
  const schema = getSchemaForType(componentData.type);

  if (!schema) {
    return (
      <Box p={2}>
        <Typography color="error">No schema found for type: {componentData.type}</Typography>
      </Box>
    );
  }

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm({
    resolver: zodResolver(schema),
    defaultValues: componentData,
  });

  useEffect(() => {
    reset(componentData);
  }, [componentData, reset]);

  const onSubmit = (data: unknown) => {
    onUpdate(data as ComponentData);
  };

  const renderField = (fieldName: string, fieldSchema: z.ZodTypeAny) => {
    const fieldDef = fieldSchema._def;

    // Handle enum fields
    if (fieldDef.typeName === 'ZodEnum') {
      return (
        <Controller
          name={fieldName}
          control={control}
          render={({ field }) => (
            <FormControl fullWidth size="small" error={!!errors[fieldName]}>
              <InputLabel>{fieldName}</InputLabel>
              <Select
                {...field}
                label={fieldName}
                inputProps={{ 'data-testid': `property-${fieldName.toLowerCase()}` }}
              >
                {fieldDef.values.map((value: string) => (
                  <MenuItem key={value} value={value}>
                    {value}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}
        />
      );
    }

    // Handle boolean fields
    if (fieldDef.typeName === 'ZodBoolean') {
      return (
        <Controller
          name={fieldName}
          control={control}
          render={({ field }) => (
            <FormControlLabel
              control={<Switch {...field} checked={field.value} />}
              label={fieldName}
            />
          )}
        />
      );
    }

    // Handle number fields
    if (fieldDef.typeName === 'ZodNumber') {
      const min = fieldDef.checks?.find((c: unknown) => c.kind === 'min')?.value ?? 0;
      const max = fieldDef.checks?.find((c: unknown) => c.kind === 'max')?.value ?? 100;

      return (
        <Controller
          name={fieldName}
          control={control}
          render={({ field }) => (
            <Box>
              <Typography variant="caption" gutterBottom>
                {fieldName}: {field.value}
              </Typography>
              <Slider
                {...field}
                min={min}
                max={max}
                valueLabelDisplay="auto"
                size="small"
              />
            </Box>
          )}
        />
      );
    }

    // Handle string fields (default)
    return (
      <Controller
        name={fieldName}
        control={control}
        render={({ field }) => (
          <TextField
            {...field}
            label={fieldName}
            fullWidth
            size="small"
            error={!!errors[fieldName]}
            helperText={errors[fieldName]?.message as string}
            inputProps={{ 'data-testid': `property-${fieldName.toLowerCase()}` }}
          />
        )}
      />
    );
  };

  const renderFormFields = () => {
    if (!schema || schema._def.typeName !== 'ZodObject') {
      return null;
    }

    const shape = (schema as z.ZodObject<unknown>).shape;
    const fields = Object.entries(shape).filter(
      ([key]) => !['id', 'type'].includes(key),
    );

    return fields.map(([fieldName, fieldSchema]) => (
      <Box key={fieldName} mb={2}>
        {renderField(fieldName, fieldSchema as z.ZodTypeAny)}
      </Box>
    ));
  };

  return (
    <Box
      component="form"
      onSubmit={handleSubmit(onSubmit)}
      className="p-4 overflow-y-auto max-h-[70vh]" >
      <Typography variant="h6" gutterBottom>
        Edit {componentData.type}
      </Typography>

      {renderFormFields()}

      <Stack direction="row" spacing={2} mt={3}>
        <Button
          type="submit"
          variant="contained"
          disabled={!isDirty}
          fullWidth
        >
          Apply
        </Button>
        {onCancel && (
          <Button variant="outlined" onClick={onCancel} fullWidth>
            Cancel
          </Button>
        )}
      </Stack>
    </Box>
  );
};
