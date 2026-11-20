import { cn } from "@/shared/lib/utils";

const estilos: Record<string, string> = {
  PENDIENTE: "bg-amber-100 text-amber-900",
  EN_PROCESO: "bg-blue-100 text-blue-900",
  LISTO: "bg-emerald-100 text-emerald-900",
  ENTREGADO: "bg-teal-100 text-teal-900",
  CANCELADO: "bg-rose-100 text-rose-900",
};
const nombres: Record<string, string> = {
  PENDIENTE: "Pendiente",
  EN_PROCESO: "En proceso",
  LISTO: "Listo para entregar",
  ENTREGADO: "Entregado",
  CANCELADO: "Cancelado",
};

export function PedidoEstadoBadge({
  codigo,
  nombre,
}: {
  codigo: string;
  nombre?: string;
}) {
  return (
    <span
      className={cn(
        "inline-block rounded-full px-2.5 py-1 text-xs font-semibold",
        estilos[codigo] ?? "bg-muted text-foreground",
      )}
    >
      {nombre ?? nombres[codigo] ?? codigo}
    </span>
  );
}
