/**
 * TutorPutor - Content Download Manager
 *
 * Manages downloading modules for offline access.
 * Implements a download queue with progress tracking and storage quota management.
 *
 * @doc.type module
 * @doc.purpose Content download management
 * @doc.layer product
 * @doc.pattern Service
 */

import type { ModuleDetail, ModuleId } from "@ghatana/tutorputor-contracts/v1";

// Type aliases for backwards compatibility with existing code
type Module = ModuleDetail;
type Lesson = ModuleDetail["blocks"] extends (infer T)[] ? T : never;
type Quiz = { id: string; questions: unknown[] };

// ============================================================================
// Types
// ============================================================================

/**
 * Download status for a module.
 */
export type DownloadStatus =
  | "idle"
  | "queued"
  | "downloading"
  | "paused"
  | "completed"
  | "failed";

/**
 * Download progress for a module.
 */
export interface DownloadProgress {
  moduleId: string;
  status: DownloadStatus;
  progress: number; // 0-100
  bytesDownloaded: number;
  totalBytes: number;
  currentItem: string | null; // Current lesson/quiz being downloaded
  startedAt: Date | null;
  completedAt: Date | null;
  error: string | null;
}

/**
 * Download queue item.
 */
export interface DownloadQueueItem {
  moduleId: string;
  priority: number; // Higher = more priority
  addedAt: Date;
}

/**
 * Storage quota information.
 */
export interface StorageQuota {
  usedBytes: number;
  totalBytes: number;
  availableBytes: number;
  percentUsed: number;
}

/**
 * Download manager configuration.
 */
export interface DownloadManagerConfig {
  /** API base URL */
  apiBaseUrl: string;
  /** Maximum concurrent downloads */
  maxConcurrent: number;
  /** Storage quota limit in bytes */
  storageQuotaBytes: number;
  /** Chunk size for downloads */
  chunkSizeBytes: number;
  /** Auth token getter */
  getAuthToken: () => Promise<string | null>;
  /** Storage adapter for persisting downloads */
  storage: DownloadStorage;
}

/**
 * Storage interface for persisting downloaded content.
 */
export interface DownloadStorage {
  saveModule(module: Module & { downloadedAt: string }): Promise<void>;
  getModule(moduleId: string): Promise<Module | null>;
  deleteModule(moduleId: string): Promise<void>;
  getAllModules(): Promise<Module[]>;
  getStorageUsed(): Promise<number>;
}

/**
 * Download event callbacks.
 */
export interface DownloadCallbacks {
  onQueueChange?: (queue: DownloadQueueItem[]) => void;
  onProgressChange?: (progress: DownloadProgress) => void;
  onDownloadComplete?: (moduleId: string) => void;
  onDownloadError?: (moduleId: string, error: string) => void;
  onStorageWarning?: (quota: StorageQuota) => void;
}

// ============================================================================
// Download Manager
// ============================================================================

/**
 * Content Download Manager
 *
 * Manages a queue of module downloads with:
 * - Priority-based queue management
 * - Progress tracking per module
 * - Storage quota enforcement
 * - Pause/resume capability
 * - Error handling and retry
 */
export class ContentDownloadManager {
  private config: DownloadManagerConfig;
  private callbacks: DownloadCallbacks = {};
  private queue: DownloadQueueItem[] = [];
  private activeDownloads: Map<string, DownloadProgress> = new Map();
  private isPaused = false;

  constructor(config: DownloadManagerConfig) {
    this.config = config;
  }

  /**
   * Set event callbacks.
   */
  setCallbacks(callbacks: DownloadCallbacks): void {
    this.callbacks = callbacks;
  }

  // =========================================================================
  // Queue Management
  // =========================================================================

  /**
   * Add a module to the download queue.
   */
  async queueDownload(moduleId: string, priority: number = 0): Promise<void> {
    // Check if already in queue or downloading
    if (this.isInQueue(moduleId) || this.isDownloading(moduleId)) {
      return;
    }

    // Check storage quota
    const quota = await this.getStorageQuota();
    if (quota.percentUsed > 95) {
      this.callbacks.onStorageWarning?.(quota);
      throw new Error(
        "Storage quota exceeded. Please remove some downloaded modules.",
      );
    }

    // Add to queue
    const item: DownloadQueueItem = {
      moduleId,
      priority,
      addedAt: new Date(),
    };

    this.queue.push(item);
    this.sortQueue();
    this.callbacks.onQueueChange?.(this.queue);

    // Start processing if not paused
    if (!this.isPaused) {
      this.processQueue();
    }
  }

