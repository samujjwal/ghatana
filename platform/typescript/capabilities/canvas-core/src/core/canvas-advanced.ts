import { CanvasRenderer } from "./canvas-renderer.js";
import { CanvasElement } from "../elements/base.js";
import type { CanvasOptions } from "../types/index.js";
import {
  QuickSearchService,
  CanvasQuickSearchSession,
  CommandQuickSearchSession,
  CanvasCommandRegistry,
} from "./quick-search.js";
import {
  UndoRedoManager,
  KeyboardShortcutManager,
  PerformanceMonitor,
  VirtualizationManager,
} from "./performance.js";
import {
  AccessibilityManager,
  CollaborationManager,
  TouchGestureManager,
} from "./accessibility.js";

export interface CanvasAdvancedOptions {
  enableQuickSearch?: boolean;
  enableUndoRedo?: boolean;
  enableKeyboardShortcuts?: boolean;
  enablePerformanceMonitoring?: boolean;
  enableAccessibility?: boolean;
  enableCollaboration?: boolean;
  enableTouchGestures?: boolean;
  enableVirtualization?: boolean;
  userId?: string;
}

export class CanvasAdvanced extends CanvasRenderer {
  private quickSearchService!: QuickSearchService;
  private commandRegistry!: CanvasCommandRegistry;
  private undoRedoManager!: UndoRedoManager;
  private keyboardShortcutManager!: KeyboardShortcutManager;
  private performanceMonitor!: PerformanceMonitor;
  private accessibilityManager!: AccessibilityManager;
  private collaborationManager?: CollaborationManager;
  private touchGestureManager?: TouchGestureManager;
  private virtualizationManager?: VirtualizationManager;

  constructor(container: HTMLElement, options: CanvasOptions = {} as CanvasOptions) {
    super(container, options);

    this.initializeAdvancedServices(options as unknown as CanvasAdvancedOptions);
    this.setupAdvancedEventHandlers();
    this.registerDefaultCommands();
    this.setupKeyboardShortcuts();
  }

  private initializeAdvancedServices(options: CanvasAdvancedOptions): void {
    // Command Registry
    this.commandRegistry = new CanvasCommandRegistry();

    // Quick Search
    if (options.enableQuickSearch !== false) {
      this.quickSearchService = new QuickSearchService();
      this.setupQuickSearch();
    }

    // Undo/Redo
    if (options.enableUndoRedo !== false) {
      this.undoRedoManager = new UndoRedoManager();
      this.setupUndoRedo();
    }

    // Keyboard Shortcuts
    if (options.enableKeyboardShortcuts !== false) {
      this.keyboardShortcutManager = new KeyboardShortcutManager();
    }

    // Performance Monitoring
    if (options.enablePerformanceMonitoring !== false) {
      this.performanceMonitor = new PerformanceMonitor();
      this.performanceMonitor.startMonitoring();
    }

    // Accessibility
    if (options.enableAccessibility !== false) {
      this.accessibilityManager = new AccessibilityManager();
      this.setupAccessibility();
    }

    // Collaboration
    if (options.enableCollaboration && options.userId) {
      this.collaborationManager = new CollaborationManager(options.userId);
    }

    // Touch Gestures
    if (options.enableTouchGestures) {
      this.touchGestureManager = new TouchGestureManager(
        this.getCanvasElement(),
      );
      this.setupTouchGestures();
    }

    // Virtualization
    if (options.enableVirtualization) {
      this.virtualizationManager = new VirtualizationManager({
        itemHeight: 50,
        containerHeight: 600, // Fixed height since we can't access this.canvas.height
        overscan: 10,
        threshold: 100,
      });
    }
  }

  private setupQuickSearch(): void {
    const canvasSession = new CanvasQuickSearchSession(this.getElements());
    const commandSession = new CommandQuickSearchSession(
      this.commandRegistry?.getAll() ?? [],
    );

    this.quickSearchService.registerSession("canvas", canvasSession);
    this.quickSearchService.registerSession("commands", commandSession);

    // Update canvas session when elements change
    this.on("elementAdd", () => {
      const newSession = new CanvasQuickSearchSession(this.getElements());
      this.quickSearchService.registerSession("canvas", newSession);
    });

    this.on("elementRemove", () => {
      const newSession = new CanvasQuickSearchSession(this.getElements());
      this.quickSearchService.registerSession("canvas", newSession);
    });
  }

