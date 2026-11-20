import type { ApiClient, PagedResponse } from "@/shared/api/http";
import type {
  ParametrosProveedores,
  ProveedorRequest,
  ProveedorResponse,
} from "../types/proveedores";

const path = "/proveedores";

export const proveedoresApi = {
  listar: (
    api: ApiClient,
    params: ParametrosProveedores,
    signal: AbortSignal,
  ) => api<PagedResponse<ProveedorResponse>>(path, { params, signal }),
  obtener: (api: ApiClient, id: string, signal: AbortSignal) =>
    api<ProveedorResponse>(`${path}/${id}`, { signal }),
  crear: (api: ApiClient, body: ProveedorRequest) =>
    api<ProveedorResponse>(path, { method: "POST", body }),
  editar: (api: ApiClient, id: string, body: ProveedorRequest) =>
    api<ProveedorResponse>(`${path}/${id}`, { method: "PUT", body }),
  desactivar: (api: ApiClient, id: string) =>
    api<void>(`${path}/${id}`, { method: "DELETE", expectedStatus: 204 }),
  activar: (api: ApiClient, id: string) =>
    api<ProveedorResponse>(`${path}/${id}/activar`, { method: "PATCH" }),
};
