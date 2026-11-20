import { ApiError } from "@/shared/api/http";
import { idempotencyRetryAt } from "@/shared/api/idempotency";
import { resultadoIncierto } from "@/features/pedidos/rules";
import type { Cobro, IntentoCobro } from "../types/cobros";

export type OperacionCobro =
  | { estado: "BORRADOR" }
  | { estado: "ENVIANDO"; intento: IntentoCobro }
  | { estado: "CONFIRMADO"; cobro: Cobro }
  | { estado: "ERROR_VALIDADO"; pedidoId: string; mensaje: string }
  | {
      estado: "RESULTADO_INCIERTO";
      mensaje: string;
      intento: IntentoCobro;
    }
  | {
      estado: "CONFLICTO";
      intento: IntentoCobro;
      mensaje: string;
      retryAt: number | null;
    }
  | { estado: "VERIFICAR"; pedidoId: string };

export function resolverError(
  error: unknown,
  intento: IntentoCobro,
  reintento: boolean,
  ahora = Date.now(),
): OperacionCobro {
  if (error instanceof ApiError && error.status === 409) {
    if (error.code === "IDEMPOTENCIA_CLAVE_REUTILIZADA") {
      return {
        estado: "CONFLICTO",
        intento,
        retryAt: null,
        mensaje:
          "La referencia del envío ya está asociada a otra operación. Verificá el historial del pedido.",
      };
    }
    if (
      error.code === "IDEMPOTENCIA_EN_CURSO" ||
      (error.code === undefined && error.retryAfter !== undefined)
    ) {
      return {
        estado: "CONFLICTO",
        intento,
        retryAt: idempotencyRetryAt(error.retryAfter, ahora),
        mensaje:
          "El servidor todavía está procesando el cobro. Esperá antes de volver a consultar con el mismo envío.",
      };
    }
  }
  const accesoOValidacion =
    error instanceof ApiError && [400, 401, 403].includes(error.status);
  if (
    resultadoIncierto(error) ||
    (reintento &&
      (accesoOValidacion ||
        (error instanceof ApiError && error.status === 429)))
  ) {
    return {
      estado: "RESULTADO_INCIERTO",
      intento,
      mensaje: accesoOValidacion
        ? (error as ApiError).message +
          " El resultado del intento anterior sigue pendiente."
        : "Resultado pendiente de confirmar. Revisá el historial o reintentá con el mismo envío.",
    };
  }
  return {
    estado: "ERROR_VALIDADO",
    pedidoId: intento.pedidoId,
    mensaje:
      error instanceof ApiError
        ? [error.message, ...error.fields.map(({ mensaje }) => mensaje)].join(
            " ",
          )
        : "No se pudo registrar el cobro.",
  };
}
