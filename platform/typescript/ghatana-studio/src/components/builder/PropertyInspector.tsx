/**
 * @fileoverview Property inspector for visual builder.
 *
 * Displays and edits properties of selected components in the builder.
 * Supports prop editing, slot management, binding configuration, and metadata.
 *
 * @doc.type component
 * @doc.purpose Component property editing and configuration
 * @doc.layer platform
 */

import type { KeyboardEvent, ReactElement } from 'react';
import { useState, useEffect } from 'react';
import { Typography, Input, Button, Card, CardContent, CardHeader, Badge } from '@ghatana/design-system';
import type { ComponentInstance, NodeId } from '@ghatana/ui-builder';

export interface PropertyInspectorProps {
  /** The currently selected component instance */
  selectedInstance: ComponentInstance | null;
  /** Callback when a property is updated */
  onPropertyUpdate: (instanceId: NodeId, prop: string, value: unknown) => void;
}

interface PropertyParseResult {
  readonly success: boolean;
  readonly value?: unknown;
  readonly error?: string;
}

function parsePropertyInput(input: string): PropertyParseResult {
  const trimmed = input.trim();
  const requiresJsonParse = /^[{["-]|\b(?:true|false|null)\b|-?\d/u.test(trimmed);
  if (!requiresJsonParse) {
    return { success: true, value: input };
  }

  try {
    return { success: true, value: JSON.parse(trimmed) };
  } catch {
    return {
      success: false,
      error: 'Enter valid JSON, or use plain text without JSON delimiters.',
    };
  }
}

export function PropertyInspector({
  selectedInstance,
  onPropertyUpdate,
}: PropertyInspectorProps): ReactElement {
  const [editingProp, setEditingProp] = useState<string | null>(null);
  const [propValue, setPropValue] = useState<string>('');
  const [propError, setPropError] = useState<string | null>(null);

  useEffect(() => {
    setEditingProp(null);
    setPropValue('');
    setPropError(null);
  }, [selectedInstance]);

  if (!selectedInstance) {
    return (
      <Card>
        <CardContent className="py-12">
          <div className="text-center">
            <Typography variant="body1" className="text-gray-500">
              Select a component to edit its properties
            </Typography>
          </div>
        </CardContent>
      </Card>
    );
  }

  const handlePropChange = (prop: string, value: unknown): void => {
    onPropertyUpdate(selectedInstance.id, prop, value);
  };

  const startEdit = (prop: string, currentValue: unknown): void => {
    setEditingProp(prop);
    setPropValue(typeof currentValue === 'object' ? JSON.stringify(currentValue) : String(currentValue ?? ''));
    setPropError(null);
  };

  const saveEdit = (): void => {
    if (editingProp) {
      const parsed = parsePropertyInput(propValue);
      if (!parsed.success) {
        setPropError(parsed.error ?? 'Invalid property value.');
        return;
      }
      handlePropChange(editingProp, parsed.value);
      setEditingProp(null);
      setPropValue('');
      setPropError(null);
    }
  };

  const cancelEdit = (): void => {
    setEditingProp(null);
    setPropValue('');
    setPropError(null);
  };

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="p-3 border-b">
        <Typography variant="h3" className="font-semibold">
          Properties
        </Typography>
        <Typography variant="body2" className="text-gray-500 text-sm mt-1">
          {selectedInstance.contractName}
        </Typography>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-3 space-y-4">
        {/* Component Info */}
        <Card>
          <CardHeader title="Component" />
          <CardContent className="space-y-3">
            <div>
              <Typography variant="body2" className="text-gray-500 text-xs uppercase tracking-wide mb-1">
                ID
              </Typography>
              <Typography variant="body1" className="font-mono text-sm">
                {selectedInstance.id}
              </Typography>
            </div>
            <div>
              <Typography variant="body2" className="text-gray-500 text-xs uppercase tracking-wide mb-1">
                Contract
              </Typography>
              <Typography variant="body1" className="font-mono text-sm">
                {selectedInstance.contractName}
              </Typography>
            </div>
          </CardContent>
        </Card>

        {/* Props */}
        <Card>
          <CardHeader title="Props" />
          <CardContent className="space-y-3">
            {Object.keys(selectedInstance.props).length === 0 ? (
              <Typography variant="body2" className="text-gray-500">
                No props defined
              </Typography>
            ) : (
              Object.entries(selectedInstance.props).map(([prop, value]) => (
                <div key={prop} className="space-y-1" data-testid={`builder-property-row-${prop}`}>
                  <Typography variant="body2" className="text-gray-500 text-xs uppercase tracking-wide">
                    {prop}
                  </Typography>
                  {editingProp === prop ? (
                    <div className="flex gap-2">
                      <Input
                        data-testid={`builder-property-input-${prop}`}
                        value={propValue}
                        onChange={(e) => {
                          setPropValue(e.target.value);
                          setPropError(null);
                        }}
                        className="flex-1"
                        aria-invalid={propError !== null}
                        aria-describedby={propError !== null ? `builder-property-error-${prop}` : undefined}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') saveEdit();
                          if (e.key === 'Escape') cancelEdit();
                        }}
                        autoFocus
                      />
                      <Button variant="primary" size="sm" onClick={saveEdit} data-testid={`builder-property-save-${prop}`}>
                        Save
                      </Button>
                      <Button variant="secondary" size="sm" onClick={cancelEdit} data-testid={`builder-property-cancel-${prop}`}>
                        Cancel
                      </Button>
                      {propError !== null && (
                        <p
                          id={`builder-property-error-${prop}`}
                          role="alert"
                          data-testid={`builder-property-validation-${prop}`}
                          className="text-xs text-red-600"
                        >
                          {propError}
                        </p>
                      )}
                    </div>
                  ) : (
                    <div
                      data-testid={`builder-property-value-${prop}`}
                      role="button"
                      tabIndex={0}
                      aria-label={`Edit ${prop}`}
                      className="p-2 bg-gray-50 rounded border cursor-pointer hover:bg-gray-100"
                      onClick={() => startEdit(prop, value)}
                      onKeyDown={(event: KeyboardEvent<HTMLDivElement>) => {
                        if (event.key !== 'Enter' && event.key !== ' ') return;
                        event.preventDefault();
                        startEdit(prop, value);
                      }}
                    >
                      <Typography variant="body1" className="font-mono text-sm">
                        {typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)}
                      </Typography>
                    </div>
                  )}
                </div>
              ))
            )}
          </CardContent>
        </Card>

        {/* Bindings */}
        {selectedInstance.bindings.length > 0 && (
          <Card>
            <CardHeader title="Bindings" />
            <CardContent className="space-y-3">
              {selectedInstance.bindings.map((binding, index) => (
                <div key={index} className="p-2 bg-gray-50 rounded border">
                  <div className="flex items-center justify-between mb-1">
                    <Typography variant="body2" className="font-medium">
                      {binding.type}
                    </Typography>
                    <Badge variant="soft" tone="neutral" className="text-xs">
                      {binding.target}
                    </Badge>
                  </div>
                  <Typography variant="body2" className="text-gray-600 text-sm font-mono">
                    {binding.source}
                  </Typography>
                </div>
              ))}
            </CardContent>
          </Card>
        )}

        {/* Metadata */}
        <Card>
          <CardHeader title="Metadata" />
          <CardContent className="space-y-3">
            {selectedInstance.metadata && Object.keys(selectedInstance.metadata).length > 0 ? (
              Object.entries(selectedInstance.metadata).map(([key, value]) => (
                <div key={key} className="space-y-1">
                  <Typography variant="body2" className="text-gray-500 text-xs uppercase tracking-wide">
                    {key}
                  </Typography>
                  <Typography variant="body1" className="font-mono text-sm">
                    {String(value)}
                  </Typography>
                </div>
              ))
            ) : (
              <Typography variant="body2" className="text-gray-500">
                No metadata
              </Typography>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
