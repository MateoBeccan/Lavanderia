import { zodResolver } from "@hookform/resolvers/zod";
import { AlertDialog } from "radix-ui";
import { useEffect, useRef, useState } from "react";
import {
  Controller,
  FormProvider,
  useFieldArray,
  useForm,
  useWatch,
} from "react-hook-form";
import type { FieldPath } from "react-hook-form";
import {
  Link,
  useBeforeUnload,
  useBlocker,
  useLocation,
  useNavigate,
} from "react-router";
import { toast } from "sonner";
import { ApiError } from "@/shared/api/http";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { useModificarPedido } from "../hooks/queries";
import {
  errorPedido,
  fechaPedido,
  hoyPedidos,
  importePedido,
  resultadoIncierto,
} from "../rules";
import {
  lineaVacia,
  pedidoSchema,
  prepararPedido,
  valoresPedido,
} from "../schemas/pedido";
import type { PedidoValores, PedidoValidado } from "../schemas/pedido";
import type { Pedido } from "../types/pedidos";
import { PedidoDetalleForm } from "./PedidoDetalleForm";
import { PedidoClienteSelector } from "./SelectorPedido";

const camposServidor =
  /^(clienteId|fechaEntregaEstimada|detalles|detalles\.\d+\.(prendaId|servicioId|cantidad|estadoPrenda|observaciones))$/;

