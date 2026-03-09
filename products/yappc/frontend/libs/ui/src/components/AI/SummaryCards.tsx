import { LineChart as RefreshIcon } from 'lucide-react';
import { Lightbulb as LightbulbIcon } from 'lucide-react';
import { Shield as SecurityIcon } from 'lucide-react';
import { Timer as TimerIcon } from 'lucide-react';
import { Box, Card, CardContent, Chip, Typography } from '@ghatana/ui';
import { Grid } from '../Grid';
import { resolveMuiColor } from '../../utils/safePalette';
import React from 'react';

import type { RecommendationCounts } from './types';

/** Build time prediction summary */
interface BuildTimeSummary {
    predictedTime: number;
    confidence: number;
    range: { min: number; max: number };
    factors: Array<{ name: string; impact: number; description?: string }>;
}

/** Deployment risk summary */
interface DeploymentRiskSummary {
    riskLevel: 'low' | 'medium' | 'high' | 'critical';
    riskScore: number;
    riskFactors: Array<{ factor: string; impact: number; description: string }>;
    recommendations: string[];
}

/** Props for SummaryCards component */
interface InsightsSummaryProps {
    buildTimePrediction?: BuildTimeSummary;
    deploymentRisk?: DeploymentRiskSummary;
    models?: Array<{ accuracy: number }>;
    recommendationCounts?: RecommendationCounts;
}

export const SummaryCards: React.FC<InsightsSummaryProps> = ({
    buildTimePrediction,
    deploymentRisk,
    models = [],
    recommendationCounts = { critical: 0, high: 0, medium: 0, low: 0, total: 0 }
}) => {
    const theme = useTheme();
    return (
        <Grid cols="grid-cols-1 sm:grid-cols-2 md:grid-cols-4" gap="gap-4">
            {buildTimePrediction && (
                <div>
                    <Card>
                        <CardContent>
                            <Box className="flex items-center gap-2 mb-2">
                                <TimerIcon color={resolveMuiColor(theme, 'info', 'default') as unknown} />
                                <Typography as="h6">Build Time</Typography>
                            </Box>
                            <Typography as="h4" color="info.main">{buildTimePrediction.predictedTime}s</Typography>
                            <Typography as="p" className="text-sm" color="text.secondary">
                                ± {Math.ceil((buildTimePrediction.range.max - buildTimePrediction.range.min) / 2)}s ({(buildTimePrediction.confidence * 100).toFixed(0)}% confidence)</Typography>
                        </CardContent>
                    </Card>
                </div>
            )}

            {deploymentRisk && (
                <div>
                    <Card>
                        <CardContent>
                            <Box className="flex items-center gap-2 mb-2">
                                <SecurityIcon color={resolveMuiColor(theme, deploymentRisk.riskLevel === 'critical' ? 'error' : deploymentRisk.riskLevel === 'high' ? 'warning' : 'success', 'default') as unknown} />
                                <Typography as="h6">Risk Level</Typography>
                            </Box>
                            <Typography as="h4" color={resolveMuiColor(theme, deploymentRisk.riskLevel === 'critical' ? 'error' : deploymentRisk.riskLevel === 'high' ? 'warning' : 'success', 'main')}>
                                {deploymentRisk.riskLevel.toUpperCase()}
                            </Typography>
                            <Typography as="p" className="text-sm" color="text.secondary">
                                Score: {deploymentRisk.riskScore}/100
                            </Typography>
                        </CardContent>
                    </Card>
                </div>
            )}

            <div>
                <Card>
                    <CardContent>
                        <Box className="flex items-center gap-2 mb-2">
                            <LightbulbIcon color={resolveMuiColor(theme, 'warning', 'default') as unknown} />
                            <Typography as="h6">Recommendations</Typography>
                        </Box>
                        <Typography as="h4" color="warning.main">{recommendationCounts.total}</Typography>
                        <Box className="flex gap-2 mt-2">
                            {recommendationCounts.critical > 0 && <Chip size="sm" label={`${recommendationCounts.critical} Critical`} color={resolveMuiColor(theme, 'error', 'default')} />}
                            {recommendationCounts.high > 0 && <Chip size="sm" label={`${recommendationCounts.high} High`} color={resolveMuiColor(theme, 'warning', 'default')} />}
                        </Box>
                    </CardContent>
                </Card>
            </div>

            <div>
                <Card>
                    <CardContent>
                        <Box className="flex items-center gap-2 mb-2">
                            <RefreshIcon color={resolveMuiColor(theme, 'success', 'default') as unknown} />
                            <Typography as="h6">AI Accuracy</Typography>
                        </Box>
                        <Typography as="h4" color="success.main">{models.length > 0 ? (models.reduce((s, m) => s + m.accuracy, 0) / models.length * 100).toFixed(0) : 0}%</Typography>
                        <Typography as="p" className="text-sm" color="text.secondary">{models.length} active models</Typography>
                    </CardContent>
                </Card>
            </div>
        </Grid>
    );
};

export default SummaryCards;
