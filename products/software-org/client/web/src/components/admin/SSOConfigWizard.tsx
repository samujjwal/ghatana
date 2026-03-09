import React, { useState } from 'react';
import type { ReactNode } from 'react';
import {
    Box,
    Card,
    Button,
    Stack,
    Typography,
    TextField,
    Select,
    MenuItem,
    RadioGroup,
    FormControlLabel,
    Radio,
    Checkbox,
    Stepper,
    Step,
    StepLabel,
    Alert,
    Chip,
    Progress,
    FormControl,
    InputLabel,
    IconButton,
    Divider,
} from '@ghatana/ui';

/**
 * SSO Provider types
 */
type SSOProvider = 'okta' | 'azure-ad' | 'google' | 'saml' | null;

/**
 * SSO Configuration data
 */
interface SSOConfig {
    // Provider selection
    provider: SSOProvider;

    // Provider-specific configuration
    okta?: {
        domain: string;
        clientId: string;
        clientSecret: string;
        authorizationServerId?: string;
    };
    azureAd?: {
        tenantId: string;
        clientId: string;
        clientSecret: string;
        redirectUri: string;
    };
    google?: {
        clientId: string;
        clientSecret: string;
        hostedDomain?: string;
    };
    saml?: {
        entityId: string;
        ssoUrl: string;
        certificate: string;
        signRequests: boolean;
        attributeMapping: {
            email: string;
            firstName: string;
            lastName: string;
            groups?: string;
        };
    };

    // Testing results
    testConnection?: {
        status: 'success' | 'error' | 'pending';
        message?: string;
        timestamp?: string;
    };
    testLogin?: {
        status: 'success' | 'error' | 'pending';
        user?: {
            email: string;
            name: string;
            attributes: Record<string, any>;
        };
        message?: string;
    };

    // Rollout settings
    rollout?: {
        enabledForAll: boolean;
        enabledGroups: string[];
        fallbackToPassword: boolean;
        requireMfa: boolean;
        sessionTimeout: number; // minutes
    };
}

/**
 * Test result display
 */
interface TestResult {
    status: 'idle' | 'testing' | 'success' | 'error';
    message?: string;
    details?: Record<string, any>;
}

/**
 * Props for SSOConfigWizard
 */
interface SSOConfigWizardProps {
    onComplete?: (config: SSOConfig) => void;
    onCancel?: () => void;
    onTestConnection?: (config: SSOConfig) => Promise<{ success: boolean; message?: string }>;
    onTestLogin?: (config: SSOConfig) => Promise<{
        success: boolean;
        user?: any;
        message?: string;
    }>;
    existingConfig?: SSOConfig;
    availableGroups?: Array<{ id: string; name: string }>;
}

/**
 * SSO Configuration Wizard
 *
 * 5-step wizard for configuring Single Sign-On:
 * 1. Provider Selection
 * 2. Configuration Details
 * 3. Connection Testing
 * 4. Rollout Settings
 * 5. Review & Confirm
 */
