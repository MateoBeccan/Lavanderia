import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import type { ApiClient } from "@/shared/api/http";
import type { ApiCatalogo, PaginaCatalogo, RegistroCatalogo } from "./api";

const listas = (path: string) => [path, "listas"] as const;
const detalle = (path: string, id: string) => [path, "detalle", id] as const;

export function opcionesDisponibles<Request, Response extends RegistroCatalogo>(
  catalogo: ApiCatalogo<Request, Response>,
  api: ApiClient,
  params: PaginaCatalogo,
) {
  return queryOptions({
    queryKey: [...listas(catalogo.path), params],
    queryFn: ({ signal }) => catalogo.listar(api, params, signal),
  });
}

export function useRegistroCatalogo<Request, Response extends RegistroCatalogo>(
  catalogo: ApiCatalogo<Request, Response>,
  id: string,
) {
  const { api } = useSession();
  return useQuery({
    queryKey: detalle(catalogo.path, id),
    queryFn: ({ signal }) => catalogo.obtener(api, id, signal),
  });
}

type Operacion<Request> =
  | { tipo: "guardar"; body: Request; id?: string }
  | { tipo: "activar" | "desactivar"; id: string };

export function useModificarCatalogo<
  Request,
  Response extends RegistroCatalogo,
>(catalogo: ApiCatalogo<Request, Response>) {
  const { api } = useSession();
  const cache = useQueryClient();
  return useMutation<Response | void, Error, Operacion<Request>>({
    mutationFn: (operacion: Operacion<Request>) => {
      switch (operacion.tipo) {
        case "guardar":
          return catalogo.guardar(api, operacion.body, operacion.id);
        case "activar":
          return catalogo.activar(api, operacion.id);
        case "desactivar":
          return catalogo.desactivar(api, operacion.id);
      }
    },
    onSuccess: async (registro, operacion) => {
      const id = registro ? String(registro.id) : operacion.id;
      if (id === undefined) return;
      const key = detalle(catalogo.path, id);
      await cache.cancelQueries({ queryKey: key, exact: true });
      if (registro) cache.setQueryData(key, registro);
      else cache.removeQueries({ queryKey: key, exact: true });
      await cache.invalidateQueries({ queryKey: listas(catalogo.path) });
    },
  });
}
