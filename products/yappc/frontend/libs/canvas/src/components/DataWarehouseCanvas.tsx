/**
 * @doc.type component
 * @doc.purpose Data warehouse schema canvas for Journey 17.1 (Data Architect - Data Warehouse Schema)
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback } from 'react';
import { Surface as Paper, Box, Typography, Button, IconButton, Dialog, DialogTitle, DialogContent, DialogActions, TextField, MenuItem, Chip, InteractiveList as List, ListItem, ListItemText, Divider, Tab, Tabs, Alert, Stack, FormControl, FormControlLabel, Checkbox, InputLabel, Select } from '@ghatana/ui';
import { Plus as AddIcon, Pencil as EditIcon, Trash2 as DeleteIcon, Link as LinkIcon, Grid3x3 as TableIcon, BarChart3 as FactIcon, Tag as DimensionIcon, Code as CodeIcon, Check as CheckIcon, AlertTriangle as WarningIcon, Download as ExportIcon, Wand2 as AutoLayoutIcon } from 'lucide-react';
import {
    useDataWarehouse,
    type DWTable,
    type Column,
    type TableType,
    type DimensionType,
    type SCDType,
    type ColumnDataType,
} from '../hooks/useDataWarehouse';

/**
 * Component props
 */
export interface DataWarehouseCanvasProps {
    /**
     * Enable star schema auto-layout
     */
    enableStarSchemaLayout?: boolean;

    /**
     * Callback when schema changes
     */
    onSchemaChange?: (tables: DWTable[]) => void;
}

const COLUMN_DATA_TYPES: ColumnDataType[] = [
    'INT',
    'BIGINT',
    'VARCHAR',
    'TEXT',
    'DATE',
    'TIMESTAMP',
    'DECIMAL',
    'BOOLEAN',
    'JSON',
];

const DIMENSION_TYPES: DimensionType[] = [
    'conformed',
    'degenerate',
    'junk',
    'role-playing',
    'slowly-changing',
];

const SCD_TYPES: SCDType[] = ['Type 1', 'Type 2', 'Type 3', 'Type 4', 'Type 6'];

/**
 * Data Warehouse Canvas Component
 * 
 * Provides comprehensive data warehouse schema modeling with fact/dimension tables,
 * FK relationships, grain definitions, and ETL mapping.
 */
