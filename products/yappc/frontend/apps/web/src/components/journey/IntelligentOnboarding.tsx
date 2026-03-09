/**
 * Intelligent Onboarding - Enhanced AI-driven onboarding experience
 * 
 * Smart onboarding that adapts to user context and provides
 * personalized workspace and project setup recommendations.
 */

import React, { useState } from 'react';
import { Box, Card, CardContent, Typography, Button, Stepper, Step, StepLabel, TextField, Chip, Avatar } from '@ghatana/ui';
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
        { title: 'Welcome', description: 'Get to know you' },
        { title: 'Your Role', description: 'Tell us about your role' },
        { title: 'Project Setup', description: 'Configure your first project' },
        { title: 'Ready to Go!', description: 'Complete setup' }
    ];

    const roleOptions = [
        { id: 'developer', name: 'Developer', description: 'Building applications' },
        { id: 'designer', name: 'Designer', description: 'Creating user experiences' },
        { id: 'product_manager', name: 'Product Manager', description: 'Managing products' },
        { id: 'full_stack', name: 'Full Stack', description: 'End-to-end development' }
    ];

    const projectTypes = [
        { id: 'webapp', name: 'Web Application', emoji: '⚡' },
        { id: 'mobile', name: 'Mobile App', emoji: '✨' },
        { id: 'api', name: 'API Service', emoji: '🚀' },
        { id: 'library', name: 'Component Library', emoji: '📁' }
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
                        <Avatar className="mb-6 w-[64px] h-[64px] bg-blue-600 mx-auto">
                            <AutoAwesome size={32} />
                        </Avatar>
                        <Typography as="h4" fontWeight="bold" mb={2}>
                            Welcome to Yappc
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            Let's set up your workspace in seconds with AI-powered guidance
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
                            label="What's your experience level?"
                            value={userData.experience}
                            onChange={(e) => setUserData({ ...userData, experience: e.target.value })}
                            placeholder="e.g., Beginner, Intermediate, Expert"
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
                            This helps us personalize your experience and recommendations
                        </Typography>
                        <Box display="grid" gap={2}>
                            {roleOptions.map(role => (
                                <Card
                                    key={role.id}
                                    variant="flat"
                                    className="cursor-pointer hover:border-blue-600" style={{ border: userData.role === role.id ? '2px solid' : '1px solid', borderColor: userData.role === role.id ? 'primary.main' : 'divider' }}
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
                            Choose your first project type to get started
                        </Typography>
                        <Box display="grid" gridTemplateColumns="repeat(auto-fit, minmax(200px, 1fr))" gap={2}>
                            {projectTypes.map(type => (
                                <Card
                                    key={type.id}
                                    variant="flat"
                                    className="cursor-pointer hover:border-blue-600" style={{ border: userData.projectType === type.id ? '2px solid' : '1px solid', borderColor: userData.projectType === type.id ? 'primary.main' : 'divider' }}
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
                        <Avatar className="mb-6 w-[64px] h-[64px] bg-green-600 mx-auto">
                            <CheckCircle size={32} />
                        </Avatar>
                        <Typography as="h4" fontWeight="bold" mb={2}>
                            You're All Set!
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            Your workspace is ready. We've personalized your experience based on your profile.
                        </Typography>
                        <Box display="flex" justifyContent="center" gap={2} mb={4}>
                            <Chip label={`${userData.name || 'User'}`} variant="outlined" />
                            <Chip label={roleOptions.find(r => r.id === userData.role)?.name || 'Role'} variant="outlined" />
                            <Chip label={projectTypes.find(p => p.id === userData.projectType)?.name || 'Project'} variant="outlined" />
                        </Box>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            Click continue to start your journey
                        </Typography>
                    </Box>
                );

            default:
                return null;
        }
    };

    return (
        <Box className="h-full overflow-auto bg-bg-default p-6">
            <Card variant="flat" className="border border-solid border-gray-200 dark:border-gray-700 max-w-[600px] mx-auto">
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
