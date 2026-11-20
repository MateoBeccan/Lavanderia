import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { useSession } from "@/features/auth/session";
import { DataTable } from "@/shared/components/tables/DataTable";
import { EmptyState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { FormField } from "@/shared/components/forms/FormField";
import { cajaApi } from "../api/caja";
import { cajaKeys, useCatalogosCaja } from "../hooks/queries";
import { filtrosSchema } from "../schemas/caja";
import type { FiltrosMovimientos, MovimientoCaja } from "../types/caja";
import { CajaFeedback } from "./CajaFeedback";
import { fechaCaja, importeCaja, naturaleza } from "./formato";

export function MovimientosCaja({ id }: { id: string }) {
  const { api } = useSession();
  const [page, setPage] = useState(0);
  const [filtros, setFiltros] = useState<FiltrosMovimientos>({});
  const [error, setError] = useState<string>();
  const { tipos, metodos } = useCatalogosCaja();
  const movimientos = useQuery({
    queryKey: [...cajaKeys.movimientos(id), filtros, page],
    queryFn: ({ signal }) =>
      cajaApi.movimientos(api, id, page, filtros, signal),
  });

  return (
    <section className="space-y-4" aria-labelledby="movimientos-titulo">
      <div>
        <h2 id="movimientos-titulo" className="text-lg font-semibold">
          Movimientos
        </h2>
        <p className="text-sm text-muted-foreground">
          Orden del servidor por fecha e ID, más recientes primero.
        </p>
      </div>
      <form
        className="surface flex flex-wrap items-start gap-3 p-4"
        aria-label="Filtros de movimientos"
        onReset={() => {
          setFiltros({});
          setPage(0);
          setError(undefined);
        }}
        onSubmit={(event) => {
          event.preventDefault();
          const parsed = filtrosSchema.safeParse(
            Object.fromEntries(new FormData(event.currentTarget)),
          );
          if (!parsed.success) {
            setError(parsed.error.issues[0]?.message);
            return;
          }
          const { tipo, metodoPagoId, desde, hasta } = parsed.data;
          setFiltros({
            tipo: tipo || undefined,
            metodoPagoId: metodoPagoId || undefined,
            desde: desde || undefined,
            hasta: hasta || undefined,
          });
          setError(undefined);
          setPage(0);
        }}
      >
        <FormField id="mov-tipo" label="Tipo">
          <select
            id="mov-tipo"
            name="tipo"
            className="min-h-11 w-full rounded-md border border-border bg-white px-3"
          >
            <option value="">Todos</option>
            {tipos.data?.map((tipo) => (
              <option key={tipo.id} value={tipo.codigo}>
                {tipo.nombre}
              </option>
            ))}
          </select>
          <CajaFeedback query={tipos} />
        </FormField>
        <FormField id="mov-metodo" label="Método de pago">
          <select
            id="mov-metodo"
            name="metodoPagoId"
            className="min-h-11 w-full rounded-md border border-border bg-white px-3"
          >
            <option value="">Todos</option>
            {metodos.data?.map((metodo) => (
              <option key={metodo.id} value={metodo.id}>
                {metodo.nombre}
              </option>
            ))}
          </select>
          <CajaFeedback query={metodos} />
        </FormField>
        <FormField id="mov-desde" label="Desde">
          <Input id="mov-desde" name="desde" type="date" />
        </FormField>
        <FormField id="mov-hasta" label="Hasta" error={error}>
          <Input
            id="mov-hasta"
            name="hasta"
            type="date"
            aria-invalid={!!error}
            aria-describedby={error ? "mov-hasta-error" : undefined}
          />
        </FormField>
        <div className="flex flex-wrap gap-2 sm:pt-6">
          <Button type="submit">Aplicar filtros</Button>
          <Button type="reset" variant="outline">
            Limpiar
          </Button>
        </div>
      </form>
      <CajaFeedback query={movimientos} />
      {!movimientos.error &&
        movimientos.data &&
        (movimientos.data.page.totalElements === 0 ? (
          <EmptyState
            title="Sin movimientos"
            description="No hay movimientos para esta caja con los filtros seleccionados."
          />
        ) : (
          <DataTable<MovimientoCaja>
            caption={"Movimientos de caja #" + id}
            data={movimientos.data.content}
            getRowId={(row) => String(row.id)}
            pagination={{
              pageIndex: page,
              totalPages: movimientos.data.page.totalPages,
              onPageChange: setPage,
            }}
            columns={[
              {
                id: "id",
                header: "ID",
                cell: ({ row }) => (
                  <Link
                    className="underline"
                    to={`/caja/${id}/movimientos/${row.original.id}`}
                    aria-label={`Ver movimiento #${row.original.id}`}
                  >
                    #{row.original.id}
                  </Link>
                ),
              },
              {
                id: "fecha",
                header: "Fecha y hora",
                cell: ({ row }) => fechaCaja(row.original.fechaHora),
              },
              {
                id: "tipo",
                header: "Tipo",
                cell: ({ row }) => (
                  <>
                    <Sentido codigo={row.original.tipoMovimientoCodigo} />{" "}
                    {row.original.tipoMovimientoNombre}
                  </>
                ),
              },
              {
                id: "importe",
                header: "Importe",
                cell: ({ row }) => importeCaja(row.original.monto),
              },
              {
                accessorKey: "descripcion",
                header: "Concepto",
                cell: ({ row }) => (
                  <span className="whitespace-normal break-words">
                    {row.original.descripcion}
                  </span>
                ),
              },
              { accessorKey: "metodoPagoNombre", header: "Método" },
              { accessorKey: "usuarioNombre", header: "Registrado por" },
            ]}
          />
        ))}
    </section>
  );
}

export function Sentido({ codigo }: { codigo: string }) {
  const sentido = naturaleza(codigo);
  if (!sentido) return null;
  return (
    <span
      className={
        sentido === "Ingreso"
          ? "rounded bg-emerald-100 px-2 py-1 text-emerald-900"
          : "rounded bg-orange-100 px-2 py-1 text-orange-900"
      }
    >
      {sentido}
    </span>
  );
}
