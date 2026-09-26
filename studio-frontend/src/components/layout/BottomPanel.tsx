/* eslint-disable react-refresh/only-export-components */
/**
 * BottomPanel Component
 * Container for the chat input area.
 * Uses a portal target so Chat page can render its input here.
 */
import { useEffect, useState, useCallback, useMemo } from 'react';
import { createPortal } from 'react-dom';

type BottomPanelContent = React.ReactNode;

interface BottomPanelContextValue {
  content: BottomPanelContent;
  setContent: (content: BottomPanelContent) => void;
  clearContent: () => void;
}

import { createContext, useContext } from 'react';

const BottomPanelContext = createContext<BottomPanelContextValue | null>(null);

export function useBottomPanel() {
  const ctx = useContext(BottomPanelContext);
  if (!ctx) {
    throw new Error('useBottomPanel must be used within BottomPanelProvider');
  }
  return ctx;
}

export function BottomPanelProvider({ children }: { children: React.ReactNode }) {
  const [content, setContentState] = useState<BottomPanelContent>(null);

  const setContent = useCallback((next: BottomPanelContent) => {
    setContentState(next);
  }, []);

  const clearContent = useCallback(() => {
    setContentState(null);
  }, []);

  const value = useMemo(
    () => ({ content, setContent, clearContent }),
    [content, setContent, clearContent],
  );

  return (
    <BottomPanelContext.Provider value={value}>
      {children}
    </BottomPanelContext.Provider>
  );
}

interface BottomPanelProps {
  containerId: string;
}

export function BottomPanel({ containerId }: BottomPanelProps) {
  const [container, setContainer] = useState<HTMLElement | null>(null);
  const ctx = useContext(BottomPanelContext);

  useEffect(() => {
    // DOM lookup must wait until after the commit that renders the target
    // node; defer the state write to a microtask so it stays out of the
    // synchronous effect body.
    let cancelled = false;
    queueMicrotask(() => {
      if (cancelled) return;
      const el = document.getElementById(containerId);
      if (el) setContainer(el);
    });
    return () => {
      cancelled = true;
    };
  }, [containerId]);

  if (!container || !ctx) return null;

  return createPortal(ctx.content, container);
}
