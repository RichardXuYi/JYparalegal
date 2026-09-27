/**
 * electron-store shim: plain JSON file persistence under DATA_DIR.
 *
 * Implements the subset of the electron-store API used by
 * host-core/utils/store.ts, host-core/services/backend-auth-api.ts and friends:
 * constructor({ name, defaults }), get, set, delete, clear, has, .store.
 */
import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { DATA_DIR } from '../env';

type StoreOptions<T> = {
  name?: string;
  defaults?: T;
  cwd?: string;
};

export default class Store<T extends object = Record<string, unknown>> {
  private readonly path: string;
  private readonly defaults: Partial<T>;
  private data: Record<string, unknown>;

  constructor(options: StoreOptions<T> = {}) {
    const name = options.name ?? 'config';
    const dir = options.cwd ?? DATA_DIR;
    this.path = join(dir, `${name}.json`);
    this.defaults = (options.defaults ?? {}) as Partial<T>;
    this.data = { ...this.defaults, ...this.read() };
  }

  private read(): Record<string, unknown> {
    try {
      return JSON.parse(readFileSync(this.path, 'utf8')) as Record<string, unknown>;
    } catch {
      return {};
    }
  }

  private persist(): void {
    mkdirSync(dirname(this.path), { recursive: true });
    // Write-then-rename keeps the file readable if the process dies mid-write.
    const tmp = `${this.path}.tmp`;
    writeFileSync(tmp, JSON.stringify(this.data, null, 2), 'utf8');
    renameSync(tmp, this.path);
  }

  get store(): T {
    return { ...this.data } as T;
  }

  get<K extends keyof T & string>(key: K): T[K] {
    return (this.data[key] ?? this.defaults[key]) as T[K];
  }

  set(keyOrObject: string | Record<string, unknown>, value?: unknown): void {
    if (typeof keyOrObject === 'string') {
      this.data[keyOrObject] = value;
    } else {
      Object.assign(this.data, keyOrObject);
    }
    this.persist();
  }

  has(key: string): boolean {
    return Object.prototype.hasOwnProperty.call(this.data, key);
  }

  delete(key: string): void {
    delete this.data[key];
    this.persist();
  }

  clear(): void {
    this.data = { ...this.defaults };
    this.persist();
  }
}
