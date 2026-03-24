/**
 * Tests for useServiceBlueprint hook
 */

import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useServiceBlueprint } from '../useServiceBlueprint';

describe('useServiceBlueprint', () => {
    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            expect(result.current.blueprintName).toBe('Service Blueprint');
            expect(result.current.serviceDescription).toBe('');
            expect(result.current.lanes).toHaveLength(4);
            expect(result.current.connections).toHaveLength(0);
            expect(result.current.getNodeCount()).toBe(0);
            expect(result.current.getConnectionCount()).toBe(0);
            expect(result.current.getTouchpointCount()).toBe(0);
        });

        it('should initialize with custom blueprint name', () => {
            const { result } = renderHook(() =>
                useServiceBlueprint({ initialBlueprintName: 'E-Commerce Checkout' })
            );

            expect(result.current.blueprintName).toBe('E-Commerce Checkout');
        });

        it('should initialize with service description', () => {
            const { result } = renderHook(() =>
                useServiceBlueprint({
                    initialServiceDescription: 'Complete checkout flow for online orders',
                })
            );

            expect(result.current.serviceDescription).toBe('Complete checkout flow for online orders');
        });

        it('should initialize with correct lane types in order', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            expect(result.current.lanes[0].type).toBe('customer');
            expect(result.current.lanes[1].type).toBe('frontstage');
            expect(result.current.lanes[2].type).toBe('backstage');
            expect(result.current.lanes[3].type).toBe('support');
        });

        it('should initialize with empty nodes in all lanes', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            result.current.lanes.forEach(lane => {
                expect(lane.nodes).toHaveLength(0);
            });
        });
    });

    describe('Configuration Management', () => {
        it('should update blueprint name', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.setBlueprintName('Hotel Check-in');
            });

            expect(result.current.blueprintName).toBe('Hotel Check-in');
        });

        it('should update service description', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.setServiceDescription('Guest check-in and room assignment process');
            });

            expect(result.current.serviceDescription).toBe('Guest check-in and room assignment process');
        });
    });

    describe('Node Management', () => {
        it('should add node to customer lane', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('customer', {
                    name: 'Browse Products',
                    description: 'Customer browses product catalog',
                });
            });

            const customerLane = result.current.lanes.find(l => l.type === 'customer');
            expect(customerLane?.nodes).toHaveLength(1);
            expect(customerLane?.nodes[0].name).toBe('Browse Products');
            expect(customerLane?.nodes[0].id).toBe(nodeId!);
        });

        it('should add node with duration', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('frontstage', {
                    name: 'Process Payment',
                    description: 'Accept and verify payment',
                    duration: '30 seconds',
                });
            });

            const frontstageLane = result.current.lanes.find(l => l.type === 'frontstage');
            expect(frontstageLane?.nodes[0].duration).toBe('30 seconds');
        });

        it('should add multiple nodes to same lane', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('customer', { name: 'Browse' });
                result.current.addNode('customer', { name: 'Select' });
                result.current.addNode('customer', { name: 'Checkout' });
            });

            const customerLane = result.current.lanes.find(l => l.type === 'customer');
            expect(customerLane?.nodes).toHaveLength(3);
        });

        it('should add nodes to different lanes', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('customer', { name: 'Customer Action' });
                result.current.addNode('frontstage', { name: 'Frontstage Action' });
                result.current.addNode('backstage', { name: 'Backstage Action' });
                result.current.addNode('support', { name: 'Support Action' });
            });

            expect(result.current.getNodeCount()).toBe(4);
            result.current.lanes.forEach(lane => {
                expect(lane.nodes).toHaveLength(1);
            });
        });

        it('should update node name', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('customer', { name: 'Original Name' });
            });

            act(() => {
                result.current.updateNode(nodeId!, { name: 'Updated Name' });
            });

            const node = result.current.getNode(nodeId!);
            expect(node?.name).toBe('Updated Name');
        });

        it('should update node description', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('customer', { name: 'Browse' });
            });

            act(() => {
                result.current.updateNode(nodeId!, { description: 'New description' });
            });

            const node = result.current.getNode(nodeId!);
            expect(node?.description).toBe('New description');
        });

        it('should update node duration', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('frontstage', { name: 'Process' });
            });

            act(() => {
                result.current.updateNode(nodeId!, { duration: '5 minutes' });
            });

            const node = result.current.getNode(nodeId!);
            expect(node?.duration).toBe('5 minutes');
        });

        it('should delete node', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('customer', { name: 'Browse' });
            });

            act(() => {
                result.current.deleteNode(nodeId!);
            });

            expect(result.current.getNodeCount()).toBe(0);
        });

        it('should delete node and its connections', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId1: string, nodeId2: string;
            act(() => {
                nodeId1 = result.current.addNode('customer', { name: 'Node 1' });
                nodeId2 = result.current.addNode('frontstage', { name: 'Node 2' });
                result.current.addConnection({ from: nodeId1, to: nodeId2, type: 'flow' });
            });

            act(() => {
                result.current.deleteNode(nodeId1!);
            });

            expect(result.current.getNodeCount()).toBe(1);
            expect(result.current.getConnectionCount()).toBe(0);
        });

        it('should get node by ID', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('customer', {
                    name: 'Browse Products',
                    description: 'Customer browses catalog',
                });
            });

            const node = result.current.getNode(nodeId!);
            expect(node).toBeDefined();
            expect(node?.name).toBe('Browse Products');
            expect(node?.description).toBe('Customer browses catalog');
        });

        it('should return undefined for non-existent node', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            const node = result.current.getNode('non-existent');
            expect(node).toBeUndefined();
        });
    });

    describe('Connection Management', () => {
        it('should add flow connection', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId1: string, nodeId2: string;
            act(() => {
                nodeId1 = result.current.addNode('customer', { name: 'Node 1' });
                nodeId2 = result.current.addNode('customer', { name: 'Node 2' });
                result.current.addConnection({ from: nodeId1, to: nodeId2, type: 'flow' });
            });

            expect(result.current.connections).toHaveLength(1);
            expect(result.current.connections[0].type).toBe('flow');
        });

        it('should add support connection', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId1: string, nodeId2: string;
            act(() => {
                nodeId1 = result.current.addNode('frontstage', { name: 'Node 1' });
                nodeId2 = result.current.addNode('backstage', { name: 'Node 2' });
                result.current.addConnection({ from: nodeId1, to: nodeId2, type: 'support' });
            });

            expect(result.current.connections).toHaveLength(1);
            expect(result.current.connections[0].type).toBe('support');
        });

        it('should add multiple connections', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let node1: string, node2: string, node3: string;
            act(() => {
                node1 = result.current.addNode('customer', { name: 'Node 1' });
                node2 = result.current.addNode('frontstage', { name: 'Node 2' });
                node3 = result.current.addNode('backstage', { name: 'Node 3' });
                result.current.addConnection({ from: node1, to: node2, type: 'flow' });
                result.current.addConnection({ from: node2, to: node3, type: 'flow' });
            });

            expect(result.current.connections).toHaveLength(2);
        });

        it('should delete connection', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId1: string, nodeId2: string;
            act(() => {
                nodeId1 = result.current.addNode('customer', { name: 'Node 1' });
                nodeId2 = result.current.addNode('frontstage', { name: 'Node 2' });
                result.current.addConnection({ from: nodeId1, to: nodeId2, type: 'flow' });
            });

            const connectionId = result.current.connections[0].id;

            act(() => {
                result.current.deleteConnection(connectionId);
            });

            expect(result.current.connections).toHaveLength(0);
        });
    });

    describe('Touchpoint Management', () => {
        it('should add touchpoint to node', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('frontstage', { name: 'Greet Customer' });
                result.current.addTouchpoint(nodeId, {
                    name: 'Front Desk',
                    channel: 'In-person',
                    metrics: '2 min avg wait',
                });
            });

            const node = result.current.getNode(nodeId!);
            expect(node?.touchpoints).toHaveLength(1);
            expect(node?.touchpoints?.[0].name).toBe('Front Desk');
        });

        it('should add touchpoint with channel', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('frontstage', { name: 'Send Confirmation' });
                result.current.addTouchpoint(nodeId, {
                    name: 'Email',
                    channel: 'Email',
                });
            });

            const node = result.current.getNode(nodeId!);
            expect(node?.touchpoints?.[0].channel).toBe('Email');
        });

        it('should add touchpoint with metrics', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('customer', { name: 'Submit Form' });
                result.current.addTouchpoint(nodeId, {
                    name: 'Web Form',
                    metrics: '95% completion rate',
                });
            });

            const node = result.current.getNode(nodeId!);
            expect(node?.touchpoints?.[0].metrics).toBe('95% completion rate');
        });

        it('should add multiple touchpoints to same node', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('frontstage', { name: 'Customer Support' });
                result.current.addTouchpoint(nodeId, { name: 'Phone' });
                result.current.addTouchpoint(nodeId, { name: 'Chat' });
                result.current.addTouchpoint(nodeId, { name: 'Email' });
            });

            const node = result.current.getNode(nodeId!);
            expect(node?.touchpoints).toHaveLength(3);
        });

        it('should delete touchpoint', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            let nodeId: string;
            act(() => {
                nodeId = result.current.addNode('frontstage', { name: 'Support' });
                result.current.addTouchpoint(nodeId, { name: 'Phone' });
            });

            const node = result.current.getNode(nodeId!);
            const touchpointId = node?.touchpoints?.[0].id;

            act(() => {
                result.current.deleteTouchpoint(nodeId!, touchpointId!);
            });

            const updatedNode = result.current.getNode(nodeId!);
            expect(updatedNode?.touchpoints).toHaveLength(0);
        });

        it('should increment touchpoint count', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                const node1 = result.current.addNode('customer', { name: 'Node 1' });
                const node2 = result.current.addNode('frontstage', { name: 'Node 2' });
                result.current.addTouchpoint(node1, { name: 'TP1' });
                result.current.addTouchpoint(node1, { name: 'TP2' });
                result.current.addTouchpoint(node2, { name: 'TP3' });
            });

            expect(result.current.getTouchpointCount()).toBe(3);
        });
    });

    describe('Validation', () => {
        it('should report empty lanes', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            const issues = result.current.validateBlueprint();

            expect(issues).toContain('Empty lanes: customer, frontstage, backstage, support');
        });

        it('should report customer nodes without touchpoints', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('customer', { name: 'Browse' });
            });

            const issues = result.current.validateBlueprint();

            expect(issues.some(i => i.includes('customer nodes without touchpoints'))).toBe(true);
        });

        it('should report frontstage nodes without touchpoints', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('frontstage', { name: 'Process' });
            });

            const issues = result.current.validateBlueprint();

            expect(issues.some(i => i.includes('frontstage nodes without touchpoints'))).toBe(true);
        });

        it('should not report backstage nodes without touchpoints', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('backstage', { name: 'Process Data' });
            });

            const issues = result.current.validateBlueprint();

            expect(issues.some(i => i.includes('backstage nodes without touchpoints'))).toBe(false);
        });

        it('should report orphaned nodes', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('customer', { name: 'Isolated Node' });
            });

            const issues = result.current.validateBlueprint();

            expect(issues.some(i => i.includes('Orphaned nodes'))).toBe(true);
        });

        it('should pass validation for complete blueprint', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                const node1 = result.current.addNode('customer', { name: 'Browse' });
                const node2 = result.current.addNode('frontstage', { name: 'Help' });
                result.current.addTouchpoint(node1, { name: 'Website' });
                result.current.addTouchpoint(node2, { name: 'Chat' });
                result.current.addConnection({ from: node1, to: node2, type: 'flow' });
            });

            const issues = result.current.validateBlueprint();

            expect(issues).toHaveLength(0);
        });
    });

    describe('Flow Analysis', () => {
        it('should identify orphaned nodes', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('customer', { name: 'Isolated' });
            });

            const analysis = result.current.analyzeFlow();

            expect(analysis.orphanedNodes).toHaveLength(1);
            expect(analysis.orphanedNodes[0].name).toBe('Isolated');
        });

        it('should identify nodes missing touchpoints', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('customer', { name: 'No Touchpoint' });
            });

            const analysis = result.current.analyzeFlow();

            expect(analysis.missingTouchpoints).toHaveLength(1);
        });

        it('should identify cross-lane connections', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                const node1 = result.current.addNode('customer', { name: 'Customer' });
                const node2 = result.current.addNode('backstage', { name: 'Backend' });
                result.current.addConnection({ from: node1, to: node2, type: 'support' });
            });

            const analysis = result.current.analyzeFlow();

            expect(analysis.crossLaneConnections).toHaveLength(1);
        });

        it('should not report same-lane connections as cross-lane', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                const node1 = result.current.addNode('customer', { name: 'Step 1' });
                const node2 = result.current.addNode('customer', { name: 'Step 2' });
                result.current.addConnection({ from: node1, to: node2, type: 'flow' });
            });

            const analysis = result.current.analyzeFlow();

            expect(analysis.crossLaneConnections).toHaveLength(0);
        });
    });

    describe('Export', () => {
        it('should export blueprint as JSON', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            const exported = result.current.exportBlueprint();
            const parsed = JSON.parse(exported);

            expect(parsed).toHaveProperty('name');
            expect(parsed).toHaveProperty('lanes');
            expect(parsed).toHaveProperty('connections');
            expect(parsed).toHaveProperty('metadata');
        });

        it('should include blueprint name in export', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.setBlueprintName('Test Blueprint');
            });

            const exported = result.current.exportBlueprint();
            const parsed = JSON.parse(exported);

            expect(parsed.name).toBe('Test Blueprint');
        });

        it('should include service description in export', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.setServiceDescription('Test description');
            });

            const exported = result.current.exportBlueprint();
            const parsed = JSON.parse(exported);

            expect(parsed.description).toBe('Test description');
        });

        it('should include nodes in export', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('customer', { name: 'Test Node' });
            });

            const exported = result.current.exportBlueprint();
            const parsed = JSON.parse(exported);

            const customerLane = parsed.lanes.find((l: unknown) => l.type === 'customer');
            expect(customerLane.nodes).toHaveLength(1);
            expect(customerLane.nodes[0].name).toBe('Test Node');
        });

        it('should include connections with node names in export', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                const node1 = result.current.addNode('customer', { name: 'Node A' });
                const node2 = result.current.addNode('frontstage', { name: 'Node B' });
                result.current.addConnection({ from: node1, to: node2, type: 'flow' });
            });

            const exported = result.current.exportBlueprint();
            const parsed = JSON.parse(exported);

            expect(parsed.connections).toHaveLength(1);
            expect(parsed.connections[0].from).toBe('Node A');
            expect(parsed.connections[0].to).toBe('Node B');
            expect(parsed.connections[0].type).toBe('flow');
        });

        it('should include metadata with counts in export', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                const node1 = result.current.addNode('customer', { name: 'Node 1' });
                const node2 = result.current.addNode('frontstage', { name: 'Node 2' });
                result.current.addTouchpoint(node1, { name: 'TP1' });
                result.current.addConnection({ from: node1, to: node2, type: 'flow' });
            });

            const exported = result.current.exportBlueprint();
            const parsed = JSON.parse(exported);

            expect(parsed.metadata.nodeCount).toBe(2);
            expect(parsed.metadata.connectionCount).toBe(1);
            expect(parsed.metadata.touchpointCount).toBe(1);
        });

        it('should include validation issues in export', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('customer', { name: 'Isolated' });
            });

            const exported = result.current.exportBlueprint();
            const parsed = JSON.parse(exported);

            expect(parsed.validation).toBeInstanceOf(Array);
            expect(parsed.validation.length).toBeGreaterThan(0);
        });
    });

    describe('Count Utilities', () => {
        it('should count nodes across all lanes', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.addNode('customer', { name: 'C1' });
                result.current.addNode('customer', { name: 'C2' });
                result.current.addNode('frontstage', { name: 'F1' });
            });

            expect(result.current.getNodeCount()).toBe(3);
        });

        it('should count connections', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                const n1 = result.current.addNode('customer', { name: 'N1' });
                const n2 = result.current.addNode('frontstage', { name: 'N2' });
                const n3 = result.current.addNode('backstage', { name: 'N3' });
                result.current.addConnection({ from: n1, to: n2, type: 'flow' });
                result.current.addConnection({ from: n2, to: n3, type: 'flow' });
            });

            expect(result.current.getConnectionCount()).toBe(2);
        });

        it('should count touchpoints across all nodes', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                const n1 = result.current.addNode('customer', { name: 'N1' });
                const n2 = result.current.addNode('frontstage', { name: 'N2' });
                result.current.addTouchpoint(n1, { name: 'TP1' });
                result.current.addTouchpoint(n1, { name: 'TP2' });
                result.current.addTouchpoint(n2, { name: 'TP3' });
            });

            expect(result.current.getTouchpointCount()).toBe(3);
        });
    });

    describe('Complex Scenarios', () => {
        it('should handle complete e-commerce checkout blueprint', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                // Customer journey
                const browse = result.current.addNode('customer', { name: 'Browse Products', duration: '5 min' });
                const cart = result.current.addNode('customer', { name: 'Add to Cart', duration: '30 sec' });
                const checkout = result.current.addNode('customer', { name: 'Checkout', duration: '2 min' });

                // Frontstage
                const showProducts = result.current.addNode('frontstage', { name: 'Show Products', duration: '1 sec' });
                const processPayment = result.current.addNode('frontstage', { name: 'Process Payment', duration: '10 sec' });

                // Backstage
                const inventoryCheck = result.current.addNode('backstage', { name: 'Check Inventory' });
                const orderProcessing = result.current.addNode('backstage', { name: 'Process Order' });

                // Support
                const database = result.current.addNode('support', { name: 'Product Database' });
                const paymentGateway = result.current.addNode('support', { name: 'Payment Gateway' });

                // Touchpoints
                result.current.addTouchpoint(browse, { name: 'Web', channel: 'Website', metrics: '95% satisfaction' });
                result.current.addTouchpoint(cart, { name: 'Mobile App', channel: 'Mobile' });
                result.current.addTouchpoint(checkout, { name: 'Checkout Form', channel: 'Web', metrics: '85% conversion' });
                result.current.addTouchpoint(showProducts, { name: 'Product Page', channel: 'Web' });
                result.current.addTouchpoint(processPayment, { name: 'Payment Form', channel: 'Web', metrics: '99% success' });

                // Connections
                result.current.addConnection({ from: browse, to: showProducts, type: 'flow' });
                result.current.addConnection({ from: cart, to: inventoryCheck, type: 'flow' });
                result.current.addConnection({ from: checkout, to: processPayment, type: 'flow' });
                result.current.addConnection({ from: processPayment, to: orderProcessing, type: 'flow' });
                result.current.addConnection({ from: showProducts, to: database, type: 'support' });
                result.current.addConnection({ from: processPayment, to: paymentGateway, type: 'support' });
            });

            expect(result.current.getNodeCount()).toBe(8);
            expect(result.current.getConnectionCount()).toBe(6);
            expect(result.current.getTouchpointCount()).toBe(5);
        });

        it('should handle blueprint with validation and export', () => {
            const { result } = renderHook(() => useServiceBlueprint());

            act(() => {
                result.current.setBlueprintName('Complete Service');
                const node1 = result.current.addNode('customer', { name: 'Start' });
                const node2 = result.current.addNode('frontstage', { name: 'Process' });
                result.current.addTouchpoint(node1, { name: 'Entry Point' });
                result.current.addTouchpoint(node2, { name: 'Service Desk' });
                result.current.addConnection({ from: node1, to: node2, type: 'flow' });
            });

            const issues = result.current.validateBlueprint();
            expect(issues).toHaveLength(0);

            const exported = result.current.exportBlueprint();
            const parsed = JSON.parse(exported);
            expect(parsed.name).toBe('Complete Service');
            expect(parsed.validation).toHaveLength(0);
        });
    });
});
