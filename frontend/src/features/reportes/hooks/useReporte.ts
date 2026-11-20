import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import { consultarReporte } from "../api/reportes";
import type { Filtros, TipoReporte } from "../types/reportes";

export function useReporte<T extends TipoReporte>(
  tipo: T,
  filtros: Filtros,
  paginacion: { page: number; size: number },
  filtrosValidos: boolean,
) {
  const { api, user } = useSession();
  const params = { ...filtros, ...paginacion };
  return useQuery({
    queryKey: ["reportes", tipo, params],
    queryFn: ({ signal }) => consultarReporte(api, tipo, params, signal),
    enabled: filtrosValidos && user?.rol === "ADMINISTRADOR",
    staleTime: 0,
    refetchOnWindowFocus: false,
  });
}
