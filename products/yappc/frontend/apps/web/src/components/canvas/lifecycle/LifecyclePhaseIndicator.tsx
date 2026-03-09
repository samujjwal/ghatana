/**
 * Lifecycle Phase Indicator Component
 * 
 * Displays current lifecycle phase and allows phase transitions
 * 
 * @doc.type component
 * @doc.purpose Display and control lifecycle phase
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Select,
  Stack,
  Tooltip,
  Typography,
} from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import {
    LifecyclePhase,
    getPhaseDescription,
    getPhaseColor,
    canTransitionToPhase,
} from '@/types/lifecycle';

export interface LifecyclePhaseIndicatorProps {
    currentPhase: LifecyclePhase;
    onPhaseChange: (phase: LifecyclePhase) => void;
    disabled?: boolean;
}

/**
 * Lifecycle Phase Indicator
 * Shows current phase as a colored chip with transition dialog
 */
export const LifecyclePhaseIndicator: React.FC<LifecyclePhaseIndicatorProps> = ({
    currentPhase,
    onPhaseChange,
    disabled = false,
}) => {
    const [dialogOpen, setDialogOpen] = React.useState(false);
    const [selectedPhase, setSelectedPhase] = React.useState(currentPhase);

    const phaseColor = getPhaseColor(currentPhase);
    const phaseDescription = getPhaseDescription(currentPhase);

    const handleOpenDialog = () => {
        setSelectedPhase(currentPhase);
        setDialogOpen(true);
    };

    const handleCloseDialog = () => {
        setDialogOpen(false);
    };

    const handleConfirmTransition = () => {
        if (selectedPhase !== currentPhase) {
            onPhaseChange(selectedPhase);
        }
        setDialogOpen(false);
    };

    // Get all phases that can be transitioned to from current phase
    const availablePhases = (Object.values(LifecyclePhase) as LifecyclePhase[]).filter((phase) =>
        canTransitionToPhase(currentPhase, phase)
    );

    return (
        <>
            <Tooltip title={phaseDescription}>
                <Chip
                    label={currentPhase}
                    onClick={disabled ? undefined : handleOpenDialog}
                    className="text-white font-bold" style={{ backgroundColor: phaseColor, cursor: disabled ? 'default' : 'pointer' }}
                    size="medium"
                />
            </Tooltip>

            <Dialog open={dialogOpen} onClose={handleCloseDialog} maxWidth="sm" fullWidth>
                <DialogTitle>Change Lifecycle Phase</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <Typography variant="body2" color="text.secondary">
                            Current Phase: <strong>{currentPhase}</strong>
                        </Typography>
                        <Typography variant="body2">
                            {phaseDescription}
                        </Typography>

                        <Box>
                            <Typography variant="subtitle2" gutterBottom>
                                Transition to:
                            </Typography>
                            <Select
                                fullWidth
                                value={selectedPhase}
                                onChange={(e) => setSelectedPhase(e.target.value as LifecyclePhase)}
                            >
                                <MenuItem value={currentPhase} disabled>
                                    {currentPhase} (Current)
                                </MenuItem>
                                {availablePhases.map((phase: LifecyclePhase) => (
                                    <MenuItem key={phase as string} value={phase}>
                                        {phase} - {getPhaseDescription(phase)}
                                    </MenuItem>
                                ))}
                            </Select>
                        </Box>

                        {selectedPhase !== currentPhase && (
                            <Box
                                className="p-4 rounded bg-sky-400" >
                                <Typography variant="body2">
                                    <strong>Next Phase:</strong> {getPhaseDescription(selectedPhase)}
                                </Typography>
                            </Box>
                        )}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleCloseDialog}>Cancel</Button>
                    <Button
                        onClick={handleConfirmTransition}
                        variant="contained"
                        disabled={selectedPhase === currentPhase}
                    >
                        Change Phase
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    );
};

/**
 * Compact Phase Indicator (for toolbar)
 */
export const CompactPhaseIndicator: React.FC<{
    currentPhase: LifecyclePhase;
}> = ({ currentPhase }) => {
    const phaseColor = getPhaseColor(currentPhase);

    return (
        <Box
            className="flex items-center gap-2 px-3 py-1 bg-[rgba(255,_255,_255,_0.9)] rounded" style={{ border: `2px solid ${phaseColor, backgroundColor: 'phaseColor' }}
        >
            <Box
                className="rounded-full w-[8px] h-[8px]" />
            <Typography variant="caption" fontWeight="bold">
                {currentPhase}
            </Typography>
        </Box>
    );
};

/**
 * Phase Progress Bar (shows all phases with current highlighted)
 */
export const PhaseProgressBar: React.FC<{
    currentPhase: LifecyclePhase;
}> = ({ currentPhase }) => {
    const allPhases = [
        LifecyclePhase.INTENT,
        LifecyclePhase.SHAPE,
        LifecyclePhase.VALIDATE,
        LifecyclePhase.GENERATE,
        LifecyclePhase.RUN,
        LifecyclePhase.OBSERVE,
        LifecyclePhase.IMPROVE,
    ];

    const currentIndex = allPhases.indexOf(currentPhase);

    return (
        <Box className="w-full">
            <Stack direction="row" spacing={0.5}>
                {allPhases.map((phase, index) => {
                    const isActive = index === currentIndex;
                    const isPast = index < currentIndex;
                    const color = getPhaseColor(phase);

                    return (
                        <Tooltip key={phase} title={getPhaseDescription(phase)}>
                            <Box
                                className="flex-1 h-[4px] rounded transition-all duration-300" style={{ backgroundColor: isActive || isPast ? color : 'grey.300', opacity: isActive ? 1 : isPast ? 0.7 : 0.3 }}
                            />
                        </Tooltip>
                    );
                })}
            </Stack>
            <Typography variant="caption" color="text.secondary" className="mt-1">
                {getPhaseDescription(currentPhase)}
            </Typography>
        </Box>
    );
};
