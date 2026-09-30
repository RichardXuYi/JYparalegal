import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  dismissNotice,
  reportFailure,
  reportSuccess,
  subscribeNotice,
  type AppNotice,
} from '../../src/lib/notice';

afterEach(() => {
  dismissNotice();
  vi.restoreAllMocks();
});

describe('reportFailure', () => {
  it('reports an error notice with the humanized message', () => {
    const received: (AppNotice | null)[] = [];
    subscribeNotice((notice) => received.push(notice));

    reportFailure('failed to fetch');

    expect(received.at(-1)).toEqual({
      kind: 'error',
      message: '连不上服务器，请确认后台已启动。',
      title: undefined,
      actions: undefined,
    });
  });

  it('keeps the given title and recovery actions', () => {
    const received: (AppNotice | null)[] = [];
    subscribeNotice((notice) => received.push(notice));
    const onClick = vi.fn();

    reportFailure('boom', 'fallback', {
      title: 'Task failed',
      actions: [{ label: 'Retry', onClick, primary: true }],
    });

    const notice = received.at(-1);
    expect(notice?.title).toBe('Task failed');
    expect(notice?.actions).toHaveLength(1);
    expect(notice?.actions?.[0]?.label).toBe('Retry');

    notice?.actions?.[0]?.onClick();
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('marks entitlement errors as the locked variant', () => {
    const received: (AppNotice | null)[] = [];
    subscribeNotice((notice) => received.push(notice));
    const locked = new Error('quota exceeded');
    locked.name = 'EntitlementError';

    reportFailure(locked, 'fallback');

    expect(received.at(-1)?.kind).toBe('locked');
  });

  it('clears the notice on dismiss', () => {
    const received: (AppNotice | null)[] = [];
    subscribeNotice((notice) => received.push(notice));

    reportFailure('boom');
    expect(received.at(-1)).not.toBeNull();

    dismissNotice();
    expect(received.at(-1)).toBeNull();
  });
});

describe('reportSuccess', () => {
  it('does not raise a blocking notice', () => {
    const received: (AppNotice | null)[] = [];
    subscribeNotice((notice) => received.push(notice));

    reportSuccess('saved');

    expect(received.at(-1)).toBeNull();
  });
});