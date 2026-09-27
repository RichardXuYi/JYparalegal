import { defineConfig, loadEnv } from 'vite';
import type { ProxyOptions } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { resolve } from 'path';
import { visualizer } from 'rollup-plugin-visualizer';

// ---------------------------------------------------------------------------
// Path aliases — single source of truth shared with vitest.config.ts
// ---------------------------------------------------------------------------
export const alias = {
  '@': resolve(__dirname, 'src'),
  '@shared': resolve(__dirname, 'shared'),
} as const;

// ---------------------------------------------------------------------------
// Shared vendor packages — referenced by both optimizeDeps.include and
// resolve.dedupe. Adding a package here keeps dev startup fast AND avoids
// pnpm hoisting edge cases where nested deps resolve to different copies.
// ---------------------------------------------------------------------------
const SHARED_VENDOR = [
  'react',
  'react-dom',
  'react-router-dom',
  'i18next',
  'react-i18next',
  'zustand',
  'sonner',
  'lucide-react',
  'motion',
];

// ---------------------------------------------------------------------------
// Heavy/lazy dependencies that should be pre-bundled by Vite for faster dev
// startup and split into separate chunks for production caching. These extend
// the shared vendor list with bundles that are typically loaded on-demand
// (editor, markdown, spreadsheet).
// ---------------------------------------------------------------------------
const HEAVY_DEPS = [
  ...SHARED_VENDOR,
  'monaco-editor',
  '@monaco-editor/react',
  'react-markdown',
  'remark-gfm',
  'rehype-katex',
  'xlsx',
];

// ---------------------------------------------------------------------------
// Proxy targets — shared between the dev server and the preview server so
// both stay in sync. Each entry rewrites to the same backend regardless of
// which local server is serving the SPA.
// ---------------------------------------------------------------------------
interface ProxyEndpoints {
  hostServerUrl: string;
  hostWsUrl: string;
  backendUrl: string;
  isProduction: boolean;
}

const createProxyConfig = ({
  hostServerUrl,
  hostWsUrl,
  backendUrl,
  isProduction,
}: ProxyEndpoints): Record<string, ProxyOptions> => ({
  // Host service (WebSocket + auth endpoints)
  '/ws': {
    target: hostWsUrl,
    ws: true,
    changeOrigin: true,
  },
  '/auth': {
    target: hostServerUrl,
    changeOrigin: true,
    secure: isProduction,
  },
  // JY backend (Spring Boot)
  '/api': {
    target: backendUrl,
    changeOrigin: true,
    secure: isProduction,
    // Rewrite path if needed
    // rewrite: (path) => path.replace(/^\/api/, ''),
  },
  // 平台业务代理：/platform/** → 宿主服务（由它带存储的 Bearer token 转发到 Java backend）。
  // 缺此条时 dev 下平台页（签署/总览/模板/证据/法庭/语音等）全部拿不到数据（审阅 #24）。
  '/platform': {
    target: hostServerUrl,
    changeOrigin: true,
    secure: isProduction,
  },
});

// ---------------------------------------------------------------------------
// Asset filename strategy — group static assets into predictable directories
// so the CDN / web-server cache rules can target them individually.
// ---------------------------------------------------------------------------
const assetFileName = (assetInfo: { name?: string }): string => {
  const name = assetInfo.name ?? '';
  if (/\.(png|jpe?g|gif|svg|webp|ico)$/i.test(name)) {
    return 'assets/images/[name]-[hash][extname]';
  }
  if (/\.(woff2?|eot|ttf|otf)$/i.test(name)) {
    return 'assets/fonts/[name]-[hash][extname]';
  }
  if (/\.css$/i.test(name)) {
    return 'assets/css/[name]-[hash][extname]';
  }
  return `assets/[name]-[hash][extname]`;
};

// ---------------------------------------------------------------------------
// Manual chunk strategy — keep heavyweight editor/markdown stacks out of the
// boot chunk so the initial load stays lean. Returns the chunk name for a
// given module id, or `undefined` to let Rollup decide.
// ---------------------------------------------------------------------------
const chunkClassifier = (id: string): string | undefined => {
  if (!id.includes('node_modules')) return undefined;

  // Monaco editor → own chunk (large, lazy-loaded)
  if (id.includes('monaco-editor') || id.includes('@monaco-editor')) {
    return 'monaco';
  }
  // Markdown rendering pipeline
  if (
    id.includes('react-markdown') ||
    id.includes('remark-') ||
    id.includes('rehype-') ||
    id.includes('katex')
  ) {
    return 'markdown';
  }
  // Spreadsheet processing
  if (id.includes('xlsx')) {
    return 'xlsx';
  }
  // Radix UI primitives → shared vendor chunk
  if (id.includes('@radix-ui')) {
    return 'vendor-ui';
  }
  // React core + ecosystem
  if (
    id.includes('/react/') ||
    id.includes('/react-dom/') ||
    id.includes('/react-router') ||
    id.includes('/zustand/') ||
    id.includes('/i18next/')
  ) {
    return 'vendor-core';
  }
  // Animation & icon libraries
  if (
    id.includes('/motion/') ||
    id.includes('lucide-react') ||
    id.includes('sonner')
  ) {
    return 'vendor-ux';
  }
  // Utility libraries
  if (
    id.includes('lodash') ||
    id.includes('dayjs') ||
    id.includes('date-fns') ||
    id.includes('clsx') ||
    id.includes('tailwind-merge')
  ) {
    return 'vendor-utils';
  }
  return undefined;
};

