/**
 * Login Page
 *
 * Studio 端登录界面，复刻 web 端 GrandPoem 登录视觉（双栏品牌 + 表单），
 * 改用 Studio 现有 UI 组件与设计令牌。所有认证经主进程 `hostApi.auth`
 * 完成（渲染进程不直接访问后端），符合 harness 后端通信边界。
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, Building2, Lock, LogIn, Shield, User } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { TitleBar } from '@/components/layout/TitleBar';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuthStore } from '@/stores/auth';

export function Login() {
  const navigate = useNavigate();
  const login = useAuthStore((state) => state.login);
  const error = useAuthStore((state) => state.error);
  const clearError = useAuthStore((state) => state.clearError);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<{ username?: string; password?: string }>({});

  function validate() {
    const next: { username?: string; password?: string } = {};
    if (!username.trim()) next.username = '请输入账号';
    if (!password) next.password = '请输入密码';
    setFieldErrors(next);
    return Object.keys(next).length === 0;
  }

  async function handleSubmit() {
    clearError();
    if (!validate()) return;
    setSubmitting(true);
    try {
      const ok = await login(username.trim(), password, rememberMe);
      if (ok) {
        navigate('/', { replace: true });
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen relative overflow-hidden bg-gradient-to-br from-slate-50 via-blue-50 to-purple-50 dark:from-[hsl(15,60%,8%)] dark:via-[hsl(20,70%,12%)] dark:to-[hsl(25,65%,8%)] flex items-center justify-center p-4">
      {/* Windows 无边框窗口：登录页同样需要拖拽区与最小化/最大化/关闭控件 */}
      <div className="absolute inset-x-0 top-0 z-20">
        <TitleBar />
      </div>
      {/* 背景装饰 */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-40 -right-40 w-96 h-96 bg-gradient-to-br from-blue-400/25 to-purple-400/25 dark:from-orange-400/25 dark:to-red-400/25 rounded-full blur-3xl animate-pulse" />
        <div className="absolute -bottom-40 -left-40 w-96 h-96 bg-gradient-to-tr from-purple-400/25 to-blue-400/25 dark:from-red-400/25 dark:to-orange-400/25 rounded-full blur-3xl animate-pulse" style={{ animationDelay: '1.5s' }} />
        <div className="absolute top-1/3 left-1/3 w-[500px] h-[500px] bg-gradient-to-r from-blue-300/15 to-purple-300/15 dark:from-orange-300/15 dark:to-red-300/15 rounded-full blur-3xl" />
      </div>

      <div className="relative w-full max-w-5xl grid lg:grid-cols-2 gap-8 items-center">
        {/* 左侧品牌展示 */}
        <div className="hidden lg:flex flex-col gap-8 p-8">
          <div className="space-y-6">
            <div className="flex items-center gap-5">
              <div className="relative">
                <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-blue-600 via-blue-500 to-purple-600 flex items-center justify-center shadow-2xl shadow-blue-500/30 relative z-10 dark:from-orange-600 dark:via-red-500 dark:to-red-600 dark:shadow-red-500/30">
                  <span className="text-white font-bold text-4xl tracking-tight">JY</span>
                </div>
                <div className="absolute -inset-2 bg-gradient-to-br from-blue-600/40 to-purple-600/40 dark:from-orange-600/40 dark:to-red-600/40 rounded-3xl blur-xl animate-pulse" />
              </div>
              <div>
                <h1 className="text-3xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent dark:from-orange-500 dark:to-red-500">JYparalegal</h1>
                <p className="text-base text-slate-600 mt-1.5 dark:text-slate-300">给中国法律人用的 AI 合同工作台</p>
                <div className="flex items-center gap-2 mt-2">
                  <div className="h-1.5 w-1.5 rounded-full bg-blue-600 dark:bg-orange-600" />
                  <span className="text-xs text-slate-500 dark:text-slate-400">合同审查 · 发起签署 · 证据留存</span>
                </div>
              </div>
            </div>

            <div className="space-y-4 pt-4">
              <div className="flex items-start gap-4 p-4 rounded-xl bg-white/50 border border-slate-100/50 dark:bg-white/5 dark:border-white/10">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center shrink-0 shadow-lg shadow-blue-500/20 dark:from-orange-500 dark:to-red-600 dark:shadow-red-500/20">
                  <Shield className="w-6 h-6 text-white" />
                </div>
                <div>
                  <h3 className="font-semibold text-slate-800 mb-1 dark:text-slate-100">智能合同管理</h3>
                  <p className="text-sm text-slate-600 leading-relaxed dark:text-slate-300">AI 驱动的合同审查、风险评估与智能起草</p>
                </div>
              </div>
              <div className="flex items-start gap-4 p-4 rounded-xl bg-white/50 border border-slate-100/50 dark:bg-white/5 dark:border-white/10">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-purple-500 to-purple-600 flex items-center justify-center shrink-0 shadow-lg shadow-purple-500/20 dark:from-red-500 dark:to-orange-600 dark:shadow-orange-500/20">
                  <Building2 className="w-6 h-6 text-white" />
                </div>
                <div>
                  <h3 className="font-semibold text-slate-800 mb-1 dark:text-slate-100">企业级服务</h3>
                  <p className="text-sm text-slate-600 leading-relaxed dark:text-slate-300">给中国法律人用的「会说人话、能干活、留得下证据」的 AI 工作台</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* 右侧登录表单 */}
        <div className="w-full max-w-md mx-auto lg:mx-0">
          <Card className="shadow-2xl border-0 bg-white/90 backdrop-blur-md relative overflow-hidden text-slate-800">
            <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-blue-600 via-purple-500 to-purple-600 dark:from-orange-600 dark:via-red-500 dark:to-red-600" />

            <CardHeader className="space-y-3 pb-6 pt-8">
              <div className="lg:hidden flex items-center gap-3 mb-2">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-600 to-purple-600 flex items-center justify-center shadow-lg dark:from-orange-600 dark:to-red-600">
                  <span className="text-white font-bold text-2xl">JY</span>
                </div>
                <h1 className="text-xl font-bold text-slate-800 dark:text-slate-100">JYparalegal</h1>
              </div>

              <CardTitle className="text-2xl font-bold text-center text-slate-800">欢迎回来</CardTitle>
              <CardDescription className="text-base text-center text-slate-500">
                登录您的账户以访问合同工作台
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-5 pb-8">
              <div className="space-y-2">
                <Label htmlFor="username" className="text-sm font-semibold text-slate-700">账号</Label>
                <div className="relative group">
                  <User className="absolute left-3.5 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-400 group-focus-within:text-blue-600 transition-colors" />
                  <Input
                    id="username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    autoComplete="username"
                    className="pl-12 h-12 bg-slate-50 border-slate-200 text-slate-800 focus-visible:ring-blue-500/30"
                    aria-invalid={!!fieldErrors.username}
                    placeholder="请输入您的账号"
                  />
                </div>
                {fieldErrors.username ? (
                  <div className="flex items-center gap-1.5 text-sm text-red-600 mt-1.5">
                    <AlertCircle className="h-4 w-4" />
                    <span>{fieldErrors.username}</span>
                  </div>
                ) : null}
              </div>

              <div className="space-y-2">
                <Label htmlFor="password" className="text-sm font-semibold text-slate-700">密码</Label>
                <div className="relative group">
                  <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-400 group-focus-within:text-blue-600 transition-colors" />
                  <Input
                    id="password"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="current-password"
                    className="pl-12 h-12 bg-slate-50 border-slate-200 text-slate-800 focus-visible:ring-blue-500/30"
                    aria-invalid={!!fieldErrors.password}
                    placeholder="请输入您的密码"
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') void handleSubmit();
                    }}
                  />
                </div>
                {fieldErrors.password ? (
                  <div className="flex items-center gap-1.5 text-sm text-red-600 mt-1.5">
                    <AlertCircle className="h-4 w-4" />
                    <span>{fieldErrors.password}</span>
                  </div>
                ) : null}
              </div>

              <label className="flex items-center gap-2 py-2 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  className="h-4 w-4 rounded border-slate-300 text-blue-600 accent-blue-600"
                />
                <span className="text-sm text-slate-500">记住我（30天内免登录）</span>
              </label>

              {error ? (
                <div className="flex items-center gap-2 rounded-lg bg-red-50 border border-red-200 px-3 py-2.5 text-sm text-red-600">
                  <AlertCircle className="h-4 w-4 shrink-0" />
                  <span>{error}</span>
                </div>
              ) : null}

              <Button
                className="w-full h-12 bg-gradient-to-r from-blue-600 via-blue-500 to-purple-600 hover:opacity-90 text-white font-semibold shadow-lg shadow-blue-500/30 dark:from-orange-600 dark:via-red-500 dark:to-red-600 dark:shadow-red-500/30"
                size="lg"
                onClick={() => void handleSubmit()}
                disabled={submitting}
              >
                {submitting ? (
                  <span className="flex items-center gap-2.5">
                    <div className="h-5 w-5 border-2 border-current border-t-transparent rounded-full animate-spin" />
                    登录中...
                  </span>
                ) : (
                  <span className="flex items-center gap-2.5">
                    <LogIn className="h-5 w-5" />
                    立即登录
                  </span>
                )}
              </Button>

              <div className="text-center text-sm text-slate-500 pt-1">
                还没有账户？
                <button type="button" className="ml-1 font-medium text-blue-600 hover:underline" onClick={() => navigate('/register')}>
                  注册账户
                </button>
              </div>
            </CardContent>
          </Card>

          <div className="mt-8 text-center">
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white/60 backdrop-blur-sm border border-slate-200/50">
              <div className="h-2 w-2 rounded-full bg-green-500 animate-pulse" />
              <span className="text-xs text-slate-500 font-medium">系统运行正常</span>
            </div>
            <div className="mt-4 text-xs text-slate-400">
              <span className="block">© 2026 JYparalegal · 保留所有权利</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
