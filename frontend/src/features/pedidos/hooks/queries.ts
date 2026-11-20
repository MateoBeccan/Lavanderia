import { ApiError } from "@/shared/api/http";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import { pedidosApi } from "../api/pedidos";
import type { PedidoRequest } from "../schemas/pedido";
import type { FiltrosPedido, Pedido } from "../types/pedidos";

const listas = ["pedidos", "listas"] as const;
const total = ["pedidos", "total"] as const;
const recurso = (tipo: string, id: string) => ["pedidos", tipo, id] as const;
export const pedidosKeys = { total };

export function usePedidos(params: FiltrosPedido) {
  const { api } = useSession();
  return useQuery({
    queryKey: [...listas, params],
    queryFn: ({ signal }) => pedidosApi.listar(api, params, signal),
  });
}

export function useEstadosPedido() {
  const { api } = useSession();
  return useQuery({
    queryKey: ["pedidos", "estados"],
    queryFn: ({ signal }) => pedidosApi.estados(api, signal),
  });
}

export function usePedido(id: string) {
  const { api } = useSession();
  return useQuery({
    queryKey: recurso("detalle", id),
    queryFn: ({ signal }) => pedidosApi.detalle(api, id, signal),
  });
}

export function useHistorialPedido(id: string) {
  const { api } = useSession();
  return useQuery({
    queryKey: recurso("historial", id),
    queryFn: ({ signal }) => pedidosApi.historial(api, id, signal),
  });
}

export function useSaldoPedido(id: string) {
  const { api } = useSession();
  return useQuery({
    queryKey: recurso("saldo", id),
    queryFn: ({ signal }) => pedidosApi.saldo(api, id, signal),
  });
}

export function useTieneCobros(id: string, enabled: boolean) {
  const { api } = useSession();
  return useQuery({
    queryKey: recurso("tiene-cobros", id),
    queryFn: ({ signal }) => pedidosApi.tieneCobros(api, id, signal),
    enabled,
  });
}

type Operacion =
  | { body: PedidoRequest; id?: number }
  | { id: number; codigo: string; observacion: string };

export function useModificarPedido() {
  const { api } = useSession();
  const cache = useQueryClient();
  const refrescar = async (id: number) => {
    const pedido = String(id);
    await Promise.all([
      cache.invalidateQueries({ queryKey: recurso("detalle", pedido) }),
      cache.invalidateQueries({ queryKey: recurso("historial", pedido) }),
      cache.invalidateQueries({ queryKey: recurso("saldo", pedido) }),
      cache.invalidateQueries({ queryKey: recurso("tiene-cobros", pedido) }),
      cache.invalidateQueries({ queryKey: listas }),
      cache.invalidateQueries({ queryKey: total }),
      cache.invalidateQueries({ queryKey: ["pedidos", "estados"] }),
    ]);
  };
  return useMutation({
    retry: false,
    mutationFn: (op: Operacion) =>
      "body" in op
        ? pedidosApi.guardar(api, op.body, op.id)
        : pedidosApi.cambiarEstado(api, op.id, op.codigo, op.observacion),
    onSuccess: async (pedido: Pedido, op) => {
      const id = String(pedido.id);
      const key = recurso("detalle", id);
      await cache.cancelQueries({ queryKey: key, exact: true });
      cache.setQueryData(key, pedido);
      await Promise.all([
        cache.invalidateQueries({ queryKey: recurso("historial", id) }),
        cache.invalidateQueries({ queryKey: recurso("saldo", id) }),
        cache.invalidateQueries({ queryKey: recurso("tiene-cobros", id) }),
        cache.invalidateQueries({ queryKey: listas }),
        ...(op.id === undefined
          ? [cache.invalidateQueries({ queryKey: total })]
          : []),
      ]);
    },
    onError: (error, op) => {
      if (
        op.id !== undefined &&
        error instanceof ApiError &&
        error.status === 409
      )
        void refrescar(op.id);
    },
  });
}
