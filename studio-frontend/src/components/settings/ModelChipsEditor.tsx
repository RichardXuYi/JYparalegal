/**
 * ModelChipsEditor
 * A scrollable chip/tag editor for managing a comma-separated list of model ids.
 * The comma-separated string remains the single source of truth so the existing
 * save pipeline (which splits on commas) stays unchanged.
 */
import React, { useState } from 'react';
import { X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { parseModelIdList } from '@/lib/providers';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';

interface ModelChipsEditorProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
}

export function ModelChipsEditor({ value, onChange, placeholder, className }: ModelChipsEditorProps) {
  const { t } = useTranslation('settings');
  const [draft, setDraft] = useState('');
  const models = parseModelIdList(value);

  const commit = (raw: string) => {
    const incoming = parseModelIdList(raw);
    if (incoming.length === 0) {
      setDraft('');
      return;
    }
    const merged = [...models];
    let skipped = false;
    for (const model of incoming) {
      if (merged.includes(model)) {
        skipped = true;
        continue;
      }
      merged.push(model);
    }
    if (skipped) {
      toast.info(t('aiProviders.toast.duplicateModelsSkipped', '已跳过重复模型'));
    }
    onChange(merged.join(','));
    setDraft('');
  };

  const removeModel = (model: string) => {
    onChange(models.filter((m) => m !== model).join(','));
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      commit(draft);
    } else if (event.key === 'Backspace' && draft.length === 0 && models.length > 0) {
      event.preventDefault();
      removeModel(models[models.length - 1]);
    }
  };

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const next = event.target.value;
    if (/[，,\n]/.test(next)) {
      commit(next);
    } else {
      setDraft(next);
    }
  };

  return (
    <div
      className={cn(
        'flex flex-wrap items-center gap-1.5 max-h-32 overflow-y-auto rounded-xl border border-black/10 dark:border-white/10 bg-transparent p-2 shadow-sm transition-all focus-within:ring-2 focus-within:ring-blue-500/50 focus-within:border-blue-500',
        className,
      )}
    >
      {models.map((model) => (
        <Badge
          key={model}
          variant="secondary"
          className="gap-1 font-mono text-xs font-normal pr-1"
        >
          {model}
          <button
            type="button"
            aria-label={`remove ${model}`}
            onClick={() => removeModel(model)}
            className="rounded-full p-0.5 hover:bg-black/10 dark:hover:bg-white/10"
          >
            <X className="h-3 w-3" />
          </button>
        </Badge>
      ))}
      <input
        value={draft}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        onBlur={() => commit(draft)}
        placeholder={models.length === 0 ? placeholder : undefined}
        className="flex-1 min-w-[8ch] bg-transparent font-mono text-meta text-foreground placeholder:text-foreground/40 outline-none border-0 focus:ring-0"
      />
    </div>
  );
}
