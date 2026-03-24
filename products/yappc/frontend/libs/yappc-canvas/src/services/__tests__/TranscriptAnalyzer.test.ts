/**
 * TranscriptAnalyzer Tests
 * 
 * Comprehensive test suite for Transcript Analyzer (Journey 19.1)
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { TranscriptAnalyzer } from '../TranscriptAnalyzer';

describe('TranscriptAnalyzer', () => {
    let analyzer: TranscriptAnalyzer;

    beforeEach(() => {
        analyzer = new TranscriptAnalyzer();
    });

    describe('Quote Extraction', () => {
        it('should extract quotes from transcript', async () => {
            const transcript = `
                User 1: "I really love this feature, it makes my work so much easier."
                Interviewer: How do you feel about the interface?
                User 1: "The interface is confusing and difficult to navigate."
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.quotes.length).toBeGreaterThan(0);
            expect(analysis.quotes[0].text).toContain('love this feature');
        });

        it('should identify speakers', async () => {
            const transcript = `
                User 1: "This is a quote from user 1"
                Participant 2: "This is from participant 2"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.quotes).toHaveLength(2);
            expect(analysis.quotes[0].speaker).toContain('User');
            expect(analysis.quotes[1].speaker).toContain('Participant');
        });

        it('should handle different quote formats', async () => {
            const transcript = `
                User: "Double quotes work"
                User: "Curly quotes work"
                User: "Another format"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.quotes.length).toBeGreaterThan(0);
        });

        it('should skip very short quotes', async () => {
            const transcript = `
                User: "Yes"
                User: "No"
                User: "This is a longer quote that should be extracted"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.quotes.length).toBe(1);
            expect(analysis.quotes[0].text).toContain('longer quote');
        });
    });

    describe('Emotion Detection', () => {
        it('should detect positive emotions', async () => {
            const transcript = `
                User: "I'm so happy with this feature, it's great and very helpful!"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.emotions.some((e) => e.type === 'positive')).toBe(true);
        });

        it('should detect negative emotions', async () => {
            const transcript = `
                User: "This is frustrating and confusing. I hate this workflow."
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.emotions.some((e) => e.type === 'negative')).toBe(true);
        });

        it('should detect emotion in quotes', async () => {
            const transcript = `
                User: "I love how easy this is to use!"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.quotes[0].emotion).toBe('positive');
        });

        it('should detect negative emotion in quotes', async () => {
            const transcript = `
                User: "This is really frustrating and difficult to understand"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.quotes[0].emotion).toBe('negative');
        });
    });

    describe('Touchpoint Extraction', () => {
        it('should extract touchpoints from interactions', async () => {
            const transcript = `
                User: "I use the dashboard every day"
                User: "When I click the button, nothing happens"
                User: "I tried to open the settings menu"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.touchpoints.length).toBeGreaterThan(0);
            expect(analysis.touchpoints.some((tp) => tp.toLowerCase().includes('dashboard'))).toBe(true);
        });

        it('should capitalize touchpoints', async () => {
            const transcript = `User: "I use the dashboard"`;

            const analysis = await analyzer.analyzeTranscript(transcript);

            const dashboardTp = analysis.touchpoints.find((tp) => tp.toLowerCase().includes('dashboard'));
            expect(dashboardTp).toBeDefined();
            expect(dashboardTp?.[0]).toBe(dashboardTp?.[0].toUpperCase());
        });

        it('should filter out common words', async () => {
            const transcript = `
                User: "I use the and for with"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            const commonWords = ['the', 'and', 'for', 'with'];
            commonWords.forEach((word) => {
                expect(analysis.touchpoints.every((tp) => tp.toLowerCase() !== word)).toBe(true);
            });
        });

        it('should limit touchpoints to 10', async () => {
            const transcript = `
                I use app1, app2, app3, app4, app5, app6, app7, app8, app9, app10, app11, app12
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.touchpoints.length).toBeLessThanOrEqual(10);
        });
    });

    describe('Pain Point Detection', () => {
        it('should identify pain points from frustration keywords', async () => {
            const transcript = `
                User: "This is frustrating"
                User: "I find this confusing"
                User: "It's difficult to understand"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.painPoints.length).toBeGreaterThan(0);
        });

        it('should detect inability statements as pain points', async () => {
            const transcript = `
                User: "I can't find the button"
                User: "I couldn't figure out how to save"
                User: "I'm unable to complete this task"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.painPoints.length).toBeGreaterThan(0);
        });

        it('should mark quotes with pain indicators as pain points', async () => {
            const transcript = `
                User: "This is so frustrating and annoying"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.quotes[0].painPoint).toBe(true);
        });

        it('should detect pain point intensity', async () => {
            const highIntensity = `User: "This is extremely frustrating and really terrible!"`;
            const mediumIntensity = `User: "This is frustrating"`;
            const lowIntensity = `User: "This is a bit annoying"`;

            const high = await analyzer.analyzeTranscript(highIntensity);
            const medium = await analyzer.analyzeTranscript(mediumIntensity);
            const low = await analyzer.analyzeTranscript(lowIntensity);

            expect(high.quotes[0].intensity).toBe('high');
            expect(medium.quotes[0].intensity).toBe('medium');
        });

        it('should limit pain points to 5', async () => {
            const transcript = `
                Problem 1, problem 2, problem 3, problem 4, problem 5, problem 6, problem 7
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.painPoints.length).toBeLessThanOrEqual(5);
        });
    });

    describe('Structured Data', () => {
        it('should return structured data for UI', async () => {
            const transcript = `
                User 1: "I love the dashboard feature"
                User 1: "But the settings are confusing"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);
            const structured = analyzer.getStructuredData(analysis);

            expect(structured.touchpoints).toBeDefined();
            expect(structured.quotes).toBeDefined();
            expect(structured.painPoints).toBeDefined();
            expect(structured.emotions).toBeDefined();
        });

        it('should map quotes to structured format', async () => {
            const transcript = `User: "This is great!"`;

            const analysis = await analyzer.analyzeTranscript(transcript);
            const structured = analyzer.getStructuredData(analysis);

            expect(structured.quotes.length).toBeGreaterThan(0);
            expect(structured.quotes[0]).toHaveProperty('text');
            expect(structured.quotes[0]).toHaveProperty('speaker');
        });

        it('should include emotion in structured data', async () => {
            const transcript = `User: "I love this!"`;

            const analysis = await analyzer.analyzeTranscript(transcript);
            const structured = analyzer.getStructuredData(analysis);

            expect(structured.quotes[0].emotion).toBeDefined();
        });
    });

    describe('Summary Generation', () => {
        it('should generate analysis summary', async () => {
            const transcript = `
                User: "I use the app daily"
                User: "Sometimes it's frustrating"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.summary).toBeDefined();
            expect(typeof analysis.summary).toBe('string');
            expect(analysis.summary.length).toBeGreaterThan(0);
        });

        it('should include counts in summary', async () => {
            const transcript = `
                User: "Quote 1"
                User: "Quote 2"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.summary).toContain('2');
            expect(analysis.summary).toContain('quote');
        });
    });

    describe('Edge Cases', () => {
        it('should handle empty transcript', async () => {
            const analysis = await analyzer.analyzeTranscript('');

            expect(analysis.quotes).toHaveLength(0);
            expect(analysis.touchpoints).toHaveLength(0);
            expect(analysis.painPoints).toHaveLength(0);
        });

        it('should handle transcript with no quotes', async () => {
            const transcript = `
                This is a transcript without any quoted text.
                Just regular sentences.
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.quotes).toHaveLength(0);
        });

        it('should handle special characters in quotes', async () => {
            const transcript = `User: "Email: test@example.com, Phone: (123) 456-7890"`;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.quotes.length).toBeGreaterThan(0);
        });

        it('should handle multi-line quotes', async () => {
            const transcript = `
                User: "This is a quote
                that spans multiple
                lines in the transcript"
            `;

            const analysis = await analyzer.analyzeTranscript(transcript);

            expect(analysis.quotes.length).toBeGreaterThan(0);
        });
    });
});
