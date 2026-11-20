import { queryOptions, useQuery } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import { cajaApi } from "../api/caja";
import type { ApiClient } from "@/shared/api/http";

export const cajaKeys = {
  all: ["caja"] as const,
  abierta: ["caja", "abierta"] as const,
  saldo: (id: string | undefined) => ["caja", "saldo", id] as const,
  historial: (page: number) => ["caja", "historial", page] as const,
  movimientos: (id: string) => ["caja", "movimientos", id] as const,
  movimiento: (id: string) => ["caja", "movimiento", id] as const,
  tipos: ["caja", "tipos"] as const,
  metodos: ["caja", "metodos"] as const,
};

export const saldoOptions = (api: ApiClient, id?: string) =>
  queryOptions({
    queryKey: cajaKeys.saldo(id),
    enabled: id !== undefined,
    queryFn: ({ signal }) => cajaApi.saldo(api, id!, signal),
  });

export function useCajaAbierta() {
  const { api } = useSession();
  return useQuery({
    queryKey: cajaKeys.abierta,
    queryFn: ({ signal }) => cajaApi.abierta(api, signal),
  });
}

export function useCatalogosCaja() {
  const { api } = useSession();
  const tipos = useQuery({
    queryKey: cajaKeys.tipos,
    queryFn: ({ signal }) => cajaApi.tipos(api, signal),
  });
  const metodos = useQuery({
    queryKey: cajaKeys.metodos,
    queryFn: ({ signal }) => cajaApi.metodos(api, signal),
  });
  return { tipos, metodos };
}

export function useMetodosPago() {
  const { api } = useSession();
  return useQuery({
    queryKey: cajaKeys.metodos,
    queryFn: ({ signal }) => cajaApi.metodos(api, signal),
  });
}
