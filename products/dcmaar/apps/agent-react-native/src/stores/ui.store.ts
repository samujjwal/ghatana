/**
 * UI State Management Store - Jotai Atoms
 *
 * Manages application-wide UI state including:
 * - Navigation and screen state
 * - Theme (dark/light mode)
 * - Modal and bottom sheet visibility
 * - Toast/notification display
 * - Sidebar/menu states
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization
 * - Atomic updates for predictable state
 *
 * @doc.type module
 * @doc.purpose UI state management
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';

/**
 * Notification to display to user.
 *
 * @interface Notification
 * @property {string} id - Unique notification ID
 * @property {'info' | 'success' | 'warning' | 'error'} type - Notification type
 * @property {string} message - Display message
 * @property {number} duration - Milliseconds to show (0 = indefinite)
 * @property {Date} createdAt - When notification was created
 */
export interface Notification {
  id: string;
  type: 'info' | 'success' | 'warning' | 'error';
  message: string;
  duration: number;
  createdAt: Date;
}

/**
 * UI state configuration.
 *
 * @interface UIState
 * @property {'dark' | 'light'} theme - Current theme
 * @property {string} currentScreen - Active screen name
 * @property {Record<string, boolean>} modalsOpen - Modal visibility by name
 * @property {Notification[]} notifications - Active notifications
 * @property {boolean} isSidebarOpen - Sidebar visibility (mobile)
 * @property {boolean} isLoading - Global loading state
 */
export interface UIState {
  theme: 'dark' | 'light';
  currentScreen: string;
  modalsOpen: Record<string, boolean>;
  notifications: Notification[];
  isSidebarOpen: boolean;
  isLoading: boolean;
}

/**
 * Initial UI state.
 *
 * GIVEN: App initialization
 * WHEN: uiAtom is first accessed
 * THEN: UI starts with light theme, no modals open
 */
const initialUIState: UIState = {
  theme: 'light',
  currentScreen: 'home',
  modalsOpen: {},
  notifications: [],
  isSidebarOpen: false,
  isLoading: false,
};

/**
 * Core UI atom.
 *
 * Holds complete UI state including:
 * - Theme preference
 * - Navigation state
 * - Modal visibility
 * - Notifications
 *
 * Usage (in components):
 * `const [ui, setUI] = useAtom(uiAtom);`
 */
export const uiAtom = atom<UIState>(initialUIState);

/**
 * Derived atom: Current screen.
 *
 * GIVEN: uiAtom with currentScreen
 * WHEN: currentScreenAtom is read
 * THEN: Returns name of active screen
 *
 * Usage (in components):
 * `const [screen] = useAtom(currentScreenAtom);`
 * Render correct screen based on currentScreen
 */
export const currentScreenAtom = atom<string>((get) => {
  return get(uiAtom).currentScreen;
});

/**
 * Derived atom: Is modal open?
 *
 * GIVEN: uiAtom with modalsOpen
 * WHEN: isModalOpenAtom is read with modalName
 * THEN: Returns boolean for that modal's visibility
 *
 * Usage (in components):
 * `const getModalState = useAtomValue(isModalOpenAtom);`
 * `const isOpen = getModalState('settings');`
 */
export const isModalOpenAtom = atom((get) => {
  const ui = get(uiAtom);
  return (modalName: string) => ui.modalsOpen[modalName] || false;
});

/**
 * Derived atom: Active notifications.
 *
 * GIVEN: uiAtom with notifications
 * WHEN: activeNotificationsAtom is read
 * THEN: Returns all active notifications
 *
 * Usage (in components):
 * `const [notifications] = useAtom(activeNotificationsAtom);`
 * Render notification toasts
 */
export const activeNotificationsAtom = atom<Notification[]>((get) => {
  return get(uiAtom).notifications;
});

/**
 * Derived atom: Current theme.
 *
 * GIVEN: uiAtom with theme
 * WHEN: themeAtom is read
 * THEN: Returns current theme ('dark' or 'light')
 *
 * Usage (in components):
 * `const [theme] = useAtom(themeAtom);`
 * Apply theme colors to UI
 */
export const themeAtom = atom<'dark' | 'light'>((get) => {
  return get(uiAtom).theme;
});

/**
 * Action atom: Navigate to screen.
 *
 * GIVEN: User navigates or app routes
 * WHEN: navigateToAtom is called
 * THEN: Sets currentScreen
 *       Closes sidebar on mobile
 *
 * Usage (in components):
 * `const [, navigateTo] = useAtom(navigateToAtom);`
 * navigateTo('device-list');
 */
export const navigateToAtom = atom<null, [screenName: string], void>(
  null,
  (get, set, screenName: string) => {
    const ui = get(uiAtom);
    set(uiAtom, {
      ...ui,
      currentScreen: screenName,
      isSidebarOpen: false, // Close sidebar on navigation
    });
  }
);

/**
 * Action atom: Toggle modal visibility.
 *
 * GIVEN: User opens or closes modal
 * WHEN: toggleModalAtom is called
 * THEN: Toggles isOpen state for modal
 *
 * Usage (in components):
 * `const [, toggleModal] = useAtom(toggleModalAtom);`
 * toggleModal('settings-modal');
 */
export const toggleModalAtom = atom<null, [modalName: string], void>(
  null,
  (get, set, modalName: string) => {
    const ui = get(uiAtom);
    set(uiAtom, {
      ...ui,
      modalsOpen: {
        ...ui.modalsOpen,
        [modalName]: !ui.modalsOpen[modalName],
      },
    });
  }
);

