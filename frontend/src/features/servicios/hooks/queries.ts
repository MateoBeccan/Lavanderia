import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import type { PaginaCatalogo } from "@/shared/catalogos/api";
import { opcionesDisponibles } from "@/shared/catalogos/queries";
import { serviciosApi } from "../api/servicios";

export function useServiciosDisponibles(params: PaginaCatalogo) {
  const { api } = useSession();
  return useQuery(opcionesDisponibles(serviciosApi, api, params));
}