// ---------------------------------------------------------------------------
// https://vitejs.dev/config/
// ---------------------------------------------------------------------------
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  // Proxy targets are driven by .env so each environment can point at a
  // different backend / host server without touching the config.
  const hostServerUrl = env.VITE_HOST_SERVER_URL ?? 'http://localhost:8788';
  const hostWsUrl = env.VITE_HOST_WS_URL ?? 'ws://localhost:8788';
  const backendUrl = env.VITE_BACKEND_URL ?? 'http://localhost:8181';
  const isProduction = mode === 'production';

  // Built once and reused by both `server` and `preview` so the two stay in
  // sync automatically when proxy rules change.
  const proxyConfig = createProxyConfig({
    hostServerUrl,
    hostWsUrl,
    backendUrl,
    isProduction,
  });

  return {
    base: '/',

    plugins: [
      tailwindcss(),
      react(),
      // Bundle analyzer for production builds (optional, enabled via env)
      mode === 'analyze' && visualizer({
        open: true,
        gzipSize: true,
        brotliSize: true,
        filename: 'dist/stats.html',
      }),
    ].filter(Boolean),

    resolve: {
      alias,
      // Ensure a single instance of these packages even when nested deps
      // resolve to different copies (pnpm hoisting edge-cases).
      dedupe: SHARED_VENDOR,
    },

    // -----------------------------------------------------------------------
    // Asset optimization
    // -----------------------------------------------------------------------
    assetsInclude: ['**/*.svg', '**/*.png', '**/*.jpg', '**/*.jpeg', '**/*.gif', '**/*.webp'],

    // -----------------------------------------------------------------------
    // Dev server
    // -----------------------------------------------------------------------
    server: {
      host: '127.0.0.1',
      port: Number(env.VITE_DEV_SERVER_PORT) || 3105,
      strictPort: false,
      // Optimize hot module replacement
      hmr: {
        overlay: true,
        timeout: 5000,
      },
      // File watcher optimizations for faster HMR
      watch: {
        usePolling: false,
        interval: 100,
        ignored: ['**/node_modules/**', '**/dist/**'],
      },
      // CORS and security headers
      headers: {
        'Cross-Origin-Embedder-Policy': 'credentialless',
        'Cross-Origin-Opener-Policy': 'same-origin',
      },
      proxy: proxyConfig,
    },

    // -----------------------------------------------------------------------
    // Dependency pre-bundling — speeds up cold-start of the dev server by
    // bundling large / frequently-imported packages once into cached chunks.
    // -----------------------------------------------------------------------
    optimizeDeps: {
      include: HEAVY_DEPS,
      // Monaco editor workers
      exclude: ['monaco-editor'],
      esbuildOptions: {
        target: 'es2022',
        // Better source map support for debugging
        sourcemap: mode === 'development' ? 'linked' : false,
      },
      // Force optimization on these packages even if they are ESM
      needsInterop: [],
    },

    // -----------------------------------------------------------------------
    // CSS optimization
    // -----------------------------------------------------------------------
    css: {
      devSourcemap: mode === 'development',
      transformer: 'postcss',
      // Preprocessor options if needed
      preprocessorOptions: {
        // Add global sass/less variables if needed
      },
    },

    // -----------------------------------------------------------------------
    // JSON optimization
    // -----------------------------------------------------------------------
    json: {
      stringify: false,
      namedExports: true,
    },

    // -----------------------------------------------------------------------
    // Production build
    // -----------------------------------------------------------------------
    build: {
      outDir: 'dist',
      emptyOutDir: true,
      target: 'es2022',
      // Use esbuild for faster builds (much faster than terser)
      minify: 'esbuild',
      sourcemap: mode === 'production' ? false : 'hidden',
      cssCodeSplit: true,
      cssMinify: mode === 'production',
      // Chunk size warning limit (500KB)
      chunkSizeWarningLimit: 500,
      // CommonJS to ESM conversion
      commonjsOptions: {
        include: [/node_modules/],
        extensions: ['.js', '.cjs'],
        transformMixedEsModules: true,
      },
      rollupOptions: {
        output: {
          // Better asset naming
          assetFileNames: assetFileName,
          chunkFileNames: 'assets/js/[name]-[hash].js',
          entryFileNames: 'assets/js/[name]-[hash].js',
          // Smart code-splitting: keep heavyweight editor/markdown stacks out
          // of the boot chunk so the initial load stays lean.
          manualChunks: chunkClassifier,
        },
      },
    },

    // -----------------------------------------------------------------------
    // Preview server (for testing production build locally)
    // -----------------------------------------------------------------------
    preview: {
      host: '127.0.0.1',
      port: 4173,
      strictPort: false,
      open: false,
      proxy: proxyConfig,
    },
  };
});
