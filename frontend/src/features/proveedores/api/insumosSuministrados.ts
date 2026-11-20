import type { ApiClient, PagedResponse } from "@/shared/api/http";
import type { Pagina } from "@/features/insumos/types/insumos";
import type {
  AsociarInsumoRequest,
  InsumoSuministrado,
} from "../types/insumosSuministrados";

const recurso = (proveedorId: string) => `/proveedores/${proveedorId}/insumos`;

export const insumosSuministradosApi = {
  listar: (
    api: ApiClient,
    proveedorId: string,
    params: Pagina,
    signal: AbortSignal,
  ) =>
    api<PagedResponse<InsumoSuministrado>>(recurso(proveedorId), {
      params,
      signal,
    }),
  asociar: (api: ApiClient, proveedorId: string, body: AsociarInsumoRequest) =>
    api<InsumoSuministrado>(recurso(proveedorId), { method: "POST", body }),
  desasociar: (api: ApiClient, proveedorId: string, insumoId: number) =>
    api<void>(`${recurso(proveedorId)}/${insumoId}`, {
      method: "DELETE",
      expectedStatus: 204,
    }),
};
