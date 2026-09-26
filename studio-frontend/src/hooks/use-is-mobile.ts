/**
 * useIsMobile Hook
 * Structural breakpoint for the mobile shell (narrow viewport check).
 */
import { useMediaQuery } from '@/hooks/use-media-query';

export const MOBILE_MEDIA_QUERY = '(max-width: 639px)';

export function useIsMobile(): boolean {
  return useMediaQuery(MOBILE_MEDIA_QUERY);
}