export const DataWarehouseCanvas: React.FC<DataWarehouseCanvasProps> = ({
    enableStarSchemaLayout = false,
    onSchemaChange,
}) => {
    const {
        tables,
        addTable,
        updateTable,
        deleteTable,
        addColumn,
        updateColumn,
        deleteColumn,
        relationships,
        addRelationship,
        deleteRelationship,
        getRelationshipsForTable,
        etlMappings,
        addETLMapping,
        deleteETLMapping,
        getETLMappingsForTable,
        validateSchema,
        setGrain,
        autoLayoutStarSchema,
        exportDDL,
        exportERDiagram,
    } = useDataWarehouse({ starSchemaLayout: enableStarSchemaLayout });

    const [activeTab, setActiveTab] = useState<'tables' | 'relationships' | 'etl' | 'validation'>(
        'tables'
    );
    const [tableDialogOpen, setTableDialogOpen] = useState(false);
    const [columnDialogOpen, setColumnDialogOpen] = useState(false);
    const [relationshipDialogOpen, setRelationshipDialogOpen] = useState(false);
    const [etlDialogOpen, setETLDialogOpen] = useState(false);
    const [exportDialogOpen, setExportDialogOpen] = useState(false);
    const [selectedTable, setSelectedTable] = useState<DWTable | null>(null);
    const [selectedColumn, setSelectedColumn] = useState<Column | null>(null);
    const [exportFormat, setExportFormat] = useState<'postgres' | 'mysql' | 'snowflake' | 'bigquery' | 'mermaid'>('postgres');

    // Form state for new table
    const [newTableForm, setNewTableForm] = useState<{
        name: string;
        type: TableType;
        dimensionType?: DimensionType;
        scdType?: SCDType;
        grain?: string;
        description?: string;
    }>({
        name: '',
        type: 'fact',
        grain: '',
        description: '',
    });

    // Form state for new column
    const [newColumnForm, setNewColumnForm] = useState<{
        name: string;
        dataType: ColumnDataType;
        isPrimaryKey: boolean;
        isNullable: boolean;
        description?: string;
    }>({
        name: '',
        dataType: 'INT',
        isPrimaryKey: false,
        isNullable: true,
        description: '',
    });

    // Form state for new relationship
    const [newRelationshipForm, setNewRelationshipForm] = useState<{
        fromTableId: string;
        fromColumnId: string;
        toTableId: string;
        toColumnId: string;
        cardinality: '1:1' | '1:N' | 'N:M';
    }>({
        fromTableId: '',
        fromColumnId: '',
        toTableId: '',
        toColumnId: '',
        cardinality: '1:N',
    });

    // Form state for ETL mapping
    const [newETLForm, setNewETLForm] = useState<{
        targetTableId: string;
        sourceName: string;
        loadStrategy: 'full' | 'incremental' | 'upsert';
    }>({
        targetTableId: '',
        sourceName: '',
        loadStrategy: 'incremental',
    });

    const handleAddTable = useCallback(() => {
        if (!newTableForm.name.trim()) return;

        addTable({
            ...newTableForm,
            columns: [],
        });

        setNewTableForm({
            name: '',
            type: 'fact',
            grain: '',
            description: '',
        });
        setTableDialogOpen(false);

        if (onSchemaChange) {
            onSchemaChange(tables);
        }
    }, [newTableForm, addTable, tables, onSchemaChange]);

    const handleAddColumn = useCallback(() => {
        if (!newColumnForm.name.trim() || !selectedTable) return;

        addColumn(selectedTable.id, {
            ...newColumnForm,
            isForeignKey: false,
        });

        setNewColumnForm({
            name: '',
            dataType: 'INT',
            isPrimaryKey: false,
            isNullable: true,
            description: '',
        });
        setColumnDialogOpen(false);
    }, [newColumnForm, selectedTable, addColumn]);

    const handleAddRelationship = useCallback(() => {
        if (
            !newRelationshipForm.fromTableId ||
            !newRelationshipForm.fromColumnId ||
            !newRelationshipForm.toTableId ||
            !newRelationshipForm.toColumnId
        ) {
            return;
        }

        addRelationship(newRelationshipForm);

        setNewRelationshipForm({
            fromTableId: '',
            fromColumnId: '',
            toTableId: '',
            toColumnId: '',
            cardinality: '1:N',
        });
        setRelationshipDialogOpen(false);
    }, [newRelationshipForm, addRelationship]);

    const handleAddETLMapping = useCallback(() => {
        if (!newETLForm.targetTableId || !newETLForm.sourceName.trim()) return;

        addETLMapping({
            ...newETLForm,
            columnMappings: {},
            transformations: [],
        });

        setNewETLForm({
            targetTableId: '',
            sourceName: '',
            loadStrategy: 'incremental',
        });
        setETLDialogOpen(false);
    }, [newETLForm, addETLMapping]);

    const handleExport = useCallback(() => {
        let content: string;
        let filename: string;

        if (exportFormat === 'mermaid') {
            content = exportERDiagram();
            filename = 'schema-diagram.mmd';
        } else {
            content = exportDDL(exportFormat);
            filename = `schema-${exportFormat}.sql`;
        }

        const blob = new Blob([content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);

        setExportDialogOpen(false);
    }, [exportFormat, exportDDL, exportERDiagram]);

    const validation = validateSchema();

    return (
        <Box className="p-6">
            {/* Header */}
            <Stack direction="row" justifyContent="space-between" alignItems="center" className="mb-6">
                <Box>
                    <Typography as="h4" gutterBottom>
                        Data Warehouse Schema
                    </Typography>
                    <Typography as="p" className="text-sm" color="text.secondary">
                        Design fact tables, dimensions, and relationships
                    </Typography>
                </Box>
                <Stack direction="row" spacing={1}>
                    {enableStarSchemaLayout && tables.some(t => t.type === 'fact') && (
                        <Button
                            variant="outlined"
                            startIcon={<AutoLayoutIcon />}
                            onClick={() => {
                                const factTable = tables.find(t => t.type === 'fact');
                                if (factTable) {
                                    autoLayoutStarSchema(factTable.id);
                                }
                            }}
                        >
                            Auto Layout
                        </Button>
                    )}
                    <Button
                        variant="solid"
                        startIcon={<ExportIcon />}
                        onClick={() => setExportDialogOpen(true)}
                    >
                        Export DDL
                    </Button>
                </Stack>
            </Stack>

            {/* Validation Status */}
            {!validation.valid && (
                <Alert severity="error" className="mb-4">
                    <Typography as="p" className="text-sm font-medium">Schema Validation Errors:</Typography>
                    <ul style={{ margin: 0, paddingLeft: 20, gridTemplateColumns: 'repeat(auto-fill }}>
                        {validation.errors.map((error, idx) => (
                            <li key={idx}>{error}</li>
                        ))}
                    </ul>
                </Alert>
            )}

            {validation.warnings.length > 0 && (
                <Alert severity="warning" className="mb-4">
                    <Typography as="p" className="text-sm font-medium">Warnings:</Typography>
                    <ul style={{ margin: 0, paddingLeft: 20 }}>
                        {validation.warnings.map((warning, idx) => (
                            <li key={idx}>{warning}</li>
                        ))}
                    </ul>
                </Alert>
            )}

            {/* Tabs */}
            <Paper className="mb-4">
                <Tabs value={activeTab} onChange={(_, val) => setActiveTab(val)}>
                    <Tab label="Tables" value="tables" />
                    <Tab label="Relationships" value="relationships" />
                    <Tab label="ETL Mappings" value="etl" />
                    <Tab label="Validation" value="validation" />
                </Tabs>
            </Paper>

            {/* Tables Tab */}
            {activeTab === 'tables' && (
                <Paper className="p-4">
                    <Stack direction="row" justifyContent="space-between" alignItems="center" className="mb-4">
                        <Typography as="h6">Tables ({tables.length})</Typography>
                        <Button
                            variant="solid"
                            startIcon={<AddIcon />}
                            onClick={() => setTableDialogOpen(true)}
                        >
                            Add Table
                        </Button>
                    </Stack>

                    <Box className="grid gap-4" >
                        {tables.map(table => (
                            <Paper key={table.id} variant="outlined" className="p-4">
                                <Stack direction="row" justifyContent="space-between" alignItems="start" className="mb-2">
                                    <Stack direction="row" spacing={1} alignItems="center">
                                        {table.type === 'fact' ? <FactIcon tone="primary" /> : <DimensionIcon tone="secondary" />}
                                        <Typography as="p" className="text-lg font-medium" fontWeight="bold">
                                            {table.name}
                                        </Typography>
                                    </Stack>
                                    <Stack direction="row" spacing={0.5}>
                                        <IconButton
                                            size="sm"
                                            onClick={() => {
                                                setSelectedTable(table);
                                                setColumnDialogOpen(true);
                                            }}
                                        >
                                            <AddIcon size={16} />
                                        </IconButton>
                                        <IconButton size="sm" onClick={() => deleteTable(table.id)}>
                                            <DeleteIcon size={16} />
                                        </IconButton>
                                    </Stack>
                                </Stack>

                                <Stack direction="row" spacing={1} className="mb-2">
                                    <Chip label={table.type} size="sm" tone="primary" />
                                    {table.dimensionType && (
                                        <Chip label={table.dimensionType} size="sm" />
                                    )}
                                    {table.scdType && (
                                        <Chip label={table.scdType} size="sm" tone="secondary" />
                                    )}
                                </Stack>

                                {table.grain && (
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mb-2 block">
                                        <strong>Grain:</strong> {table.grain}
                                    </Typography>
                                )}

                                {table.description && (
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mb-2 block">
                                        {table.description}
                                    </Typography>
                                )}

                                <Divider className="my-2" />

                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mb-1 block">
                                    <strong>Columns ({table.columns.length}):</strong>
                                </Typography>

                                <List dense disablePadding>
                                    {table.columns.slice(0, 5).map(col => (
                                        <ListItem key={col.id} dense disableGutters>
                                            <ListItemText
                                                primary={
                                                    <Typography as="span" className="text-xs text-gray-500">
                                                        {col.isPrimaryKey && '🔑 '}
                                                        {col.isForeignKey && '🔗 '}
                                                        {col.name}: {col.dataType}
                                                        {!col.isNullable && ' NOT NULL'}
                                                    </Typography>
                                                }
                                            />
                                        </ListItem>
                                    ))}
                                    {table.columns.length > 5 && (
                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                            +{table.columns.length - 5} more...
                                        </Typography>
                                    )}
                                </List>
                            </Paper>
                        ))}
                    </Box>

                    {tables.length === 0 && (
                        <Alert severity="info">
                            No tables defined yet. Click "Add Table" to create your first table.
                        </Alert>
                    )}
                </Paper>
            )}

            {/* Relationships Tab */}
            {activeTab === 'relationships' && (
                <Paper className="p-4">
                    <Stack direction="row" justifyContent="space-between" alignItems="center" className="mb-4">
                        <Typography as="h6">Foreign Key Relationships ({relationships.length})</Typography>
                        <Button
                            variant="solid"
                            startIcon={<LinkIcon />}
                            onClick={() => setRelationshipDialogOpen(true)}
                        >
                            Add Relationship
                        </Button>
                    </Stack>

                    <List>
                        {relationships.map((rel, idx) => {
                            const fromTable = tables.find(t => t.id === rel.fromTableId);
                            const toTable = tables.find(t => t.id === rel.toTableId);
                            const fromCol = fromTable?.columns.find(c => c.id === rel.fromColumnId);
                            const toCol = toTable?.columns.find(c => c.id === rel.toColumnId);

                            return (
                                <React.Fragment key={rel.id}>
                                    <ListItem
                                        secondaryAction={
                                            <IconButton edge="end" onClick={() => deleteRelationship(rel.id)}>
                                                <DeleteIcon />
                                            </IconButton>
                                        }
                                    >
                                        <ListItemText
                                            primary={`${fromTable?.name}.${fromCol?.name} → ${toTable?.name}.${toCol?.name}`}
                                            secondary={`Cardinality: ${rel.cardinality}`}
                                        />
                                    </ListItem>
                                    {idx < relationships.length - 1 && <Divider />}
                                </React.Fragment>
                            );
                        })}
                    </List>

                    {relationships.length === 0 && (
                        <Alert severity="info">
                            No relationships defined yet. Click "Add Relationship" to create foreign key relationships.
                        </Alert>
                    )}
                </Paper>
            )}

            {/* ETL Mappings Tab */}
            {activeTab === 'etl' && (
                <Paper className="p-4">
                    <Stack direction="row" justifyContent="space-between" alignItems="center" className="mb-4">
                        <Typography as="h6">ETL Mappings ({etlMappings.length})</Typography>
                        <Button
                            variant="solid"
                            startIcon={<AddIcon />}
                            onClick={() => setETLDialogOpen(true)}
                        >
                            Add ETL Mapping
                        </Button>
                    </Stack>

                    <List>
                        {etlMappings.map((mapping, idx) => {
                            const table = tables.find(t => t.id === mapping.targetTableId);

                            return (
                                <React.Fragment key={mapping.id}>
                                    <ListItem
                                        secondaryAction={
                                            <IconButton edge="end" onClick={() => deleteETLMapping(mapping.id)}>
                                                <DeleteIcon />
                                            </IconButton>
                                        }
                                    >
                                        <ListItemText
                                            primary={`${mapping.sourceName} → ${table?.name}`}
                                            secondary={`Load Strategy: ${mapping.loadStrategy}`}
                                        />
                                    </ListItem>
                                    {idx < etlMappings.length - 1 && <Divider />}
                                </React.Fragment>
                            );
                        })}
                    </List>

                    {etlMappings.length === 0 && (
                        <Alert severity="info">
                            No ETL mappings defined yet. Click "Add ETL Mapping" to configure data sources.
                        </Alert>
                    )}
                </Paper>
            )}

            {/* Validation Tab */}
            {activeTab === 'validation' && (
                <Paper className="p-4">
                    <Typography as="h6" gutterBottom>
                        Schema Validation Results
                    </Typography>

                    {validation.valid ? (
                        <Alert severity="success" icon={<CheckIcon />} className="mb-4">
                            Schema validation passed! No errors found.
                        </Alert>
                    ) : (
                        <Alert severity="error" icon={<WarningIcon />} className="mb-4">
                            Schema validation failed. Please fix the errors below.
                        </Alert>
                    )}

                    {validation.errors.length > 0 && (
                        <Box className="mb-4">
                            <Typography as="p" className="text-sm font-medium" tone="danger" gutterBottom>
                                Errors:
                            </Typography>
                            <List>
                                {validation.errors.map((error, idx) => (
                                    <ListItem key={idx}>
                                        <ListItemText primary={error} />
                                    </ListItem>
                                ))}
                            </List>
                        </Box>
                    )}

                    {validation.warnings.length > 0 && (
                        <Box>
                            <Typography as="p" className="text-sm font-medium" color="warning.main" gutterBottom>
                                Warnings:
                            </Typography>
                            <List>
                                {validation.warnings.map((warning, idx) => (
                                    <ListItem key={idx}>
                                        <ListItemText primary={warning} />
                                    </ListItem>
                                ))}
                            </List>
                        </Box>
                    )}
                </Paper>
            )}

            {/* Add Table Dialog */}
            <Dialog open={tableDialogOpen} onClose={() => setTableDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Table</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Table Name"
                            value={newTableForm.name}
                            onChange={e => setNewTableForm({ ...newTableForm, name: e.target.value })}
                            fullWidth
                        />
                        <FormControl fullWidth>
                            <InputLabel>Table Type</InputLabel>
                            <Select
                                value={newTableForm.type}
                                label="Table Type"
                                onChange={e =>
                                    setNewTableForm({ ...newTableForm, type: e.target.value as TableType })
                                }
                            >
                                <MenuItem value="fact">Fact</MenuItem>
                                <MenuItem value="dimension">Dimension</MenuItem>
                                <MenuItem value="bridge">Bridge</MenuItem>
                            </Select>
                        </FormControl>

                        {newTableForm.type === 'dimension' && (
                            <FormControl fullWidth>
                                <InputLabel>Dimension Type</InputLabel>
                                <Select
                                    value={newTableForm.dimensionType || ''}
                                    label="Dimension Type"
                                    onChange={e =>
                                        setNewTableForm({
                                            ...newTableForm,
                                            dimensionType: e.target.value as DimensionType,
                                        })
                                    }
                                >
                                    {DIMENSION_TYPES.map(type => (
                                        <MenuItem key={type} value={type}>
                                            {type}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                        )}

                        {newTableForm.dimensionType === 'slowly-changing' && (
                            <FormControl fullWidth>
                                <InputLabel>SCD Type</InputLabel>
                                <Select
                                    value={newTableForm.scdType || ''}
                                    label="SCD Type"
                                    onChange={e =>
                                        setNewTableForm({ ...newTableForm, scdType: e.target.value as SCDType })
                                    }
                                >
                                    {SCD_TYPES.map(type => (
                                        <MenuItem key={type} value={type}>
                                            {type}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                        )}

                        {newTableForm.type === 'fact' && (
                            <TextField
                                label="Grain (e.g., One row per transaction line item)"
                                value={newTableForm.grain}
                                onChange={e => setNewTableForm({ ...newTableForm, grain: e.target.value })}
                                fullWidth
                                multiline
                                rows={2}
                            />
                        )}

                        <TextField
                            label="Description"
                            value={newTableForm.description}
                            onChange={e => setNewTableForm({ ...newTableForm, description: e.target.value })}
                            fullWidth
                            multiline
                            rows={2}
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setTableDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddTable} variant="solid">
                        Add
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Column Dialog */}
            <Dialog open={columnDialogOpen} onClose={() => setColumnDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Column to {selectedTable?.name}</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Column Name"
                            value={newColumnForm.name}
                            onChange={e => setNewColumnForm({ ...newColumnForm, name: e.target.value })}
                            fullWidth
                        />
                        <FormControl fullWidth>
                            <InputLabel>Data Type</InputLabel>
                            <Select
                                value={newColumnForm.dataType}
                                label="Data Type"
                                onChange={e =>
                                    setNewColumnForm({ ...newColumnForm, dataType: e.target.value as ColumnDataType })
                                }
                            >
                                {COLUMN_DATA_TYPES.map(type => (
                                    <MenuItem key={type} value={type}>
                                        {type}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={newColumnForm.isPrimaryKey}
                                    onChange={e =>
                                        setNewColumnForm({ ...newColumnForm, isPrimaryKey: e.target.checked })
                                    }
                                />
                            }
                            label="Primary Key"
                        />
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={newColumnForm.isNullable}
                                    onChange={e =>
                                        setNewColumnForm({ ...newColumnForm, isNullable: e.target.checked })
                                    }
                                />
                            }
                            label="Nullable"
                        />
                        <TextField
                            label="Description"
                            value={newColumnForm.description}
                            onChange={e => setNewColumnForm({ ...newColumnForm, description: e.target.value })}
                            fullWidth
                            multiline
                            rows={2}
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setColumnDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddColumn} variant="solid">
                        Add
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Relationship Dialog */}
            <Dialog
                open={relationshipDialogOpen}
                onClose={() => setRelationshipDialogOpen(false)}
                size="sm"
                fullWidth
            >
                <DialogTitle>Add Foreign Key Relationship</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <FormControl fullWidth>
                            <InputLabel>From Table</InputLabel>
                            <Select
                                value={newRelationshipForm.fromTableId}
                                label="From Table"
                                onChange={e =>
                                    setNewRelationshipForm({ ...newRelationshipForm, fromTableId: e.target.value })
                                }
                            >
                                {tables.map(table => (
                                    <MenuItem key={table.id} value={table.id}>
                                        {table.name}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        {newRelationshipForm.fromTableId && (
                            <FormControl fullWidth>
                                <InputLabel>From Column</InputLabel>
                                <Select
                                    value={newRelationshipForm.fromColumnId}
                                    label="From Column"
                                    onChange={e =>
                                        setNewRelationshipForm({ ...newRelationshipForm, fromColumnId: e.target.value })
                                    }
                                >
                                    {tables
                                        .find(t => t.id === newRelationshipForm.fromTableId)
                                        ?.columns.map(col => (
                                            <MenuItem key={col.id} value={col.id}>
                                                {col.name} ({col.dataType})
                                            </MenuItem>
                                        ))}
                                </Select>
                            </FormControl>
                        )}

                        <FormControl fullWidth>
                            <InputLabel>To Table</InputLabel>
                            <Select
                                value={newRelationshipForm.toTableId}
                                label="To Table"
                                onChange={e =>
                                    setNewRelationshipForm({ ...newRelationshipForm, toTableId: e.target.value })
                                }
                            >
                                {tables.map(table => (
                                    <MenuItem key={table.id} value={table.id}>
                                        {table.name}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        {newRelationshipForm.toTableId && (
                            <FormControl fullWidth>
                                <InputLabel>To Column</InputLabel>
                                <Select
                                    value={newRelationshipForm.toColumnId}
                                    label="To Column"
                                    onChange={e =>
                                        setNewRelationshipForm({ ...newRelationshipForm, toColumnId: e.target.value })
                                    }
                                >
                                    {tables
                                        .find(t => t.id === newRelationshipForm.toTableId)
                                        ?.columns.map(col => (
                                            <MenuItem key={col.id} value={col.id}>
                                                {col.name} ({col.dataType})
                                            </MenuItem>
                                        ))}
                                </Select>
                            </FormControl>
                        )}

                        <FormControl fullWidth>
                            <InputLabel>Cardinality</InputLabel>
                            <Select
                                value={newRelationshipForm.cardinality}
                                label="Cardinality"
                                onChange={e =>
                                    setNewRelationshipForm({
                                        ...newRelationshipForm,
                                        cardinality: e.target.value as '1:1' | '1:N' | 'N:M',
                                    })
                                }
                            >
                                <MenuItem value="1:1">One-to-One (1:1)</MenuItem>
                                <MenuItem value="1:N">One-to-Many (1:N)</MenuItem>
                                <MenuItem value="N:M">Many-to-Many (N:M)</MenuItem>
                            </Select>
                        </FormControl>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setRelationshipDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddRelationship} variant="solid">
                        Add
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add ETL Mapping Dialog */}
            <Dialog open={etlDialogOpen} onClose={() => setETLDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add ETL Mapping</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <FormControl fullWidth>
                            <InputLabel>Target Table</InputLabel>
                            <Select
                                value={newETLForm.targetTableId}
                                label="Target Table"
                                onChange={e => setNewETLForm({ ...newETLForm, targetTableId: e.target.value })}
                            >
                                {tables.map(table => (
                                    <MenuItem key={table.id} value={table.id}>
                                        {table.name}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        <TextField
                            label="Source Name (table/file)"
                            value={newETLForm.sourceName}
                            onChange={e => setNewETLForm({ ...newETLForm, sourceName: e.target.value })}
                            fullWidth
                        />

                        <FormControl fullWidth>
                            <InputLabel>Load Strategy</InputLabel>
                            <Select
                                value={newETLForm.loadStrategy}
                                label="Load Strategy"
                                onChange={e =>
                                    setNewETLForm({
                                        ...newETLForm,
                                        loadStrategy: e.target.value as 'full' | 'incremental' | 'upsert',
                                    })
                                }
                            >
                                <MenuItem value="full">Full Load</MenuItem>
                                <MenuItem value="incremental">Incremental</MenuItem>
                                <MenuItem value="upsert">Upsert</MenuItem>
                            </Select>
                        </FormControl>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setETLDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddETLMapping} variant="solid">
                        Add
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Export Dialog */}
            <Dialog open={exportDialogOpen} onClose={() => setExportDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Export Schema</DialogTitle>
                <DialogContent>
                    <FormControl fullWidth className="mt-2">
                        <InputLabel>Export Format</InputLabel>
                        <Select
                            value={exportFormat}
                            label="Export Format"
                            onChange={e => setExportFormat(e.target.value as unknown)}
                        >
                            <MenuItem value="postgres">PostgreSQL DDL</MenuItem>
                            <MenuItem value="mysql">MySQL DDL</MenuItem>
                            <MenuItem value="snowflake">Snowflake DDL</MenuItem>
                            <MenuItem value="bigquery">BigQuery DDL</MenuItem>
                            <MenuItem value="mermaid">Mermaid ER Diagram</MenuItem>
                        </Select>
                    </FormControl>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setExportDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleExport} variant="solid">
                        Export
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
