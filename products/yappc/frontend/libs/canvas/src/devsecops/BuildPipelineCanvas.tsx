/**
 * @doc.type component
 * @doc.purpose Build Pipeline Optimization canvas (Journey 30.1)
 * @doc.layer product
 * @doc.pattern DevSecOps Canvas Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Typography, IconButton, Tooltip, Surface as Paper, Chip, Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions, Select, MenuItem, FormControl, InputLabel } from '@ghatana/ui';
import { Hammer as Build, Play as PlayArrow, HardDrive as Storage, Gauge as Speed, Plus as Add } from 'lucide-react';

/**
 * Build step interface
 */
export interface BuildStep {
    id: string;
    name: string;
    duration: number; // in seconds
    parallel: boolean;
    cacheEnabled: boolean;
    cacheStrategy?: 'npm' | 'maven' | 'gradle' | 'docker' | 'custom';
}

/**
 * Props for BuildPipelineCanvas
 */
export interface BuildPipelineCanvasProps {
    steps?: BuildStep[];
    onAddStep?: (step: Omit<BuildStep, 'id'>) => void;
    onUpdateStep?: (id: string, updates: Partial<BuildStep>) => void;
    onDeleteStep?: (id: string) => void;
    totalDuration?: number;
    parallelizable?: boolean;
}

/**
 * BuildPipelineCanvas Component
 * 
 * Build optimization for build engineers with:
 * - Build step nodes with duration
 * - Parallelization visualizer
 * - Cache strategy nodes
 */
export const BuildPipelineCanvas: React.FC<BuildPipelineCanvasProps> = ({
    steps = [],
    onAddStep,
    onUpdateStep,
    onDeleteStep,
    totalDuration = 0,
    parallelizable = false,
}) => {
    const [showAddDialog, setShowAddDialog] = useState(false);
    const [newStep, setNewStep] = useState({
        name: '',
        duration: 60,
        parallel: false,
        cacheEnabled: false,
        cacheStrategy: 'npm' as const,
    });

    const handleAddStep = useCallback(() => {
        if (!newStep.name.trim()) return;

        onAddStep?.(newStep);
        setNewStep({
            name: '',
            duration: 60,
            parallel: false,
            cacheEnabled: false,
            cacheStrategy: 'npm',
        });
        setShowAddDialog(false);
    }, [newStep, onAddStep]);

    const formatDuration = (seconds: number) => {
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return mins > 0 ? `${mins}m ${secs}s` : `${secs}s`;
    };

    const renderStep = (step: BuildStep) => (
        <Paper
            key={step.id}
            className="p-4 mb-4" style={{ borderLeft: `4px solid ${step.parallel ? '#10b981' : '#3b82f6' }}
        >
            <Box className="flex items-start gap-2">
                <Build className="mt-1" />
                <Box className="flex-1">
                    <Typography as="p" className="text-sm font-medium" fontWeight="bold">
                        {step.name}
                    </Typography>
                    <Box className="mt-2 flex gap-1 flex-wrap">
                        <Chip icon={<Speed />} label={formatDuration(step.duration)} size="sm" />
                        {step.parallel && <Chip label="Parallel" tone="success" size="sm" />}
                        {step.cacheEnabled && (
                            <Chip icon={<Storage />} label={step.cacheStrategy} tone="primary" size="sm" />
                        )}
                    </Box>
                </Box>
                <IconButton size="sm" onClick={() => onDeleteStep?.(step.id)} className="text-red-600">
                    <Typography as="span" className="text-xs text-gray-500">×</Typography>
                </IconButton>
            </Box>
        </Paper>
    );

    return (
        <Box className="h-full flex flex-col">
            <Paper className="p-4 mb-4">
                <Box className="flex items-center gap-4 flex-wrap">
                    <Typography as="h6">Build Pipeline Optimization</Typography>

                    <Button startIcon={<Add />} variant="solid" onClick={() => setShowAddDialog(true)}>
                        Add Step
                    </Button>

                    <Box className="flex-1" />

                    <Chip icon={<Speed />} label={`Total: ${formatDuration(totalDuration)}`} />
                    {parallelizable && <Chip icon={<PlayArrow />} label="Parallelizable" tone="success" />}
                </Box>
            </Paper>

            <Box className="flex-1 overflow-y-auto p-4">
                {steps.length === 0 ? (
                    <Box className="text-center py-16">
                        <Typography as="h6" color="text.secondary">
                            No build steps yet
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            Add build steps to optimize your pipeline
                        </Typography>
                    </Box>
                ) : (
                    steps.map(renderStep)
                )}
            </Box>

            <Dialog open={showAddDialog} onClose={() => setShowAddDialog(false)} size="sm" fullWidth>
                <DialogTitle>Add Build Step</DialogTitle>
                <DialogContent>
                    <Box className="flex flex-col gap-4 mt-2">
                        <TextField
                            label="Step Name"
                            value={newStep.name}
                            onChange={(e) => setNewStep({ ...newStep, name: e.target.value })}
                            fullWidth
                            autoFocus
                        />

                        <TextField
                            label="Duration (seconds)"
                            type="number"
                            value={newStep.duration}
                            onChange={(e) => setNewStep({ ...newStep, duration: parseInt(e.target.value) || 0 })}
                            fullWidth
                        />

                        <FormControl fullWidth>
                            <InputLabel>Cache Strategy</InputLabel>
                            <Select
                                value={newStep.cacheStrategy}
                                onChange={(e) =>
                                    setNewStep({
                                        ...newStep,
                                        cacheStrategy: e.target.value as unknown,
                                        cacheEnabled: true,
                                    })
                                }
                                label="Cache Strategy"
                            >
                                <MenuItem value="npm">NPM Cache</MenuItem>
                                <MenuItem value="maven">Maven Cache</MenuItem>
                                <MenuItem value="gradle">Gradle Cache</MenuItem>
                                <MenuItem value="docker">Docker Layer Cache</MenuItem>
                                <MenuItem value="custom">Custom Cache</MenuItem>
                            </Select>
                        </FormControl>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowAddDialog(false)}>Cancel</Button>
                    <Button onClick={handleAddStep} variant="solid" disabled={!newStep.name.trim()}>
                        Add
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
