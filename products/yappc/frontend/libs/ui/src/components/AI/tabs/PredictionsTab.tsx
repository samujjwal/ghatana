import { Card, CardContent, LinearProgress, InteractiveList as List, ListItem, ListItemText, Typography, Chip } from '@ghatana/ui';
import { Grid } from '../../Grid';
import React from 'react';
import { Box } from '@ghatana/ui';
/** Props for PredictionsTab */
interface Props {
    insights: {
        buildTimePrediction?: {
            predictedTime: number;
            confidence: number;
            range: { min: number; max: number };
            factors: Array<{ name: string; description?: string; impact: number }>;
        };
    };
}

export const PredictionsTab: React.FC<Props> = ({ insights }) => {
    if (!insights?.buildTimePrediction) return null;

    return (
        <Grid cols="grid-cols-1 md:grid-cols-2" gap="gap-6">
            <div>
                <Card>
                    <CardContent>
                        <Typography as="h6">Build Time Prediction</Typography>
                        <Typography as="h4" tone="primary">{insights.buildTimePrediction.predictedTime.toFixed(1)} minutes</Typography>

                        <div style={{ marginTop: 12 }}>
                            <Typography as="p" className="text-sm">Range: {insights.buildTimePrediction.range.min.toFixed(1)} - {insights.buildTimePrediction.range.max.toFixed(1)} minutes</Typography>
                            <LinearProgress variant="determinate" value={insights.buildTimePrediction.confidence * 100} className="mb-2" />
                            <Typography as="span" className="text-xs text-gray-500">Confidence: {(insights.buildTimePrediction.confidence * 100).toFixed(0)}%</Typography>
                        </div>
                    </CardContent>
                </Card>
            </div>

            <div>
                <Card>
                    <CardContent>
                        <Typography as="h6" gutterBottom>Performance Factors</Typography>
                        <List dense>
                            {insights.buildTimePrediction.factors.map((factor, index) => (
                                <ListItem key={index}>
                                    <ListItemText
                                        primary={factor.name}
                                        secondary={factor.description}
                                        primaryTypographyProps={{ variant: 'body2' }}
                                        secondaryTypographyProps={{ variant: 'caption' }}
                                    />
                                    <Box className="ml-auto">
                                        <Chip
                                            label={`${(factor.impact * 100).toFixed(0)}%`}
                                            size="sm"
                                            color={factor.impact > 0.7 ? 'error' : factor.impact > 0.3 ? 'warning' : 'success'}
                                            variant="outlined"
                                        />
                                    </Box>
                                </ListItem>
                            ))}
                        </List>
                    </CardContent>
                </Card>
            </div>
        </Grid>
    );
};

export default PredictionsTab;
