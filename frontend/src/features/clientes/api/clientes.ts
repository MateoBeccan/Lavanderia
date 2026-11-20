import type { ApiClient, PagedResponse } from "@/shared/api/http";
import type {
  ClienteRequest,
  ClienteResponse,
  ParametrosClientes,
} from "../types/clientes";

const path = "/clientes";

export const clientesApi = {
  listar: (api: ApiClient, params: ParametrosClientes, signal: AbortSignal) =>
    api<PagedResponse<ClienteResponse>>(path, { params, signal }),
  obtener: (api: ApiClient, id: string, signal: AbortSignal) =>
    api<ClienteResponse>(`${path}/${id}`, { signal }),
  crear: (api: ApiClient, body: ClienteRequest) =>
    api<ClienteResponse>(path, { method: "POST", body }),
  editar: (api: ApiClient, id: string, body: ClienteRequest) =>
    api<ClienteResponse>(`${path}/${id}`, { method: "PUT", body }),
  desactivar: (api: ApiClient, id: string) =>
    api<void>(`${path}/${id}`, { method: "DELETE" }),
};
