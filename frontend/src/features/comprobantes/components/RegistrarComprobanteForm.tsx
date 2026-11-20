import { useRef, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { AlertDialog } from "radix-ui";
import { useForm } from "react-hook-form";
import { importeCaja } from "@/features/caja/components/formato";
import { fechaPedido, hoyPedidos } from "@/features/pedidos/rules";
import { ApiError } from "@/shared/api/http";
import { ErrorState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { useRegistrarComprobante } from "../hooks/queries";
import {
  comprobanteSchema,
  type CamposComprobante,
} from "../schemas/comprobante";
import type {
  ReferenciaComprobante,
  SolicitudComprobante,
} from "../types/comprobantes";
import { nombreTipo, requiereConsulta } from "./presentacion";

export function RegistrarComprobanteForm({
  operacion,
  consultando,
  volverAConsultarSinRegistro,
}: {
  operacion: ReferenciaComprobante;
  consultando: boolean;
  volverAConsultarSinRegistro: () => Promise<boolean>;
}) {
  const alta = useRegistrarComprobante(operacion);
  const [revision, revisar] = useState<SolicitudComprobante>();
  const [mensaje, mostrarMensaje] = useState("");
  const enCurso = useRef(false);
  const botonRevision = useRef<HTMLButtonElement>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<CamposComprobante, unknown, SolicitudComprobante>({
    resolver: zodResolver(comprobanteSchema),
    defaultValues: { fechaEmision: hoyPedidos(), observaciones: "" },
  });
  const bloquearAlta = alta.isError && requiereConsulta(alta.error);
  const ocupado = alta.isPending || consultando;

  async function confirmar() {
    if (!revision || ocupado || bloquearAlta || enCurso.current) return;
    enCurso.current = true;
    revisar(undefined);
    mostrarMensaje("");
    try {
      await alta.mutateAsync(revision);
    } catch (error) {
      if (requiereConsulta(error)) {
        mostrarMensaje(
          error instanceof ApiError && error.status === 409
            ? "La operación ya tiene un comprobante. Consultalo para abrir el registro existente."
            : "No pudimos confirmar el registro. Consultá la operación para verificar si se guardó.",
        );
      } else if (error instanceof ApiError) {
        mostrarMensaje(error.message);
        for (const { campo, mensaje } of error.fields) {
          if (campo === "fechaEmision" || campo === "observaciones") {
            setError(campo, { message: mensaje });
          }
        }
      }
    } finally {
      enCurso.current = false;
    }
  }

  return (
    <>
      {mensaje && <ErrorState message={mensaje} />}
      {bloquearAlta ? (
        <Button
          variant="outline"
          className="justify-self-start"
          disabled={consultando}
          onClick={async () => {
            if (await volverAConsultarSinRegistro()) {
              mostrarMensaje(
                "Todavía no aparece un comprobante. Volvé a consultar antes de intentar otro registro.",
              );
            }
          }}
        >
          {consultando ? "Consultando…" : "Consultar comprobante"}
        </Button>
      ) : (
        <form
          noValidate
          className="grid max-w-xl gap-4"
          onSubmit={handleSubmit(revisar)}
        >
          <fieldset disabled={ocupado} className="grid min-w-0 gap-4">
            <FormField
              id="fecha-emision"
              label="Fecha de emisión"
              error={errors.fechaEmision?.message}
            >
              <Input
                id="fecha-emision"
                type="date"
                min="0001-01-01"
                max={hoyPedidos()}
                required
                aria-invalid={!!errors.fechaEmision}
                aria-describedby={
                  errors.fechaEmision ? "fecha-emision-error" : undefined
                }
                {...register("fechaEmision")}
              />
            </FormField>
            <FormField
              id="observaciones-comprobante"
              label="Observaciones (opcional)"
              error={errors.observaciones?.message}
            >
              <textarea
                id="observaciones-comprobante"
                className="w-full rounded-md border border-input bg-background px-3 py-2"
                rows={2}
                aria-invalid={!!errors.observaciones}
                aria-describedby={
                  errors.observaciones
                    ? "observaciones-comprobante-error"
                    : undefined
                }
                {...register("observaciones")}
              />
            </FormField>
            <Button
              ref={botonRevision}
              type="submit"
              className="justify-self-start"
            >
              {alta.isPending ? "Registrando…" : "Revisar comprobante"}
            </Button>
          </fieldset>
        </form>
      )}
      <AlertDialog.Root
        open={!!revision}
        onOpenChange={(open) => {
          if (!open) revisar(undefined);
        }}
      >
        <AlertDialog.Portal>
          <AlertDialog.Overlay className="overlay" />
          <AlertDialog.Content
            className="dialog"
            onCloseAutoFocus={(event) => {
              event.preventDefault();
              botonRevision.current?.focus();
            }}
          >
            <AlertDialog.Title className="text-xl font-bold">
              Confirmar comprobante interno
            </AlertDialog.Title>
            <AlertDialog.Description className="mt-2 text-muted-foreground">
              {nombreTipo(operacion.tipo)} #{operacion.id} ·{" "}
              {importeCaja(operacion.monto)} · Emisión{" "}
              {revision && fechaPedido(revision.fechaEmision)}.
            </AlertDialog.Description>
            {revision?.observaciones && (
              <p className="mt-3 whitespace-pre-wrap wrap-anywhere">
                {revision.observaciones}
              </p>
            )}
            <div className="mt-6 flex flex-wrap justify-end gap-2">
              <AlertDialog.Cancel asChild>
                <Button variant="outline">Cancelar</Button>
              </AlertDialog.Cancel>
              <Button disabled={ocupado} onClick={() => void confirmar()}>
                Confirmar registro
              </Button>
            </div>
          </AlertDialog.Content>
        </AlertDialog.Portal>
      </AlertDialog.Root>
    </>
  );
}
