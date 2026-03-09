/**
 * Class Diagram Canvas Content
 * 
 * UML class diagram visualization for Diagram × File level.
 * Displays class structure with properties, methods, and relationships.
 * 
 * @doc.type component
 * @doc.purpose Class diagram for OOP structure visualization
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Divider,
  Surface as Paper,
} from '@ghatana/ui';

interface ClassMember {
    name: string;
    type: string;
    visibility: 'public' | 'private' | 'protected';
}

interface ClassMethod {
    name: string;
    params: string;
    returnType: string;
    visibility: 'public' | 'private' | 'protected';
}

interface ClassEntity {
    id: string;
    name: string;
    type: 'class' | 'interface' | 'abstract';
    properties: ClassMember[];
    methods: ClassMethod[];
    extends?: string;
    implements?: string[];
    position: { x: number; y: number };
}

// Mock class data
const MOCK_CLASSES: ClassEntity[] = [
    {
        id: 'user',
        name: 'User',
        type: 'class',
        properties: [
            { name: 'id', type: 'string', visibility: 'private' },
            { name: 'email', type: 'string', visibility: 'private' },
            { name: 'name', type: 'string', visibility: 'public' },
            { name: 'createdAt', type: 'Date', visibility: 'private' },
        ],
        methods: [
            { name: 'constructor', params: 'email: string, name: string', returnType: 'void', visibility: 'public' },
            { name: 'getEmail', params: '', returnType: 'string', visibility: 'public' },
            { name: 'updateName', params: 'name: string', returnType: 'void', visibility: 'public' },
            { name: 'validate', params: '', returnType: 'boolean', visibility: 'private' },
        ],
        implements: ['IUser'],
        position: { x: 30, y: 40 },
    },
    {
        id: 'admin',
        name: 'Admin',
        type: 'class',
        properties: [
            { name: 'permissions', type: 'string[]', visibility: 'private' },
            { name: 'role', type: 'AdminRole', visibility: 'public' },
        ],
        methods: [
            { name: 'constructor', params: 'email: string, name: string, role: AdminRole', returnType: 'void', visibility: 'public' },
            { name: 'hasPermission', params: 'permission: string', returnType: 'boolean', visibility: 'public' },
            { name: 'grantPermission', params: 'permission: string', returnType: 'void', visibility: 'public' },
        ],
        extends: 'User',
        position: { x: 30, y: 10 },
    },
    {
        id: 'iuser',
        name: 'IUser',
        type: 'interface',
        properties: [
            { name: 'id', type: 'string', visibility: 'public' },
            { name: 'email', type: 'string', visibility: 'public' },
            { name: 'name', type: 'string', visibility: 'public' },
        ],
        methods: [
            { name: 'getEmail', params: '', returnType: 'string', visibility: 'public' },
            { name: 'updateName', params: 'name: string', returnType: 'void', visibility: 'public' },
        ],
        position: { x: 70, y: 40 },
    },
    {
        id: 'repository',
        name: 'UserRepository',
        type: 'abstract',
        properties: [
            { name: 'dataSource', type: 'DataSource', visibility: 'protected' },
        ],
        methods: [
            { name: 'find', params: 'id: string', returnType: 'Promise<User>', visibility: 'public' },
            { name: 'save', params: 'user: User', returnType: 'Promise<void>', visibility: 'public' },
            { name: 'delete', params: 'id: string', returnType: 'Promise<void>', visibility: 'public' },
            { name: 'connect', params: '', returnType: 'Promise<void>', visibility: 'protected' },
        ],
        position: { x: 70, y: 10 },
    },
];

const getVisibilitySymbol = (visibility: string) => {
    switch (visibility) {
        case 'public':
            return '+';
        case 'private':
            return '-';
        case 'protected':
            return '#';
        default:
            return '';
    }
};

const getClassColor = (type: ClassEntity['type']) => {
    switch (type) {
        case 'class':
            return { bg: '#E3F2FD', border: '#2196F3' };
        case 'interface':
            return { bg: '#F3E5F5', border: '#9C27B0' };
        case 'abstract':
            return { bg: '#FFF3E0', border: '#FF9800' };
    }
};

const ClassNode = ({
    entity,
    onClick,
    isSelected,
}: {
    entity: ClassEntity;
    onClick: (id: string) => void;
    isSelected: boolean;
}) => {
    const colors = getClassColor(entity.type);

    return (
        <Paper
            elevation={isSelected ? 8 : 3}
            onClick={() => onClick(entity.id)}
            className="absolute" style={{ left: `${entity.position.x, color: 'colors.border', color: 'colors.border' }}
        >
            {/* Class header */}
            <Box
                className="p-3" style={{ backgroundColor: colors.bg, borderBottom: `2px solid ${colors.border }}
            >
                {entity.type !== 'class' && (
                    <Typography
                        variant="caption"
                        ccolor: colors.border */
                    >
                        «{entity.type}»
                    </Typography>
                )}
                <Typography
                    variant="subtitle2"
                    className="font-bold font-mono" >
                    {entity.name}
                </Typography>
                {entity.extends && (
                    <Typography variant="caption" color="text.secondary" className="text-[0.7rem]">
                        extends {entity.extends}
                    </Typography>
                )}
                {entity.implements && entity.implements.length > 0 && (
                    <Typography variant="caption" color="text.secondary" className="text-[0.7rem]">
                        implements {entity.implements.join(', ')}
                    </Typography>
                )}
            </Box>

            {/* Properties section */}
            <Box className="overflow-auto p-2 min-h-[60px] max-h-[120px]">
                {entity.properties.length > 0 ? (
                    entity.properties.map((prop, i) => (
                        <Typography
                            key={i}
                            variant="caption"
                            className="font-mono text-xs block" style={{ color: prop.visibility === 'private' ? 'text.secondary' : 'text.primary' }}
                        >
                            {getVisibilitySymbol(prop.visibility)} {prop.name}: {prop.type}
                        </Typography>
                    ))
                ) : (
                    <Typography variant="caption" color="text.disabled" className="italic">
                        No properties
                    </Typography>
                )}
            </Box>

            <Divider />

            {/* Methods section */}
            <Box className="overflow-auto p-2 min-h-[60px] max-h-[150px]">
                {entity.methods.length > 0 ? (
                    entity.methods.map((method, i) => (
                        <Typography
                            key={i}
                            variant="caption"
                            className="font-mono text-xs block" style={{ color: method.visibility === 'private' ? 'text.secondary' : 'text.primary' }}
                        >
                            {getVisibilitySymbol(method.visibility)} {method.name}({method.params}): {method.returnType}
                        </Typography>
                    ))
                ) : (
                    <Typography variant="caption" color="text.disabled" className="italic">
                        No methods
                    </Typography>
                )}
            </Box>
        </Paper>
    );
};

const RelationshipLine = ({
    from,
    to,
    type,
}: {
    from: ClassEntity;
    to: ClassEntity;
    type: 'extends' | 'implements';
}) => {
    const x1 = from.position.x;
    const y1 = from.position.y;
    const x2 = to.position.x;
    const y2 = to.position.y;

    const markerType = type === 'extends' ? 'triangle' : 'diamond';

    return (
        <svg
            style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                pointerEvents: 'none',
                zIndex: 0,
            }}
        >
            <defs>
                <marker
                    id={`${markerType}-${from.id}-${to.id}`}
                    markerWidth="12"
                    markerHeight="12"
                    refX="10"
                    refY="6"
                    orient="auto"
                >
                    {type === 'extends' ? (
                        <polygon points="0 0, 12 6, 0 12" fill="white" stroke="#666" strokeWidth="1.5" />
                    ) : (
                        <polygon points="6 0, 12 6, 6 12, 0 6" fill="white" stroke="#666" strokeWidth="1.5" />
                    )}
                </marker>
            </defs>
            <line
                x1={`${x1}%`}
                y1={`${y1}%`}
                x2={`${x2}%`}
                y2={`${y2}%`}
                stroke="#666"
                strokeWidth="2"
                strokeDasharray={type === 'implements' ? '5,5' : 'none'}
                markerEnd={`url(#${markerType}-${from.id}-${to.id})`}
            />
        </svg>
    );
};

export const ClassDiagramCanvas = () => {
    const [classes] = useState<ClassEntity[]>(MOCK_CLASSES);
    const [selectedClass, setSelectedClass] = useState<string | null>(null);

    const hasContent = classes.length > 0;

    const handleClassClick = (id: string) => {
        setSelectedClass(id === selectedClass ? null : id);
    };

    // Build relationships
    const relationships: Array<{ from: ClassEntity; to: ClassEntity; type: 'extends' | 'implements' }> = [];
    classes.forEach(cls => {
        if (cls.extends) {
            const parent = classes.find(c => c.name === cls.extends);
            if (parent) {
                relationships.push({ from: cls, to: parent, type: 'extends' });
            }
        }
        if (cls.implements) {
            cls.implements.forEach(interfaceName => {
                const iface = classes.find(c => c.name === interfaceName);
                if (iface) {
                    relationships.push({ from: cls, to: iface, type: 'implements' });
                }
            });
        }
    });

    const selectedEntity = classes.find(c => c.id === selectedClass);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Generate from Code',
                    onClick: () => {
                        console.log('Generate class diagram');
                    },
                },
                secondaryAction: {
                    label: 'Create Class',
                    onClick: () => {
                        console.log('Create class');
                    },
                },
            }}
        >
            <Box
                className="relative h-full w-full bg-[#fafafa]" style={{ backgroundImage: '`
            radial-gradient(circle, backgroundSize: '15px 15px' }} >
                {/* Relationship lines */}
                {relationships.map((rel, i) => (
                    <RelationshipLine key={i} from={rel.from} to={rel.to} type={rel.type} />
                ))}

                {/* Class nodes */}
                {classes.map(cls => (
                    <ClassNode
                        key={cls.id}
                        entity={cls}
                        onClick={handleClassClick}
                        isSelected={cls.id === selectedClass}
                    />
                ))}

                {/* Legend */}
                <Box
                    className="absolute rounded bottom-[16px] left-[16px] bg-white p-4 shadow min-w-[200px]"
                >
                    <Typography variant="subtitle2" gutterBottom>
                        UML Legend
                    </Typography>
                    <Typography variant="caption" display="block" className="font-mono">
                        + Public
                    </Typography>
                    <Typography variant="caption" display="block" className="font-mono">
                        - Private
                    </Typography>
                    <Typography variant="caption" display="block" className="font-mono">
                        # Protected
                    </Typography>
                    <Box className="mt-2">
                        <Typography variant="caption" display="block">
                            ━━━▷ Inheritance
                        </Typography>
                        <Typography variant="caption" display="block">
                            ┈┈┈◇ Implementation
                        </Typography>
                    </Box>
                </Box>

                {/* Stats */}
                {selectedEntity && (
                    <Box
                        className="absolute rounded top-[16px] right-[16px] bg-white p-4 shadow min-w-[220px]"
                    >
                        <Typography variant="subtitle2" gutterBottom className="font-semibold">
                            {selectedEntity.name}
                        </Typography>
                        <Typography variant="caption" display="block" color="text.secondary">
                            Type: {selectedEntity.type}
                        </Typography>
                        <Typography variant="caption" display="block" color="text.secondary">
                            Properties: {selectedEntity.properties.length}
                        </Typography>
                        <Typography variant="caption" display="block" color="text.secondary">
                            Methods: {selectedEntity.methods.length}
                        </Typography>
                        {selectedEntity.extends && (
                            <Typography variant="caption" display="block" color="primary.main" className="mt-2">
                                Extends: {selectedEntity.extends}
                            </Typography>
                        )}
                        {selectedEntity.implements && selectedEntity.implements.length > 0 && (
                            <Typography variant="caption" display="block" color="secondary.main">
                                Implements: {selectedEntity.implements.join(', ')}
                            </Typography>
                        )}
                    </Box>
                )}
            </Box>
        </BaseCanvasContent>
    );
};

export default ClassDiagramCanvas;
