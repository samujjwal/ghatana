/**
 * useUserJourney Tests
 * 
 * Tests for user journey hook
 * 
 * @doc.type test
 * @doc.purpose useUserJourney hook tests
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useUserJourney } from '../useUserJourney';
import type { EmotionType } from '../useUserJourney';

describe('useUserJourney', () => {
    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useUserJourney());

            expect(result.current.stages).toEqual([]);
            expect(result.current.journeyName).toBe('Customer Journey');
            expect(result.current.persona).toBe('End User');
        });

        it('should initialize with custom values', () => {
            const { result } = renderHook(() => useUserJourney({
                initialJourneyName: 'E-commerce Journey',
                initialPersona: 'Online Shopper',
            }));

            expect(result.current.journeyName).toBe('E-commerce Journey');
            expect(result.current.persona).toBe('Online Shopper');
        });
    });

    describe('Stage Management', () => {
        it('should add stage', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Awareness');
            });

            expect(result.current.stages).toHaveLength(1);
            expect(result.current.stages[0]).toMatchObject({
                name: 'Awareness',
            });
            expect(stageId!).toBeDefined();
        });

        it('should add stage with description', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                result.current.addStage('Awareness', 'User discovers the product');
            });

            expect(result.current.stages[0]).toMatchObject({
                name: 'Awareness',
                description: 'User discovers the product',
            });
        });

        it('should add multiple stages', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                result.current.addStage('Awareness');
                result.current.addStage('Consideration');
                result.current.addStage('Purchase');
            });

            expect(result.current.stages).toHaveLength(3);
            expect(result.current.stages[0].name).toBe('Awareness');
            expect(result.current.stages[1].name).toBe('Consideration');
            expect(result.current.stages[2].name).toBe('Purchase');
        });

        it('should update stage', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Awareness');
            });

            act(() => {
                result.current.updateStage(stageId!, {
                    name: 'Discovery',
                    description: 'Updated description',
                });
            });

            expect(result.current.stages[0]).toMatchObject({
                name: 'Discovery',
                description: 'Updated description',
            });
        });

        it('should delete stage', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Awareness');
                result.current.addStage('Consideration');
            });

            act(() => {
                result.current.deleteStage(stageId!);
            });

            expect(result.current.stages).toHaveLength(1);
            expect(result.current.stages[0].name).toBe('Consideration');
        });

        it('should get stage by ID', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Awareness');
            });

            const stage = result.current.getStage(stageId!);

            expect(stage).toBeDefined();
            expect(stage?.name).toBe('Awareness');
        });
    });

    describe('Touchpoint Management', () => {
        it('should add touchpoint to stage', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Awareness');
            });

            act(() => {
                result.current.addTouchpoint(stageId!, {
                    name: 'Homepage',
                    type: 'digital',
                    channel: 'Website',
                });
            });

            const stage = result.current.stages[0];
            expect(stage.touchpoints).toHaveLength(1);
            expect(stage.touchpoints![0]).toMatchObject({
                name: 'Homepage',
                type: 'digital',
                channel: 'Website',
            });
        });

        it('should add multiple touchpoints', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Awareness');
                result.current.addTouchpoint(stageId!, {
                    name: 'Homepage',
                    type: 'digital',
                });
                result.current.addTouchpoint(stageId!, {
                    name: 'Store',
                    type: 'physical',
                });
            });

            const stage = result.current.stages[0];
            expect(stage.touchpoints).toHaveLength(2);
        });

        it('should delete touchpoint', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Awareness');
                result.current.addTouchpoint(stageId!, {
                    name: 'Homepage',
                    type: 'digital',
                });
            });

            const touchpointId = result.current.stages[0].touchpoints![0].id;

            act(() => {
                result.current.deleteTouchpoint(stageId!, touchpointId);
            });

            expect(result.current.stages[0].touchpoints).toHaveLength(0);
        });

        it('should get touchpoint count', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                const stage1 = result.current.addStage('Awareness');
                const stage2 = result.current.addStage('Consideration');
                result.current.addTouchpoint(stage1, { name: 'Homepage', type: 'digital' });
                result.current.addTouchpoint(stage1, { name: 'Email', type: 'digital' });
                result.current.addTouchpoint(stage2, { name: 'Store', type: 'physical' });
            });

            expect(result.current.getTouchpointCount()).toBe(3);
        });
    });

    describe('Pain Point Management', () => {
        it('should add pain point to stage', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Checkout');
            });

            act(() => {
                result.current.addPainPoint(stageId!, {
                    description: 'Shipping cost surprise',
                    severity: 3,
                    category: 'Pricing',
                });
            });

            const stage = result.current.stages[0];
            expect(stage.painPoints).toHaveLength(1);
            expect(stage.painPoints![0]).toMatchObject({
                description: 'Shipping cost surprise',
                severity: 3,
                category: 'Pricing',
            });
        });

        it('should add pain point without category', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Checkout');
                result.current.addPainPoint(stageId!, {
                    description: 'Form validation errors',
                    severity: 2,
                });
            });

            const stage = result.current.stages[0];
            expect(stage.painPoints![0].category).toBeUndefined();
        });

        it('should delete pain point', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Checkout');
                result.current.addPainPoint(stageId!, {
                    description: 'Issue',
                    severity: 1,
                });
            });

            const painPointId = result.current.stages[0].painPoints![0].id;

            act(() => {
                result.current.deletePainPoint(stageId!, painPointId);
            });

            expect(result.current.stages[0].painPoints).toHaveLength(0);
        });

        it('should get pain point count', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                const stage1 = result.current.addStage('Awareness');
                const stage2 = result.current.addStage('Checkout');
                result.current.addPainPoint(stage1, { description: 'Issue 1', severity: 1 });
                result.current.addPainPoint(stage2, { description: 'Issue 2', severity: 2 });
                result.current.addPainPoint(stage2, { description: 'Issue 3', severity: 3 });
            });

            expect(result.current.getPainPointCount()).toBe(3);
        });
    });

    describe('Emotion Management', () => {
        it('should add emotion to stage', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Purchase');
            });

            act(() => {
                result.current.addEmotion(stageId!, 'positive');
            });

            const stage = result.current.stages[0];
            expect(stage.emotions).toHaveLength(1);
            expect(stage.emotions![0].type).toBe('positive');
        });

        it('should add multiple emotions', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Purchase');
                result.current.addEmotion(stageId!, 'positive');
                result.current.addEmotion(stageId!, 'very-positive');
            });

            const stage = result.current.stages[0];
            expect(stage.emotions).toHaveLength(2);
        });

        it('should delete emotion', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Purchase');
                result.current.addEmotion(stageId!, 'neutral');
            });

            const emotionId = result.current.stages[0].emotions![0].id;

            act(() => {
                result.current.deleteEmotion(stageId!, emotionId);
            });

            expect(result.current.stages[0].emotions).toHaveLength(0);
        });

        it.each<EmotionType>([
            'very-negative',
            'negative',
            'neutral',
            'positive',
            'very-positive',
        ])('should handle %s emotion', (emotionType) => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Test Stage');
                result.current.addEmotion(stageId!, emotionType);
            });

            expect(result.current.stages[0].emotions![0].type).toBe(emotionType);
        });
    });

    describe('User Quote Management', () => {
        it('should add user quote to stage', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Support');
            });

            act(() => {
                result.current.addUserQuote(stageId!, {
                    text: 'The support team was very helpful',
                    source: 'User Interview #5',
                });
            });

            const stage = result.current.stages[0];
            expect(stage.userQuotes).toHaveLength(1);
            expect(stage.userQuotes![0]).toMatchObject({
                text: 'The support team was very helpful',
                source: 'User Interview #5',
            });
        });

        it('should add quote without source', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Support');
                result.current.addUserQuote(stageId!, {
                    text: 'Great experience',
                });
            });

            const stage = result.current.stages[0];
            expect(stage.userQuotes![0].source).toBeUndefined();
        });

        it('should delete user quote', () => {
            const { result } = renderHook(() => useUserJourney());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage('Support');
                result.current.addUserQuote(stageId!, {
                    text: 'Test quote',
                });
            });

            const quoteId = result.current.stages[0].userQuotes![0].id;

            act(() => {
                result.current.deleteUserQuote(stageId!, quoteId);
            });

            expect(result.current.stages[0].userQuotes).toHaveLength(0);
        });

        it('should get quote count', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                const stage1 = result.current.addStage('Stage 1');
                const stage2 = result.current.addStage('Stage 2');
                result.current.addUserQuote(stage1, { text: 'Quote 1' });
                result.current.addUserQuote(stage2, { text: 'Quote 2' });
            });

            expect(result.current.getQuoteCount()).toBe(2);
        });
    });

    describe('Transcript Analysis', () => {
        it('should analyze transcript with pain keywords', async () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                result.current.addStage('Test Stage');
            });

            await act(async () => {
                await result.current.analyzeTranscript(
                    'This is very frustrated experience\nThe process is difficult and confusing'
                );
            });

            const stage = result.current.stages[0];
            expect(stage.painPoints!.length).toBeGreaterThan(0);
        });

        it('should analyze transcript with positive keywords', async () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                result.current.addStage('Test Stage');
            });

            await act(async () => {
                await result.current.analyzeTranscript(
                    'I love this feature\nIt is great and very easy to use'
                );
            });

            const stage = result.current.stages[0];
            expect(stage.userQuotes!.length).toBeGreaterThan(0);
        });

        it('should add emotions based on sentiment', async () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                result.current.addStage('Test Stage');
            });

            await act(async () => {
                await result.current.analyzeTranscript('Great experience overall');
            });

            const stage = result.current.stages[0];
            expect(stage.emotions!.length).toBeGreaterThan(0);
        });
    });

    describe('Heatmap Generation', () => {
        it('should generate heatmap data', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                const stage1 = result.current.addStage('Stage 1');
                const stage2 = result.current.addStage('Stage 2');
                result.current.addPainPoint(stage1, { description: 'Pain 1', severity: 3 });
                result.current.addPainPoint(stage2, { description: 'Pain 2', severity: 1 });
            });

            const heatmap = result.current.generateHeatmap();

            expect(heatmap).toHaveLength(2);
            expect(heatmap[0]).toHaveProperty('stageId');
            expect(heatmap[0]).toHaveProperty('intensity');
            expect(heatmap[0].intensity).toBeGreaterThan(heatmap[1].intensity);
        });

        it('should return zero intensity for stages without pain points', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                result.current.addStage('Happy Stage');
            });

            const heatmap = result.current.generateHeatmap();

            expect(heatmap[0].intensity).toBe(0);
        });
    });

    describe('Journey Export', () => {
        it('should export journey as JSON', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                const stage = result.current.addStage('Awareness', 'Discovery phase');
                result.current.addTouchpoint(stage, { name: 'Homepage', type: 'digital' });
                result.current.addPainPoint(stage, { description: 'Slow load time', severity: 2 });
            });

            const exported = result.current.exportJourney();
            const parsed = JSON.parse(exported);

            expect(parsed).toHaveProperty('name', 'Customer Journey');
            expect(parsed).toHaveProperty('persona', 'End User');
            expect(parsed.stages).toHaveLength(1);
            expect(parsed.stages[0]).toHaveProperty('name', 'Awareness');
            expect(parsed.metadata).toHaveProperty('touchpointCount', 1);
            expect(parsed.metadata).toHaveProperty('painPointCount', 1);
        });

        it('should include metadata in export', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                result.current.addStage('Test');
            });

            const exported = result.current.exportJourney();
            const parsed = JSON.parse(exported);

            expect(parsed.metadata).toHaveProperty('exportedAt');
            expect(parsed.metadata).toHaveProperty('touchpointCount');
            expect(parsed.metadata).toHaveProperty('painPointCount');
            expect(parsed.metadata).toHaveProperty('quoteCount');
        });
    });

    describe('Journey Configuration', () => {
        it('should update journey name', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                result.current.setJourneyName('New Journey Name');
            });

            expect(result.current.journeyName).toBe('New Journey Name');
        });

        it('should update persona', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                result.current.setPersona('Mobile User');
            });

            expect(result.current.persona).toBe('Mobile User');
        });
    });

    describe('Complex Scenarios', () => {
        it('should handle complete journey with all elements', () => {
            const { result } = renderHook(() => useUserJourney());

            act(() => {
                // Stage 1: Awareness
                const awareness = result.current.addStage('Awareness', 'Discovery phase');
                result.current.addTouchpoint(awareness, { name: 'Google Search', type: 'digital' });
                result.current.addTouchpoint(awareness, { name: 'Social Media', type: 'digital' });
                result.current.addEmotion(awareness, 'neutral');

                // Stage 2: Consideration
                const consideration = result.current.addStage('Consideration', 'Evaluation phase');
                result.current.addTouchpoint(consideration, { name: 'Product Page', type: 'digital' });
                result.current.addPainPoint(consideration, {
                    description: 'Too many options',
                    severity: 2,
                    category: 'Usability',
                });
                result.current.addEmotion(consideration, 'negative');

                // Stage 3: Purchase
                const purchase = result.current.addStage('Purchase', 'Checkout phase');
                result.current.addTouchpoint(purchase, { name: 'Checkout', type: 'digital' });
                result.current.addPainPoint(purchase, {
                    description: 'Unexpected shipping cost',
                    severity: 3,
                    category: 'Pricing',
                });
                result.current.addUserQuote(purchase, {
                    text: 'I almost abandoned the cart due to high shipping',
                    source: 'User Interview #12',
                });
                result.current.addEmotion(purchase, 'very-negative');
            });

            expect(result.current.stages).toHaveLength(3);
            expect(result.current.getTouchpointCount()).toBe(4);
            expect(result.current.getPainPointCount()).toBe(2);
            expect(result.current.getQuoteCount()).toBe(1);
        });

        it('should maintain data integrity after multiple operations', () => {
            const { result } = renderHook(() => useUserJourney());

            let stage1Id: string, stage2Id: string;
            act(() => {
                stage1Id = result.current.addStage('Stage 1');
                stage2Id = result.current.addStage('Stage 2');
                result.current.addTouchpoint(stage1Id!, { name: 'TP1', type: 'digital' });
                result.current.addTouchpoint(stage2Id!, { name: 'TP2', type: 'physical' });
            });

            act(() => {
                result.current.deleteStage(stage1Id!);
            });

            expect(result.current.stages).toHaveLength(1);
            expect(result.current.getTouchpointCount()).toBe(1);
            expect(result.current.stages[0].touchpoints![0].name).toBe('TP2');
        });
    });
});
