import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Toaster } from "sonner";
import { SessionProvider } from "@/features/auth/session";

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 30_000, retry: false } },
});

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <SessionProvider>{children}</SessionProvider>
      <Toaster
        position="bottom-right"
        richColors
        toastOptions={{
          closeButton: true,
          closeButtonAriaLabel: "Cerrar notificación",
        }}
      />
    </QueryClientProvider>
  );
}
