import type { ApiClient, PagedResponse } from "@/shared/api/http";

export type RegistroCatalogo = { id: number; nombre: string; activo: boolean };
export type PaginaCatalogo = {
  page: number;
  size: number;
  sort: readonly string[];
};

export function crearApiRegistroCatalogo<
  Request,
  Response extends RegistroCatalogo,
>(path: string) {
  return {
    path,
    obtener: (api: ApiClient, id: string, signal: AbortSignal) =>
      api<Response>(`${path}/${id}`, { signal }),
    guardar: (api: ApiClient, body: Request, id?: string) =>
      id === undefined
        ? api<Response>(path, { method: "POST", body })
        : api<Response>(`${path}/${id}`, { method: "PUT", body }),
    desactivar: (api: ApiClient, id: string) =>
      api<void>(`${path}/${id}`, { method: "DELETE" }),
    activar: (api: ApiClient, id: string) =>
      api<Response>(`${path}/${id}/activar`, { method: "PATCH" }),
  };
}

export function crearApiCatalogo<Request, Response extends RegistroCatalogo>(
  path: string,
) {
  return {
    ...crearApiRegistroCatalogo<Request, Response>(path),
    listar: (api: ApiClient, params: PaginaCatalogo, signal: AbortSignal) =>
      api<PagedResponse<Response>>(path, { params, signal }),
  };
}

export type ApiCatalogo<
  Request,
  Response extends RegistroCatalogo,
> = ReturnType<typeof crearApiCatalogo<Request, Response>>;
