import type { ApiClient, PagedResponse } from "@/shared/api/http";
import { crearApiRegistroCatalogo } from "@/shared/catalogos/api";
import type {
  CategoriaInsumo,
  CategoriaInsumoRequest,
  Insumo,
  InsumoRequest,
  ParametrosInsumos,
} from "../types/insumos";

export const insumosApi = {
  ...crearApiRegistroCatalogo<InsumoRequest, Insumo>("/insumos"),
  listar: (api: ApiClient, params: ParametrosInsumos, signal: AbortSignal) =>
    api<PagedResponse<Insumo>>("/insumos", { params, signal }),
};

export const categoriasInsumoApi = {
  ...crearApiRegistroCatalogo<CategoriaInsumoRequest, CategoriaInsumo>(
    "/categorias-insumo",
  ),
  listar: (api: ApiClient, signal: AbortSignal) =>
    api<CategoriaInsumo[]>("/categorias-insumo", { signal }),
};
