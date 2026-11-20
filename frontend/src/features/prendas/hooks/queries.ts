import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import type { PaginaCatalogo } from "@/shared/catalogos/api";
import { opcionesDisponibles } from "@/shared/catalogos/queries";
import { prendasApi } from "../api/prendas";

export function usePrendasDisponibles(params: PaginaCatalogo) {
  const { api } = useSession();
  return useQuery(opcionesDisponibles(prendasApi, api, params));
}
