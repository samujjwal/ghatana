/**
 * Browser Sound Notification Utility
 * Play audio alerts for critical events
 */

export type SoundType = 'critical' | 'high' | 'medium' | 'info' | 'success' | 'error';

export interface SoundNotificationOptions {
    volume?: number; // 0.0 to 1.0
    loop?: boolean;
    duration?: number; // milliseconds
}

/**
 * Sound notification manager
 */
export class SoundNotificationManager {
    private audioContext: AudioContext | null = null;
    private enabled: boolean = true;
    private volume: number = 0.7;
    private sounds: Map<SoundType, string> = new Map();

    constructor() {
        this.initializeSounds();
    }

    /**
     * Initialize sound URLs (can be local files or base64 data URLs)
     */
    private initializeSounds(): void {
        // Using Web Audio API to generate tones
        // In production, replace with actual audio file URLs
        this.sounds.set('critical', this.generateToneDataUrl(800, 200, 3)); // Urgent triple beep
        this.sounds.set('high', this.generateToneDataUrl(600, 200, 2)); // Double beep
        this.sounds.set('medium', this.generateToneDataUrl(500, 150, 1)); // Single beep
        this.sounds.set('info', this.generateToneDataUrl(400, 100, 1)); // Soft beep
        this.sounds.set('success', this.generateToneDataUrl(600, 100, 1)); // Success tone
        this.sounds.set('error', this.generateToneDataUrl(300, 200, 1)); // Error tone
    }

    /**
     * Generate a simple tone as data URL (placeholder)
     */
    private generateToneDataUrl(frequency: number, duration: number, count: number): string {
        // This is a placeholder - in production, use actual audio files
        // For now, we'll play tones programmatically
        return `tone:${frequency}:${duration}:${count}`;
    }

    /**
     * Play notification sound
     */
    async playSound(type: SoundType, options: SoundNotificationOptions = {}): Promise<void> {
        if (!this.enabled) {
            console.log('[Sound] Notifications are disabled');
            return;
        }

        const soundUrl = this.sounds.get(type);
        if (!soundUrl) {
            console.warn(`[Sound] No sound configured for type: ${type}`);
            return;
        }

        try {
            // Check if it's a tone definition
            if (soundUrl.startsWith('tone:')) {
                await this.playTone(soundUrl, options);
            } else {
                await this.playAudioFile(soundUrl, options);
            }
        } catch (error) {
            console.error('[Sound] Failed to play notification:', error);
        }
    }

    /**
     * Play a programmatically generated tone
     */
    private async playTone(
        toneDefinition: string,
        options: SoundNotificationOptions,
    ): Promise<void> {
        const [, frequency, duration, count] = toneDefinition.split(':').map(Number);

        if (!this.audioContext) {
            this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
        }

        const ctx = this.audioContext;
        const volume = options.volume ?? this.volume;

        for (let i = 0; i < count; i++) {
            const oscillator = ctx.createOscillator();
            const gainNode = ctx.createGain();

            oscillator.connect(gainNode);
            gainNode.connect(ctx.destination);

            oscillator.frequency.value = frequency;
            oscillator.type = 'sine';

            gainNode.gain.setValueAtTime(0, ctx.currentTime);
            gainNode.gain.linearRampToValueAtTime(volume, ctx.currentTime + 0.01);
            gainNode.gain.linearRampToValueAtTime(0, ctx.currentTime + duration / 1000);

            const startTime = ctx.currentTime + (i * (duration + 100)) / 1000;
            oscillator.start(startTime);
            oscillator.stop(startTime + duration / 1000);

            // Wait for this beep to finish before playing the next
            if (i < count - 1) {
                await new Promise((resolve) => setTimeout(resolve, duration + 100));
            }
        }
    }

    /**
     * Play an audio file
     */
    private async playAudioFile(url: string, options: SoundNotificationOptions): Promise<void> {
        const audio = new Audio(url);
        audio.volume = options.volume ?? this.volume;
        audio.loop = options.loop ?? false;

        if (options.duration) {
            setTimeout(() => {
                audio.pause();
                audio.currentTime = 0;
            }, options.duration);
        }

        await audio.play();
    }

    /**
     * Play alert sound based on severity
     */
    async playAlertSound(severity: string): Promise<void> {
        const soundMap: Record<string, SoundType> = {
            critical: 'critical',
            high: 'high',
            medium: 'medium',
            low: 'info',
        };

        const soundType = soundMap[severity] || 'info';
        await this.playSound(soundType);
    }

    /**
     * Enable/disable sound notifications
     */
    setEnabled(enabled: boolean): void {
        this.enabled = enabled;
        console.log(`[Sound] Notifications ${enabled ? 'enabled' : 'disabled'}`);
    }

    /**
     * Set default volume
     */
    setVolume(volume: number): void {
        this.volume = Math.max(0, Math.min(1, volume));
        console.log(`[Sound] Volume set to ${(this.volume * 100).toFixed(0)}%`);
    }

    /**
     * Request browser notification permission
     */
    async requestPermission(): Promise<NotificationPermission> {
        if (!('Notification' in window)) {
            console.warn('[Sound] Browser notifications not supported');
            return 'denied';
        }

        if (Notification.permission === 'granted') {
            return 'granted';
        }

        if (Notification.permission !== 'denied') {
            return await Notification.requestPermission();
        }

        return Notification.permission;
    }

    /**
     * Show browser notification with sound
     */
    async showNotification(
        title: string,
        options: NotificationOptions & { soundType?: SoundType } = {},
    ): Promise<void> {
        const permission = await this.requestPermission();

        if (permission === 'granted') {
            const notification = new Notification(title, {
                icon: '/favicon.ico',
                badge: '/badge.png',
                ...options,
            });

            // Play sound
            if (options.soundType) {
                await this.playSound(options.soundType);
            }

            // Auto-close after 5 seconds
            setTimeout(() => notification.close(), 5000);
        }
    }

    /**
     * Test all notification sounds
     */
    async testSounds(): Promise<void> {
        const sounds: SoundType[] = ['info', 'success', 'medium', 'high', 'critical', 'error'];

        for (const sound of sounds) {
            console.log(`[Sound] Testing: ${sound}`);
            await this.playSound(sound);
            await new Promise((resolve) => setTimeout(resolve, 1000));
        }
    }
}

// Singleton instance
export const soundManager = new SoundNotificationManager();

// Export for React hook usage
export function useSoundNotifications() {
    const playAlertSound = (severity: string) => soundManager.playAlertSound(severity);
    const setEnabled = (enabled: boolean) => soundManager.setEnabled(enabled);
    const setVolume = (volume: number) => soundManager.setVolume(volume);
    const testSounds = () => soundManager.testSounds();

    return {
        playAlertSound,
        setEnabled,
        setVolume,
        testSounds,
        soundManager,
    };
}
