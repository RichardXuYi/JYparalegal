/**
 * Startup timeline instrumentation.
 *
 * Why: the desktop cold-start delay is split across two segments that look
 * identical to the user ("nothing happens"), but have completely different
 * causes:
 *
 *   A) process start -> app.whenReady()  : OS level (antivirus scanning, cloud
 *      reputation lookup, cold file-system cache, loading a large unsigned PE)
 *   B) app.whenReady() -> usable UI      : our own main-process work, first
 *      paint, and the Gateway cold boot
 *
 * `process.uptime()` at the first mark is the authoritative measurement of
 * segment A: it counts from real process start, not from the first log line.
 * Every mark after that gives the segment B breakdown.
 *
 * Keep this dependency-free (logger only) so it can be imported from anywhere
 * in the main process, including very early in module evaluation.
 */
import { logger } from './logger';

const TIMELINE_ORIGIN = Date.now();
let lastMarkAt = TIMELINE_ORIGIN;

/** Milliseconds since the timeline module was first evaluated. */
export function startupElapsedMs(): number {
  return Date.now() - TIMELINE_ORIGIN;
}

/**
 * Milliseconds since the OS started this process. Available before
 * `app.whenReady()` and therefore the only way to measure segment A.
 */
export function processUptimeMs(): number {
  return Math.round(process.uptime() * 1000);
}

/**
 * Append one startup milestone to the log.
 *
 * @param phase  short kebab-case phase name, e.g. `window-created`
 * @param extra  optional structured payload (serialized as JSON)
 */
export function markStartup(phase: string, extra?: Record<string, unknown>): void {
  const now = Date.now();
  const deltaFromPrevious = now - lastMarkAt;
  lastMarkAt = now;

  const parts = [
    `[startup] ${phase}`,
    `sinceModule=${now - TIMELINE_ORIGIN}ms`,
    `delta=${deltaFromPrevious}ms`,
    `uptime=${processUptimeMs()}ms`,
  ];
  if (extra) {
    parts.push(JSON.stringify(extra));
  }

  try {
    logger.info(parts.join(' '));
  } catch {
    // Logging must never be able to break startup.
  }
}

/** Log the full segment A/B summary once startup has settled. */
export function logStartupSummary(reason: string): void {
  markStartup('summary', { reason, uptime: processUptimeMs() });
}
