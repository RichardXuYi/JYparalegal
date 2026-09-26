import { useNavigate } from 'react-router-dom';
import {
  FileText,
  LayoutDashboard,
  ScrollText,
  Shield,
  Building2,
  FileDiff,
  Scale,
  MessageSquare,
  Briefcase,
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface FeatureItem {
  route: string;
  label: string;
  description: string;
  icon: React.ComponentType<{ className?: string; strokeWidth?: number }>;
  color: string;
}

const FEATURES: FeatureItem[] = [
  {
    route: '/',
    label: 'Agent',
    description: 'AI 智能对话助手',
    icon: MessageSquare,
    color: 'from-blue-500 to-cyan-500',
  },
  {
    route: '/signing',
    label: '签署',
    description: '合同签署与管理',
    icon: FileText,
    color: 'from-purple-500 to-pink-500',
  },
  {
    route: '/overview',
    label: '工作台',
    description: '业务数据总览',
    icon: LayoutDashboard,
    color: 'from-green-500 to-emerald-500',
  },
  {
    route: '/templates',
    label: '模板',
    description: '合同模板库',
    icon: ScrollText,
    color: 'from-orange-500 to-amber-500',
  },
  {
    route: '/evidence',
    label: '证据',
    description: '电子存证管理',
    icon: Shield,
    color: 'from-red-500 to-rose-500',
  },
  {
    route: '/company',
    label: '企业管理',
    description: '组织与成员管理',
    icon: Building2,
    color: 'from-indigo-500 to-violet-500',
  },
  {
    route: '/compare',
    label: '文档对比',
    description: '合同版本对比',
    icon: FileDiff,
    color: 'from-teal-500 to-cyan-500',
  },
  {
    route: '/moot',
    label: '模拟法庭',
    description: '庭审演练（即将开发）',
    icon: Scale,
    color: 'from-fuchsia-500 to-purple-500',
  },
];

export default function Features() {
  const navigate = useNavigate();

  return (
    <div className="h-full overflow-y-auto p-6">
      <div className="mx-auto max-w-4xl">
        <div className="mb-8 text-center">
          <div className="mb-3 inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-purple-600 shadow-lg">
            <Briefcase className="h-7 w-7 text-white" strokeWidth={1.75} />
          </div>
          <h1 className="text-2xl font-bold text-foreground">全部功能</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            探索 JYparalegal 的全部能力
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
          {FEATURES.map((feature) => {
            const Icon = feature.icon;
            return (
              <button
                key={feature.route}
                type="button"
                onClick={() => navigate(feature.route)}
                className={cn(
                  'group relative flex flex-col items-center rounded-2xl border border-border/50 bg-card p-5 text-center transition-all',
                  'hover:-translate-y-1 hover:shadow-lg hover:border-border',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
                )}
              >
                <div
                  className={cn(
                    'mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br shadow-md transition-transform group-hover:scale-110',
                    feature.color,
                  )}
                >
                  <Icon className="h-6 w-6 text-white" strokeWidth={1.75} />
                </div>
                <h3 className="text-sm font-semibold text-foreground">{feature.label}</h3>
                <p className="mt-1 text-xs text-muted-foreground">{feature.description}</p>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
