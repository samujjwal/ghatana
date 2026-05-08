import { AlertCircle as ErrorIcon } from 'lucide-react';
import { TrendingUp as TrendingUpIcon } from 'lucide-react';
import React from 'react';

import {
  Card,
  CardContent,
  InteractiveList as List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Typography,
} from '@ghatana/design-system';

import { Grid } from '../../Grid/Grid.tailwind';

/** Component props */
interface Props {
  insights: {
    detected: string[];
    anomalies: string[];
    trends: string[];
  };
}

export const PatternAnalysisTab: React.FC<Props> = ({ insights }) => {
  if (!insights) return null;

  const hasData =
    insights.detected.length > 0 ||
    insights.anomalies.length > 0 ||
    insights.trends.length > 0;

  return (
    <div className="w-full">
      <div className="w-full mb-6">
        <Card>
          <CardContent>
            <Typography as="h6">Pattern Analysis</Typography>

            {hasData ? (
              <Grid
                cols="grid-cols-1 md:grid-cols-3"
                gap="gap-4"
                className="mt-4"
              >
                {insights.detected.length > 0 && (
                  <div>
                    <Typography
                      as="p"
                      className="text-lg font-medium"
                      gutterBottom
                    >
                      Detected Patterns
                    </Typography>
                    <List>
                      {insights.detected.map((pattern, index) => (
                        <ListItem key={`pattern-${index}`}>
                          <ListItemText primary={pattern} />
                        </ListItem>
                      ))}
                    </List>
                  </div>
                )}

                {insights.anomalies.length > 0 && (
                  <div>
                    <Typography
                      as="p"
                      className="text-lg font-medium"
                      gutterBottom
                    >
                      Anomalies
                    </Typography>
                    <List>
                      {insights.anomalies.map((anomaly, index) => (
                        <ListItem key={`anomaly-${index}`}>
                          <ListItemIcon>
                            <ErrorIcon color="currentColor" size={16} />
                          </ListItemIcon>
                          <ListItemText primary={anomaly} />
                        </ListItem>
                      ))}
                    </List>
                  </div>
                )}

                {insights.trends.length > 0 && (
                  <div>
                    <Typography
                      as="p"
                      className="text-lg font-medium"
                      gutterBottom
                    >
                      Trends
                    </Typography>
                    <List>
                      {insights.trends.map((trend, index) => (
                        <ListItem key={`trend-${index}`}>
                          <ListItemIcon>
                            <TrendingUpIcon color="currentColor" size={16} />
                          </ListItemIcon>
                          <ListItemText primary={trend} />
                        </ListItem>
                      ))}
                    </List>
                  </div>
                )}
              </Grid>
            ) : (
              <Typography
                as="p"
                color="text.secondary"
                className="mt-4"
              >
                No pattern data available. Run an analysis to detect patterns in
                your data.
              </Typography>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default PatternAnalysisTab;
