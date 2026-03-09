/**
 * @doc.type service
 * @doc.purpose Animation export service for video and GIF generation
 * @doc.layer product
 * @doc.pattern Service
 */

import type { AnimationSpec, AnimationRenderer } from './service';
import { AnimationRuntime } from './service';

// Browser API type augmentations for Node.js environment
interface BrowserDocument {
    createElement(tagName: 'canvas'): BrowserCanvas;
    createElement(tagName: 'a'): BrowserAnchor;
    body: {
        appendChild(node: any): void;
        removeChild(node: any): void;
    };
}

interface BrowserCanvas {
    width: number;
    height: number;
    getContext(contextId: '2d'): BrowserCanvasContext | null;
}

interface BrowserCanvasContext {
    fillStyle: string;
    fillRect(x: number, y: number, width: number, height: number): void;
    getImageData(sx: number, sy: number, sw: number, sh: number): BrowserImageData;
}

interface BrowserImageData {
    width: number;
    height: number;
    data: Uint8ClampedArray;
}

interface BrowserAnchor {
    href: string;
    download: string;
    click(): void;
}

interface BrowserURL {
    createObjectURL(blob: Blob): string;
    revokeObjectURL(url: string): void;
}

// Declare browser globals
declare const document: BrowserDocument | undefined;
declare const URL: BrowserURL | undefined;

export interface ExportOptions {
    format: 'json' | 'video' | 'gif';
    width?: number;
    height?: number;
    fps?: number;
    quality?: number; // 0-100
    backgroundColor?: string;
}

export interface ExportProgress {
    progress: number; // 0-1
    currentFrame: number;
    totalFrames: number;
    status: 'preparing' | 'rendering' | 'encoding' | 'complete' | 'error';
    message?: string;
}

export type ExportProgressCallback = (progress: ExportProgress) => void;

/**
 * Animation Export Service
 * Handles exporting animations to various formats
 */
export class AnimationExportService {
    /**
     * Export animation to JSON
     */
    async exportToJSON(animation: AnimationSpec): Promise<Blob> {
        const json = JSON.stringify(animation, null, 2);
        return new Blob([json], { type: 'application/json' });
    }

    /**
     * Export animation to video (MP4)
     * Note: This requires server-side rendering with ffmpeg
     */
    async exportToVideo(
        animation: AnimationSpec,
        renderer: AnimationRenderer,
        options: Partial<ExportOptions> = {},
        onProgress?: ExportProgressCallback
    ): Promise<Blob> {
        const {
            width = 1920,
            height = 1080,
            fps = 60,
            quality = 80,
            backgroundColor = '#000000',
        } = options;

        const totalFrames = Math.ceil(animation.durationSeconds * fps);
        const frameInterval = 1000 / fps;

        // Notify preparation
        onProgress?.({
            progress: 0,
            currentFrame: 0,
            totalFrames,
            status: 'preparing',
            message: 'Preparing video export...',
        });

        // Create canvas for rendering
        const canvas = this.createCanvas(width, height);
        const ctx = canvas.getContext('2d')!;

        // Collect all frames
        const frames: BrowserImageData[] = [];
        const runtime = new AnimationRuntime();
        runtime.setRenderer(renderer);
        runtime.loadAnimation(animation);

        // Render each frame
        for (let frame = 0; frame < totalFrames; frame++) {
            const timeMs = frame * frameInterval;

            // Clear canvas
            ctx.fillStyle = backgroundColor;
            ctx.fillRect(0, 0, width, height);

            // Seek to frame time and render
            runtime.seek(timeMs);

            // Capture frame
            const imageData = ctx.getImageData(0, 0, width, height);
            frames.push(imageData);

            // Report progress
            onProgress?.({
                progress: frame / totalFrames,
                currentFrame: frame,
                totalFrames,
                status: 'rendering',
                message: `Rendering frame ${frame + 1}/${totalFrames}...`,
            });
        }

        // Encode to video
        onProgress?.({
            progress: 0.9,
            currentFrame: totalFrames,
            totalFrames,
            status: 'encoding',
            message: 'Encoding video...',
        });

        // In a real implementation, this would send frames to a server-side
        // encoder (ffmpeg) or use WebCodecs API for browser-based encoding
        const videoBlob = await this.encodeFramesToVideo(frames, fps, quality);

        onProgress?.({
            progress: 1,
            currentFrame: totalFrames,
            totalFrames,
            status: 'complete',
            message: 'Video export complete!',
        });

        return videoBlob;
    }

