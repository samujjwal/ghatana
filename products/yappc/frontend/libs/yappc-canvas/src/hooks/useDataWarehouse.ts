/**
 * @doc.type hook
 * @doc.purpose Data warehouse schema modeling hook for Journey 17.1 (Data Architect - Data Warehouse Schema)
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useMemo } from 'react';
import { type Node, type Edge, useReactFlow } from '@xyflow/react';

/**
 * Table types in data warehouse
 */
export type TableType = 'fact' | 'dimension' | 'bridge';

/**
 * Dimension types
 */
export type DimensionType = 'conformed' | 'degenerate' | 'junk' | 'role-playing' | 'slowly-changing';

/**
 * SCD (Slowly Changing Dimension) types
 */
export type SCDType = 'Type 1' | 'Type 2' | 'Type 3' | 'Type 4' | 'Type 6';

/**
 * Column data types
 */
export type ColumnDataType = 'INT' | 'BIGINT' | 'VARCHAR' | 'TEXT' | 'DATE' | 'TIMESTAMP' | 'DECIMAL' | 'BOOLEAN' | 'JSON';

/**
 * Schema validation result
 */
export interface ValidationResult {
    valid: boolean;
    errors: string[];
    warnings: string[];
}

/**
 * Column definition
 */
export interface Column {
    id: string;
    name: string;
    dataType: ColumnDataType;
    isPrimaryKey: boolean;
    isForeignKey: boolean;
    isNullable: boolean;
    description?: string;
    /**
     * For FK columns, reference to the table.column
     */
    references?: {
        tableId: string;
        columnId: string;
    };
}

/**
 * Data warehouse table
 */
export interface DWTable {
    id: string;
    name: string;
    type: TableType;
    /**
     * For dimensions, specify the dimension type
     */
    dimensionType?: DimensionType;
    /**
     * For SCD dimensions, specify the SCD type
     */
    scdType?: SCDType;
    /**
     * Columns in the table
     */
    columns: Column[];
    /**
     * Grain definition (for fact tables)
     */
    grain?: string;
    /**
     * Description/business purpose
     */
    description?: string;
    /**
     * Position on canvas
     */
    position?: { x: number; y: number };
}

/**
 * Foreign key relationship
 */
export interface ForeignKeyRelationship {
    id: string;
    fromTableId: string;
    fromColumnId: string;
    toTableId: string;
    toColumnId: string;
    /**
     * Cardinality: one-to-one, one-to-many, many-to-many
     */
    cardinality: '1:1' | '1:N' | 'N:M';
}

/**
 * ETL mapping configuration
 */
export interface ETLMapping {
    id: string;
    /**
     * Target DW table
     */
    targetTableId: string;
    /**
     * Source table/file name
     */
    sourceName: string;
    /**
     * Column mappings: target column -> source expression
     */
    columnMappings: Record<string, string>;
    /**
     * Transformation logic
     */
    transformations?: string[];
    /**
     * Load strategy: full, incremental, upsert
     */
    loadStrategy: 'full' | 'incremental' | 'upsert';
}

/**
 * Hook options
 */
export interface UseDataWarehouseOptions {
    /**
     * Enable automatic layout
     */
    autoLayout?: boolean;
    /**
     * Star schema mode (fact in center, dimensions around)
     */
    starSchemaLayout?: boolean;
}

/**
 * Hook return value
 */
export interface UseDataWarehouseResult {
    // Tables
    tables: DWTable[];
    addTable: (table: Omit<DWTable, 'id'>) => string;
    updateTable: (tableId: string, updates: Partial<DWTable>) => void;
    deleteTable: (tableId: string) => void;
    getTable: (tableId: string) => DWTable | undefined;

    // Columns
    addColumn: (tableId: string, column: Omit<Column, 'id'>) => void;
    updateColumn: (tableId: string, columnId: string, updates: Partial<Column>) => void;
    deleteColumn: (tableId: string, columnId: string) => void;

