import { z } from "zod";
import { nombreCatalogo } from "@/shared/catalogos/schema";

export const prendaSchema = z.object({ nombre: nombreCatalogo(50) });
