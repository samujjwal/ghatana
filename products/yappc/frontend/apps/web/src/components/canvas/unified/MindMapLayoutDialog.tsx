/**
 * MindMapLayoutDialog - Mind Map Auto-Layout UI
 * 
 * Provides tree, radial, and fishbone layout algorithms
 * 
 * @doc.type component
 * @doc.purpose Mind map layout selection
 * @doc.layer components
 * @doc.pattern Component
 */

import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Stack,
  Box,
  Typography,
  RadioGroup,
  FormControlLabel,
  Radio,
  Alert,
} from '@ghatana/ui';
import { GitBranch as TreeIcon, ScatterChart as RadialIcon, GitFork as FishboneIcon } from 'lucide-react';

interface MindMapLayoutDialogProps {
    open: boolean;
    onClose: () => void;
    onApplyLayout: (layoutType: 'tree' | 'radial' | 'fishbone') => void;
}

export function MindMapLayoutDialog({
    open,
    onClose,
    onApplyLayout
}: MindMapLayoutDialogProps) {
    const [selectedLayout, setSelectedLayout] = useState<'tree' | 'radial' | 'fishbone'>('tree');

    const handleApply = () => {
        onApplyLayout(selectedLayout);
        onClose();
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
            <DialogTitle>Apply Mind Map Layout</DialogTitle>
            <DialogContent>
                <Stack spacing={3} className="pt-4">
                    <Alert severity="info">
                        This will rearrange all mind map nodes according to the selected algorithm.
                    </Alert>

                    <RadioGroup
                        value={selectedLayout}
                        onChange={(e) => setSelectedLayout(e.target.value as unknown)}
                    >
                        <Stack spacing={2}>
                            {/* Tree Layout */}
                            <Box
                                className="p-4 border border-solid rounded cursor-pointer hover:bg-gray-100 hover:dark:bg-gray-800" style={{ borderColor: selectedLayout === 'tree' ? 'primary.main' : 'divider' }}
                                onClick={() => setSelectedLayout('tree')}
                            >
                                <FormControlLabel
                                    value="tree"
                                    control={<Radio />}
                                    label={
                                        <Stack direction="row" spacing={2} alignItems="center">
                                            <TreeIcon />
                                            <Box>
                                                <Typography variant="body1" fontWeight={500}>
                                                    Tree Layout
                                                </Typography>
                                                <Typography variant="caption" color="text.secondary">
                                                    Hierarchical tree structure (horizontal or vertical)
                                                </Typography>
                                            </Box>
                                        </Stack>
                                    }
                                    className="m-0 w-full"
                                />
                            </Box>

                            {/* Radial Layout */}
                            <Box
                                className="p-4 border border-solid rounded cursor-pointer hover:bg-gray-100 hover:dark:bg-gray-800" style={{ borderColor: selectedLayout === 'radial' ? 'primary.main' : 'divider' }}
                                onClick={() => setSelectedLayout('radial')}
                            >
                                <FormControlLabel
                                    value="radial"
                                    control={<Radio />}
                                    label={
                                        <Stack direction="row" spacing={2} alignItems="center">
                                            <RadialIcon />
                                            <Box>
                                                <Typography variant="body1" fontWeight={500}>
                                                    Radial Layout
                                                </Typography>
                                                <Typography variant="caption" color="text.secondary">
                                                    Circular arrangement radiating from center
                                                </Typography>
                                            </Box>
                                        </Stack>
                                    }
                                    className="m-0 w-full"
                                />
                            </Box>

                            {/* Fishbone Layout */}
                            <Box
                                className="p-4 border border-solid rounded cursor-pointer hover:bg-gray-100 hover:dark:bg-gray-800" style={{ borderColor: selectedLayout === 'fishbone' ? 'primary.main' : 'divider' }}
                                onClick={() => setSelectedLayout('fishbone')}
                            >
                                <FormControlLabel
                                    value="fishbone"
                                    control={<Radio />}
                                    label={
                                        <Stack direction="row" spacing={2} alignItems="center">
                                            <FishboneIcon />
                                            <Box>
                                                <Typography variant="body1" fontWeight={500}>
                                                    Fishbone Layout (Ishikawa)
                                                </Typography>
                                                <Typography variant="caption" color="text.secondary">
                                                    Cause-and-effect diagram with angled branches
                                                </Typography>
                                            </Box>
                                        </Stack>
                                    }
                                    className="m-0 w-full"
                                />
                            </Box>
                        </Stack>
                    </RadioGroup>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button onClick={handleApply} variant="contained">
                    Apply Layout
                </Button>
            </DialogActions>
        </Dialog>
    );
}
