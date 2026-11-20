import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import { comprasApi } from "../api/compras";
import type { CompraFiltros, CompraRequest } from "../types/compras";

const listas = ["compras", "listas"] as const;
const ficha = (id: string) => ["compras", "ficha", id] as const;
const detalle = (id: string) => [...ficha(id), "detalle"] as const;

export const comprasKeys = { listas, ficha };

export function useCompras(filtros: CompraFiltros, page: number) {
  const { api } = useSession();
  const params = { ...filtros, page, size: 20 };
  return useQuery({
    queryKey: [...listas, params],
    queryFn: ({ signal }) => comprasApi.listar(api, params, signal),
  });
}

export function useCompra(id: string) {
  const { api } = useSession();
  return useQuery({
    queryKey: detalle(id),
    queryFn: ({ signal }) => comprasApi.obtener(api, id, signal),
  });
}

export function useSaldoCompra(id: string) {
  const { api } = useSession();
  return useQuery({
    queryKey: [...ficha(id), "saldo"],
    queryFn: ({ signal }) => comprasApi.saldo(api, id, signal),
    staleTime: 0,
  });
}

export function usePagosAsociados(id: string, page: number) {
  const { api } = useSession();
  return useQuery({
    queryKey: [...ficha(id), "pagos", page],
    queryFn: ({ signal }) => comprasApi.pagos(api, id, page, signal),
  });
}

export function useEstadosPago() {
  const { api } = useSession();
  return useQuery({
    queryKey: ["estados-pago"],
    queryFn: ({ signal }) => comprasApi.estados(api, signal),
  });
}

export function useGuardarCompra(id?: number) {
  const { api } = useSession();
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (body: CompraRequest) =>
      comprasApi.guardar(api, id, body, AbortSignal.timeout(20_000)),
    retry: false,
    onSuccess: async (compra) => {
      const compraId = String(compra.id);
      await cache.cancelQueries({ queryKey: ficha(compraId) });
      await cache.invalidateQueries({
        queryKey: ficha(compraId),
        refetchType: "none",
      });
      cache.setQueryData(detalle(compraId), compra);
      void cache.invalidateQueries({ queryKey: listas });
    },
    onError: () => {
      void cache.invalidateQueries({ queryKey: listas });
      if (id !== undefined)
        void cache.invalidateQueries({ queryKey: ficha(String(id)) });
    },
  });
}
