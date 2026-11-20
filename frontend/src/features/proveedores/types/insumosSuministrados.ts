export type AsociarInsumoRequest = { insumoId: number };

export type InsumoSuministrado = {
  proveedorId: number;
  proveedorNombre: string;
  insumoId: number;
  insumoNombre: string;
  categoriaId: number;
  categoriaNombre: string;
};
