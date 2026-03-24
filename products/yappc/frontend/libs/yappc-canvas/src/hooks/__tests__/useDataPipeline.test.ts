/**
 * useDataPipeline Tests
 * 
 * Tests for data pipeline hook
 * 
 * @doc.type test
 * @doc.purpose useDataPipeline hook tests
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useDataPipeline } from '../useDataPipeline';
import type { PipelineNodeType } from '../useDataPipeline';

describe('useDataPipeline', () => {
    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useDataPipeline());

            expect(result.current.nodes).toEqual([]);
            expect(result.current.connections).toEqual([]);
            expect(result.current.selectedNode).toBeNull();
            expect(result.current.pipelineName).toBe('data_pipeline');
            expect(result.current.schedule).toBe('0 2 * * *');
        });

        it('should initialize with custom name and schedule', () => {
            const { result } = renderHook(() => useDataPipeline({
                initialName: 'custom_pipeline',
                initialSchedule: '0 */6 * * *',
            }));

            expect(result.current.pipelineName).toBe('custom_pipeline');
            expect(result.current.schedule).toBe('0 */6 * * *');
        });
    });

    describe('Node Management', () => {
        it('should add source node', () => {
            const { result } = renderHook(() => useDataPipeline());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('source');
            });

            expect(result.current.nodes).toHaveLength(1);
            expect(result.current.nodes[0]).toMatchObject({
                type: 'source',
                name: 'Source 1',
            });
            expect(result.current.selectedNode).toBe(nodeId!);
        });

        it('should add transform node', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                result.current.addNode('transform');
            });

            expect(result.current.nodes).toHaveLength(1);
            expect(result.current.nodes[0].type).toBe('transform');
            expect(result.current.nodes[0].name).toBe('Transform 1');
        });

        it('should add sink node', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                result.current.addNode('sink');
            });

            expect(result.current.nodes).toHaveLength(1);
            expect(result.current.nodes[0].type).toBe('sink');
            expect(result.current.nodes[0].name).toBe('Sink 1');
        });

        it('should add multiple nodes and increment names', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                result.current.addNode('source');
                result.current.addNode('source');
                result.current.addNode('transform');
            });

            expect(result.current.nodes).toHaveLength(3);
            expect(result.current.nodes[0].name).toBe('Source 1');
            expect(result.current.nodes[1].name).toBe('Source 2');
            expect(result.current.nodes[2].name).toBe('Transform 1');
        });

        it('should update node', () => {
            const { result } = renderHook(() => useDataPipeline());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('source');
            });

            act(() => {
                result.current.updateNode(nodeId!, { name: 'Updated Source' });
            });

            expect(result.current.nodes[0].name).toBe('Updated Source');
        });

        it('should update node config', () => {
            const { result } = renderHook(() => useDataPipeline());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('source');
            });

            act(() => {
                result.current.updateNodeConfig(nodeId!, {
                    sourceType: 'postgres',
                    tableName: 'users',
                });
            });

            expect(result.current.nodes[0].config).toMatchObject({
                sourceType: 'postgres',
                tableName: 'users',
            });
        });

        it('should delete node', () => {
            const { result } = renderHook(() => useDataPipeline());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('source');
                result.current.addNode('transform');
            });

            act(() => {
                result.current.deleteNode(nodeId!);
            });

            expect(result.current.nodes).toHaveLength(1);
            expect(result.current.nodes[0].type).toBe('transform');
        });

        it('should clear selection when deleting selected node', () => {
            const { result } = renderHook(() => useDataPipeline());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('source');
            });

            expect(result.current.selectedNode).toBe(nodeId!);

            act(() => {
                result.current.deleteNode(nodeId!);
            });

            expect(result.current.selectedNode).toBeNull();
        });

        it('should select node', () => {
            const { result } = renderHook(() => useDataPipeline());

            let node1: string, node2: string;
            act(() => {
                node1 = result.current.addNode('source');
                node2 = result.current.addNode('transform');
            });

            act(() => {
                result.current.selectNode(node1!);
            });

            expect(result.current.selectedNode).toBe(node1!);
        });
    });

    describe('Connection Management', () => {
        it('should add valid connection', () => {
            const { result } = renderHook(() => useDataPipeline());

            let source: string, transform: string;
            act(() => {
                source = result.current.addNode('source');
                transform = result.current.addNode('transform');
            });

            act(() => {
                result.current.addConnection(source!, transform!);
            });

            expect(result.current.connections).toHaveLength(1);
            expect(result.current.connections[0]).toMatchObject({
                from: source,
                to: transform,
            });
        });

        it('should allow source to transform connection', () => {
            const { result } = renderHook(() => useDataPipeline());

            let source: string, transform: string;
            act(() => {
                source = result.current.addNode('source');
                transform = result.current.addNode('transform');
            });

            const canConnect = result.current.canConnect(source!, transform!);
            expect(canConnect).toBe(true);
        });

        it('should allow transform to sink connection', () => {
            const { result } = renderHook(() => useDataPipeline());

            let transform: string, sink: string;
            act(() => {
                transform = result.current.addNode('transform');
                sink = result.current.addNode('sink');
            });

            const canConnect = result.current.canConnect(transform!, sink!);
            expect(canConnect).toBe(true);
        });

        it('should allow transform to transform connection', () => {
            const { result } = renderHook(() => useDataPipeline());

            let transform1: string, transform2: string;
            act(() => {
                transform1 = result.current.addNode('transform');
                transform2 = result.current.addNode('transform');
            });

            const canConnect = result.current.canConnect(transform1!, transform2!);
            expect(canConnect).toBe(true);
        });

        it('should prevent duplicate connections', () => {
            const { result } = renderHook(() => useDataPipeline());

            let source: string, transform: string;
            act(() => {
                source = result.current.addNode('source');
                transform = result.current.addNode('transform');
            });

            act(() => {
                result.current.addConnection(source!, transform!);
            });

            const canConnect = result.current.canConnect(source!, transform!);
            expect(canConnect).toBe(false);
        });

        it('should delete connection', () => {
            const { result } = renderHook(() => useDataPipeline());

            let source: string, transform: string;
            act(() => {
                source = result.current.addNode('source');
                transform = result.current.addNode('transform');
                result.current.addConnection(source!, transform!);
            });

            const connectionId = result.current.connections[0].id;

            act(() => {
                result.current.deleteConnection(connectionId);
            });

            expect(result.current.connections).toHaveLength(0);
        });

        it('should delete connections when node is deleted', () => {
            const { result } = renderHook(() => useDataPipeline());

            let source: string, transform: string, sink: string;
            act(() => {
                source = result.current.addNode('source');
                transform = result.current.addNode('transform');
                sink = result.current.addNode('sink');
                result.current.addConnection(source!, transform!);
                result.current.addConnection(transform!, sink!);
            });

            act(() => {
                result.current.deleteNode(transform!);
            });

            expect(result.current.connections).toHaveLength(0);
        });
    });

    describe('Schema Management', () => {
        it('should set node schema', () => {
            const { result } = renderHook(() => useDataPipeline());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('source');
            });

            const schema = [
                { name: 'id', type: 'number' as const, nullable: false },
                { name: 'name', type: 'string' as const, nullable: false },
            ];

            act(() => {
                result.current.setNodeSchema(nodeId!, schema);
            });

            expect(result.current.nodes[0].config.schema).toEqual(schema);
        });

        it('should add schema mapping', () => {
            const { result } = renderHook(() => useDataPipeline());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('transform');
            });

            const mapping = {
                sourceColumn: 'user_id',
                targetColumn: 'id',
                transformation: 'CAST(user_id AS STRING)',
            };

            act(() => {
                result.current.addSchemaMapping(nodeId!, mapping);
            });

            expect(result.current.nodes[0].config.mappings).toHaveLength(1);
            expect(result.current.nodes[0].config.mappings![0]).toEqual(mapping);
        });

        it('should remove schema mapping', () => {
            const { result } = renderHook(() => useDataPipeline());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('transform');
                result.current.addSchemaMapping(nodeId!, {
                    sourceColumn: 'col1',
                    targetColumn: 'col1_new',
                });
                result.current.addSchemaMapping(nodeId!, {
                    sourceColumn: 'col2',
                    targetColumn: 'col2_new',
                });
            });

            act(() => {
                result.current.removeSchemaMapping(nodeId!, 0);
            });

            expect(result.current.nodes[0].config.mappings).toHaveLength(1);
            expect(result.current.nodes[0].config.mappings![0].sourceColumn).toBe('col2');
        });
    });

    describe('Code Generation', () => {
        it('should generate Airflow DAG', () => {
            const { result } = renderHook(() => useDataPipeline());

            let source: string, transform: string, sink: string;
            act(() => {
                source = result.current.addNode('source');
                transform = result.current.addNode('transform');
                sink = result.current.addNode('sink');
                result.current.addConnection(source!, transform!);
                result.current.addConnection(transform!, sink!);
            });

            const dag = result.current.generateAirflowDAG();

            expect(dag).toContain('from airflow import DAG');
            expect(dag).toContain('from airflow.operators.python import PythonOperator');
            expect(dag).toContain("'data_pipeline'");
            expect(dag).toContain("schedule_interval='0 2 * * *");
            expect(dag).toContain('def source_');
            expect(dag).toContain('def transform_');
            expect(dag).toContain('def sink_');
        });

        it('should generate DAG with custom name and schedule', () => {
            const { result } = renderHook(() => useDataPipeline({
                initialName: 'analytics_pipeline',
                initialSchedule: '0 */4 * * *',
            }));

            act(() => {
                result.current.addNode('source');
            });

            const dag = result.current.generateAirflowDAG();

            expect(dag).toContain("'analytics_pipeline'");
            expect(dag).toContain("schedule_interval='0 */4 * * *");
        });

        it('should generate DBT model', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                const source = result.current.addNode('source');
                result.current.updateNodeConfig(source, { tableName: 'raw_events' });
                result.current.addNode('transform');
            });

            const dbt = result.current.generateDBTModel();

            expect(dbt).toContain('-- DBT Model: data_pipeline');
            expect(dbt).toContain("source('raw_events', 'raw_events')");
            expect(dbt).toContain('with source_data as');
        });

        it('should export pipeline as JSON', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                const source = result.current.addNode('source');
                const sink = result.current.addNode('sink');
                result.current.addConnection(source, sink);
            });

            const exported = result.current.exportPipeline();
            const parsed = JSON.parse(exported);

            expect(parsed).toHaveProperty('name', 'data_pipeline');
            expect(parsed).toHaveProperty('schedule', '0 2 * * *');
            expect(parsed.nodes).toHaveLength(2);
            expect(parsed.connections).toHaveLength(1);
        });
    });

    describe('Validation', () => {
        it('should validate empty pipeline', () => {
            const { result } = renderHook(() => useDataPipeline());

            const errors = result.current.validatePipeline();

            expect(errors).toHaveLength(2);
            expect(errors[0].message).toContain('at least one source');
            expect(errors[1].message).toContain('at least one sink');
        });

        it('should validate missing source', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                result.current.addNode('sink');
            });

            const errors = result.current.validatePipeline();
            const sourceError = errors.find((e) => e.message.includes('source'));

            expect(sourceError).toBeDefined();
        });

        it('should validate unconfigured nodes', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                result.current.addNode('source');
                result.current.addNode('sink');
            });

            const errors = result.current.validatePipeline();

            expect(errors.some((e) => e.message.includes('Source type not configured'))).toBe(true);
            expect(errors.some((e) => e.message.includes('Sink type not configured'))).toBe(true);
        });

        it('should validate disconnected nodes', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                const source = result.current.addNode('source');
                result.current.updateNodeConfig(source, { sourceType: 'postgres' });
                const sink = result.current.addNode('sink');
                result.current.updateNodeConfig(sink, { sinkType: 'bigquery' });
            });

            const errors = result.current.validatePipeline();

            expect(errors.some((e) => e.message.includes('not connected'))).toBe(true);
        });

        it('should mark valid pipeline', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                const source = result.current.addNode('source');
                result.current.updateNodeConfig(source, { sourceType: 'postgres' });
                const sink = result.current.addNode('sink');
                result.current.updateNodeConfig(sink, { sinkType: 'bigquery' });
                result.current.addConnection(source, sink);
            });

            expect(result.current.isValid).toBe(true);
        });
    });

    describe('Utility Functions', () => {
        it('should get node by ID', () => {
            const { result } = renderHook(() => useDataPipeline());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('source');
            });

            const node = result.current.getNode(nodeId!);

            expect(node).toBeDefined();
            expect(node?.id).toBe(nodeId);
        });

        it('should get nodes by type', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                result.current.addNode('source');
                result.current.addNode('source');
                result.current.addNode('transform');
            });

            const sources = result.current.getNodesByType('source');

            expect(sources).toHaveLength(2);
            expect(sources.every((n) => n.type === 'source')).toBe(true);
        });

        it('should get upstream nodes', () => {
            const { result } = renderHook(() => useDataPipeline());

            let source: string, transform: string;
            act(() => {
                source = result.current.addNode('source');
                transform = result.current.addNode('transform');
                result.current.addConnection(source!, transform!);
            });

            const upstream = result.current.getUpstreamNodes(transform!);

            expect(upstream).toHaveLength(1);
            expect(upstream[0].id).toBe(source);
        });

        it('should get downstream nodes', () => {
            const { result } = renderHook(() => useDataPipeline());

            let source: string, transform: string;
            act(() => {
                source = result.current.addNode('source');
                transform = result.current.addNode('transform');
                result.current.addConnection(source!, transform!);
            });

            const downstream = result.current.getDownstreamNodes(source!);

            expect(downstream).toHaveLength(1);
            expect(downstream[0].id).toBe(transform);
        });
    });

    describe('Pipeline Configuration', () => {
        it('should update pipeline name', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                result.current.setPipelineName('new_pipeline');
            });

            expect(result.current.pipelineName).toBe('new_pipeline');
        });

        it('should update schedule', () => {
            const { result } = renderHook(() => useDataPipeline());

            act(() => {
                result.current.setSchedule('0 0 * * 0');
            });

            expect(result.current.schedule).toBe('0 0 * * 0');
        });
    });
});
