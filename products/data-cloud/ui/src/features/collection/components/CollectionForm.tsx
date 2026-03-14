import React from 'react';
import { useForm, SubmitHandler } from 'react-hook-form';
import { Button, TextField, TextArea, Switch } from '@ghatana/design-system';
import { Plus, Trash2 } from 'lucide-react';

// Local label keeps this form lightweight while the design-system field layer stays optional.
const Label = ({ htmlFor, children }: { htmlFor: string; children: React.ReactNode }) => (
  <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor={htmlFor}>
    {children}
  </label>
);

interface CollectionFormProps {
  initialData?: {
    name: string;
    description: string;
    isActive?: boolean;
    schema: {
      name?: string;
      fields: Array<{
        name: string;
        type: string;
        required?: boolean;
        description?: string;
      }>;
    };
  };
  onSubmit: (data: CollectionFormData) => void;
  onCancel: () => void;
  isSubmitting: boolean;
}

export interface CollectionFormData {
  name: string;
  description: string;
  isActive: boolean;
  schema: {
    name: string;
    fields: Array<{
      name: string;
      type: string;
      required: boolean;
      description?: string;
    }>;
  };
}

export function CollectionForm({ initialData, onSubmit, onCancel, isSubmitting }: CollectionFormProps) {
  const { register, handleSubmit, formState: { errors }, watch, setValue } = useForm<CollectionFormData>({
    defaultValues: initialData ? {
      name: initialData.name,
      description: initialData.description,
      isActive: initialData.isActive ?? true,
      schema: {
        name: initialData.schema.name ?? '',
        fields: initialData.schema.fields.map(field => ({
          name: field.name,
          type: field.type,
          required: field.required ?? false,
          description: field.description || ''
        }))
      }
    } : {
      name: '',
      description: '',
      isActive: true,
      schema: {
        name: '',
        fields: [
          { name: 'id', type: 'string', required: true, description: 'Unique identifier' },
          { name: 'createdAt', type: 'date', required: true, description: 'Creation timestamp' },
          { name: 'updatedAt', type: 'date', required: true, description: 'Last update timestamp' }
        ]
      }
    }
  });

  const fields = watch('schema.fields') || [];

  const fieldTypes = [
    'string', 'number', 'boolean', 'date', 'email', 'url', 'text'
  ];

  const addField = () => {
    const newFields = [...fields, { name: '', type: 'string', required: false, description: '' }];
    setValue('schema.fields' as any, newFields);
  };

  const removeField = (index: number) => {
    const newFields = [...fields];
    newFields.splice(index, 1);
    setValue('schema.fields' as any, newFields);
  };

  const onSubmitForm: SubmitHandler<CollectionFormData> = (data) => {
    onSubmit(data);
  };

  return (
    <form onSubmit={handleSubmit(onSubmitForm)} className="space-y-6">
      <div className="space-y-4">
        <h2 className="text-lg font-medium">Collection Details</h2>
        
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="name">Name</Label>
            <TextField
              id="name"
              {...register('name', { required: 'Name is required' })}
              placeholder="e.g., Products, Users, Orders"
            />
            {errors.name?.message ? <p className="text-sm text-red-500">{errors.name.message}</p> : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="schema.name">Schema Name</Label>
            <TextField
              id="schema.name"
              {...register('schema.name', { required: 'Schema name is required' })}
              placeholder="e.g., product, user, order"
            />
            {errors.schema?.name?.message ? (
              <p className="text-sm text-red-500">{errors.schema.name.message}</p>
            ) : null}
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="description">Description</Label>
          <TextArea
            id="description"
            {...register('description')}
            placeholder="Describe what this collection is used for"
            rows={3}
          />
        </div>

        <div className="flex items-center space-x-2">
          <Switch
            id="isActive"
            checked={watch('isActive')}
            onToggle={(checked) => setValue('isActive', checked)}
          />
          <Label htmlFor="isActive">Active</Label>
        </div>
      </div>

      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-medium">Fields</h2>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={addField}
          >
            <Plus className="h-4 w-4 mr-2" />
            Add Field
          </Button>
        </div>

        <div className="space-y-4">
          {fields.map((field, index) => (
            <div key={index} className="border rounded-lg p-4 space-y-4">
              <div className="flex justify-between items-start">
                <h3 className="font-medium">Field {index + 1}</h3>
                {index > 2 && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => removeField(index)}
                    className="text-red-500 hover:text-red-700"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                )}
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="space-y-2">
                  <Label htmlFor={`fields.${index}.name`}>Field Name</Label>
                  <TextField
                    id={`fields.${index}.name`}
                    {...register(`schema.fields.${index}.name` as const, {
                      required: 'Field name is required',
                      pattern: {
                        value: /^[a-z][a-zA-Z0-9]*$/,
                        message: 'Must start with a lowercase letter and contain only alphanumeric characters'
                      }
                    })}
                    placeholder="e.g., firstName, email"
                  />
                  {errors.schema?.fields?.[index]?.name && (
                    <p className="text-sm text-red-500">
                      {errors.schema.fields[index]?.name?.message}
                    </p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor={`fields.${index}.type`}>Type</Label>
                  <select
                    id={`fields.${index}.type`}
                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                    {...register(`schema.fields.${index}.type` as const, {
                      required: 'Field type is required'
                    })}
                  >
                    {fieldTypes.map((type) => (
                      <option key={type} value={type}>
                        {type.charAt(0).toUpperCase() + type.slice(1)}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="flex items-end space-x-2">
                  <div className="flex items-center space-x-2">
                    <input
                      type="checkbox"
                      id={`fields.${index}.required`}
                      {...register(`schema.fields.${index}.required` as const)}
                      className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                    />
                    <Label htmlFor={`fields.${index}.required`}>Required</Label>
                  </div>
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor={`fields.${index}.description`}>Description (Optional)</Label>
                <TextField
                  id={`fields.${index}.description`}
                  {...register(`schema.fields.${index}.description` as const)}
                  placeholder="Description of what this field represents"
                />
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="flex justify-end space-x-3 pt-4">
        <Button
          type="button"
          variant="outline"
          onClick={onCancel}
          disabled={isSubmitting}
        >
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Saving...' : 'Save Collection'}
        </Button>
      </div>
    </form>
  );
}
