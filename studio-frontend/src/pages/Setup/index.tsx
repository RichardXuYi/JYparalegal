/**
 * Setup Wizard Page
 * First-time setup experience for new users
 */
import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import {
  Check,
  ChevronLeft,
  ChevronRight,
  Loader2,
  AlertCircle,
  RefreshCw,
  CheckCircle2,
  XCircle,
  ExternalLink,
} from 'lucide-react';
import { TitleBar } from '@/components/layout/TitleBar';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import { useGatewayStore } from '@/stores/gateway';
import { useSettingsStore } from '@/stores/settings';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { SUPPORTED_LANGUAGES } from '@/i18n';
import { toast } from 'sonner';
import { hostApi } from '@/lib/host-api';

interface SetupStep {
  id: string;
  title: string;
  description: string;
}

const STEP = {
  WELCOME: 0,
  RUNTIME: 1,
  INSTALLING: 2,
  COMPLETE: 3,
} as const;

const getSteps = (t: TFunction): SetupStep[] => [
  {
    id: 'welcome',
    title: t('steps.welcome.title'),
    description: t('steps.welcome.description'),
  },
  {
    id: 'runtime',
    title: t('steps.runtime.title'),
    description: t('steps.runtime.description'),
  },
  {
    id: 'installing',
    title: t('steps.installing.title'),
    description: t('steps.installing.description'),
  },
  {
    id: 'complete',
    title: t('steps.complete.title'),
    description: t('steps.complete.description'),
  },
];

// Default skills to auto-install (no additional API keys required)
interface DefaultSkill {
  id: string;
  name: string;
  description: string;
}

const getDefaultSkills = (t: TFunction): DefaultSkill[] => [
  { id: 'opencode', name: t('defaultSkills.opencode.name'), description: t('defaultSkills.opencode.description') },
  { id: 'python-env', name: t('defaultSkills.python-env.name'), description: t('defaultSkills.python-env.description') },
  { id: 'code-assist', name: t('defaultSkills.code-assist.name'), description: t('defaultSkills.code-assist.description') },
  { id: 'file-tools', name: t('defaultSkills.file-tools.name'), description: t('defaultSkills.file-tools.description') },
  { id: 'terminal', name: t('defaultSkills.terminal.name'), description: t('defaultSkills.terminal.description') },
];

import grandpoemStudioIcon from '@/assets/logo.svg';

// NOTE: Channel types moved to Settings > Channels page
// NOTE: Skill bundles moved to Settings > Skills page - auto-install essential skills during setup

