import { useState } from "react";
import { ApiError } from "@/shared/api/http";
import { selectCatalogo } from "@/shared/catalogos/config";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { fechasReporte, leerFiltros, validarFiltros } from "../schemas/filtros";
import type { CampoFiltro, Filtros } from "../types/reportes";
import { FiltroEntidad } from "./FiltroEntidad";

type Catalogo = {
  data?: readonly { codigo: string; nombre: string }[];
  isPending: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => Promise<unknown>;
};

export type CampoConsulta = {
  nombre: Exclude<CampoFiltro, (typeof fechasReporte)[number]>;
  etiqueta: string;
  catalogo?: Catalogo;
};

export function FiltrosReporte({
  campos,
  filtros,
  aplicar,
  limpiar,
}: {
  campos: CampoConsulta[];
  filtros: Filtros;
  aplicar: (filtros: Filtros) => void;
  limpiar: () => void;
}) {
  const [error, setError] = useState<string>();
  const [revision, setRevision] = useState(0);
  return (
    <form
      aria-label="Filtros del reporte"
      className="surface grid gap-4 p-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5"
      onSubmit={(event) => {
        event.preventDefault();
        const nuevos = leerFiltros(new FormData(event.currentTarget), [
          ...fechasReporte,
          ...campos.map(({ nombre }) => nombre),
        ]);
        const invalido = validarFiltros(nuevos);
        setError(invalido);
        if (!invalido) aplicar(nuevos);
      }}
      onReset={() => {
        setError(undefined);
        setRevision((valor) => valor + 1);
        limpiar();
      }}
    >
      {fechasReporte.map((nombre) => (
        <FormField
          key={nombre}
          id={nombre}
          label={nombre === "fechaDesde" ? "Fecha desde" : "Fecha hasta"}
        >
          <Input
            id={nombre}
            name={nombre}
            type="date"
            min="0001-01-01"
            max="9999-12-31"
            defaultValue={filtros[nombre]}
          />
        </FormField>
      ))}
      {campos.map(({ nombre, etiqueta, catalogo }) => {
        const valor = filtros[nombre];
        if (nombre === "clienteId" || nombre === "proveedorId") {
          return (
            <FiltroEntidad
              key={nombre + revision}
              campo={nombre}
              inicial={valor}
            />
          );
        }
        const opciones = catalogo?.data ?? [];
        const opcionesConFiltro =
          valor && !opciones.some(({ codigo }) => codigo === valor)
            ? [{ codigo: valor, nombre: valor }, ...opciones]
            : opciones;
        return (
          <FormField key={nombre} id={nombre} label={etiqueta}>
            {catalogo ? (
              <select
                id={nombre}
                name={nombre}
                className={selectCatalogo}
                defaultValue={valor ?? ""}
              >
                <option value="">Todos</option>
                {opcionesConFiltro.map(({ codigo, nombre }) => (
                  <option key={codigo} value={codigo}>
                    {nombre}
                  </option>
                ))}
              </select>
            ) : (
              <Input
                id={nombre}
                name={nombre}
                inputMode="numeric"
                defaultValue={valor}
              />
            )}
          </FormField>
        );
      })}
      <div className="col-span-full flex flex-wrap gap-2">
        <Button type="submit">Aplicar filtros</Button>
        <Button type="reset" variant="outline">
          Limpiar filtros
        </Button>
      </div>
      {(error ||
        campos.some(
          ({ catalogo }) => catalogo?.isPending || catalogo?.isError,
        )) && (
        <div className="col-span-full space-y-2">
          {error && <ErrorState message={error} />}
          {campos.map(({ nombre, etiqueta, catalogo }) =>
            catalogo?.isPending ? (
              <LoadingState
                key={nombre}
                label={`Cargando opciones de ${etiqueta.toLowerCase()}…`}
              />
            ) : catalogo?.isError ? (
              <ErrorState
                key={nombre}
                message={`${etiqueta}. ${catalogo.error instanceof ApiError ? catalogo.error.message : "Las opciones no están disponibles."}`}
                onRetry={() => void catalogo.refetch()}
              />
            ) : null,
          )}
        </div>
      )}
    </form>
  );
}
