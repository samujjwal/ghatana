#!/usr/bin/env node
"use strict";
/**
 * Canvas Seed Script - Generate demo canvas data
 */
var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (const p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.seedScenarios = void 0;
exports.generateSeedData = generateSeedData;
function generateSeedData(options) {
    if (options === void 0) { options = {}; }
    const _a = options.nodeCount, nodeCount = _a === void 0 ? 20 : _a, _b = options.connectionDensity, connectionDensity = _b === void 0 ? 0.3 : _b, _c = options.includeShapes, includeShapes = _c === void 0 ? true : _c, _d = options.includeStrokes, includeStrokes = _d === void 0 ? true : _d, _e = options.canvasSize, canvasSize = _e === void 0 ? { width: 1200, height: 800 } : _e;
    const elements = [];
    const connections = [];
    // Generate nodes
    const nodeTypes = ['api', 'data', 'component', 'flow'];
    const nodeLabels = [
        'User Service', 'Payment Gateway', 'Database', 'Cache Layer',
        'Load Balancer', 'API Gateway', 'Auth Service', 'Notification Service',
        'Analytics Engine', 'Search Index', 'File Storage', 'CDN',
        'Message Queue', 'Worker Pool', 'Monitoring', 'Logging Service',
        'Config Service', 'Backup System', 'Security Scanner', 'Health Check',
    ];
    for (var i = 0; i < nodeCount; i++) {
        const nodeType = nodeTypes[Math.floor(Math.random() * nodeTypes.length)];
        const label = nodeLabels[i % nodeLabels.length];
        var element = {
            id: "node-".concat(i + 1),
            kind: 'node',
            type: nodeType,
            position: {
                x: Math.random() * (canvasSize.width - 200) + 100,
                y: Math.random() * (canvasSize.height - 200) + 100,
            },
            size: {
                width: 150 + Math.random() * 50,
                height: 80 + Math.random() * 20,
            },
            data: {
                label,
                description: "".concat(label, " component"),
                version: "v".concat(Math.floor(Math.random() * 3) + 1, ".").concat(Math.floor(Math.random() * 10)),
            },
            style: {
                backgroundColor: getNodeColor(nodeType),
                borderColor: '#cccccc',
                color: '#333333',
            },
        };
        elements.push(element);
    }
    // Generate connections
    const nodeIds = elements.map((el) => { return el.id; });
    const targetConnections = Math.floor(nodeCount * connectionDensity);
    const _loop_1 = function (i) {
        const sourceId = nodeIds[Math.floor(Math.random() * nodeIds.length)];
        const targetId = nodeIds[Math.floor(Math.random() * nodeIds.length)];
        // Avoid self-connections and duplicates
        if (sourceId === targetId)
            return "continue";
        if (connections.some((c) => { return c.source === sourceId && c.target === targetId; }))
            return "continue";
        const connection = {
            id: "connection-".concat(i + 1),
            source: sourceId,
            target: targetId,
            type: 'default',
            data: {
                label: getConnectionLabel(),
            },
            style: {
                stroke: '#666666',
                strokeWidth: 2,
            },
        };
        connections.push(connection);
    };
    for (var i = 0; i < targetConnections; i++) {
        _loop_1(i);
    }
    // Generate shapes if requested
    if (includeShapes) {
        const shapeTypes = ['rectangle', 'ellipse'];
        const shapeCount = Math.floor(nodeCount * 0.3);
        for (var i = 0; i < shapeCount; i++) {
            const shapeType = shapeTypes[Math.floor(Math.random() * shapeTypes.length)];
            var element = {
                id: "shape-".concat(i + 1),
                kind: 'shape',
                type: 'shape',
                position: {
                    x: Math.random() * (canvasSize.width - 150) + 50,
                    y: Math.random() * (canvasSize.height - 150) + 50,
                },
                size: {
                    width: 100 + Math.random() * 100,
                    height: 60 + Math.random() * 60,
                },
                data: {
                    shapeType,
                },
                style: {
                    fill: getShapeColor(),
                    stroke: '#999999',
                    strokeWidth: 2,
                },
            };
            elements.push(element);
        }
    }
    // Generate strokes if requested
    if (includeStrokes) {
        const strokeCount = Math.floor(nodeCount * 0.2);
        for (var i = 0; i < strokeCount; i++) {
            const startX = Math.random() * canvasSize.width;
            const startY = Math.random() * canvasSize.height;
            const points = [{ x: startX, y: startY }];
            // Generate smooth curve
            const pointCount = 5 + Math.floor(Math.random() * 10);
            for (let j = 1; j < pointCount; j++) {
                const prevPoint = points[j - 1];
                points.push({
                    x: prevPoint.x + (Math.random() - 0.5) * 100,
                    y: prevPoint.y + (Math.random() - 0.5) * 100,
                });
            }
            var element = {
                id: "stroke-".concat(i + 1),
                kind: 'stroke',
                type: 'stroke',
                position: { x: 0, y: 0 },
                data: {
                    points,
                    tool: 'pen',
                },
                style: {
                    stroke: getStrokeColor(),
                    strokeWidth: 2 + Math.random() * 3,
                },
            };
            elements.push(element);
        }
    }
    return {
        elements,
        connections,
        viewport: {
            x: 0,
            y: 0,
            zoom: 1,
        },
        metadata: {
            title: 'Demo Canvas',
            description: 'Generated demo canvas with sample architecture',
            createdAt: new Date().toISOString(),
            version: '1.0.0',
        },
    };
}
function getNodeColor(type) {
    const colors = {
        api: '#e3f2fd',
        data: '#f3e5f5',
        component: '#e8f5e8',
        flow: '#fff3e0',
    };
    return colors[type] || '#f5f5f5';
}
function getShapeColor() {
    const colors = [
        'rgba(255, 235, 59, 0.3)',
        'rgba(255, 193, 7, 0.3)',
        'rgba(255, 152, 0, 0.3)',
        'rgba(255, 87, 34, 0.3)',
        'rgba(244, 67, 54, 0.3)',
    ];
    return colors[Math.floor(Math.random() * colors.length)];
}
function getStrokeColor() {
    const colors = [
        '#2196f3',
        '#4caf50',
        '#ff9800',
        '#f44336',
        '#9c27b0',
        '#607d8b',
    ];
    return colors[Math.floor(Math.random() * colors.length)];
}
function getConnectionLabel() {
    const labels = [
        'HTTP',
        'gRPC',
        'WebSocket',
        'Database',
        'Cache',
        'Queue',
        'Event',
        'API Call',
    ];
    return labels[Math.floor(Math.random() * labels.length)];
}
// Predefined seed scenarios
exports.seedScenarios = {
    small () { return generateSeedData({
        nodeCount: 5,
        connectionDensity: 0.4,
        includeShapes: false,
        includeStrokes: false,
    }); },
    medium () { return generateSeedData({
        nodeCount: 15,
        connectionDensity: 0.3,
        includeShapes: true,
        includeStrokes: true,
    }); },
    large () { return generateSeedData({
        nodeCount: 50,
        connectionDensity: 0.2,
        includeShapes: true,
        includeStrokes: true,
    }); },
    performance () { return generateSeedData({
        nodeCount: 100,
        connectionDensity: 0.15,
        includeShapes: true,
        includeStrokes: true,
        canvasSize: { width: 2000, height: 1500 },
    }); },
    microservices () {
        const data = generateSeedData({
            nodeCount: 25,
            connectionDensity: 0.25,
            includeShapes: false,
            includeStrokes: false,
        });
        // Override with microservices-specific data
        const services = [
            'API Gateway', 'User Service', 'Auth Service', 'Payment Service',
            'Order Service', 'Inventory Service', 'Notification Service',
            'Analytics Service', 'Search Service', 'Recommendation Engine',
            'File Storage', 'CDN', 'Load Balancer', 'Database Cluster',
            'Redis Cache', 'Message Queue', 'Event Bus', 'Monitoring',
            'Logging', 'Config Service', 'Service Mesh', 'Circuit Breaker',
            'Rate Limiter', 'Health Check', 'Backup Service',
        ];
        data.elements.forEach((element, index) => {
            if (element.kind === 'node' && services[index]) {
                element.data = __assign(__assign({}, element.data), { label: services[index], description: "".concat(services[index], " microservice") });
            }
        });
        data.metadata = __assign(__assign({}, data.metadata), { title: 'Microservices Architecture', description: 'Sample microservices architecture diagram' });
        return data;
    },
};
// CLI interface
if (require.main === module) {
    const scenario = process.argv[2] || 'medium';
    if (!(scenario in exports.seedScenarios)) {
        console.error("Unknown scenario: ".concat(scenario));
        console.error("Available scenarios: ".concat(Object.keys(exports.seedScenarios).join(', ')));
        process.exit(1);
    }
    const data = exports.seedScenarios[scenario]();
    console.log(JSON.stringify(data, null, 2));
}
exports.default = generateSeedData;
