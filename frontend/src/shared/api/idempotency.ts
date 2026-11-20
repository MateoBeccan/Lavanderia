export function idempotencyRetryAt(
  retryAfter: string | undefined,
  now: number,
) {
  const value = retryAfter?.trim() ?? "";
  const deadline = /^\d+$/.test(value)
    ? now + Number(value) * 1000
    : Date.parse(value);
  return Number.isFinite(deadline) ? Math.max(now, deadline) : now + 1000;
}
