import { useNavigate } from 'react-router';
import { Box, Typography, Stack, Button, Grid, Surface as Paper } from '@ghatana/ui';
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
        { icon: <AutoAwesome tone="primary" />, title: 'AI-First Design', desc: 'Implicit personas drive intelligent scaffolding.' },
        { icon: <Architecture tone="secondary" />, title: 'Governed Architecture', desc: 'Standardized templates that scale securely.' },
        { icon: <Speed tone="success" />, title: 'Rapid Scaffolding', desc: 'Generate complete stacks in seconds.' },
        { icon: <Security tone="warning" />, title: 'Enterprise Ready', desc: 'Built-in observability, auth, and testing.' },
    ];

    return (
        <Box className="flex flex-col min-h-screen bg-gray-50 dark:bg-gray-950">
            {/* Public Header */}
            <Box component="header" className="p-4 flex justify-between items-center border-gray-200 dark:border-gray-700 border-b" >
                <Typography as="h6" fontWeight="bold">YAPPC</Typography>
                <Stack direction="row" spacing={2}>
                    <Button variant="ghost" onClick={handleLogin}>Sign In</Button>
                    <Button variant="solid" onClick={handleGetStarted}>Get Started</Button>
                </Stack>
            </Box>

            {/* Hero */}
            <Box className="flex-1 flex flex-col items-center justify-center p-8 md:p-16 text-center">
                <Typography
                    as="h2"
                    fontWeight="bold"
                    gutterBottom
                    className="max-w-[900px] text-[2.5rem] md:text-6xl"
                >
                    Build AI-Powered Applications in Minutes
                </Typography>
                <Typography
                    as="h5"
                    color="text.secondary"
                    className="mb-12 max-w-[600px] text-xl md:text-2xl"
                >
                    From idea to deployed application with the power of implicit personas and agentic workflows.
                </Typography>

                {/* Feature Grid */}
                <Grid container spacing={3} className="mb-16 max-w-[1000px]">
                    {features.map((f, i) => (
                        <Grid size={{ xs: 12, sm: 6, md: 3 }} key={i}>
                            <Paper variant="flat" className="p-6 text-center h-full border border-solid border-gray-200 dark:border-gray-700">
                                <Box className="mb-4">{f.icon}</Box>
                                <Typography as="p" className="text-lg font-medium" fontWeight="bold" gutterBottom>{f.title}</Typography>
                                <Typography as="p" className="text-sm" color="text.secondary">{f.desc}</Typography>
                            </Paper>
                        </Grid>
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