    /**
     * Export animation to GIF
     */
    async exportToGIF(
        animation: AnimationSpec,
        renderer: AnimationRenderer,
        options: Partial<ExportOptions> = {},
        onProgress?: ExportProgressCallback
    ): Promise<Blob> {
        const {
            width = 800,
            height = 600,
            fps = 30,
            quality = 80,
            backgroundColor = '#000000',
        } = options;

        const totalFrames = Math.ceil(animation.durationSeconds * fps);
        const frameInterval = 1000 / fps;

        onProgress?.({
            progress: 0,
            currentFrame: 0,
            totalFrames,
            status: 'preparing',
            message: 'Preparing GIF export...',
        });

        const canvas = this.createCanvas(width, height);
        const ctx = canvas.getContext('2d')!;

        const frames: BrowserImageData[] = [];
        const runtime = new AnimationRuntime();
        runtime.setRenderer(renderer);
        runtime.loadAnimation(animation);

        // Render frames
        for (let frame = 0; frame < totalFrames; frame++) {
            const timeMs = frame * frameInterval;

            ctx.fillStyle = backgroundColor;
            ctx.fillRect(0, 0, width, height);

            runtime.seek(timeMs);

            const imageData = ctx.getImageData(0, 0, width, height);
            frames.push(imageData);

            onProgress?.({
                progress: frame / totalFrames,
                currentFrame: frame,
                totalFrames,
                status: 'rendering',
                message: `Rendering frame ${frame + 1}/${totalFrames}...`,
            });
        }

        onProgress?.({
            progress: 0.9,
            currentFrame: totalFrames,
            totalFrames,
            status: 'encoding',
            message: 'Encoding GIF...',
        });

        const gifBlob = await this.encodeFramesToGIF(frames, frameInterval);

        onProgress?.({
            progress: 1,
            currentFrame: totalFrames,
            totalFrames,
            status: 'complete',
            message: 'GIF export complete!',
        });

        return gifBlob;
    }

    /**
     * Create a canvas element
     */
    private createCanvas(width: number, height: number): BrowserCanvas {
        if (typeof document !== 'undefined') {
            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            return canvas;
        }

        // For Node.js environment, you would use node-canvas
        throw new Error('Canvas creation not supported in this environment');
    }

    /**
     * Encode frames to video using WebCodecs API or server-side ffmpeg
     */
    private async encodeFramesToVideo(
        frames: BrowserImageData[],
        fps: number,
        quality: number
    ): Promise<Blob> {
        // This is a placeholder implementation
        // In production, you would either:
        // 1. Use WebCodecs API (browser-based encoding)
        // 2. Send frames to server for ffmpeg encoding
        // 3. Use a library like ffmpeg.wasm

        // For now, return a mock blob
        console.warn('Video encoding not fully implemented. Returning placeholder.');

        // Placeholder: Create a simple data URL
        const mockData = new Uint8Array(frames.length * 100);
        return new Blob([mockData], { type: 'video/mp4' });
    }

    /**
     * Encode frames to GIF
     */
    private async encodeFramesToGIF(
        frames: BrowserImageData[],
        frameDelay: number
    ): Promise<Blob> {
        // This is a placeholder implementation
        // In production, you would use a GIF encoding library like:
        // - gif.js
        // - omggif
        // - gifenc

        console.warn('GIF encoding not fully implemented. Returning placeholder.');

        // Placeholder: Create a simple data URL
        const mockData = new Uint8Array(frames.length * 100);
        return new Blob([mockData], { type: 'image/gif' });
    }

    /**
     * Download a blob as a file
     */
    downloadBlob(blob: Blob, filename: string): void {
        if (typeof document === 'undefined' || typeof URL === 'undefined') {
            throw new Error('Download not supported in this environment');
        }

        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    /**
     * Get file extension for format
     */
    getFileExtension(format: ExportOptions['format']): string {
        switch (format) {
            case 'json':
                return 'json';
            case 'video':
                return 'mp4';
            case 'gif':
                return 'gif';
            default:
                return 'bin';
        }
    }

    /**
     * Generate filename for export
     */
    generateFilename(animation: AnimationSpec, format: ExportOptions['format']): string {
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const sanitizedTitle = animation.title.replace(/[^a-z0-9]/gi, '_').toLowerCase();
        const extension = this.getFileExtension(format);
        return `${sanitizedTitle}_${timestamp}.${extension}`;
    }
}

/**
 * Singleton instance
 */
export const animationExportService = new AnimationExportService();
