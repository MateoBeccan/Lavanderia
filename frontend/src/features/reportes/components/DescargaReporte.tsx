import { FileDown, LoaderCircle } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useSession } from "@/features/auth/session";
import { ApiError } from "@/shared/api/http";
import { ErrorState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { descargarReporte } from "../api/reportes";
import type { Filtros, TipoReporte } from "../types/reportes";

export function DescargaReporte({
  tipo,
  filtros,
  bloqueado,
}: {
  tipo: TipoReporte;
  filtros: Filtros;
  bloqueado: boolean;
}) {
  const { api, user } = useSession();
  const solicitud = useRef<AbortController>(null);
  const [estado, setEstado] = useState<"generando" | "descargado">();
  const [error, setError] = useState<string>();

  useEffect(() => () => solicitud.current?.abort(), []);

  async function generar() {
    if (solicitud.current || bloqueado || user?.rol !== "ADMINISTRADOR") return;
    const control = new AbortController();
    solicitud.current = control;
    setEstado("generando");
    setError(undefined);
    try {
      const { blob, nombre } = await descargarReporte(
        api,
        tipo,
        filtros,
        control.signal,
      );
      control.signal.throwIfAborted();
      const url = URL.createObjectURL(blob);
      const enlace = document.createElement("a");
      enlace.href = url;
      enlace.download = nombre ?? `mira-que-te-lavo-${tipo}.pdf`;
      document.body.append(enlace);
      enlace.click();
      enlace.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
      setEstado("descargado");
    } catch (error) {
      if (control.signal.aborted) return;
      setEstado(undefined);
      setError(
        error instanceof ApiError
          ? error.message
          : "No se pudo descargar el PDF. Intentá nuevamente.",
      );
    } finally {
      solicitud.current = null;
    }
  }

  if (user?.rol !== "ADMINISTRADOR") return null;
  return (
    <div className="surface flex flex-wrap items-center justify-between gap-3 p-4">
      <div className="min-w-0">
        <p className="font-semibold">Exportar reporte</p>
        <p className="text-sm text-muted-foreground">
          El PDF incluye todos los registros que coinciden con los filtros
          aplicados.
        </p>
      </div>
      <Button
        onClick={() => void generar()}
        disabled={bloqueado || estado === "generando"}
      >
        {estado === "generando" ? (
          <LoaderCircle
            className="animate-spin motion-reduce:animate-none"
            aria-hidden="true"
          />
        ) : (
          <FileDown aria-hidden="true" />
        )}
        {estado === "generando" ? "Generando PDF…" : "Generar PDF"}
      </Button>
      <div className="basis-full empty:hidden" role="status">
        {estado === "generando"
          ? "Preparando el archivo…"
          : estado === "descargado"
            ? "PDF generado. Se inició la descarga."
            : null}
      </div>
      {error && (
        <div className="basis-full">
          <ErrorState message={error} />
        </div>
      )}
    </div>
  );
}