    // Relationships
    relationships: ForeignKeyRelationship[];
    addRelationship: (relationship: Omit<ForeignKeyRelationship, 'id'>) => void;
    deleteRelationship: (relationshipId: string) => void;
    getRelationshipsForTable: (tableId: string) => ForeignKeyRelationship[];

    // ETL Mappings
    etlMappings: ETLMapping[];
    addETLMapping: (mapping: Omit<ETLMapping, 'id'>) => void;
    updateETLMapping: (mappingId: string, updates: Partial<ETLMapping>) => void;
    deleteETLMapping: (mappingId: string) => void;
    getETLMappingsForTable: (tableId: string) => ETLMapping[];

    // Schema Analysis
    getFactTables: () => DWTable[];
    getDimensionTables: () => DWTable[];
    getBridgeTables: () => DWTable[];
    getConformedDimensions: () => DWTable[];
    validateSchema: () => ValidationResult;

    // Grain Management
    setGrain: (tableId: string, grain: string) => void;
    getGrain: (tableId: string) => string | undefined;

    // Visualization
    syncToCanvas: () => void;
    autoLayoutStarSchema: (factTableId: string) => void;

    // Export
    exportDDL: (dialect: 'postgres' | 'mysql' | 'snowflake' | 'bigquery') => string;
    exportERDiagram: () => string;
}

/**
 * Data Warehouse Schema Hook
 * 
 * Provides comprehensive data warehouse modeling with fact/dimension tables,
 * foreign key relationships, grain definition, ETL mappings, and schema validation.
 * 
 * @example
 * ```tsx
 * const {
 *   tables,
 *   addTable,
 *   addRelationship,
 *   setGrain,
 *   validateSchema,
 *   exportDDL,
 * } = useDataWarehouse({ starSchemaLayout: true });
 * 
 * // Add fact table
 * const factId = addTable({
 *   name: 'fact_sales',
 *   type: 'fact',
 *   columns: [...],
 *   grain: 'One row per transaction line item',
 * });
 * 
 * // Add dimension
 * const dimId = addTable({
 *   name: 'dim_product',
 *   type: 'dimension',
 *   dimensionType: 'conformed',
 *   columns: [...],
 * });
 * 
 * // Create FK relationship
 * addRelationship({
 *   fromTableId: factId,
 *   fromColumnId: 'product_key',
 *   toTableId: dimId,
 *   toColumnId: 'product_key',
 *   cardinality: '1:N',
 * });
 * ```
 */
