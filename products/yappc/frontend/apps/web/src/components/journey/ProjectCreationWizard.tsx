/**
 * Project Creation Wizard - Guided project setup with AI recommendations
 * 
 * Smart project creation that provides templates, recommendations,
 * and intelligent defaults based on user context.
 */

import React, { useState } from 'react';
import { Box, Card, CardContent, Typography, Button, TextField, Stepper, Step, StepLabel, Avatar } from '@ghatana/ui';
import { Plus as Add, ArrowRight as ArrowForward, ArrowLeft as ArrowBack, CheckCircle, Sparkles as AutoAwesome } from 'lucide-react';

interface ProjectCreationWizardProps {
    onComplete: (projectId: string) => void;
}

export function ProjectCreationWizard({ onComplete }: ProjectCreationWizardProps) {
    const [currentStep, setCurrentStep] = useState(0);
    const [projectData, setProjectData] = useState({
        name: '',
        description: '',
        type: '',
        template: '',
        features: [] as string[]
    });

    const steps = [
        { title: 'Project Basics', description: 'Name and description' },
        { title: 'Choose Type', description: 'Select project type' },
        { title: 'Select Template', description: 'Choose a template' },
        { title: 'Features', description: 'Select features' },
        { title: 'Create', description: 'Review and create' }
    ];

    const projectTypes = [
        { id: 'webapp', name: 'Web Application', description: 'Full-stack web app with React', emoji: '⚡' },
        { id: 'api', name: 'API Service', description: 'RESTful or GraphQL backend', emoji: '🚀' },
        { id: 'mobile', name: 'Mobile App', description: 'React Native cross-platform', emoji: '✨' },
        { id: 'library', name: 'Component Library', description: 'Reusable components & utils', emoji: '📁' }
    ];

    const templates = [
        { id: 'minimal', name: 'Minimal', description: 'Basic setup with essentials' },
        { id: 'fullstack', name: 'Full Stack', description: 'Complete app with backend and frontend' },
        { id: 'enterprise', name: 'Enterprise', description: 'Production-ready with all features' },
        { id: 'custom', name: 'Custom', description: 'Build from scratch' }
    ];

    const features = [
        { id: 'auth', name: 'Authentication', description: 'User login and registration' },
        { id: 'database', name: 'Database', description: 'Data persistence layer' },
        { id: 'api', name: 'API Layer', description: 'RESTful API endpoints' },
        { id: 'testing', name: 'Testing Suite', description: 'Unit and integration tests' },
        { id: 'ci-cd', name: 'CI/CD Pipeline', description: 'Automated deployment' },
        { id: 'monitoring', name: 'Monitoring', description: 'Logs and metrics' }
    ];

    const handleNext = () => {
        if (currentStep < steps.length - 1) {
            setCurrentStep(currentStep + 1);
        } else {
            // Create project
            const projectId = `project-${Date.now()}`;
            onComplete(projectId);
        }
    };

    const handleBack = () => {
        if (currentStep > 0) {
            setCurrentStep(currentStep - 1);
        }
    };

    const toggleFeature = (featureId: string) => {
        setProjectData(prev => ({
            ...prev,
            features: prev.features.includes(featureId)
                ? prev.features.filter(f => f !== featureId)
                : [...prev.features, featureId]
        }));
    };

    const renderStepContent = () => {
        switch (currentStep) {
            case 0:
                return (
                    <Box py={4}>
                        <Typography as="h5" fontWeight="bold" mb={2}>
                            Let's start with the basics
                        </Typography>
                        <TextField
                            fullWidth
                            label="Project Name"
                            value={projectData.name}
                            onChange={(e) => setProjectData({ ...projectData, name: e.target.value })}
                            placeholder="My Awesome Project"
                            className="mb-6"
                        />
                        <TextField
                            fullWidth
                            label="Description"
                            value={projectData.description}
                            onChange={(e) => setProjectData({ ...projectData, description: e.target.value })}
                            placeholder="What are you building?"
                            multiline
                            rows={3}
                            className="mb-6"
                        />
                    </Box>
                );

            case 1:
                return (
                    <Box py={4}>
                        <Typography as="h5" fontWeight="bold" mb={2}>
                            What type of project?
                        </Typography>
                        <Box display="grid" gridTemplateColumns="repeat(auto-fit, minmax(250px, 1fr))" gap={2}>
                            {projectTypes.map(type => (
                                <Card
                                    key={type.id}
                                    variant="flat"
                                    className="cursor-pointer hover:border-blue-600" style={{ border: projectData.type === type.id ? '2px solid' : '1px solid', borderColor: projectData.type === type.id ? 'primary.main' : 'divider' }}
                                    onClick={() => setProjectData({ ...projectData, type: type.id })}
                                >
                                    <CardContent className="text-center">
                                        <Typography as="h3" mb={2}>
                                            {type.emoji}
                                        </Typography>
                                        <Typography as="h6" fontWeight="medium">
                                            {type.name}
                                        </Typography>
                                        <Typography as="p" className="text-sm" color="text.secondary">
                                            {type.description}
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
                            Choose a template
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            Start with a pre-configured template to save time
                        </Typography>
                        <Box display="grid" gap={2}>
                            {templates.map(template => (
                                <Card
                                    key={template.id}
                                    variant="flat"
                                    className="cursor-pointer hover:border-blue-600" style={{ border: projectData.template === template.id ? '2px solid' : '1px solid', borderColor: projectData.template === template.id ? 'primary.main' : 'divider' }}
                                    onClick={() => setProjectData({ ...projectData, template: template.id })}
                                >
                                    <CardContent>
                                        <Typography as="h6" fontWeight="medium">
                                            {template.name}
                                        </Typography>
                                        <Typography as="p" className="text-sm" color="text.secondary">
                                            {template.description}
                                        </Typography>
                                    </CardContent>
                                </Card>
                            ))}
                        </Box>
                    </Box>
                );

            case 3:
                return (
                    <Box py={4}>
                        <Typography as="h5" fontWeight="bold" mb={2}>
                            Select features
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            Choose the features you need for your project
                        </Typography>
                        <Box display="grid" gap={2}>
                            {features.map(feature => (
                                <Card
                                    key={feature.id}
                                    variant="flat"
                                    className="cursor-pointer hover:border-blue-600" style={{ border: projectData.features.includes(feature.id) ? '2px solid' : '1px solid', borderColor: projectData.features.includes(feature.id) ? 'primary.main' : 'divider' }}
                                    onClick={() => toggleFeature(feature.id)}
                                >
                                    <CardContent>
                                        <Typography as="h6" fontWeight="medium">
                                            {feature.name}
                                        </Typography>
                                        <Typography as="p" className="text-sm" color="text.secondary">
                                            {feature.description}
                                        </Typography>
                                    </CardContent>
                                </Card>
                            ))}
                        </Box>
                    </Box>
                );

            case 4:
                return (
                    <Box className="text-center py-8">
                        <Avatar className="mb-6 w-[64px] h-[64px] bg-green-600 mx-auto">
                            <CheckCircle size={32} />
                        </Avatar>
                        <Typography as="h4" fontWeight="bold" mb={2}>
                            Ready to Create!
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            Review your project details and click create to get started
                        </Typography>
                        <Card variant="flat" className="border border-solid border-gray-200 dark:border-gray-700 max-w-[400px] mx-auto">
                            <CardContent>
                                <Typography as="h6" fontWeight="medium" mb={2}>
                                    {projectData.name || 'Untitled Project'}
                                </Typography>
                                <Typography as="p" className="text-sm" color="text.secondary" mb={2}>
                                    {projectData.description || 'No description provided'}
                                </Typography>
                                <Box display="flex" gap={1} flexWrap="wrap" justifyContent="center">
                                    <Typography as="span" className="text-xs text-gray-500">
                                        Type: {projectTypes.find(t => t.id === projectData.type)?.name || 'Not selected'}
                                    </Typography>
                                    <Typography as="span" className="text-xs text-gray-500">
                                        Template: {templates.find(t => t.id === projectData.template)?.name || 'Not selected'}
                                    </Typography>
                                    <Typography as="span" className="text-xs text-gray-500">
                                        Features: {projectData.features.length} selected
                                    </Typography>
                                </Box>
                            </CardContent>
                        </Card>
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
                            disabled={currentStep === 0 && !projectData.name}
                        >
                            {currentStep === steps.length - 1 ? 'Create Project' : 'Next'}
                        </Button>
                    </Box>
                </CardContent>
            </Card>
        </Box>
    );
}
