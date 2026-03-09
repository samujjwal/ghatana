declare module 'idb' {
  export type IDBPDatabase = any;
  export type IDBPTransaction = any;
  export type IDBPObjectStore = any;
  export function openDB(name: string, version?: number, options?: unknown): Promise<IDBPDatabase>;
}
