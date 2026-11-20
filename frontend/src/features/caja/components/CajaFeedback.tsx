import { ApiError } from "@/shared/api/http";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";

export function CajaFeedback({
  query,
}: {
  query: {
    isPending: boolean;
    error: Error | null;
    refetch: () => Promise<unknown>;
  };
}) {
  if (query.isPending) return <LoadingState />;
  if (!query.error) return null;
  const status =
    query.error instanceof ApiError ? query.error.status : undefined;
  return (
    <ErrorState
      message={query.error.message}
      onRetry={
        status === 401 || status === 403 || status === 404
          ? undefined
          : () => {
              void query.refetch();
            }
      }
    />
  );
}
