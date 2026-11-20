import { ApiError } from "@/shared/api/http";
import type { TipoOperacion } from "../types/comprobantes";

export const nombreTipo = (tipo: TipoOperacion) =>
  tipo === "COBRO" ? "Cobro" : "Pago a proveedor";

export function requiereConsulta(error: unknown) {
  return (
    !(error instanceof ApiError) ||
    error.status === 0 ||
    error.status === 408 ||
    error.status === 409 ||
    (error.status >= 200 && error.status < 300) ||
    error.status >= 500
  );
}