export const SSOConfigWizard: React.FC<SSOConfigWizardProps> = ({
    onComplete,
    onCancel,
    onTestConnection,
    onTestLogin,
    existingConfig,
    availableGroups = [],
}) => {
    const [activeStep, setActiveStep] = useState(0);
    const [config, setConfig] = useState<SSOConfig>(
        existingConfig || {
            provider: null,
            rollout: {
                enabledForAll: false,
                enabledGroups: [],
                fallbackToPassword: true,
                requireMfa: false,
                sessionTimeout: 480, // 8 hours
            },
        }
    );
    const [testResult, setTestResult] = useState<TestResult>({ status: 'idle' });
    const [loginTestResult, setLoginTestResult] = useState<TestResult>({ status: 'idle' });

    const steps = [
        'Select Provider',
        'Configure Details',
        'Test Connection',
        'Rollout Settings',
        'Review & Confirm',
    ];

    // Validation
    const canProceed = (): boolean => {
        switch (activeStep) {
            case 0:
                return config.provider !== null;
            case 1:
                return validateConfiguration();
            case 2:
                return testResult.status === 'success';
            case 3:
                return validateRolloutSettings();
            case 4:
                return true;
            default:
                return false;
        }
    };

    const validateConfiguration = (): boolean => {
        switch (config.provider) {
            case 'okta':
                return !!(
                    config.okta?.domain &&
                    config.okta?.clientId &&
                    config.okta?.clientSecret
                );
            case 'azure-ad':
                return !!(
                    config.azureAd?.tenantId &&
                    config.azureAd?.clientId &&
                    config.azureAd?.clientSecret &&
                    config.azureAd?.redirectUri
                );
            case 'google':
                return !!(config.google?.clientId && config.google?.clientSecret);
            case 'saml':
                return !!(
                    config.saml?.entityId &&
                    config.saml?.ssoUrl &&
                    config.saml?.certificate &&
                    config.saml?.attributeMapping.email
                );
            default:
                return false;
        }
    };

    const validateRolloutSettings = (): boolean => {
        if (!config.rollout) return false;
        if (!config.rollout.enabledForAll && config.rollout.enabledGroups.length === 0) {
            return false;
        }
        return config.rollout.sessionTimeout > 0;
    };

    // Handlers
    const handleNext = () => {
        if (canProceed()) {
            setActiveStep((prev) => prev + 1);
        }
    };

    const handleBack = () => {
        setActiveStep((prev) => prev - 1);
    };

    const handleProviderSelect = (provider: SSOProvider) => {
        setConfig({ ...config, provider });
    };

    const handleTestConnection = async () => {
        if (!onTestConnection) {
            setTestResult({
                status: 'success',
                message: 'Mock test passed (no handler provided)',
            });
            return;
        }

        setTestResult({ status: 'testing' });

        try {
            const result = await onTestConnection(config);
            setTestResult({
                status: result.success ? 'success' : 'error',
                message: result.message || (result.success ? 'Connection successful' : 'Connection failed'),
            });
        } catch (error) {
            setTestResult({
                status: 'error',
                message: error instanceof Error ? error.message : 'Connection test failed',
            });
        }
    };

    const handleTestLogin = async () => {
        if (!onTestLogin) {
            setLoginTestResult({
                status: 'success',
                message: 'Mock login passed (no handler provided)',
                details: { email: 'test@example.com', name: 'Test User' },
            });
            return;
        }

        setLoginTestResult({ status: 'testing' });

        try {
            const result = await onTestLogin(config);
            setLoginTestResult({
                status: result.success ? 'success' : 'error',
                message: result.message || (result.success ? 'Login test successful' : 'Login test failed'),
                details: result.user,
            });
        } catch (error) {
            setLoginTestResult({
                status: 'error',
                message: error instanceof Error ? error.message : 'Login test failed',
            });
        }
    };

    const handleComplete = () => {
        onComplete?.(config);
    };

    // Step 1: Provider Selection
    const renderProviderSelection = () => {
        const providers = [
            {
                id: 'okta' as SSOProvider,
                name: 'Okta',
                description: 'Enterprise identity management with extensive app catalog',
                popular: true,
            },
            {
                id: 'azure-ad' as SSOProvider,
                name: 'Azure Active Directory',
                description: 'Microsoft\'s cloud-based identity and access management',
                popular: true,
            },
            {
                id: 'google' as SSOProvider,
                name: 'Google Workspace',
                description: 'Google\'s SSO for Workspace organizations',
                popular: true,
            },
            {
                id: 'saml' as SSOProvider,
                name: 'Generic SAML 2.0',
                description: 'Standard SAML 2.0 protocol for custom providers',
                popular: false,
            },
        ];

        return (
            <Box>
                <Typography variant="h6" gutterBottom>
                    Select Your SSO Provider
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                    Choose the identity provider your organization uses for authentication
                </Typography>

                <Stack spacing={2}>
                    {providers.map((provider) => (
                        <Card
                            key={provider.id}
                            variant="outlined"
                            sx={{
                                p: 2,
                                cursor: 'pointer',
                                borderColor: config.provider === provider.id ? 'primary.main' : 'divider',
                                bgcolor: config.provider === provider.id ? 'action.selected' : 'transparent',
                                '&:hover': {
                                    borderColor: 'primary.main',
                                    bgcolor: 'action.hover',
                                },
                            }}
                            onClick={() => handleProviderSelect(provider.id)}
                        >
                            <Stack direction="row" alignItems="center" spacing={2}>
                                <Radio checked={config.provider === provider.id} />
                                <Box sx={{ flex: 1 }}>
                                    <Stack direction="row" alignItems="center" spacing={1}>
                                        <Typography variant="subtitle1">{provider.name}</Typography>
                                        {provider.popular && (
                                            <Chip label="Popular" size="small" color="primary" />
                                        )}
                                    </Stack>
                                    <Typography variant="body2" color="text.secondary">
                                        {provider.description}
                                    </Typography>
                                </Box>
                            </Stack>
                        </Card>
                    ))}
                </Stack>
            </Box>
        );
    };

    // Step 2: Configuration Details
    const renderConfiguration = () => {
        switch (config.provider) {
            case 'okta':
                return (
                    <Box>
                        <Typography variant="h6" gutterBottom>
                            Configure Okta SSO
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                            Enter your Okta organization details. You can find these in your Okta admin console.
                        </Typography>

                        <Stack spacing={3}>
                            <TextField
                                fullWidth
                                label="Okta Domain"
                                placeholder="your-domain.okta.com"
                                value={config.okta?.domain || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        okta: { ...config.okta, domain: e.target.value } as any,
                                    })
                                }
                                helperText="Your Okta organization domain (without https://)"
                            />
                            <TextField
                                fullWidth
                                label="Client ID"
                                value={config.okta?.clientId || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        okta: { ...config.okta, clientId: e.target.value } as any,
                                    })
                                }
                            />
                            <TextField
                                fullWidth
                                label="Client Secret"
                                type="password"
                                value={config.okta?.clientSecret || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        okta: { ...config.okta, clientSecret: e.target.value } as any,
                                    })
                                }
                            />
                            <TextField
                                fullWidth
                                label="Authorization Server ID (Optional)"
                                value={config.okta?.authorizationServerId || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        okta: { ...config.okta, authorizationServerId: e.target.value } as any,
                                    })
                                }
                                helperText="Leave blank to use the default authorization server"
                            />
                        </Stack>
                    </Box>
                );

            case 'azure-ad':
                return (
                    <Box>
                        <Typography variant="h6" gutterBottom>
                            Configure Azure AD SSO
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                            Enter your Azure AD application details from the Azure portal.
                        </Typography>

                        <Stack spacing={3}>
                            <TextField
                                fullWidth
                                label="Tenant ID"
                                value={config.azureAd?.tenantId || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        azureAd: { ...config.azureAd, tenantId: e.target.value } as any,
                                    })
                                }
                                helperText="Your Azure AD tenant ID (GUID)"
                            />
                            <TextField
                                fullWidth
                                label="Application (Client) ID"
                                value={config.azureAd?.clientId || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        azureAd: { ...config.azureAd, clientId: e.target.value } as any,
                                    })
                                }
                            />
                            <TextField
                                fullWidth
                                label="Client Secret"
                                type="password"
                                value={config.azureAd?.clientSecret || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        azureAd: { ...config.azureAd, clientSecret: e.target.value } as any,
                                    })
                                }
                            />
                            <TextField
                                fullWidth
                                label="Redirect URI"
                                placeholder="https://your-app.com/auth/callback"
                                value={config.azureAd?.redirectUri || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        azureAd: { ...config.azureAd, redirectUri: e.target.value } as any,
                                    })
                                }
                                helperText="Must match the redirect URI registered in Azure AD"
                            />
                        </Stack>
                    </Box>
                );

            case 'google':
                return (
                    <Box>
                        <Typography variant="h6" gutterBottom>
                            Configure Google Workspace SSO
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                            Enter your Google Cloud project OAuth 2.0 credentials.
                        </Typography>

                        <Stack spacing={3}>
                            <TextField
                                fullWidth
                                label="Client ID"
                                value={config.google?.clientId || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        google: { ...config.google, clientId: e.target.value } as any,
                                    })
                                }
                                helperText="OAuth 2.0 Client ID from Google Cloud Console"
                            />
                            <TextField
                                fullWidth
                                label="Client Secret"
                                type="password"
                                value={config.google?.clientSecret || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        google: { ...config.google, clientSecret: e.target.value } as any,
                                    })
                                }
                            />
                            <TextField
                                fullWidth
                                label="Hosted Domain (Optional)"
                                placeholder="example.com"
                                value={config.google?.hostedDomain || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        google: { ...config.google, hostedDomain: e.target.value } as any,
                                    })
                                }
                                helperText="Restrict sign-in to users from a specific Google Workspace domain"
                            />
                        </Stack>
                    </Box>
                );

            case 'saml':
                return (
                    <Box>
                        <Typography variant="h6" gutterBottom>
                            Configure SAML 2.0 SSO
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                            Configure your SAML identity provider settings and attribute mappings.
                        </Typography>

                        <Stack spacing={3}>
                            <TextField
                                fullWidth
                                label="Entity ID"
                                placeholder="https://your-idp.com/entity-id"
                                value={config.saml?.entityId || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        saml: {
                                            ...config.saml,
                                            entityId: e.target.value,
                                            signRequests: config.saml?.signRequests ?? false,
                                            attributeMapping: config.saml?.attributeMapping || {
                                                email: '',
                                                firstName: '',
                                                lastName: '',
                                            },
                                        } as any,
                                    })
                                }
                                helperText="The unique identifier for your SAML IdP"
                            />
                            <TextField
                                fullWidth
                                label="SSO URL"
                                placeholder="https://your-idp.com/sso"
                                value={config.saml?.ssoUrl || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        saml: { ...config.saml, ssoUrl: e.target.value } as any,
                                    })
                                }
                                helperText="The single sign-on endpoint URL"
                            />
                            <TextField
                                fullWidth
                                label="X.509 Certificate"
                                multiline
                                rows={4}
                                placeholder="-----BEGIN CERTIFICATE-----&#10;...&#10;-----END CERTIFICATE-----"
                                value={config.saml?.certificate || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        saml: { ...config.saml, certificate: e.target.value } as any,
                                    })
                                }
                                helperText="The IdP's public certificate for signature verification"
                            />

                            <Divider />
                            <Typography variant="subtitle2">Attribute Mapping</Typography>
                            <TextField
                                fullWidth
                                label="Email Attribute"
                                placeholder="email or mail"
                                value={config.saml?.attributeMapping.email || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        saml: {
                                            ...config.saml,
                                            attributeMapping: {
                                                ...config.saml?.attributeMapping,
                                                email: e.target.value,
                                            } as any,
                                        } as any,
                                    })
                                }
                            />
                            <TextField
                                fullWidth
                                label="First Name Attribute"
                                placeholder="givenName or firstName"
                                value={config.saml?.attributeMapping.firstName || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        saml: {
                                            ...config.saml,
                                            attributeMapping: {
                                                ...config.saml?.attributeMapping,
                                                firstName: e.target.value,
                                            } as any,
                                        } as any,
                                    })
                                }
                            />
                            <TextField
                                fullWidth
                                label="Last Name Attribute"
                                placeholder="surname or lastName"
                                value={config.saml?.attributeMapping.lastName || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        saml: {
                                            ...config.saml,
                                            attributeMapping: {
                                                ...config.saml?.attributeMapping,
                                                lastName: e.target.value,
                                            } as any,
                                        } as any,
                                    })
                                }
                            />
                            <TextField
                                fullWidth
                                label="Groups Attribute (Optional)"
                                placeholder="groups or memberOf"
                                value={config.saml?.attributeMapping.groups || ''}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        saml: {
                                            ...config.saml,
                                            attributeMapping: {
                                                ...config.saml?.attributeMapping,
                                                groups: e.target.value,
                                            } as any,
                                        } as any,
                                    })
                                }
                            />

                            <FormControlLabel
                                control={
                                    <Checkbox
                                        checked={config.saml?.signRequests || false}
                                        onChange={(e) =>
                                            setConfig({
                                                ...config,
                                                saml: { ...config.saml, signRequests: e.target.checked } as any,
                                            })
                                        }
                                    />
                                }
                                label="Sign authentication requests"
                            />
                        </Stack>
                    </Box>
                );

            default:
                return null;
        }
    };

    // Step 3: Test Connection
    const renderTesting = () => {
        return (
            <Box>
                <Typography variant="h6" gutterBottom>
                    Test SSO Connection
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                    Verify that your SSO configuration is working correctly before enabling it for users.
                </Typography>

                <Stack spacing={3}>
                    {/* Connection Test */}
                    <Card variant="outlined" sx={{ p: 3 }}>
                        <Stack spacing={2}>
                            <Stack direction="row" alignItems="center" justifyContent="space-between">
                                <Box>
                                    <Typography variant="subtitle1">Test Connection</Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        Verify provider credentials and connectivity
                                    </Typography>
                                </Box>
                                <Button
                                    variant="outlined"
                                    onClick={handleTestConnection}
                                    disabled={testResult.status === 'testing'}
                                >
                                    {testResult.status === 'testing' ? 'Testing...' : 'Test Connection'}
                                </Button>
                            </Stack>

                            {testResult.status === 'testing' && <Progress variant="linear" value={0} indeterminate />}

                            {testResult.status === 'success' && (
                                <Alert severity="success">{testResult.message}</Alert>
                            )}

                            {testResult.status === 'error' && (
                                <Alert severity="error">{testResult.message}</Alert>
                            )}
                        </Stack>
                    </Card>

                    {/* Login Test */}
                    <Card variant="outlined" sx={{ p: 3 }}>
                        <Stack spacing={2}>
                            <Stack direction="row" alignItems="center" justifyContent="space-between">
                                <Box>
                                    <Typography variant="subtitle1">Test User Login</Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        Perform a test authentication with your SSO provider
                                    </Typography>
                                </Box>
                                <Button
                                    variant="outlined"
                                    onClick={handleTestLogin}
                                    disabled={
                                        testResult.status !== 'success' || loginTestResult.status === 'testing'
                                    }
                                >
                                    {loginTestResult.status === 'testing' ? 'Testing...' : 'Test Login'}
                                </Button>
                            </Stack>

                            {loginTestResult.status === 'testing' && <Progress variant="linear" value={0} indeterminate />}

                            {loginTestResult.status === 'success' && (
                                <>
                                    <Alert severity="success">{loginTestResult.message}</Alert>
                                    {loginTestResult.details && (
                                        <Box sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 1 }}>
                                            <Typography variant="caption" color="text.secondary">
                                                User Attributes
                                            </Typography>
                                            <pre style={{ fontSize: '12px', margin: '8px 0 0 0' }}>
                                                {JSON.stringify(loginTestResult.details, null, 2)}
                                            </pre>
                                        </Box>
                                    )}
                                </>
                            )}

                            {loginTestResult.status === 'error' && (
                                <Alert severity="error">{loginTestResult.message}</Alert>
                            )}
                        </Stack>
                    </Card>

                    {testResult.status !== 'success' && (
                        <Alert severity="info">
                            Connection test must pass before you can test user login.
                        </Alert>
                    )}
                </Stack>
            </Box>
        );
    };

    // Step 4: Rollout Settings
    const renderRolloutSettings = () => {
        return (
            <Box>
                <Typography variant="h6" gutterBottom>
                    Configure Rollout Settings
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                    Control how SSO is enabled for your users and configure security settings.
                </Typography>

                <Stack spacing={3}>
                    {/* Enable for All vs Groups */}
                    <Card variant="outlined" sx={{ p: 3 }}>
                        <Typography variant="subtitle1" gutterBottom>
                            User Enablement
                        </Typography>
                        <RadioGroup
                            value={config.rollout?.enabledForAll ? 'all' : 'groups'}
                            onChange={(e: { target: { value: unknown } }) =>
                                setConfig({
                                    ...config,
                                    rollout: {
                                        ...config.rollout!,
                                        enabledForAll: e.target.value === 'all',
                                        enabledGroups:
                                            e.target.value === 'all'
                                                ? []
                                                : config.rollout?.enabledGroups || [],
                                    },
                                })
                            }
                        >
                            <FormControlLabel
                                value="all"
                                control={<Radio />}
                                label="Enable for all users"
                            />
                            <FormControlLabel
                                value="groups"
                                control={<Radio />}
                                label="Enable for specific groups"
                            />
                        </RadioGroup>

                        {!config.rollout?.enabledForAll && (
                            <FormControl fullWidth sx={{ mt: 2 }}>
                                <InputLabel>Select Groups</InputLabel>
                                <Select
                                    multiple
                                    value={config.rollout?.enabledGroups || []}
                                    onChange={(e: { target: { value: unknown } }) => {
                                        const raw = e.target.value;
                                        const enabledGroups = Array.isArray(raw)
                                            ? raw.map((v) => String(v))
                                            : [String(raw)];

                                        setConfig({
                                            ...config,
                                            rollout: {
                                                ...config.rollout!,
                                                enabledGroups,
                                            },
                                        });
                                    }}
                                    renderValue={(selected: unknown): ReactNode => {
                                        const selectedIds = Array.isArray(selected)
                                            ? (selected as unknown[]).map((v) => String(v))
                                            : [];

                                        return (
                                            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                                                {selectedIds.map((groupId) => {
                                                    const group = availableGroups.find((g) => g.id === groupId);
                                                    return <Chip key={groupId} label={group?.name || groupId} size="small" />;
                                                })}
                                            </Box>
                                        );
                                    }}
                                >
                                    {availableGroups.map((group) => (
                                        <MenuItem key={group.id} value={group.id}>
                                            {group.name}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                        )}
                    </Card>

                    {/* Security Settings */}
                    <Card variant="outlined" sx={{ p: 3 }}>
                        <Typography variant="subtitle1" gutterBottom>
                            Security Settings
                        </Typography>
                        <Stack spacing={2}>
                            <FormControlLabel
                                control={
                                    <Checkbox
                                        checked={config.rollout?.fallbackToPassword || false}
                                        onChange={(e) =>
                                            setConfig({
                                                ...config,
                                                rollout: {
                                                    ...config.rollout!,
                                                    fallbackToPassword: e.target.checked,
                                                },
                                            })
                                        }
                                    />
                                }
                                label="Allow fallback to password authentication"
                            />
                            <FormControlLabel
                                control={
                                    <Checkbox
                                        checked={config.rollout?.requireMfa || false}
                                        onChange={(e) =>
                                            setConfig({
                                                ...config,
                                                rollout: {
                                                    ...config.rollout!,
                                                    requireMfa: e.target.checked,
                                                },
                                            })
                                        }
                                    />
                                }
                                label="Require MFA through SSO provider"
                            />

                            <TextField
                                fullWidth
                                type="number"
                                label="Session Timeout (minutes)"
                                value={config.rollout?.sessionTimeout || 480}
                                onChange={(e) =>
                                    setConfig({
                                        ...config,
                                        rollout: {
                                            ...config.rollout!,
                                            sessionTimeout: parseInt(e.target.value) || 480,
                                        },
                                    })
                                }
                                helperText="How long SSO sessions remain valid without re-authentication"
                            />
                        </Stack>
                    </Card>
                </Stack>
            </Box>
        );
    };

    // Step 5: Review & Confirm
    const renderReview = () => {
        return (
            <Box>
                <Typography variant="h6" gutterBottom>
                    Review & Confirm
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                    Review your SSO configuration before enabling it. You can modify settings later.
                </Typography>

                <Stack spacing={2}>
                    <Card variant="outlined" sx={{ p: 3 }}>
                        <Typography variant="subtitle1" gutterBottom>
                            Provider
                        </Typography>
                        <Typography variant="body1">
                            {config.provider === 'okta' && 'Okta'}
                            {config.provider === 'azure-ad' && 'Azure Active Directory'}
                            {config.provider === 'google' && 'Google Workspace'}
                            {config.provider === 'saml' && 'Generic SAML 2.0'}
                        </Typography>
                    </Card>

                    <Card variant="outlined" sx={{ p: 3 }}>
                        <Typography variant="subtitle1" gutterBottom>
                            Configuration
                        </Typography>
                        <Stack spacing={1}>
                            {config.provider === 'okta' && (
                                <>
                                    <Typography variant="body2">
                                        <strong>Domain:</strong> {config.okta?.domain}
                                    </Typography>
                                    <Typography variant="body2">
                                        <strong>Client ID:</strong> {config.okta?.clientId}
                                    </Typography>
                                </>
                            )}
                            {config.provider === 'azure-ad' && (
                                <>
                                    <Typography variant="body2">
                                        <strong>Tenant ID:</strong> {config.azureAd?.tenantId}
                                    </Typography>
                                    <Typography variant="body2">
                                        <strong>Client ID:</strong> {config.azureAd?.clientId}
                                    </Typography>
                                </>
                            )}
                            {config.provider === 'google' && (
                                <>
                                    <Typography variant="body2">
                                        <strong>Client ID:</strong> {config.google?.clientId}
                                    </Typography>
                                    {config.google?.hostedDomain && (
                                        <Typography variant="body2">
                                            <strong>Hosted Domain:</strong> {config.google.hostedDomain}
                                        </Typography>
                                    )}
                                </>
                            )}
                            {config.provider === 'saml' && (
                                <>
                                    <Typography variant="body2">
                                        <strong>Entity ID:</strong> {config.saml?.entityId}
                                    </Typography>
                                    <Typography variant="body2">
                                        <strong>SSO URL:</strong> {config.saml?.ssoUrl}
                                    </Typography>
                                    <Typography variant="body2">
                                        <strong>Email Attribute:</strong> {config.saml?.attributeMapping.email}
                                    </Typography>
                                </>
                            )}
                        </Stack>
                    </Card>

                    <Card variant="outlined" sx={{ p: 3 }}>
                        <Typography variant="subtitle1" gutterBottom>
                            Rollout Settings
                        </Typography>
                        <Stack spacing={1}>
                            <Typography variant="body2">
                                <strong>Enabled for:</strong>{' '}
                                {config.rollout?.enabledForAll
                                    ? 'All users'
                                    : `${config.rollout?.enabledGroups.length || 0} group(s)`}
                            </Typography>
                            <Typography variant="body2">
                                <strong>Password fallback:</strong>{' '}
                                {config.rollout?.fallbackToPassword ? 'Enabled' : 'Disabled'}
                            </Typography>
                            <Typography variant="body2">
                                <strong>Require MFA:</strong>{' '}
                                {config.rollout?.requireMfa ? 'Yes' : 'No'}
                            </Typography>
                            <Typography variant="body2">
                                <strong>Session timeout:</strong> {config.rollout?.sessionTimeout} minutes
                            </Typography>
                        </Stack>
                    </Card>

                    <Alert severity="info">
                        After enabling SSO, users in the selected groups will be required to authenticate
                        through {config.provider} on their next login.
                    </Alert>
                </Stack>
            </Box>
        );
    };

    // Render current step content
    const renderStepContent = () => {
        switch (activeStep) {
            case 0:
                return renderProviderSelection();
            case 1:
                return renderConfiguration();
            case 2:
                return renderTesting();
            case 3:
                return renderRolloutSettings();
            case 4:
                return renderReview();
            default:
                return null;
        }
    };

    return (
        <Box sx={{ maxWidth: 800, mx: 'auto', p: 3 }}>
            <Card sx={{ p: 4 }}>
                <Typography variant="h4" gutterBottom>
                    SSO Configuration
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
                    Configure Single Sign-On for your organization
                </Typography>

                <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
                    {steps.map((label) => (
                        <Step key={label}>
                            <StepLabel>{label}</StepLabel>
                        </Step>
                    ))}
                </Stepper>

                <Box sx={{ minHeight: 400 }}>{renderStepContent()}</Box>

                <Divider sx={{ my: 3 }} />

                <Stack direction="row" justifyContent="space-between">
                    <Button onClick={onCancel}>Cancel</Button>
                    <Stack direction="row" spacing={2}>
                        <Button onClick={handleBack} disabled={activeStep === 0}>
                            Back
                        </Button>
                        {activeStep === steps.length - 1 ? (
                            <Button variant="contained" onClick={handleComplete} disabled={!canProceed()}>
                                Enable SSO
                            </Button>
                        ) : (
                            <Button variant="contained" onClick={handleNext} disabled={!canProceed()}>
                                Next
                            </Button>
                        )}
                    </Stack>
                </Stack>
            </Card>
        </Box>
    );
};
