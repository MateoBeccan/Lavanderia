import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import type { ComponentProps } from "react";
import { Link, useLocation, useSearchParams } from "react-router";
import { toast } from "sonner";
import { Plus } from "lucide-react";
import { useSession } from "@/features/auth/session";
import { PageHeader } from "@/shared/components/PageHeader";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { DataTable } from "@/shared/components/tables/DataTable";
import { Button } from "@/shared/components/ui/button";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { Input } from "@/shared/components/ui/input";
import type { RegistroCatalogo } from "./api";
import { selectCatalogo } from "./config";
import type { ConfigCatalogo } from "./config";
import { errorCatalogo } from "./feedback";
import { opcionesDisponibles, useModificarCatalogo } from "./queries";

const ordenes = [
  ["id,asc", "Más antiguos"],
  ["id,desc", "Más recientes"],
  ["nombre,asc", "Nombre A–Z"],
  ["nombre,desc", "Nombre Z–A"],
] as const;
const tamanios = [10, 20, 50];

export function CatalogoListado<Request, Response extends RegistroCatalogo>({
  config,
}: {
  config: ConfigCatalogo<Request, Response>;
}) {
  const { api, user } = useSession();
  const admin = user?.rol === "ADMINISTRADOR";
  const { search } = useLocation();
  const [params, setParams] = useSearchParams();
  const [filtro, setFiltro] = useState("");
  const { titulo, singular, genero } = config;
  const { path } = config.api;
  const actual = Number(params.get("page"));
  const page =
    Number.isInteger(actual) && actual >= 0 && actual <= 2147483647
      ? actual
      : 0;
  const solicitado = Number(params.get("size"));
  const size = tamanios.includes(solicitado) ? solicitado : 20;
  const sort =
    ordenes.find(([valor]) => valor === params.get("sort"))?.[0] ?? "id,asc";
  const listado = useQuery(
    opcionesDisponibles(config.api, api, {
      page,
      size,
      sort: sort.startsWith("nombre") ? [sort, "id,asc"] : [sort],
    }),
  );
  const baja = useModificarCatalogo(config.api);
  const metadata = listado.data?.page;
  const ultima = Math.max(0, (metadata?.totalPages ?? 1) - 1);
  const fueraDeRango = !!metadata && page > ultima;
  useEffect(() => {
    if (!fueraDeRango) return;
    const next = new URLSearchParams(params);
    next.set("page", String(ultima));
    setParams(next, { replace: true });
  }, [fueraDeRango, params, setParams, ultima]);

  function actualizar(values: Record<string, string | number>) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(values))
      next.set(key, String(value));
    setParams(next);
  }

  const termino = filtro.trim().toLocaleLowerCase("es-AR");
  const filas =
    listado.data?.content.filter(({ nombre }) =>
      nombre.toLocaleLowerCase("es-AR").includes(termino),
    ) ?? [];
  const columnas: ComponentProps<typeof DataTable<Response>>["columns"] = [
    {
      id: "nombre",
      header: "Nombre",
      cell: ({ row: { original } }) => (
        <Link
          className="font-semibold underline underline-offset-4"
          to={{ pathname: `${path}/${original.id}`, search }}
        >
          {original.nombre}
        </Link>
      ),
    },
    ...(config.datos ?? []).map(({ label, valor }) => ({
      id: label,
      header: label,
      cell: ({ row }: { row: { original: Response } }) => valor(row.original),
    })),
    {
      id: "estado",
      header: "Estado",
      cell: ({ row }) => (row.original.activo ? "Activo" : "Inactivo"),
    },
  ];
  if (admin)
    columnas.push({
      id: "acciones",
      header: "Acciones",
      cell: ({ row: { original } }) => (
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline">
            <Link
              to={{ pathname: `${path}/${original.id}/editar`, search }}
              aria-label={`Editar ${original.nombre}`}
            >
              Editar
            </Link>
          </Button>
          <ConfirmDialog
            trigger={
              <Button
                variant="outline"
                disabled={baja.isPending}
                aria-label={`Desactivar ${original.nombre}`}
              >
                Desactivar
              </Button>
            }
            title={`Desactivar ${singular}`}
            description={`«${original.nombre}» dejará de estar disponible en el catálogo. Guardá su ID (${original.id}) si necesitás reactivarlo.`}
            confirmLabel={`Desactivar ${singular}`}
            onConfirm={() =>
              baja.mutate(
                { tipo: "desactivar", id: String(original.id) },
                {
                  onSuccess: () =>
                    toast.success(`Baja confirmada · ID ${original.id}.`),
                },
              )
            }
          />
        </div>
      ),
    });

  return (
    <>
      <PageHeader
        title={titulo}
        action={
          admin && (
            <div className="flex flex-wrap gap-2">
              <Button asChild variant="outline">
                <Link
                  to={{ pathname: `${path}/reactivar`, search }}
                >{`Reactivar ${singular}`}</Link>
              </Button>
              <Button asChild>
                <Link to={{ pathname: `${path}/nuevo`, search }}>
                  <Plus aria-hidden="true" />
                  {`Nuev${genero} ${singular}`}
                </Link>
              </Button>
            </div>
          )
        }
      />
      <section
        aria-label="Controles del listado"
        className="surface mb-4 grid gap-4 p-4 sm:grid-cols-[minmax(0,1fr)_auto_auto]"
      >
        <FormField id="catalogo-filtro" label="Filtrar página actual">
          <Input
            id="catalogo-filtro"
            type="search"
            value={filtro}
            onChange={(event) => setFiltro(event.target.value)}
            placeholder="Nombre"
          />
        </FormField>
        <FormField id="catalogo-orden" label="Ordenar por">
          <select
            id="catalogo-orden"
            className={selectCatalogo}
            value={sort}
            onChange={(event) =>
              actualizar({ sort: event.target.value, page: 0 })
            }
          >
            {ordenes.map(([valor, etiqueta]) => (
              <option key={valor} value={valor}>
                {etiqueta}
              </option>
            ))}
          </select>
        </FormField>
        <FormField id="catalogo-tamanio" label="Por página">
          <select
            id="catalogo-tamanio"
            className={selectCatalogo}
            value={size}
            onChange={(event) =>
              actualizar({ size: event.target.value, page: 0 })
            }
          >
            {tamanios.map((valor) => (
              <option key={valor}>{valor}</option>
            ))}
          </select>
        </FormField>
      </section>
      {baja.isPending && (
        <div className="mb-4">
          <LoadingState label="Desactivando…" />
        </div>
      )}
      {baja.isError && (
        <div className="mb-4">
          <ErrorState message={errorCatalogo(baja.error)} />
        </div>
      )}
      {listado.isError ? (
        <ErrorState
          message={errorCatalogo(listado.error)}
          onRetry={() => void listado.refetch()}
        />
      ) : listado.isPending || fueraDeRango ? (
        <LoadingState label={`Cargando ${titulo.toLowerCase()}…`} />
      ) : (
        <>
          <p className="mb-3 text-sm text-muted-foreground" aria-live="polite">
            {listado.data.page.totalElements.toLocaleString("es-AR")}{" "}
            {titulo.toLowerCase()} activ{genero}s
            {termino && ` · ${filas.length} en esta página`}
          </p>
          {listado.isFetching && <LoadingState label="Actualizando listado…" />}
          {listado.data.page.totalElements === 0 ? (
            <EmptyState
              title={`Todavía no hay ${titulo.toLowerCase()} activ${genero}s`}
              description="Los registros activos aparecen en este listado."
            />
          ) : (
            <>
              {filas.length === 0 && (
                <div className="mb-4">
                  <EmptyState
                    title="Sin coincidencias en esta página"
                    description="Limpiá el filtro o consultá otra página."
                    action={
                      <Button variant="outline" onClick={() => setFiltro("")}>
                        Limpiar filtro
                      </Button>
                    }
                  />
                </div>
              )}
              <DataTable
                caption={`${titulo} activ${genero}s`}
                data={filas}
                columns={columnas}
                getRowId={(fila) => String(fila.id)}
                pagination={{
                  pageIndex: listado.data.page.number,
                  totalPages: listado.data.page.totalPages,
                  onPageChange: (index) => actualizar({ page: index }),
                }}
              />
            </>
          )}
        </>
      )}
    </>
  );
}