export function useDataWarehouse(options: UseDataWarehouseOptions = {}): UseDataWarehouseResult {
    const { autoLayout = false, starSchemaLayout = false } = options;
    const { getNodes, setNodes, getEdges, setEdges } = useReactFlow();

    const [tables, setTables] = useState<DWTable[]>([]);
    const [relationships, setRelationships] = useState<ForeignKeyRelationship[]>([]);
    const [etlMappings, setETLMappings] = useState<ETLMapping[]>([]);

    // Table Management
    const addTable = useCallback((table: Omit<DWTable, 'id'>): string => {
        const id = `table-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newTable: DWTable = {
            ...table,
            id,
            position: table.position || { x: Math.random() * 500, y: Math.random() * 500 },
        };

        setTables(prev => [...prev, newTable]);
        return id;
    }, []);

    const updateTable = useCallback((tableId: string, updates: Partial<DWTable>) => {
        setTables(prev =>
            prev.map(table =>
                table.id === tableId ? { ...table, ...updates } : table
            )
        );
    }, []);

    const deleteTable = useCallback((tableId: string) => {
        setTables(prev => prev.filter(table => table.id !== tableId));
        // Also delete related relationships
        setRelationships(prev =>
            prev.filter(rel => rel.fromTableId !== tableId && rel.toTableId !== tableId)
        );
        // Delete related ETL mappings
        setETLMappings(prev => prev.filter(mapping => mapping.targetTableId !== tableId));
    }, []);

    const getTable = useCallback((tableId: string): DWTable | undefined => {
        return tables.find(table => table.id === tableId);
    }, [tables]);

    // Column Management
    const addColumn = useCallback((tableId: string, column: Omit<Column, 'id'>) => {
        const columnId = `col-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newColumn: Column = { ...column, id: columnId };

        setTables(prev =>
            prev.map(table =>
                table.id === tableId
                    ? { ...table, columns: [...table.columns, newColumn] }
                    : table
            )
        );
    }, []);

    const updateColumn = useCallback((tableId: string, columnId: string, updates: Partial<Column>) => {
        setTables(prev =>
            prev.map(table =>
                table.id === tableId
                    ? {
                        ...table,
                        columns: table.columns.map(col =>
                            col.id === columnId ? { ...col, ...updates } : col
                        ),
                    }
                    : table
            )
        );
    }, []);

    const deleteColumn = useCallback((tableId: string, columnId: string) => {
        setTables(prev =>
            prev.map(table =>
                table.id === tableId
                    ? { ...table, columns: table.columns.filter(col => col.id !== columnId) }
                    : table
            )
        );
        // Delete relationships involving this column
        setRelationships(prev =>
            prev.filter(
                rel =>
                    !(
                        (rel.fromTableId === tableId && rel.fromColumnId === columnId) ||
                        (rel.toTableId === tableId && rel.toColumnId === columnId)
                    )
            )
        );
    }, []);

    // Relationship Management
    const addRelationship = useCallback((relationship: Omit<ForeignKeyRelationship, 'id'>) => {
        const id = `rel-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newRelationship: ForeignKeyRelationship = { ...relationship, id };

        setRelationships(prev => [...prev, newRelationship]);

        // Mark columns as foreign keys
        setTables(prev =>
            prev.map(table => {
                if (table.id === relationship.fromTableId) {
                    return {
                        ...table,
                        columns: table.columns.map(col =>
                            col.id === relationship.fromColumnId
                                ? {
                                    ...col,
                                    isForeignKey: true,
                                    references: {
                                        tableId: relationship.toTableId,
                                        columnId: relationship.toColumnId,
                                    },
                                }
                                : col
                        ),
                    };
                }
                return table;
            })
        );
    }, []);

    const deleteRelationship = useCallback((relationshipId: string) => {
        const relationship = relationships.find(rel => rel.id === relationshipId);
        if (relationship) {
            // Remove FK flag from column
            setTables(prev =>
                prev.map(table => {
                    if (table.id === relationship.fromTableId) {
                        return {
                            ...table,
                            columns: table.columns.map(col =>
                                col.id === relationship.fromColumnId
                                    ? { ...col, isForeignKey: false, references: undefined }
                                    : col
                            ),
                        };
                    }
                    return table;
                })
            );
        }

        setRelationships(prev => prev.filter(rel => rel.id !== relationshipId));
    }, [relationships]);

    const getRelationshipsForTable = useCallback(
        (tableId: string): ForeignKeyRelationship[] => {
            return relationships.filter(
                rel => rel.fromTableId === tableId || rel.toTableId === tableId
            );
        },
        [relationships]
    );

    // ETL Mapping Management
    const addETLMapping = useCallback((mapping: Omit<ETLMapping, 'id'>) => {
        const id = `etl-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newMapping: ETLMapping = { ...mapping, id };

        setETLMappings(prev => [...prev, newMapping]);
    }, []);

    const updateETLMapping = useCallback((mappingId: string, updates: Partial<ETLMapping>) => {
        setETLMappings(prev =>
            prev.map(mapping => (mapping.id === mappingId ? { ...mapping, ...updates } : mapping))
        );
    }, []);

    const deleteETLMapping = useCallback((mappingId: string) => {
        setETLMappings(prev => prev.filter(mapping => mapping.id !== mappingId));
    }, []);

    const getETLMappingsForTable = useCallback(
        (tableId: string): ETLMapping[] => {
            return etlMappings.filter(mapping => mapping.targetTableId === tableId);
        },
        [etlMappings]
    );

    // Schema Analysis
    const getFactTables = useCallback((): DWTable[] => {
        return tables.filter(table => table.type === 'fact');
    }, [tables]);

    const getDimensionTables = useCallback((): DWTable[] => {
        return tables.filter(table => table.type === 'dimension');
    }, [tables]);

    const getBridgeTables = useCallback((): DWTable[] => {
        return tables.filter(table => table.type === 'bridge');
    }, [tables]);

    const getConformedDimensions = useCallback((): DWTable[] => {
        return tables.filter(
            table => table.type === 'dimension' && table.dimensionType === 'conformed'
        );
    }, [tables]);

    const validateSchema = useCallback((): {
        valid: boolean;
        errors: string[];
        warnings: string[];
    } => {
        const errors: string[] = [];
        const warnings: string[] = [];

        // Check fact tables have grain defined
        const factTables = tables.filter(t => t.type === 'fact');
        for (const fact of factTables) {
            if (!fact.grain || fact.grain.trim() === '') {
                errors.push(`Fact table "${fact.name}" is missing grain definition`);
            }
        }

        // Check all tables have primary keys
        for (const table of tables) {
            const hasPK = table.columns.some(col => col.isPrimaryKey);
            if (!hasPK) {
                errors.push(`Table "${table.name}" has no primary key`);
            }
        }

        // Check FK relationships are valid
        for (const rel of relationships) {
            const fromTable = tables.find(t => t.id === rel.fromTableId);
            const toTable = tables.find(t => t.id === rel.toTableId);

            if (!fromTable) {
                errors.push(`Relationship references non-existent from table: ${rel.fromTableId}`);
            }
            if (!toTable) {
                errors.push(`Relationship references non-existent to table: ${rel.toTableId}`);
            }
        }

        // Warnings for best practices
        const dimTables = tables.filter(t => t.type === 'dimension');
        for (const dim of dimTables) {
            if (!dim.dimensionType) {
                warnings.push(`Dimension "${dim.name}" should specify a dimension type`);
            }
            if (dim.dimensionType === 'slowly-changing' && !dim.scdType) {
                warnings.push(`SCD dimension "${dim.name}" should specify SCD type`);
            }
        }

        return {
            valid: errors.length === 0,
            errors,
            warnings,
        };
    }, [tables, relationships]);

    // Grain Management
    const setGrain = useCallback((tableId: string, grain: string) => {
        updateTable(tableId, { grain });
    }, [updateTable]);

    const getGrain = useCallback(
        (tableId: string): string | undefined => {
            return tables.find(table => table.id === tableId)?.grain;
        },
        [tables]
    );

    // Visualization
    const syncToCanvas = useCallback(() => {
        // Convert tables to React Flow nodes
        const nodes: Node[] = tables.map(table => ({
            id: table.id,
            type: table.type === 'fact' ? 'factTable' : table.type === 'dimension' ? 'dimensionTable' : 'bridgeTable',
            position: table.position || { x: 0, y: 0 },
            data: {
                label: table.name,
                table,
            },
        }));

        // Convert relationships to React Flow edges
        const edges: Edge[] = relationships.map(rel => ({
            id: rel.id,
            source: rel.fromTableId,
            target: rel.toTableId,
            type: 'foreignKey',
            label: rel.cardinality,
            data: { relationship: rel },
        }));

        setNodes(nodes);
        setEdges(edges);
    }, [tables, relationships, setNodes, setEdges]);

    const autoLayoutStarSchema = useCallback(
        (factTableId: string) => {
            const fact = tables.find(t => t.id === factTableId);
            if (!fact || fact.type !== 'fact') return;

            // Place fact in center
            updateTable(factTableId, { position: { x: 400, y: 300 } });

            // Get dimensions connected to this fact
            const connectedDims = relationships
                .filter(rel => rel.fromTableId === factTableId)
                .map(rel => rel.toTableId);

            // Arrange dimensions in circle around fact
            const radius = 250;
            const angleStep = (2 * Math.PI) / connectedDims.length;

            connectedDims.forEach((dimId, index) => {
                const angle = index * angleStep;
                const x = 400 + radius * Math.cos(angle);
                const y = 300 + radius * Math.sin(angle);
                updateTable(dimId, { position: { x, y } });
            });
        },
        [tables, relationships, updateTable]
    );

    // Export
    const exportDDL = useCallback(
        (dialect: 'postgres' | 'mysql' | 'snowflake' | 'bigquery'): string => {
            let ddl = `-- Data Warehouse Schema DDL (${dialect})\n\n`;

            for (const table of tables) {
                ddl += `-- ${table.type.toUpperCase()}: ${table.name}\n`;
                if (table.description) {
                    ddl += `-- ${table.description}\n`;
                }
                if (table.type === 'fact' && table.grain) {
                    ddl += `-- Grain: ${table.grain}\n`;
                }

                ddl += `CREATE TABLE ${table.name} (\n`;

                const columnDefs = table.columns.map(col => {
                    let def = `    ${col.name} ${col.dataType}`;
                    if (col.isPrimaryKey) def += ' PRIMARY KEY';
                    if (!col.isNullable) def += ' NOT NULL';
                    return def;
                });

                ddl += columnDefs.join(',\n');
                ddl += '\n);\n\n';

                // Add foreign key constraints
                const tableRels = relationships.filter(rel => rel.fromTableId === table.id);
                for (const rel of tableRels) {
                    const fromCol = table.columns.find(c => c.id === rel.fromColumnId);
                    const toTable = tables.find(t => t.id === rel.toTableId);
                    const toCol = toTable?.columns.find(c => c.id === rel.toColumnId);

                    if (fromCol && toTable && toCol) {
                        ddl += `ALTER TABLE ${table.name} ADD CONSTRAINT fk_${table.name}_${toTable.name} `;
                        ddl += `FOREIGN KEY (${fromCol.name}) REFERENCES ${toTable.name}(${toCol.name});\n`;
                    }
                }

                ddl += '\n';
            }

            return ddl;
        },
        [tables, relationships]
    );

    const exportERDiagram = useCallback((): string => {
        // Export as Mermaid ER diagram
        let diagram = 'erDiagram\n';

        for (const table of tables) {
            diagram += `    ${table.name} {\n`;
            for (const col of table.columns) {
                const type = col.dataType.toLowerCase();
                const key = col.isPrimaryKey ? 'PK' : col.isForeignKey ? 'FK' : '';
                diagram += `        ${type} ${col.name} ${key}\n`;
            }
            diagram += '    }\n';
        }

        for (const rel of relationships) {
            const fromTable = tables.find(t => t.id === rel.fromTableId);
            const toTable = tables.find(t => t.id === rel.toTableId);
            if (fromTable && toTable) {
                const symbol = rel.cardinality === '1:1' ? '||--||' : rel.cardinality === '1:N' ? '||--o{' : 'o{--o{';
                diagram += `    ${fromTable.name} ${symbol} ${toTable.name} : "${rel.cardinality}"\n`;
            }
        }

        return diagram;
    }, [tables, relationships]);

    return {
        tables,
        addTable,
        updateTable,
        deleteTable,
        getTable,
        addColumn,
        updateColumn,
        deleteColumn,
        relationships,
        addRelationship,
        deleteRelationship,
        getRelationshipsForTable,
        etlMappings,
        addETLMapping,
        updateETLMapping,
        deleteETLMapping,
        getETLMappingsForTable,
        getFactTables,
        getDimensionTables,
        getBridgeTables,
        getConformedDimensions,
        validateSchema,
        setGrain,
        getGrain,
        syncToCanvas,
        autoLayoutStarSchema,
        exportDDL,
        exportERDiagram,
    };
}
