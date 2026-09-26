import { defineConfig, type PluginOption } from 'vite';
import react from '@vitejs/plugin-react';
import electron from 'vite-plugin-electron';
import renderer from 'vite-plugin-electron-renderer';
import tailwindcss from '@tailwindcss/vite';
import { resolve } from 'path';
import { existsSync, readFileSync } from 'fs';

function getExtensionPackages(): Set<string> {
  try {
    const manifestPath = resolve(__dirname, 'grandpoem-extensions.json');
    if (!existsSync(manifestPath)) return new Set();
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf-8'));
    const allIds: string[] = [
      ...(manifest.extensions?.main ?? []),
      ...(manifest.extensions?.renderer ?? []),
    ];
    const pkgs = new Set<string>();
    for (const id of allIds) {
      if (id.startsWith('builtin/')) continue;
      const parts = id.split('/');
      pkgs.add(parts[0].startsWith('@') ? parts.slice(0, 2).join('/') : parts[0]);
    }
    return pkgs;
  } catch {
    return new Set();
  }
}

const extensionPackages = getExtensionPackages();
const alias = {
  '@': resolve(__dirname, 'src'),
  '@electron': resolve(__dirname, 'electron'),
  '@shared': resolve(__dirname, 'shared'),
};

function isMainProcessExternal(id: string): boolean {
  if (!id || id.startsWith('\0')) return false;
  if (id.startsWith('.') || id.startsWith('/') || /^[A-Za-z]:[\\/]/.test(id)) return false;
  if (id.startsWith('@/') || id.startsWith('@electron/') || id.startsWith('@shared/')) return false;
  for (const pkg of extensionPackages) {
    if (id === pkg || id.startsWith(pkg + '/')) return false;
  }
  return true;
}

/**
 * 生产构建给渲染层文档注入 CSP。
 *
 * <p>为什么用 meta 而不是响应头：打包后渲染层由 `win.loadFile()` 以 `file://` 加载，
 * `session.webRequest.onHeadersReceived` 对 `file://` 不发火（它只覆盖 Gateway UI 的
 * http 源），所以 CSP 必须写进 HTML 本身。主窗口里的 `<webview>`（Gateway 控制台）是
 * 独立文档，不受这份策略约束。</p>
 *
 * <p>只在 build 时注入：dev 走 Vite dev server，React Refresh 需要 `unsafe-eval`、
 * HMR 需要 `ws:`，对开发期的 CSP 没有验证价值。</p>
 *
 * <p>`style-src` 保留 `'unsafe-inline'`：Tailwind/Radix/sonner 在运行时注入 `<style>`
 * 与内联样式，去掉会白屏。安全收益的主项在 `script-src 'self'`——它掐掉的是
 * XSS → 执行 JS → 经 IPC 提权这条链，而 `nodeIntegration:false` + `contextIsolation:true`
 * + `sandbox:true` 已经封住直接提权。</p>
 */
const RENDERER_CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  // /platform/* 由 src/lib/platform-fetch.ts 改走 IPC，不发网络请求；
  // 这里放行的是渲染层确实要直连的本地宿主/网关端口。
  "connect-src 'self' http://127.0.0.1:* http://localhost:* ws://127.0.0.1:* ws://localhost:*",
  // Monaco 的语言 worker 由 Vite `?worker` 打包，可能以 blob: 装载
  "worker-src 'self' blob:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'none'",
].join('; ');

function rendererCspPlugin(): PluginOption {
  return {
    name: 'jy-renderer-csp',
    apply: 'build',
    transformIndexHtml(html: string): string {
      return html.replace(
        '<title>JYparalegal</title>',
        `<title>JYparalegal</title>\n    <meta http-equiv="Content-Security-Policy" content="${RENDERER_CSP}" />`
      );
    },
  };
}

// https://vitejs.dev/config/
export default defineConfig({
  // Required for Electron: all asset URLs must be relative because the renderer
  // loads via file:// in production. vite-plugin-electron-renderer sets this
  // automatically, but we declare it explicitly so the intent is clear and the
  // build remains correct even if plugin order ever changes.
  base: './',
  plugins: [
    tailwindcss(),
    react(),
    rendererCspPlugin(),
    electron([
      {
        // Main process entry file
        entry: 'electron/main/index.ts',
        onstart(options) {
          options.startup();
        },
        vite: {
          resolve: { alias },
          build: {
            outDir: 'dist-electron/main',
            rollupOptions: {
              external: isMainProcessExternal,
            },
          },
        },
      },
      {
        // Preload scripts entry file
        entry: 'electron/preload/index.ts',
        onstart(options) {
          options.reload();
        },
        vite: {
          resolve: { alias },
          build: {
            outDir: 'dist-electron/preload',
            rollupOptions: {
              external: ['electron'],
            },
          },
        },
      },
    ]),
    renderer(),
  ],
  resolve: {
    alias,
    dedupe: ['react', 'react-dom', 'react-i18next', 'zustand', 'sonner', 'lucide-react'],
  },
  server: {
    host: '127.0.0.1',
    port: 3104,
    strictPort: false,
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
});
