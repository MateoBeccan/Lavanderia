import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import type { Pagina } from "@/features/insumos/types/insumos";
import { ApiError, type ApiClient } from "@/shared/api/http";
import { insumosSuministradosApi } from "../api/insumosSuministrados";
import type { InsumoSuministrado } from "../types/insumosSuministrados";

export const claveInsumosSuministrados = ["insumos-suministrados"] as const;
const proveedor = (id: string) => [...claveInsumosSuministrados, id] as const;

export function opcionesInsumosSuministrados(
  api: ApiClient,
  id: string,
  params: Pagina,
) {
  return queryOptions({
    queryKey: [...proveedor(id), "lista", params],
    queryFn: ({ signal }) =>
      insumosSuministradosApi.listar(api, id, params, signal),
  });
}

export function opcionesIdsInsumosSuministrados(api: ApiClient, id: string) {
  return queryOptions({
    queryKey: [...proveedor(id), "ids"],
    queryFn: async ({ signal }) => {
      const ids: number[] = [];
      let page = 0;
      let totalPages: number;
      do {
        const resultado = await insumosSuministradosApi.listar(
          api,
          id,
          { page, size: 100 },
          signal,
        );
        ids.push(...resultado.content.map(({ insumoId }) => insumoId));
        totalPages = resultado.page.totalPages;
        page++;
      } while (page < totalPages);
      return ids;
    },
  });
}

export function useIdsInsumosSuministrados(id: string) {
  const { api } = useSession();
  return useQuery(opcionesIdsInsumosSuministrados(api, id));
}

export function useCambiarInsumoSuministrado(id: string) {
  const { api } = useSession();
  const cache = useQueryClient();
  return useMutation({
    mutationFn: ({
      tipo,
      insumoId,
    }: {
      tipo: "asociar" | "desasociar";
      insumoId: number;
    }): Promise<InsumoSuministrado | void> =>
      tipo === "asociar"
        ? insumosSuministradosApi.asociar(api, id, { insumoId })
        : insumosSuministradosApi.desasociar(api, id, insumoId),
    onSuccess: () => cache.invalidateQueries({ queryKey: proveedor(id) }),
    onError: (error) => {
      if (error instanceof ApiError && [404, 409].includes(error.status)) {
        return cache.invalidateQueries({ queryKey: proveedor(id) });
      }
    },
  });
}
