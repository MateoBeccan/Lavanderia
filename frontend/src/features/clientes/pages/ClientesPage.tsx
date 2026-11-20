import { useEffect, useMemo, useState } from "react";
import type { ComponentProps } from "react";
import { Plus } from "lucide-react";
import { Link, useLocation, useSearchParams } from "react-router";
import { toast } from "sonner";
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
import { EstadoActivo } from "@/shared/components/EstadoActivo";
import { clienteError } from "../components/feedback";
import { useClientes, useDesactivarCliente } from "../hooks/queries";
import { ordenesClientes } from "../types/clientes";
import type { ClienteResponse } from "../types/clientes";

const tamanios = [10, 20, 50];
const etiquetasOrden = [
  "Más antiguos",
  "Más recientes",
  "Apellido A–Z",
  "Apellido Z–A",
];
const normalizar = (texto: string) =>
  texto
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLocaleLowerCase("es-AR");

export function ClientesPage() {
  const { user } = useSession();
  const { search } = useLocation();
  const [params, setParams] = useSearchParams();
  const [busqueda, setBusqueda] = useState("");
  const [filtro, setFiltro] = useState("");
  const requestedPage = Number(params.get("page"));
  const page =
    Number.isSafeInteger(requestedPage) &&
    requestedPage >= 0 &&
    requestedPage <= 2_147_483_647
      ? requestedPage
      : 0;
  const requestedSize = Number(params.get("size"));
  const size = tamanios.includes(requestedSize) ? requestedSize : 20;
  const sort =
    ordenesClientes.find((value) => value === params.get("sort")) ??
    ordenesClientes[0];
  const listado = useClientes({
    page,
    size,
    sort: sort.startsWith("apellido") ? [sort, "id,asc"] : [sort],
  });
  const baja = useDesactivarCliente();
  const metadata = listado.data?.page;
  const lastPage = Math.max(0, (metadata?.totalPages ?? 1) - 1);
  const pageOutOfRange = !!metadata && page > lastPage;

  useEffect(() => {
    const timer = setTimeout(() => setFiltro(normalizar(busqueda.trim())), 300);
    return () => clearTimeout(timer);
  }, [busqueda]);

  useEffect(() => {
    if (!pageOutOfRange) return;
    const nextParams = new URLSearchParams(params);
    nextParams.set("page", String(lastPage));
    setParams(nextParams, { replace: true });
  }, [lastPage, pageOutOfRange, params, setParams]);

  function updateParams(values: Record<string, string | number>) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(values))
      next.set(key, String(value));
    setParams(next);
  }

  const filas = useMemo(() => {
    const contenido = listado.data?.content ?? [];
    if (!filtro) return contenido;
    return contenido.filter((cliente) => {
      const { nombre, apellido, dni, telefono, email } = cliente;
      const texto = normalizar(
        [
          nombre + " " + apellido,
          apellido + " " + nombre,
          dni,
          telefono,
          email,
        ].join(" "),
      );
      return filtro.split(/\s+/).every((palabra) => texto.includes(palabra));
    });
  }, [filtro, listado.data]);

  const nuevo = { pathname: "/clientes/nuevo", search };
  const columns: ComponentProps<typeof DataTable<ClienteResponse>>["columns"] =
    [
      {
        id: "cliente",
        header: "Cliente",
        cell: ({ row: { original: cliente } }) => (
          <Link
            className="font-semibold underline underline-offset-4"
            to={{ pathname: `/clientes/${cliente.id}`, search }}
          >
            {cliente.apellido}, {cliente.nombre}
          </Link>
        ),
      },
      { id: "dni", header: "DNI", cell: ({ row }) => row.original.dni ?? "—" },
      {
        id: "telefono",
        header: "Teléfono",
        cell: ({ row }) => row.original.telefono ?? "—",
      },
      {
        id: "email",
        header: "Correo electrónico",
        cell: ({ row }) => row.original.email ?? "—",
      },
      {
        id: "estado",
        header: "Estado",
        cell: ({ row }) => <EstadoActivo activo={row.original.activo} />,
      },
      {
        id: "acciones",
        header: "Acciones",
        cell: ({ row: { original: cliente } }) => {
          const nombre = cliente.nombre + " " + cliente.apellido;
          const id = String(cliente.id);
          return (
            <div className="flex flex-wrap gap-2">
              <Button asChild variant="outline">
                <Link
                  to={{ pathname: `/clientes/${id}/editar`, search }}
                  aria-label={`Editar a ${nombre}`}
                >
                  Editar
                </Link>
              </Button>
              {user?.rol === "ADMINISTRADOR" && cliente.activo && (
                <ConfirmDialog
                  trigger={
                    <Button
                      variant="outline"
                      disabled={baja.isPending}
                      aria-label={`Desactivar a ${nombre}`}
                    >
                      {baja.isPending && baja.variables === id
                        ? "Desactivando…"
                        : "Desactivar"}
                    </Button>
                  }
                  title="Desactivar cliente"
                  description={`${nombre} dejará de aparecer en el listado de clientes activos.`}
                  confirmLabel="Desactivar cliente"
                  onConfirm={() =>
                    baja.mutate(id, {
                      onSuccess: () => toast.success("Cliente desactivado."),
                    })
                  }
                />
              )}
            </div>
          );
        },
      },
    ];

  return (
    <>
      <PageHeader
        title="Clientes"
        description="Consultá y gestioná los clientes activos."
        action={
          <Button asChild>
            <Link to={nuevo}>
              <Plus aria-hidden="true" />
              Nuevo cliente
            </Link>
          </Button>
        }
      />
      <section
        aria-label="Controles del listado"
        className="surface mb-4 grid gap-4 p-4 sm:grid-cols-[minmax(0,1fr)_auto_auto]"
      >
        <FormField id="clientes-busqueda" label="Buscar en esta página">
          <Input
            id="clientes-busqueda"
            type="search"
            value={busqueda}
            onChange={(event) => setBusqueda(event.target.value)}
            placeholder="Nombre, DNI, teléfono o correo"
          />
        </FormField>
        <FormField id="clientes-orden" label="Ordenar por">
          <select
            id="clientes-orden"
            className="h-10 rounded-lg border bg-background px-3 text-sm"
            value={sort}
            onChange={(event) =>
              updateParams({ sort: event.target.value, page: 0 })
            }
          >
            {ordenesClientes.map((value, index) => (
              <option value={value} key={value}>
                {etiquetasOrden[index]}
              </option>
            ))}
          </select>
        </FormField>
        <FormField id="clientes-tamanio" label="Por página">
          <select
            id="clientes-tamanio"
            className="h-10 rounded-lg border bg-background px-3 text-sm"
            value={size}
            onChange={(event) =>
              updateParams({ size: event.target.value, page: 0 })
            }
          >
            {tamanios.map((value) => (
              <option value={value} key={value}>
                {value}
              </option>
            ))}
          </select>
        </FormField>
      </section>
      {baja.isError && (
        <div className="mb-4">
          <ErrorState message={clienteError(baja.error)} />
        </div>
      )}
      {listado.isError ? (
        <ErrorState
          message={clienteError(listado.error)}
          onRetry={() => void listado.refetch()}
        />
      ) : listado.isPending || pageOutOfRange ? (
        <LoadingState label="Cargando clientes…" />
      ) : (
        <>
          <div className="mb-3 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
            <p aria-live="polite">
              {listado.data.page.totalElements.toLocaleString("es-AR")}{" "}
              {listado.data.page.totalElements === 1
                ? "cliente activo"
                : "clientes activos"}
              {filtro &&
                ` · ${filas.length} ${filas.length === 1 ? "coincidencia" : "coincidencias"} en esta página`}
            </p>
            {listado.isFetching && (
              <LoadingState label="Actualizando clientes…" />
            )}
          </div>
          {listado.data.page.totalElements === 0 ? (
            <EmptyState
              title="Todavía no hay clientes activos"
              description="Registrá un cliente para empezar."
            />
          ) : (
            <>
              {filas.length === 0 && (
                <div className="mb-4">
                  <EmptyState
                    title="Sin coincidencias en esta página"
                    description="Podés limpiar la búsqueda o consultar otra página."
                    action={
                      <Button
                        variant="outline"
                        onClick={() => {
                          setBusqueda("");
                          setFiltro("");
                        }}
                      >
                        Limpiar búsqueda
                      </Button>
                    }
                  />
                </div>
              )}
              <DataTable
                caption="Clientes activos"
                data={filas}
                columns={columns}
                getRowId={(cliente) => String(cliente.id)}
                pagination={{
                  pageIndex: listado.data.page.number,
                  totalPages: listado.data.page.totalPages,
                  onPageChange: (index) => updateParams({ page: index }),
                }}
              />
            </>
          )}
        </>
      )}
    </>
  );
}
