/**
 * @doc.type service
 * @doc.purpose Video and GIF encoding for animation export
 * @doc.layer product
 * @doc.pattern Media Processing Service
 */

import type { AnimationSpec } from './service';

// Browser API declarations for encoding libraries
declare global {
    interface Window {
        createFFmpeg: any;
        GIF: any;
    }
    const window: Window | undefined;

    interface ImageData {
        data: Uint8ClampedArray;
        width: number;
        height: number;
    }

    interface CanvasGradient { }
    interface CanvasPattern { }

    interface OffscreenCanvas {
        getContext(contextId: '2d'): OffscreenCanvasRenderingContext2D | null;
        convertToBlob(options?: { type?: string; quality?: number }): Promise<Blob>;
    }

    interface OffscreenCanvasRenderingContext2D {
        clearRect(x: number, y: number, width: number, height: number): void;
        fillRect(x: number, y: number, width: number, height: number): void;
        fillStyle: string | CanvasGradient | CanvasPattern;
        globalAlpha: number;
        save(): void;
        restore(): void;
        translate(x: number, y: number): void;
        rotate(angle: number): void;
        scale(x: number, y: number): void;
        putImageData(imageData: ImageData, dx: number, dy: number): void;
        getImageData(sx: number, sy: number, sw: number, sh: number): ImageData;
    }

    const OffscreenCanvas: {
        new(width: number, height: number): OffscreenCanvas;
    };
}

export interface VideoEncodingOptions {
    format: 'mp4' | 'webm' | 'gif';
    quality: 'low' | 'medium' | 'high';
    fps: number;
    width: number;
    height: number;
    duration?: number;
    loop?: boolean;
}

export interface EncodingProgress {
    stage: 'preparing' | 'encoding' | 'processing' | 'finalizing' | 'complete';
    progress: number; // 0-100
    message: string;
    timeElapsed: number;
    timeEstimated: number;
}

/**
 * Video Encoding Service
 * Handles video and GIF encoding using ffmpeg.wasm and gif.js
 */
export class VideoEncodingService {
    private ffmpeg: any = null;
    private gifEncoder: any = null;
    private isInitialized = false;

    /**
     * Initialize encoding libraries
     */
    async initialize(): Promise<void> {
        if (this.isInitialized) return;

        try {
            // Initialize FFmpeg
            if (typeof window !== 'undefined' && window.createFFmpeg) {
                this.ffmpeg = window.createFFmpeg({
                    log: true,
                    corePath: 'https://unpkg.com/@ffmpeg/core@0.10.0/dist/ffmpeg-core.js',
                });

                await this.ffmpeg.load();
            }

            // Initialize GIF encoder
            if (typeof window !== 'undefined' && window.GIF) {
                this.gifEncoder = new window.GIF({
                    workers: 2,
                    quality: 10,
                    width: 800,
                    height: 600,
                    workerScript: 'https://unpkg.com/gif.js@0.2.0/dist/gif.worker.js',
                });
            }

            this.isInitialized = true;
        } catch (error) {
            throw new Error(`Failed to initialize encoding libraries: ${error instanceof Error ? error.message : 'Unknown error'}`);
        }
    }

    /**
     * Encode animation to video format
     */
    async encodeVideo(
        animation: AnimationSpec,
        options: VideoEncodingOptions,
        onProgress?: (progress: EncodingProgress) => void
    ): Promise<Blob> {
        await this.initialize();

        const startTime = Date.now();
        const frames = await this.generateFrames(animation, options);

        if (options.format === 'gif') {
            return this.encodeGIF(frames, options, onProgress, startTime);
        } else {
            return this.encodeMP4(frames, options, onProgress, startTime);
        }
    }

    /**
     * Generate frames from animation
     */
    private async generateFrames(
        animation: AnimationSpec,
        options: VideoEncodingOptions
    ): Promise<ImageData[]> {
        const frames: ImageData[] = [];
        const totalFrames = Math.floor((animation.durationSeconds * 1000) / (1000 / options.fps));
        const frameInterval = 1000 / options.fps;

        for (let i = 0; i < totalFrames; i++) {
            const currentTime = i * frameInterval;
            const frame = await this.renderFrame(animation, currentTime, options);
            frames.push(frame);
        }

        return frames;
    }

    /**
     * Render single frame
     */
    private async renderFrame(
        animation: AnimationSpec,
        currentTime: number,
        options: VideoEncodingOptions
    ): Promise<ImageData> {
        // Create offscreen canvas for rendering
        const canvas = new OffscreenCanvas(options.width, options.height);
        const ctx = canvas.getContext('2d');

        if (!ctx) {
            throw new Error('Failed to get 2D context');
        }

        // Clear canvas
        ctx.clearRect(0, 0, options.width, options.height);

        // Set background
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, options.width, options.height);

