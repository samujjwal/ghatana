// @ts-nocheck
/**
 * Intelligent Onboarding - Enhanced guided onboarding experience
 * 
 * Smart onboarding that adapts to user context and provides
 * personalized workspace and project setup recommendations.
 */

import React, { useState } from 'react';
import { Box, Card, CardContent, Typography, Button, Stepper, Step, StepLabel, TextField, Chip, Avatar } from '@ghatana/design-system';
import { Sparkles as AutoAwesome, ArrowRight as ArrowForward, ArrowLeft as ArrowBack, CheckCircle } from 'lucide-react';

interface IntelligentOnboardingProps {
    onComplete: () => void;
}

export function IntelligentOnboarding({ onComplete }: IntelligentOnboardingProps) {
    const [currentStep, setCurrentStep] = useState(0);
    const [userData, setUserData] = useState({
        name: '',
        role: '',
        experience: '',
        projectType: '',
        preferences: [] as string[]
    });

    const steps = [
        { title: 'Welcome', description: 'Start your workspace setup' },
        { title: 'Your Role', description: 'Personalize guidance around your role' },
        { title: 'Project Setup', description: 'Tell us what you are building' },
        { title: 'Ready to Go!', description: 'Finish onboarding' }
    ];

    const roleOptions = [
        { id: 'developer', name: 'Developer', description: 'Build and ship application features' },
        { id: 'designer', name: 'Designer', description: 'Shape product flows and UI systems' },
        { id: 'product_manager', name: 'Product Manager', description: 'Plan, prioritize, and coordinate delivery' },
        { id: 'full_stack', name: 'Full Stack', description: 'Work across frontend, backend, and deployment' }
    ];

    const projectTypes = [
        { id: 'webapp', name: 'Web Application', emoji: '⚡' },
        { id: 'mobile', name: 'Mobile App', emoji: '✨' },
        { id: 'api', name: 'API Service', emoji: '🚀' },
        { id: 'library', name: 'Library', emoji: '📁' }
    ];

    const handleNext = () => {
        if (currentStep < steps.length - 1) {
            setCurrentStep(currentStep + 1);
        } else {
            onComplete();
        }
    };

    const handleBack = () => {
        if (currentStep > 0) {
            setCurrentStep(currentStep - 1);
        }
    };

    const renderStepContent = () => {
        switch (currentStep) {
            case 0:
                return (
                    <Box className="text-center py-8">
                        <Avatar className="mb-6 w-[64px] h-[64px] bg-primary mx-auto">
                            <AutoAwesome size={32} />
                        </Avatar>
                        <Typography as="h4" fontWeight="bold" mb={2}>
                            Welcome to Yappc
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            Get guided recommendations tailored to your project and workflow.
                        </Typography>
                        <TextField
                            fullWidth
                            label="Your Name"
                            value={userData.name}
                            onChange={(e) => setUserData({ ...userData, name: e.target.value })}
                            className="mb-6"
                        />
                        <TextField
                            fullWidth
                            label="Experience Level"
                            value={userData.experience}
                            onChange={(e) => setUserData({ ...userData, experience: e.target.value })}
                            placeholder="e.g., Beginner, Intermediate, Advanced"
                            className="mb-6"
                        />
                    </Box>
                );

            case 1:
                return (
                    <Box py={4}>
                        <Typography as="h5" fontWeight="bold" mb={2}>
                            What's your primary role?
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            We will personalize the workspace around how you build.
                        </Typography>
                        <Box display="grid" gap={2}>
                            {roleOptions.map(role => (
                                <Card
                                    key={role.id}
                                    variant="flat"
                                    className="cursor-pointer hover:border-info-border" style={{ border: userData.role === role.id ? '2px solid' : '1px solid', borderColor: userData.role === role.id ? 'primary.main' : 'divider' }}
                                    onClick={() => setUserData({ ...userData, role: role.id })}
                                >
                                    <CardContent>
                                        <Typography as="h6" fontWeight="medium">
                                            {role.name}
                                        </Typography>
                                        <Typography as="p" className="text-sm" color="text.secondary">
                                            {role.description}
                                        </Typography>
                                    </CardContent>
                                </Card>
                            ))}
                        </Box>
                    </Box>
                );

            case 2:
                return (
                    <Box py={4}>
                        <Typography as="h5" fontWeight="bold" mb={2}>
                            What are you building?
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            Choose the closest project type so we can tune the starting point.
                        </Typography>
                        <Box display="grid" gridTemplateColumns="repeat(auto-fit, minmax(200px, 1fr))" gap={2}>
                            {projectTypes.map(type => (
                                <Card
                                    key={type.id}
                                    variant="flat"
                                    className="cursor-pointer hover:border-info-border" style={{ border: userData.projectType === type.id ? '2px solid' : '1px solid', borderColor: userData.projectType === type.id ? 'primary.main' : 'divider' }}
                                    onClick={() => setUserData({ ...userData, projectType: type.id })}
                                >
                                    <CardContent className="text-center">
                                        <Typography as="h3" mb={2}>
                                            {type.emoji}
                                        </Typography>
                                        <Typography as="h6" fontWeight="medium">
                                            {type.name}
                                        </Typography>
                                    </CardContent>
                                </Card>
                            ))}
                        </Box>
                    </Box>
                );

            case 3:
                return (
                    <Box className="text-center py-8">
                        <Avatar className="mb-6 w-[64px] h-[64px] bg-success-bg mx-auto">
                            <CheckCircle size={32} />
                        </Avatar>
                        <Typography as="h4" fontWeight="bold" mb={2}>
                            Your workspace is ready
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            You can start shaping your product with the context you shared.
                        </Typography>
                        <Box display="flex" justifyContent="center" gap={2} mb={4}>
                            <Chip label={`${userData.name || 'New user'}`} variant="outlined" />
                            <Chip label={roleOptions.find(r => r.id === userData.role)?.name || 'Role'} variant="outlined" />
                            <Chip label={projectTypes.find(p => p.id === userData.projectType)?.name || 'Project'} variant="outlined" />
                        </Box>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            Continue your journey from the workspace dashboard.
                        </Typography>
                    </Box>
                );

            default:
                return null;
        }
    };

    return (
        <Box className="h-full overflow-auto bg-bg-default p-6">
            <Card variant="flat" className="border border-solid border-border dark:border-border max-w-[600px] mx-auto">
                <CardContent>
                    {/* Stepper */}
                    <Stepper activeStep={currentStep} className="mb-8">
                        {steps.map((step, index) => (
                            <Step key={index}>
                                <StepLabel>{step.title}</StepLabel>
                            </Step>
                        ))}
                    </Stepper>

                    {/* Step Content */}
                    {renderStepContent()}

                    {/* Navigation */}
                    <Box display="flex" justifyContent="space-between" mt={4}>
                        <Button
                            variant="outlined"
                            onClick={handleBack}
                            disabled={currentStep === 0}
                            startIcon={<ArrowBack />}
                        >
                            Back
                        </Button>
                        <Button
                            variant="solid"
                            onClick={handleNext}
                            endIcon={<ArrowForward />}
                            disabled={currentStep === 1 && !userData.role}
                        >
                            {currentStep === steps.length - 1 ? 'Start Journey' : 'Next'}
                        </Button>
                    </Box>
                </CardContent>
            </Card>
        </Box>
    );
}
