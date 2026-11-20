import { useQuery } from "@tanstack/react-query";
import { useCajaAbierta } from "@/features/caja/hooks/queries";
import type { PagedResponse } from "@/shared/api/http";
import { useSession } from "@/features/auth/session";

import { clientesApi } from "@/features/clientes/api/clientes";
import { clientesKeys } from "@/features/clientes/hooks/queries";
import { pedidosKeys } from "@/features/pedidos/hooks/queries";
import type { PedidoResumen } from "@/features/pedidos/types/pedidos";
export const queryKeys = {
  pedidos: { total: pedidosKeys.total },
};

export function useDashboardQueries() {
  const { api } = useSession();
  const clientes = useQuery({
    queryKey: clientesKeys.totalActivos,
    queryFn: async ({ signal }) =>
      (
        await clientesApi.listar(
          api,
          { page: 0, size: 1, sort: ["id,asc"] },
          signal,
        )
      ).page.totalElements,
  });
  const pedidos = useQuery({
    queryKey: queryKeys.pedidos.total,
    queryFn: async ({ signal }) =>
      (
        await api<PagedResponse<PedidoResumen>>("/pedidos", {
          signal,
          params: { size: 1 },
        })
      ).page.totalElements,
  });
  const caja = useCajaAbierta();
  return { clientes, pedidos, caja };
}
