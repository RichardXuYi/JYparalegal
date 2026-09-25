/**
 * Initializing Screen
 *
 * Brand-styled full-screen loading overlay shown during initial configuration
 * (auth restore, settings, gateway, providers). Fades out smoothly when
 * `visible` transitions from true to false via CSS transitions.
 *
 * Visual design mirrors the Login page for a consistent brand experience.
 *
 * Note: This component always renders its DOM; visibility is controlled via
 * CSS opacity/pointer-events. The parent should stop rendering it once the
 * fade-out animation is complete to free resources.
 */
import { useTranslation } from 'react-i18next';

interface InitializingScreenProps {
  /** Whether the loading overlay is visible. CSS transition handles fade-out. */
  visible: boolean;
}

const FADE_DURATION_MS = 300;

export function InitializingScreen({ visible }: InitializingScreenProps) {
  const { t } = useTranslation('common');
  return (
    <div
      className="fixed inset-0 z-[99999] flex items-center justify-center overflow-hidden"
      style={{
        transition: `opacity ${FADE_DURATION_MS}ms ease-out, transform ${FADE_DURATION_MS}ms ease-out`,
        opacity: visible ? 1 : 0,
        transform: visible ? 'scale(1)' : 'scale(0.98)',
        pointerEvents: visible ? 'auto' : 'none',
      }}
    >
      {/* Background gradient - matching Login page */}
      <div className="absolute inset-0 bg-gradient-to-br from-slate-50 via-blue-50 to-purple-50 dark:from-[hsl(15,60%,8%)] dark:via-[hsl(20,70%,12%)] dark:to-[hsl(25,65%,8%)]" />

      {/* Decorative blurs */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div
          className="absolute -top-40 -right-40 w-96 h-96 bg-gradient-to-br from-orange-400/20 to-red-400/20 rounded-full blur-3xl animate-pulse"
        />
        <div
          className="absolute -bottom-40 -left-40 w-96 h-96 bg-gradient-to-tr from-red-400/20 to-orange-400/20 rounded-full blur-3xl animate-pulse"
          style={{ animationDelay: '1.5s' }}
        />
      </div>

      {/* Content */}
      <div className="relative flex flex-col items-center gap-8">
        {/* Brand logo */}
        <div className="flex items-center gap-4">
          <div className="relative">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-blue-600 via-blue-500 to-purple-600 flex items-center justify-center shadow-2xl shadow-blue-500/30 relative z-10 dark:from-orange-600 dark:via-red-500 dark:to-red-600 dark:shadow-red-500/30">
              <span className="text-white font-bold text-3xl tracking-tight">JY</span>
            </div>
            <div className="absolute -inset-1.5 bg-gradient-to-br from-blue-600/30 to-purple-600/30 rounded-2xl blur-lg animate-pulse dark:from-orange-600/30 dark:to-red-600/30" />
          </div>
          <div>
            <h1 className="text-2xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent dark:from-orange-500 dark:to-red-500">
              JYparalegal
            </h1>
            <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
              {t('init.tagline')}
            </p>
          </div>
        </div>

        {/* Three-dot pulse animation */}
        <div className="flex items-center gap-2">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="w-2.5 h-2.5 rounded-full bg-gradient-to-br from-blue-500 to-purple-500 dark:from-orange-500 dark:to-red-500"
              style={{
                animation: 'init-dot-pulse 1.4s ease-in-out infinite',
                animationDelay: `${i * 0.16}s`,
              }}
            />
          ))}
        </div>

        {/* Text */}
        <div className="space-y-2 text-center">
          <p className="text-base font-medium text-slate-700 dark:text-slate-200">
            {t('init.configuring')}
          </p>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            {t('init.firstLoadHint')}
          </p>
        </div>
      </div>

      {/* Inline keyframes for the dot pulse animation */}
      <style>{`
        @keyframes init-dot-pulse {
          0%, 80%, 100% {
            transform: scale(0.6);
            opacity: 0.4;
          }
          40% {
            transform: scale(1);
            opacity: 1;
          }
        }
      `}</style>
    </div>
  );
}
