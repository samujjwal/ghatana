import { Box, Typography, Button, Stack, Card } from '@ghatana/ui';
import { Sparkles as AutoAwesome, GitBranch as AccountTree, LayoutGrid as ViewModule } from 'lucide-react';

interface ImprovedEmptyStateProps {
  onSelectTemplate: () => void;
  onBlankCanvas: () => void;
  onAIAssistant: () => void;
}

/**
 * Improved empty state with better visual hierarchy and feature discovery
 * Shows preview of canvas capabilities with clear CTAs
 */
export function ImprovedEmptyState({
  onSelectTemplate,
  onBlankCanvas,
  onAIAssistant,
}: ImprovedEmptyStateProps) {
  return (
    <Box
      className="absolute flex flex-col items-center justify-center p-6 inset-0" >
      <Card
        variant="flat"
        className="p-10 w-full flex flex-col items-center gap-6 max-w-[600px] border border-solid border-gray-200 dark:border-gray-700" >
        <Box
          className="w-[64px] h-[64px] rounded-xl" style={{ backgroundColor: (theme) =>
              `linear-gradient(135deg, backgroundColor: (theme) => alpha(theme.palette.background.paper, backgroundColor: (theme) =>
          `linear-gradient(135deg }} >
          <AccountTree className="text-[32px]" />
        </Box>

        <Box textAlign="center">
          <Typography as="h5" fontWeight={600} gutterBottom>
            Start Building Your Architecture
          </Typography>
          <Typography as="p" color="text.secondary">
            Choose how you'd like to begin, then drag components from the left
            palette onto the canvas.
          </Typography>
        </Box>

        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          width="100%"
          justifyContent="center"
        >
          <Button
            variant="solid"
            size="lg"
            startIcon={<AutoAwesome />}
            onClick={onAIAssistant}
            className="flex-1 whitespace-nowrap"
          >
            AI Assistant
          </Button>
          <Button
            variant="outlined"
            size="lg"
            onClick={onSelectTemplate}
            className="flex-1 whitespace-nowrap"
          >
            Use Template
          </Button>
          <Button
            variant="ghost"
            size="lg"
            onClick={onBlankCanvas}
            className="flex-1 whitespace-nowrap"
          >
            Blank Canvas
          </Button>
        </Stack>

        <Box
          className="flex items-center gap-2 mt-2 p-3 rounded-lg text-gray-500 dark:text-gray-400" style={{ backgroundColor: (theme) => alpha(theme.palette.action.hover }} >
          <ViewModule size={16} />
          <Typography as="p" className="text-sm">
            <strong>Tip:</strong> Press{' '}
            <Box
              component="kbd"
              className="px-[6.4px] py-[1.6px] rounded font-semibold bg-blue-50 dark:bg-blue-900/20 font-mono text-[0.85em]"
            >
              ⌘K
            </Box>{' '}
            anytime for quick actions
          </Typography>
        </Box>
      </Card>
    </Box>
  );
}

