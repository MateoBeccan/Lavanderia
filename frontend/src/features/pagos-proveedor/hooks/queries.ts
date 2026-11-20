import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import { pagosApi } from "../api/pagos";

export function usePago(id: string) {
  const { api } = useSession();
  return useQuery({
    queryKey: ["pagos-proveedor", "detalle", id],
    queryFn: ({ signal }) => pagosApi.detalle(api, id, signal),
  });
}
