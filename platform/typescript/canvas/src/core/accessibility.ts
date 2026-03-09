import { CanvasElement } from "../elements/base.js";

export interface AccessibilityOptions {
  enableScreenReader: boolean;
  enableKeyboardNavigation: boolean;
  enableHighContrast: boolean;
  enableReducedMotion: boolean;
  fontSize: "small" | "medium" | "large";
}

export interface AriaLabel {
  element: CanvasElement;
  label: string;
  description?: string;
  role: string;
}

export class AccessibilityManager {
  private options: AccessibilityOptions;
  private ariaLabels: Map<string, AriaLabel> = new Map();
  private focusedElement: CanvasElement | null = null;
  private listeners: Set<(element: CanvasElement | null) => void> = new Set();

  constructor(options: Partial<AccessibilityOptions> = {}) {
    this.options = {
      enableScreenReader: true,
      enableKeyboardNavigation: true,
      enableHighContrast: false,
      enableReducedMotion: false,
      fontSize: "medium",
      ...options,
    };
  }

  setOptions(options: Partial<AccessibilityOptions>): void {
    this.options = { ...this.options, ...options };
    this.updateAccessibility();
  }

  getOptions(): AccessibilityOptions {
    return { ...this.options };
  }

  addAriaLabel(
    element: CanvasElement,
    label: string,
    description?: string,
    role: string = "generic",
  ): void {
    this.ariaLabels.set(element.id, {
      element,
      label,
      description,
      role,
    });
  }

  removeAriaLabel(elementId: string): void {
    this.ariaLabels.delete(elementId);
  }

  getAriaLabel(elementId: string): AriaLabel | undefined {
    return this.ariaLabels.get(elementId);
  }

  getAllAriaLabels(): AriaLabel[] {
    return Array.from(this.ariaLabels.values());
  }

  setFocusedElement(element: CanvasElement | null): void {
    this.focusedElement = element;
    this.notifyListeners();

    if (element && this.options.enableScreenReader) {
      this.announceElement(element);
    }
  }

  getFocusedElement(): CanvasElement | null {
    return this.focusedElement;
  }

  navigateNext(): CanvasElement | null {
    // Simple navigation logic - can be enhanced
    const elements = Array.from(this.ariaLabels.values()).map(
      (label) => label.element,
    );
    const currentIndex = this.focusedElement
      ? elements.indexOf(this.focusedElement)
      : -1;
    const nextIndex = (currentIndex + 1) % elements.length;

    const nextElement = elements[nextIndex] || null;
    this.setFocusedElement(nextElement);
    return nextElement;
  }

  navigatePrevious(): CanvasElement | null {
    const elements = Array.from(this.ariaLabels.values()).map(
      (label) => label.element,
    );
    const currentIndex = this.focusedElement
      ? elements.indexOf(this.focusedElement)
      : -1;
    const prevIndex =
      currentIndex <= 0 ? elements.length - 1 : currentIndex - 1;

    const prevElement = elements[prevIndex] || null;
    this.setFocusedElement(prevElement);
    return prevElement;
  }

