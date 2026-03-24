/**
 * @doc.type service
 * @doc.purpose AI-powered transcript analysis for extracting user quotes and insights (Journey 19.1)
 * @doc.layer product
 * @doc.pattern Service
 */

import type { EmotionType } from '../components/UserJourneyCanvas';

/**
 * Pain point intensity levels (for heatmap)
 */
export type PainPointIntensity = 'low' | 'medium' | 'high';

/**
 * Extracted quote from transcript
 */
export interface ExtractedQuote {
    text: string;
    speaker: string;
    timestamp?: string;
    emotion?: EmotionType;
    painPoint?: boolean;
    intensity?: PainPointIntensity;
    touchpointLabel?: string;
}

/**
 * Analysis result from transcript
 */
export interface TranscriptAnalysis {
    quotes: ExtractedQuote[];
    touchpoints: string[];
    painPoints: string[];
    emotions: Array<{ label: string; type: EmotionType }>;
    summary: string;
}

/**
 * TranscriptAnalyzer Service
 * 
 * AI-powered service for analyzing interview transcripts and extracting:
 * - User quotes with speaker attribution
 * - Touchpoints (interaction moments)
 * - Pain points with intensity
 * - Emotional states
 * - Journey insights
 */
export class TranscriptAnalyzer {
    /**
     * Analyze transcript and extract structured data
     */
    async analyzeTranscript(transcript: string): Promise<TranscriptAnalysis> {
        // In production, this would call an AI service (OpenAI, Anthropic, etc.)
        // For now, we'll implement a basic pattern-matching approach

        const lines = transcript.split('\n').filter((line) => line.trim());
        const quotes: ExtractedQuote[] = [];
        const touchpoints = new Set<string>();
        const painPoints = new Set<string>();
        const emotions: Array<{ label: string; type: EmotionType }> = [];

        // Extract quotes (look for quoted text)
        const quotePattern = /[""]([^"""]+)[""]|"([^"]+)"/g;
        let match;

        while ((match = quotePattern.exec(transcript)) !== null) {
            const quoteText = match[1] || match[2];
            if (quoteText && quoteText.length > 10) {
                // Try to find speaker (look for "User X:", "Participant:", etc.)
                const speakerMatch = transcript
                    .slice(Math.max(0, match.index - 50), match.index)
                    .match(/(?:User|Participant|Interviewee|P\d+|U\d+)\s*(?:\d+)?:/i);

                const speaker = speakerMatch ? speakerMatch[0].replace(':', '').trim() : 'User';

                const quote: ExtractedQuote = {
                    text: quoteText.trim(),
                    speaker,
                    emotion: this.detectEmotion(quoteText),
                    painPoint: this.isPainPoint(quoteText),
                };

                if (quote.painPoint) {
                    quote.intensity = this.detectIntensity(quoteText);
                }

                quotes.push(quote);
            }
        }

        // Extract touchpoints (interaction verbs + objects)
        const touchpointPatterns = [
            /(?:use|uses|used|using)\s+(?:the\s+)?(\w+(?:\s+\w+)?)/gi,
            /(?:click|clicked|clicking|tap|tapped|tapping)\s+(?:on\s+)?(?:the\s+)?(\w+(?:\s+\w+)?)/gi,
            /(?:open|opened|opening|access|accessed|accessing)\s+(?:the\s+)?(\w+(?:\s+\w+)?)/gi,
            /(?:navigate|navigated|navigating|go|went|going)\s+to\s+(?:the\s+)?(\w+(?:\s+\w+)?)/gi,
        ];

        for (const pattern of touchpointPatterns) {
            let touchpointMatch;
            while ((touchpointMatch = pattern.exec(transcript)) !== null) {
                const touchpoint = touchpointMatch[1].trim();
                if (touchpoint.length > 2 && !this.isCommonWord(touchpoint)) {
                    touchpoints.add(this.capitalize(touchpoint));
                }
            }
        }

        // Extract pain points (frustration indicators)
        const painPointPatterns = [
            /(?:frustrat|annoying|confusing|difficult|hard|struggle|problem|issue|error)\w*/gi,
            /(?:can't|cannot|couldn't|unable)\s+\w+/gi,
            /(?:wish|hope|want|need)\s+(?:I\s+)?could/gi,
        ];

        for (const pattern of painPointPatterns) {
            let painMatch;
            while ((painMatch = pattern.exec(transcript)) !== null) {
                const context = transcript.slice(
                    Math.max(0, painMatch.index - 30),
                    Math.min(transcript.length, painMatch.index + 50)
                );
                painPoints.add(context.trim());
            }
        }

        // Extract emotions
        const emotionKeywords = {
            positive: ['happy', 'love', 'great', 'excellent', 'easy', 'helpful', 'enjoy', 'pleased'],
            negative: ['frustrat', 'annoying', 'hate', 'terrible', 'difficult', 'confusing', 'struggle'],
            neutral: ['okay', 'fine', 'neutral', 'normal'],
        };

        for (const [emotionType, keywords] of Object.entries(emotionKeywords)) {
            for (const keyword of keywords) {
                const regex = new RegExp(keyword, 'gi');
                if (regex.test(transcript)) {
                    emotions.push({
                        label: keyword,
                        type: emotionType as EmotionType,
                    });
                }
            }
        }

        return {
            quotes,
            touchpoints: Array.from(touchpoints).slice(0, 10),
            painPoints: Array.from(painPoints).slice(0, 5),
            emotions: emotions.slice(0, 5),
            summary: this.generateSummary(quotes.length, touchpoints.size, painPoints.size),
        };
    }

    /**
     * Get structured data for UI consumption
     * Returns analysis that can be used to populate journey canvas
     */
    getStructuredData(analysis: TranscriptAnalysis): {
        touchpoints: Array<{ label: string; description: string }>;
        quotes: Array<{ text: string; speaker: string; emotion?: EmotionType }>;
        painPoints: Array<{ description: string; intensity: PainPointIntensity }>;
        emotions: Array<{ label: string; type: EmotionType }>;
    } {
        return {
            touchpoints: analysis.touchpoints.map((tp) => ({
                label: tp,
                description: 'User interaction point',
            })),
            quotes: analysis.quotes.map((q) => ({
                text: q.text,
                speaker: q.speaker,
                emotion: q.emotion,
            })),
            painPoints: analysis.painPoints.map((pp, index) => ({
                description: pp,
                intensity: (analysis.quotes[index]?.intensity || 'medium') as PainPointIntensity,
            })),
            emotions: analysis.emotions,
        };
    }

    /**
     * Detect emotion from text
     */
    private detectEmotion(text: string): EmotionType {
        const positiveWords = ['happy', 'love', 'great', 'excellent', 'easy', 'helpful', 'enjoy'];
        const negativeWords = ['frustrat', 'annoying', 'hate', 'terrible', 'difficult', 'confusing'];

        const lowerText = text.toLowerCase();

        const hasPositive = positiveWords.some((word) => lowerText.includes(word));
        const hasNegative = negativeWords.some((word) => lowerText.includes(word));

        if (hasPositive && !hasNegative) return 'positive';
        if (hasNegative && !hasPositive) return 'negative';
        return 'neutral';
    }

    /**
     * Check if text indicates a pain point
     */
    private isPainPoint(text: string): boolean {
        const painIndicators = [
            'frustrat',
            'annoying',
            'confusing',
            'difficult',
            'hard',
            'struggle',
            'problem',
            'issue',
            "can't",
            'cannot',
            'unable',
        ];

        const lowerText = text.toLowerCase();
        return painIndicators.some((indicator) => lowerText.includes(indicator));
    }

    /**
     * Detect intensity of pain point
     */
    private detectIntensity(text: string): PainPointIntensity {
        const highIntensityWords = ['very', 'extremely', 'really', 'terrible', 'impossible', 'hate'];
        const lowerText = text.toLowerCase();

        if (highIntensityWords.some((word) => lowerText.includes(word))) {
            return 'high';
        }

        const mediumIntensityWords = ['difficult', 'frustrating', 'annoying'];
        if (mediumIntensityWords.some((word) => lowerText.includes(word))) {
            return 'medium';
        }

        return 'low';
    }

    /**
     * Check if word is too common to be a touchpoint
     */
    private isCommonWord(word: string): boolean {
        const commonWords = [
            'the',
            'and',
            'for',
            'with',
            'this',
            'that',
            'from',
            'have',
            'has',
            'had',
            'was',
            'were',
            'been',
            'being',
        ];
        return commonWords.includes(word.toLowerCase());
    }

    /**
     * Capitalize first letter
     */
    private capitalize(text: string): string {
        return text.charAt(0).toUpperCase() + text.slice(1);
    }

    /**
     * Generate analysis summary
     */
    private generateSummary(quoteCount: number, touchpointCount: number, painPointCount: number): string {
        return `Extracted ${quoteCount} quotes, identified ${touchpointCount} touchpoints, and ${painPointCount} pain points from the transcript.`;
    }
}
