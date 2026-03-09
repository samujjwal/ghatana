/**
 * Root Dashboard
 *
 * Main entry point for Root User persona.
 * Provides overview of platform health, tenant status, and global user management.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Box,
    Card,
    Grid,
    Tab,
    Tabs,
    Typography,
} from '@ghatana/ui';
import { PlatformHealth } from '../../components/root/PlatformHealth';
import { TenantInspector } from '../../components/root/TenantInspector';
import { GlobalUserSearch } from '../../components/root/GlobalUserSearch';

export const RootDashboard: React.FC = () => {
    const [activeTab, setActiveTab] = useState<'health' | 'tenants' | 'users'>('health');

    const handleTabChange = (_event: React.SyntheticEvent, newValue: string) => {
        setActiveTab(newValue as any);
    };

    return (
        <Box className="p-6 max-w-7xl mx-auto">
            {/* Header */}
            <Box className="mb-6">
                <Typography variant="h4" className="font-bold text-slate-900 dark:text-neutral-100">
                    Platform Administration
                </Typography>
                <Typography variant="body1" className="text-slate-600 dark:text-neutral-400">
                    Global oversight and management for all tenants and users.
                </Typography>
            </Box>

            {/* Navigation */}
            <Card className="mb-6">
                <Tabs value={activeTab} onChange={handleTabChange}>
                    <Tab label="Platform Health" value="health" />
                    <Tab label="Tenant Management" value="tenants" />
                    <Tab label="Global Users" value="users" />
                </Tabs>
            </Card>

            {/* Content */}
            <Box>
                {activeTab === 'health' && <PlatformHealth />}
                {activeTab === 'tenants' && <TenantInspector />}
                {activeTab === 'users' && <GlobalUserSearch />}
            </Box>
        </Box>
    );
};

export default RootDashboard;
