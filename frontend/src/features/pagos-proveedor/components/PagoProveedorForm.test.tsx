import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import type { SaldoCaja } from "@/features/caja/types/caja";
import { PagoProveedorForm } from "./PagoProveedorForm";

it("solicita una revisión y vuelve a validar antes de confirmar", async () => {
  const registrar = vi.fn();
  const user = userEvent.setup();
  const caja = {
    cajaId: 8,
    saldoGeneral: 100,
    efectivoDisponible: 100,
  } as SaldoCaja;
  const metodos = [{ id: 1, codigo: "EFECTIVO", nombre: "Efectivo" }];
  const form = (saldo: number) => (
    <PagoProveedorForm
      saldo={saldo}
      metodos={metodos}
      caja={caja}
      verificando={false}
      registrar={registrar}
    />
  );
  const vista = render(form(50));
  await user.type(screen.getByLabelText("Importe pagado"), "11,25");
  await user.selectOptions(screen.getByLabelText("Método de pago"), "1");
  await user.click(screen.getByRole("button", { name: "Revisar pago" }));
  expect(registrar).not.toHaveBeenCalled();
  vista.rerender(form(10));
  await user.click(screen.getByRole("button", { name: "Confirmar pago" }));
  expect(screen.getByText(/supera el saldo pendiente/)).toBeTruthy();
  expect(registrar).not.toHaveBeenCalled();
  await user.clear(screen.getByLabelText("Importe pagado"));
  await user.type(screen.getByLabelText("Importe pagado"), "8,20");
  await user.click(screen.getByRole("button", { name: "Revisar pago" }));
  await user.click(screen.getByRole("button", { name: "Confirmar pago" }));
  expect(registrar).toHaveBeenCalledExactlyOnceWith({
    monto: 8.2,
    metodoPagoId: 1,
  });
});
