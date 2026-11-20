import { useRef, useState } from "react";
import { Dialog } from "radix-ui";
import { toast } from "sonner";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import { selectCatalogo } from "@/shared/catalogos/config";
import { useModificarPedido } from "../hooks/queries";
import { errorPedido, resultadoIncierto } from "../rules";
import type { EstadoDisponible, Pedido } from "../types/pedidos";

export function PedidoEstadoDialog({
  pedido,
  destinos,
  verificar,
}: {
  pedido: Pedido;
  destinos: EstadoDisponible[];
  verificar: () => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [codigo, setCodigo] = useState("");
  const [observacion, setObservacion] = useState("");
  const [error, setError] = useState("");
  const [incierto, setIncierto] = useState(false);
  const enviando = useRef(false);
  const mutation = useModificarPedido();
  const destino = destinos.find((estado) => estado.codigo === codigo);
  const cancelar = codigo === "CANCELADO";

  function abrir(siguiente: boolean) {
    if (enviando.current) return;
    setOpen(siguiente);
    if (siguiente) {
      setCodigo("");
      setObservacion("");
      setError("");
      setIncierto(false);
    }
  }

  async function enviar() {
    if (enviando.current || !destino || incierto) return;
    const motivo = observacion.trim();
    if (cancelar && !motivo) {
      setError("Ingresá el motivo de cancelación.");
      return;
    }
    setError("");
    enviando.current = true;
    try {
      await mutation.mutateAsync({
        id: pedido.id,
        codigo,
        observacion: motivo,
      });
      setOpen(false);
      toast.success("Estado actualizado.");
    } catch (fallo) {
      if (resultadoIncierto(fallo)) {
        setIncierto(true);
        setError(
          "No pudimos confirmar el cambio. Verificá el estado y el historial antes de repetirlo.",
        );
      } else setError(errorPedido(fallo));
    } finally {
      enviando.current = false;
    }
  }

  return (
    <Dialog.Root open={open} onOpenChange={abrir}>
      {destinos.length > 0 && (
        <Dialog.Trigger asChild>
          <Button>Cambiar estado</Button>
        </Dialog.Trigger>
      )}
      <Dialog.Portal>
        <Dialog.Overlay className="overlay" />
        <Dialog.Content className="dialog max-h-[90dvh] overflow-y-auto">
          <Dialog.Title className="text-xl font-bold">
            Cambiar estado del pedido
          </Dialog.Title>
          <Dialog.Description className="mt-2 text-muted-foreground">
            Estado actual · {pedido.estadoPedido.nombre}
          </Dialog.Description>
          <form
            className="mt-4 grid gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              void enviar();
            }}
          >
            <fieldset
              className="grid gap-4"
              disabled={mutation.isPending || incierto}
            >
              <FormField id="estado-destino" label="Nuevo estado">
                <select
                  id="estado-destino"
                  className={selectCatalogo}
                  value={destino?.codigo ?? ""}
                  onChange={(e) => {
                    setCodigo(e.target.value);
                    setError("");
                  }}
                  required
                >
                  <option value="">Seleccioná un estado</option>
                  {destinos.map((estado) => (
                    <option key={estado.id} value={estado.codigo}>
                      {estado.nombre}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField
                id="estado-observacion"
                label={
                  cancelar ? "Motivo de cancelación" : "Observación (opcional)"
                }
              >
                <textarea
                  id="estado-observacion"
                  className="min-h-20 rounded-lg border bg-background p-2 text-sm"
                  maxLength={250}
                  value={observacion}
                  onChange={(e) => setObservacion(e.target.value)}
                />
              </FormField>
            </fieldset>
            {error && <ErrorState message={error} />}
            {mutation.isPending && (
              <LoadingState label="Actualizando estado…" />
            )}
            <div className="flex flex-wrap justify-end gap-2">
              {incierto ? (
                <Button
                  type="button"
                  onClick={async () => {
                    await verificar();
                    setOpen(false);
                  }}
                >
                  Actualizar estado e historial
                </Button>
              ) : (
                <>
                  <Dialog.Close asChild>
                    <Button
                      type="button"
                      variant="outline"
                      disabled={mutation.isPending}
                    >
                      Volver
                    </Button>
                  </Dialog.Close>
                  <Button
                    type="submit"
                    variant={cancelar ? "destructive" : "default"}
                    disabled={!destino || mutation.isPending}
                  >
                    {cancelar ? "Confirmar cancelación" : "Confirmar cambio"}
                  </Button>
                </>
              )}
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
