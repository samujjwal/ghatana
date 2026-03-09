/**
 * Tenant Inspector Component
 *
 * List and inspect tenants.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
    Box,
    Card,
    Grid,
    Typography,
    Chip,
    Button,
    Progress,
    TextField,
} from '@ghatana/ui';

interface Tenant {
    id: string;
    name: string;
    status: string;
    key: string;
    plan: string;
    environmentCount: number;
    createdAt: string;
}

export const TenantInspector: React.FC = () => {
    const [search, setSearch] = useState('');
    const { data: tenants = [], isLoading } = useQuery<Tenant[]>({
        queryKey: ['/api/v1/root/tenants'],
        queryFn: async () => {
            const res = await fetch('/api/v1/root/tenants');
            if (!res.ok) throw new Error('Failed to fetch tenants');
            return res.json();
        },
    });

    const filteredTenants = tenants.filter(t =>
        t.name.toLowerCase().includes(search.toLowerCase()) ||
        t.key.toLowerCase().includes(search.toLowerCase())
    );

    if (isLoading) return <Progress variant="linear" value={0} indeterminate />;

    return (
        <Box>
            <Box className="flex justify-between items-center mb-4">
                <TextField
                    placeholder="Search tenants..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    className="w-full max-w-md"
                />
                <Button variant="contained" color="primary">
                    Create Tenant
                </Button>
            </Box>

            <Grid container spacing={3}>
                {filteredTenants.map((tenant) => (
                    <Grid item xs={12} md={6} lg={4} key={tenant.id}>
                        <Card className="h-full hover:shadow-md transition-shadow cursor-pointer">
                            <Box className="p-4">
                                <Box className="flex justify-between items-start mb-2">
                                    <Box>
                                        <Typography variant="h6" className="font-bold text-slate-900 dark:text-neutral-100">
                                            {tenant.name}
                                        </Typography>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                            {tenant.key} · {tenant.plan}
                                        </Typography>
                                    </Box>
                                    <Chip
                                        label={tenant.status || 'ACTIVE'}
                                        color={tenant.status === 'suspended' ? 'error' : 'success'}
                                        size="small"
                                    />
                                </Box>

                                <Box className="flex gap-4 mt-4">
                                    <Box>
                                        <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                                            {tenant.environmentCount}
                                        </Typography>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                            Environments
                                        </Typography>
                                    </Box>
                                </Box>

                                <Box className="mt-4 pt-4 border-t border-slate-100 dark:border-neutral-800 flex justify-end">
                                    <Button size="small" variant="text">
                                        Inspect Details
                                    </Button>
                                </Box>
                            </Box>
                        </Card>
                    </Grid>
                ))}
            </Grid>
        </Box>
    );
};
