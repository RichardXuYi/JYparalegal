/**
 * Account settings section.
 * Account info, logout, cloud config sync, and skill sync.
 */
import { useCallback, useEffect, useState } from 'react';
import {
  LogOut,
  User,
  ArrowLeftRight,
  Cloud,
  CloudUpload,
  CloudDownload,
  ShieldCheck,
  Monitor,
  Smartphone,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { toast } from 'sonner';
import { hostApi } from '@/lib/host-api';
import type { SyncScope, SyncScopeStatus } from '@/lib/host-api';
import type { AuthDevice } from '@shared/host-api/contract';
import { useAuthStore } from '@/stores/auth';
import { useSkillsStore } from '@/stores/skills';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { SettingsGroup, SettingRow } from '@/components/settings/primitives';

interface AccountSectionProps {
  gradientClass?: string;
}

type SyncDirection = 'upload' | 'download';

const SYNC_SCOPES: { scope: SyncScope; labelKey: string; labelDefault: string; descKey: string; descDefault: string }[] = [
  {
    scope: 'skills',
    labelKey: 'account.cloudSync.scopeSkills',
    labelDefault: '技能',
    descKey: 'account.cloudSync.scopeSkillsDesc',
    descDefault: '你自行管理的技能',
  },
  {
    scope: 'preferences',
    labelKey: 'account.cloudSync.scopePreferences',
    labelDefault: '偏好设置',
    descKey: 'account.cloudSync.scopePreferencesDesc',
    descDefault: '主题、语言等非敏感应用设置（含人格与记忆）',
  },
];

export function AccountSection({ gradientClass }: AccountSectionProps) {
  const { t } = useTranslation('settings');
  const currentUser = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const currentDeviceId = useAuthStore((state) => state.currentDeviceId);
  const [loggingOut, setLoggingOut] = useState(false);

  // Device management
  const [devices, setDevices] = useState<AuthDevice[]>([]);
  const [devicesLoading, setDevicesLoading] = useState(true);
  const [revokeConfirm, setRevokeConfirm] = useState<{ open: boolean; deviceId: number | null }>({
    open: false,
    deviceId: null,
  });

  const fetchDevices = useCallback(async () => {
    try {
      const result = await hostApi.auth.listDevices();
      if (result.success) {
        setDevices(result.devices ?? []);
      }
    } finally {
      setDevicesLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchDevices();
  }, [fetchDevices]);

  const handleRevokeDevice = async () => {
    const deviceId = revokeConfirm.deviceId;
    if (deviceId === null) return;
    setRevokeConfirm({ open: false, deviceId: null });
    try {
      const result = await hostApi.auth.revokeDevice(deviceId);
      if (result.success) {
        toast.success(t('account.devices.disconnected', '已断开该设备的连接'));
        void fetchDevices();
      } else {
        toast.error(t('account.devices.failed', '断开连接失败'));
      }
    } catch {
      toast.error(t('account.devices.failed', '断开连接失败'));
    }
  };

  const formatDeviceName = (device: AuthDevice): string => {
    if (device.deviceName) return device.deviceName;
    if (device.userAgent) {
      const ua = device.userAgent;
      if (ua.includes('Windows')) return 'Windows';
      if (ua.includes('Mac')) return 'macOS';
      if (ua.includes('Linux')) return 'Linux';
      return ua.substring(0, 40);
    }
    return 'Unknown';
  };

  const formatLoginTime = (createdAt: string): string => {
    try {
      return new Date(createdAt).toLocaleString();
    } catch {
      return createdAt;
    }
  };

  // Cloud config sync (directional: upload = local→cloud, download = cloud→local).
  const [selectedScopes, setSelectedScopes] = useState<SyncScope[]>(['skills', 'preferences']);
  const [cloudBusy, setCloudBusy] = useState<SyncDirection | null>(null);
  const [confirm, setConfirm] = useState<{ open: boolean; direction: SyncDirection; message: string }>({
    open: false,
    direction: 'upload',
    message: '',
  });

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      setLoggingOut(false);
    }
  };

  const toggleScope = (scope: SyncScope) => {
    setSelectedScopes((prev) =>
      prev.includes(scope) ? prev.filter((item) => item !== scope) : [...prev, scope],
    );
  };

  const scopeLabel = (scope: SyncScope): string => {
    const meta = SYNC_SCOPES.find((item) => item.scope === scope);
    return meta ? t(meta.labelKey, meta.labelDefault) : scope;
  };

  const buildPreview = (statuses: SyncScopeStatus[]): string => {
    if (statuses.length === 0) return t('account.cloudSync.previewEmpty', '没有可同步的内容');
    return statuses
      .map((status) => {
        const state = status.inSync
          ? t('account.cloudSync.inSync', '已一致')
          : t('account.cloudSync.diff', '有差异');
        const local = status.localItems ?? (status.localHash ? 1 : 0);
        const remote = status.remoteItems ?? (status.remoteHash ? 1 : 0);
        return `${scopeLabel(status.scope)}: ${state} (${t('account.cloudSync.local', '本地')} ${local} / ${t('account.cloudSync.remote', '云端')} ${remote})`;
      })
      .join('  ·  ');
  };

  const openConfirm = async (direction: SyncDirection) => {
    if (selectedScopes.length === 0) {
      toast.error(t('account.cloudSync.selectAtLeastOne', '请至少选择一项同步范围'));
      return;
    }
    setCloudBusy(direction);
    try {
      const status = await hostApi.sync.status({ scopes: selectedScopes });
      if (!status.success) {
        if (status.requiresAuth) {
          toast.error(t('account.cloudSync.authRequired', '请先登录后再同步'));
        } else {
          toast.error(t('account.cloudSync.statusFailed', '获取同步状态失败') + (status.error ? `: ${status.error}` : ''));
        }
        return;
      }
      const preview = buildPreview(status.scopes ?? []);
      const notice =
        direction === 'upload'
          ? t('account.cloudSync.uploadNotice', '将用本机配置覆盖云端备份。')
          : t('account.cloudSync.downloadNotice', '将用云端配置覆盖本机（覆盖前已自动备份到本地）。');
      setConfirm({ open: true, direction, message: `${notice}  ${preview}` });
    } catch (error) {
      toast.error(t('account.cloudSync.statusFailed', '获取同步状态失败') + `: ${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setCloudBusy(null);
    }
  };

  const runCloudSync = async () => {
    const direction = confirm.direction;
    setConfirm((prev) => ({ ...prev, open: false }));
    setCloudBusy(direction);
    try {
      const payload = { scopes: selectedScopes };
      const result = direction === 'upload'
        ? await hostApi.sync.upload(payload)
        : await hostApi.sync.download(payload);
      if (!result.success) {
        if (result.requiresAuth) {
          toast.error(t('account.cloudSync.authRequired', '请先登录后再同步'));
        } else {
          const failed = (result.results ?? []).filter((item) => !item.ok);
          const detail = failed.map((item) => `${scopeLabel(item.scope)}${item.error ? `: ${item.error}` : ''}`).join('; ');
          toast.error(t('account.cloudSync.failed', '同步失败') + (detail ? `: ${detail}` : ''));
        }
        return;
      }
      const totals = (result.results ?? []).reduce(
        (acc, item) => ({ pushed: acc.pushed + (item.pushed ?? 0), pulled: acc.pulled + (item.pulled ?? 0) }),
        { pushed: 0, pulled: 0 },
      );
      if (direction === 'upload') {
        toast.success(t('account.cloudSync.uploadDone', '已上传到云端（{{count}} 项）', { count: totals.pushed }));
      } else {
        toast.success(t('account.cloudSync.downloadDone', '已从云端下载（{{count}} 项）', { count: totals.pulled }));
        // Pulled skills/agents may have changed local disk — refresh skills list.
        void useSkillsStore.getState().fetchSkills();
      }
    } catch (error) {
      toast.error(t('account.cloudSync.failed', '同步失败') + `: ${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setCloudBusy(null);
    }
  };

  return (
    <div className="space-y-6">
      <SettingsGroup title={t('account.title', '账户')} gradientClass={gradientClass} icon={User}>
        <SettingRow
          icon={User}
          label={currentUser?.username || t('account.unknownUser', '未知用户')}
          description={currentUser?.email || t('account.signedIn', '已登录')}
          control={
            <Button
              variant="outline"
              size="sm"
              onClick={() => void handleLogout()}
              disabled={loggingOut}
              className="rounded-full"
            >
              <LogOut className={cn('h-4 w-4 mr-2', loggingOut && 'animate-pulse')} />
              {loggingOut ? t('account.loggingOut', '退出中...') : t('account.logout', '退出登录')}
            </Button>
          }
        />
      </SettingsGroup>

      <SettingsGroup title={t('account.devices.title', '已登录设备')} gradientClass={gradientClass} icon={Monitor}>
        {devicesLoading ? (
          <SettingRow
            icon={Monitor}
            label={t('account.devices.loading', '加载中...')}
            description=""
          />
        ) : devices.length === 0 ? (
          <SettingRow
            icon={Monitor}
            label={t('account.devices.empty', '暂无其他已登录设备')}
            description=""
          />
        ) : (
          devices.map((device) => {
            const isCurrentDevice = device.id === currentDeviceId;
            return (
              <SettingRow
                key={device.id}
                icon={Smartphone}
                label={
                  <span className="flex items-center gap-2">
                    {formatDeviceName(device)}
                    {isCurrentDevice && (
                      <span className="inline-flex items-center rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                        {t('account.devices.thisDevice', '本机')}
                      </span>
                    )}
                  </span>
                }
                description={
                  <span className="flex flex-col gap-0.5">
                    {device.ipAddress && <span>IP: {device.ipAddress}</span>}
                    <span>{t('account.devices.loginTime', '登录时间：{{time}}', { time: formatLoginTime(device.createdAt) })}</span>
                  </span>
                }
                control={
                  isCurrentDevice ? undefined : (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setRevokeConfirm({ open: true, deviceId: device.id })}
                      className="rounded-full"
                    >
                      <LogOut className="h-4 w-4 mr-2" />
                      {t('account.devices.disconnect', '断开连接')}
                    </Button>
                  )
                }
              />
            );
          })
        )}
      </SettingsGroup>

      <SettingsGroup title={t('account.cloudSync.title', '云同步')} gradientClass={gradientClass} icon={Cloud}>
        <SettingRow
          align="start"
          icon={Cloud}
          label={t('account.cloudSync.scopeLabel', '同步范围')}
          description={t('account.cloudSync.scopeDesc', '选择要在设备之间同步的内容')}
        >
          <div className="flex flex-col gap-2">
            {SYNC_SCOPES.map((item) => {
              const active = selectedScopes.includes(item.scope);
              return (
                <button
                  key={item.scope}
                  type="button"
                  onClick={() => toggleScope(item.scope)}
                  className={cn(
                    'flex items-start gap-3 rounded-xl border px-3 py-2 text-left transition-colors',
                    active
                      ? 'border-primary bg-primary/5'
                      : 'border-border hover:bg-muted/40',
                  )}
                >
                  <span
                    className={cn(
                      'mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded border',
                      active ? 'border-primary bg-primary text-primary-foreground' : 'border-muted-foreground/40',
                    )}
                  >
                    {active && <span className="h-2 w-2 rounded-[2px] bg-current" />}
                  </span>
                  <span className="min-w-0">
                    <span className="block text-sm font-medium text-foreground">{t(item.labelKey, item.labelDefault)}</span>
                    <span className="block text-xs text-muted-foreground">{t(item.descKey, item.descDefault)}</span>
                  </span>
                </button>
              );
            })}
          </div>
        </SettingRow>

        <SettingRow
          icon={ArrowLeftRight}
          label={t('account.cloudSync.actions', '上传 / 下载')}
          description={t('account.cloudSync.actionsDesc', '上传把本机配置备份到服务器；下载用云端配置覆盖本机')}
          control={
            <div className="flex flex-col sm:flex-row gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => void openConfirm('upload')}
                disabled={cloudBusy !== null}
                className="rounded-full text-xs sm:text-sm"
              >
                <CloudUpload className={cn('h-4 w-4 mr-2', cloudBusy === 'upload' && 'animate-pulse')} />
                {cloudBusy === 'upload'
                  ? t('account.cloudSync.working', '处理中...')
                  : t('account.cloudSync.upload', '上传到云端')}
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => void openConfirm('download')}
                disabled={cloudBusy !== null}
                className="rounded-full text-xs sm:text-sm"
              >
                <CloudDownload className={cn('h-4 w-4 mr-2', cloudBusy === 'download' && 'animate-pulse')} />
                {cloudBusy === 'download'
                  ? t('account.cloudSync.working', '处理中...')
                  : t('account.cloudSync.download', '从云端下载')}
              </Button>
            </div>
          }
        />

        <SettingRow
          align="start"
          icon={ShieldCheck}
          label={t('account.cloudSync.securityTitle', '安全提示')}
          description={t(
            'account.cloudSync.securityNotice',
            'Provider API key 等敏感凭证不会上传，需在每台设备单独配置。',
          )}
        />
      </SettingsGroup>



      <ConfirmDialog
        open={revokeConfirm.open}
        title={t('account.devices.disconnectConfirmTitle', '断开设备连接')}
        message={t('account.devices.disconnectConfirmMessage', '确定要断开该设备的连接吗？断开后该设备需要重新登录。')}
        confirmLabel={t('account.devices.disconnect', '断开连接')}
        cancelLabel={t('account.devices.cancel', '取消')}
        variant="destructive"
        onConfirm={handleRevokeDevice}
        onCancel={() => setRevokeConfirm({ open: false, deviceId: null })}
      />

      <ConfirmDialog
        open={confirm.open}
        title={
          confirm.direction === 'upload'
            ? t('account.cloudSync.confirmUploadTitle', '上传到云端')
            : t('account.cloudSync.confirmDownloadTitle', '从云端下载')
        }
        message={confirm.message}
        confirmLabel={
          confirm.direction === 'upload'
            ? t('account.cloudSync.upload', '上传到云端')
            : t('account.cloudSync.download', '从云端下载')
        }
        cancelLabel={t('account.cloudSync.cancel', '取消')}
        variant={confirm.direction === 'download' ? 'destructive' : 'default'}
        onConfirm={runCloudSync}
        onCancel={() => setConfirm((prev) => ({ ...prev, open: false }))}
      />
    </div>
  );
}

export default AccountSection;
