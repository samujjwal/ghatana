/**
 * @doc.type component
 * @doc.purpose Feature Request Mapping for customer success (Journey 24.1)
 * @doc.layer product
 * @doc.pattern Specialized Canvas Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Typography, Surface as Paper, Button, Chip, Alert, InteractiveList as List, ListItem, ListItemText } from '@ghatana/ui';
import { UploadFile, Sparkles as AutoAwesome, TrendingUp } from 'lucide-react';

/**
 * Feature request interface
 */
export interface FeatureRequest {
    id: string;
    description: string;
    count: number;
    category: string;
    impact: 'high' | 'medium' | 'low';
    affectedScreens: string[];
}

/**
 * Props for FeatureRequestMapper
 */
export interface FeatureRequestMapperProps {
    requests?: FeatureRequest[];
    onUploadCSV?: (file: File) => void;
    onClusterRequests?: () => void;
    onAnalyzeImpact?: (requestId: string) => void;
}

/**
 * FeatureRequestMapper Component
 * 
 * Feature request mapping for customer success with:
 * - Upload feedback CSV
 * - AI clustering (top requests)
 * - Impact mapping (affected screens)
 */
export const FeatureRequestMapper: React.FC<FeatureRequestMapperProps> = ({
    requests = [],
    onUploadCSV,
    onClusterRequests,
    onAnalyzeImpact,
}) => {
    const [processing, setProcessing] = useState(false);

    const handleFileUpload = useCallback(
        (event: React.ChangeEvent<HTMLInputElement>) => {
            const file = event.target.files?.[0];
            if (file) {
                onUploadCSV?.(file);
                setProcessing(true);
                setTimeout(() => setProcessing(false), 1500);
            }
        },
        [onUploadCSV]
    );

    const getImpactColor = (impact: string) => {
        switch (impact) {
            case 'high':
                return 'error';
            case 'medium':
                return 'warning';
            case 'low':
                return 'default';
            default:
                return 'default';
        }
    };

    return (
        <Box className="h-full flex flex-col">
            <Paper className="p-4 mb-4">
                <Box className="flex items-center gap-4 flex-wrap">
                    <Typography as="h6">Feature Request Mapper</Typography>

                    <Button component="label" startIcon={<UploadFile />} variant="outlined">
                        Upload CSV
                        <input type="file" hidden accept=".csv" onChange={handleFileUpload} />
                    </Button>

                    <Button startIcon={<AutoAwesome />} variant="solid" onClick={onClusterRequests} disabled={requests.length === 0}>
                        AI Cluster
                    </Button>
                </Box>

                {processing && (
                    <Alert severity="info" className="mt-4">
                        Processing feedback data...
                    </Alert>
                )}
            </Paper>

            <Box className="flex-1 overflow-y-auto p-4">
                {requests.length === 0 ? (
                    <Box className="text-center py-16">
                        <Typography as="h6" color="text.secondary">
                            No feature requests yet
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            Upload a CSV file to analyze customer feedback
                        </Typography>
                    </Box>
                ) : (
                    <List>
                        {requests.map((request) => (
                            <Paper key={request.id} className="p-4 mb-4">
                                <ListItem className="flex-col items-start p-0">
                                    <Box className="flex items-center gap-2 mb-2 w-full">
                                        <TrendingUp />
                                        <ListItemText
                                            primary={request.description}
                                            secondary={`${request.count} requests`}
                                        />
                                        <Chip label={request.impact} color={getImpactColor(request.impact)} size="sm" />
                                    </Box>

                                    <Box className="flex gap-1 flex-wrap">
                                        <Chip label={request.category} size="sm" variant="outlined" />
                                        {request.affectedScreens.map((screen, idx) => (
                                            <Chip key={idx} label={screen} size="sm" />
                                        ))}
                                    </Box>

                                    <Button
                                        size="sm"
                                        onClick={() => onAnalyzeImpact?.(request.id)}
                                        className="mt-2"
                                    >
                                        Analyze Impact
                                    </Button>
                                </ListItem>
                            </Paper>
                        ))}
                    </List>
                )}
            </Box>
        </Box>
    );
};
