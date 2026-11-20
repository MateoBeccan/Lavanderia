import { ApiError } from "@/shared/api/http";

export function errorCatalogo(error: unknown) {
  return error instanceof ApiError
    ? error.message
    : "Ocurrió un error inesperado. Intentá nuevamente.";
}
