import { crearApiCatalogo } from "@/shared/catalogos/api";
import type { PrendaRequest, PrendaResponse } from "../types/prendas";

export const prendasApi = crearApiCatalogo<PrendaRequest, PrendaResponse>(
  "/prendas",
);