  subscribe(listener: (element: CanvasElement | null) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private announceElement(element: CanvasElement): void {
    const ariaLabel = this.ariaLabels.get(element.id);
    if (!ariaLabel) return;

    const announcement = `${ariaLabel.label}${ariaLabel.description ? ". " + ariaLabel.description : ""}`;

    // Create a live region for screen reader announcements
    const liveRegion =
      document.getElementById("yappc-live-region") || this.createLiveRegion();
    liveRegion.textContent = announcement;
  }

  private createLiveRegion(): HTMLElement {
    const liveRegion = document.createElement("div");
    liveRegion.id = "yappc-live-region";
    liveRegion.setAttribute("aria-live", "polite");
    liveRegion.setAttribute("aria-atomic", "true");
    liveRegion.style.position = "absolute";
    liveRegion.style.left = "-10000px";
    liveRegion.style.width = "1px";
    liveRegion.style.height = "1px";
    liveRegion.style.overflow = "hidden";
    document.body.appendChild(liveRegion);
    return liveRegion;
  }

  private updateAccessibility(): void {
    // Update theme for high contrast
    if (this.options.enableHighContrast) {
      this.applyHighContrastTheme();
    } else {
      this.applyNormalTheme();
    }

    // Update font size
    this.updateFontSize();

    // Update reduced motion
    if (this.options.enableReducedMotion) {
      document.documentElement.style.setProperty(
        "--yappc-transition-duration",
        "0ms",
      );
    } else {
      document.documentElement.style.removeProperty(
        "--yappc-transition-duration",
      );
    }
  }

  private applyHighContrastTheme(): void {
    // High contrast theme implementation
    // This would need to be implemented based on your theme system
    console.log("High contrast theme would be applied here");
  }

  private applyNormalTheme(): void {
    // Reset to default theme
    // This would need to be implemented based on your theme system
  }

  private updateFontSize(): void {
    const fontSizes = {
      small: 14,
      medium: 16,
      large: 20,
    };

    const fontSize = fontSizes[this.options.fontSize];
    document.documentElement.style.setProperty(
      "--yappc-font-size",
      `${fontSize}px`,
    );
  }

  private notifyListeners(): void {
    for (const listener of this.listeners) {
      listener(this.focusedElement);
    }
  }
}

export interface CollaborationUser {
  id: string;
  name: string;
  color: string;
  cursor?: { x: number; y: number };
  selection?: string[];
}

export interface CollaborationEvent {
  type:
  | "cursor"
  | "selection"
  | "element-add"
  | "element-update"
  | "element-remove";
  userId: string;
  data: unknown;
  timestamp: number;
}

export class CollaborationManager {
  private users: Map<string, CollaborationUser> = new Map();
  private events: CollaborationEvent[] = [];
  private listeners: Set<(event: CollaborationEvent) => void> = new Set();
  private userId: string;
  private isConnected: boolean = false;

  constructor(userId: string) {
    this.userId = userId;
  }

  connect(): void {
    this.isConnected = true;
    // WebSocket connection would be established here
  }

  disconnect(): void {
    this.isConnected = false;
    this.users.clear();
    this.events = [];
  }

  addUser(user: CollaborationUser): void {
    this.users.set(user.id, user);
    this.broadcastEvent({
      type: "cursor",
      userId: user.id,
      data: user.cursor,
      timestamp: Date.now(),
    });
  }

  removeUser(userId: string): void {
    this.users.delete(userId);
  }

  updateUser(userId: string, updates: Partial<CollaborationUser>): void {
    const user = this.users.get(userId);
    if (user) {
      Object.assign(user, updates);
      this.broadcastEvent({
        type: "cursor",
        userId,
        data: updates as Partial<CollaborationUser>,
        timestamp: Date.now(),
      });
    }
  }

  sendEvent(event: Omit<CollaborationEvent, "userId" | "timestamp">): void {
    if (!this.isConnected) return;

    const fullEvent: CollaborationEvent = {
      ...event,
      userId: this.userId,
      timestamp: Date.now(),
    };

    this.broadcastEvent(fullEvent);
    // Send to server via WebSocket
  }

  subscribe(listener: (event: CollaborationEvent) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  getUsers(): CollaborationUser[] {
    return Array.from(this.users.values());
  }

  getEvents(): CollaborationEvent[] {
    return [...this.events];
  }

  private broadcastEvent(event: CollaborationEvent): void {
    this.events.push(event);

    // Keep only recent events (last 100)
    if (this.events.length > 100) {
      this.events = this.events.slice(-100);
    }

    // Notify all listeners
    for (const listener of this.listeners) {
      listener(event);
    }
  }
}

export interface TouchGesture {
  type: "tap" | "double-tap" | "long-press" | "pinch" | "pan" | "swipe";
  touches: Touch[];
  center: { x: number; y: number };
  distance?: number;
  direction?: "up" | "down" | "left" | "right";
}

export class TouchGestureManager {
  private listeners: Map<string, (gesture: TouchGesture) => void> = new Map();
  private lastTap: number = 0;
  private tapTimeout: number | null = null;
  private longPressTimeout: number | null = null;
  private initialDistance: number = 0;
  private initialCenter: { x: number; y: number } = { x: 0, y: 0 };

  constructor(private element: HTMLElement) {
    this.bindEvents();
  }

  on(eventType: string, listener: (gesture: TouchGesture) => void): void {
    this.listeners.set(eventType, listener);
  }

  off(eventType: string): void {
    this.listeners.delete(eventType);
  }

