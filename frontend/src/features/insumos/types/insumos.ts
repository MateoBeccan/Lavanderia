import type { RegistroCatalogo } from "@/shared/catalogos/api";

export type TipoCatalogoInsumos = "insumo" | "categoría";
export type CategoriaInsumo = RegistroCatalogo;
export type CategoriaInsumoRequest = { nombre: string };
export type InsumoRequest = CategoriaInsumoRequest & { categoriaId: number };
export type Insumo = RegistroCatalogo & {
  categoriaId: number;
  categoriaNombre: string;
};
export type Pagina = { page: number; size: number };
export type ParametrosInsumos = Pagina & { categoriaId?: string };