export function PedidoForm({
  pedido,
  autorizado = true,
}: {
  pedido?: Pedido;
  autorizado?: boolean;
}) {
  const { search } = useLocation();
  const navigate = useNavigate();
  const enviando = useRef(false);
  const guardado = useRef(false);
  const revisar = useRef<HTMLButtonElement>(null);
  const [revision, setRevision] = useState<PedidoValidado | null>(null);
  const [incierto, setIncierto] = useState(false);
  const mutation = useModificarPedido();
  const form = useForm<PedidoValores, unknown, PedidoValidado>({
    resolver: zodResolver(pedidoSchema(!!pedido)),
    defaultValues: valoresPedido(pedido),
  });
  const {
    control,
    handleSubmit,
    register,
    setError,
    setValue,
    formState: { errors, isDirty },
  } = form;
  const { fields, append, remove } = useFieldArray({
    control,
    name: "detalles",
  });
  const ocupado = mutation.isPending;
  const blocker = useBlocker(
    () => !guardado.current && (isDirty || enviando.current || incierto),
  );
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (
      !enviando.current &&
      window.confirm("¿Salir del formulario y descartar los cambios?")
    )
      blocker.proceed();
    else blocker.reset();
  }, [blocker]);
  useBeforeUnload((event) => {
    if (guardado.current || (!isDirty && !enviando.current && !incierto))
      return;
    event.preventDefault();
    event.returnValue = true;
  });

  async function confirmar() {
    if (!revision || enviando.current || incierto || !autorizado) return;
    enviando.current = true;
    setRevision(null);
    try {
      const resultado = await mutation.mutateAsync({
        body: prepararPedido(revision),
        id: pedido?.id,
      });
      guardado.current = true;
      toast.success(pedido ? "Pedido actualizado." : "Pedido registrado.");
      await navigate(
        { pathname: `/pedidos/${resultado.id}`, search },
        { replace: true },
      );
    } catch (error) {
      if (resultadoIncierto(error)) {
        setIncierto(true);
        return;
      }
      if (error instanceof ApiError) {
        for (const { campo, mensaje } of error.fields) {
          const ruta = campo.replace(/\[(\d+)\]/g, ".$1");
          if (camposServidor.test(ruta))
            setError(ruta as FieldPath<PedidoValores>, { message: mensaje });
        }
        if (pedido && error.status === 409) toast.error(error.message);
      }
      setError("root", { message: errorPedido(error) });
    } finally {
      enviando.current = false;
    }
  }

  const volver = {
    pathname: pedido ? `/pedidos/${pedido.id}` : "/pedidos",
    search,
  };
  const cliente = useWatch({ control, name: "clienteNombre" });
  return (
    <>
      <FormProvider {...form}>
        <form
          className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-5 xl:grid-cols-[minmax(0,1fr)_17rem]"
          noValidate
          onSubmit={handleSubmit((valores) => {
            if (!enviando.current && !incierto && autorizado)
              setRevision(valores);
          })}
        >
          <fieldset
            disabled={ocupado || incierto}
            className="surface grid min-w-0 gap-5 p-4 sm:p-6"
          >
            <div className="grid gap-5 sm:grid-cols-2">
              {pedido ? (
                <dl>
                  <dt className="text-sm text-muted-foreground">Cliente</dt>
                  <dd>
                    {pedido.clienteNombre} {pedido.clienteApellido}
                  </dd>
                </dl>
              ) : (
                <FormField
                  id="pedido-cliente"
                  label="Cliente"
                  error={errors.clienteId?.message}
                >
                  <Controller
                    name="clienteId"
                    control={control}
                    render={({ field }) => (
                      <PedidoClienteSelector
                        id="pedido-cliente"
                        ref={field.ref}
                        value={field.value}
                        onBlur={field.onBlur}
                        nombreSeleccionado={cliente}
                        aria-invalid={!!errors.clienteId}
                        aria-describedby={
                          errors.clienteId ? "pedido-cliente-error" : undefined
                        }
                        onSelect={(opcion) => {
                          setValue("clienteNombre", opcion?.nombre ?? "");
                          field.onChange(opcion?.id ?? null);
                        }}
                      />
                    )}
                  />
                </FormField>
              )}
              <FormField
                id="entrega"
                label="Entrega estimada"
                error={errors.fechaEntregaEstimada?.message}
              >
                <Input
                  id="entrega"
                  type="date"
                  min={hoyPedidos()}
                  {...register("fechaEntregaEstimada")}
                  aria-invalid={!!errors.fechaEntregaEstimada}
                  aria-describedby={
                    errors.fechaEntregaEstimada ? "entrega-error" : undefined
                  }
                />
              </FormField>
            </div>
            <div className="grid gap-4">
              {fields.map(({ id }, index) => (
                <PedidoDetalleForm
                  key={id}
                  index={index}
                  quitar={fields.length > 1 ? () => remove(index) : undefined}
                />
              ))}
            </div>
            {errors.detalles?.root?.message && (
              <ErrorState message={errors.detalles.root.message} />
            )}
            <Button
              className="justify-self-start"
              type="button"
              variant="outline"
              onClick={() => append({ ...lineaVacia }, { shouldFocus: false })}
            >
              Agregar renglón
            </Button>
          </fieldset>
          <section
            aria-labelledby="resumen-pedido"
            className="surface grid content-start gap-4 self-start p-4 xl:sticky xl:top-4"
          >
            <h2 id="resumen-pedido" className="font-semibold">
              Resumen
            </h2>
            <p>
              {fields.length} {fields.length === 1 ? "renglón" : "renglones"}
            </p>
            {pedido && (
              <p>
                Total registrado <strong>{importePedido(pedido.total)}</strong>
              </p>
            )}
            <p className="text-sm text-muted-foreground">
              El total se confirma al guardar.
            </p>
            {errors.root?.message && (
              <ErrorState message={errors.root.message} />
            )}
            {ocupado && <LoadingState label="Guardando pedido…" />}
            {incierto && (
              <div className="grid gap-2">
                <ErrorState message="No pudimos confirmar el guardado. Verificá si el pedido se registró o actualizó antes de volver a enviar." />
                <Link
                  className="underline"
                  to={pedido ? volver : "/pedidos?sort=id,desc"}
                  target="_blank"
                  rel="noopener"
                >
                  Verificar en otra pestaña
                </Link>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIncierto(false)}
                >
                  Ya verifiqué el resultado
                </Button>
              </div>
            )}
            {!autorizado && (
              <ErrorState message="No pudimos verificar que el pedido siga habilitado para edición." />
            )}
            <Button
              ref={revisar}
              type="submit"
              disabled={ocupado || incierto || !autorizado}
            >
              Revisar pedido
            </Button>
            {!ocupado && (
              <Button asChild variant="outline">
                <Link to={volver}>Salir del formulario</Link>
              </Button>
            )}
          </section>
        </form>
      </FormProvider>
      <AlertDialog.Root
        open={!!revision}
        onOpenChange={(open) => {
          if (!open) setRevision(null);
        }}
      >
        <AlertDialog.Portal>
          <AlertDialog.Overlay className="overlay" />
          <AlertDialog.Content
            className="dialog max-h-[90dvh] overflow-y-auto"
            onCloseAutoFocus={(event) => {
              event.preventDefault();
              revisar.current?.focus();
            }}
          >
            <AlertDialog.Title className="text-xl font-bold">
              Confirmar {pedido ? "edición" : "registro"}
            </AlertDialog.Title>
            <AlertDialog.Description className="mt-2 text-muted-foreground">
              {revision?.clienteNombre} · Entrega{" "}
              {revision && fechaPedido(revision.fechaEntregaEstimada)}
            </AlertDialog.Description>
            <ul className="my-4 grid gap-2">
              {revision?.detalles.map((linea, index) => (
                <li key={index} className="rounded-md border p-2 wrap-anywhere">
                  <p>
                    {linea.prendaNombre} · {linea.servicioNombre} ·{" "}
                    {linea.cantidad.toLocaleString("es-AR")}
                    {linea.modalidadCobro === "KILO" ? " kg" : ""}
                  </p>
                  {linea.estadoPrenda && (
                    <p className="text-sm">
                      Estado de la prenda · {linea.estadoPrenda}
                    </p>
                  )}
                  {linea.observaciones && (
                    <p className="text-sm">{linea.observaciones}</p>
                  )}
                </li>
              ))}
            </ul>
            <div className="flex flex-wrap justify-end gap-2">
              <AlertDialog.Cancel asChild>
                <Button variant="outline">Volver a editar</Button>
              </AlertDialog.Cancel>
              <AlertDialog.Action asChild>
                <Button disabled={!autorizado} onClick={() => void confirmar()}>
                  Guardar pedido
                </Button>
              </AlertDialog.Action>
            </div>
          </AlertDialog.Content>
        </AlertDialog.Portal>
      </AlertDialog.Root>
    </>
  );
}
