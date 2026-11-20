import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/shared/lib/utils";

export function StatCard({
  title,
  icon: Icon,
  children,
  footnote,
  className,
}: {
  title: string;
  icon: LucideIcon;
  children: ReactNode;
  footnote?: string;
  className?: string;
}) {
  return (
    <article className={cn("stat-card surface", className)}>
      <span className="icon-tile">
        <Icon size={18} aria-hidden="true" />
      </span>
      <div className="min-w-0">
        <h3 className="text-xs font-semibold text-muted-foreground">{title}</h3>
        <div className="stat-value">{children}</div>
        {footnote && (
          <p className="text-xs text-muted-foreground">{footnote}</p>
        )}
      </div>
    </article>
  );
}
