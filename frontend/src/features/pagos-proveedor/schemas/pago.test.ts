import { describe, expect, it } from "vitest";
import { ApiError } from "@/shared/api/http";
import { errorOperacionFinanciera } from "@/shared/api/errorOperacionFinanciera";
import {
  pagoProveedorSchema,
  puedeGestionarPagos,
  tieneSaldoPendiente,
} from "./pago";

const metodos = [
  { id: 1, codigo: "EFECTIVO", nombre: "Efectivo" },
  { id: 2, codigo: "TRANSFERENCIA", nombre: "Transferencia" },
];
const caja = { saldoGeneral: -0.5, efectivoDisponible: 10.1 };
const schema = pagoProveedorSchema(20.31, metodos, caja);

describe("pago a proveedores", () => {
  it.each(["0", "-1", "1,005", "100000000", "1e1", "", "texto"])(
    "rechaza el importe %s",
    (monto) => {
      expect(schema.safeParse({ monto, metodoPagoId: "1" }).success).toBe(
        false,
      );
    },
  );
  it("admite un pago parcial o por el saldo exacto con dos decimales", () => {
    expect(schema.parse({ monto: "10,10", metodoPagoId: "1" })).toEqual({
      monto: 10.1,
      metodoPagoId: 1,
    });
    expect(schema.parse({ monto: "20.31", metodoPagoId: "2" }).monto).toBe(
      20.31,
    );
  });
  it("rechaza importes fuera de saldo, efectivo o rango de caja y métodos ausentes", () => {
    expect(
      schema.safeParse({ monto: "20.32", metodoPagoId: "2" }).error?.issues[0]
        .message,
    ).toMatch(/saldo pendiente/);
    expect(
      schema.safeParse({ monto: "10.11", metodoPagoId: "1" }).error?.issues[0]
        .message,
    ).toMatch(/efectivo/);
    expect(schema.safeParse({ monto: "1", metodoPagoId: "99" }).success).toBe(
      false,
    );
    expect(
      pagoProveedorSchema(20.31, metodos, {
        ...caja,
        saldoGeneral: -99999999.98,
      }).safeParse({ monto: "0.02", metodoPagoId: "2" }).error?.issues[0]
        .message,
    ).toMatch(/rango/);
  });
  it("habilita ambos roles solo con estado y saldo admisibles", () => {
    expect(puedeGestionarPagos("EMPLEADO")).toBe(true);
    expect(puedeGestionarPagos("ADMINISTRADOR")).toBe(true);
    expect(puedeGestionarPagos()).toBe(false);
    const saldo = {
      compraId: 42,
      totalCompra: 20,
      totalPagado: 5,
      saldoPendiente: 15,
      estadoPagoNombre: "Parcial",
    };
    for (const estadoPagoCodigo of ["PENDIENTE", "PARCIAL"]) {
      expect(tieneSaldoPendiente({ ...saldo, estadoPagoCodigo })).toBe(true);
    }
    expect(
      tieneSaldoPendiente({
        ...saldo,
        estadoPagoCodigo: "PARCIAL",
        saldoPendiente: 0,
      }),
    ).toBe(false);
    expect(tieneSaldoPendiente({ ...saldo, estadoPagoCodigo: "PAGADO" })).toBe(
      false,
    );
  });
  it("mantiene pendiente el resultado ambiguo y aplica Retry-After", () => {
    expect(
      errorOperacionFinanciera(
        new DOMException("Timeout", "TimeoutError"),
        false,
      ).estado,
    ).toBe("PENDIENTE");
    expect(
      errorOperacionFinanciera(new ApiError(201, "Inválida"), false).estado,
    ).toBe("PENDIENTE");
    expect(
      errorOperacionFinanciera(new ApiError(401, "Sesión expirada"), true)
        .estado,
    ).toBe("PENDIENTE");
    expect(
      errorOperacionFinanciera(
        new ApiError(409, "", [], "IDEMPOTENCIA_EN_CURSO", "3"),
        false,
        1000,
      ),
    ).toMatchObject({ estado: "PENDIENTE", retryAt: 4000 });
    expect(
      errorOperacionFinanciera(
        new ApiError(409, "", [], "IDEMPOTENCIA_CLAVE_REUTILIZADA"),
        false,
      ).estado,
    ).toBe("VERIFICAR");
    expect(
      errorOperacionFinanciera(new ApiError(409, "Saldo insuficiente"), false)
        .estado,
    ).toBe("ERROR");
  });
});
