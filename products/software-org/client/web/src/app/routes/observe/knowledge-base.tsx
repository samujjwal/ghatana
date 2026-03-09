/**
 * Knowledge Base
 *
 * @doc.type route
 * @doc.section OBSERVE
 */

import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { MainLayout } from '@/app/Layout';
import {
    KnowledgeBase,
    mockKnowledgeBaseData,
    type KnowledgeArticle,
    type KnowledgeMetrics,
} from '@/components/cross-functional/KnowledgeBase';

type ApiArticle = {
    id: string;
    title: string;
    content: string;
    summary: string | null;
    category: string;
    tags: string[];
    author: { id: string; name: string | null; email: string };
    views: number;
    likes: number;
    isPublished: boolean;
    createdAt: string;
    updatedAt: string;
};

const estimateReadTimeMinutes = (content: string): number => {
    const wordCount = content.trim().split(/\s+/).filter(Boolean).length;
    return Math.max(1, Math.ceil(wordCount / 200));
};

export default function KnowledgeBaseRoute() {
    const { data: apiArticles } = useQuery<ApiArticle[]>({
        queryKey: ['knowledge', 'articles'],
        queryFn: async () => {
            const response = await fetch('/api/v1/knowledge/articles');
            if (!response.ok) throw new Error('Failed to load articles');
            return response.json();
        },
    });

    const viewModel = useMemo(() => {
        if (!apiArticles) return mockKnowledgeBaseData;

        const articles: KnowledgeArticle[] = apiArticles.map((a) => ({
            id: a.id,
            title: a.title,
            summary: a.summary ?? '',
            category: a.category,
            tags: a.tags,
            author: a.author?.name ?? 'Unknown',
            authorRole: '',
            createdDate: a.createdAt,
            lastUpdated: a.updatedAt,
            views: a.views,
            likes: a.likes,
            version: 1,
            status: a.isPublished ? 'published' : 'draft',
            readTime: estimateReadTimeMinutes(a.content),
            difficulty: 'beginner',
        }));

        const now = Date.now();
        const thirtyDaysMs = 30 * 24 * 60 * 60 * 1000;
        const recentUpdates = articles.filter((a) => {
            const updatedAtMs = new Date(a.lastUpdated).getTime();
            return Number.isFinite(updatedAtMs) && now - updatedAtMs <= thirtyDaysMs;
        }).length;

        const activeContributors = new Set(articles.map((a) => a.author)).size;
        const popularTopics = new Set(articles.map((a) => a.category)).size;

        const metrics: KnowledgeMetrics = {
            totalArticles: articles.length,
            recentUpdates,
            popularTopics,
            activeContributors,
        };

        return {
            ...mockKnowledgeBaseData,
            metrics,
            articles,
        };
    }, [apiArticles]);

    return (
        <MainLayout>
            <div className="p-6">
                <KnowledgeBase {...viewModel} />
            </div>
        </MainLayout>
    );
}
