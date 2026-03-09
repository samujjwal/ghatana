import React from 'react';
import { Box, Card, CardContent, Stack, Typography } from '../../ui/tw-compat';

export interface InfoCardProps {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
  footer?: React.ReactNode;
  children?: React.ReactNode;
  icon?: React.ReactNode;
}

export const InfoCard: React.FC<InfoCardProps> = ({
  title,
  subtitle,
  action,
  children,
  footer,
  icon,
}) => {
  return (
    <Card variant="outlined" className="h-full flex flex-col">
      <CardContent className="flex-1 flex flex-col gap-2">
        <Stack direction="row" className="justify-between items-center">
          <Stack direction="row" className="items-center gap-1.5">
            {icon ? <Box className="text-primary-600 dark:text-primary-400">{icon}</Box> : null}
            <Box>
              <Typography variant="subtitle1" className="font-semibold">
                {title}
              </Typography>
              {subtitle ? (
                <Typography variant="caption" color="text.secondary">
                  {subtitle}
                </Typography>
              ) : null}
            </Box>
          </Stack>
          {action}
        </Stack>

        {children ? <Box className="flex-1">{children}</Box> : null}

        {footer ? (
          <Box className="mt-auto pt-1 border-t border-gray-200 dark:border-gray-700">
            {footer}
          </Box>
        ) : null}
      </CardContent>
    </Card>
  );
};

export default InfoCard;
