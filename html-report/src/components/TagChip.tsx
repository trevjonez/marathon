import type { HtmlTag } from '../types/report';

/** `group value` pill. Attributes (description, ticket) go in the tooltip and, when `detailed`, inline. */
export function TagChip({ tag, detailed = false }: { tag: HtmlTag; detailed?: boolean }) {
  const attributes = Object.entries(tag.attributes ?? {});
  return (
    <span
      className="tag-chip inline-flex items-center gap-1 rounded border border-surface-border bg-surface-alt px-1.5 py-0.5 text-[10px]"
      title={attributes.map(([k, v]) => `${k}: ${v}`).join('\n') || undefined}
    >
      <span className="uppercase tracking-wide text-slate-500 dark:text-slate-400">{tag.group}</span>
      <span className="font-medium">{tag.value}</span>
      {detailed &&
        attributes.map(([k, v]) => (
          <span key={k} className="text-slate-500 dark:text-slate-400">
            · {v}
          </span>
        ))}
    </span>
  );
}
