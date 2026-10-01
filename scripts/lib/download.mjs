/**
 * Shared download helper for the bundled-binary scripts
 * (uv / agent-browser / node).
 *
 * Why this exists:
 *   The scripts used a bare `fetch()`. Node's built-in fetch does NOT honour
 *   HTTP_PROXY / HTTPS_PROXY on its own, so on a machine that reaches the
 *   internet only through a local proxy (common behind corporate or local VPN
 *   tooling), every download died with `ECONNRESET` / `UND_ERR_CONNECT_TIMEOUT`
 *   even though `git` worked fine.
 *
 * Behaviour:
 *   - On first download, if HTTPS_PROXY / HTTP_PROXY / ALL_PROXY (or their
 *     lowercase forms) is set, install undici's ProxyAgent as the global
 *     dispatcher so the built-in fetch routes through it.
 *   - NO_PROXY is honoured per host: bypassed hosts use a direct agent.
 *   - With no proxy env vars set, nothing is installed and fetch stays direct.
 *
 * Note: a per-request `dispatcher` option is deliberately NOT used. On modern
 * Node the global `fetch` is Node's own implementation (not undici's), and it
 * rejects that option with UND_ERR_INVALID_ARG. Setting the global dispatcher
 * is the supported way to influence it.
 */

const PROXY_ENV_KEYS = ['HTTPS_PROXY', 'https_proxy', 'HTTP_PROXY', 'http_proxy', 'ALL_PROXY', 'all_proxy'];
const NO_PROXY_ENV_KEYS = ['NO_PROXY', 'no_proxy'];

function readEnv(keys) {
  for (const key of keys) {
    const value = process.env[key];
    if (typeof value === 'string' && value.trim()) return value.trim();
  }
  return '';
}

function hostOf(url) {
  try {
    return new URL(url).hostname.toLowerCase();
  } catch {
    return '';
  }
}

/** True when NO_PROXY lists this host (exact, suffix, or '*'). */
function isBypassed(host) {
  const noProxy = readEnv(NO_PROXY_ENV_KEYS);
  if (!noProxy || !host) return false;
  return noProxy.split(',').some((raw) => {
    const entry = raw.trim().toLowerCase().replace(/^\./, '');
    if (!entry) return false;
    if (entry === '*') return true;
    return host === entry || host.endsWith(`.${entry}`);
  });
}

let proxyInstalled = false;

/**
 * Install the proxy dispatcher once per process, if a proxy is configured.
 * Returns the proxy URL that was applied, or '' when running direct.
 */
export async function installProxyDispatcher() {
  if (proxyInstalled) return proxyInstalled === true ? 'installed' : '';
  proxyInstalled = true;

  const proxy = readEnv(PROXY_ENV_KEYS);
  if (!proxy) return '';

  try {
    const { ProxyAgent, setGlobalDispatcher } = await import('undici');
    setGlobalDispatcher(new ProxyAgent(proxy));
    console.log(`   (proxy) downloads routed via ${proxy}`);
    return proxy;
  } catch (error) {
    proxyInstalled = false;
    console.warn(
      `   (proxy) ${proxy} is set but undici's ProxyAgent could not be loaded `
      + `(${error instanceof Error ? error.message : String(error)}); downloading directly.`,
    );
    return '';
  }
}

/**
 * fetch() that respects the standard proxy environment variables.
 *
 * @param {string} url
 * @param {RequestInit} [init]
 * @param {{ allowProxy?: boolean }} [options] allowProxy=false forces a direct
 *   request for hosts listed in NO_PROXY.
 */
export async function fetchWithProxy(url, init = {}, options = {}) {
  const allowProxy = options.allowProxy ?? !isBypassed(hostOf(url));
  if (allowProxy) await installProxyDispatcher();
  return fetch(url, init);
}

/** GET + Buffer with proxy support and a helpful error message. */
export async function downloadToBuffer(url) {
  const response = await fetchWithProxy(url);
  if (!response.ok) {
    throw new Error(`Failed to download ${url}: ${response.status} ${response.statusText}`);
  }
  return Buffer.from(await response.arrayBuffer());
}