import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import { claveInsumosSuministrados } from "@/features/proveedores/hooks/insumosSuministrados";
import type { ApiClient } from "@/shared/api/http";
import { categoriasInsumoApi, insumosApi } from "../api/insumos";
import type {
  CategoriaInsumo,
  CategoriaInsumoRequest,
  Insumo,
  InsumoRequest,
  ParametrosInsumos,
  TipoCatalogoInsumos,
} from "../types/insumos";

const claveInsumos = [insumosApi.path] as const;
const claveCategorias = [categoriasInsumoApi.path] as const;
const detalle = (path: string, id: string) => [path, "detalle", id] as const;

export function opcionesInsumos(api: ApiClient, params: ParametrosInsumos) {
  return queryOptions({
    queryKey: [...claveInsumos, "lista", params],
    queryFn: ({ signal }) => insumosApi.listar(api, params, signal),
  });
}

export function useCategoriasInsumo(enabled = true) {
  const { api } = useSession();
  return useQuery({
    queryKey: [...claveCategorias, "lista"],
    queryFn: ({ signal }) => categoriasInsumoApi.listar(api, signal),
    enabled,
  });
}

export const recursosInsumos = {
  insumo: insumosApi,
  categoría: categoriasInsumoApi,
};

export function useRegistroInsumos(tipo: TipoCatalogoInsumos, id: string) {
  const { api } = useSession();
  const recurso = recursosInsumos[tipo];
  return useQuery({
    queryKey: detalle(recurso.path, id),
    queryFn: ({ signal }): Promise<CategoriaInsumo | Insumo> =>
      recurso.obtener(api, id, signal),
  });
}

type Cambio =
  | {
      tipo: "guardar";
      body: InsumoRequest | CategoriaInsumoRequest;
      id?: string;
    }
  | { tipo: "activar" | "desactivar"; id: string };

export function useCambiarRegistroInsumos(tipo: TipoCatalogoInsumos) {
  const { api } = useSession();
  const cache = useQueryClient();
  const recurso = recursosInsumos[tipo];
  return useMutation({
    mutationFn: (cambio: Cambio): Promise<CategoriaInsumo | Insumo | void> => {
      switch (cambio.tipo) {
        case "guardar":
          return "categoriaId" in cambio.body
            ? insumosApi.guardar(api, cambio.body, cambio.id)
            : categoriasInsumoApi.guardar(api, cambio.body, cambio.id);
        case "activar":
          return recurso.activar(api, cambio.id);
        case "desactivar":
          return recurso.desactivar(api, cambio.id);
      }
    },
    onSuccess: async (registro, cambio) => {
      const id = registro ? String(registro.id) : cambio.id;
      const key = detalle(recurso.path, id ?? "");
      await cache.cancelQueries({ queryKey: key, exact: true });
      if (registro) cache.setQueryData(key, registro);
      else cache.removeQueries({ queryKey: key, exact: true });
      await Promise.all([
        cache.invalidateQueries({ queryKey: claveInsumos }),
        cache.invalidateQueries({ queryKey: claveInsumosSuministrados }),
        ...(tipo === "categoría"
          ? [cache.invalidateQueries({ queryKey: claveCategorias })]
          : []),
      ]);
    },
  });
}
