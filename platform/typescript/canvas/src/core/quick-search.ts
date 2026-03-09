import { CanvasElement } from "../elements/base.js";

export interface QuickSearchItem<T = unknown> {
  id: string;
  source: string;
  label: {
    title: string;
    subTitle?: string;
  };
  group?: QuickSearchGroup;
  score: number;
  icon?: string;
  keyBinding?: string;
  payload: T;
}

export interface QuickSearchGroup {
  id: string;
  label: {
    i18nKey?: string;
    title?: string;
  };
  score: number;
}

export interface QuickSearchOptions {
  placeholder?: string;
  label?: string;
  maxResults?: number;
  debounceMs?: number;
}

export interface QuickSearchResult<T = unknown> {
  item: QuickSearchItem<T>;
  action: () => void | Promise<void>;
}

export interface QuickSearchSession<S extends string = string, T = unknown> {
  query$: {
    next: (query: string) => void;
    value: string;
  };
  items$: {
    value: QuickSearchItem<T>[];
  };
  query: (query: string) => void;
}

export class QuickSearchService {
  private sessions: Map<string, QuickSearchSession> = new Map();
  private listeners: Set<(results: QuickSearchItem<unknown>[]) => void> =
    new Set();
  private currentQuery: string = "";
  private debounceTimer: number | null = null;

  registerSession(id: string, session: QuickSearchSession): void {
    this.sessions.set(id, session);

    // Subscribe to session changes
    if ("items$" in session && typeof session.items$.value !== "undefined") {
      // For reactive sessions
      const updateResults = () => {
        const allItems = Array.from(this.sessions.values()).flatMap(
          (session) => session.items$?.value || [],
        );
        this.notifyListeners(allItems);
      };

      // Simple subscription simulation
      session.query$.next = (query: string) => {
        this.currentQuery = query;
        session.query(query);
        updateResults();
      };
    }
  }

  unregisterSession(id: string): void {
    this.sessions.delete(id);
  }

  search(
    query: string,
    options: QuickSearchOptions = {},
  ): QuickSearchItem<unknown>[] {
    this.currentQuery = query;

    const allItems: QuickSearchItem<unknown>[] = [];

    for (const session of this.sessions.values()) {
      session.query(query);
      if (session.items$?.value) {
        allItems.push(...session.items$.value);
      }
    }

    // Sort by score and limit results
    const sortedItems = allItems
      .sort((a, b) => b.score - a.score)
      .slice(0, options.maxResults || 50);

    this.notifyListeners(sortedItems);
    return sortedItems;
  }

  subscribe(
    listener: (results: QuickSearchItem<unknown>[]) => void,
  ): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notifyListeners(results: QuickSearchItem<unknown>[]): void {
    for (const listener of this.listeners) {
      listener(results);
    }
  }

  clear(): void {
    this.currentQuery = "";
    for (const session of this.sessions.values()) {
      session.query("");
    }
    this.notifyListeners([]);
  }
}

export class CanvasQuickSearchSession implements QuickSearchSession<
  "canvas",
  CanvasElement
> {
  query$ = { next: (query: string) => {}, value: "" };
  items$ = { value: [] as QuickSearchItem<CanvasElement>[] };

  constructor(private elements: CanvasElement[]) {}

  query(query: string): void {
    this.query$.value = query;

    if (!query) {
      this.items$.value = this.elements.map((element) => ({
        id: `element:${element.id}`,
        source: "canvas",
        label: {
          title: element.type,
          subTitle: element.id,
        },
        group: {
          id: "canvas:elements",
          label: { title: "Canvas Elements" },
          score: 10,
        },
        score: 1,
        payload: element,
      }));
      return;
    }

    const filtered = this.elements.filter(
      (element) =>
        element.type.toLowerCase().includes(query.toLowerCase()) ||
        element.id.toLowerCase().includes(query.toLowerCase()),
    );

    this.items$.value = filtered.map((element) => ({
      id: `element:${element.id}`,
      source: "canvas",
      label: {
        title: element.type,
        subTitle: element.id,
      },
      group: {
        id: "canvas:elements",
        label: { title: "Canvas Elements" },
        score: 10,
      },
      score: query.length > 0 ? 2 : 1,
      payload: element,
    }));
  }
}

export class CommandQuickSearchSession implements QuickSearchSession<
  "commands",
  CanvasCommand
> {
  query$ = { next: () => {}, value: "" };
  items$ = { value: [] as QuickSearchItem<CanvasCommand>[] };

  constructor(private commands: CanvasCommand[]) {}

  query(query: string): void {
    this.query$.value = query;

    const commands = this.commands.filter(
      (cmd) =>
        !query ||
        cmd.label.toLowerCase().includes(query.toLowerCase()) ||
        cmd.description?.toLowerCase().includes(query.toLowerCase()),
    );

    this.items$.value = commands.map((command) => ({
      id: `command:${command.id}`,
      source: "commands",
      label: {
        title: command.label,
        subTitle: command.description,
      },
      group: {
        id: "commands:canvas",
        label: { title: "Commands" },
        score: 20,
      },
      score: 1,
      icon: command.icon,
      keyBinding: command.keyBinding,
      payload: command,
    }));
  }
}

export interface CanvasCommand {
  id: string;
  label: string;
  description?: string;
  icon?: string;
  keyBinding?: string;
  action: () => void | Promise<void>;
  precondition?: () => boolean;
}

export class CanvasCommandRegistry {
  private commands: Map<string, CanvasCommand> = new Map();

  register(command: CanvasCommand): void {
    this.commands.set(command.id, command);
  }

  unregister(id: string): void {
    this.commands.delete(id);
  }

  get(id: string): CanvasCommand | undefined {
    return this.commands.get(id);
  }

  getAll(): CanvasCommand[] {
    return Array.from(this.commands.values());
  }

  execute(id: string): boolean {
    const command = this.commands.get(id);
    if (!command) return false;

    if (command.precondition && !command.precondition()) {
      return false;
    }

    command.action();
    return true;
  }
}
