import React from 'react';
import { Grid, Surface as Paper, Box } from '@ghatana/ui';
import { Skeleton } from '../common/SkeletonLoaders';

export function DashboardSkeleton() {
    return (
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8 animate-fade-in">
            {/* Header Section */}
            <div className="space-y-2">
                <Skeleton className="h-10 w-1/3" />
                <Skeleton className="h-6 w-1/2 opacity-60" />
            </div>

            {/* Input Section */}
            <Skeleton className="h-32 w-full rounded-2xl" />

            {/* Main Grid */}
            <Grid container spacing={6}>
                {/* Left Column: Tasks */}
                <Grid size={{ xs: 12, md: 6 }}>
                    <div className="mb-4 flex justify-between items-center">
                        <Skeleton className="h-8 w-40" />
                        <Skeleton className="h-8 w-20" />
                    </div>
                    <Paper variant="outlined" className="rounded-lg overflow-hidden">
                        {[1, 2, 3].map((i) => (
                            <Box key={i} className="p-4 border-gray-200 dark:border-gray-700" style={{ borderBottom: i < 3 ? 1 : 0 }}>
                                <div className="flex justify-between mb-2">
                                    <Skeleton className="h-6 w-3/4" />
                                    <Skeleton className="h-6 w-8" />
                                </div>
                                <div className="flex gap-2">
                                    <Skeleton className="h-4 w-1/4" />
                                    <Skeleton className="h-4 w-1/4" />
                                </div>
                            </Box>
                        ))}
                    </Paper>
                </Grid>

                {/* Right Column: Projects & Workflows */}
                <Grid size={{ xs: 12, md: 6 }}>
                    {/* Projects */}
                    <div className="mb-8">
                        <div className="mb-4 flex justify-between items-center">
                            <Skeleton className="h-8 w-40" />
                            <Skeleton className="h-8 w-24" />
                        </div>
                        <Grid container spacing={2}>
                            {[1, 2].map((i) => (
                                <Grid size={{ xs: 12, sm: 6 }} key={i}>
                                    <Paper variant="outlined" className="p-4 rounded-lg h-[140px]">
                                        <Skeleton className="h-6 w-1/2 mb-2" />
                                        <Skeleton className="h-4 w-full mb-4" />
                                        <Skeleton className="h-2 w-full mt-auto" />
                                    </Paper>
                                </Grid>
                            ))}
                        </Grid>
                    </div>

                    {/* Workflows */}
                    <div>
                        <div className="mb-4 flex justify-between items-center">
                            <Skeleton className="h-8 w-40" />
                            <Skeleton className="h-8 w-24" />
                        </div>
                        <Grid container spacing={2}>
                            {[1, 2].map((i) => (
                                <Grid size={{ xs: 12, sm: 6 }} key={i}>
                                    <Paper variant="outlined" className="p-4 rounded-lg h-[100px]">
                                        <div className="flex justify-between mb-2">
                                            <Skeleton className="h-6 w-1/2" />
                                            <Skeleton className="h-6 w-6 rounded-full" />
                                        </div>
                                        <Skeleton className="h-4 w-3/4" />
                                    </Paper>
                                </Grid>
                            ))}
                        </Grid>
                    </div>
                </Grid>
            </Grid>
        </div>
    );
}
