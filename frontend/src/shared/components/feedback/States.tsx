import { CircleAlert, Inbox, LoaderCircle } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "../ui/button";

export function LoadingState({ label = "Cargando…" }: { label?: string }) {
  return (
    <p
      role="status"
      className="flex items-center gap-2 text-sm text-muted-foreground"
    >
      <LoaderCircle
        size={17}
        className="motion-safe:animate-spin"
        aria-hidden="true"
      />
      {label}
    </p>
  );
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <p
        role="alert"
        className="flex items-center gap-2 text-sm text-destructive"
      >
        <CircleAlert size={17} className="shrink-0" aria-hidden="true" />
        {message}
      </p>
      {onRetry && (
        <Button variant="outline" onClick={onRetry}>
          Reintentar
        </Button>
      )}
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <section className="surface flex flex-col items-center gap-3 p-8 text-center sm:p-14">
      <span className="icon-tile">
        <Inbox size={21} aria-hidden="true" />
      </span>
      <h2 className="text-lg font-semibold">{title}</h2>
      <p className="text-sm text-muted-foreground">{description}</p>
      {action}
    </section>
  );
}
