/**
 * Innovation Tracker
 *
 * @doc.type route
 * @doc.section OBSERVE
 */

import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { MainLayout } from '@/app/Layout';
import {
    InnovationTracker,
    mockInnovationData,
    type InnovationMetrics,
    type Idea as UiIdea,
    type Experiment as UiExperiment,
} from '@/components/cross-functional/InnovationTracker';

type ApiIdea = {
    id: string;
    title: string;
    description: string;
    votes: number;
    status: string;
    author: { id: string; name: string | null };
    createdAt: string;
    updatedAt: string;
};

type ApiExperiment = {
    id: string;
    ideaId: string;
    title: string;
    status: string;
    progress: number;
    startDate: string | null;
    endDate: string | null;
    createdAt: string;
    updatedAt: string;
};

export default function InnovationTrackerRoute() {
    const { data: ideasApi } = useQuery<ApiIdea[]>({
        queryKey: ['innovation', 'ideas'],
        queryFn: async () => {
            const response = await fetch('/api/v1/innovation/ideas');
            if (!response.ok) throw new Error('Failed to load ideas');
            return response.json();
        },
    });

    const { data: experimentsApi } = useQuery<ApiExperiment[]>({
        queryKey: ['innovation', 'experiments'],
        queryFn: async () => {
            const response = await fetch('/api/v1/innovation/experiments');
            if (!response.ok) throw new Error('Failed to load experiments');
            return response.json();
        },
    });

    const viewModel = useMemo(() => {
        if (!ideasApi && !experimentsApi) return mockInnovationData;

        const ideas: UiIdea[] = (ideasApi ?? []).map((i) => ({
            id: i.id,
            title: i.title,
            description: i.description,
            submitter: i.author?.name ?? 'Unknown',
            team: '—',
            submittedDate: i.createdAt,
            votes: i.votes,
            status: i.status === 'review' ? 'under-review' : (i.status as UiIdea['status']),
            category: 'technology',
            potentialImpact: 'medium',
        }));

        const experiments: UiExperiment[] = (experimentsApi ?? []).map((e) => ({
            id: e.id,
            ideaId: e.ideaId,
            title: e.title,
            hypothesis: '',
            owner: '—',
            startDate: e.startDate ?? e.createdAt,
            endDate: e.endDate ?? e.updatedAt,
            progress: e.progress,
            status: e.status === 'completed' ? 'analyzing' : (e.status as UiExperiment['status']),
            successCriteria: '',
            resources: [],
        }));

        const activeExperiments = experiments.filter((e) => e.status === 'in-progress').length;

        const metrics: InnovationMetrics = {
            totalIdeas: ideas.length,
            activeExperiments,
            successRate: 0,
            impactValue: '—',
        };

        return {
            ...mockInnovationData,
            metrics,
            ideas,
            experiments,
        };
    }, [ideasApi, experimentsApi]);

    return (
        <MainLayout>
            <div className="p-6">
                <InnovationTracker {...viewModel} />
            </div>
        </MainLayout>
    );
}
