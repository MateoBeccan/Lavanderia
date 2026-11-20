import type { ZodType } from "zod";
import type { ApiCatalogo, RegistroCatalogo } from "./api";

export type DatosFormularioCatalogo = {
  nombre: string;
  precio?: string;
  modalidadCobro?: string;
};

export type ConfigCatalogo<Request, Response extends RegistroCatalogo> = {
  api: ApiCatalogo<Request, Response>;
  schema: ZodType<Request, DatosFormularioCatalogo>;
  titulo: string;
  singular: "servicio" | "prenda";
  genero: "o" | "a";
  modalidades?: readonly string[];
  valores: (registro?: Response) => DatosFormularioCatalogo;
  datos?: { label: string; valor: (registro: Response) => string }[];
};

export const selectCatalogo =
  "h-10 w-full rounded-lg border bg-background px-3 text-sm";
