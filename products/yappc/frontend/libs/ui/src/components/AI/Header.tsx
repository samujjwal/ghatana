import { Brain as PsychologyIcon } from 'lucide-react';
import { RefreshCw as RefreshIcon } from 'lucide-react';
import { Box, IconButton, Tooltip, Typography, Spinner as CircularProgress } from '@ghatana/ui';
import React from 'react';

import { wrapForTooltip } from '../../utils/accessibility';

/**
 *
 */
interface Props {
    title?: string;
    isAnalyzing?: boolean;
    onRefresh?: () => void;
}

export const Header: React.FC<Props> = ({ title = 'AI Insights & Recommendations', isAnalyzing, onRefresh }) => {
    return (
        <Box className="flex items-center justify-between mb-6">
            <Box className="flex items-center gap-2">
                <PsychologyIcon tone="primary" />
                <Typography as="h4" component="h1">{title}</Typography>
                {isAnalyzing && <CircularProgress size={20} />}
            </Box>

            <Box className="flex items-center gap-4">
                <Tooltip title="Refresh analysis">
                    {wrapForTooltip(
                        <IconButton onClick={onRefresh}>
                            <RefreshIcon aria-hidden={true} />
                        </IconButton>,
                        { 'aria-describedby': `ai-refresh-analysis` }
                    )}
                </Tooltip>
            </Box>
        </Box>
    );
};

export default Header;
