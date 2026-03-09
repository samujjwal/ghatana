import { CheckCircle as CheckCircleIcon } from 'lucide-react';
import { ChevronDown as ExpandMoreIcon } from 'lucide-react';
import { Accordion, AccordionDetails, AccordionSummary, Surface as Paper, Typography } from '@ghatana/ui';
import { Grid } from '../../Grid';
import React from 'react';

import { RecommendationCard } from '../RecommendationCard';

import type { RecommendationsByType, RecommendationCounts } from '../types';

/**
 * Recommendations tab shows grouped recommendations and allows implementing/dismissing them.
 */
interface Props {
    recommendationsByType: RecommendationsByType;
    recommendationCounts: RecommendationCounts;
    implementingIds: Set<string>;
    onImplement: (id: string) => void;
    onDismiss: (id: string) => void;
}

export const RecommendationsTab: React.FC<Props> = ({
    recommendationsByType,
    recommendationCounts,
    implementingIds,
    onImplement,
    onDismiss,
}) => {
    if (recommendationCounts.total === 0) {
        return (
            <Paper className="p-8 text-center">
                <CheckCircleIcon className="mb-4 text-5xl text-green-600" />
                <Typography as="h6" gutterBottom>
                    No Recommendations
                </Typography>
                <Typography color="text.secondary">Your system is performing optimally. Check back later for new insights.</Typography>
            </Paper>
        );
    }

    return (
        <div>
            {Object.entries(recommendationsByType).map(([type, recommendations]) => (
                <Accordion key={type} defaultExpanded>
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                        <Typography as="h6" className="capitalize">
                            {type} Recommendations ({recommendations.length})
                        </Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                        <Grid cols="grid-cols-1 md:grid-cols-2" gap="gap-6">
                            {recommendations.map((recommendation) => (
                                <div key={recommendation.id}>
                                    <RecommendationCard
                                        recommendation={recommendation}
                                        onImplement={onImplement}
                                        onDismiss={onDismiss}
                                        isImplementing={implementingIds.has(recommendation.id)}
                                    />
                                </div>
                            ))}
                        </Grid>
                    </AccordionDetails>
                </Accordion>
            ))}
        </div>
    );
};

export default RecommendationsTab;
