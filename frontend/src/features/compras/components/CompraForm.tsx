import { zodResolver } from "@hookform/resolvers/zod";
import { useRef, useState } from "react";
import {
  Controller,
  useFieldArray,
  useForm,
  useWatch,
  type FieldPath,
} from "react-hook-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router";
import { useSession } from "@/features/auth/session";
import { proveedoresApi } from "@/features/proveedores/api/proveedores";
import {
  claveInsumosSuministrados,
  opcionesIdsInsumosSuministrados,
  opcionesInsumosSuministrados,
} from "@/features/proveedores/hooks/insumosSuministrados";
import { ApiError } from "@/shared/api/http";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { useUnsavedChanges } from "@/shared/components/forms/useUnsavedChanges";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { useGuardarCompra } from "../hooks/queries";
import {
  compraSchema,
  filaVacia,
  solicitudCompra,
  subtotalEstimado,
  type CompraFormValues,
  type CompraValidada,
} from "../schemas/compra";
import type { Compra, CompraRequest } from "../types/compras";
import { CompraProveedorSelector } from "./CompraProveedorSelector";
import { CompraResumen } from "./CompraResumen";
import { DetalleCompraForm } from "./DetalleCompraForm";
import { importeEstimado, resultadoIncierto } from "./presentacion";
import { SelectorPagina } from "./SelectorPagina";

