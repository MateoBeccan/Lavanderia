import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import { proveedoresApi } from "../api/proveedores";
import { claveInsumosSuministrados } from "./insumosSuministrados";
import type {
  ParametrosProveedores,
  ProveedorRequest,
  ProveedorResponse,
} from "../types/proveedores";

const listas = ["proveedores", "listas"] as const;
const detalle = (id: string) => ["proveedores", "detalle", id] as const;

export function useProveedores(params: ParametrosProveedores) {
  const { api } = useSession();
  return useQuery({
    queryKey: [...listas, params],
    queryFn: ({ signal }) => proveedoresApi.listar(api, params, signal),
  });
}

export function useProveedor(id: string) {
  const { api } = useSession();
  return useQuery({
    queryKey: detalle(id),
    queryFn: ({ signal }) => proveedoresApi.obtener(api, id, signal),
  });
}

type CambioProveedor =
  | { tipo: "guardar"; body: ProveedorRequest; id?: string }
  | { tipo: "desactivar" | "activar"; id: string };

export function useCambiarProveedor() {
  const { api } = useSession();
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (
      cambio: CambioProveedor,
    ): Promise<ProveedorResponse | void> => {
      switch (cambio.tipo) {
        case "guardar":
          return cambio.id === undefined
            ? proveedoresApi.crear(api, cambio.body)
            : proveedoresApi.editar(api, cambio.id, cambio.body);
        case "desactivar":
          return proveedoresApi.desactivar(api, cambio.id);
        case "activar":
          return proveedoresApi.activar(api, cambio.id);
      }
    },
    onSuccess: async (proveedor, cambio) => {
      const id = cambio.id ?? String(proveedor?.id);
      const queryKey = detalle(id);
      await cache.cancelQueries({ queryKey, exact: true });
      if (proveedor) {
        cache.setQueryData(queryKey, proveedor);
      } else {
        cache.removeQueries({ queryKey, exact: true });
      }
      await Promise.all([
        cache.invalidateQueries({ queryKey: listas }),
        cache.invalidateQueries({
          queryKey: [...claveInsumosSuministrados, id],
        }),
      ]);
    },
  });
}
