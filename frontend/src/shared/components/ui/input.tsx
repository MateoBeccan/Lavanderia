import type { ComponentProps } from "react";
import { cn } from "@/shared/lib/utils";

export function Input({ className, ...props }: ComponentProps<"input">) {
  return (
    <input
      data-slot="input"
      className={cn(
        "min-h-11 w-full rounded-md border border-border bg-white px-3 text-base placeholder:text-muted-foreground aria-invalid:border-destructive disabled:opacity-60",
        className,
      )}
      {...props}
    />
  );
}