  /**
   * Remove a module from the download queue.
   */
  removeFromQueue(moduleId: string): void {
    this.queue = this.queue.filter((item) => item.moduleId !== moduleId);
    this.callbacks.onQueueChange?.(this.queue);
  }

  /**
   * Check if a module is in the queue.
   */
  isInQueue(moduleId: string): boolean {
    return this.queue.some((item) => item.moduleId === moduleId);
  }

  /**
   * Check if a module is currently downloading.
   */
  isDownloading(moduleId: string): boolean {
    const progress = this.activeDownloads.get(moduleId);
    return progress?.status === "downloading";
  }

  /**
   * Get the current queue.
   */
  getQueue(): DownloadQueueItem[] {
    return [...this.queue];
  }

  /**
   * Sort queue by priority (higher first) and then by added time.
   */
  private sortQueue(): void {
    this.queue.sort((a, b) => {
      if (b.priority !== a.priority) {
        return b.priority - a.priority;
      }
      return a.addedAt.getTime() - b.addedAt.getTime();
    });
  }

  // =========================================================================
  // Download Processing
  // =========================================================================

  /**
   * Process the download queue.
   */
  private async processQueue(): Promise<void> {
    if (this.isPaused) return;

    const activeCount = Array.from(this.activeDownloads.values()).filter(
      (p) => p.status === "downloading",
    ).length;

    // Start downloads up to max concurrent
    while (
      activeCount + this.getProcessingCount() < this.config.maxConcurrent &&
      this.queue.length > 0
    ) {
      const item = this.queue.shift();
      if (item) {
        this.downloadModule(item.moduleId);
      }
    }
  }

  private getProcessingCount(): number {
    return Array.from(this.activeDownloads.values()).filter(
      (p) => p.status === "downloading",
    ).length;
  }

  /**
   * Download a single module.
   */
  private async downloadModule(moduleId: string): Promise<void> {
    const progress: DownloadProgress = {
      moduleId,
      status: "downloading",
      progress: 0,
      bytesDownloaded: 0,
      totalBytes: 0,
      currentItem: null,
      startedAt: new Date(),
      completedAt: null,
      error: null,
    };

    this.activeDownloads.set(moduleId, progress);
    this.callbacks.onProgressChange?.(progress);

    try {
      // Fetch module metadata
      const module = await this.fetchModuleMetadata(moduleId);
      progress.totalBytes = module.totalSizeBytes ?? 0;

      // Download lessons
      const lessons: Lesson[] = [];
      for (let i = 0; i < (module.lessons?.length ?? 0); i++) {
        const lessonId = module.lessons![i].id;
        progress.currentItem = `Lesson ${i + 1}`;
        this.callbacks.onProgressChange?.(progress);

        const lesson = await this.fetchLesson(moduleId, lessonId);
        lessons.push(lesson);

        progress.bytesDownloaded += JSON.stringify(lesson).length;
        progress.progress = Math.round(
          (progress.bytesDownloaded / progress.totalBytes) * 100,
        );
        this.callbacks.onProgressChange?.(progress);
      }

      // Download quizzes
      const quizzes: Quiz[] = [];
      for (let i = 0; i < (module.quizzes?.length ?? 0); i++) {
        const quizId = module.quizzes![i].id;
        progress.currentItem = `Quiz ${i + 1}`;
        this.callbacks.onProgressChange?.(progress);

        const quiz = await this.fetchQuiz(moduleId, quizId);
        quizzes.push(quiz);

        progress.bytesDownloaded += JSON.stringify(quiz).length;
        progress.progress = Math.round(
          (progress.bytesDownloaded / progress.totalBytes) * 100,
        );
        this.callbacks.onProgressChange?.(progress);
      }

      // Save to storage
      const completeModule: Module = {
        ...module,
        lessons,
        quizzes,
      };

      await this.config.storage.saveModule({
        ...completeModule,
        downloadedAt: new Date().toISOString(),
      });

      // Update progress
      progress.status = "completed";
      progress.progress = 100;
      progress.completedAt = new Date();
      progress.currentItem = null;
      this.callbacks.onProgressChange?.(progress);
      this.callbacks.onDownloadComplete?.(moduleId);

      // Process next in queue
      this.processQueue();
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : "Download failed";

      progress.status = "failed";
      progress.error = errorMessage;
      this.callbacks.onProgressChange?.(progress);
      this.callbacks.onDownloadError?.(moduleId, errorMessage);

      // Process next in queue
      this.processQueue();
    }
  }

