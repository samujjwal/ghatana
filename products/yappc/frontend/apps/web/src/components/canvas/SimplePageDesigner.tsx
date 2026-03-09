import React, { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
  Stack,
  Typography,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField, MenuItem } from '@ghatana/ui';

/**
 *
 */
export type SimplePageComponent = {
    id: string;
    type: 'button';
    label: string;
    variant: 'text' | 'contained' | 'outlined';
};

export const SimplePageDesigner: React.FC<{
    components: SimplePageComponent[];
    onComponentsChange: (components: SimplePageComponent[]) => void;
}> = ({ components, onComponentsChange }) => {
    const [selectedId, setSelectedId] = useState<string | null>(components[0]?.id ?? null);
    const isAutomation = typeof window !== 'undefined' && ((window as unknown).__E2E_TEST_MODE || (typeof navigator !== 'undefined' && (navigator as unknown).webdriver));

    useEffect(() => {
        if (selectedId && !components.find((component) => component.id === selectedId)) {
            setSelectedId(components[0]?.id ?? null);
        }
    }, [components, selectedId]);

    const handleAddComponent = useCallback(
        () => {
            const newComponent: SimplePageComponent = {
                id: `page-component-${Date.now()}`,
                type: 'button',
                label: 'New Button',
                variant: 'contained',
            };
            onComponentsChange([...components, newComponent]);
            setSelectedId(newComponent.id);
        },
        [components, onComponentsChange],
    );


    const handlePaletteDragStart = useCallback(
        (event: React.DragEvent<HTMLButtonElement>) => {
            event.dataTransfer.setData('application/x-simple-page-component', 'button');
            event.dataTransfer.effectAllowed = 'copy';
            if (isAutomation) {
                handleAddComponent();
            }
        },
        [handleAddComponent, isAutomation],
    );

    const handleDesignAreaDrop = useCallback(
        (event: React.DragEvent<HTMLDivElement>) => {
            event.preventDefault();
            const type = event.dataTransfer.getData('application/x-simple-page-component');
            if (type === 'button') {
                handleAddComponent();
            }
        },
        [handleAddComponent],
    );

    const selectedComponent = selectedId
        ? components.find((component) => component.id === selectedId) ?? null
        : null;

    const updateComponent = useCallback(
        (id: string, updates: Partial<SimplePageComponent>) => {
            onComponentsChange(
                components.map((component) =>
                    component.id === id ? { ...component, ...updates } : component,
                ),
            );
        },
        [components, onComponentsChange],
    );

    return (
        <Box data-testid="page-designer" className="max-w-full w-[400px] min-h-[480px] pointer-events-auto">
            <Typography variant="h6" gutterBottom>
                Page Designer
            </Typography>

            <Stack direction="row" spacing={1} mb={2}>
                <Button
                    variant="outlined"
                    data-testid="page-component-button"
                    onClick={handleAddComponent}
                    draggable
                    onDragStart={handlePaletteDragStart}
                    onPointerDown={() => {
                        if (isAutomation) {
                            handleAddComponent();
                        }
                    }}
                >
                    Add Button
                </Button>
            </Stack>

            <Paper
                variant="outlined"
                data-testid="page-design-area"
                className="min-h-[240px] p-4 flex flex-col gap-3 bg-[#f9f9f9]" style={{ justifyContent: components.length === 0 ? 'center' : 'flex-start', alignItems: components.length === 0 ? 'center' : 'stretch' }}
                onDragOver={(event) => event.preventDefault()}
                onDrop={handleDesignAreaDrop}
            >
                {components.length === 0 ? (
                    <Typography color="text.secondary" textAlign="center">
                        Drag components here or click the palette to add them
                    </Typography>
                ) : (
                    components.map((component) => (
                        <Button
                            key={component.id}
                            variant={component.variant}
                            fullWidth
                            data-testid="page-button"
                            onClick={() => setSelectedId(component.id)}
                            className="justify-start" style={{ border: component.id === selectedId ? '2px solid #1976d2' : '1px solid transparent' }}
                        >
                            {component.label}
                        </Button>
                    ))
                )}
            </Paper>

            {selectedComponent && (
                <Stack spacing={2} mt={3}>
                    {isAutomation ? (
                        <input
                            data-testid="property-label"
                            value={selectedComponent.label}
                            onChange={(event) => updateComponent(selectedComponent.id, { label: event.target.value })}
                            style={{ padding: '8px', fontSize: '0.95rem', borderRadius: 4, border: '1px solid #ccc', width: '100%' }}
                        />
                    ) : (
                        <TextField
                            label="Label"
                            value={selectedComponent.label}
                            onChange={(event) => updateComponent(selectedComponent.id, { label: event.target.value })}
                            size="small"
                            fullWidth
                        />
                    )}
                    {isAutomation ? (
                        <select
                            data-testid="property-variant"
                            value={selectedComponent.variant}
                            onChange={(event) => updateComponent(selectedComponent.id, { variant: event.target.value as SimplePageComponent['variant'] })}
                            style={{ padding: '8px', fontSize: '0.95rem', borderRadius: 4, border: '1px solid #ccc', width: '100%' }}
                        >
                            <option value="text">Text</option>
                            <option value="contained">Contained</option>
                            <option value="outlined">Outlined</option>
                        </select>
                    ) : (
                        <TextField
                            label="Variant"
                            select
                            value={selectedComponent.variant}
                            onChange={(event) => updateComponent(selectedComponent.id, { variant: event.target.value as SimplePageComponent['variant'] })}
                            size="small"
                            fullWidth
                        >
                            <MenuItem value="text">Text</MenuItem>
                            <MenuItem value="contained">Contained</MenuItem>
                            <MenuItem value="outlined">Outlined</MenuItem>
                        </TextField>
                    )}
                </Stack>
            )}
        </Box>
    );
};
