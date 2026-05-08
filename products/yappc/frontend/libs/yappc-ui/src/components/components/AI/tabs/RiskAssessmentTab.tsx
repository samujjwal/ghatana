import { Lightbulb as LightbulbIcon } from 'lucide-react';
import { AlertTriangle as WarningIcon } from 'lucide-react';
import React from 'react';

import {
  Card,
  CardContent,
  Chip,
  LinearProgress,
  InteractiveList as List,
  ListItem,
  ListItemIcon,
  ListItemText as ListItemSecondaryAction,
  ListItemText,
  Typography,
} from '@ghatana/design-system';

import { Grid } from '../../Grid';

/** Single risk factor */
interface RiskFactor {
  factor: string;
  description?: string;
  weight: number;
}

/** Props for RiskAssessmentTab */
interface Props {
  insights: {
    deploymentRisk?: {
      riskLevel: 'low' | 'medium' | 'high' | 'critical';
      riskScore: number;
      riskFactors: RiskFactor[];
      recommendations: string[];
    };
  };
}

export const RiskAssessmentTab: React.FC<Props> = ({ insights }) => {
  if (!insights?.deploymentRisk) return null;

  return (
    <div className="w-full">
      <Card>
        <CardContent>
          <Typography as="h6" gutterBottom>
            Risk Assessment
          </Typography>
          <div className="flex items-center gap-4 mb-4">
            <Chip
              label={`${insights.deploymentRisk.riskLevel.toUpperCase()}`}
              color={
                insights.deploymentRisk.riskLevel === 'critical'
                  ? 'error'
                  : insights.deploymentRisk.riskLevel === 'high'
                    ? 'warning'
                    : 'success'
              }
              variant="outlined"
              size="sm"
            />
            <LinearProgress
              variant="determinate"
              value={insights.deploymentRisk.riskScore}
              className="grow h-[8px] rounded-2xl"
              color={
                insights.deploymentRisk.riskLevel === 'critical'
                  ? 'error'
                  : insights.deploymentRisk.riskLevel === 'high'
                    ? 'warning'
                    : 'success'
              }
            />
            <Typography as="p" className="text-sm" color="text.secondary">
              {insights.deploymentRisk.riskScore}% Risk
            </Typography>
          </div>

          <Grid cols="grid-cols-1 md:grid-cols-2" gap="gap-6">
            <div>
              <Typography as="p" className="text-sm font-medium" gutterBottom>
                Risk Factors
              </Typography>
              <List>
                {insights.deploymentRisk.riskFactors.map((factor, index) => (
                  <ListItem key={index}>
                    <ListItemIcon>
                      <WarningIcon color="currentColor" />
                    </ListItemIcon>
                    <ListItemText
                      primary={factor.factor}
                      secondary={factor.description}
                    />
                    <ListItemSecondaryAction>
                      <Chip
                        label={`${(factor.weight * 100).toFixed(0)}%`}
                        size="sm"
                        color={
                          factor.weight > 0.7
                            ? 'error'
                            : factor.weight > 0.3
                              ? 'warning'
                              : 'success'
                        }
                        variant="outlined"
                      />
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
              </List>
            </div>

            <div>
              <Typography as="p" className="text-sm font-medium" gutterBottom>
                Recommendations
              </Typography>
              <List>
                {insights.deploymentRisk.recommendations.map(
                  (recommendation, index) => (
                    <ListItem key={index}>
                      <ListItemIcon>
                        <LightbulbIcon color="currentColor" />
                      </ListItemIcon>
                      <ListItemText primary={recommendation} />
                    </ListItem>
                  )
                )}
              </List>
            </div>
          </Grid>
        </CardContent>
      </Card>
    </div>
  );
};

export default RiskAssessmentTab;