/**
 * Action atom: Open modal.
 *
 * GIVEN: Modal should be displayed
 * WHEN: openModalAtom is called
 * THEN: Sets modal to open
 *
 * Usage (in components):
 * `const [, openModal] = useAtom(openModalAtom);`
 * openModal('policy-creation');
 */
export const openModalAtom = atom<null, [modalName: string], void>(
  null,
  (get, set, modalName: string) => {
    const ui = get(uiAtom);
    set(uiAtom, {
      ...ui,
      modalsOpen: {
        ...ui.modalsOpen,
        [modalName]: true,
      },
    });
  }
);

/**
 * Action atom: Close modal.
 *
 * GIVEN: Modal should be hidden
 * WHEN: closeModalAtom is called
 * THEN: Sets modal to closed
 *
 * Usage (in components):
 * `const [, closeModal] = useAtom(closeModalAtom);`
 * closeModal('settings-modal');
 */
export const closeModalAtom = atom<null, [modalName: string], void>(
  null,
  (get, set, modalName: string) => {
    const ui = get(uiAtom);
    set(uiAtom, {
      ...ui,
      modalsOpen: {
        ...ui.modalsOpen,
        [modalName]: false,
      },
    });
  }
);

/**
 * Action atom: Toggle theme.
 *
 * GIVEN: User clicks theme toggle
 * WHEN: toggleThemeAtom is called
 * THEN: Switches between dark and light
 *
 * Usage (in components):
 * `const [, toggleTheme] = useAtom(toggleThemeAtom);`
 * toggleTheme();
 */
export const toggleThemeAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const ui = get(uiAtom);
    set(uiAtom, {
      ...ui,
      theme: ui.theme === 'dark' ? 'light' : 'dark',
    });
  }
);

/**
 * Action atom: Show notification.
 *
 * GIVEN: Event that should notify user
 * WHEN: showNotificationAtom is called
 * THEN: Adds notification to queue
 *       Auto-removes after duration
 *
 * Usage (in components):
 * `const [, showNotif] = useAtom(showNotificationAtom);`
 * await showNotif({
 *   type: 'success',
 *   message: 'Policy applied successfully',
 *   duration: 3000
 * });
 */
export const showNotificationAtom = atom<
  null,
  [Omit<Notification, 'id' | 'createdAt'>],
  Promise<void>
>(
  null,
  async (get, set, notifData) => {
    const ui = get(uiAtom);

    const notification: Notification = {
      id: 'notif-' + Date.now(),
      ...notifData,
      createdAt: new Date(),
    };

    set(uiAtom, {
      ...ui,
      notifications: [...ui.notifications, notification],
    });

    // Auto-remove after duration if duration > 0
    if (notifData.duration > 0) {
      await new Promise((resolve) => setTimeout(resolve, notifData.duration));

      // Remove notification
      const current = get(uiAtom);
      set(uiAtom, {
        ...current,
        notifications: current.notifications.filter((n) => n.id !== notification.id),
      });
    }
  }
);

/**
 * Action atom: Dismiss notification.
 *
 * GIVEN: Notification displayed
 * WHEN: dismissNotificationAtom is called
 * THEN: Removes notification from queue
 *
 * Usage (in components):
 * `const [, dismiss] = useAtom(dismissNotificationAtom);`
 * dismiss('notif-123');
 */
export const dismissNotificationAtom = atom<null, [notificationId: string], void>(
  null,
  (get, set, notificationId: string) => {
    const ui = get(uiAtom);
    set(uiAtom, {
      ...ui,
      notifications: ui.notifications.filter((n) => n.id !== notificationId),
    });
  }
);

/**
 * Action atom: Set global loading state.
 *
 * GIVEN: Operation is in progress
 * WHEN: setLoadingAtom is called
 * THEN: Sets or clears global loading flag
 *
 * Usage (in components):
 * `const [, setLoading] = useAtom(setLoadingAtom);`
 * setLoading(true); // Show loading
 * setLoading(false); // Hide loading
 */
export const setLoadingAtom = atom<null, [isLoading: boolean], void>(
  null,
  (get, set, isLoading: boolean) => {
    const ui = get(uiAtom);
    set(uiAtom, {
      ...ui,
      isLoading,
    });
  }
);

/**
 * Action atom: Toggle sidebar.
 *
 * GIVEN: Mobile user taps menu icon
 * WHEN: toggleSidebarAtom is called
 * THEN: Toggles sidebar visibility
 *
 * Usage (in components):
 * `const [, toggleSidebar] = useAtom(toggleSidebarAtom);`
 * toggleSidebar();
 */
export const toggleSidebarAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const ui = get(uiAtom);
    set(uiAtom, {
      ...ui,
      isSidebarOpen: !ui.isSidebarOpen,
    });
  }
);

/**
 * Action atom: Close sidebar.
 *
 * GIVEN: Sidebar is open
 * WHEN: closeSidebarAtom is called
 * THEN: Sets sidebar to closed
 *
 * Usage (in components):
 * `const [, closeSidebar] = useAtom(closeSidebarAtom);`
 * closeSidebar();
 */
export const closeSidebarAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const ui = get(uiAtom);
    set(uiAtom, {
      ...ui,
      isSidebarOpen: false,
    });
  }
);
