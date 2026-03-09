/**
 * @doc.type component
 * @doc.purpose Domain Model Review canvas for business SMEs (Journey 23.1)
 * @doc.layer product
 * @doc.pattern Specialized Canvas Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Typography, IconButton, Tooltip, Surface as Paper, Chip, Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions, InteractiveList as List, ListItem } from '@ghatana/ui';
import { Tag as Category, Rule, MessageSquare as Comment, Plus as Add } from 'lucide-react';

/**
 * Domain model node interface
 */
export interface DomainEntity {
    id: string;
    name: string;
    attributes: EntityAttribute[];
    annotations: string[];
    businessRules: string[];
}

export interface EntityAttribute {
    name: string;
    type: string;
    required: boolean;
}

/**
 * Props for DomainModelCanvas
 */
export interface DomainModelCanvasProps {
    entities?: DomainEntity[];
    onAddEntity?: (entity: Omit<DomainEntity, 'id'>) => void;
    onUpdateEntity?: (id: string, updates: Partial<DomainEntity>) => void;
    onDeleteEntity?: (id: string) => void;
    onAddAnnotation?: (entityId: string, annotation: string) => void;
}

/**
 * DomainModelCanvas Component
 * 
 * Domain model review for business SMEs with:
 * - Entity nodes with attributes
 * - Annotation for missing fields
 * - Business rule nodes
 */
export const DomainModelCanvas: React.FC<DomainModelCanvasProps> = ({
    entities = [],
    onAddEntity,
    onUpdateEntity,
    onDeleteEntity,
    onAddAnnotation,
}) => {
    const [showAddDialog, setShowAddDialog] = useState(false);
    const [newEntity, setNewEntity] = useState({
        name: '',
        attributes: [] as EntityAttribute[],
        annotations: [] as string[],
        businessRules: [] as string[],
    });

    const handleAddEntity = useCallback(() => {
        if (!newEntity.name.trim()) return;

        onAddEntity?.(newEntity);
        setNewEntity({
            name: '',
            attributes: [],
            annotations: [],
            businessRules: [],
        });
        setShowAddDialog(false);
    }, [newEntity, onAddEntity]);

    const renderEntity = (entity: DomainEntity) => (
        <Paper key={entity.id} className="p-4 mb-4" style={{ borderLeft: '4px solid #8b5cf6' }} >
            <Box className="flex items-start gap-2">
                <Category className="mt-1" />
                <Box className="flex-1">
                    <Typography as="h6">{entity.name}</Typography>

                    {entity.attributes.length > 0 && (
                        <Box className="mt-2">
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Attributes:</Typography>
                            <List dense>
                                {entity.attributes.map((attr, idx) => (
                                    <ListItem key={idx} className="py-0">
                                        <Typography as="p" className="text-sm">
                                            {attr.name}: {attr.type} {attr.required && <Chip label="required" size="sm" />}
                                        </Typography>
                                    </ListItem>
                                ))}
                            </List>
                        </Box>
                    )}

                    {entity.businessRules.length > 0 && (
                        <Box className="mt-2">
                            <Chip icon={<Rule />} label={`${entity.businessRules.length} rules`} size="sm" />
                        </Box>
                    )}

                    {entity.annotations.length > 0 && (
                        <Box className="mt-2">
                            {entity.annotations.map((note, idx) => (
                                <Chip key={idx} icon={<Comment />} label={note} size="sm" className="mr-1 mb-1" />
                            ))}
                        </Box>
                    )}
                </Box>
                <IconButton size="sm" onClick={() => onDeleteEntity?.(entity.id)} className="text-red-600">
                    <Typography as="span" className="text-xs text-gray-500">×</Typography>
                </IconButton>
            </Box>
        </Paper>
    );

    return (
        <Box className="h-full flex flex-col">
            <Paper className="p-4 mb-4">
                <Box className="flex items-center gap-4">
                    <Typography as="h6">Domain Model Review</Typography>
                    <Button startIcon={<Add />} variant="solid" onClick={() => setShowAddDialog(true)}>
                        Add Entity
                    </Button>
                </Box>
            </Paper>

            <Box className="flex-1 overflow-y-auto p-4">
                {entities.length === 0 ? (
                    <Box className="text-center py-16">
                        <Typography as="h6" color="text.secondary">No entities yet</Typography>
                        <Typography as="p" className="text-sm" color="text.secondary">Add entities to review your domain model</Typography>
                    </Box>
                ) : (
                    entities.map(renderEntity)
                )}
            </Box>

            <Dialog open={showAddDialog} onClose={() => setShowAddDialog(false)} size="sm" fullWidth>
                <DialogTitle>Add Entity</DialogTitle>
                <DialogContent>
                    <Box className="flex flex-col gap-4 mt-2">
                        <TextField
                            label="Entity Name"
                            value={newEntity.name}
                            onChange={(e) => setNewEntity({ ...newEntity, name: e.target.value })}
                            fullWidth
                            autoFocus
                        />
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowAddDialog(false)}>Cancel</Button>
                    <Button onClick={handleAddEntity} variant="solid" disabled={!newEntity.name.trim()}>
                        Add
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
