// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/shared/api/http";
import { PedidoForm } from "./components/PedidoForm";
import {
  destinosPedido,
  errorPedido,
  estadoEditable,
  importePedido,
  puedeOperarPedidos,
  resultadoIncierto,
} from "./rules";
import { pedidoSchema, prepararPedido, valoresPedido } from "./schemas/pedido";

vi.mock("@/features/auth/session", () => ({
  useSession: () => ({
    api: async () => ({
      content: [],
      page: { number: 0, size: 10, totalPages: 0, totalElements: 0 },
    }),
  }),
}));
afterEach(cleanup);

const valores = {
  ...valoresPedido(),
  clienteId: 2,
  fechaEntregaEstimada: "2099-12-30",
  detalles: [
    {
      prendaId: 3,
      servicioId: 4,
      modalidadCobro: "KILO" as const,
      cantidad: "1,25",
      estadoPrenda: "",
      observaciones: "",
    },
  ],
};

describe("validación del pedido", () => {
  it("conserva IDs, interpreta coma decimal y omite datos de interfaz", () => {
    expect(prepararPedido(pedidoSchema(false).parse(valores))).toEqual({
      clienteId: 2,
      fechaEntregaEstimada: "2099-12-30",
      detalles: [
        {
          prendaId: 3,
          servicioId: 4,
          cantidad: 1.25,
          estadoPrenda: null,
          observaciones: null,
        },
      ],
    });
    const editado = pedidoSchema(true).parse({
      ...valores,
      detalles: [{ ...valores.detalles[0], detalleId: 9 }],
    });
    expect(prepararPedido(editado)).not.toHaveProperty("clienteId");
    expect(prepararPedido(editado).detalles[0].detalleId).toBe(9);
  });

  it.each(["", "0", "-1", "0,001", "123456789", "1e3"])(
    "rechaza la cantidad %s",
    (cantidad) => {
      const resultado = pedidoSchema(false).safeParse({
        ...valores,
        detalles: [{ ...valores.detalles[0], cantidad }],
      });
      expect(resultado.error?.issues[0].path).toEqual([
        "detalles",
        0,
        "cantidad",
      ]);
    },
  );

  it("rechaza líneas vacías, entregas pasadas y fracciones por prenda", () => {
    expect(
      pedidoSchema(false).safeParse({ ...valores, detalles: [] }).success,
    ).toBe(false);
    expect(
      pedidoSchema(false).safeParse({
        ...valores,
        fechaEntregaEstimada: "2000-01-01",
      }).success,
    ).toBe(false);
    expect(
      pedidoSchema(false).safeParse({
        ...valores,
        detalles: [{ ...valores.detalles[0], modalidadCobro: "PRENDA" }],
      }).error?.issues[0].message,
    ).toMatch(/entera/);
  });

  it("permite agregar y quitar renglones y localiza errores en el renglón restante", async () => {
    const router = createMemoryRouter([{ path: "/", element: <PedidoForm /> }]);
    const client = new QueryClient();
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={client}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("button", { name: "Agregar renglón" }));
    expect(
      screen.getByRole("group", { name: "Renglón 2" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Quitar renglón 1" }));
    expect(
      screen.queryByRole("group", { name: "Renglón 2" }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Revisar pedido" }));
    const renglon = within(screen.getByRole("group", { name: "Renglón 1" }));
    expect(await renglon.findByLabelText("Prenda")).toHaveAccessibleDescription(
      "Seleccioná una opción.",
    );
    expect(renglon.getByLabelText("Servicio")).toHaveAccessibleDescription(
      "Seleccioná una opción.",
    );
  });
});

it("intersecta las transiciones con los estados activos y restringe cancelación con cobros", () => {
  const estados = [
    "PENDIENTE",
    "EN_PROCESO",
    "LISTO",
    "ENTREGADO",
    "CANCELADO",
  ].map((codigo, id) => ({
    id,
    codigo,
    nombre: codigo,
    orden: id,
    activo: true,
    descripcion: null,
  }));
  const destinos = (actual: string, cobros?: boolean) =>
    destinosPedido(actual, estados, cobros).map((s) => s.codigo);
  expect(destinos("PENDIENTE", false)).toEqual(["EN_PROCESO", "CANCELADO"]);
  expect(destinos("EN_PROCESO", true)).toEqual(["LISTO"]);
  expect(destinos("LISTO")).toEqual(["ENTREGADO"]);
  estados[1].activo = false;
  expect(destinos("PENDIENTE")).toEqual([]);
  expect(destinos("CANCELADO")).toEqual([]);
  expect(estadoEditable("ENTREGADO")).toBe(false);
  expect(estadoEditable("LISTO")).toBe(true);
  expect(puedeOperarPedidos("EMPLEADO")).toBe(true);
  expect(puedeOperarPedidos("ADMINISTRADOR")).toBe(true);
});

it("formatea importes confirmados y distingue errores con resultado incierto", () => {
  expect(importePedido(1234.05)).toMatch(/1\.234,05/);
  const conflicto = new ApiError(409, "Transición no permitida.");
  expect(errorPedido(conflicto)).toBe(conflicto.message);
  expect(resultadoIncierto(conflicto)).toBe(false);
  for (const status of [0, 408, 502, 504, 201]) {
    expect(resultadoIncierto(new ApiError(status, "Sin confirmación"))).toBe(
      true,
    );
  }
});
