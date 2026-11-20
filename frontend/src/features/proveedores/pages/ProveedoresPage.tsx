import { useEffect } from "react";
import type { ComponentProps } from "react";
import { Plus } from "lucide-react";
import { Link, useLocation, useSearchParams } from "react-router";
import { useSession } from "@/features/auth/session";
import { selectCatalogo } from "@/shared/catalogos/config";
import { EstadoActivo } from "@/shared/components/EstadoActivo";
import { PageHeader } from "@/shared/components/PageHeader";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { DataTable } from "@/shared/components/tables/DataTable";
import { Button } from "@/shared/components/ui/button";
import { DesactivarProveedor } from "../components/DesactivarProveedor";
import {
  camposFiltro,
  FiltrosProveedores,
} from "../components/FiltrosProveedores";
import { proveedorError } from "../components/feedback";
import { useProveedores } from "../hooks/queries";
import type {
  FiltrosProveedores as Filtros,
  ProveedorResponse,
} from "../types/proveedores";

const tamanios = [10, 20, 50];
const nombres = new Intl.Collator("es-AR", { sensitivity: "base" });

export function ProveedoresPage() {
  const { user } = useSession();
  const admin = user?.rol === "ADMINISTRADOR";
  const { search } = useLocation();
  const [params, setParams] = useSearchParams();
  const filtros: Filtros = {};
  for (const [key] of camposFiltro) {
    const valor = params.get(key)?.trim();
    if (valor) filtros[key] = valor;
  }
  const solicitado = Number(params.get("page"));
  const page =
    Number.isInteger(solicitado) &&
    solicitado >= 0 &&
    solicitado <= 2_147_483_647
      ? solicitado
      : 0;
  const size =
    tamanios.find((value) => value === Number(params.get("size"))) ?? 20;
  const sort = params.get("orden") === "nombre" ? "nombre" : "id";
  const listado = useProveedores({ page, size, ...filtros });
  const metadata = listado.data?.page;
  const ultima = Math.max(0, (metadata?.totalPages ?? 1) - 1);
  const fueraDeRango = !!metadata && page > ultima;

  useEffect(() => {
    if (!fueraDeRango || !listado.isSuccess) return;
    const next = new URLSearchParams(params);
    next.set("page", String(ultima));
    setParams(next, { replace: true });
  }, [fueraDeRango, listado.isSuccess, params, setParams, ultima]);

  function actualizar(values: Record<string, string | number | undefined>) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(values)) {
      if (value === undefined) next.delete(key);
      else next.set(key, String(value));
    }
    setParams(next);
  }

  const contenido = listado.data?.content ?? [];
  const filas =
    sort === "nombre"
      ? [...contenido].sort(
          (a, b) => nombres.compare(a.nombre, b.nombre) || a.id - b.id,
        )
      : contenido;
  const columns: ComponentProps<
    typeof DataTable<ProveedorResponse>
  >["columns"] = [
    {
      id: "nombre",
      header: "Proveedor",
      cell: ({
        row: {
          original: { id, nombre },
        },
      }) => (
        <Link
          className="font-semibold underline underline-offset-4"
          to={{ pathname: `/proveedores/${id}`, search }}
        >
          {nombre}
        </Link>
      ),
    },
    { accessorKey: "cuit", header: "CUIT" },
    { accessorKey: "telefono", header: "Teléfono" },
    {
      id: "rubro",
      header: "Rubro",
      cell: ({ row }) => row.original.rubro ?? "—",
    },
    {
      id: "estado",
      header: "Estado",
      cell: ({ row }) => <EstadoActivo activo={row.original.activo} />,
    },
  ];
  if (admin) {
    columns.push({
      id: "acciones",
      header: "Acciones",
      cell: ({ row: { original: proveedor } }) => (
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline">
            <Link
              to={{ pathname: `/proveedores/${proveedor.id}/editar`, search }}
              aria-label={`Editar a ${proveedor.nombre}`}
            >
              Editar
            </Link>
          </Button>
          <DesactivarProveedor proveedor={proveedor} />
        </div>
      ),
    });
  }

  return (
    <>
      <PageHeader
        title="Proveedores"
        action={
          admin && (
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" asChild>
                <Link to={{ pathname: "/proveedores/reactivar", search }}>
                  Reactivar proveedor
                </Link>
              </Button>
              <Button asChild>
                <Link to={{ pathname: "/proveedores/nuevo", search }}>
                  <Plus aria-hidden="true" /> Nuevo proveedor
                </Link>
              </Button>
            </div>
          )
        }
      />
      <section
        className="surface mb-4 grid gap-5 p-4 sm:p-6"
        aria-label="Controles del listado"
      >
        <FiltrosProveedores
          key={JSON.stringify(filtros)}
          filtros={filtros}
          onApply={(next) =>
            actualizar({
              ...Object.fromEntries(
                camposFiltro.map(([key]) => [key, next[key]]),
              ),
              page: 0,
            })
          }
        />
        <div className="grid gap-4 border-t border-border pt-4 sm:grid-cols-[auto_auto] sm:justify-start">
          <FormField id="proveedores-orden" label="Orden en esta página">
            <select
              id="proveedores-orden"
              className={`${selectCatalogo} border-border`}
              value={sort}
              onChange={(event) => actualizar({ orden: event.target.value })}
            >
              <option value="id">ID ascendente (orden del servidor)</option>
              <option value="nombre">Nombre A–Z</option>
            </select>
          </FormField>
          <FormField id="proveedores-tamanio" label="Por página">
            <select
              id="proveedores-tamanio"
              className={`${selectCatalogo} border-border`}
              value={size}
              onChange={(event) =>
                actualizar({ page: 0, size: event.target.value })
              }
            >
              {tamanios.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </FormField>
        </div>
      </section>
      {listado.isError ? (
        <ErrorState
          message={proveedorError(listado.error)}
          onRetry={() => void listado.refetch()}
        />
      ) : listado.isPending || fueraDeRango ? (
        <LoadingState label="Cargando proveedores…" />
      ) : (
        <>
          <div className="mb-3 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
            <p aria-live="polite">
              {listado.data.page.totalElements.toLocaleString("es-AR")}{" "}
              {listado.data.page.totalElements === 1
                ? "proveedor activo"
                : "proveedores activos"}
            </p>
            {listado.isFetching && (
              <LoadingState label="Actualizando proveedores…" />
            )}
          </div>
          {filas.length ? (
            <DataTable
              caption="Proveedores activos"
              data={filas}
              columns={columns}
              getRowId={(row) => String(row.id)}
              pagination={{
                pageIndex: listado.data.page.number,
                totalPages: listado.data.page.totalPages,
                onPageChange: (index) => actualizar({ page: index }),
              }}
            />
          ) : (
            <EmptyState
              title="Sin proveedores activos"
              description={
                Object.keys(filtros).length
                  ? "Ningún proveedor coincide con los filtros aplicados."
                  : "Todavía no hay proveedores activos en el listado."
              }
            />
          )}
        </>
      )}
    </>
  );
}
