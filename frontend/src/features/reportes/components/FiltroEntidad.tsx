import { useState } from "react";
import { CompraProveedorSelector } from "@/features/compras/components/CompraProveedorSelector";
import { PedidoClienteSelector } from "@/features/pedidos/components/SelectorPedido";
import { FormField } from "@/shared/components/forms/FormField";
import { Input } from "@/shared/components/ui/input";

export function FiltroEntidad({
  campo,
  inicial,
}: {
  campo: "clienteId" | "proveedorId";
  inicial?: string;
}) {
  const [valor, setValor] = useState(inicial ?? "");
  const [nombre, setNombre] = useState<string>();
  const [idImpreciso, setIdImpreciso] = useState(false);
  const id = `${campo}-selector`;
  const numero = Number(valor);
  const seleccion = Number.isSafeInteger(numero) && numero > 0 ? numero : null;
  const cliente = campo === "clienteId";

  function seleccionar(id: number | null, etiqueta?: string) {
    if (id !== null && !Number.isSafeInteger(id)) {
      setIdImpreciso(true);
      return;
    }
    setIdImpreciso(false);
    setValor(id === null ? "" : String(id));
    setNombre(etiqueta);
  }

  return (
    <div className="grid min-w-0 content-start gap-2">
      {cliente ? (
        <FormField id={id} label="Cliente">
          <PedidoClienteSelector
            id={id}
            value={seleccion}
            placeholder="Todos los clientes"
            nombreSeleccionado={nombre}
            onSelect={(registro) =>
              seleccionar(registro?.id ?? null, registro?.nombre)
            }
          />
        </FormField>
      ) : (
        <CompraProveedorSelector
          id={id}
          value={seleccion}
          nombre={nombre}
          placeholder="Todos los proveedores"
          onChange={seleccionar}
        />
      )}
      <details open={idImpreciso || (valor && !seleccion) ? true : undefined}>
        <summary className="cursor-pointer text-sm text-primary">
          Usar ID de {cliente ? "cliente" : "proveedor"}
        </summary>
        <div className="mt-2">
          <FormField
            id={campo}
            label={cliente ? "ID del cliente" : "ID del proveedor"}
          >
            <Input
              id={campo}
              name={campo}
              inputMode="numeric"
              value={valor}
              aria-describedby={campo + "-ayuda"}
              onChange={(event) => {
                setValor(event.target.value);
                setNombre(undefined);
                setIdImpreciso(false);
              }}
            />
            {idImpreciso && (
              <p role="alert" className="text-sm text-destructive">
                Ingresá el ID completo para conservar todos sus dígitos.
              </p>
            )}
            <p id={campo + "-ayuda"} className="text-sm text-muted-foreground">
              También admite registros históricos. Dejá el campo vacío para
              incluir todos.
            </p>
          </FormField>
        </div>
      </details>
    </div>
  );
}
