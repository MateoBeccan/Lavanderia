import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import { clientesApi } from "../api/clientes";
import type { ClienteRequest, ParametrosClientes } from "../types/clientes";

const listas = ["clientes", "listas"] as const;
export const clientesKeys = {
  listas,
  lista: (params: ParametrosClientes) => [...listas, params] as const,
  totalActivos: [...listas, "total-activos"] as const,
  detalle: (id: string) => ["clientes", "detalle", id] as const,
};

export function useClientes(params: ParametrosClientes) {
  const { api } = useSession();
  return useQuery({
    queryKey: clientesKeys.lista(params),
    queryFn: ({ signal }) => clientesApi.listar(api, params, signal),
  });
}

export function useCliente(id: string) {
  const { api } = useSession();
  return useQuery({
    queryKey: clientesKeys.detalle(id),
    queryFn: ({ signal }) => clientesApi.obtener(api, id, signal),
  });
}

export function useGuardarCliente(id?: string) {
  const { api } = useSession();
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (body: ClienteRequest) =>
      id === undefined
        ? clientesApi.crear(api, body)
        : clientesApi.editar(api, id, body),
    onSuccess: async (cliente) => {
      const queryKey = clientesKeys.detalle(String(cliente.id));
      await cache.cancelQueries({ queryKey, exact: true });
      cache.setQueryData(queryKey, cliente);
      await cache.invalidateQueries({ queryKey: clientesKeys.listas });
    },
  });
}

export function useDesactivarCliente() {
  const { api } = useSession();
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => clientesApi.desactivar(api, id),
    onSuccess: async (_, id) => {
      cache.removeQueries({ queryKey: clientesKeys.detalle(id), exact: true });
      await cache.invalidateQueries({ queryKey: clientesKeys.listas });
    },
  });
}
