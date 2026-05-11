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
import { useTranslation } from '@ghatana/i18n';

interface IntelligentOnboardingProps {
    onComplete: () => void;
}

export function IntelligentOnboarding({ onComplete }: IntelligentOnboardingProps) {
    const { t } = useTranslation('common');
    const [currentStep, setCurrentStep] = useState(0);
    const [userData, setUserData] = useState({
        name: '',
        role: '',
        experience: '',
        projectType: '',
        preferences: [] as string[]
    });

    const steps = [
        { title: t('onboarding.step.welcome'), description: t('onboarding.stepDesc.welcome') },
        { title: t('onboarding.step.yourRole'), description: t('onboarding.stepDesc.yourRole') },
        { title: t('onboarding.step.projectSetup'), description: t('onboarding.stepDesc.projectSetup') },
        { title: t('onboarding.step.readyToGo'), description: t('onboarding.stepDesc.readyToGo') }
    ];

    const roleOptions = [
        { id: 'developer', name: t('onboarding.role.developer.name'), description: t('onboarding.role.developer.desc') },
        { id: 'designer', name: t('onboarding.role.designer.name'), description: t('onboarding.role.designer.desc') },
        { id: 'product_manager', name: t('onboarding.role.pm.name'), description: t('onboarding.role.pm.desc') },
        { id: 'full_stack', name: t('onboarding.role.fullStack.name'), description: t('onboarding.role.fullStack.desc') }
    ];

    const projectTypes = [
        { id: 'webapp', name: t('onboarding.project.webApp'), emoji: '⚡' },
        { id: 'mobile', name: t('onboarding.project.mobile'), emoji: '✨' },
        { id: 'api', name: t('onboarding.project.api'), emoji: '🚀' },
        { id: 'library', name: t('onboarding.project.library'), emoji: '📁' }
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
                            {t('onboarding.welcomeTitle')}
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            {t('onboarding.welcomeSubtitle')}
                        </Typography>
                        <TextField
                            fullWidth
                            label={t('onboarding.yourName')}
                            value={userData.name}
                            onChange={(e) => setUserData({ ...userData, name: e.target.value })}
                            className="mb-6"
                        />
                        <TextField
                            fullWidth
                            label={t('onboarding.experienceQuestion')}
                            value={userData.experience}
                            onChange={(e) => setUserData({ ...userData, experience: e.target.value })}
                            placeholder={t('onboarding.experiencePlaceholder')}
                            className="mb-6"
                        />
                    </Box>
                );

            case 1:
                return (
                    <Box py={4}>
                        <Typography as="h5" fontWeight="bold" mb={2}>
                            {t('onboarding.primaryRole')}
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            {t('onboarding.personalizeText')}
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
                            {t('onboarding.projectTypeQuestion')}
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            {t('onboarding.projectTypeSubtitle')}
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
                            {t('onboarding.allSet')}
                        </Typography>
                        <Typography as="p" color="text.secondary" mb={4}>
                            {t('onboarding.readySubtitle')}
                        </Typography>
                        <Box display="flex" justifyContent="center" gap={2} mb={4}>
                            <Chip label={`${userData.name || t('onboarding.defaultUser')}`} variant="outlined" />
                            <Chip label={roleOptions.find(r => r.id === userData.role)?.name || t('onboarding.defaultRole')} variant="outlined" />
                            <Chip label={projectTypes.find(p => p.id === userData.projectType)?.name || t('onboarding.defaultProject')} variant="outlined" />
                        </Box>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            {t('onboarding.continueJourney')}
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
                            {t('onboarding.nav.back')}
                        </Button>
                        <Button
                            variant="solid"
                            onClick={handleNext}
                            endIcon={<ArrowForward />}
                            disabled={currentStep === 1 && !userData.role}
                        >
                            {currentStep === steps.length - 1 ? t('onboarding.nav.startJourney') : t('onboarding.nav.next')}
                        </Button>
                    </Box>
                </CardContent>
            </Card>
        </Box>
    );
}
