import { zodResolver } from "@hookform/resolvers/zod";
import { useRef } from "react";
import { Controller, useForm } from "react-hook-form";
import { Link, useLocation, useNavigate } from "react-router";
import { toast } from "sonner";
import { z } from "zod";
import { ApiError } from "@/shared/api/http";
import { SelectorCatalogo } from "@/shared/catalogos/SelectorCatalogo";
import { errorCatalogo } from "@/shared/catalogos/feedback";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { useUnsavedChanges } from "@/shared/components/forms/useUnsavedChanges";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import {
  useCambiarRegistroInsumos,
  useCategoriasInsumo,
} from "../hooks/queries";
import { categoriaInsumoSchema, insumoSchema } from "../schemas/insumos";
import type {
  CategoriaInsumo,
  CategoriaInsumoRequest,
  Insumo,
  InsumoRequest,
  TipoCatalogoInsumos,
} from "../types/insumos";
import { catalogo } from "./catalogo";

type Valores = { nombre: string; categoriaId: number | null };
const resolverInsumo = zodResolver(insumoSchema);
const resolverCategoria = zodResolver(
  categoriaInsumoSchema
    .extend({ categoriaId: z.number().nullable() })
    .transform(({ nombre }) => ({ nombre })),
);

export function CatalogoInsumosForm({
  tipo,
  registro,
}: {
  tipo: TipoCatalogoInsumos;
  registro?: CategoriaInsumo | Insumo;
}) {
  const esInsumo = tipo === "insumo";
  const insumo = registro && "categoriaId" in registro ? registro : undefined;
  const path = catalogo[tipo].path;
  const id = registro && String(registro.id);
  const volver = id ? `${path}/${id}` : path;
  const { search } = useLocation();
  const navigate = useNavigate();
  const categorias = useCategoriasInsumo(esInsumo);
  const mutation = useCambiarRegistroInsumos(tipo);
  const enviando = useRef(false);
  const guardado = useRef(false);
  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<Valores, unknown, CategoriaInsumoRequest | InsumoRequest>({
    resolver: esInsumo ? resolverInsumo : resolverCategoria,
    defaultValues: {
      nombre: registro?.nombre ?? "",
      categoriaId: insumo?.categoriaId ?? null,
    },
  });
  useUnsavedChanges(isDirty, enviando, guardado);

  async function submit(body: CategoriaInsumoRequest | InsumoRequest) {
    if (enviando.current) return;
    if (
      "categoriaId" in body &&
      !categorias.data?.some(
        ({ id, activo }) => id === body.categoriaId && activo,
      )
    ) {
      setError(
        "categoriaId",
        { message: "Seleccioná una categoría activa." },
        { shouldFocus: true },
      );
      return;
    }
    enviando.current = true;
    try {
      const saved = await mutation.mutateAsync({ tipo: "guardar", id, body });
      if (!saved) throw new Error("Falta el registro guardado.");
      guardado.current = true;
      toast.success(id ? "Cambios guardados." : "Registro creado.");
      await navigate(
        { pathname: `${path}/${saved.id}`, search },
        { replace: true },
      );
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 409 && error.message.includes("con ese nombre")) {
          setError("nombre", { message: error.message });
          return;
        }
        if (
          esInsumo &&
          [404, 409].includes(error.status) &&
          error.message.includes("categoría")
        ) {
          setError("categoriaId", { message: error.message });
          void categorias.refetch();
          return;
        }
        const fields = error.fields.filter(
          ({ campo }) =>
            campo === "nombre" || (esInsumo && campo === "categoriaId"),
        );
        for (const { campo, mensaje } of fields) {
          setError(campo as keyof Valores, { message: mensaje });
        }
        if (fields.length && fields.length === error.fields.length) return;
      }
      setError("root", { message: errorCatalogo(error) });
    } finally {
      enviando.current = false;
    }
  }

  return (
    <form
      noValidate
      onSubmit={handleSubmit(submit)}
      className="surface p-4 sm:p-6"
    >
      <fieldset
        disabled={isSubmitting}
        className="grid min-w-0 gap-5 sm:grid-cols-2"
      >
        <FormField id="nombre" label="Nombre" error={errors.nombre?.message}>
          <Input
            id="nombre"
            required
            aria-invalid={!!errors.nombre}
            aria-describedby={errors.nombre && "nombre-error"}
            {...register("nombre")}
          />
        </FormField>
        {esInsumo && (
          <Controller
            control={control}
            name="categoriaId"
            render={({ field, fieldState }) => (
              <FormField
                id="categoriaId"
                label="Categoría"
                error={fieldState.error?.message}
              >
                <SelectorCatalogo
                  {...field}
                  id="categoriaId"
                  value={field.value ?? null}
                  required
                  opciones={categorias.data ?? []}
                  nombreSeleccionado={insumo?.categoriaNombre}
                  disabled={!categorias.isSuccess}
                  aria-invalid={!!fieldState.error}
                  aria-describedby={fieldState.error && "categoriaId-error"}
                />
              </FormField>
            )}
          />
        )}
      </fieldset>
      {esInsumo && categorias.isPending && (
        <LoadingState label="Cargando categorías…" />
      )}
      {esInsumo && categorias.isError && (
        <ErrorState
          message={errorCatalogo(categorias.error)}
          onRetry={() => void categorias.refetch()}
        />
      )}
      {esInsumo && categorias.isSuccess && categorias.data.length === 0 && (
        <p className="mt-4 text-sm">
          Creá una{" "}
          <Link
            className="underline underline-offset-4"
            to="/insumos/categorias/nuevo"
          >
            categoría de insumo
          </Link>{" "}
          para continuar.
        </p>
      )}
      {errors.root?.message && (
        <div className="mt-5">
          <ErrorState message={errors.root.message} />
        </div>
      )}
      <div className="mt-6 flex flex-wrap gap-3">
        <Button
          type="submit"
          disabled={
            isSubmitting ||
            (esInsumo && (!categorias.isSuccess || !categorias.data.length))
          }
        >
          {isSubmitting ? "Guardando…" : `Guardar ${tipo}`}
        </Button>
        <Button variant="outline" asChild>
          <Link to={{ pathname: volver, search }}>Cancelar</Link>
        </Button>
      </div>
    </form>
  );
}
