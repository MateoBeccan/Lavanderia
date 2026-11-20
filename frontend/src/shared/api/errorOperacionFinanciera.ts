import { ApiError } from "@/shared/api/http";
import { idempotencyRetryAt } from "@/shared/api/idempotency";

export function errorOperacionFinanciera(
  error: unknown,
  reintento: boolean,
  ahora = Date.now(),
) {
  if (
    error instanceof ApiError &&
    error.status === 409 &&
    error.code === "IDEMPOTENCIA_CLAVE_REUTILIZADA"
  ) {
    return {
      estado: "VERIFICAR",
      mensaje:
        "La referencia del envío ya corresponde a otra operación. Revisá el historial.",
    } as const;
  }
  if (
    error instanceof ApiError &&
    error.status === 409 &&
    (error.code === "IDEMPOTENCIA_EN_CURSO" ||
      (error.code === undefined && error.retryAfter !== undefined))
  ) {
    return {
      estado: "PENDIENTE",
      retryAt: idempotencyRetryAt(error.retryAfter, ahora),
      mensaje:
        "Spring todavía está procesando este envío. Esperá el plazo indicado antes de reintentar.",
    } as const;
  }
  if (
    !(error instanceof ApiError) ||
    error.status < 400 ||
    error.status === 408 ||
    error.status >= 500 ||
    (reintento && [400, 401, 403, 429].includes(error.status))
  ) {
    return {
      estado: "PENDIENTE",
      retryAt: 0,
      mensaje:
        "Resultado pendiente de confirmar. Reintentá con el mismo envío. Si venció la sesión, ingresá con la misma cuenta.",
    } as const;
  }
  return {
    estado: "ERROR",
    mensaje: [
      error.message,
      ...error.fields.map(({ mensaje }) => mensaje),
    ].join(" "),
  } as const;
}