export function Setup() {
  const { t } = useTranslation(['setup', 'channels']);
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = useState<number>(STEP.WELCOME);

  // Setup state
  // Installation state for the Installing step
  const [installedSkills, setInstalledSkills] = useState<string[]>([]);
  // Runtime check status
  const [runtimeChecksPassed, setRuntimeChecksPassed] = useState(false);

  const steps = getSteps(t);
  const safeStepIndex = Number.isInteger(currentStep)
    ? Math.min(Math.max(currentStep, STEP.WELCOME), steps.length - 1)
    : STEP.WELCOME;
  const step = steps[safeStepIndex] ?? steps[STEP.WELCOME];
  const isFirstStep = safeStepIndex === STEP.WELCOME;
  const isLastStep = safeStepIndex === steps.length - 1;

  const markSetupComplete = useSettingsStore((state) => state.markSetupComplete);

  // Derive canProceed based on current step - computed directly to avoid useEffect
  const canProceed = useMemo(() => {
    switch (safeStepIndex) {
      case STEP.WELCOME:
        return true;
      case STEP.RUNTIME:
        return runtimeChecksPassed;
      case STEP.INSTALLING:
        return false; // Cannot manually proceed, auto-proceeds when done
      case STEP.COMPLETE:
        return true;
      default:
        return true;
    }
  }, [safeStepIndex, runtimeChecksPassed]);

  const handleNext = async () => {
    if (isLastStep) {
      // Complete setup
      markSetupComplete();
      toast.success(t('complete.title'));
      navigate('/');
    } else {
      setCurrentStep((i) => i + 1);
    }
  };

  const handleBack = () => {
    setCurrentStep((i) => Math.max(i - 1, 0));
  };

  const handleSkip = () => {
    markSetupComplete();
    navigate('/');
  };

  // Auto-proceed when installation is complete
  const handleInstallationComplete = useCallback((skills: string[]) => {
    setInstalledSkills(skills);
    // Auto-proceed to next step after a short delay
    setTimeout(() => {
      setCurrentStep((i) => i + 1);
    }, 1000);
  }, []);


  return (
    <div data-testid="setup-page" className="flex h-screen flex-col overflow-hidden bg-background text-foreground">
      <TitleBar />
      <div className="flex flex-1 overflow-hidden">
        {/* Left branding / stepper sidebar */}
        <aside className="hidden md:flex w-[320px] shrink-0 flex-col bg-gradient-to-b from-primary to-primary/80 p-8 text-primary-foreground">
          <div className="mb-12 flex items-center gap-3">
            <img src={grandpoemStudioIcon} alt="JYparalegal" className="h-9 w-9" />
            <span className="text-lg font-bold tracking-tight">JYparalegal</span>
          </div>
          <VerticalStepper steps={steps} currentStep={safeStepIndex} />
          <div className="mt-auto pt-8 text-xs text-primary-foreground/60">
            {t('welcome.description')}
          </div>
        </aside>

        {/* Right content area */}
        <main className="flex flex-1 flex-col overflow-hidden">
          {/* Compact horizontal stepper fallback for narrow widths */}
          <div className="flex shrink-0 justify-center pt-6 md:hidden">
            <CompactStepper steps={steps} currentStep={safeStepIndex} />
          </div>

          <div className="flex-1 overflow-auto">
            <AnimatePresence mode="wait">
              <motion.div
                key={step.id}
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                className="mx-auto max-w-2xl p-8"
              >
                <div className="mb-8">
                  <h1 className="mb-2 text-2xl font-bold tracking-tight md:text-3xl">{t(`steps.${step.id}.title`)}</h1>
                  <p className="text-muted-foreground">{t(`steps.${step.id}.description`)}</p>
                </div>

                {/* Step-specific content */}
                <div className="rounded-xl border bg-surface-modal p-6 shadow-sm md:p-8">
                  {safeStepIndex === STEP.WELCOME && <WelcomeContent />}
                  {safeStepIndex === STEP.RUNTIME && <RuntimeContent onStatusChange={setRuntimeChecksPassed} />}
                  {safeStepIndex === STEP.INSTALLING && (
                    <InstallingContent
                      skills={getDefaultSkills(t)}
                      onComplete={handleInstallationComplete}
                      onSkip={() => setCurrentStep((i) => i + 1)}
                    />
                  )}
                  {safeStepIndex === STEP.COMPLETE && (
                    <CompleteContent
                      installedSkills={installedSkills}
                    />
                  )}
                </div>
              </motion.div>
            </AnimatePresence>
          </div>

          {/* Navigation - hidden during installation step */}
          {safeStepIndex !== STEP.INSTALLING && (
            <div className="shrink-0 border-t bg-background/80 px-8 py-4">
              <div className="mx-auto flex max-w-2xl justify-between">
                <div>
                  {!isFirstStep && (
                    <Button variant="ghost" onClick={handleBack}>
                      <ChevronLeft className="h-4 w-4 mr-2" />
                      {t('nav.back')}
                    </Button>
                  )}
                </div>
                <div className="flex gap-2">
                  {!isLastStep && safeStepIndex !== STEP.RUNTIME && (
                    <Button data-testid="setup-skip-button" variant="ghost" onClick={handleSkip}>
                      {t('nav.skipSetup')}
                    </Button>
                  )}
                  <Button data-testid="setup-next-button" onClick={handleNext} disabled={!canProceed}>
                    {isLastStep ? (
                      t('nav.getStarted')
                    ) : (
                      <>
                        {t('nav.next')}
                        <ChevronRight className="h-4 w-4 ml-2" />
                      </>
                    )}
                  </Button>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}

// ==================== Stepper Components ====================

function VerticalStepper({ steps, currentStep }: { steps: SetupStep[]; currentStep: number }) {
  return (
    <div className="flex flex-col">
      {steps.map((s, i) => {
        const done = i < currentStep;
        const active = i === currentStep;
        return (
          <div key={s.id} className="flex gap-4">
            <div className="flex flex-col items-center">
              <div
                className={cn(
                  'flex h-9 w-9 shrink-0 items-center justify-center rounded-full border-2 transition-colors',
                  done
                    ? 'border-primary-foreground bg-primary-foreground text-primary'
                    : active
                      ? 'border-primary-foreground bg-primary-foreground/15 text-primary-foreground ring-2 ring-primary-foreground/40'
                      : 'border-primary-foreground/30 text-primary-foreground/50'
                )}
              >
                {done ? <Check className="h-4 w-4" /> : <span className="text-sm font-medium">{i + 1}</span>}
              </div>
              {i < steps.length - 1 && (
                <div
                  className={cn(
                    'my-1 min-h-[2rem] w-0.5 flex-1 rounded transition-colors',
                    done ? 'bg-primary-foreground' : 'bg-primary-foreground/25'
                  )}
                />
              )}
            </div>
            <div className="pb-8 pt-1">
              <p
                className={cn(
                  'text-sm transition-colors',
                  active
                    ? 'font-semibold text-primary-foreground'
                    : done
                      ? 'text-primary-foreground/90'
                      : 'text-primary-foreground/50'
                )}
              >
                {s.title}
              </p>
              <p className={cn('mt-0.5 text-xs', active ? 'text-primary-foreground/80' : 'text-primary-foreground/40')}>
                {s.description}
              </p>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function CompactStepper({ steps, currentStep }: { steps: SetupStep[]; currentStep: number }) {
  return (
    <div className="flex items-center gap-2">
      {steps.map((s, i) => (
        <div key={s.id} className="flex items-center">
          <div
            className={cn(
              'flex h-8 w-8 items-center justify-center rounded-full border-2 transition-colors',
              i < currentStep
                ? 'border-primary bg-primary text-primary-foreground'
                : i === currentStep
                  ? 'border-primary text-primary'
                  : 'border-muted-foreground/30 text-muted-foreground'
            )}
          >
            {i < currentStep ? <Check className="h-4 w-4" /> : <span className="text-sm">{i + 1}</span>}
          </div>
          {i < steps.length - 1 && (
            <div
              className={cn(
                'h-0.5 w-8 transition-colors',
                i < currentStep ? 'bg-primary' : 'bg-muted-foreground/30'
              )}
            />
          )}
        </div>
      ))}
    </div>
  );
}

// ==================== Step Content Components ====================

function WelcomeContent() {
  const { t } = useTranslation(['setup', 'settings']);
  const { language, setLanguage } = useSettingsStore();

  return (
    <div data-testid="setup-welcome-step" className="space-y-6">
      <p className="text-muted-foreground">
        {t('welcome.description')}
      </p>

      {/* Language Selector */}
      <div className="flex flex-wrap gap-2">
        {SUPPORTED_LANGUAGES.map((lang) => (
          <Button
            key={lang.code}
            variant={language === lang.code ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setLanguage(lang.code)}
            className={cn(
              'h-8 text-xs',
              language === lang.code
                ? 'bg-black/5 dark:bg-white/10 text-foreground'
                : 'text-muted-foreground hover:bg-black/5 dark:hover:bg-white/5',
            )}
          >
            {lang.label}
          </Button>
        ))}
      </div>

      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {[
          t('welcome.features.noCommand'),
          t('welcome.features.modernUI'),
          t('welcome.features.bundles'),
          t('welcome.features.crossPlatform'),
        ].map((feature) => (
          <li
            key={feature}
            className="flex items-center gap-2 rounded-lg bg-surface-input/50 p-3 text-sm text-foreground/80"
          >
            <CheckCircle2 className="h-5 w-5 shrink-0 text-green-600 dark:text-green-400" />
            {feature}
          </li>
        ))}
      </ul>
    </div>
  );
}

interface RuntimeContentProps {
  onStatusChange: (canProceed: boolean) => void;
}

function RuntimeContent({ onStatusChange }: RuntimeContentProps) {
  const { t } = useTranslation('setup');
  const gatewayStatus = useGatewayStore((state) => state.status);
  const startGateway = useGatewayStore((state) => state.start);

  const [checks, setChecks] = useState({
    nodejs: { status: 'checking' as 'checking' | 'success' | 'error', message: '' },
    openclaw: { status: 'checking' as 'checking' | 'success' | 'error', message: '' },
    gateway: { status: 'checking' as 'checking' | 'success' | 'error', message: '' },
  });
  const [showLogs, setShowLogs] = useState(false);
  const [logContent, setLogContent] = useState('');
  const [openclawDir, setOpenclawDir] = useState('');
  const gatewayTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  const runChecks = useCallback(async () => {
    // Reset checks
    setChecks({
      nodejs: { status: 'checking', message: '' },
      openclaw: { status: 'checking', message: '' },
      gateway: { status: 'checking', message: '' },
    });

    // Check Node.js —always available in Electron
    setChecks((prev) => ({
      ...prev,
      nodejs: { status: 'success', message: t('runtime.status.success') },
    }));

    // Check OpenClaw package status
    try {
      const openclawStatus = await hostApi.openclaw.status();

      setOpenclawDir(openclawStatus.dir);

      if (!openclawStatus.packageExists) {
        setChecks((prev) => ({
          ...prev,
          openclaw: {
            status: 'error',
            message: `OpenClaw package not found at: ${openclawStatus.dir}`
          },
        }));
      } else if (!openclawStatus.isBuilt) {
        setChecks((prev) => ({
          ...prev,
          openclaw: {
            status: 'error',
            message: 'OpenClaw package found but dist is missing'
          },
        }));
      } else {
        const versionLabel = openclawStatus.version ? ` v${openclawStatus.version}` : '';
        setChecks((prev) => ({
          ...prev,
          openclaw: {
            status: 'success',
            message: `OpenClaw package ready${versionLabel}`
          },
        }));
      }
    } catch (error) {
      setChecks((prev) => ({
        ...prev,
        openclaw: { status: 'error', message: `Check failed: ${error}` },
      }));
    }

    // Check Gateway —read directly from store to avoid stale closure
    // Don't immediately report error; gateway may still be initializing
    const currentGateway = useGatewayStore.getState().status;
    if (currentGateway.state === 'running') {
      setChecks((prev) => ({
        ...prev,
        gateway: { status: 'success', message: `Running on port ${currentGateway.port}` },
      }));
    } else if (currentGateway.state === 'error') {
      setChecks((prev) => ({
        ...prev,
        gateway: { status: 'error', message: currentGateway.error || t('runtime.status.error') },
      }));
    } else {
      // Gateway is 'stopped', 'starting', or 'reconnecting'
      // Keep as 'checking' —the dedicated useEffect will update when status changes
      setChecks((prev) => ({
        ...prev,
        gateway: {
          status: 'checking',
          message: currentGateway.state === 'starting' ? t('runtime.status.checking') : 'Waiting for gateway...'
        },
      }));
    }
  }, [t]);

  useEffect(() => {
    runChecks();
  }, [runChecks]);

  // Update canProceed when gateway status changes
  useEffect(() => {
    const allPassed = checks.nodejs.status === 'success'
      && checks.openclaw.status === 'success'
      && (checks.gateway.status === 'success' || gatewayStatus.state === 'running');
    onStatusChange(allPassed);
  }, [checks, gatewayStatus, onStatusChange]);

  // Update gateway check when gateway status changes
  useEffect(() => {
    if (gatewayStatus.state === 'running') {
      setChecks((prev) => ({
        ...prev,
        gateway: { status: 'success', message: t('runtime.status.gatewayRunning', { port: gatewayStatus.port }) },
      }));
    } else if (gatewayStatus.state === 'error') {
      setChecks((prev) => ({
        ...prev,
        gateway: { status: 'error', message: gatewayStatus.error || 'Failed to start' },
      }));
    } else if (gatewayStatus.state === 'starting' || gatewayStatus.state === 'reconnecting') {
      setChecks((prev) => ({
        ...prev,
        gateway: { status: 'checking', message: 'Starting...' },
      }));
    }
    // 'stopped' state: keep current check status (likely 'checking') to allow startup time
  }, [gatewayStatus, t]);

  // Gateway startup timeout —show error only after giving enough time to initialize
  useEffect(() => {
    if (gatewayTimeoutRef.current) {
      clearTimeout(gatewayTimeoutRef.current);
      gatewayTimeoutRef.current = null;
    }

    // If gateway is already in a terminal state, no timeout needed
    if (gatewayStatus.state === 'running' || gatewayStatus.state === 'error') {
      return;
    }

    // Set timeout for non-terminal states (stopped, starting, reconnecting)
    gatewayTimeoutRef.current = setTimeout(() => {
      setChecks((prev) => {
        if (prev.gateway.status === 'checking') {
          return {
            ...prev,
            gateway: { status: 'error', message: 'Gateway startup timed out' },
          };
        }
        return prev;
      });
    }, 600 * 1000); // 600 seconds —enough for gateway to fully initialize

    return () => {
      if (gatewayTimeoutRef.current) {
        clearTimeout(gatewayTimeoutRef.current);
        gatewayTimeoutRef.current = null;
      }
    };
  }, [gatewayStatus.state]);

  const handleStartGateway = async () => {
    setChecks((prev) => ({
      ...prev,
      gateway: { status: 'checking', message: 'Starting...' },
    }));
    await startGateway();
  };

  const handleShowLogs = async () => {
    try {
      const logs = await hostApi.logs.recent(100);
      setLogContent(logs.content);
      setShowLogs(true);
    } catch {
      setLogContent('(Failed to load logs)');
      setShowLogs(true);
    }
  };

  const handleOpenLogDir = async () => {
    try {
      const { dir: logDir } = await hostApi.logs.dir();
      if (logDir) {
        await hostApi.shell.showItemInFolder(logDir);
      }
    } catch {
      // ignore
    }
  };

  const ERROR_TRUNCATE_LEN = 30;

  const renderStatus = (status: 'checking' | 'success' | 'error', message: string) => {
    if (status === 'checking') {
      return (
        <span className="flex items-center gap-2 text-yellow-700 dark:text-yellow-400 whitespace-nowrap">
          <Loader2 className="h-5 w-5 flex-shrink-0 animate-spin" />
          {message || 'Checking...'}
        </span>
      );
    }
    if (status === 'success') {
      return (
        <span className="flex items-center gap-2 text-green-700 dark:text-green-400 whitespace-nowrap">
          <CheckCircle2 className="h-5 w-5 flex-shrink-0" />
          {message}
        </span>
      );
    }

    const isLong = message.length > ERROR_TRUNCATE_LEN;
    const displayMsg = isLong ? message.slice(0, ERROR_TRUNCATE_LEN) : message;

    return (
      <span className="flex items-center gap-2 text-red-700 dark:text-red-400 whitespace-nowrap">
        <XCircle className="h-5 w-5 flex-shrink-0" />
        <span>{displayMsg}</span>
        {isLong && (
          <Tooltip>
            <TooltipTrigger asChild>
              <span className="cursor-pointer text-red-700 dark:text-red-300 hover:text-red-800 dark:hover:text-red-200 font-medium">...</span>
            </TooltipTrigger>
            <TooltipContent side="top" className="max-w-sm whitespace-normal break-words text-xs">
              {message}
            </TooltipContent>
          </Tooltip>
        )}
      </span>
    );
  };

  const statusDot = (status: 'checking' | 'success' | 'error') =>
    cn(
      'h-2.5 w-2.5 shrink-0 rounded-full',
      status === 'success' ? 'bg-green-500' : status === 'error' ? 'bg-red-500' : 'bg-yellow-500'
    );

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold tracking-tight">{t('runtime.title')}</h2>
        <div className="flex gap-1">
          <Button variant="ghost" size="sm" className="h-8 text-xs" onClick={handleShowLogs}>
            {t('runtime.viewLogs')}
          </Button>
          <Button variant="ghost" size="sm" className="h-8 text-xs" onClick={runChecks}>
            <RefreshCw className="h-3.5 w-3.5 mr-1.5" />
            {t('runtime.recheck')}
          </Button>
        </div>
      </div>
      <div className="space-y-2.5">
        <div className="grid grid-cols-[1fr_auto] items-center gap-4 rounded-lg border bg-surface-input/50 p-4">
          <div className="flex items-center gap-3 text-left">
            <span className={statusDot(checks.nodejs.status)} />
            <span className="font-medium">{t('runtime.nodejs')}</span>
          </div>
          <div className="flex justify-end">
            {renderStatus(checks.nodejs.status, checks.nodejs.message)}
          </div>
        </div>
        <div className="grid grid-cols-[1fr_auto] items-center gap-4 rounded-lg border bg-surface-input/50 p-4">
          <div className="flex min-w-0 items-start gap-3 text-left">
            <span className={cn(statusDot(checks.openclaw.status), 'mt-1.5')} />
            <div className="min-w-0">
              <span className="font-medium">{t('runtime.openclaw')}</span>
              {openclawDir && (
                <p className="text-xs text-muted-foreground mt-0.5 font-mono break-all">
                  {openclawDir}
                </p>
              )}
            </div>
          </div>
          <div className="flex justify-end self-start mt-0.5">
            {renderStatus(checks.openclaw.status, checks.openclaw.message)}
          </div>
        </div>
        <div className="grid grid-cols-[1fr_auto] items-center gap-4 rounded-lg border bg-surface-input/50 p-4">
          <div className="flex items-center gap-3 text-left">
            <span className={statusDot(checks.gateway.status)} />
            <span className="font-medium">{t('runtime.gateway')}</span>
            {checks.gateway.status === 'error' && (
              <Button variant="outline" size="sm" onClick={handleStartGateway}>
                {t('runtime.startGateway')}
              </Button>
            )}
          </div>
          <div className="flex justify-end">
            {renderStatus(checks.gateway.status, checks.gateway.message)}
          </div>
        </div>
      </div>

      {(checks.nodejs.status === 'error' || checks.openclaw.status === 'error') && (
        <div className="mt-4 p-4 rounded-lg bg-red-500/10 border border-red-500/20">
          <div className="flex items-start gap-2">
            <AlertCircle className="h-5 w-5 text-red-700 dark:text-red-400 mt-0.5" />
            <div>
              <p className="font-medium text-red-700 dark:text-red-400">{t('runtime.issue.title')}</p>
              <p className="text-sm text-muted-foreground mt-1">
                {t('runtime.issue.desc')}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Log viewer panel */}
      {showLogs && (
        <div className="mt-4 p-4 rounded-lg bg-black/40 border border-border">
          <div className="flex items-center justify-between mb-2">
            <p className="font-medium text-foreground text-sm">{t('runtime.logs.title')}</p>
            <div className="flex gap-2">
              <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={handleOpenLogDir}>
                <ExternalLink className="h-3 w-3 mr-1" />
                {t('runtime.logs.openFolder')}
              </Button>
              <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => setShowLogs(false)}>
                {t('runtime.logs.close')}
              </Button>
            </div>
          </div>
          <pre className="text-xs text-foreground/80 bg-black/5 dark:bg-white/10 p-3 rounded max-h-60 overflow-auto whitespace-pre-wrap font-mono">
            {logContent || t('runtime.logs.noLogs')}
          </pre>
        </div>
      )}
    </div>
  );
}

// NOTE: ProviderContent component removed - configure providers via Settings > AI Providers


// Installation status for each skill
type InstallStatus = 'pending' | 'installing' | 'completed' | 'failed';

interface SkillInstallState {
  id: string;
  name: string;
  description: string;
  status: InstallStatus;
}

interface InstallingContentProps {
  skills: DefaultSkill[];
  onComplete: (installedSkills: string[]) => void;
  onSkip: () => void;
}

function InstallingContent({ skills, onComplete, onSkip }: InstallingContentProps) {
  const { t } = useTranslation('setup');
  const [skillStates, setSkillStates] = useState<SkillInstallState[]>(
    skills.map((s) => ({ ...s, status: 'pending' as InstallStatus }))
  );
  const [overallProgress, setOverallProgress] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const installStarted = useRef(false);

  // Real installation process
  useEffect(() => {
    if (installStarted.current) return;
    installStarted.current = true;

    const runRealInstall = async () => {
      try {
        // Step 1: Initialize all skills to 'installing' state for UI
        setSkillStates(prev => prev.map(s => ({ ...s, status: 'installing' })));
        setOverallProgress(10);

        // Step 2: Call the backend to install uv and setup Python
        const result = await hostApi.uv.installAll();

        if (result.success) {
          setSkillStates(prev => prev.map(s => ({ ...s, status: 'completed' })));
          setOverallProgress(100);

          await new Promise((resolve) => setTimeout(resolve, 800));
          onComplete(skills.map(s => s.id));
        } else {
          setSkillStates(prev => prev.map(s => ({ ...s, status: 'failed' })));
          setErrorMessage(result.error || 'Unknown error during installation');
          toast.error('Environment setup failed');
        }
      } catch (err) {
        setSkillStates(prev => prev.map(s => ({ ...s, status: 'failed' })));
        setErrorMessage(String(err));
        toast.error('Installation error');
      }
    };

    runRealInstall();
  }, [skills, onComplete]);

  const getStatusIcon = (status: InstallStatus) => {
    switch (status) {
      case 'pending':
        return <div className="h-5 w-5 rounded-full border-2 border-muted-foreground/30" />;
      case 'installing':
        return <Loader2 className="h-5 w-5 text-primary animate-spin" />;
      case 'completed':
        return <CheckCircle2 className="h-5 w-5 text-green-700 dark:text-green-400" />;
      case 'failed':
        return <XCircle className="h-5 w-5 text-red-700 dark:text-red-400" />;
    }
  };

  const getStatusText = (skill: SkillInstallState) => {
    const base = 'rounded-full px-2.5 py-0.5 text-xs font-medium';
    switch (skill.status) {
      case 'pending':
        return <span className={cn(base, 'bg-surface-input text-muted-foreground')}>{t('installing.status.pending')}</span>;
      case 'installing':
        return <span className={cn(base, 'bg-primary/10 text-primary')}>{t('installing.status.installing')}</span>;
      case 'completed':
        return <span className={cn(base, 'bg-green-500/10 text-green-700 dark:text-green-400')}>{t('installing.status.installed')}</span>;
      case 'failed':
        return <span className={cn(base, 'bg-red-500/10 text-red-700 dark:text-red-400')}>{t('installing.status.failed')}</span>;
    }
  };

  return (
    <div className="space-y-6">
      <div className="text-center">
        <div className="mb-3 text-4xl">⏳</div>
        <p className="text-muted-foreground">
          {t('installing.subtitle')}
        </p>
      </div>

      {/* Progress bar */}
      <div className="space-y-2">
        <div className="flex justify-between text-sm">
          <span className="text-muted-foreground">{t('installing.progress')}</span>
          <span className="font-medium text-primary">{overallProgress}%</span>
        </div>
        <div className="h-2.5 overflow-hidden rounded-full bg-secondary">
          <motion.div
            className="h-full rounded-full bg-primary"
            initial={{ width: 0 }}
            animate={{ width: `${overallProgress}%` }}
            transition={{ duration: 0.3 }}
          />
        </div>
      </div>

      {/* Skill list */}
      <div className="max-h-48 space-y-2 overflow-y-auto">
        {skillStates.map((skill) => (
          <motion.div
            key={skill.id}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className={cn(
              'flex items-center justify-between rounded-lg border p-3',
              skill.status === 'installing' ? 'bg-surface-input' : 'bg-surface-input/50'
            )}
          >
            <div className="flex items-center gap-3">
              {getStatusIcon(skill.status)}
              <div>
                <p className="font-medium">{skill.name}</p>
                <p className="text-xs text-muted-foreground">{skill.description}</p>
              </div>
            </div>
            {getStatusText(skill)}
          </motion.div>
        ))}
      </div>

      {/* Error Message Display */}
      {errorMessage && (
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          className="p-4 rounded-lg bg-red-500/10 border border-red-500/30 text-red-700 dark:text-red-300 text-sm"
        >
          <div className="flex items-start gap-2">
            <AlertCircle className="h-5 w-5 text-red-700 dark:text-red-400 shrink-0 mt-0.5" />
            <div className="space-y-1">
              <p className="font-semibold">{t('installing.error')}</p>
              <pre className="text-xs bg-black/5 dark:bg-white/5 p-2 rounded overflow-x-auto whitespace-pre-wrap font-mono">
                {errorMessage}
              </pre>
              <Button
                variant="link"
                className="text-red-700 dark:text-red-400 p-0 h-auto text-xs underline"
                onClick={() => window.location.reload()}
              >
                {t('installing.restart')}
              </Button>
            </div>
          </div>
        </motion.div>
      )}

      {!errorMessage && (
        <p className="text-sm text-muted-foreground text-center">
          {t('installing.wait')}
        </p>
      )}
      <div className="flex justify-end">
        <Button
          variant="ghost"
          className="text-muted-foreground"
          onClick={onSkip}
        >
          {t('installing.skip')}
        </Button>
      </div>
    </div>
  );
}
interface CompleteContentProps {
  installedSkills: string[];
}

function CompleteContent({ installedSkills }: CompleteContentProps) {
  const { t } = useTranslation(['setup', 'settings']);
  const gatewayStatus = useGatewayStore((state) => state.status);

  const installedSkillNames = getDefaultSkills(t)
    .filter((s: DefaultSkill) => installedSkills.includes(s.id))
    .map((s: DefaultSkill) => s.name)
    .join(', ');

  return (
    <div className="space-y-6 text-center">
      <div className="flex justify-center">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-green-500/10">
          <CheckCircle2 className="h-9 w-9 text-green-600 dark:text-green-400" />
        </div>
      </div>
      <div className="space-y-2">
        <h2 className="text-xl font-bold tracking-tight">{t('complete.title')}</h2>
        <p className="text-muted-foreground">
          {t('complete.subtitle')}
        </p>
      </div>

      <div className="mx-auto max-w-md space-y-2.5 text-left">
        <div className="flex items-center justify-between rounded-lg border bg-surface-input/50 p-4">
          <span className="font-medium">{t('complete.components')}</span>
          <span className="text-green-700 dark:text-green-400">
            {installedSkillNames || `${installedSkills.length} ${t('installing.status.installed')}`}
          </span>
        </div>
        <div className="flex items-center justify-between rounded-lg border bg-surface-input/50 p-4">
          <span className="font-medium">{t('complete.gateway')}</span>
          <span className={gatewayStatus.state === 'running' && gatewayStatus.gatewayReady !== false ? 'text-green-700 dark:text-green-400' : gatewayStatus.state === 'running' ? 'text-red-700 dark:text-red-400' : 'text-yellow-700 dark:text-yellow-400'}>
            {gatewayStatus.state === 'running'
              ? gatewayStatus.gatewayReady !== false
                ? `✅ ${t('complete.running')}`
                : 'starting'
              : gatewayStatus.state}
          </span>
        </div>
      </div>

      <p className="text-sm text-muted-foreground">
        {t('complete.footer')}
      </p>
    </div>
  );
}

export default Setup;
