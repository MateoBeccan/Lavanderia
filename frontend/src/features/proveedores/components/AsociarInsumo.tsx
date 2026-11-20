import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useId, useState } from "react";
import { toast } from "sonner";
import { useSession } from "@/features/auth/session";
import {
  opcionesInsumos,
  useCategoriasInsumo,
} from "@/features/insumos/hooks/queries";
import { ApiError } from "@/shared/api/http";
import { SelectorCatalogo } from "@/shared/catalogos/SelectorCatalogo";
import { errorCatalogo } from "@/shared/catalogos/feedback";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import {
  useCambiarInsumoSuministrado,
  useIdsInsumosSuministrados,
} from "../hooks/insumosSuministrados";

export function AsociarInsumo({
  proveedorId,
  onClose,
}: {
  proveedorId: string;
  onClose: () => void;
}) {
  const { api } = useSession();
  const controlId = useId();
  const [page, setPage] = useState(0);
  const [seleccion, setSeleccion] = useState<{
    id: number;
    nombre: string;
    categoriaId: number;
  } | null>(null);
  const lista = useQuery({
    ...opcionesInsumos(api, { page, size: 10 }),
    placeholderData: keepPreviousData,
  });
  const asociados = useIdsInsumosSuministrados(proveedorId);
  const categorias = useCategoriasInsumo();
  const mutation = useCambiarInsumoSuministrado(proveedorId);
  const ids = new Set(asociados.data);
  const categoriasActivas = new Set(
    categorias.data?.filter(({ activo }) => activo).map(({ id }) => id),
  );
  const opciones =
    lista.data?.content
      .filter(
        ({ id, activo, categoriaId }) =>
          activo && categoriasActivas.has(categoriaId) && !ids.has(id),
      )
      .map((insumo) => ({
        ...insumo,
        nombre: `${insumo.nombre} · ${insumo.categoriaNombre} · ID ${insumo.id}`,
      })) ?? [];
  const disponible =
    lista.isSuccess &&
    !lista.isPlaceholderData &&
    asociados.isSuccess &&
    categorias.isSuccess;
  const seleccionActual =
    seleccion &&
    categoriasActivas.has(seleccion.categoriaId) &&
    !ids.has(seleccion.id)
      ? seleccion
      : null;
  const error = mutation.isError ? errorCatalogo(mutation.error) : undefined;
  const totalPages = lista.data?.page.totalPages ?? 0;
  const ultima = Math.max(0, totalPages - 1);

  return (
    <form
      className="surface space-y-4 p-4"
      onSubmit={(event) => {
        event.preventDefault();
        if (!seleccionActual || !disponible || mutation.isPending) return;
        mutation.mutate(
          { tipo: "asociar", insumoId: seleccionActual.id },
          {
            onSuccess: () => {
              setSeleccion(null);
              toast.success("Insumo asociado.");
            },
            onError: (error) => {
              if (
                error instanceof ApiError &&
                [404, 409].includes(error.status)
              ) {
                setSeleccion(null);
                void categorias.refetch();
                void lista.refetch().then(({ data }) => {
                  if (data)
                    setPage((actual) =>
                      Math.min(actual, Math.max(0, data.page.totalPages - 1)),
                    );
                });
              }
            },
          },
        );
      }}
    >
      <FormField id={controlId} label="Insumo para asociar" error={error}>
        <SelectorCatalogo
          id={controlId}
          opciones={disponible ? opciones : []}
          value={seleccionActual?.id ?? null}
          nombreSeleccionado={seleccionActual?.nombre}
          disabled={!disponible || mutation.isPending}
          aria-invalid={!!error}
          aria-describedby={error && controlId + "-error"}
          onChange={(id) => {
            mutation.reset();
            setSeleccion(opciones.find((opcion) => opcion.id === id) ?? null);
          }}
        />
      </FormField>
      {(lista.isPending ||
        lista.isPlaceholderData ||
        asociados.isPending ||
        categorias.isPending) && (
        <LoadingState label="Cargando insumos disponibles…" />
      )}
      {lista.isError && (
        <ErrorState
          message={errorCatalogo(lista.error)}
          onRetry={() => void lista.refetch()}
        />
      )}
      {asociados.isError && (
        <ErrorState
          message={errorCatalogo(asociados.error)}
          onRetry={() => void asociados.refetch()}
        />
      )}
      {categorias.isError && (
        <ErrorState
          message={errorCatalogo(categorias.error)}
          onRetry={() => void categorias.refetch()}
        />
      )}
      {disponible && opciones.length === 0 && (
        <p className="text-sm text-muted-foreground">
          Sin insumos disponibles en esta página.
        </p>
      )}
      {(totalPages > 1 || page > 0) && (
        <nav
          aria-label="Páginas de insumos disponibles"
          className="flex flex-wrap items-center gap-2"
        >
          <Button
            variant="outline"
            type="button"
            disabled={
              page === 0 || mutation.isPending || lista.isPlaceholderData
            }
            onClick={() => setPage(Math.min(page - 1, ultima))}
          >
            Anterior
          </Button>
          <span aria-live="polite" className="text-sm">
            Página {Math.min(page, ultima) + 1} de {Math.max(1, totalPages)}
          </span>
          <Button
            variant="outline"
            type="button"
            disabled={
              page >= ultima || mutation.isPending || lista.isPlaceholderData
            }
            onClick={() => setPage(page + 1)}
          >
            Siguiente
          </Button>
        </nav>
      )}
      <div className="flex flex-wrap gap-2">
        <Button
          type="submit"
          disabled={!seleccionActual || !disponible || mutation.isPending}
        >
          {mutation.isPending ? "Asociando…" : "Confirmar asociación"}
        </Button>
        <Button
          type="button"
          variant="outline"
          onClick={onClose}
          disabled={mutation.isPending}
        >
          Cerrar selector
        </Button>
      </div>
    </form>
  );
}
