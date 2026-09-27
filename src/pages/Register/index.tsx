/**
 * Register Page
 *
 * 桌面端轻量注册：仅手机号 + 密码；企业另需公司名（信用代码选填）。
 * 与 Login 同视觉语言。注册经主进程 `hostApi.auth.register`（渲染进程不直连后端），
 * 成功后用手机号+密码自动登录进入应用。
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, Building2, Lock, Phone, UserPlus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { TitleBar } from '@/components/layout/TitleBar';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { hostApi } from '@/lib/host-api';
import { useAuthStore } from '@/stores/auth';

type UserType = 'PERSONAL' | 'ENTERPRISE';
type FieldErrors = Partial<Record<'phone' | 'password' | 'confirmPassword' | 'companyName', string>>;

const PHONE_RE = /^1[3-9]\d{9}$/;

export function Register() {
  const navigate = useNavigate();
  const login = useAuthStore((state) => state.login);

  const [userType, setUserType] = useState<UserType>('ENTERPRISE');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [companyName, setCompanyName] = useState('');
  const [unifiedCreditCode, setUnifiedCreditCode] = useState('');
  const [legalPerson, setLegalPerson] = useState('');
  const [contactEmail, setContactEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  const enterprise = userType === 'ENTERPRISE';

  function validate(): boolean {
    const next: FieldErrors = {};
    if (!PHONE_RE.test(phone.trim())) next.phone = '请输入有效的手机号';
    if (password.length < 6) next.password = '密码至少 6 位';
    if (confirmPassword !== password) next.confirmPassword = '两次输入的密码不一致';
    if (enterprise && !companyName.trim()) next.companyName = '请填写公司名称';
    setFieldErrors(next);
    return Object.keys(next).length === 0;
  }

  async function handleSubmit() {
    setError(null);
    if (!validate()) return;
    setSubmitting(true);
    try {
      const result = await hostApi.auth.register({
        phone: phone.trim(),
        password,
        userType,
        ...(enterprise
          ? {
              companyName: companyName.trim(),
              unifiedCreditCode: unifiedCreditCode.trim() || undefined,
              legalPerson: legalPerson.trim() || undefined,
              contactEmail: contactEmail.trim() || undefined,
            }
          : {}),
      });
      if (!result.success) {
        setError(result.error || '注册失败，请稍后重试');
        return;
      }
      // 注册成功后用手机号+密码自动登录
      const ok = await login(phone.trim(), password, true);
      if (ok) {
        navigate('/', { replace: true });
      } else {
        // 注册成功但自动登录失败：回到登录页让用户手动登录
        navigate('/login', { replace: true });
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '注册失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  }

  const inputCls = 'pl-12 h-12 bg-slate-50 border-slate-200 text-slate-800 focus-visible:ring-blue-500/30';

  return (
    <div className="min-h-screen relative overflow-hidden bg-gradient-to-br from-slate-50 via-blue-50 to-purple-50 dark:from-[hsl(15,60%,8%)] dark:via-[hsl(20,70%,12%)] dark:to-[hsl(25,65%,8%)] flex items-center justify-center p-4">
      <div className="absolute inset-x-0 top-0 z-20">
        <TitleBar />
      </div>
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-40 -right-40 w-96 h-96 bg-gradient-to-br from-blue-400/25 to-purple-400/25 dark:from-orange-400/25 dark:to-red-400/25 rounded-full blur-3xl animate-pulse" />
        <div className="absolute -bottom-40 -left-40 w-96 h-96 bg-gradient-to-tr from-purple-400/25 to-blue-400/25 dark:from-red-400/25 dark:to-orange-400/25 rounded-full blur-3xl animate-pulse" style={{ animationDelay: '1.5s' }} />
      </div>

      <div className="relative w-full max-w-md">
        <Card className="shadow-2xl border-0 bg-white/90 backdrop-blur-md relative overflow-hidden text-slate-800 dark:bg-slate-900/90 dark:text-slate-100">
          <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-blue-600 via-purple-500 to-purple-600 dark:from-orange-600 dark:via-red-500 dark:to-red-600" />
          <CardHeader className="space-y-3 pb-5 pt-8">
            <div className="flex items-center gap-3 mb-1">
              <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-600 to-purple-600 flex items-center justify-center shadow-lg dark:from-orange-600 dark:to-red-600">
                <span className="text-white font-bold text-2xl">JY</span>
              </div>
              <h1 className="text-xl font-bold text-slate-800 dark:text-slate-100">JYparalegal</h1>
            </div>
            <CardTitle className="text-2xl font-bold text-center text-slate-800">创建账户</CardTitle>
            <CardDescription className="text-base text-center text-slate-500">
              注册后即可使用 AI 合同工作台
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 pb-8">
            {/* 账户类型切换 */}
            <div className="grid grid-cols-2 gap-2 p-1 rounded-lg bg-slate-100">
              {(['ENTERPRISE', 'PERSONAL'] as const).map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setUserType(t)}
                  className={`h-9 rounded-md text-sm font-medium transition-colors ${
                    userType === t ? 'bg-white text-blue-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
                  }`}
                >
                  {t === 'ENTERPRISE' ? '企业用户' : '个人用户'}
                </button>
              ))}
            </div>

            {/* 手机号 */}
            <div className="space-y-2">
              <Label htmlFor="phone" className="text-sm font-semibold text-slate-700">手机号</Label>
              <div className="relative group">
                <Phone className="absolute left-3.5 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-400 group-focus-within:text-blue-600 transition-colors" />
                <Input id="phone" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel"
                  className={inputCls} aria-invalid={!!fieldErrors.phone} placeholder="请输入手机号" />
              </div>
              {fieldErrors.phone ? <FieldError msg={fieldErrors.phone} /> : null}
            </div>

            {/* 密码 */}
            <div className="space-y-2">
              <Label htmlFor="password" className="text-sm font-semibold text-slate-700">密码</Label>
              <div className="relative group">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-400 group-focus-within:text-blue-600 transition-colors" />
                <Input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                  autoComplete="new-password" className={inputCls} aria-invalid={!!fieldErrors.password} placeholder="至少 6 位" />
              </div>
              {fieldErrors.password ? <FieldError msg={fieldErrors.password} /> : null}
            </div>

            {/* 确认密码 */}
            <div className="space-y-2">
              <Label htmlFor="confirmPassword" className="text-sm font-semibold text-slate-700">确认密码</Label>
              <div className="relative group">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-400 group-focus-within:text-blue-600 transition-colors" />
                <Input id="confirmPassword" type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)}
                  autoComplete="new-password" className={inputCls} aria-invalid={!!fieldErrors.confirmPassword} placeholder="再次输入密码"
                  onKeyDown={(e) => { if (e.key === 'Enter') void handleSubmit(); }} />
              </div>
              {fieldErrors.confirmPassword ? <FieldError msg={fieldErrors.confirmPassword} /> : null}
            </div>

            {/* 企业信息 */}
            {enterprise ? (
              <div className="space-y-4 rounded-lg border border-slate-200 bg-slate-50/50 p-4">
                <div className="flex items-center gap-2 text-sm font-semibold text-slate-700">
                  <Building2 className="h-4 w-4 text-blue-600" /> 企业信息
                </div>
                <div className="space-y-2">
                  <Label htmlFor="companyName" className="text-sm font-medium text-slate-600">公司名称 <span className="text-red-500">*</span></Label>
                  <Input id="companyName" value={companyName} onChange={(e) => setCompanyName(e.target.value)}
                    className="h-11 bg-white border-slate-200 text-slate-800" aria-invalid={!!fieldErrors.companyName} placeholder="企业全称" />
                  {fieldErrors.companyName ? <FieldError msg={fieldErrors.companyName} /> : null}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="creditCode" className="text-sm font-medium text-slate-600">统一社会信用代码</Label>
                  <Input id="creditCode" value={unifiedCreditCode} onChange={(e) => setUnifiedCreditCode(e.target.value)}
                    className="h-11 bg-white border-slate-200 text-slate-800" placeholder="选填" />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-2">
                    <Label htmlFor="legalPerson" className="text-sm font-medium text-slate-600">法人</Label>
                    <Input id="legalPerson" value={legalPerson} onChange={(e) => setLegalPerson(e.target.value)}
                      className="h-11 bg-white border-slate-200 text-slate-800" placeholder="选填" />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="contactEmail" className="text-sm font-medium text-slate-600">联系邮箱</Label>
                    <Input id="contactEmail" value={contactEmail} onChange={(e) => setContactEmail(e.target.value)}
                      className="h-11 bg-white border-slate-200 text-slate-800" placeholder="选填" />
                  </div>
                </div>
              </div>
            ) : (
              <p className="text-xs text-slate-500 leading-5">
                个人用户可使用 AI Agent 能力；签署、庭审、证据等法律域功能为企业用户专属。
              </p>
            )}

            {error ? (
              <div className="flex items-center gap-2 rounded-lg bg-red-50 border border-red-200 px-3 py-2.5 text-sm text-red-600">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            ) : null}

            <Button className="w-full h-12 bg-gradient-to-r from-blue-600 via-blue-500 to-purple-600 hover:opacity-90 text-white font-semibold shadow-lg shadow-blue-500/30 dark:from-orange-600 dark:via-red-500 dark:to-red-600 dark:shadow-red-500/30"
              size="lg" onClick={() => void handleSubmit()} disabled={submitting}>
              {submitting ? (
                <span className="flex items-center gap-2.5">
                  <div className="h-5 w-5 border-2 border-current border-t-transparent rounded-full animate-spin" /> 注册中...
                </span>
              ) : (
                <span className="flex items-center gap-2.5"><UserPlus className="h-5 w-5" /> 注册</span>
              )}
            </Button>

            <div className="text-center text-sm text-slate-500">
              已有账户？
              <button type="button" className="ml-1 font-medium text-blue-600 hover:underline" onClick={() => navigate('/login')}>
                去登录
              </button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function FieldError({ msg }: { msg: string }) {
  return (
    <div className="flex items-center gap-1.5 text-sm text-red-600 mt-1.5">
      <AlertCircle className="h-4 w-4" />
      <span>{msg}</span>
    </div>
  );
}
