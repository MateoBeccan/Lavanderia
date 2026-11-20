import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import type { ComponentProps, ReactNode } from "react";
import { Link, useLocation, useSearchParams } from "react-router";
import { useSession } from "@/features/auth/session";
import { SelectorCatalogo } from "@/shared/catalogos/SelectorCatalogo";
import { selectCatalogo } from "@/shared/catalogos/config";
import { errorCatalogo } from "@/shared/catalogos/feedback";
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
import { catalogo, porNombre, tamaniosInsumos } from "../components/catalogo";
import { opcionesInsumos, useCategoriasInsumo } from "../hooks/queries";
import type {
  CategoriaInsumo,
  Insumo,
  TipoCatalogoInsumos,
} from "../types/insumos";

function EncabezadoListado({
  tipo,
  children,
}: {
  tipo: TipoCatalogoInsumos;
  children: ReactNode;
}) {
  const { user } = useSession();
  const { search } = useLocation();
  const { path, nuevo, titulo } = catalogo[tipo];
  return (
    <>
      <PageHeader
        title={titulo}
        action={
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline">
              {tipo === "insumo" ? (
                <Link to={catalogo.categoría.path}>Categorías de insumos</Link>
              ) : (
                <Link to={catalogo.insumo.path}>Volver a insumos</Link>
              )}
            </Button>
            {user?.rol === "ADMINISTRADOR" && (
              <>
                <Button asChild variant="outline">
                  <Link to={{ pathname: path + "/reactivar", search }}>
                    Reactivar {tipo}
                  </Link>
                </Button>
                <Button asChild>
                  <Link to={{ pathname: path + "/nuevo", search }}>
                    {nuevo}
                  </Link>
                </Button>
              </>
            )}
          </div>
        }
      />
      {children}
    </>
  );
}

function TablaCatalogoInsumos({
  tipo,
  filas,
  pagination,
}: {
  tipo: TipoCatalogoInsumos;
  filas: (CategoriaInsumo | Insumo)[];
  pagination?: ComponentProps<
    typeof DataTable<CategoriaInsumo | Insumo>
  >["pagination"];
}) {
  const { search } = useLocation();
  const { path, titulo } = catalogo[tipo];
  const columns: ComponentProps<
    typeof DataTable<CategoriaInsumo | Insumo>
  >["columns"] = [
    { accessorKey: "id", header: "ID" },
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
    {
      id: "estado",
      header: "Estado",
      cell: ({ row }) => <EstadoActivo activo={row.original.activo} />,
    },
  ];
  if (tipo === "insumo")
    columns.splice(2, 0, {
      accessorKey: "categoriaNombre",
      header: "Categoría",
    });
  return filas.length ? (
    <DataTable
      caption={titulo}
      data={filas}
      columns={columns}
      getRowId={(fila) => String(fila.id)}
      pagination={pagination}
    />
  ) : (
    <EmptyState
      title={
        tipo === "insumo" ? "Sin insumos activos" : "Sin categorías activas"
      }
      description="Los registros activos que coincidan con la consulta aparecerán acá."
    />
  );
}

export function CategoriasInsumoPage() {
  const categorias = useCategoriasInsumo();
  return (
    <EncabezadoListado tipo="categoría">
      {categorias.isPending ? (
        <LoadingState label="Cargando categorías…" />
      ) : categorias.isError ? (
        <ErrorState
          message={errorCatalogo(categorias.error)}
          onRetry={() => void categorias.refetch()}
        />
      ) : (
        <TablaCatalogoInsumos tipo="categoría" filas={categorias.data} />
      )}
    </EncabezadoListado>
  );
}

export function InsumosPage() {
  const { api } = useSession();
  const [params, setParams] = useSearchParams();
  const solicitado = Number(params.get("page"));
  const page =
    Number.isInteger(solicitado) && solicitado >= 0 && solicitado <= 2147483647
      ? solicitado
      : 0;
  const size =
    tamaniosInsumos.find((valor) => valor === Number(params.get("size"))) ?? 20;
  const orden = params.get("orden") === "nombre" ? "nombre" : "id";
  const filtro = Number(params.get("categoriaId"));
  const categoriaId =
    Number.isSafeInteger(filtro) && filtro > 0 ? filtro : null;
  const categorias = useCategoriasInsumo();
  const listado = useQuery(
    opcionesInsumos(api, {
      page,
      size,
      categoriaId: categoriaId ? String(categoriaId) : undefined,
    }),
  );
  const ultima = Math.max(0, (listado.data?.page.totalPages ?? 1) - 1);
  const fueraDeRango = listado.isSuccess && page > ultima;
  useEffect(() => {
    if (!fueraDeRango) return;
    const next = new URLSearchParams(params);
    next.set("page", String(ultima));
    setParams(next, { replace: true });
  }, [fueraDeRango, params, setParams, ultima]);

  function actualizar(values: Record<string, string | number | null>) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(values)) {
      if (value === null) next.delete(key);
      else next.set(key, String(value));
    }
    setParams(next);
  }

  return (
    <EncabezadoListado tipo="insumo">
      <section
        aria-label="Controles del listado"
        className="surface mb-4 grid gap-4 p-4 sm:grid-cols-[minmax(0,1fr)_auto_auto]"
      >
        <FormField id="filtro-categoria" label="Filtrar por categoría">
          <SelectorCatalogo
            id="filtro-categoria"
            opciones={categorias.data ?? []}
            value={categoriaId}
            nombreSeleccionado={
              categoriaId ? `Categoría · ID ${categoriaId}` : undefined
            }
            placeholder="Todas las categorías"
            onChange={(id) => actualizar({ categoriaId: id, page: 0 })}
          />
        </FormField>
        <FormField id="insumos-orden" label="Orden en esta página">
          <select
            id="insumos-orden"
            className={selectCatalogo}
            value={orden}
            onChange={(event) => actualizar({ orden: event.target.value })}
          >
            <option value="id">ID ascendente (orden del servidor)</option>
            <option value="nombre">Nombre A–Z</option>
          </select>
        </FormField>
        <FormField id="insumos-tamanio" label="Por página">
          <select
            id="insumos-tamanio"
            className={selectCatalogo}
            value={size}
            onChange={(event) =>
              actualizar({ size: event.target.value, page: 0 })
            }
          >
            {tamaniosInsumos.map((valor) => (
              <option key={valor}>{valor}</option>
            ))}
          </select>
        </FormField>
      </section>
      {categorias.isPending && <LoadingState label="Cargando categorías…" />}
      {categorias.isError && (
        <ErrorState
          message={errorCatalogo(categorias.error)}
          onRetry={() => void categorias.refetch()}
        />
      )}
      {listado.isError ? (
        <ErrorState
          message={errorCatalogo(listado.error)}
          onRetry={() => void listado.refetch()}
        />
      ) : listado.isPending || fueraDeRango ? (
        <LoadingState label="Cargando insumos…" />
      ) : (
        <>
          <p className="mb-3 text-sm text-muted-foreground" aria-live="polite">
            {listado.data.page.totalElements}{" "}
            {listado.data.page.totalElements === 1
              ? "insumo activo"
              : "insumos activos"}
          </p>
          <TablaCatalogoInsumos
            tipo="insumo"
            filas={
              orden === "nombre"
                ? [...listado.data.content].sort(porNombre)
                : listado.data.content
            }
            pagination={{
              pageIndex: listado.data.page.number,
              totalPages: listado.data.page.totalPages,
              onPageChange: (index) => actualizar({ page: index }),
            }}
          />
        </>
      )}
    </EncabezadoListado>
  );
}
