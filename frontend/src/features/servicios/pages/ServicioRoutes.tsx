import { CatalogoRoutes } from "@/shared/catalogos/CatalogoRoutes";
import { configServicio } from "../components/config";

export function ServicioRoutes() {
  return <CatalogoRoutes config={configServicio} />;
}
