/**
 * CollectionForm component.
 *
 * @doc.type component
 * @doc.purpose Form for creating or updating a collection with configurable fields, privacy options, and Zod validation
 * @doc.layer product
 * @doc.pattern Form
 */
import React from 'react';
import { useForm, SubmitHandler, type Resolver } from 'react-hook-form';
import { Button, TextField, TextArea, Switch } from '@ghatana/design-system';
import { Plus, Trash2, Shield, Clock } from 'lucide-react';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { SensitivityBadge, TrustBadge } from '../../../components/governance/TrustSignal';

export const SensitivityLevelSchema = z.enum(['public', 'internal', 'confidential', 'pii', 'restricted']);
export type SensitivityLevel = z.infer<typeof SensitivityLevelSchema>;

export const RetentionPolicySchema = z.enum(['standard', 'short_term', 'long_term', 'indefinite', 'compliance']);
export type RetentionPolicy = z.infer<typeof RetentionPolicySchema>;

const collectionSchema = z.object({
  name: z.string().min(2, 'Name must be at least 2 characters').max(50, 'Name must be less than 50 characters'),
  description: z.string().max(200, 'Description must be less than 200 characters').optional().default(''),
  isActive: z.boolean().default(true),
  sensitivity: SensitivityLevelSchema.default('internal'),
  retentionPolicy: RetentionPolicySchema.default('standard'),
  schema: z.object({
    name: z.string().min(2, 'Schema name is required'),
    fields: z.array(z.object({
      name: z.string().regex(/^[a-z][a-zA-Z0-9]*$/, 'Must start with a lowercase letter and contain only alphanumeric characters'),
      type: z.string().min(1, 'Type is required'),
      required: z.boolean().default(false),
      description: z.string().optional().default('')
    })).min(1, 'At least one field is required')
  })
});

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

export type CollectionFormData = z.output<typeof collectionSchema>;

export function CollectionForm({ initialData, onSubmit, onCancel, isSubmitting }: CollectionFormProps) {
  const { register, handleSubmit, formState: { errors }, watch, setValue } = useForm<CollectionFormData>({
    resolver: zodResolver(collectionSchema) as unknown as Resolver<CollectionFormData>,
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

        {/* Trust Signals — sensitivity and retention */}
        <div className="rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/30 p-4 space-y-4">
          <div className="flex items-center gap-2 mb-2">
            <Shield className="h-4 w-4 text-blue-600 dark:text-blue-400" />
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Trust & Governance</h3>
          </div>
            {/* DC-UX-013: Governance fields are form-draft state only.
                They are persisted when the collection is saved. */}
            <p className="text-xs text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900 rounded px-2 py-1">
              These settings take effect after the collection is saved.
            </p>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="sensitivity">Data Sensitivity</Label>
              <select
                id="sensitivity"
                {...register('sensitivity')}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <option value="public">Public</option>
                <option value="internal">Internal</option>
                <option value="confidential">Confidential</option>
                <option value="pii">PII (Personal Data)</option>
                <option value="restricted">Restricted</option>
              </select>
              <div className="pt-1">
                <SensitivityBadge level={watch('sensitivity')} />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="retentionPolicy">Retention Policy</Label>
              <select
                id="retentionPolicy"
                {...register('retentionPolicy')}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <option value="standard">Standard (1 year)</option>
                <option value="short_term">Short-term (30 days)</option>
                <option value="long_term">Long-term (7 years)</option>
                <option value="indefinite">Indefinite</option>
                <option value="compliance">Compliance (regulated)</option>
              </select>
              <div className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                <Clock className="h-3 w-3" />
                {watch('retentionPolicy') === 'compliance' && (
                  <TrustBadge status="warning" label="Requires review" />
                )}
                <span>
                  {watch('retentionPolicy') === 'standard' && 'Data retained for 1 year then auto-purged'}
                  {watch('retentionPolicy') === 'short_term' && 'Data purged after 30 days'}
                  {watch('retentionPolicy') === 'long_term' && 'Data retained for 7 years for audit'}
                  {watch('retentionPolicy') === 'indefinite' && 'Data retained until manually deleted'}
                  {watch('retentionPolicy') === 'compliance' && 'Regulated retention — legal review required'}
                </span>
              </div>
            </div>
          </div>
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
                    aria-label={`Remove field ${index + 1}`}
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
