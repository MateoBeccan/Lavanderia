export type ClienteRequest = {
  nombre: string;
  apellido: string;
  dni: string;
  telefono: string | null;
  email: string | null;
};

export type ClienteResponse = Omit<ClienteRequest, "dni"> & {
  id: number;
  dni: string | null;
  activo: boolean;
  fechaCreacion: string;
};

export const ordenesClientes = [
  "id,asc",
  "id,desc",
  "apellido,asc",
  "apellido,desc",
] as const;

export type ParametrosClientes = {
  page: number;
  size: number;
  sort: readonly string[];
};
