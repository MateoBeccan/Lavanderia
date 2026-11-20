import { useState } from "react";
import type { FormEvent } from "react";
import { FormField } from "@/shared/components/forms/FormField";
import { ErrorState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import type { SolicitudCaja, TipoManual } from "../api/escrituras";
import { useCatalogosCaja } from "../hooks/queries";
import {
  aperturaSchema,
  movimientoManualSchema,
  validarEgreso,
} from "../schemas/caja";
import type { OpcionCaja, SaldoCaja } from "../types/caja";
import { CajaFeedback } from "./CajaFeedback";
import { importeCaja } from "./formato";

const acciones = {
  APERTURA: "Abrir caja",
  INGRESO_MANUAL: "Registrar ingreso manual",
  EGRESO_MANUAL: "Registrar egreso manual",
};

type Props = {
  deshabilitado: boolean;
  iniciar: (solicitud: SolicitudCaja) => void;
};

function Formulario({
  tipo,
  saldo,
  metodos = [],
  deshabilitado,
  iniciar,
  cancelar,
}: Props & {
  tipo: SolicitudCaja["tipo"];
  saldo?: SaldoCaja;
  metodos?: OpcionCaja[];
  cancelar?: () => void;
}) {
  const [solicitud, setSolicitud] = useState<SolicitudCaja>();
  const [error, setError] = useState<string>();
  const apertura = tipo === "APERTURA";
  const metodo =
    solicitud && "cajaId" in solicitud
      ? metodos.find(({ id }) => id === solicitud.body.metodoPagoId)
      : undefined;
  const errorSaldo =
    solicitud?.tipo === "EGRESO_MANUAL" && saldo && metodo
      ? validarEgreso(
          solicitud.body.monto,
          metodo.codigo,
          saldo.efectivoDisponible,
        )
      : undefined;

  function revisar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSolicitud(undefined);
    setError(undefined);
    const datos = Object.fromEntries(new FormData(event.currentTarget));
    if (tipo === "APERTURA") {
      const parsed = aperturaSchema.safeParse(datos);
      if (parsed.success) setSolicitud({ tipo, body: parsed.data });
      else setError(parsed.error.issues[0]?.message);
      return;
    }
    const parsed = movimientoManualSchema.safeParse(datos);
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message);
      return;
    }
    if (!saldo || !metodos.some(({ id }) => id === parsed.data.metodoPagoId)) {
      setError("Elegí un método de pago activo.");
      return;
    }
    setSolicitud({ tipo, cajaId: saldo.cajaId, body: parsed.data });
  }

  return (
    <div className="space-y-3">
      {!apertura && (
        <p className="text-sm text-muted-foreground">
          {tipo === "INGRESO_MANUAL"
            ? "Los cobros de pedidos se registran desde Cobros."
            : "Registrá salidas de caja ajenas a pagos a proveedores."}
        </p>
      )}
      <form
        className="grid items-start gap-3 sm:max-w-lg"
        onSubmit={revisar}
        onChange={() => {
          setSolicitud(undefined);
          setError(undefined);
        }}
      >
        <FormField
          id="operacion-caja-importe"
          label={apertura ? "Importe inicial" : "Importe"}
        >
          <Input
            id="operacion-caja-importe"
            name={apertura ? "montoInicial" : "monto"}
            inputMode="decimal"
          />
        </FormField>
        {!apertura && (
          <>
            <FormField id="operacion-caja-metodo" label="Método del movimiento">
              <select
                id="operacion-caja-metodo"
                name="metodoPagoId"
                className="min-h-11 w-full rounded-md border border-border bg-white px-3"
              >
                <option value="">Seleccioná un método</option>
                {metodos.map(({ id, nombre }) => (
                  <option key={id} value={id}>
                    {nombre}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField id="operacion-caja-concepto" label="Concepto">
              <Input id="operacion-caja-concepto" name="descripcion" />
            </FormField>
          </>
        )}
        <div className="flex flex-wrap gap-2">
          <Button type="submit" disabled={deshabilitado}>
            {apertura ? "Revisar apertura" : "Revisar movimiento"}
          </Button>
          {cancelar && (
            <Button type="button" variant="outline" onClick={cancelar}>
              Cancelar edición
            </Button>
          )}
        </div>
      </form>
      {(error || errorSaldo) && <ErrorState message={error ?? errorSaldo!} />}
      {solicitud &&
        !errorSaldo &&
        (solicitud.tipo === "APERTURA" || metodo) && (
          <ConfirmDialog
            trigger={<Button disabled={deshabilitado}>{acciones[tipo]}</Button>}
            title={acciones[tipo]}
            description={
              solicitud.tipo === "APERTURA"
                ? `La caja se abrirá con ${importeCaja(solicitud.body.montoInicial)} de efectivo inicial.`
                : `${importeCaja(solicitud.body.monto)} · ${metodo?.nombre} · ${solicitud.body.descripcion} · Caja #${solicitud.cajaId}`
            }
            confirmLabel={
              apertura ? "Confirmar apertura" : "Confirmar movimiento"
            }
            onConfirm={() => {
              if (!deshabilitado) iniciar(solicitud);
            }}
          />
        )}
    </div>
  );
}

export function AperturaCaja(props: Props) {
  return (
    <section
      className="surface space-y-3 p-5"
      aria-labelledby="apertura-titulo"
    >
      <h2 id="apertura-titulo" className="text-lg font-semibold">
        Apertura de caja
      </h2>
      <Formulario {...props} tipo="APERTURA" />
    </section>
  );
}

export function AccionesCaja({
  saldo,
  ...props
}: Props & { saldo: SaldoCaja }) {
  const [seleccion, setSeleccion] = useState<TipoManual>();
  const { tipos, metodos } = useCatalogosCaja();
  const disponibles = (["INGRESO_MANUAL", "EGRESO_MANUAL"] as const).filter(
    (codigo) => tipos.data?.some((tipo) => tipo.codigo === codigo),
  );
  const habilitado =
    tipos.isSuccess && metodos.isSuccess && metodos.data.length > 0;

  return (
    <section
      className="surface space-y-3 p-5"
      aria-labelledby="movimientos-manuales-titulo"
    >
      <h2 id="movimientos-manuales-titulo" className="text-lg font-semibold">
        Movimientos manuales
      </h2>
      <CajaFeedback query={tipos} />
      <CajaFeedback query={metodos} />
      {tipos.isSuccess &&
        metodos.isSuccess &&
        (!disponibles.length || !metodos.data.length) && (
          <p className="text-sm text-muted-foreground">
            Se requiere un tipo manual y un método de pago activos.
          </p>
        )}
      {habilitado && (
        <div className="flex flex-wrap gap-2">
          {disponibles.map((tipo) => (
            <Button
              key={tipo}
              variant="outline"
              aria-pressed={seleccion === tipo}
              onClick={() => setSeleccion(tipo)}
            >
              {tipo === "INGRESO_MANUAL" ? "Ingreso manual" : "Egreso manual"}
            </Button>
          ))}
        </div>
      )}
      {habilitado && seleccion && disponibles.includes(seleccion) && (
        <Formulario
          key={seleccion}
          {...props}
          tipo={seleccion}
          saldo={saldo}
          metodos={metodos.data}
          cancelar={() => setSeleccion(undefined)}
        />
      )}
    </section>
  );
}
