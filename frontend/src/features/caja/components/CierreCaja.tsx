import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import type { z } from "zod";
import { ApiError } from "@/shared/api/http";
import { useSession } from "@/features/auth/session";
import { FormField } from "@/shared/components/forms/FormField";
import { ErrorState } from "@/shared/components/feedback/States";
import { Input } from "@/shared/components/ui/input";
import { Button } from "@/shared/components/ui/button";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { cajaApi } from "../api/caja";
import { cajaKeys } from "../hooks/queries";
import { cierreSchema } from "../schemas/caja";
import { importeCaja } from "./formato";

export function CierreCaja({ id }: { id: string }) {
  const { api } = useSession();
  const client = useQueryClient();
  const enviando = useRef(false);
  const [contado, setContado] = useState<number>();
  const [incierto, setIncierto] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<
    z.input<typeof cierreSchema>,
    unknown,
    z.output<typeof cierreSchema>
  >({
    resolver: zodResolver(cierreSchema),
    defaultValues: { efectivoContado: "" },
  });
  const cierre = useMutation({
    mutationFn: (efectivoContado: number) =>
      cajaApi.cerrar(api, id, efectivoContado),
    retry: false,
    networkMode: "always",
    onSuccess: async () => {
      toast.success("Cierre y arqueo registrados.");
      await client.invalidateQueries({ queryKey: cajaKeys.all });
    },
    onError: async (error) => {
      if (
        !(error instanceof ApiError) ||
        error.status < 400 ||
        error.status === 408 ||
        error.status >= 500
      ) {
        setIncierto(true);
        return;
      }
      const campo = error.fields.find(
        ({ campo }) => campo === "efectivoContado",
      );
      if (campo) setError("efectivoContado", { message: campo.mensaje });
      if (error.status === 404 || error.status === 409) {
        await client.invalidateQueries({ queryKey: cajaKeys.all });
      }
    },
    onSettled: () => {
      enviando.current = false;
      setContado(undefined);
    },
  });
  const error = errors.efectivoContado?.message;
  if (incierto) {
    return (
      <div className="surface space-y-3 p-5">
        <ErrorState message="Resultado incierto. Consultá el saldo y los movimientos de esta caja para verificar el estado del cierre." />
        <Button
          variant="outline"
          onClick={() => {
            void client.invalidateQueries({ queryKey: cajaKeys.all });
          }}
        >
          Consultar estado actualizado
        </Button>
      </div>
    );
  }

  return (
    <section className="surface space-y-3 p-5" aria-labelledby="cierre-titulo">
      <h2 id="cierre-titulo" className="text-lg font-semibold">
        Cierre y arqueo
      </h2>
      <p className="text-sm text-muted-foreground">
        Ingresá el efectivo contado. Spring Boot calcula la diferencia con el
        efectivo esperado al cerrar.
      </p>
      <form
        onSubmit={handleSubmit(({ efectivoContado }) =>
          setContado(efectivoContado),
        )}
        className="flex flex-wrap items-end gap-3"
      >
        <FormField id="efectivo-contado" label="Efectivo contado" error={error}>
          <Input
            id="efectivo-contado"
            inputMode="decimal"
            disabled={cierre.isPending}
            aria-invalid={!!error}
            aria-describedby={error ? "efectivo-contado-error" : undefined}
            {...register("efectivoContado", {
              onChange: () => setContado(undefined),
            })}
          />
        </FormField>
        <Button type="submit" disabled={cierre.isPending}>
          {cierre.isPending ? "Registrando cierre…" : "Revisar cierre"}
        </Button>
      </form>
      {cierre.error && !error && <ErrorState message={cierre.error.message} />}
      {contado !== undefined && !cierre.isPending && (
        <ConfirmDialog
          trigger={<Button variant="destructive">Cerrar caja</Button>}
          title={"Cerrar caja #" + id}
          description={`Se registrará ${importeCaja(contado)} de efectivo contado y la caja quedará cerrada.`}
          confirmLabel="Confirmar cierre"
          onConfirm={() => {
            if (enviando.current) return;
            enviando.current = true;
            cierre.mutate(contado);
          }}
        />
      )}
    </section>
  );
}