  private setupUndoRedo(): void {
    this.on("elementAdd", (element) => {
      this.undoRedoManager.addItem({
        id: `add-${element.id}-${Date.now()}`,
        type: "add",
        elementId: element.id,
        data: element,
        timestamp: Date.now(),
      });
    });

    this.on("elementRemove", (element) => {
      this.undoRedoManager.addItem({
        id: `remove-${element.id}-${Date.now()}`,
        type: "remove",
        elementId: element.id,
        data: element,
        timestamp: Date.now(),
      });
    });
  }

  private setupAccessibility(): void {
    // Add aria labels for existing elements
    this.getElements().forEach((element) => {
      this.accessibilityManager.addAriaLabel(
        element,
        `${element.type} element`,
        `Position: ${JSON.stringify(element.xywh)}`,
        this.getAriaRole(element.type),
      );
    });

    // Setup keyboard navigation
    this.keyboardShortcutManager.register({
      key: "Tab",
      modifiers: [],
      action: () => {
        const next = this.accessibilityManager.navigateNext();
        if (next) {
          this.selectElement(next);
        }
      },
    });

    this.keyboardShortcutManager.register({
      key: "Tab",
      modifiers: ["shift"],
      action: () => {
        const prev = this.accessibilityManager.navigatePrevious();
        if (prev) {
          this.selectElement(prev);
        }
      },
    });
  }

  private setupTouchGestures(): void {
    if (!this.touchGestureManager) return;

    this.touchGestureManager.on("double-tap", (gesture) => {
      // Zoom in on double tap
      const viewport = this.getViewport();
      const point = this.screenToCanvas(gesture.center);
      viewport.setZoom(viewport.zoom * 1.5, point.x, point.y);
    });

    this.touchGestureManager.on("pinch", (gesture) => {
      // Handle pinch to zoom
      if (gesture.distance) {
        const viewport = this.getViewport();
        const scaleFactor = 1 + gesture.distance / 200;
        viewport.setZoom(
          viewport.zoom * scaleFactor,
          gesture.center.x,
          gesture.center.y,
        );
      }
    });
  }

  private setupAdvancedEventHandlers(): void {
    // Performance monitoring
    if (this.performanceMonitor) {
      const originalRender = this.render.bind(this);
      this.render = () => {
        const startTime = performance.now();
        originalRender();
        const endTime = performance.now();

        this.performanceMonitor.updateMetrics(
          this.getElements().length,
          endTime - startTime,
        );
      };
    }

    // Keyboard shortcuts
    if (this.keyboardShortcutManager) {
      this.getCanvasElement().addEventListener("keydown", (event) => {
        this.keyboardShortcutManager.handle(event);
      });
    }
  }

  private registerDefaultCommands(): void {
    // Undo
    this.commandRegistry.register({
      id: "undo",
      label: "Undo",
      description: "Undo last action",
      keyBinding: "ctrl+z",
      action: () => {
        this.undo();
      },
      precondition: () => this.undoRedoManager?.canUndo() || false,
    });

    // Redo
    this.commandRegistry.register({
      id: "redo",
      label: "Redo",
      description: "Redo last action",
      keyBinding: "ctrl+y",
      action: () => {
        this.redo();
      },
      precondition: () => this.undoRedoManager?.canRedo() || false,
    });

    // Delete
    this.commandRegistry.register({
      id: "delete",
      label: "Delete",
      description: "Delete selected elements",
      keyBinding: "delete",
      action: () => {
        this.deleteSelected();
      },
      precondition: () => this.getSelectedElements().length > 0,
    });

    // Select All
    this.commandRegistry.register({
      id: "select-all",
      label: "Select All",
      description: "Select all elements",
      keyBinding: "ctrl+a",
      action: () => {
        this.selectAll();
      },
    });

    // Clear Selection
    this.commandRegistry.register({
      id: "clear-selection",
      label: "Clear Selection",
      description: "Clear selection",
      keyBinding: "escape",
      action: () => this.clearSelection(),
    });

    // Zoom In
    this.commandRegistry.register({
      id: "zoom-in",
      label: "Zoom In",
      description: "Zoom in canvas",
      keyBinding: "ctrl+plus",
      action: () => {
        const viewport = this.getViewport();
        viewport.setZoom(viewport.zoom * 1.2);
      },
    });

    // Zoom Out
    this.commandRegistry.register({
      id: "zoom-out",
      label: "Zoom Out",
      description: "Zoom out canvas",
      keyBinding: "ctrl+minus",
      action: () => {
        const viewport = this.getViewport();
        viewport.setZoom(viewport.zoom * 0.8);
      },
    });

    // Reset Zoom
    this.commandRegistry.register({
      id: "reset-zoom",
      label: "Reset Zoom",
      description: "Reset zoom to 100%",
      keyBinding: "ctrl+0",
      action: () => {
        const viewport = this.getViewport();
        viewport.setZoom(1);
      },
    });

    // Toggle Grid
    this.commandRegistry.register({
      id: "toggle-grid",
      label: "Toggle Grid",
      description: "Toggle canvas grid",
      keyBinding: "ctrl+g",
      action: () => {
        this.triggerEvent("toggleGrid");
      },
    });

    // Export
    this.commandRegistry.register({
      id: "export",
      label: "Export",
      description: "Export canvas",
      keyBinding: "ctrl+e",
      action: () => {
        this.triggerEvent("export");
      },
    });
  }

