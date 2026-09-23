/**
 * Skills Page
 * Browse and manage AI skills
 */
import { Suspense, lazy, useEffect, useRef, useState, useCallback } from 'react';
import {
  Search,
  Puzzle,
  Lock,
  Package,
  X,
  AlertCircle,
  Trash2,
  Upload,
  RefreshCw,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { SettingsPageHeader } from '@/components/settings/primitives';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import { Sheet, SheetContent } from '@/components/ui/sheet';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { useSkillsStore } from '@/stores/skills';
import { useGatewayStore } from '@/stores/gateway';
import { LoadingSpinner } from '@/components/common/LoadingSpinner';
import { cn } from '@/lib/utils';
import { hostApi } from '@/lib/host-api';
import { isGatewayStopped } from '@/lib/gateway-status';
import { toast } from 'sonner';
import type { Skill } from '@/types/skill';
import type { GatewayStatus } from '@/types/gateway';
import { rendererExtensionRegistry } from '@/extensions/registry';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { SkillFileSections } from '@/components/file-preview/SkillFileSections';
import type { FilePreviewTarget } from '@/components/file-preview/FilePreviewOverlay';
import type { SkillFile } from '@/lib/skill-files';

const FilePreviewOverlayLazy = lazy(() =>
  import('@/components/file-preview/FilePreviewOverlay').then((m) => ({ default: m.FilePreviewOverlay })),
);

function skillFileToTarget(file: SkillFile): FilePreviewTarget {
  return {
    filePath: file.filePath,
    fileName: file.fileName,
    ext: file.ext,
    mimeType: file.mimeType,
    contentType: file.contentType,
  };
}

/**
 * Check if a string is a valid emoji (or emoji sequence).
 * Returns true if the string contains only emoji characters and whitespace.
 */
function isValidEmoji(value: string | undefined): boolean {
  if (!value) return false;
  // Emoji regex pattern - matches common emoji ranges
  const emojiRegex = /^[\p{Emoji}\p{Emoji_Modifier}\p{Emoji_Modifier_Base}\p{Emoji_Presentation}\s]+$/u;
  return emojiRegex.test(value);
}

const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

const INSTALL_ERROR_CODES = new Set(['installTimeoutError', 'installRateLimitError']);
const FETCH_ERROR_CODES = new Set(['fetchTimeoutError', 'fetchRateLimitError', 'timeoutError', 'rateLimitError']);
const SEARCH_ERROR_CODES = new Set(['searchTimeoutError', 'searchRateLimitError', 'timeoutError', 'rateLimitError']);

type SkillsGatewayBannerState = 'none' | 'stopped';

function getSkillsGatewayBannerState(status: GatewayStatus): SkillsGatewayBannerState {
  if (isGatewayStopped(status)) {
    return 'stopped';
  }
  return 'none';
}

// Skill detail dialog component
interface SkillDetailDialogProps {
  skill: Skill | null;
  isOpen: boolean;
  onClose: () => void;
  onToggle: (enabled: boolean) => void;
  onUninstall?: (slug: string) => void;
}

function resolveSkillSourceLabel(skill: Skill, t: TFunction<'skills'>): string {
  const source = (skill.source || '').trim().toLowerCase();
  if (!source) {
    if (skill.isBundled) return t('source.badge.bundled', { defaultValue: 'Bundled dir' });
    return t('source.badge.unknown', { defaultValue: 'Unknown source' });
  }
  if (source === 'openclaw-bundled') return t('source.badge.bundled', { defaultValue: 'Bundled dir' });
  if (source === 'openclaw-managed') return t('source.badge.managed', { defaultValue: 'Managed' });
  if (source === 'openclaw-workspace') return t('source.badge.workspace', { defaultValue: 'Workspace' });
  if (source === 'openclaw-extra') return t('source.badge.extra', { defaultValue: 'Extra dirs' });
  if (source === 'openclaw-plugin') return t('source.badge.plugin', { defaultValue: 'Plugin dir' });
  if (source === 'agents-skills-personal') return t('source.badge.agentsPersonal', { defaultValue: 'Personal .agents' });
  if (source === 'agents-skills-project') return t('source.badge.agentsProject', { defaultValue: 'Project .agents' });
  return source;
}

function canUninstallSkill(skill: Skill): boolean {
  return (skill.source || '').trim().toLowerCase() === 'openclaw-managed';
}

function SkillDetailDialog({ skill, isOpen, onClose, onToggle, onUninstall }: SkillDetailDialogProps) {
  const { t } = useTranslation('skills');
  const [openedSkillFile, setOpenedSkillFile] = useState<FilePreviewTarget | null>(null);
  const detailMetaComponents = rendererExtensionRegistry.getSkillDetailMetaComponents();

  if (!skill) return null;

  const uninstallable = canUninstallSkill(skill);

  return (
    <Sheet open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <Suspense fallback={null}>
        <FilePreviewOverlayLazy
          file={openedSkillFile}
          readOnly
          onClose={() => setOpenedSkillFile(null)}
        />
      </Suspense>
      <SheetContent
        className="w-full sm:max-w-[450px] p-0 flex flex-col border-l border-black/10 dark:border-white/10 bg-surface-modal shadow-[0_0_40px_rgba(0,0,0,0.2)]"
        side="right"
      >
        {/* Scrollable Content */}
        <div className="flex-1 overflow-y-auto px-8 py-10">
          <div className="flex flex-col items-center mb-8">
            <div className="w-16 h-16 flex items-center justify-center rounded-full bg-surface-modal border border-black/5 dark:border-white/5 shrink-0 mb-4 relative shadow-sm">
              <span className="text-3xl">{isValidEmoji(skill.icon) ? skill.icon : ''}</span>
              {skill.isCore && (
                <div className="absolute -bottom-1 -right-1 bg-surface-modal rounded-full p-1 shadow-sm border border-black/5 dark:border-white/5">
                  <Lock className="h-3 w-3 text-muted-foreground shrink-0" />
                </div>
              )}
            </div>
            <h2 className="text-3xl font-bold font-normal mb-3 text-center tracking-tight">
              {skill.name}
            </h2>
            <div data-skill-detail-meta-row="1" className="flex items-center justify-center flex-wrap gap-2.5 mb-6 opacity-80">
              {skill.version && (
                <Badge variant="secondary" className="shrink-0 whitespace-nowrap font-mono text-tiny font-medium px-3 py-0.5 rounded-full bg-black/[0.04] dark:bg-white/[0.08] hover:bg-black/[0.08] dark:hover:bg-white/[0.12] border-0 shadow-none text-foreground/70 transition-colors">
                  v{skill.version}
                </Badge>
              )}
              <Badge variant="secondary" className="shrink-0 whitespace-nowrap font-mono text-tiny font-medium px-3 py-0.5 rounded-full bg-black/[0.04] dark:bg-white/[0.08] hover:bg-black/[0.08] dark:hover:bg-white/[0.12] border-0 shadow-none text-foreground/70 transition-colors">
                {skill.isCore ? t('detail.coreSystem') : skill.isBundled ? t('detail.bundled') : t('detail.userInstalled')}
              </Badge>
              {detailMetaComponents.map((DetailMetaComponent, index) => (
                <DetailMetaComponent key={`skill-detail-meta-${index}`} skill={skill} />
              ))}
            </div>

            {skill.description && (
              <p className="text-sm text-foreground/70 font-medium leading-[1.6] text-center px-4">
                {skill.description}
              </p>
            )}
          </div>

          <div className="space-y-7 px-1">
            <div className="space-y-2">
              <h3 className="text-meta font-bold text-foreground/80">{t('detail.source')}</h3>
              <div className="flex items-center gap-2 flex-wrap">
                <Badge variant="secondary" className="shrink-0 whitespace-nowrap font-mono text-tiny font-medium px-3 py-0.5 rounded-full bg-black/[0.04] dark:bg-white/[0.08] border-0 shadow-none text-foreground/70">
                  {resolveSkillSourceLabel(skill, t)}
                </Badge>
              </div>
            </div>

            {/* File Sections �?read-only preview of skill content */}
            {skill.baseDir && (
              <div className="space-y-3">
                <h3 className="text-meta font-bold text-foreground/80">
                  {t('detail.sections.title', { defaultValue: '内容' })}
                </h3>
                <SkillFileSections
                  baseDir={skill.baseDir}
                  onOpen={(file) => setOpenedSkillFile(skillFileToTarget(file))}
                />
              </div>
            )}

          </div>

          {/* Centered Footer Button �?uninstall / disable / enable */}
          {!skill.isCore && (
            <div className="pt-8 pb-4 flex items-center justify-center w-full px-2 max-w-[340px] mx-auto">
              <Button
                variant="outline"
                className="w-full h-[42px] text-meta rounded-full font-semibold shadow-sm bg-transparent border-black/20 dark:border-white/20 hover:bg-black/5 dark:hover:bg-white/5 transition-colors text-foreground/80 hover:text-foreground"
                onClick={() => {
                  if (uninstallable && onUninstall && skill.slug) {
                    onUninstall(skill.slug);
                    onClose();
                  } else {
                    onToggle(!skill.enabled);
                  }
                }}
              >
                {uninstallable && onUninstall
                  ? t('detail.uninstall')
                  : (skill.enabled ? t('detail.disable') : t('detail.enable'))}
              </Button>
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

export function Skills() {
  const {
    skills,
    loading,
    error,
    fetchSkills,
    syncSkills,
    syncing,
    enableSkill,
    disableSkill,
    searchResults,
    searchSkills,
    installSkill,
    uninstallSkill,
    searching,
    searchError,
    installing
  } = useSkillsStore();
  const { t } = useTranslation('skills');
  const gatewayStatus = useGatewayStore((state) => state.status);
  const [searchQuery, setSearchQuery] = useState('');
  const [installQuery, setInstallQuery] = useState('');
  const [installSheetOpen, setInstallSheetOpen] = useState(false);
  const [selectedSkill, setSelectedSkill] = useState<Skill | null>(null);
  const [statusFilter, setStatusFilter] = useState<'all' | 'enabled' | 'disabled'>('all');
  const [marketplaceAvailable, setMarketplaceAvailable] = useState(false);

  const gatewayRunning = gatewayStatus.state === 'running';
  const gatewayReportedReady = gatewayStatus.gatewayReady !== false;
  const gatewayRuntimeKey = `${gatewayStatus.pid ?? 'none'}:${gatewayStatus.connectedAt ?? 'none'}:${gatewayStatus.port}`;
  const gatewayBannerState = getSkillsGatewayBannerState(gatewayStatus);
  const [showGatewayBanner, setShowGatewayBanner] = useState(false);

  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (gatewayBannerState === 'none') {
      timer = setTimeout(() => {
        setShowGatewayBanner(false);
      }, 0);
    } else {
      timer = setTimeout(() => {
        setShowGatewayBanner(true);
      }, 1500);
    }
    return () => clearTimeout(timer);
  }, [gatewayBannerState]);

  useEffect(() => {
    let cancelled = false;
    let retryTimer: ReturnType<typeof setInterval> | null = null;

    const attemptFetch = async () => {
      const ok = await fetchSkills();
      if (cancelled || !ok) return;
      if (retryTimer) {
        clearInterval(retryTimer);
        retryTimer = null;
      }
    };

    void attemptFetch();

    if (gatewayRunning && !gatewayReportedReady) {
      retryTimer = setInterval(() => {
        void attemptFetch();
      }, 5_000);
    }

    return () => {
      cancelled = true;
      if (retryTimer) {
        clearInterval(retryTimer);
      }
    };
  }, [fetchSkills, gatewayReportedReady, gatewayRunning, gatewayRuntimeKey]);

  useEffect(() => {
    let cancelled = false;
    void hostApi.skills.clawhubCapability()
      .then((result) => {
        if (cancelled) return;
        setMarketplaceAvailable(Boolean(result.success && (result.capability?.canInstall || result.capability?.canSearch)));
      })
      .catch(() => {
        if (!cancelled) setMarketplaceAvailable(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const safeSkills = Array.isArray(skills) ? skills : [];
  const enabledSkillsCount = safeSkills.filter((skill) => skill.enabled).length;
  const disabledSkillsCount = safeSkills.filter((skill) => !skill.enabled).length;
  const filteredSkills = safeSkills.filter((skill) => {
    const q = searchQuery.toLowerCase().trim();
    const matchesSearch = q.length === 0
      || skill.name.toLowerCase().includes(q)
      || skill.description.toLowerCase().includes(q)
      || skill.id.toLowerCase().includes(q)
      || (skill.slug || '').toLowerCase().includes(q)
      || (skill.author || '').toLowerCase().includes(q);
    const matchesStatus = statusFilter === 'all'
      || (statusFilter === 'enabled' ? skill.enabled : !skill.enabled);
    return matchesSearch && matchesStatus;
  }).sort((a, b) => {
    if (a.enabled && !b.enabled) return -1;
    if (!a.enabled && b.enabled) return 1;
    if (a.isCore && !b.isCore) return -1;
    if (!a.isCore && b.isCore) return 1;
    return a.name.localeCompare(b.name);
  });


  const handleToggle = useCallback(async (skillId: string, enable: boolean) => {
    try {
      if (enable) {
        await enableSkill(skillId);
        toast.success(t('toast.enabled'));
      } else {
        await disableSkill(skillId);
        toast.success(t('toast.disabled'));
      }
    } catch (err) {
      toast.error(String(err));
    }
  }, [enableSkill, disableSkill, t]);

  const handleSync = useCallback(async () => {
    const result = await syncSkills();
    if (!result.success) {
      if (result.requiresAuth) {
        toast.error(t('toast.syncAuthRequired'));
      } else {
        toast.error(t('toast.syncFailed') + (result.error ? ': ' + result.error : ''));
      }
      return;
    }
    const pushed = result.pushed ?? 0;
    const pulled = result.pulled ?? 0;
    const failed = result.failed ?? 0;
    if (failed > 0) {
      toast.error(t('toast.syncPartial', { failed }));
    } else if (pushed === 0 && pulled === 0) {
      toast.success(t('toast.syncNoChanges'));
    } else {
      toast.success(t('toast.syncSuccess', { pushed, pulled }));
    }
  }, [syncSkills, t]);

  const handleStatusFilterClick = useCallback((nextFilter: 'enabled' | 'disabled') => {
    setStatusFilter((current) => (current === nextFilter ? 'all' : nextFilter));
  }, []);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [pendingOverwrite, setPendingOverwrite] = useState<{ fileName: string; contentBase64: string; slug?: string } | null>(null);

  const performUpload = useCallback(async (payload: { fileName: string; contentBase64: string; overwrite?: boolean }) => {
    setUploading(true);
    try {
      const result = await hostApi.skills.upload(payload);
      if (result.success) {
        toast.success(t('toast.uploadSuccess', { slug: result.slug ?? payload.fileName }));
        await fetchSkills();
        return;
      }
      if (result.code === 'exists') {
        setPendingOverwrite({ fileName: payload.fileName, contentBase64: payload.contentBase64, slug: result.slug });
        return;
      }
      if (result.code === 'invalid_skill') {
        toast.error(t('toast.uploadInvalidSkill'));
      } else if (result.code === 'too_large') {
        toast.error(t('toast.uploadTooLarge'));
      } else if (result.code === 'unsupported_type') {
        toast.error(t('toast.uploadUnsupportedType'));
      } else if (result.code === 'zip_unsafe') {
        toast.error(t('toast.uploadZipUnsafe'));
      } else {
        toast.error(t('toast.uploadFailed') + (result.error ? ': ' + result.error : ''));
      }
    } catch (err) {
      toast.error(t('toast.uploadFailed') + ': ' + String(err));
    } finally {
      setUploading(false);
    }
  }, [fetchSkills, setPendingOverwrite, t]);

  const handleUploadFileSelected = useCallback((file: File) => {
    const lowerName = file.name.toLowerCase();
    if (!lowerName.endsWith('.md') && !lowerName.endsWith('.zip')) {
      toast.error(t('toast.uploadUnsupportedType'));
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      toast.error(t('toast.uploadTooLarge'));
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = typeof reader.result === 'string' ? reader.result : '';
      const contentBase64 = dataUrl.split(',')[1] ?? '';
      if (!contentBase64) {
        toast.error(t('toast.uploadFailed'));
        return;
      }
      void performUpload({ fileName: file.name, contentBase64 });
    };
    reader.onerror = () => {
      toast.error(t('toast.uploadFailed'));
    };
    reader.readAsDataURL(file);
  }, [performUpload, t]);

  const [skillsDirPath, setSkillsDirPath] = useState('~/.openclaw/skills');

  useEffect(() => {
    hostApi.openclaw.getSkillsDir()
      .then((dir) => setSkillsDirPath(dir))
      .catch(console.error);
  }, []);

  useEffect(() => {
    if (!installSheetOpen) {
      return;
    }

    const query = installQuery.trim();
    if (query.length === 0) {
      searchSkills('');
      return;
    }

    const timer = setTimeout(() => {
      searchSkills(query);
    }, 300);
    return () => clearTimeout(timer);
  }, [installQuery, installSheetOpen, searchSkills]);

  const handleInstall = useCallback(async (slug: string) => {
    try {
      await installSkill(slug);
      toast.success(t('toast.installed'));
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err);
      if (INSTALL_ERROR_CODES.has(errorMessage)) {
        toast.error(t(`toast.${errorMessage}`, { path: skillsDirPath }), { duration: 10000 });
      } else {
        toast.error(t('toast.failedInstall') + ': ' + errorMessage);
      }
    }
  }, [installSkill, t, skillsDirPath]);
  const handleUninstall = useCallback(async (slug: string) => {
    try {
      await uninstallSkill(slug);
      toast.success(t('toast.uninstalled'));
    } catch (err) {
      toast.error(t('toast.failedUninstall') + ': ' + String(err));
    }
  }, [uninstallSkill, t]);

  if (loading) {
    return (
      <div className="flex flex-col h-full w-full dark:bg-background items-center justify-center">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  return (
    <div data-testid="skills-page" className="flex flex-col h-full w-full dark:bg-background overflow-hidden">
      <div className="w-full max-w-5xl mx-auto flex flex-col h-full p-6 pt-8">

        {/* Header */}
        <SettingsPageHeader
          title={t('title')}
          description={t('subtitle')}
          actions={
            <>
              <button
                onClick={handleSync}
                disabled={syncing}
                className="hover:bg-black/5 dark:hover:bg-white/5 transition-colors shrink-0 text-meta font-medium px-4 h-8 rounded-full border border-black/10 dark:border-white/10 flex items-center justify-center text-foreground/80 hover:text-foreground disabled:opacity-60 disabled:cursor-not-allowed"
              >
                <RefreshCw className={cn('h-4 w-4 mr-2', syncing && 'animate-spin')} />
                {syncing ? t('syncing') : t('sync')}
              </button>
              <button
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading}
                className="hover:bg-black/5 dark:hover:bg-white/5 transition-colors shrink-0 text-meta font-medium px-4 h-8 rounded-full border border-black/10 dark:border-white/10 flex items-center justify-center text-foreground/80 hover:text-foreground disabled:opacity-60 disabled:cursor-not-allowed"
              >
                <Upload className="h-4 w-4 mr-2" />
                {uploading ? t('uploading') : t('upload')}
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept=".md,.zip"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  e.target.value = '';
                  if (file) handleUploadFileSelected(file);
                }}
              />
            </>
          }
        />

        {/* Gateway Status Banner */}
        {showGatewayBanner && gatewayBannerState !== 'none' && (
          <div
            data-testid="skills-gateway-banner"
            data-state={gatewayBannerState}
            className="mb-6 p-4 rounded-xl border border-yellow-500/50 bg-yellow-500/10 flex items-center gap-3"
          >
            <AlertCircle className="h-5 w-5 text-yellow-600 dark:text-yellow-400" />
            <span className="text-sm font-medium text-yellow-700 dark:text-yellow-400">
              {t('gatewayWarning')}
            </span>
          </div>
        )}

        {/* Toolbar: prominent search + status filters + marketplace */}
        <div className="shrink-0 mb-5 space-y-3">
          <div className="relative">
            <Search className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder={t('search')}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="h-12 rounded-2xl pl-11 pr-11 text-sm bg-black/[0.03] dark:bg-white/[0.04] border-black/10 dark:border-white/10 shadow-sm focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:border-primary/40 transition-all"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="absolute right-4 top-1/2 -translate-y-1/2 text-foreground/50 hover:text-foreground"
              >
                <X className="h-4 w-4" />
              </button>
            )}
          </div>
          <div className="flex items-center justify-between gap-3 flex-wrap">
            <div className="flex items-center flex-wrap gap-2">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                data-testid="skills-filter-enabled"
                onClick={() => handleStatusFilterClick('enabled')}
                className={cn(
                  'h-8 rounded-full px-3.5 text-meta font-medium border shadow-none transition-colors',
                  statusFilter === 'enabled'
                    ? 'bg-primary/10 border-primary/30 text-primary'
                    : 'bg-transparent border-black/10 dark:border-white/10 text-muted-foreground hover:text-foreground hover:bg-black/5 dark:hover:bg-white/5',
                )}
              >
                {t('filter.enabledList', { count: enabledSkillsCount })}
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                data-testid="skills-filter-disabled"
                onClick={() => handleStatusFilterClick('disabled')}
                className={cn(
                  'h-8 rounded-full px-3.5 text-meta font-medium border shadow-none transition-colors',
                  statusFilter === 'disabled'
                    ? 'bg-primary/10 border-primary/30 text-primary'
                    : 'bg-transparent border-black/10 dark:border-white/10 text-muted-foreground hover:text-foreground hover:bg-black/5 dark:hover:bg-white/5',
                )}
              >
                {t('filter.disabledList', { count: disabledSkillsCount })}
              </Button>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              {marketplaceAvailable && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setInstallQuery('');
                    setInstallSheetOpen(true);
                  }}
                  className="h-8 text-meta font-medium rounded-full px-4 border-black/10 dark:border-white/10 bg-transparent hover:bg-black/5 dark:hover:bg-white/5 shadow-none"
                >
                  <Package className="h-3.5 w-3.5 mr-1.5" />
                  {t('actions.installSkill')}
                </Button>
              )}
            </div>
          </div>
        </div>

        {/* Content Area */}
        <div className="flex-1 overflow-y-auto pr-2 pb-10 min-h-0 -mr-2">
          {error && (
            <div className="mb-4 p-4 rounded-xl border border-destructive/50 bg-destructive/10 text-destructive text-sm font-medium flex items-center gap-2">
              <AlertCircle className="h-5 w-5 shrink-0" />
              <span>
                {FETCH_ERROR_CODES.has(error)
                  ? t(`toast.${error}`, { path: skillsDirPath })
                  : error}
              </span>
            </div>
          )}

          <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
            {filteredSkills.length === 0 ? (
              <div className="xl:col-span-2 flex flex-col items-center justify-center py-20 text-muted-foreground">
                <Puzzle className="h-10 w-10 mb-4 opacity-50" />
                <p>{searchQuery ? t('noSkillsSearch') : t('noSkillsAvailable')}</p>
              </div>
            ) : (
              filteredSkills.map((skill) => (
                <div
                  key={skill.id}
                  className="group relative flex flex-col rounded-2xl glass-card p-5 hover:shadow-md hover:-translate-y-0.5 transition-all cursor-pointer"
                  onClick={() => setSelectedSkill(skill)}
                >
                  <div className="flex items-start gap-4">
                    <div className="h-12 w-12 shrink-0 flex items-center justify-center text-2xl bg-black/5 dark:bg-white/5 border border-black/5 dark:border-white/10 rounded-xl overflow-hidden">
                      {isValidEmoji(skill.icon) ? skill.icon : '🧩'}
                    </div>
                    <div className="flex flex-col overflow-hidden flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <h3 className="text-sm font-semibold text-foreground truncate">{skill.name}</h3>
                        {skill.isCore ? (
                          <Lock className="h-3 w-3 text-muted-foreground shrink-0" />
                        ) : null}
                        {skill.version && (
                          <span className="text-tiny font-mono text-muted-foreground shrink-0">
                            v{skill.version}
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-muted-foreground line-clamp-2 leading-relaxed min-h-[2.6em]">
                        {skill.description}
                      </p>
                    </div>
                    <div className="flex items-center shrink-0" onClick={e => e.stopPropagation()}>
                      <Switch
                        checked={skill.enabled}
                        onCheckedChange={(checked) => handleToggle(skill.id, checked)}
                        disabled={skill.isCore}
                      />
                    </div>
                  </div>
                  <div className="mt-4 pt-3 border-t border-black/5 dark:border-white/10 flex items-center gap-2 min-w-0">
                    <Badge variant="secondary" className="shrink-0 whitespace-nowrap px-2 py-0 h-5 text-2xs font-medium bg-black/5 dark:bg-white/10 border-0 shadow-none">
                      {resolveSkillSourceLabel(skill, t)}
                    </Badge>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <Sheet open={installSheetOpen && marketplaceAvailable} onOpenChange={setInstallSheetOpen}>
        <SheetContent
          className="w-full sm:max-w-[560px] p-0 flex flex-col border-l border-black/10 dark:border-white/10 bg-surface-modal shadow-[0_0_40px_rgba(0,0,0,0.2)]"
          side="right"
        >
          <div className="px-7 py-6 border-b border-black/10 dark:border-white/10">
            <h2 className="text-2xl font-bold tracking-tight">{t('marketplace.installDialogTitle')}</h2>
            <p className="mt-1 text-meta text-foreground/70">{t('marketplace.installDialogSubtitle')}</p>
            <div className="mt-4 flex flex-col md:flex-row gap-2">
              <div className="relative flex items-center bg-black/5 dark:bg-white/5 rounded-xl px-3 py-2 border border-black/10 dark:border-white/10 flex-1">
                <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
                <Input
                  placeholder={t('searchMarketplace')}
                  value={installQuery}
                  onChange={(e) => setInstallQuery(e.target.value)}
                  className="ml-2 h-auto border-0 bg-transparent p-0 shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 text-meta"
                />
                {installQuery && (
                  <button
                    type="button"
                    onClick={() => setInstallQuery('')}
                    className="text-foreground/50 hover:text-foreground shrink-0 ml-1"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                )}
              </div>
              <Button
                variant="outline"
                disabled
                className="h-10 rounded-xl border-black/10 dark:border-white/10 bg-transparent text-muted-foreground"
              >
                {t('marketplace.sourceLabel')}: {t('marketplace.sourceClawHub')}
              </Button>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-6 py-4">
            {searchError && (
              <div className="mb-4 p-4 rounded-xl border border-destructive/50 bg-destructive/10 text-destructive text-sm font-medium flex items-center gap-2">
                <AlertCircle className="h-5 w-5 shrink-0" />
                <span>
                  {SEARCH_ERROR_CODES.has(searchError.replace('Error: ', ''))
                    ? t(`toast.${searchError.replace('Error: ', '')}`, { path: skillsDirPath })
                    : searchError}
                </span>
              </div>
            )}

            {searching && (
              <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                <LoadingSpinner size="lg" />
                <p className="mt-4 text-sm">{t('marketplace.searching')}</p>
              </div>
            )}

            {!searching && searchResults.length > 0 && (
              <div className="flex flex-col gap-1">
                {searchResults.map((skill) => {
                  const isInstalled = safeSkills.some(s => s.id === skill.slug || s.name === skill.name);
                  const isInstallLoading = !!installing[skill.slug];

                  return (
                    <div
                      key={skill.slug}
                      className="group flex flex-row items-center justify-between py-3.5 px-3 rounded-xl hover:bg-black/5 dark:hover:bg-white/5 transition-colors cursor-pointer border-b border-black/5 dark:border-white/5 last:border-0"
                      onClick={() => hostApi.shell.openExternal(`https://clawhub.ai/s/${skill.slug}`)}
                    >
                      <div className="flex items-start gap-4 flex-1 overflow-hidden pr-4">
                        <div className="h-10 w-10 shrink-0 flex items-center justify-center text-xl bg-black/5 dark:bg-white/5 border border-black/5 dark:border-white/10 rounded-xl overflow-hidden">
                          📦
                        </div>
                        <div className="flex flex-col overflow-hidden">
                          <div className="flex items-center gap-2 mb-1">
                            <h3 className="text-sm font-semibold text-foreground truncate">{skill.name}</h3>
                            {skill.author && (
                              <span className="text-xs text-muted-foreground">�?{skill.author}</span>
                            )}
                          </div>
                          <p className="text-sm text-muted-foreground line-clamp-1 pr-6 leading-relaxed">
                            {skill.description}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-4 shrink-0" onClick={e => e.stopPropagation()}>
                        {skill.version && (
                          <span className="text-meta font-mono text-muted-foreground mr-2">
                            v{skill.version}
                          </span>
                        )}
                        {isInstalled ? (
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => handleUninstall(skill.slug)}
                            disabled={isInstallLoading}
                            className="h-8 shadow-none"
                          >
                            {isInstallLoading ? <LoadingSpinner size="sm" /> : <Trash2 className="h-3.5 w-3.5" />}
                          </Button>
                        ) : (
                          <Button
                            variant="default"
                            size="sm"
                            onClick={() => handleInstall(skill.slug)}
                            disabled={isInstallLoading}
                            className="h-8 px-4 rounded-full shadow-none font-medium text-xs"
                          >
                            {isInstallLoading ? <LoadingSpinner size="sm" /> : t('marketplace.install', 'Install')}
                          </Button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {!searching && searchResults.length === 0 && !searchError && (
              <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                <Package className="h-10 w-10 mb-4 opacity-50" />
                <p>{installQuery.trim() ? t('marketplace.noResults') : t('marketplace.emptyPrompt')}</p>
              </div>
            )}
          </div>
        </SheetContent>
      </Sheet>

      {/* Skill Detail Dialog */}
      <SkillDetailDialog
        skill={selectedSkill}
        isOpen={!!selectedSkill}
        onClose={() => setSelectedSkill(null)}
        onToggle={(enabled) => {
          if (!selectedSkill) return;
          handleToggle(selectedSkill.id, enabled);
          setSelectedSkill({ ...selectedSkill, enabled });
        }}
        onUninstall={handleUninstall}
      />

      {/* Skill upload overwrite confirmation */}
      <ConfirmDialog
        open={!!pendingOverwrite}
        title={t('uploadOverwriteConfirmTitle')}
        message={t('uploadOverwriteConfirmDesc', { slug: pendingOverwrite?.slug ?? pendingOverwrite?.fileName ?? '' })}
        confirmLabel={t('uploadOverwriteConfirmConfirm')}
        cancelLabel={t('uploadOverwriteConfirmCancel')}
        onConfirm={async () => {
          const pending = pendingOverwrite;
          setPendingOverwrite(null);
          if (pending) {
            await performUpload({ fileName: pending.fileName, contentBase64: pending.contentBase64, overwrite: true });
          }
        }}
        onCancel={() => setPendingOverwrite(null)}
      />
    </div>
  );
}

export default Skills;
