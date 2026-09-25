/**
 * MobileLayout Component
 * Single-column mobile application shell.
 * Structure: MobileTopBar → main content (Outlet) → ChatInput (chat page) → BottomTabBar.
 *
 * Also owns responsibilities the desktop shell delegates to Sidebar (which is
 * not mounted here): session bootstrap on gateway (re)connect and agent list
 * fetching. Soft-keyboard resizing is handled via visualViewport → --app-height.
 */
import { useEffect, useRef, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { MobileTopBar } from '@/mobile/layout/MobileTopBar';
import { BottomTabBar } from '@/mobile/layout/BottomTabBar';
import { SessionDrawer } from '@/mobile/layout/SessionDrawer';
import { SettingsModal } from '@/components/settings/SettingsModal';
import { GatewayStatusBanner } from '@/components/common/GatewayStatusBanner';
import { ChatInput } from '@/pages/Chat/ChatInput';
import { useChatStore } from '@/stores/chat';
import { useGatewayStore } from '@/stores/gateway';
import { useAgentsStore } from '@/stores/agents';

/** Keep --app-height in sync with the visual viewport (soft keyboard aware). */
function useAppHeight() {
  useEffect(() => {
    const root = document.documentElement;
    const update = () => {
      const height = window.visualViewport?.height ?? window.innerHeight;
      root.style.setProperty('--app-height', `${Math.round(height)}px`);
    };
    update();
    window.visualViewport?.addEventListener('resize', update);
    window.addEventListener('resize', update);
    return () => {
      window.visualViewport?.removeEventListener('resize', update);
      window.removeEventListener('resize', update);
      root.style.removeProperty('--app-height');
    };
  }, []);
}

export function MobileLayout() {
  const location = useLocation();
  const isChatPage = location.pathname === '/';
  const [drawerOpen, setDrawerOpen] = useState(false);

  useAppHeight();

  const sendMessage = useChatStore((s) => s.sendMessage);
  const abortRun = useChatStore((s) => s.abortRun);
  const sending = useChatStore((s) => s.sending);
  const loadSessions = useChatStore((s) => s.loadSessions);
  const loadHistory = useChatStore((s) => s.loadHistory);

  // Handle initialMessage from SSO bridge (iframe embedding)
  const initialMessageHandled = useRef(false);
  useEffect(() => {
    if (initialMessageHandled.current) return;
    const state = location.state as { initialMessage?: string } | null;
    if (state?.initialMessage) {
      initialMessageHandled.current = true;
      // Clear the state to prevent re-sending
      window.history.replaceState({}, '');
      // Send the initial message after a short delay to ensure gateway is ready
      setTimeout(() => {
        sendMessage(state.initialMessage!);
      }, 500);
    }
  }, [location.state, sendMessage]);

  // Session bootstrap on gateway (re)connect — mirrors desktop Sidebar.
  const gatewayStatus = useGatewayStore((s) => s.status);
  const isGatewayRunning = gatewayStatus.state === 'running';
  const isGatewayReady = isGatewayRunning && gatewayStatus.gatewayReady !== false;
  const gatewayRuntimeKey = `${gatewayStatus.pid ?? 'none'}:${gatewayStatus.connectedAt ?? 'none'}:${gatewayStatus.port}`;

  const hasLoadedCurrentRuntimeRef = useRef(false);

  useEffect(() => {
    hasLoadedCurrentRuntimeRef.current = false;
  }, [gatewayRuntimeKey]);

  useEffect(() => {
    if (!isGatewayReady) return;
    let cancelled = false;
    (async () => {
      try {
        await loadSessions();
      } catch (error) {
        console.warn('[MobileLayout] loadSessions failed:', error);
      }
      if (cancelled) return;
      if (hasLoadedCurrentRuntimeRef.current) return;
      hasLoadedCurrentRuntimeRef.current = true;
      try {
        await loadHistory(false);
      } catch (error) {
        console.warn('[MobileLayout] loadHistory failed:', error);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [gatewayRuntimeKey, isGatewayReady, loadHistory, loadSessions]);

  // Agent names for the session drawer — desktop fetches these in Sidebar.
  const fetchAgents = useAgentsStore((s) => s.fetchAgents);
  useEffect(() => {
    void fetchAgents();
  }, [fetchAgents]);

  return (
    <div
      data-testid="mobile-layout"
      className="flex flex-col overflow-hidden bg-background"
      style={{ height: 'var(--app-height, 100dvh)' }}
    >
      {/* 1. Slim top bar */}
      <MobileTopBar onOpenDrawer={() => setDrawerOpen(true)} />

      {/* 1b. Non-blocking gateway status banner (reconnect/degraded/failed) */}
      <GatewayStatusBanner />

      {/* 2. Main content: single full-width column */}
      <main
        data-testid="mobile-main-content"
        className="relative flex-1 min-h-0 overflow-hidden bg-surface-modal"
      >
        <Outlet />
      </main>

      {/* 3. Chat input docked above the tab bar (chat page only) */}
      {isChatPage && (
        <div className="shrink-0 border-t border-black/5 dark:border-white/10 bg-surface-modal pb-safe">
          <ChatInput
            onSend={sendMessage}
            onStop={abortRun}
            disabled={false}
            sending={sending}
          />
        </div>
      )}

      {/* 4. Bottom tab navigation */}
      <BottomTabBar />

      {/* Session list drawer (left) */}
      <SessionDrawer open={drawerOpen} onOpenChange={setDrawerOpen} />

      {/* Global settings modal (opened from the top-bar gear icon) */}
      <SettingsModal />
    </div>
  );
}
