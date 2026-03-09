/**
 * Mobile Deploy Route
 * 
 * Deployment management for mobile devices.
 * Quick deploy actions with deployment history.
 * 
 * @doc.type route
 * @doc.purpose Mobile deployment interface
 * @doc.layer product
 */

import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router';
import { Capacitor } from '@capacitor/core';
import { Haptics, ImpactStyle } from '@capacitor/haptics';
import {
  Box,
  Typography,
  Button,
  Card,
  CardContent,
  ListItem,
  ListItemText,
  Chip,
  IconButton,
  Skeleton,
  Alert,
  LinearProgress,
  InteractiveList as List,
} from '@ghatana/ui';
import { Rocket, CheckCircle, AlertCircle as Error, Clock as Schedule, RefreshCw as Refresh, CloudUpload, History, Info } from 'lucide-react';

interface Deployment {
    id: string;
    environment: 'dev' | 'staging' | 'production';
    status: 'success' | 'failed' | 'deploying' | 'pending';
    version: string;
    timestamp: Date;
    duration?: number;
}

/**
 * Mobile Deploy Component - Simplified deployment interface
 */
export default function MobileDeployRoute() {
    const { projectId } = useParams();
    const navigate = useNavigate();
    const theme = useTheme();
    const [deployments, setDeployments] = useState<Deployment[]>([]);
    const [loading, setLoading] = useState(true);
    const [deploying, setDeploying] = useState(false);
    const [selectedEnv, setSelectedEnv] = useState<'dev' | 'staging' | 'production'>('dev');

    const isNative = Capacitor && typeof Capacitor.isNativePlatform === 'function'
        ? Capacitor.isNativePlatform()
        : false;

    useEffect(() => {
        loadDeployments();
    }, [projectId]);

    const loadDeployments = async () => {
        setLoading(true);
        try {
            // Simulate loading deployment data from API
            await new Promise(resolve => setTimeout(resolve, 800));
            setDeployments([
                {
                    id: '1',
                    environment: 'production',
                    status: 'success',
                    version: 'v1.2.3',
                    timestamp: new Date(Date.now() - 3600000),
                    duration: 120,
                },
                {
                    id: '2',
                    environment: 'staging',
                    status: 'success',
                    version: 'v1.2.4-beta',
                    timestamp: new Date(Date.now() - 7200000),
                    duration: 95,
                },
            ]);
        } catch (error) {
            console.error('Failed to load deployments:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleHapticFeedback = async () => {
        if (isNative) {
            try {
                await Haptics.impact({ style: ImpactStyle.Medium });
            } catch (error) {
                // Haptics not available
            }
        }
    };

    const handleDeploy = async () => {
        handleHapticFeedback();
        setDeploying(true);
        try {
            // Simulate deployment process
            await new Promise(resolve => setTimeout(resolve, 3000));
            await loadDeployments();
            alert(`Successfully deployed to ${selectedEnv}!`);
        } catch (error) {
            alert('Deployment failed. Please try again.');
        } finally {
            setDeploying(false);
        }
    };

    const getStatusIcon = (status: Deployment['status']) => {
        switch (status) {
            case 'success':
                return <CheckCircle tone="success" />;
            case 'failed':
                return <Error tone="danger" />;
            case 'deploying':
                return <Schedule tone="primary" />;
            default:
                return <Schedule color="action" />;
        }
    };

    const getStatusColor = (status: Deployment['status']): 'success' | 'error' | 'primary' | 'default' => {
        switch (status) {
            case 'success':
                return 'success';
            case 'failed':
                return 'error';
            case 'deploying':
                return 'primary';
            default:
                return 'default';
        }
    };

    if (loading) {
        return (
            <Box className="p-4">
                <Skeleton variant="rectangular" height={120} className="mb-4 rounded-lg" />
                <Skeleton variant="rectangular" height={200} className="rounded-lg" />
            </Box>
        );
    }

    return (
        <Box className="pb-6">
            {/* Deploy Card */}
            <Card className="m-4 mb-6">
                <CardContent>
                    <Typography as="h6" gutterBottom className="flex items-center gap-2">
                        <Rocket />
                        Quick Deploy
                    </Typography>

                    {/* Environment Selection */}
                    <Box className="flex gap-2 mb-4">
                        {(['dev', 'staging', 'production'] as const).map((env) => (
                            <Chip
                                key={env}
                                label={env.charAt(0).toUpperCase() + env.slice(1)}
                                onClick={() => setSelectedEnv(env)}
                                color={selectedEnv === env ? 'primary' : 'default'}
                                variant={selectedEnv === env ? 'filled' : 'outlined'}
                                className="flex-1"
                            />
                        ))}
                    </Box>

                    {/* Deploy Button */}
                    <Button
                        variant="solid"
                        fullWidth
                        size="lg"
                        startIcon={<CloudUpload />}
                        onClick={handleDeploy}
                        disabled={deploying}
                    >
                        {deploying ? 'Deploying...' : `Deploy to ${selectedEnv}`}
                    </Button>

                    {deploying && (
                        <LinearProgress className="mt-4" />
                    )}

                    {/* Warning for production */}
                    {selectedEnv === 'production' && !deploying && (
                        <Alert severity="warning" className="mt-4">
                            <Typography as="span" className="text-xs text-gray-500">
                                Production deployment requires approval
                            </Typography>
                        </Alert>
                    )}
                </CardContent>
            </Card>

            {/* Deployment History */}
            <Box className="mx-4">
                <Box className="flex items-center justify-between mb-4">
                    <Typography as="h6" className="flex items-center gap-2">
                        <History />
                        Recent Deployments
                    </Typography>
                    <IconButton size="sm" onClick={loadDeployments}>
                        <Refresh size={16} />
                    </IconButton>
                </Box>

                <List>
                    {deployments.length === 0 ? (
                        <Card>
                            <CardContent className="text-center py-8">
                                <Info className="mb-4 text-5xl opacity-[0.3]" />
                                <Typography as="p" className="text-sm" color="text.secondary">
                                    No deployments yet
                                </Typography>
                            </CardContent>
                        </Card>
                    ) : (
                        deployments.map((deployment) => (
                            <Card key={deployment.id} className="mb-3">
                                <ListItem>
                                    <Box className="flex items-start w-full gap-4">
                                        <Box className="mt-1">
                                            {getStatusIcon(deployment.status)}
                                        </Box>

                                        <Box className="flex-1 min-w-0">
                                            <ListItemText
                                                primary={
                                                    <Box className="flex items-center gap-2 mb-1">
                                                        <Typography as="p" className="text-sm font-medium" noWrap>
                                                            {deployment.environment.charAt(0).toUpperCase() + deployment.environment.slice(1)}
                                                        </Typography>
                                                        <Chip
                                                            label={deployment.status}
                                                            size="sm"
                                                            color={getStatusColor(deployment.status)}
                                                        />
                                                    </Box>
                                                }
                                                secondary={
                                                    <Box>
                                                        <Typography as="span" className="text-xs text-gray-500" display="block">
                                                            Version: {deployment.version}
                                                        </Typography>
                                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                            {deployment.timestamp.toLocaleString()}
                                                            {deployment.duration && ` • ${deployment.duration}s`}
                                                        </Typography>
                                                    </Box>
                                                }
                                            />
                                        </Box>
                                    </Box>
                                </ListItem>
                            </Card>
                        ))
                    )}
                </List>
            </Box>
        </Box>
    );
}
