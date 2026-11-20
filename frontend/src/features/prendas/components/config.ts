import type { ConfigCatalogo } from "@/shared/catalogos/config";
import { prendasApi } from "../api/prendas";
import { prendaSchema } from "../schemas/prenda";
import type { PrendaRequest, PrendaResponse } from "../types/prendas";

export const configPrenda: ConfigCatalogo<PrendaRequest, PrendaResponse> = {
  api: prendasApi,
  schema: prendaSchema,
  titulo: "Prendas",
  singular: "prenda",
  genero: "a",
  valores: (registro) => ({ nombre: registro?.nombre ?? "" }),
};
