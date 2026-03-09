/**
 * @doc.type test
 * @doc.purpose Tests for DataWarehouseCanvas component (Journey 17.1)
 * @doc.layer product
 * @doc.pattern React Component Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DataWarehouseCanvas } from '../DataWarehouseCanvas';
import * as useDataWarehouseHook from '../../hooks/useDataWarehouse';
import type { DWTable, ForeignKeyRelationship, ETLMapping, ValidationResult } from '../../hooks/useDataWarehouse';

// Mock the hook
vi.mock('../../hooks/useDataWarehouse');

describe('DataWarehouseCanvas', () => {
    const mockTables: DWTable[] = [
        {
            id: 'fact-1',
            name: 'FactSales',
            type: 'fact',
            grain: 'One row per transaction line item',
            description: 'Sales fact table',
            columns: [
                {
                    id: 'col-1',
                    name: 'sales_id',
                    dataType: 'BIGINT',
                    isPrimaryKey: true,
                    isNullable: false,
                    isForeignKey: false,
                },
                {
                    id: 'col-2',
                    name: 'amount',
                    dataType: 'DECIMAL',
                    isPrimaryKey: false,
                    isNullable: false,
                    isForeignKey: false,
                },
                {
                    id: 'col-3',
                    name: 'customer_id',
                    dataType: 'BIGINT',
                    isPrimaryKey: false,
                    isNullable: false,
                    isForeignKey: true,
                },
            ],
        },
        {
            id: 'dim-1',
            name: 'DimCustomer',
            type: 'dimension',
            dimensionType: 'conformed',
            description: 'Customer dimension',
            columns: [
                {
                    id: 'col-4',
                    name: 'customer_id',
                    dataType: 'BIGINT',
                    isPrimaryKey: true,
                    isNullable: false,
                    isForeignKey: false,
                },
                {
                    id: 'col-5',
                    name: 'customer_name',
                    dataType: 'VARCHAR',
                    isPrimaryKey: false,
                    isNullable: false,
                    isForeignKey: false,
                },
            ],
        },
        {
            id: 'dim-2',
            name: 'DimDate',
            type: 'dimension',
            dimensionType: 'slowly-changing',
            scdType: 'Type 2',
            description: 'Date dimension with SCD Type 2',
            columns: [
                {
                    id: 'col-6',
                    name: 'date_key',
                    dataType: 'INT',
                    isPrimaryKey: true,
                    isNullable: false,
                    isForeignKey: false,
                },
                {
                    id: 'col-7',
                    name: 'full_date',
                    dataType: 'DATE',
                    isPrimaryKey: false,
                    isNullable: false,
                    isForeignKey: false,
                },
            ],
        },
    ];

    const mockRelationships: ForeignKeyRelationship[] = [
        {
            id: 'rel-1',
            fromTableId: 'fact-1',
            fromColumnId: 'col-3',
            toTableId: 'dim-1',
            toColumnId: 'col-4',
            cardinality: '1:N',
        },
    ];

    const mockETLMappings: ETLMapping[] = [
        {
            id: 'etl-1',
            targetTableId: 'fact-1',
            sourceName: 'staging.sales',
            columnMappings: {},
            transformations: [],
            loadStrategy: 'incremental',
        },
    ];

    const mockValidation: ValidationResult = {
        valid: true,
        errors: [],
        warnings: [],
    };

    const mockHookReturn = {
        tables: mockTables,
        addTable: vi.fn(),
        updateTable: vi.fn(),
        deleteTable: vi.fn(),
        getTable: vi.fn(),
        addColumn: vi.fn(),
        updateColumn: vi.fn(),
        deleteColumn: vi.fn(),
        relationships: mockRelationships,
        addRelationship: vi.fn(),
        deleteRelationship: vi.fn(),
        getRelationshipsForTable: vi.fn(),
        etlMappings: mockETLMappings,
        addETLMapping: vi.fn(),
        updateETLMapping: vi.fn(),
        deleteETLMapping: vi.fn(),
        getETLMappingsForTable: vi.fn(),
        validateSchema: vi.fn(() => mockValidation),
        getFactTables: vi.fn(),
        getDimensionTables: vi.fn(),
        getBridgeTables: vi.fn(),
        getConformedDimensions: vi.fn(),
        setGrain: vi.fn(),
        getGrain: vi.fn(),
        syncToCanvas: vi.fn(),
        autoLayoutStarSchema: vi.fn(),
        exportDDL: vi.fn(() => 'CREATE TABLE fact_sales ...'),
        exportERDiagram: vi.fn(() => 'erDiagram\n  FactSales ||--o{ DimCustomer : contains'),
    };

    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(useDataWarehouseHook.useDataWarehouse).mockReturnValue(mockHookReturn);
    });

    describe('Rendering', () => {
        it('should render the component with header', () => {
            render(<DataWarehouseCanvas />);

            expect(screen.getByText('Data Warehouse Schema')).toBeInTheDocument();
            expect(screen.getByText('Design fact tables, dimensions, and relationships')).toBeInTheDocument();
        });

        it('should render all tabs', () => {
            render(<DataWarehouseCanvas />);

            expect(screen.getByRole('tab', { name: /tables/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /relationships/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /etl mappings/i })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: /validation/i })).toBeInTheDocument();
        });

        it('should render export DDL button', () => {
            render(<DataWarehouseCanvas />);

            expect(screen.getByRole('button', { name: /export ddl/i })).toBeInTheDocument();
        });

        it('should render auto layout button when star schema enabled and fact table exists', () => {
            render(<DataWarehouseCanvas enableStarSchemaLayout />);

            expect(screen.getByRole('button', { name: /auto layout/i })).toBeInTheDocument();
        });

        it('should not render auto layout button when disabled', () => {
            render(<DataWarehouseCanvas enableStarSchemaLayout={false} />);

            expect(screen.queryByRole('button', { name: /auto layout/i })).not.toBeInTheDocument();
        });
    });

    describe('Tables Tab', () => {
        it('should display all tables', () => {
            render(<DataWarehouseCanvas />);

            expect(screen.getByText('FactSales')).toBeInTheDocument();
            expect(screen.getByText('DimCustomer')).toBeInTheDocument();
            expect(screen.getByText('DimDate')).toBeInTheDocument();
        });

        it('should display table metadata', () => {
            render(<DataWarehouseCanvas />);

            // Fact table grain
            expect(screen.getByText(/One row per transaction line item/i)).toBeInTheDocument();

            // Dimension types
            expect(screen.getByText('conformed')).toBeInTheDocument();
            expect(screen.getByText('Type 2')).toBeInTheDocument();
        });

        it('should display column count for each table', () => {
            render(<DataWarehouseCanvas />);

            const factCard = screen.getByText('FactSales').closest('div[class*="MuiPaper"]') as HTMLElement;
            expect(within(factCard).getByText(/Columns \(3\)/i)).toBeInTheDocument();
        });

        it('should display primary key indicator on columns', () => {
            render(<DataWarehouseCanvas />);

            const factCard = screen.getByText('FactSales').closest('div[class*="MuiPaper"]') as HTMLElement;
            expect(within(factCard).getByText(/🔑 sales_id/)).toBeInTheDocument();
        });

        it('should display foreign key indicator on columns', () => {
            render(<DataWarehouseCanvas />);

            const factCard = screen.getByText('FactSales').closest('div[class*="MuiPaper"]') as HTMLElement;
            expect(within(factCard).getByText(/🔗 customer_id/)).toBeInTheDocument();
        });

        it('should open add table dialog when clicking add button', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            const addButton = screen.getByRole('button', { name: /add table/i });
            await user.click(addButton);

            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.getByText('Add Table')).toBeInTheDocument();
        });

        it('should show empty state when no tables exist', () => {
            vi.mocked(useDataWarehouseHook.useDataWarehouse).mockReturnValue({
                ...mockHookReturn,
                tables: [],
            });

            render(<DataWarehouseCanvas />);

            expect(screen.getByText(/No tables defined yet/i)).toBeInTheDocument();
        });

        it('should delete table when clicking delete button', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            const factCard = screen.getByText('FactSales').closest('div[class*="MuiPaper"]') as HTMLElement;
            const deleteButton = within(factCard).getAllByRole('button')[1]; // Second button is delete
            await user.click(deleteButton);

            expect(mockHookReturn.deleteTable).toHaveBeenCalledWith('fact-1');
        });
    });

    describe('Add Table Dialog', () => {
        it('should add fact table with grain', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('button', { name: /add table/i }));

            await user.type(screen.getByLabelText(/table name/i), 'FactOrders');
            await user.click(screen.getByLabelText(/table type/i));
            await user.click(screen.getByRole('option', { name: /fact/i }));
            await user.type(screen.getByLabelText(/grain/i), 'One row per order');
            await user.type(screen.getByLabelText(/description/i), 'Orders fact table');

            await user.click(screen.getByRole('button', { name: 'Add' }));

            await waitFor(() => {
                expect(mockHookReturn.addTable).toHaveBeenCalledWith({
                    name: 'FactOrders',
                    type: 'fact',
                    grain: 'One row per order',
                    description: 'Orders fact table',
                    columns: [],
                });
            });
        });

        it('should add dimension table with type', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('button', { name: /add table/i }));

            await user.type(screen.getByLabelText(/table name/i), 'DimProduct');
            await user.click(screen.getByLabelText(/table type/i));
            await user.click(screen.getByRole('option', { name: /dimension/i }));

            // Dimension type dropdown should appear
            await user.click(screen.getByLabelText(/dimension type/i));
            await user.click(screen.getByRole('option', { name: /conformed/i }));

            await user.click(screen.getByRole('button', { name: 'Add' }));

            await waitFor(() => {
                expect(mockHookReturn.addTable).toHaveBeenCalledWith(
                    expect.objectContaining({
                        name: 'DimProduct',
                        type: 'dimension',
                        dimensionType: 'conformed',
                    })
                );
            });
        });

        it('should show SCD type selector for slowly-changing dimensions', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('button', { name: /add table/i }));

            await user.click(screen.getByLabelText(/table type/i));
            await user.click(screen.getByRole('option', { name: /dimension/i }));

            await user.click(screen.getByLabelText(/dimension type/i));
            await user.click(screen.getByRole('option', { name: /slowly-changing/i }));

            // SCD type dropdown should appear
            expect(screen.getByLabelText(/scd type/i)).toBeInTheDocument();

            await user.click(screen.getByLabelText(/scd type/i));
            await user.click(screen.getByRole('option', { name: /type 2/i }));

            await user.type(screen.getByLabelText(/table name/i), 'DimSCD');
            await user.click(screen.getByRole('button', { name: 'Add' }));

            await waitFor(() => {
                expect(mockHookReturn.addTable).toHaveBeenCalledWith(
                    expect.objectContaining({
                        scdType: 'Type 2',
                    })
                );
            });
        });

        it('should close dialog when clicking cancel', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('button', { name: /add table/i }));
            expect(screen.getByRole('dialog')).toBeInTheDocument();

            await user.click(screen.getByRole('button', { name: /cancel/i }));
            await waitFor(() => {
                expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
            });
        });
    });

    describe('Add Column Dialog', () => {
        it('should open add column dialog when clicking add on table card', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            const factCard = screen.getByText('FactSales').closest('div[class*="MuiPaper"]') as HTMLElement;
            const addButton = within(factCard).getAllByRole('button')[0]; // First button is add column
            await user.click(addButton);

            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.getByText('Add Column to FactSales')).toBeInTheDocument();
        });

        it('should add column with all properties', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            const factCard = screen.getByText('FactSales').closest('div[class*="MuiPaper"]') as HTMLElement;
            const addButton = within(factCard).getAllByRole('button')[0];
            await user.click(addButton);

            await user.type(screen.getByLabelText(/column name/i), 'quantity');
            await user.click(screen.getByLabelText(/data type/i));
            await user.click(screen.getByRole('option', { name: /INT/i }));
            await user.click(screen.getByLabelText(/nullable/i)); // Uncheck nullable
            await user.type(screen.getByLabelText(/description/i), 'Quantity sold');

            await user.click(screen.getByRole('button', { name: 'Add' }));

            await waitFor(() => {
                expect(mockHookReturn.addColumn).toHaveBeenCalledWith('fact-1', {
                    name: 'quantity',
                    dataType: 'INT',
                    isPrimaryKey: false,
                    isNullable: false,
                    isForeignKey: false,
                    description: 'Quantity sold',
                });
            });
        });

        it('should allow marking column as primary key', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            const dimCard = screen.getByText('DimCustomer').closest('div[class*="MuiPaper"]') as HTMLElement;
            const addButton = within(dimCard).getAllByRole('button')[0];
            await user.click(addButton);

            await user.type(screen.getByLabelText(/column name/i), 'id');
            await user.click(screen.getByLabelText(/primary key/i));

            await user.click(screen.getByRole('button', { name: 'Add' }));

            await waitFor(() => {
                expect(mockHookReturn.addColumn).toHaveBeenCalledWith(
                    'dim-1',
                    expect.objectContaining({
                        isPrimaryKey: true,
                    })
                );
            });
        });
    });

    describe('Relationships Tab', () => {
        it('should switch to relationships tab', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('tab', { name: /relationships/i }));

            expect(screen.getByText(/Foreign Key Relationships \(1\)/i)).toBeInTheDocument();
        });

        it('should display all relationships', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('tab', { name: /relationships/i }));

            expect(screen.getByText(/FactSales.customer_id → DimCustomer.customer_id/)).toBeInTheDocument();
            expect(screen.getByText(/Cardinality: 1:N/i)).toBeInTheDocument();
        });

        it('should open add relationship dialog', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('tab', { name: /relationships/i }));
            await user.click(screen.getByRole('button', { name: /add relationship/i }));

            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.getByText('Add Foreign Key Relationship')).toBeInTheDocument();
        });

        it('should delete relationship', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('tab', { name: /relationships/i }));

            const deleteButtons = screen.getAllByRole('button', { name: '' }).filter(btn => {
                const icon = btn.querySelector('svg');
                return icon?.getAttribute('data-testid') === 'DeleteIcon';
            });

            await user.click(deleteButtons[0]);

            expect(mockHookReturn.deleteRelationship).toHaveBeenCalledWith('rel-1');
        });

        it('should show empty state when no relationships exist', async () => {
            const user = userEvent.setup();
            vi.mocked(useDataWarehouseHook.useDataWarehouse).mockReturnValue({
                ...mockHookReturn,
                relationships: [],
            });

            render(<DataWarehouseCanvas />);
            await user.click(screen.getByRole('tab', { name: /relationships/i }));

            expect(screen.getByText(/No relationships defined yet/i)).toBeInTheDocument();
        });
    });

    describe('Add Relationship Dialog', () => {
        it('should add relationship with all fields', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('tab', { name: /relationships/i }));
            await user.click(screen.getByRole('button', { name: /add relationship/i }));

            // Select from table
            await user.click(screen.getByLabelText(/from table/i));
            await user.click(screen.getByRole('option', { name: /FactSales/i }));

            // Select from column
            await user.click(screen.getByLabelText(/from column/i));
            await user.click(screen.getByRole('option', { name: /customer_id/i }));

            // Select to table
            await user.click(screen.getByLabelText(/to table/i));
            await user.click(screen.getByRole('option', { name: /DimCustomer/i }));

            // Select to column
            await user.click(screen.getByLabelText(/to column/i));
            await user.click(screen.getAllByRole('option', { name: /customer_id/i })[0]);

            // Select cardinality
            await user.click(screen.getByLabelText(/cardinality/i));
            await user.click(screen.getByRole('option', { name: /One-to-Many/i }));

            await user.click(screen.getByRole('button', { name: 'Add' }));

            await waitFor(() => {
                expect(mockHookReturn.addRelationship).toHaveBeenCalledWith({
                    fromTableId: 'fact-1',
                    fromColumnId: 'col-3',
                    toTableId: 'dim-1',
                    toColumnId: 'col-4',
                    cardinality: '1:N',
                });
            });
        });
    });

    describe('ETL Mappings Tab', () => {
        it('should switch to ETL mappings tab', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('tab', { name: /etl mappings/i }));

            expect(screen.getByText(/ETL Mappings \(1\)/i)).toBeInTheDocument();
        });

        it('should display all ETL mappings', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('tab', { name: /etl mappings/i }));

            expect(screen.getByText(/staging.sales → FactSales/)).toBeInTheDocument();
            expect(screen.getByText(/Load Strategy: incremental/i)).toBeInTheDocument();
        });

        it('should open add ETL mapping dialog', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('tab', { name: /etl mappings/i }));
            await user.click(screen.getByRole('button', { name: /add etl mapping/i }));

            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.getByText('Add ETL Mapping')).toBeInTheDocument();
        });

        it('should delete ETL mapping', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('tab', { name: /etl mappings/i }));

            const deleteButtons = screen.getAllByRole('button', { name: '' }).filter(btn => {
                const icon = btn.querySelector('svg');
                return icon?.getAttribute('data-testid') === 'DeleteIcon';
            });

            await user.click(deleteButtons[0]);

            expect(mockHookReturn.deleteETLMapping).toHaveBeenCalledWith('etl-1');
        });

        it('should show empty state when no mappings exist', async () => {
            const user = userEvent.setup();
            vi.mocked(useDataWarehouseHook.useDataWarehouse).mockReturnValue({
                ...mockHookReturn,
                etlMappings: [],
            });

            render(<DataWarehouseCanvas />);
            await user.click(screen.getByRole('tab', { name: /etl mappings/i }));

            expect(screen.getByText(/No ETL mappings defined yet/i)).toBeInTheDocument();
        });
    });

    describe('Add ETL Mapping Dialog', () => {
        it('should add ETL mapping with all fields', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('tab', { name: /etl mappings/i }));
            await user.click(screen.getByRole('button', { name: /add etl mapping/i }));

            await user.click(screen.getByLabelText(/target table/i));
            await user.click(screen.getByRole('option', { name: /FactSales/i }));

            await user.type(screen.getByLabelText(/source name/i), 'staging.orders');

            await user.click(screen.getByLabelText(/load strategy/i));
            await user.click(screen.getByRole('option', { name: /upsert/i }));

            await user.click(screen.getByRole('button', { name: 'Add' }));

            await waitFor(() => {
                expect(mockHookReturn.addETLMapping).toHaveBeenCalledWith({
                    targetTableId: 'fact-1',
                    sourceName: 'staging.orders',
                    loadStrategy: 'upsert',
                    columnMappings: {},
                    transformations: [],
                });
            });
        });
    });

    describe('Validation Tab', () => {
        it('should show validation success when schema is valid', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('tab', { name: /validation/i }));

            expect(screen.getByText(/Schema validation passed/i)).toBeInTheDocument();
        });

        it('should display validation errors when schema is invalid', async () => {
            const user = userEvent.setup();
            vi.mocked(useDataWarehouseHook.useDataWarehouse).mockReturnValue({
                ...mockHookReturn,
                validateSchema: vi.fn(() => ({
                    valid: false,
                    errors: ['Fact table must have grain defined', 'Table X has no primary key'],
                    warnings: [],
                })),
            });

            render(<DataWarehouseCanvas />);
            await user.click(screen.getByRole('tab', { name: /validation/i }));

            expect(screen.getByText(/Schema validation failed/i)).toBeInTheDocument();
            expect(screen.getByText(/Fact table must have grain defined/i)).toBeInTheDocument();
            expect(screen.getByText(/Table X has no primary key/i)).toBeInTheDocument();
        });

        it('should display validation warnings', async () => {
            const user = userEvent.setup();
            vi.mocked(useDataWarehouseHook.useDataWarehouse).mockReturnValue({
                ...mockHookReturn,
                validateSchema: vi.fn(() => ({
                    valid: true,
                    errors: [],
                    warnings: ['Consider adding indexes', 'Large VARCHAR size detected'],
                })),
            });

            render(<DataWarehouseCanvas />);
            await user.click(screen.getByRole('tab', { name: /validation/i }));

            expect(screen.getByText(/Consider adding indexes/i)).toBeInTheDocument();
            expect(screen.getByText(/Large VARCHAR size detected/i)).toBeInTheDocument();
        });

        it('should display validation errors in header alert', () => {
            vi.mocked(useDataWarehouseHook.useDataWarehouse).mockReturnValue({
                ...mockHookReturn,
                validateSchema: vi.fn(() => ({
                    valid: false,
                    errors: ['Critical error 1'],
                    warnings: [],
                })),
            });

            render(<DataWarehouseCanvas />);

            expect(screen.getByText(/Schema Validation Errors:/i)).toBeInTheDocument();
            expect(screen.getByText(/Critical error 1/i)).toBeInTheDocument();
        });

        it('should display validation warnings in header alert', () => {
            vi.mocked(useDataWarehouseHook.useDataWarehouse).mockReturnValue({
                ...mockHookReturn,
                validateSchema: vi.fn(() => ({
                    valid: true,
                    errors: [],
                    warnings: ['Warning 1', 'Warning 2'],
                })),
            });

            render(<DataWarehouseCanvas />);

            expect(screen.getByText(/Warnings:/i)).toBeInTheDocument();
            expect(screen.getByText(/Warning 1/i)).toBeInTheDocument();
        });
    });

    describe('Export Functionality', () => {
        it('should open export dialog', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('button', { name: /export ddl/i }));

            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.getByText('Export Schema')).toBeInTheDocument();
        });

        it('should export PostgreSQL DDL', async () => {
            const user = userEvent.setup();
            const createElementSpy = vi.spyOn(document, 'createElement');
            const clickSpy = vi.fn();

            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('button', { name: /export ddl/i }));

            await user.click(screen.getByLabelText(/export format/i));
            await user.click(screen.getByRole('option', { name: /PostgreSQL DDL/i }));

            createElementSpy.mockReturnValue({
                click: clickSpy,
                href: '',
                download: '',
            } as unknown);

            await user.click(screen.getAllByRole('button', { name: /export/i })[1]); // Second Export button in dialog

            expect(mockHookReturn.exportDDL).toHaveBeenCalledWith('postgres');
        });

        it('should export Mermaid diagram', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('button', { name: /export ddl/i }));

            await user.click(screen.getByLabelText(/export format/i));
            await user.click(screen.getByRole('option', { name: /Mermaid ER Diagram/i }));

            await user.click(screen.getAllByRole('button', { name: /export/i })[1]);

            expect(mockHookReturn.exportERDiagram).toHaveBeenCalled();
        });

        it('should support all SQL dialects', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            await user.click(screen.getByRole('button', { name: /export ddl/i }));

            await user.click(screen.getByLabelText(/export format/i));

            expect(screen.getByRole('option', { name: /PostgreSQL DDL/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /MySQL DDL/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /Snowflake DDL/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /BigQuery DDL/i })).toBeInTheDocument();
        });
    });

    describe('Auto Layout', () => {
        it('should trigger auto layout for star schema', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas enableStarSchemaLayout />);

            await user.click(screen.getByRole('button', { name: /auto layout/i }));

            expect(mockHookReturn.autoLayoutStarSchema).toHaveBeenCalledWith('fact-1');
        });

        it('should not show auto layout button when no fact table exists', () => {
            vi.mocked(useDataWarehouseHook.useDataWarehouse).mockReturnValue({
                ...mockHookReturn,
                tables: mockTables.filter(t => t.type !== 'fact'),
            });

            render(<DataWarehouseCanvas enableStarSchemaLayout />);

            expect(screen.queryByRole('button', { name: /auto layout/i })).not.toBeInTheDocument();
        });
    });

    describe('Schema Change Callback', () => {
        it('should call onSchemaChange when table is added', async () => {
            const onSchemaChange = vi.fn();
            const user = userEvent.setup();
            render(<DataWarehouseCanvas onSchemaChange={onSchemaChange} />);

            await user.click(screen.getByRole('button', { name: /add table/i }));
            await user.type(screen.getByLabelText(/table name/i), 'NewTable');
            await user.click(screen.getByRole('button', { name: 'Add' }));

            await waitFor(() => {
                expect(onSchemaChange).toHaveBeenCalledWith(mockTables);
            });
        });
    });

    describe('Integration Workflows', () => {
        it('should support complete workflow: create table -> add columns -> create relationship', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            // Add table
            await user.click(screen.getByRole('button', { name: /add table/i }));
            await user.type(screen.getByLabelText(/table name/i), 'NewFact');
            await user.click(screen.getByRole('button', { name: 'Add' }));

            expect(mockHookReturn.addTable).toHaveBeenCalled();

            // Add column to existing table
            const factCard = screen.getByText('FactSales').closest('div[class*="MuiPaper"]');
            await user.click(within(factCard!).getAllByRole('button')[0]);
            await user.type(screen.getByLabelText(/column name/i), 'new_column');
            await user.click(screen.getByRole('button', { name: 'Add' }));

            expect(mockHookReturn.addColumn).toHaveBeenCalled();

            // Add relationship
            await user.click(screen.getByRole('tab', { name: /relationships/i }));
            await user.click(screen.getByRole('button', { name: /add relationship/i }));

            expect(screen.getByText('Add Foreign Key Relationship')).toBeInTheDocument();
        });

        it('should support ETL mapping workflow', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            // Switch to ETL tab
            await user.click(screen.getByRole('tab', { name: /etl mappings/i }));

            // Add mapping
            await user.click(screen.getByRole('button', { name: /add etl mapping/i }));
            await user.click(screen.getByLabelText(/target table/i));
            await user.click(screen.getByRole('option', { name: /FactSales/i }));
            await user.type(screen.getByLabelText(/source name/i), 'source_table');
            await user.click(screen.getByRole('button', { name: 'Add' }));

            expect(mockHookReturn.addETLMapping).toHaveBeenCalled();
        });

        it('should validate schema and export DDL', async () => {
            const user = userEvent.setup();
            render(<DataWarehouseCanvas />);

            // Check validation
            await user.click(screen.getByRole('tab', { name: /validation/i }));
            expect(screen.getByText(/Schema validation passed/i)).toBeInTheDocument();

            // Export
            await user.click(screen.getByRole('button', { name: /export ddl/i }));
            await user.click(screen.getAllByRole('button', { name: /export/i })[1]);

            expect(mockHookReturn.exportDDL).toHaveBeenCalled();
        });
    });
});
