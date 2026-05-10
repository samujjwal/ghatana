import { useNavigate } from 'react-router';
import { Box, Typography, Stack, Button, Grid, Surface as Paper } from '@ghatana/design-system';
import { Sparkles as AutoAwesome, Building2 as Architecture, Gauge as Speed, Shield as Security } from 'lucide-react';

interface GuestLandingViewProps {
    onLoginClick?: () => void;
    onGetStartedClick?: () => void;
    // For demo purposes
    onDemoLogin?: () => void;
    onDemoEmpty?: () => void;
}

export function GuestLandingView({
    onLoginClick,
    onGetStartedClick,
    onDemoLogin,
    onDemoEmpty
}: GuestLandingViewProps) {
    const navigate = useNavigate();

    const handleLogin = onLoginClick || (() => navigate('/login'));
    const handleGetStarted = onGetStartedClick || (() => navigate('/login'));

    const features = [
        { icon: <AutoAwesome className="text-blue-500" />, title: 'Guided Setup', desc: 'Start with a workspace and starter project that map to backed product flows.' },
        { icon: <Architecture className="text-purple-500" />, title: 'Governed Architecture', desc: 'Use consistent product structure and lifecycle checkpoints.' },
        { icon: <Speed className="text-emerald-500" />, title: 'Fast Project Kickoff', desc: 'Move from idea to a persisted project cockpit quickly.' },
        { icon: <Security className="text-orange-500" />, title: 'Operationally Honest', desc: 'Only mounted, supported surfaces are presented as active product behavior.' },
    ];

    return (
        <Box className="flex flex-col min-h-screen bg-surface-muted dark:bg-surface">
            {/* Public Header */}
            <header className="flex items-center justify-between border-b border-gray-200 p-4 dark:border-gray-700">
                <Typography variant="h6" fontWeight="bold">YAPPC</Typography>
                <Stack direction="row" spacing={2}>
                    <Button variant="ghost" onClick={handleLogin}>Sign In</Button>
                    <Button variant="solid" onClick={handleGetStarted}>Get Started</Button>
                </Stack>
            </header>

            {/* Hero */}
            <Box className="flex-1 flex flex-col items-center justify-center p-8 md:p-16 text-center">
                <Typography
                    variant="h2"
                    fontWeight="bold"
                    gutterBottom
                    className="max-w-[900px] text-[2.5rem] md:text-6xl"
                >
                    Build Product Workspaces Without Guesswork
                </Typography>
                <Typography
                    variant="h5"
                    color="text.secondary"
                    className="mb-12 max-w-[600px] text-xl md:text-2xl"
                >
                    Move from idea to a backed workspace, starter project, and lifecycle-guided cockpit with grounded product behavior.
                </Typography>

                {/* Feature Grid */}
                <Grid cols="grid-cols-1 md:grid-cols-2 lg:grid-cols-4" gap="gap-6" className="mb-16 max-w-[1000px]">
                    {features.map((f, i) => (
                        <Paper key={i} className="h-full border border-solid border-gray-200 p-6 text-center dark:border-gray-700">
                            <Box className="mb-4">{f.icon}</Box>
                            <Typography className="text-lg font-medium" fontWeight="bold" gutterBottom>{f.title}</Typography>
                            <Typography className="text-sm" color="text.secondary">{f.desc}</Typography>
                        </Paper>
                    ))}
                </Grid>

                {/* Demo Controls (if provided) */}
                {(onDemoLogin || onDemoEmpty) && (
                    <Stack direction="row" spacing={2} className="mt-8">
                        {onDemoLogin && (
                            <Button variant="solid" size="lg" onClick={onDemoLogin}>
                                Demo: Log In (Simulated)
                            </Button>
                        )}
                        {onDemoEmpty && (
                            <Button variant="outlined" size="lg" onClick={onDemoEmpty}>
                                Demo: First Time User
                            </Button>
                        )}
                    </Stack>
                )}
            </Box>
        </Box>
    );
}
