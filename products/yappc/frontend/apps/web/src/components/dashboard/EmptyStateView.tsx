import { useNavigate } from 'react-router';
import { Box, Typography, Button, Stack } from '@ghatana/ui';
import { Sparkles as AutoAwesomeIcon, LayoutDashboard as DashboardCustomize, Zap as Bolt } from 'lucide-react';
import { IdeaInput } from './IdeaInput';

interface EmptyStateViewProps {
    onCreateProject: () => void;
    onSkip?: () => void;
}

export function EmptyStateView({ onCreateProject, onSkip }: EmptyStateViewProps) {
    return (
        <div className="h-full overflow-auto bg-bg-default">
            <div className="max-w-4xl mx-auto p-6 pt-20 text-center">
                <Box className="mb-12">
                    <div className="inline-flex items-center justify-center p-4 bg-primary-50 rounded-full mb-4">
                        <AutoAwesomeIcon className="text-blue-600 text-5xl" />
                    </div>
                    <Typography as="h3" fontWeight="bold" gutterBottom>
                        Welcome to YAPPC
                    </Typography>

                    <Typography as="h6" color="text.secondary" className="mb-8">
                        What do you want to build today?
                    </Typography>

                    <IdeaInput onSubmit={onCreateProject} />

                    <Box className="mt-12 mb-8">
                        <Typography as="p" className="mb-4 text-sm" color="text.secondary">
                            or select a starting point
                        </Typography>
                        <Stack direction="row" spacing={2} justifyContent="center">
                            <Button variant="outlined" startIcon={<DashboardCustomize />}>Browse Templates</Button>
                            <Button variant="outlined" startIcon={<Bolt />}>Quick Start Guide</Button>
                        </Stack>
                    </Box>

                    {onSkip && (
                        <Box className="mt-16">
                            <Typography as="p" className="mb-4 text-sm" color="text.secondary">
                                Just looking around?
                            </Typography>
                            <Button variant="ghost" tone="neutral" onClick={onSkip}>
                                Skip to Dashboard
                            </Button>
                        </Box>
                    )}
                </Box>
            </div>
        </div>
    );
}
