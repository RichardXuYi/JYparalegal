/**
 * Main Layout Component
 * Platform-aware application shell.
 * Structure: TopBar → Agent / 智能法务 → contextual sidebar + main content
 */
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { LegalSidebar } from './LegalSidebar';
import { TopBar } from './TopBar';
import { PlatformTabs } from './PlatformTabs';
import { isLegalSection } from './nav-config';
import { SettingsModal } from '@/components/settings/SettingsModal';
import { PurchaseGateModal } from '@/components/PurchaseGateModal';
import { GatewayStatusBanner } from '@/components/common/GatewayStatusBanner';
import { ChatInput } from '@/pages/Chat/ChatInput';
import { useChatStore } from '@/stores/chat';
import { MAC_SIDEBAR_CHROME_HEIGHT } from '@shared/sidebar-layout';

export function MainLayout() {
  const platform = window.electron?.platform;
  const isMac = platform === 'darwin';
  const location = useLocation();
  const isChatPage = location.pathname === '/';
  const isLegal = isLegalSection(location.pathname);
  const showContextSidebar = isChatPage || isLegal;

  const sendMessage = useChatStore((s) => s.sendMessage);
  const abortRun = useChatStore((s) => s.abortRun);
  const sending = useChatStore((s) => s.sending);

  return (
    <div
      data-testid="main-layout"
      data-platform={platform}
      className="shell-gradient flex h-screen flex-col overflow-hidden"
    >
      <TopBar />
      <PlatformTabs />
      <GatewayStatusBanner />

      <div className="flex min-h-0 flex-1 overflow-hidden">
        {showContextSidebar && (
          <div className="flex min-h-0 shrink-0 overflow-hidden bg-transparent">
            {isLegal ? <LegalSidebar /> : <Sidebar />}
          </div>
        )}

        <div className="m-2 ml-1 flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-2xl bg-surface-modal shadow-shell">
          <main
            data-testid="main-content"
            className="relative min-h-0 flex-1 overflow-hidden"
          >
            {isMac && (
              <div
                data-testid="mac-main-drag-region"
                aria-hidden="true"
                className="drag-region absolute inset-x-0 top-0 z-10"
                style={{ height: MAC_SIDEBAR_CHROME_HEIGHT }}
              />
            )}
            <Outlet />
          </main>

          {isChatPage && (
            <div className="shrink-0 border-t border-border/60 px-4 py-3">
              <ChatInput
                onSend={sendMessage}
                onStop={abortRun}
                disabled={false}
                sending={sending}
              />
            </div>
          )}
        </div>
      </div>

      {/* Global settings modal (opened from the top-bar gear icon) */}
      <SettingsModal />

      {/* Global "请购买" modal (triggered by HTTP 402 from legal-domain calls) */}
      <PurchaseGateModal />
    </div>
  );
}