export function CompraForm({ compra }: { compra?: Compra }) {
  const { api } = useSession();
  const cache = useQueryClient();
  const navegar = useNavigate();
  const mutacion = useGuardarCompra(compra?.id);
  const [paginaInsumos, setPaginaInsumos] = useState(0);
  const [proveedorNombre, setProveedorNombre] = useState(
    compra?.proveedorNombre,
  );
  const [nombres, setNombres] = useState<Record<number, string>>(() =>
    Object.fromEntries(
      compra?.detalles.map((fila) => [fila.insumoId, fila.insumoNombre]) ?? [],
    ),
  );
  const [revision, setRevision] = useState<CompraRequest>();
  const [ocupado, setOcupado] = useState(false);
  const [incierto, setIncierto] = useState(false);
  const bloqueo = useRef(false);
  const guardado = useRef(false);
  const revisar = useRef<HTMLButtonElement>(null);
  const form = useForm<CompraFormValues, unknown, CompraValidada>({
    resolver: zodResolver(compraSchema),
    defaultValues: {
      proveedorId: compra?.proveedorId ?? null,
      numeroComprobante: compra?.numeroComprobante ?? "",
      detalles: compra?.detalles.map((fila) => ({
        detalleId: fila.id,
        insumoId: fila.insumoId,
        cantidad: String(fila.cantidad),
        precioCompra: String(fila.precioCompra),
      })) ?? [filaVacia],
    },
  });
  const { control, formState, getValues, setValue, setError, clearErrors } =
    form;
  useUnsavedChanges(formState.isDirty, bloqueo, guardado);
  const { fields, append, remove } = useFieldArray({
    control,
    name: "detalles",
  });
  const proveedorId = useWatch({ control, name: "proveedorId" });
  const detalles = useWatch({ control, name: "detalles" });
  const id = proveedorId === null ? "" : String(proveedorId);
  const insumos = useQuery({
    ...opcionesInsumosSuministrados(api, id, { page: paginaInsumos, size: 10 }),
    enabled: Boolean(id),
  });
  const opciones = (insumos.data?.content ?? []).map((fila) => ({
    id: fila.insumoId,
    nombre: fila.insumoNombre,
    activo: true,
  }));
  const total = detalles.reduce(
    (suma, fila) =>
      suma + subtotalEstimado(fila.cantidad.trim(), fila.precioCompra.trim()),
    0n,
  );
  const rutaVolver = compra ? `/compras/${compra.id}` : "/compras";

  function idsVigentes(proveedor: number) {
    return cache.fetchQuery({
      ...opcionesIdsInsumosSuministrados(api, String(proveedor)),
      staleTime: 0,
    });
  }

  async function cambiarProveedor(nuevo: number | null, nombre?: string) {
    if (bloqueo.current || nuevo === getValues("proveedorId")) return;
    bloqueo.current = true;
    setOcupado(true);
    clearErrors();
    try {
      const filas = getValues("detalles");
      const haySelecciones = filas.some((fila) => fila.insumoId !== null);
      const vigentes =
        nuevo !== null && haySelecciones
          ? new Set(await idsVigentes(nuevo))
          : new Set<number>();
      const incompatibles = filas.flatMap((fila, index) =>
        fila.insumoId !== null && !vigentes.has(fila.insumoId) ? [index] : [],
      );
      if (
        incompatibles.length &&
        !window.confirm(
          `El cambio deja ${incompatibles.length} renglón(es) sin un insumo válido. ¿Descartar esas selecciones y sus precios?`,
        )
      )
        return;
      incompatibles.forEach((index) => {
        setValue(`detalles.${index}.insumoId`, null, { shouldDirty: true });
        setValue(`detalles.${index}.precioCompra`, "", { shouldDirty: true });
      });
      setValue("proveedorId", nuevo, { shouldDirty: true });
      setProveedorNombre(nombre);
      setPaginaInsumos(0);
    } catch (error) {
      setError("root.operacion", {
        message:
          error instanceof Error
            ? error.message
            : "No pudimos verificar los insumos del proveedor.",
      });
    } finally {
      bloqueo.current = false;
      setOcupado(false);
    }
  }

  function mostrarError(error: unknown) {
    if (error instanceof ApiError) {
      for (const campo of error.fields) {
        const ruta = campo.campo.replace(/\[(\d+)\]/g, ".$1");
        if (
          /^(proveedorId|numeroComprobante|detalles\.\d+\.(insumoId|cantidad|precioCompra))$/.test(
            ruta,
          )
        ) {
          setError(ruta as FieldPath<CompraFormValues>, {
            message: campo.mensaje,
          });
        }
      }
    }
    setError("root.operacion", {
      message:
        error instanceof Error
          ? error.message
          : "La compra no se pudo guardar.",
    });
  }

  async function guardar(body: CompraRequest) {
    if (bloqueo.current || incierto) return;
    bloqueo.current = true;
    setOcupado(true);
    clearErrors();
    try {
      const [proveedor, vigentes] = await Promise.all([
        proveedoresApi.obtener(
          api,
          String(body.proveedorId),
          AbortSignal.timeout(20_000),
        ),
        idsVigentes(body.proveedorId),
      ]);
      const ids = new Set(vigentes);
      let disponible = proveedor.activo;
      if (!proveedor.activo)
        setError("proveedorId", {
          message: "El proveedor está inactivo. Seleccioná otro.",
        });
      body.detalles.forEach((fila, index) => {
        if (!ids.has(fila.insumoId)) {
          disponible = false;
          setError(`detalles.${index}.insumoId`, {
            message: "Este insumo ya no está disponible para el proveedor.",
          });
        }
      });
      if (!disponible) {
        void cache.invalidateQueries({ queryKey: ["proveedores", "listas"] });
        void cache.invalidateQueries({
          queryKey: [...claveInsumosSuministrados, String(body.proveedorId)],
        });
        return;
      }

      try {
        const confirmada = await mutacion.mutateAsync(body);
        guardado.current = true;
        navegar(`/compras/${confirmada.id}`, {
          replace: true,
          state: {
            guardada: compra ? "Cambios guardados." : "Compra registrada.",
          },
        });
      } catch (error) {
        if (resultadoIncierto(error)) setIncierto(true);
        else mostrarError(error);
        if (error instanceof ApiError && [404, 409].includes(error.status)) {
          void cache.invalidateQueries({
            queryKey: [...claveInsumosSuministrados, String(body.proveedorId)],
          });
        }
      }
    } catch (error) {
      mostrarError(error);
    } finally {
      bloqueo.current = false;
      setOcupado(false);
    }
  }

  const errores = formState.errors;
  const errorDetalles =
    errores.detalles?.root?.message ?? errores.detalles?.message;
  return (
    <>
      <form
        className="space-y-5"
        noValidate
        onSubmit={form.handleSubmit((values) => {
          if (!bloqueo.current && !incierto)
            setRevision(solicitudCompra(values));
        })}
      >
        {errores.root?.operacion?.message && (
          <ErrorState message={errores.root.operacion.message} />
        )}
        {incierto && (
          <section className="surface space-y-3 p-4">
            <ErrorState message="No pudimos confirmar el resultado. La compra podría haberse guardado. Revisá las compras antes de repetir el envío." />
            <Button asChild variant="outline">
              <Link to="/compras">Revisar listado de compras</Link>
            </Button>
          </section>
        )}
        {ocupado && (
          <LoadingState
            label={
              mutacion.isPending
                ? "Guardando compra…"
                : "Verificando disponibilidad…"
            }
          />
        )}
        <fieldset disabled={ocupado || incierto} className="space-y-5">
          <section className="surface grid gap-4 p-4 sm:grid-cols-2">
            <Controller
              name="proveedorId"
              control={control}
              render={({ field }) => (
                <CompraProveedorSelector
                  value={field.value}
                  inputRef={field.ref}
                  onBlur={field.onBlur}
                  nombre={proveedorNombre}
                  onChange={(nuevo, nombre) =>
                    void cambiarProveedor(nuevo, nombre)
                  }
                  disabled={ocupado}
                  error={errores.proveedorId?.message}
                />
              )}
            />
            <FormField
              id="compra-comprobante"
              label="Número de comprobante (opcional)"
              error={errores.numeroComprobante?.message}
            >
              <Input
                id="compra-comprobante"
                maxLength={50}
                {...form.register("numeroComprobante")}
                aria-invalid={Boolean(errores.numeroComprobante)}
                aria-describedby={
                  errores.numeroComprobante
                    ? "compra-comprobante-error"
                    : undefined
                }
              />
            </FormField>
          </section>
          <section
            className="surface space-y-4 p-4"
            aria-labelledby="compra-renglones"
          >
            <h2 id="compra-renglones" className="text-lg font-semibold">
              Renglones
            </h2>
            {!id && (
              <p className="text-sm text-muted-foreground">
                Elegí un proveedor para consultar sus insumos.
              </p>
            )}
            {id && insumos.isPending && (
              <LoadingState label="Cargando insumos del proveedor…" />
            )}
            {insumos.isError && (
              <ErrorState
                message={insumos.error.message}
                onRetry={() => void insumos.refetch()}
              />
            )}
            {insumos.data?.page.totalElements === 0 && (
              <p className="text-sm text-muted-foreground">
                El proveedor no tiene insumos vigentes.
              </p>
            )}
            <SelectorPagina
              nombre="insumos"
              page={paginaInsumos}
              total={insumos.data?.page.totalPages ?? 0}
              cambiar={setPaginaInsumos}
            />
            {fields.map((field, index) => {
              const fila = detalles[index] ?? filaVacia;
              return (
                <DetalleCompraForm
                  key={field.id}
                  form={form}
                  index={index}
                  fila={fila}
                  opciones={opciones}
                  nombre={
                    fila.insumoId === null ? undefined : nombres[fila.insumoId]
                  }
                  guardarNombre={(insumoId, nombre) =>
                    setNombres((previos) => ({
                      ...previos,
                      [insumoId]: nombre,
                    }))
                  }
                  quitar={() => remove(index)}
                  bloqueado={!id || !insumos.data || insumos.isError}
                />
              );
            })}
            {errorDetalles && <ErrorState message={errorDetalles} />}
            <Button
              type="button"
              variant="outline"
              onClick={() => append(filaVacia, { shouldFocus: true })}
            >
              Agregar renglón
            </Button>
          </section>
          <div className="surface flex flex-wrap items-center justify-between gap-3 p-4">
            <p className="font-semibold">
              Total estimado{" "}
              <span className="text-xl tabular-nums">
                {importeEstimado(total)}
              </span>
            </p>
            <div className="flex flex-wrap gap-2">
              <Button asChild variant="outline">
                <Link to={rutaVolver}>Cancelar</Link>
              </Button>
              <Button ref={revisar} type="submit">
                Revisar compra
              </Button>
            </div>
          </div>
        </fieldset>
      </form>
      {revision && (
        <CompraResumen
          compra={revision}
          proveedor={proveedorNombre ?? `proveedor #${revision.proveedorId}`}
          nombres={nombres}
          editando={Boolean(compra)}
          cerrar={() => setRevision(undefined)}
          restaurarFoco={() => revisar.current?.focus()}
          guardar={() => void guardar(revision)}
        />
      )}
    </>
  );
}
