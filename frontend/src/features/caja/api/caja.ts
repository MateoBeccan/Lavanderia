import { ApiError } from "@/shared/api/http";
import type { ApiClient, PagedResponse } from "@/shared/api/http";
import type {
  Caja,
  CajaResumen,
  SaldoCaja,
  MovimientoCaja,
  OpcionCaja,
  FiltrosMovimientos,
} from "../types/caja";

export const cajaApi = {
  async abierta(api: ApiClient, signal: AbortSignal) {
    try {
      return await api<Caja>("/cajas/abierta", { signal });
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return null;
      throw error;
    }
  },
  saldo: (api: ApiClient, id: string, signal: AbortSignal) =>
    api<SaldoCaja>("/cajas/" + id + "/saldo", { signal }),
  historial: (api: ApiClient, page: number, signal: AbortSignal) =>
    api<PagedResponse<CajaResumen>>("/cajas", {
      signal,
      params: { page, size: 20 },
    }),
  movimientos: (
    api: ApiClient,
    id: string,
    page: number,
    filtros: FiltrosMovimientos,
    signal: AbortSignal,
  ) =>
    api<PagedResponse<MovimientoCaja>>("/cajas/" + id + "/movimientos", {
      signal,
      params: { page, size: 20, ...filtros },
    }),
  movimiento: (api: ApiClient, id: string, signal: AbortSignal) =>
    api<MovimientoCaja>("/movimientos-caja/" + id, { signal }),
  tipos: (api: ApiClient, signal: AbortSignal) =>
    api<OpcionCaja[]>("/tipos-movimiento-caja", { signal }),
  metodos: (api: ApiClient, signal: AbortSignal) =>
    api<OpcionCaja[]>("/metodos-pago", { signal }),
  cerrar: (api: ApiClient, id: string, efectivoContado: number) =>
    api<Caja>("/cajas/" + id + "/cerrar", {
      method: "PATCH",
      body: { efectivoContado },
      signal: AbortSignal.timeout(15_000),
    }),
};
