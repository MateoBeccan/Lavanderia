import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
export { useMetodosPago as useMetodosCobro } from "@/features/caja/hooks/queries";
import { cobrosApi } from "../api/cobros";

export const cobrosKeys = {
  listas: (id: string) => ["cobros", "pedido", id] as const,
  detalle: (id: string) => ["cobros", "detalle", id] as const,
};

export function useCobros(id: string, page: number) {
  const { api } = useSession();
  return useQuery({
    queryKey: [...cobrosKeys.listas(id), page],
    queryFn: ({ signal }) => cobrosApi.listar(api, id, page, signal),
  });
}

export function useCobro(id: string) {
  const { api } = useSession();
  return useQuery({
    queryKey: cobrosKeys.detalle(id),
    queryFn: ({ signal }) => cobrosApi.detalle(api, id, signal),
  });
}