        // Find current keyframe
        const keyframe = this.findKeyframe(animation, currentTime);

        if (keyframe) {
            // Apply keyframe properties
            const properties = this.interpolateProperties(animation, currentTime);

            // Render based on animation type
            if (animation.type === '2d') {
                this.render2DFrame(ctx, properties, options);
            } else if (animation.type === '3d') {
                this.render3DFrame(ctx, properties, options);
            }
        }

        // Get image data
        return ctx.getImageData(0, 0, options.width, options.height);
    }

    /**
     * Find keyframe for current time
     */
    private findKeyframe(animation: AnimationSpec, currentTime: number): any {
        const keyframes = animation.keyframes;
        if (keyframes.length === 0) {
            return { timeMs: 0, properties: {}, easing: "linear" };
        }

        for (let i = keyframes.length - 1; i >= 0; i--) {
            const keyframe = keyframes[i];
            if (keyframe && keyframe.timeMs <= currentTime) {
                return keyframe;
            }
        }

        return keyframes[0] ?? { timeMs: 0, properties: {}, easing: "linear" };
    }

    /**
     * Interpolate properties for current time
     */
    private interpolateProperties(animation: AnimationSpec, currentTime: number): Record<string, any> {
        const keyframes = animation.keyframes;
        const previousKeyframe = this.findKeyframe(animation, currentTime);
        const nextKeyframe = keyframes.find(kf => kf.timeMs > currentTime);

        if (!nextKeyframe) {
            return previousKeyframe.properties;
        }

        const progress = (currentTime - previousKeyframe.timeMs) / (nextKeyframe.timeMs - previousKeyframe.timeMs);
        const easedProgress = this.applyEasing(progress, previousKeyframe.easing || 'linear');

        const interpolated: Record<string, any> = {};

        for (const [key, value] of Object.entries(previousKeyframe.properties)) {
            const nextValue = nextKeyframe.properties[key];

            if (typeof value === 'number' && typeof nextValue === 'number') {
                interpolated[key] = value + (nextValue - value) * easedProgress;
            } else {
                interpolated[key] = value;
            }
        }

        return interpolated;
    }

    /**
     * Apply easing function
     */
    private applyEasing(t: number, easing: string): number {
        switch (easing) {
            case 'linear':
                return t;
            case 'easeIn':
                return t * t;
            case 'easeOut':
                return t * (2 - t);
            case 'easeInOut':
                return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
            case 'easeInQuad':
                return t * t;
            case 'easeOutQuad':
                return t * (2 - t);
            case 'easeInOutQuad':
                return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
            case 'easeInCubic':
                return t * t * t;
            case 'easeOutCubic':
                return (--t) * t * t + 1;
            case 'easeInOutCubic':
                return t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
            default:
                return t;
        }
    }

    /**
     * Render 2D frame
     */
    private render2DFrame(
        ctx: OffscreenCanvasRenderingContext2D,
        properties: Record<string, any>,
        options: VideoEncodingOptions
    ): void {
        const { x = 0, y = 0, width = 100, height = 100, rotation = 0, scale = 1, opacity = 1, color = '#000000' } = properties;

        ctx.save();

        // Apply transformations
        ctx.translate(x + width / 2, y + height / 2);
        ctx.rotate((rotation * Math.PI) / 180);
        ctx.scale(scale, scale);
        ctx.globalAlpha = opacity;

        // Draw shape
        ctx.fillStyle = color;
        ctx.fillRect(-width / 2, -height / 2, width, height);

        ctx.restore();
    }

    /**
     * Render 3D frame (simplified)
     */
    private render3DFrame(
        ctx: OffscreenCanvasRenderingContext2D,
        properties: Record<string, any>,
        options: VideoEncodingOptions
    ): void {
        // Simplified 3D rendering using 2D canvas
        const { x = 0, y = 0, z = 0, width = 100, height = 100, rotationX = 0, rotationY = 0, rotationZ = 0, scale = 1, opacity = 1, color = '#000000' } = properties;

        ctx.save();

        // Apply 3D-like transformations
        const perspective = 1000;
        const scale3D = perspective / (perspective + z);

        ctx.translate(x + width / 2, y + height / 2);
        ctx.rotate((rotationZ * Math.PI) / 180);
        ctx.scale(scale * scale3D, scale * scale3D);
        ctx.globalAlpha = opacity;

        // Draw shape with 3D effect
        ctx.fillStyle = color;
        ctx.fillRect(-width / 2, -height / 2, width, height);

        // Add depth effect
        ctx.fillStyle = 'rgba(0, 0, 0, 0.2)';
        ctx.fillRect(-width / 2 + 5, -height / 2 + 5, width, height);

        ctx.restore();
    }

    /**
     * Encode frames to GIF
     */
    private async encodeGIF(
        frames: ImageData[],
        options: VideoEncodingOptions,
        onProgress?: (progress: EncodingProgress) => void,
        startTime?: number
    ): Promise<Blob> {
        return new Promise((resolve, reject) => {
            if (!this.gifEncoder || !window?.GIF) {
                reject(new Error('GIF encoder not initialized'));
                return;
            }

            const gif = new window.GIF({
                workers: 2,
                quality: this.getGIFQuality(options.quality),
                width: options.width,
                height: options.height,
                repeat: options.loop ? 0 : -1,
            });

            gif.on('progress', (progress: number) => {
                if (onProgress && startTime) {
                    onProgress({
                        stage: 'encoding',
                        progress: Math.round(progress * 100),
                        message: `Encoding GIF... ${Math.round(progress * 100)}%`,
                        timeElapsed: Date.now() - startTime,
                        timeEstimated: Math.round((Date.now() - startTime) / progress),
                    });
                }
            });

            gif.on('finished', (blob: Blob) => {
                if (onProgress && startTime) {
                    onProgress({
                        stage: 'complete',
                        progress: 100,
                        message: 'GIF encoding complete',
                        timeElapsed: Date.now() - startTime,
                        timeEstimated: Date.now() - startTime,
                    });
                }
                resolve(blob);
            });

            gif.on('error', (error: Error) => {
                reject(new Error(`GIF encoding failed: ${error.message}`));
            });

            // Add frames
            for (let i = 0; i < frames.length; i++) {
                const frame = frames[i];
                if (!frame) {
                    continue;
                }
                const canvas = new OffscreenCanvas(options.width, options.height);
                const ctx = canvas.getContext('2d');
                if (ctx) {
                    ctx.putImageData(frame, 0, 0);
                    gif.addFrame(canvas, { delay: 1000 / options.fps });
                }
            }

            gif.render();
        });
    }

    /**
     * Encode frames to MP4 using FFmpeg
     */
    private async encodeMP4(
        frames: ImageData[],
        options: VideoEncodingOptions,
        onProgress?: (progress: EncodingProgress) => void,
        startTime?: number
    ): Promise<Blob> {
        if (!this.ffmpeg) {
            throw new Error('FFmpeg not initialized');
        }

        try {
            // Create temporary files for frames
            const frameFiles: string[] = [];

            for (let i = 0; i < frames.length; i++) {
                const frame = frames[i];
                if (!frame) {
                    continue;
                }
                const canvas = new OffscreenCanvas(options.width, options.height);
                const ctx = canvas.getContext('2d');
                if (ctx) {
                    ctx.putImageData(frame, 0, 0);

                    const blob = await canvas.convertToBlob({ type: 'image/png' });
                    const arrayBuffer = await blob.arrayBuffer();
                    const uint8Array = new Uint8Array(arrayBuffer);

                    const fileName = `frame_${i.toString().padStart(6, '0')}.png`;
                    this.ffmpeg.FS('writeFile', fileName, uint8Array);
                    frameFiles.push(fileName);
                }
            }

            // Report progress
            if (onProgress && startTime) {
                onProgress({
                    stage: 'encoding',
                    progress: 10,
                    message: 'Preparing frames for encoding...',
                    timeElapsed: Date.now() - startTime,
                    timeEstimated: Math.round((Date.now() - startTime) * 10),
                });
            }

            // Run FFmpeg command
            const outputName = `output.${options.format}`;
            const command = this.buildFFmpegCommand(frameFiles, outputName, options);

            this.ffmpeg.setProgress((progress: any) => {
                if (onProgress && startTime) {
                    onProgress({
                        stage: 'encoding',
                        progress: Math.round(10 + progress.ratio * 80),
                        message: `Encoding video... ${Math.round(progress.ratio * 100)}%`,
                        timeElapsed: Date.now() - startTime,
                        timeEstimated: Math.round((Date.now() - startTime) / (progress.ratio || 0.1)),
                    });
                }
            });

            await this.ffmpeg.run(...command);

            // Get output file
            const outputData = this.ffmpeg.FS('readFile', outputName);
            const blob = new Blob([outputData.buffer], { type: `video/${options.format}` });

            // Clean up temporary files
            for (const fileName of frameFiles) {
                this.ffmpeg.FS('unlink', fileName);
            }
            this.ffmpeg.FS('unlink', outputName);

            // Report completion
            if (onProgress && startTime) {
                onProgress({
                    stage: 'complete',
                    progress: 100,
                    message: `${options.format.toUpperCase()} encoding complete`,
                    timeElapsed: Date.now() - startTime,
                    timeEstimated: Date.now() - startTime,
                });
            }

            return blob;
        } catch (error) {
            throw new Error(`MP4 encoding failed: ${error instanceof Error ? error.message : 'Unknown error'}`);
        }
    }

    /**
     * Build FFmpeg command
     */
    private buildFFmpegCommand(
        frameFiles: string[],
        outputName: string,
        options: VideoEncodingOptions
    ): string[] {
        const command = [
            '-framerate', options.fps.toString(),
            '-i', `frame_%06d.png`,
            '-c:v', this.getVideoCodec(options.format, options.quality),
            '-preset', this.getPreset(options.quality),
            '-crf', this.getCRF(options.quality),
            '-pix_fmt', 'yuv420p',
            '-r', options.fps.toString(),
            '-s', `${options.width}x${options.height}`,
        ];

        if (options.duration) {
            command.push('-t', options.duration.toString());
        }

        if (options.loop && options.format === 'webm') {
            command.push('-stream_loop', '-1');
        }

        command.push(outputName);
        return command;
    }

    /**
     * Get video codec based on format and quality
     */
    private getVideoCodec(format: string, quality: string): string {
        if (format === 'mp4') {
            return quality === 'high' ? 'libx264' : 'libx264';
        } else if (format === 'webm') {
            return 'libvpx-vp9';
        }
        return 'libx264';
    }

    /**
     * Get encoding preset based on quality
     */
    private getPreset(quality: string): string {
        switch (quality) {
            case 'low':
                return 'ultrafast';
            case 'medium':
                return 'medium';
            case 'high':
                return 'slow';
            default:
                return 'medium';
        }
    }

    /**
     * Get CRF value based on quality
     */
    private getCRF(quality: string): string {
        switch (quality) {
            case 'low':
                return '28';
            case 'medium':
                return '23';
            case 'high':
                return '18';
            default:
                return '23';
        }
    }

    /**
     * Get GIF quality setting
     */
    private getGIFQuality(quality: string): number {
        switch (quality) {
            case 'low':
                return 20;
            case 'medium':
                return 10;
            case 'high':
                return 5;
            default:
                return 10;
        }
    }

    /**
     * Get supported formats
     */
    getSupportedFormats(): string[] {
        return ['mp4', 'webm', 'gif'];
    }

    /**
     * Get quality presets
     */
    getQualityPresets(): Array<{ name: string; description: string }> {
        return [
            { name: 'low', description: 'Fast encoding, smaller file size' },
            { name: 'medium', description: 'Balanced quality and file size' },
            { name: 'high', description: 'Best quality, larger file size' },
        ];
    }

    /**
     * Estimate encoding time
     */
    estimateEncodingTime(
        animation: AnimationSpec,
        options: VideoEncodingOptions
    ): { minTime: number; maxTime: number; avgTime: number } {
        const totalFrames = Math.floor((animation.durationSeconds * 1000) / (1000 / options.fps));
        const complexity = this.getAnimationComplexity(animation);

        const baseTimePerFrame = options.format === 'gif' ? 50 : 100; // ms
        const qualityMultiplier = options.quality === 'high' ? 2 : options.quality === 'low' ? 0.5 : 1;

        const avgTime = totalFrames * baseTimePerFrame * complexity * qualityMultiplier;

        return {
            minTime: avgTime * 0.5,
            maxTime: avgTime * 2,
            avgTime,
        };
    }

    /**
     * Get animation complexity factor
     */
    private getAnimationComplexity(animation: AnimationSpec): number {
        let complexity = 1;

        // Factor in number of keyframes
        complexity += animation.keyframes.length * 0.1;

        // Factor in animation type
        if (animation.type === '3d') {
            complexity *= 1.5;
        }

        // Factor in number of properties
        const maxProperties = Math.max(...animation.keyframes.map(kf => Object.keys(kf.properties).length));
        complexity += maxProperties * 0.05;

        return complexity;
    }

    /**
     * Cleanup resources
     */
    destroy(): void {
        if (this.ffmpeg) {
            this.ffmpeg = null;
        }
        if (this.gifEncoder) {
            this.gifEncoder = null;
        }
        this.isInitialized = false;
    }
}

/**
 * Singleton instance
 */
export const videoEncodingService = new VideoEncodingService();
