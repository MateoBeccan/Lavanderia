import type { RegistroCatalogo } from "@/shared/catalogos/api";

export const modalidadesCobro = ["UNIDAD", "KILO", "PRENDA"] as const;
export type ModalidadCobro = (typeof modalidadesCobro)[number];

export type ServicioRequest = {
  nombre: string;
  precio: number;
  modalidadCobro: ModalidadCobro;
};
export type ServicioResponse = ServicioRequest & RegistroCatalogo;
