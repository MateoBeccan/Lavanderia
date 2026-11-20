import type { ApiClient, PagedResponse } from "@/shared/api/http";
import type {
  Compra,
  CompraFiltros,
  CompraRequest,
  CompraResumen,
  EstadoPago,
  PagoAsociado,
  SaldoCompra,
} from "../types/compras";

const path = "/compras";

export const comprasApi = {
  listar: (
    api: ApiClient,
    params: CompraFiltros & { page: number; size: number },
    signal: AbortSignal,
  ) => api<PagedResponse<CompraResumen>>(path, { params, signal }),
  obtener: (api: ApiClient, id: string, signal: AbortSignal) =>
    api<Compra>(`${path}/${id}`, { signal }),
  saldo: (api: ApiClient, id: string, signal: AbortSignal) =>
    api<SaldoCompra>(`${path}/${id}/saldo`, { signal }),
  pagos: (api: ApiClient, id: string, page: number, signal: AbortSignal) =>
    api<PagedResponse<PagoAsociado>>(`${path}/${id}/pagos`, {
      params: { page, size: 10 },
      signal,
    }),
  estados: (api: ApiClient, signal: AbortSignal) =>
    api<EstadoPago[]>("/estados-pago", { signal }),
  guardar: (
    api: ApiClient,
    id: number | undefined,
    body: CompraRequest,
    signal: AbortSignal,
  ) =>
    api<Compra>(id === undefined ? path : `${path}/${id}`, {
      method: id === undefined ? "POST" : "PUT",
      body,
      signal,
      expectedStatus: id === undefined ? 201 : 200,
    }),
};
