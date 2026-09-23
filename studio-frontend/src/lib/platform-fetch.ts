/**
 * Route renderer `fetch('/platform/...')` through the Electron main process.
 * The desktop build has no Fastify `/platform` proxy; the main process attaches
 * the stored backend bearer token and forwards to the Java API.
 */
const originalFetch = window.fetch.bind(window);

function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === 'string') return input;
  if (input instanceof URL) return input.toString();
  return input.url;
}

window.fetch = (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
  const url = requestUrl(input);
  const isPlatform = url === '/platform' || url.startsWith('/platform/') || url.startsWith('/platform?');
  const ipc = window.electron?.ipcRenderer;
  if (!isPlatform || !ipc?.invoke) {
    return originalFetch(input, init);
  }

  const method = init?.method
    ?? (typeof input !== 'string' && !(input instanceof URL) ? input.method : 'GET');
  const body = typeof init?.body === 'string' ? init.body : null;

  return ipc.invoke('platform:fetch', { path: url, method, body }).then((raw) => {
    const result = raw as { status?: number; body?: string };
    return new Response(result.body ?? 'null', {
      status: typeof result.status === 'number' ? result.status : 502,
      headers: { 'Content-Type': 'application/json' },
    });
  });
};
