import { crearApiCatalogo } from "@/shared/catalogos/api";
import type { ServicioRequest, ServicioResponse } from "../types/servicios";

export const serviciosApi = crearApiCatalogo<ServicioRequest, ServicioResponse>(
  "/servicios",
);
