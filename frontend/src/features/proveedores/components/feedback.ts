import { ApiError } from "@/shared/api/http";

export function proveedorError(error: unknown) {
  if (!(error instanceof ApiError))
    return "Ocurrió un error inesperado. Intentá nuevamente.";
  if (error.status === 403)
    return "No tenés permiso para realizar esta operación.";
  if (error.status === 404) return "El proveedor no existe o está desactivado.";
  return error.message;
}