  // =========================================================================
  // API Calls
  // =========================================================================

  private async fetchModuleMetadata(moduleId: string): Promise<Module> {
    const token = await this.config.getAuthToken();
    const response = await fetch(
      `${this.config.apiBaseUrl}/api/modules/${moduleId}`,
      {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      },
    );

    if (!response.ok) {
      throw new Error(`Failed to fetch module: ${response.status}`);
    }

    return response.json();
  }

  private async fetchLesson(
    moduleId: string,
    lessonId: string,
  ): Promise<Lesson> {
    const token = await this.config.getAuthToken();
    const response = await fetch(
      `${this.config.apiBaseUrl}/api/modules/${moduleId}/lessons/${lessonId}`,
      {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      },
    );

    if (!response.ok) {
      throw new Error(`Failed to fetch lesson: ${response.status}`);
    }

    return response.json();
  }

  private async fetchQuiz(moduleId: string, quizId: string): Promise<Quiz> {
    const token = await this.config.getAuthToken();
    const response = await fetch(
      `${this.config.apiBaseUrl}/api/modules/${moduleId}/quizzes/${quizId}`,
      {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      },
    );

    if (!response.ok) {
      throw new Error(`Failed to fetch quiz: ${response.status}`);
    }

    return response.json();
  }

  // =========================================================================
  // Control Methods
  // =========================================================================

  /**
   * Pause all downloads.
   */
  pause(): void {
    this.isPaused = true;

    // Update active download statuses
    for (const [moduleId, progress] of this.activeDownloads) {
      if (progress.status === "downloading") {
        progress.status = "paused";
        this.callbacks.onProgressChange?.(progress);
      }
    }
  }

  /**
   * Resume downloads.
   */
  resume(): void {
    this.isPaused = false;
    this.processQueue();
  }

  /**
   * Cancel a specific download.
   */
  cancel(moduleId: string): void {
    // Remove from queue
    this.removeFromQueue(moduleId);

    // Update active download
    const progress = this.activeDownloads.get(moduleId);
    if (progress) {
      progress.status = "idle";
      this.callbacks.onProgressChange?.(progress);
      this.activeDownloads.delete(moduleId);
    }
  }

  /**
   * Retry a failed download.
   */
  async retry(moduleId: string): Promise<void> {
    const progress = this.activeDownloads.get(moduleId);
    if (progress?.status === "failed") {
      this.activeDownloads.delete(moduleId);
      await this.queueDownload(moduleId, 10); // High priority retry
    }
  }

  // =========================================================================
  // Progress & Status
  // =========================================================================

  /**
   * Get progress for a specific module.
   */
  getProgress(moduleId: string): DownloadProgress | null {
    return this.activeDownloads.get(moduleId) ?? null;
  }

  /**
   * Get all active downloads.
   */
  getAllProgress(): DownloadProgress[] {
    return Array.from(this.activeDownloads.values());
  }

  /**
   * Check if a module is downloaded.
   */
  async isDownloaded(moduleId: string): Promise<boolean> {
    const module = await this.config.storage.getModule(moduleId);
    return module !== null;
  }

  // =========================================================================
  // Storage Management
  // =========================================================================

  /**
   * Get storage quota information.
   */
  async getStorageQuota(): Promise<StorageQuota> {
    const usedBytes = await this.config.storage.getStorageUsed();
    const totalBytes = this.config.storageQuotaBytes;
    const availableBytes = Math.max(0, totalBytes - usedBytes);
    const percentUsed = Math.round((usedBytes / totalBytes) * 100);

    return {
      usedBytes,
      totalBytes,
      availableBytes,
      percentUsed,
    };
  }

  /**
   * Delete a downloaded module.
   */
  async deleteDownload(moduleId: string): Promise<void> {
    await this.config.storage.deleteModule(moduleId);
    this.activeDownloads.delete(moduleId);
  }

  /**
   * Get all downloaded modules.
   */
  async getDownloadedModules(): Promise<Module[]> {
    return this.config.storage.getAllModules();
  }

  /**
   * Clear all downloaded content.
   */
  async clearAllDownloads(): Promise<void> {
    const modules = await this.config.storage.getAllModules();
    for (const module of modules) {
      await this.config.storage.deleteModule(module.id);
    }
    this.activeDownloads.clear();
    this.queue = [];
    this.callbacks.onQueueChange?.(this.queue);
  }
}