  private setupKeyboardShortcuts(): void {
    // Additional shortcuts beyond commands
    this.keyboardShortcutManager.register({
      key: "f",
      modifiers: ["ctrl"],
      action: () => {
        this.triggerEvent("openQuickSearch");
      },
    });

    this.keyboardShortcutManager.register({
      key: "s",
      modifiers: ["ctrl"],
      action: () => {
        this.triggerEvent("save");
      },
    });
  }

  private getAriaRole(elementType: string): string {
    const roleMap: Record<string, string> = {
      shape: "img",
      text: "textbox",
      connector: "img",
      code: "code",
      diagram: "region",
      group: "region",
      brush: "img",
    };

    return roleMap[elementType] || "generic";
  }

  private getCanvasElement(): HTMLElement {
    // Access the canvas element from the parent class
    // This assumes the parent class has a canvas property or method
    return ((this as unknown as Record<string, unknown>).canvas as HTMLElement | undefined) || this.container;
  }

  private triggerEvent(eventName: string): void {
    // Trigger custom events that can be listened to by the application
    this.container.dispatchEvent(new CustomEvent(eventName));
  }

  // Public API methods
  getQuickSearchService(): QuickSearchService {
    return this.quickSearchService;
  }

  getCommandRegistry(): CanvasCommandRegistry {
    return this.commandRegistry;
  }

  getUndoRedoManager(): UndoRedoManager {
    return this.undoRedoManager;
  }

  getKeyboardShortcutManager(): KeyboardShortcutManager {
    return this.keyboardShortcutManager;
  }

  getPerformanceMonitor(): PerformanceMonitor {
    return this.performanceMonitor;
  }

  getAccessibilityManager(): AccessibilityManager {
    return this.accessibilityManager;
  }

  getCollaborationManager(): CollaborationManager | undefined {
    return this.collaborationManager;
  }

  getTouchGestureManager(): TouchGestureManager | undefined {
    return this.touchGestureManager;
  }

  getVirtualizationManager(): VirtualizationManager | undefined {
    return this.virtualizationManager;
  }

  // Enhanced methods
  undo(): boolean {
    if (!this.undoRedoManager) return false;

    const item = this.undoRedoManager.undo();
    if (!item) return false;

    switch (item.type) {
      case "add":
        const elementToAdd = this.getElements().find(
          (el) => el.id === item.elementId,
        );
        if (elementToAdd) {
          this.removeElement(elementToAdd);
        }
        break;
      case "remove":
        this.addElement(item.data as CanvasElement);
        break;
      // Handle other action types
    }

    return true;
  }

  redo(): boolean {
    if (!this.undoRedoManager) return false;

    const item = this.undoRedoManager.redo();
    if (!item) return false;

    switch (item.type) {
      case "add":
        this.addElement(item.data as CanvasElement);
        break;
      case "remove":
        const elementToRemove = this.getElements().find(
          (el) => el.id === item.elementId,
        );
        if (elementToRemove) {
          this.removeElement(elementToRemove);
        }
        break;
      // Handle other action types
    }

    return true;
  }

  deleteSelected(): void {
    const selectedElements = this.getSelectedElements();
    for (const element of selectedElements) {
      this.removeElement(element);
    }
  }

  selectAll(): void {
    const elements = this.getElements();
    for (const element of elements) {
      this.selectElement(element);
    }
  }

  executeCommand(commandId: string): boolean {
    return this.commandRegistry.execute(commandId);
  }

  private screenToCanvas(point: { x: number; y: number }): {
    x: number;
    y: number;
  } {
    const viewport = this.getViewport();
    return viewport.screenToCanvas(point);
  }

  dispose(): void {
    super.dispose();

    // Dispose advanced services
    this.performanceMonitor?.stopMonitoring();
    this.collaborationManager?.disconnect();
    this.quickSearchService?.clear();
    this.undoRedoManager?.clear();
    this.keyboardShortcutManager?.clear();
    this.accessibilityManager?.removeAriaLabel("");
  }
}
