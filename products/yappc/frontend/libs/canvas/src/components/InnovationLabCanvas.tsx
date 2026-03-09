/**
 * @doc.type component
 * @doc.purpose Innovation Lab canvas for prototyping and experimentation (Journey 32.1)
 * @doc.layer product
 * @doc.pattern Innovation Canvas Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Typography, IconButton, Tooltip, Surface as Paper, Chip, Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions, Card, CardContent, CardActions } from '@ghatana/ui';
import { Lightbulb, FlaskConical as Science, TrendingUp, Plus as Add, ArrowRight as ArrowForward } from 'lucide-react';

/**
 * Prototype interface
 */
export interface Prototype {
    id: string;
    name: string;
    description: string;
    status: 'concept' | 'testing' | 'validated' | 'failed';
    abTestResults?: {
        variantA: number;
        variantB: number;
        winner?: 'A' | 'B';
    };
    roi?: {
        investment: number;
        expectedReturn: number;
        timeToReturn: number; // months
    };
}

/**
 * Props for InnovationLabCanvas
 */
export interface InnovationLabCanvasProps {
    prototypes?: Prototype[];
    onAddPrototype?: (prototype: Omit<Prototype, 'id'>) => void;
    onUpdatePrototype?: (id: string, updates: Partial<Prototype>) => void;
    onDeletePrototype?: (id: string) => void;
    onPromoteToRoadmap?: (id: string) => void;
    onRunABTest?: (id: string) => void;
}

/**
 * InnovationLabCanvas Component
 * 
 * Innovation lab for innovation leads with:
 * - Prototype nodes
 * - A/B test simulation
 * - ROI calculator
 * - "Promote to Roadmap" button
 */
export const InnovationLabCanvas: React.FC<InnovationLabCanvasProps> = ({
    prototypes = [],
    onAddPrototype,
    onUpdatePrototype,
    onDeletePrototype,
    onPromoteToRoadmap,
    onRunABTest,
}) => {
    const [showAddDialog, setShowAddDialog] = useState(false);
    const [newPrototype, setNewPrototype] = useState({
        name: '',
        description: '',
        status: 'concept' as const,
    });

    const handleAddPrototype = useCallback(() => {
        if (!newPrototype.name.trim()) return;

        onAddPrototype?.(newPrototype);
        setNewPrototype({
            name: '',
            description: '',
            status: 'concept',
        });
        setShowAddDialog(false);
    }, [newPrototype, onAddPrototype]);

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'concept':
                return 'default';
            case 'testing':
                return 'info';
            case 'validated':
                return 'success';
            case 'failed':
                return 'error';
            default:
                return 'default';
        }
    };

    const calculateROI = (proto: Prototype) => {
        if (!proto.roi) return null;
        const roi = ((proto.roi.expectedReturn - proto.roi.investment) / proto.roi.investment) * 100;
        return roi.toFixed(1);
    };

    const renderPrototype = (prototype: Prototype) => (
        <Card key={prototype.id} className="mb-4">
            <CardContent>
                <Box className="flex items-center gap-2 mb-2">
                    <Lightbulb />
                    <Typography as="h6">{prototype.name}</Typography>
                    <Chip label={prototype.status} color={getStatusColor(prototype.status)} size="sm" />
                </Box>

                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    {prototype.description}
                </Typography>

                {prototype.abTestResults && (
                    <Box className="mb-4 p-2 rounded bg-gray-100 dark:bg-gray-800">
                        <Typography as="span" className="text-xs text-gray-500" fontWeight="bold">
                            A/B Test Results
                        </Typography>
                        <Box className="flex gap-4 mt-1">
                            <Chip label={`Variant A: ${prototype.abTestResults.variantA}%`} size="sm" />
                            <Chip label={`Variant B: ${prototype.abTestResults.variantB}%`} size="sm" />
                            {prototype.abTestResults.winner && (
                                <Chip
                                    label={`Winner: ${prototype.abTestResults.winner}`}
                                    tone="success"
                                    size="sm"
                                />
                            )}
                        </Box>
                    </Box>
                )}

                {prototype.roi && (
                    <Box className="mb-4 p-2 rounded bg-gray-100 dark:bg-gray-800">
                        <Typography as="span" className="text-xs text-gray-500" fontWeight="bold">
                            ROI Calculator
                        </Typography>
                        <Box className="flex gap-4 mt-1 flex-wrap">
                            <Chip label={`Investment: $${prototype.roi.investment.toLocaleString()}`} size="sm" />
                            <Chip label={`Return: $${prototype.roi.expectedReturn.toLocaleString()}`} size="sm" />
                            <Chip label={`ROI: ${calculateROI(prototype)}%`} tone="success" size="sm" />
                            <Chip label={`${prototype.roi.timeToReturn} months`} size="sm" />
                        </Box>
                    </Box>
                )}
            </CardContent>

            <CardActions>
                <Button size="sm" startIcon={<Science />} onClick={() => onRunABTest?.(prototype.id)}>
                    Run A/B Test
                </Button>
                <Button size="sm" startIcon={<TrendingUp />}>
                    Calculate ROI
                </Button>
                {prototype.status === 'validated' && (
                    <Button
                        size="sm"
                        variant="solid"
                        startIcon={<ArrowForward />}
                        onClick={() => onPromoteToRoadmap?.(prototype.id)}
                    >
                        Promote to Roadmap
                    </Button>
                )}
                <Box className="flex-1" />
                <IconButton size="sm" onClick={() => onDeletePrototype?.(prototype.id)} className="text-red-600">
                    <Typography as="span" className="text-xs text-gray-500">×</Typography>
                </IconButton>
            </CardActions>
        </Card>
    );

    return (
        <Box className="h-full flex flex-col">
            <Paper className="p-4 mb-4">
                <Box className="flex items-center gap-4">
                    <Lightbulb className="text-blue-600 text-[32px]" />
                    <Typography as="h6">Innovation Lab</Typography>
                    <Button startIcon={<Add />} variant="solid" onClick={() => setShowAddDialog(true)}>
                        New Prototype
                    </Button>
                </Box>
            </Paper>

            <Box className="flex-1 overflow-y-auto p-4">
                {prototypes.length === 0 ? (
                    <Box className="text-center py-16">
                        <Typography as="h6" color="text.secondary">
                            No prototypes yet
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            Create prototypes to test new ideas and innovations
                        </Typography>
                    </Box>
                ) : (
                    prototypes.map(renderPrototype)
                )}
            </Box>

            <Dialog open={showAddDialog} onClose={() => setShowAddDialog(false)} size="sm" fullWidth>
                <DialogTitle>New Prototype</DialogTitle>
                <DialogContent>
                    <Box className="flex flex-col gap-4 mt-2">
                        <TextField
                            label="Prototype Name"
                            value={newPrototype.name}
                            onChange={(e) => setNewPrototype({ ...newPrototype, name: e.target.value })}
                            fullWidth
                            autoFocus
                        />
                        <TextField
                            label="Description"
                            value={newPrototype.description}
                            onChange={(e) => setNewPrototype({ ...newPrototype, description: e.target.value })}
                            fullWidth
                            multiline
                            rows={3}
                        />
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowAddDialog(false)}>Cancel</Button>
                    <Button onClick={handleAddPrototype} variant="solid" disabled={!newPrototype.name.trim()}>
                        Create
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
