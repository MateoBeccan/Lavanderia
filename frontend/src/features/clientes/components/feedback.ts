import { ApiError } from "@/shared/api/http";

export function clienteError(error: unknown) {
  if (!(error instanceof ApiError))
    return "Ocurrió un error inesperado. Intentá nuevamente.";
  if (error.status >= 500)
    return "No pudimos completar la operación. El servidor no está disponible temporalmente.";
  if (error.status === 403)
    return "No tenés permiso para realizar esta operación.";
  if (error.status === 404) return "El cliente no existe o está desactivado.";
  return error.message;
}
