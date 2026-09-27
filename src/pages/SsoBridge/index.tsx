/**
 * SSO Bridge Page
 *
 * Handles SSO login from iframe embedding scenarios (e.g., app-frontend).
 * Receives the app-frontend accessToken via postMessage (preferred) or URL
 * hash (legacy fallback), validates it against the backend, stores the studio
 * JWT in localStorage, and navigates to the main app.
 *
 * URL format (legacy): /#/sso#tk=<accessToken>&msg=<initialMessage>
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, Loader2 } from 'lucide-react';
import { useAuthStore } from '@/stores/auth';
import { TitleBar } from '@/components/layout/TitleBar';
import { resetAllUserStores } from '@/lib/user-session-reset';

export function SsoBridge() {
  const navigate = useNavigate();
  const restore = useAuthStore((s) => s.restore);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const handledRef = useRef(false);

  // Core SSO login: exchange the app-frontend accessToken for a studio JWT,
  // persist it, restore auth state, and navigate to the main chat page.
  // Shared by both the URL-hash fallback and the postMessage path.
  const handleSsoLogin = useCallback(
    (accessToken: string, message?: string | null) => {
      if (handledRef.current) return;
      handledRef.current = true;

      // Call SSO login endpoint
      fetch('/auth/sso-login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ accessToken }),
      })
        .then((r) => r.json())
        .then(async (data) => {
          if (data.success && data.token) {
            // The SSO account may differ from any previous session: wipe
            // per-account stores (this also clears any old studio-jwt) and
            // force a fresh WS bound to the new JWT before hydrating auth.
            resetAllUserStores();
            // Store studio JWT in localStorage (the bridge reads it when
            // building the reconnect URL, so store it before reconnecting).
            localStorage.setItem('studio-jwt', data.token);
            window.__grandpoemBridge?.reconnect();
            // Clear URL hash (remove token from URL for security)
            window.history.replaceState(null, '', window.location.pathname);
            // Restore auth state (will use the new JWT)
            await restore();
            // Navigate to main chat page with optional initial message
            navigate('/', {
              replace: true,
              state: { initialMessage: message || undefined },
            });
          } else {
            setError(data.error || '登录失败');
            setLoading(false);
          }
        })
        .catch(() => {
          setError('网络错误');
          setLoading(false);
        });
    },
    [navigate, restore],
  );

  useEffect(() => {
    // Fallback: parse accessToken from URL hash (compat with legacy embedders
    // that still pass the token in the hash). New embedders use postMessage.
    // window.location.hash = "#/sso#tk=xxx&msg=yyy"
    const fullHash = window.location.hash;
    // Find the second # which starts the params
    const secondHashIndex = fullHash.indexOf('#', 1);
    const paramString = secondHashIndex >= 0 ? fullHash.slice(secondHashIndex + 1) : '';
    const params = new URLSearchParams(paramString);
    const hashToken = params.get('tk');
    const hashMessage = params.get('msg');

    if (hashToken) {
      handleSsoLogin(hashToken, hashMessage);
      return;
    }

    // No token in URL: listen for a postMessage from the embedding parent
    // (e.g. app-frontend chat overlay). Only accept messages from trusted origins.
    const allowedOrigins = [
      'http://localhost:3102',
      import.meta.env.VITE_APP_FRONTEND_URL,
    ].filter(Boolean) as string[];

    const handler = (event: MessageEvent) => {
      if (!allowedOrigins.includes(event.origin)) return;
      if (event.data?.type === 'sso-token' && event.data.accessToken) {
        handleSsoLogin(event.data.accessToken, event.data.message);
      }
    };
    window.addEventListener('message', handler);

    // Safety net: if no token arrives (e.g. direct access to /#/sso), surface
    // an error instead of spinning forever.
    const timeoutId = window.setTimeout(() => {
      if (!handledRef.current) {
        setError('缺少认证信息');
        setLoading(false);
      }
    }, 3000);

    return () => {
      window.removeEventListener('message', handler);
      window.clearTimeout(timeoutId);
    };
  }, [handleSsoLogin]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-background relative">
      <div className="absolute inset-x-0 top-0 z-20">
        <TitleBar />
      </div>
      <div className="flex flex-col items-center gap-4 p-8">
        {loading && !error && (
          <>
            <Loader2 className="h-12 w-12 animate-spin text-primary" />
            <p className="text-muted-foreground">正在登录...</p>
          </>
        )}
        {error && (
          <>
            <AlertCircle className="h-12 w-12 text-destructive" />
            <p className="text-destructive">{error}</p>
            <p className="text-sm text-muted-foreground">请关闭此窗口后重试</p>
          </>
        )}
      </div>
    </div>
  );
}
