import { AlertDialog } from "radix-ui";
import { Button } from "@/shared/components/ui/button";
import { subtotalEstimado } from "../schemas/compra";
import type { CompraRequest } from "../types/compras";
import { importeEstimado } from "./presentacion";

export function CompraResumen({
  compra,
  proveedor,
  nombres,
  editando,
  cerrar,
  restaurarFoco,
  guardar,
}: {
  compra: CompraRequest;
  proveedor: string;
  nombres: Record<number, string>;
  editando: boolean;
  cerrar: () => void;
  restaurarFoco: () => void;
  guardar: () => void;
}) {
  const total = compra.detalles.reduce(
    (suma, fila) => suma + subtotalEstimado(fila.cantidad, fila.precioCompra),
    0n,
  );
  return (
    <AlertDialog.Root
      open
      onOpenChange={(open) => {
        if (!open) cerrar();
      }}
    >
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="overlay" />
        <AlertDialog.Content
          className="dialog max-h-[85dvh] overflow-y-auto"
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            restaurarFoco();
          }}
        >
          <AlertDialog.Title className="text-xl font-bold">
            Revisá la compra
          </AlertDialog.Title>
          <AlertDialog.Description className="mt-2 text-muted-foreground">
            Confirmá los renglones para {proveedor}. Spring Boot confirmará los
            importes al guardar.
          </AlertDialog.Description>
          {compra.numeroComprobante && (
            <p className="mt-3 text-sm">
              Comprobante {compra.numeroComprobante}
            </p>
          )}
          <ul className="mt-3 divide-y">
            {compra.detalles.map((fila) => (
              <li
                key={fila.insumoId}
                className="flex flex-wrap justify-between gap-2 py-2 text-sm"
              >
                <span>
                  {nombres[fila.insumoId] ?? `Insumo #${fila.insumoId}`}
                </span>
                <span className="tabular-nums">
                  {fila.cantidad.replace(".", ",")} ×{" "}
                  {fila.precioCompra.replace(".", ",")} ·{" "}
                  {importeEstimado(
                    subtotalEstimado(fila.cantidad, fila.precioCompra),
                  )}
                </span>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-lg font-semibold">
            Total estimado {importeEstimado(total)}
          </p>
          <div className="mt-5 flex flex-wrap justify-end gap-2">
            <AlertDialog.Cancel asChild>
              <Button variant="outline">Volver al formulario</Button>
            </AlertDialog.Cancel>
            <AlertDialog.Action asChild>
              <Button onClick={guardar}>
                {editando ? "Confirmar cambios" : "Confirmar registro"}
              </Button>
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
