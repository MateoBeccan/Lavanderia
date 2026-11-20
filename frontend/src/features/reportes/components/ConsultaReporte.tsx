import type { ComponentProps, ReactNode } from "react";
import { Link, useSearchParams } from "react-router";
import { ApiError } from "@/shared/api/http";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { DataTable } from "@/shared/components/tables/DataTable";
import { Button } from "@/shared/components/ui/button";
import { fechaPedido } from "@/features/pedidos/rules";
import { useReporte } from "../hooks/useReporte";
import {
  fechasReporte,
  leerFiltros,
  leerPaginacion,
  validarFiltros,
} from "../schemas/filtros";
import type { Filas, Filtros, Resumenes, TipoReporte } from "../types/reportes";
import { FiltrosReporte } from "./FiltrosReporte";
import { DescargaReporte } from "./DescargaReporte";
import type { CampoConsulta } from "./FiltrosReporte";

export function EnlaceRegistro({
  to,
  children,
}: {
  to: string;
  children: ReactNode;
}) {
  return (
    <Link
      className="font-medium text-primary underline underline-offset-2"
      to={to}
    >
      {children}
    </Link>
  );
}

export function ConsultaReporte<T extends TipoReporte>({
  tipo,
  campos,
  titulo,
  nota,
  indicadores,
  columns,
  getRowId,
}: {
  tipo: T;
  campos: CampoConsulta[];
  titulo: string;
  nota: string;
  indicadores: (resumen: Resumenes[T]) => ReactNode;
} & Pick<ComponentProps<typeof DataTable<Filas[T]>>, "columns" | "getRowId">) {
  const [params, setParams] = useSearchParams();
  const filtros = leerFiltros(params, [
    ...fechasReporte,
    ...campos.map(({ nombre }) => nombre),
  ]);
  const paginacion = leerPaginacion(params);
  const error = validarFiltros(filtros);
  const consulta = useReporte(tipo, filtros, paginacion, !error);
  const { fechaDesde, fechaHasta } = filtros;
  const periodo = error
    ? ""
    : fechaDesde && fechaHasta
      ? `Del ${fechaPedido(fechaDesde)} al ${fechaPedido(fechaHasta)}`
      : fechaDesde
        ? `Desde el ${fechaPedido(fechaDesde)}`
        : fechaHasta
          ? `Hasta el ${fechaPedido(fechaHasta)}`
          : "Todo el período";

  function actualizar(nuevos: Filtros, page = 0, size = paginacion.size) {
    const siguientes = new URLSearchParams(nuevos);
    if (page > 0) siguientes.set("page", String(page));
    if (size !== 20) siguientes.set("size", String(size));
    if (siguientes.toString() !== params.toString()) setParams(siguientes);
  }

  return (
    <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-4">
      <FiltrosReporte
        key={JSON.stringify(filtros)}
        campos={campos}
        filtros={filtros}
        aplicar={(nuevos) => actualizar(nuevos)}
        limpiar={() => actualizar({}, 0, 20)}
      />
      <DescargaReporte
        key={tipo + JSON.stringify(filtros)}
        tipo={tipo}
        filtros={filtros}
        bloqueado={Boolean(error) || !consulta.isSuccess}
      />
      {error ? (
        <ErrorState message={error} />
      ) : consulta.isPending ? (
        <LoadingState label="Cargando reporte…" />
      ) : consulta.isError ? (
        <ErrorState
          message={
            consulta.error instanceof ApiError
              ? consulta.error.message
              : "El reporte no está disponible."
          }
          onRetry={() => void consulta.refetch()}
        />
      ) : (
        <>
          <section
            aria-labelledby="resumen-reporte"
            className="grid min-w-0 gap-3"
          >
            <div>
              <h2 id="resumen-reporte" className="text-base font-semibold">
                Resumen de la consulta
              </h2>
              <p className="text-sm text-muted-foreground">
                {periodo}. {nota}
              </p>
            </div>
            <div className="grid gap-3 [overflow-wrap:anywhere] sm:grid-cols-2 xl:grid-cols-4">
              {indicadores(consulta.data.resumen)}
            </div>
          </section>
          {consulta.data.datos.length ? (
            <DataTable
              caption={titulo}
              columns={columns}
              data={consulta.data.datos}
              getRowId={getRowId}
              pagination={{
                pageIndex: consulta.data.pagina,
                totalPages: consulta.data.totalPaginas,
                onPageChange: (page) => actualizar(filtros, page),
              }}
            />
          ) : consulta.data.totalElementos > 0 ? (
            <EmptyState
              title="Esta página ya no tiene resultados"
              description="Volvé a la primera página para consultar los registros disponibles."
              action={
                <Button variant="outline" onClick={() => actualizar(filtros)}>
                  Ir a la primera página
                </Button>
              }
            />
          ) : (
            <EmptyState
              title="Sin resultados"
              description="No hay registros que coincidan con los filtros aplicados."
            />
          )}
        </>
      )}
    </div>
  );
}
