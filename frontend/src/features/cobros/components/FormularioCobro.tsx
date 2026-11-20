import { useState } from "react";
import type { FormEvent } from "react";
import { importeCaja } from "@/features/caja/components/formato";
import type { OpcionCaja } from "@/features/caja/types/caja";
import { selectCatalogo } from "@/shared/catalogos/config";
import { FormField } from "@/shared/components/forms/FormField";
import { Input } from "@/shared/components/ui/input";
import { Button } from "@/shared/components/ui/button";
import { cobroSchema } from "../schemas/cobro";
import type { SolicitudCobro } from "../types/cobros";

export function FormularioCobro({
  saldo,
  metodos,
  registrar,
}: {
  saldo: number;
  metodos: OpcionCaja[];
  registrar: (body: SolicitudCobro) => void;
}) {
  const [monto, setMonto] = useState("");
  const [metodo, setMetodo] = useState("");
  const [revision, setRevision] = useState(false);
  const [errores, setErrores] = useState<Record<string, string | undefined>>(
    {},
  );
  const opciones = metodos.map(({ id }) => id);
  const seleccionado = metodos.find(({ id }) => String(id) === metodo);

  function enviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const datos = cobroSchema(saldo, opciones).safeParse({
      monto,
      metodoPagoId: metodo,
    });
    if (!datos.success) {
      setErrores(
        Object.fromEntries(
          datos.error.issues.map(({ path, message }) => [path[0], message]),
        ),
      );
      setRevision(false);
      return;
    }
    setErrores({});
    if (!revision) {
      setRevision(true);
      return;
    }
    registrar(datos.data);
  }

  return (
    <form
      className="surface grid gap-4 p-4 sm:p-6"
      noValidate
      onSubmit={enviar}
    >
      <h2 className="text-lg font-semibold">
        {revision ? "Confirmá el cobro" : "Datos del cobro"}
      </h2>
      {revision ? (
        <p role="status">
          Vas a registrar {importeCaja(Number(monto.replace(",", ".")))} con{" "}
          {seleccionado?.nombre ?? "un método que requiere revisión"}.
        </p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            id="cobro-monto"
            label="Importe recibido"
            error={errores.monto}
          >
            <Input
              id="cobro-monto"
              inputMode="decimal"
              value={monto}
              required
              aria-invalid={!!errores.monto}
              aria-describedby={errores.monto ? "cobro-monto-error" : undefined}
              onChange={(event) => setMonto(event.target.value)}
            />
          </FormField>
          <FormField
            id="cobro-metodo"
            label="Método de pago"
            error={errores.metodoPagoId}
          >
            <select
              id="cobro-metodo"
              className={selectCatalogo}
              value={metodo}
              required
              aria-invalid={!!errores.metodoPagoId}
              aria-describedby={
                errores.metodoPagoId ? "cobro-metodo-error" : undefined
              }
              onChange={(event) => setMetodo(event.target.value)}
            >
              <option value="">Seleccioná un método</option>
              {metodos.map(({ id, nombre }) => (
                <option key={id} value={id}>
                  {nombre}
                </option>
              ))}
            </select>
          </FormField>
        </div>
      )}
      <div className="flex flex-wrap gap-2">
        <Button key={revision ? "confirmar" : "revisar"} type="submit">
          {revision ? "Confirmar cobro" : "Revisar cobro"}
        </Button>
        {revision && (
          <Button
            type="button"
            variant="outline"
            onClick={() => setRevision(false)}
          >
            Editar datos
          </Button>
        )}
      </div>
    </form>
  );
}
