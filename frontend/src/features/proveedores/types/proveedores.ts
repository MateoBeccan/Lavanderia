export type ProveedorRequest = {
  nombre: string;
  cuit: string;
  telefono: string;
  rubro: string | null;
};

export type ProveedorResponse = ProveedorRequest & {
  id: number;
  activo: boolean;
};

export type FiltrosProveedores = Partial<
  Record<keyof ProveedorRequest, string>
>;

export type ParametrosProveedores = FiltrosProveedores & {
  page: number;
  size: number;
};