  private bindEvents(): void {
    this.element.addEventListener(
      "touchstart",
      this.handleTouchStart.bind(this),
    );
    this.element.addEventListener("touchmove", this.handleTouchMove.bind(this));
    this.element.addEventListener("touchend", this.handleTouchEnd.bind(this));
  }

  private handleTouchStart(event: TouchEvent): void {
    const touches = Array.from(event.touches);

    if (touches.length === 1) {
      // Single touch - tap, double-tap, long-press, pan, swipe
      this.startTapTimer(touches);
      this.startLongPressTimer(touches);
    } else if (touches.length === 2) {
      // Two touches - pinch
      this.startPinchGesture(touches);
    }
  }

  private handleTouchMove(event: TouchEvent): void {
    const touches = Array.from(event.touches);

    if (touches.length === 1) {
      // Clear tap and long-press timers on move
      this.clearTapTimer();
      this.clearLongPressTimer();
    } else if (touches.length === 2) {
      // Update pinch
      this.updatePinchGesture(touches);
    }
  }

  private handleTouchEnd(event: TouchEvent): void {
    const touches = Array.from(event.changedTouches);

    if (touches.length === 1) {
      // Handle tap completion
      this.handleTapCompletion(touches);
    }

    this.clearAllTimers();
  }

  private startTapTimer(touches: Touch[]): void {
    this.tapTimeout = window.setTimeout(() => {
      this.handleTap(touches);
    }, 100);
  }

  private startLongPressTimer(touches: Touch[]): void {
    this.longPressTimeout = window.setTimeout(() => {
      this.handleLongPress(touches);
    }, 500);
  }

  private startPinchGesture(touches: Touch[]): void {
    this.initialDistance = this.getDistance(touches[0], touches[1]);
    this.initialCenter = this.getCenter(touches);
  }

  private updatePinchGesture(touches: Touch[]): void {
    const currentDistance = this.getDistance(touches[0], touches[1]);
    const currentCenter = this.getCenter(touches);

    const gesture: TouchGesture = {
      type: "pinch",
      touches,
      center: currentCenter,
      distance: currentDistance - this.initialDistance,
    };

    this.emitGesture("pinch", gesture);
  }

  private handleTap(touches: Touch[]): void {
    const now = Date.now();
    const timeSinceLastTap = now - this.lastTap;

    if (timeSinceLastTap < 300) {
      // Double tap
      this.emitGesture("double-tap", {
        type: "double-tap",
        touches: [touches[0]],
        center: this.getCenter([touches[0]]),
      });
      this.lastTap = 0;
    } else {
      // Single tap
      this.emitGesture("tap", {
        type: "tap",
        touches,
        center: this.getCenter(touches),
      });
      this.lastTap = now;
    }
  }

  private handleLongPress(touches: Touch[]): void {
    this.emitGesture("long-press", {
      type: "long-press",
      touches: [touches[0]],
      center: this.getCenter([touches[0]]),
    });
  }

  private handleTapCompletion(_touches?: Touch[]): void {
    // Handle swipe detection
    // This would need more complex logic to track velocity
  }

  private emitGesture(type: string, gesture: TouchGesture): void {
    const listener = this.listeners.get(type);
    if (listener) {
      listener(gesture);
    }
  }

  private getDistance(touch1: Touch, touch2: Touch): number {
    const dx = touch1.clientX - touch2.clientX;
    const dy = touch1.clientY - touch2.clientY;
    return Math.sqrt(dx * dx + dy * dy);
  }

  private getCenter(touches: Touch[]): { x: number; y: number } {
    const sumX = touches.reduce((sum, touch) => sum + touch.clientX, 0);
    const sumY = touches.reduce((sum, touch) => sum + touch.clientY, 0);

    return {
      x: sumX / touches.length,
      y: sumY / touches.length,
    };
  }

  private clearTapTimer(): void {
    if (this.tapTimeout) {
      clearTimeout(this.tapTimeout);
      this.tapTimeout = null;
    }
  }

  private clearLongPressTimer(): void {
    if (this.longPressTimeout) {
      clearTimeout(this.longPressTimeout);
      this.longPressTimeout = null;
    }
  }

  private clearAllTimers(): void {
    this.clearTapTimer();
    this.clearLongPressTimer();
  }
}
