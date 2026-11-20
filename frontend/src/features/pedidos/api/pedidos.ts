import type { ApiClient, PagedResponse } from "@/shared/api/http";
import type {
  EstadoDisponible,
  EventoPedido,
  FiltrosPedido,
  Pedido,
  PedidoResumen,
  SaldoPedido,
} from "../types/pedidos";
import type { PedidoRequest } from "../schemas/pedido";

const tiempoMaximoEnvio = 30_000;

export const pedidosApi = {
  listar: (api: ApiClient, params: FiltrosPedido, signal: AbortSignal) =>
    api<PagedResponse<PedidoResumen>>("/pedidos", { params, signal }),
  detalle: (api: ApiClient, id: string, signal: AbortSignal) =>
    api<Pedido>(`/pedidos/${id}`, { signal }),
  estados: (api: ApiClient, signal: AbortSignal) =>
    api<EstadoDisponible[]>("/estados-pedido", { signal }),
  historial: (api: ApiClient, id: string, signal: AbortSignal) =>
    api<EventoPedido[]>(`/pedidos/${id}/historial`, { signal }),
  saldo: (api: ApiClient, id: string, signal: AbortSignal) =>
    api<SaldoPedido>(`/pedidos/${id}/saldo`, { signal }),
  tieneCobros: async (api: ApiClient, id: string, signal: AbortSignal) =>
    (
      await api<PagedResponse<unknown>>(`/pedidos/${id}/cobros`, {
        params: { size: 1 },
        signal,
      })
    ).page.totalElements > 0,
  guardar: (api: ApiClient, body: PedidoRequest, id?: number) =>
    api<Pedido>(id === undefined ? "/pedidos" : `/pedidos/${id}`, {
      method: id === undefined ? "POST" : "PUT",
      body,
      signal: AbortSignal.timeout(tiempoMaximoEnvio),
    }),
  cambiarEstado: (
    api: ApiClient,
    id: number,
    codigoEstado: string,
    observacion: string,
  ) =>
    codigoEstado === "CANCELADO"
      ? api<Pedido>(`/pedidos/${id}/cancelar`, {
          method: "PATCH",
          body: { observacion },
          signal: AbortSignal.timeout(tiempoMaximoEnvio),
        })
      : api<Pedido>(`/pedidos/${id}/estado`, {
          method: "PATCH",
          body: { codigoEstado, observacion },
          signal: AbortSignal.timeout(tiempoMaximoEnvio),
        }),
};
