import { useEffect, useId, useRef, useState } from "react";
import { importeCaja } from "@/features/caja/components/formato";
import type { OpcionCaja, SaldoCaja } from "@/features/caja/types/caja";
import { selectCatalogo } from "@/shared/catalogos/config";
import { FormField } from "@/shared/components/forms/FormField";
import { Input } from "@/shared/components/ui/input";
import { Button } from "@/shared/components/ui/button";
import { pagoProveedorSchema } from "../schemas/pago";
import type { SolicitudPagoProveedor } from "../types/pagos";

export function PagoProveedorForm({
  saldo,
  metodos,
  caja,
  verificando,
  registrar,
}: {
  saldo: number;
  metodos: OpcionCaja[];
  caja: SaldoCaja;
  verificando: boolean;
  registrar: (body: SolicitudPagoProveedor) => void;
}) {
  const prefijo = useId();
  const inputMonto = `${prefijo}-monto`;
  const inputMetodo = `${prefijo}-metodo`;
  const [monto, setMonto] = useState("");
  const [metodo, setMetodo] = useState("");
  const [revision, setRevision] = useState<SolicitudPagoProveedor>();
  const [errores, setErrores] = useState<Record<string, string | undefined>>(
    {},
  );
  const seleccionado = metodos.find(({ id }) => String(id) === metodo);

  const tituloRevision = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (revision) tituloRevision.current?.focus();
  }, [revision]);

  function validar() {
    return pagoProveedorSchema(saldo, metodos, caja).safeParse({
      monto,
      metodoPagoId: metodo,
    });
  }

  return (
    <form
      className="surface grid gap-4 p-4 sm:p-6"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        if (verificando) return;
        const datos = validar();
        if (!datos.success) {
          setErrores(
            Object.fromEntries(
              datos.error.issues.map(({ path, message }) => [path[0], message]),
            ),
          );
          setRevision(undefined);
          return;
        }
        setErrores({});
        if (revision) registrar(datos.data);
        else setRevision(datos.data);
      }}
    >
      <h2 ref={tituloRevision} tabIndex={-1} className="text-lg font-semibold">
        {revision ? "Confirmá el pago" : "Datos del pago"}
      </h2>
      {revision ? (
        <p>
          Vas a registrar {importeCaja(revision.monto)} con{" "}
          {seleccionado?.nombre ?? "un método que requiere revisión"} en la caja
          #{caja.cajaId}.
        </p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            id={inputMonto}
            label="Importe pagado"
            error={errores.monto}
          >
            <Input
              id={inputMonto}
              inputMode="decimal"
              value={monto}
              required
              aria-invalid={!!errores.monto}
              aria-describedby={
                errores.monto ? `${inputMonto}-error` : undefined
              }
              onChange={(event) => setMonto(event.target.value)}
            />
          </FormField>
          <FormField
            id={inputMetodo}
            label="Método de pago"
            error={errores.metodoPagoId}
          >
            <select
              id={inputMetodo}
              className={selectCatalogo}
              value={metodo}
              required
              aria-invalid={!!errores.metodoPagoId}
              aria-describedby={
                errores.metodoPagoId ? `${inputMetodo}-error` : undefined
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
        <Button
          key={revision ? "confirmar" : "revisar"}
          type="submit"
          disabled={verificando}
        >
          {revision ? "Confirmar pago" : "Revisar pago"}
        </Button>
        {revision && (
          <Button
            type="button"
            variant="outline"
            onClick={() => setRevision(undefined)}
          >
            Editar datos
          </Button>
        )}
      </div>
    </form>
  );
}
