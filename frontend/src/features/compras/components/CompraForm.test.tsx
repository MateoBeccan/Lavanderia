import "@testing-library/jest-dom/vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/shared/api/http";
import { CompraForm } from "./CompraForm";
import {
  compraEditable,
  puedeGestionarCompras,
  resultadoIncierto,
} from "./presentacion";
import type { Compra, SaldoCompra } from "../types/compras";

const api = vi.hoisted(() => vi.fn());
vi.mock("@/features/auth/session", () => ({ useSession: () => ({ api }) }));
const proveedores = [
  { id: 1, nombre: "Primero", activo: true },
  { id: 2, nombre: "Segundo", activo: true },
];
const paginar = (content: unknown[]) => ({
  content,
  page: { size: 10, number: 0, totalElements: content.length, totalPages: 1 },
});

function mostrarFormulario() {
  const router = createMemoryRouter([{ path: "/", element: <CompraForm /> }]);
  const cache = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={cache}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

beforeEach(() => {
  api.mockReset();
  api.mockImplementation(async (path: string) => {
    if (path === "/proveedores") return paginar(proveedores);
    if (path === "/proveedores/1") return proveedores[0];
    if (path === "/proveedores/1/insumos")
      return paginar([{ insumoId: 7, insumoNombre: "Jabón" }]);
    if (path === "/proveedores/2/insumos")
      return paginar([{ insumoId: 8, insumoNombre: "Bolsa" }]);
    throw new Error("Ruta inesperada " + path);
  });
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

async function cargarRenglon(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole("option", { name: "Primero" });
  await user.selectOptions(screen.getByLabelText("Proveedor"), "1");
  await screen.findByRole("option", { name: "Jabón" });
  await user.selectOptions(screen.getByLabelText("Insumo"), "7");
  await user.type(screen.getByLabelText("Cantidad"), "1,25");
  await user.type(screen.getByLabelText("Precio de compra"), "12,34");
}

it("agrega y quita renglones y muestra errores de cantidad junto al control", async () => {
  const user = mostrarFormulario();
  await user.click(screen.getByRole("button", { name: "Agregar renglón" }));
  const segundo = screen.getByRole("group", { name: "Renglón 2" });
  await user.click(
    within(segundo).getByRole("button", { name: "Quitar renglón 2" }),
  );
  expect(segundo).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Revisar compra" }));
  expect(screen.getByLabelText("Cantidad")).toHaveAccessibleDescription(
    /valor positivo/,
  );
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
});

it("pide confirmación al cambiar de proveedor y descarta solo las selecciones incompatibles", async () => {
  const user = mostrarFormulario();
  const confirmar = vi
    .spyOn(window, "confirm")
    .mockReturnValueOnce(false)
    .mockReturnValueOnce(true);
  await cargarRenglon(user);
  await user.selectOptions(screen.getByLabelText("Proveedor"), "2");
  await vi.waitFor(() => expect(confirmar).toHaveBeenCalledTimes(1));
  expect(screen.getByLabelText("Proveedor")).toHaveValue("1");
  expect(screen.getByLabelText("Insumo")).toHaveValue("7");
  await user.selectOptions(screen.getByLabelText("Proveedor"), "2");
  await screen.findByRole("option", { name: "Bolsa" });
  expect(screen.getByLabelText("Insumo")).toHaveValue("");
  expect(screen.getByLabelText("Precio de compra")).toHaveValue("");
  expect(screen.getByLabelText("Cantidad")).toHaveValue("1,25");
});

it("vuelve a verificar las asociaciones y bloquea un insumo que dejó de estar disponible", async () => {
  const user = mostrarFormulario();
  await cargarRenglon(user);
  api.mockImplementation(async (path: string) =>
    path === "/proveedores/1" ? proveedores[0] : paginar([]),
  );
  await user.click(screen.getByRole("button", { name: "Revisar compra" }));
  await user.click(
    within(screen.getByRole("alertdialog")).getByRole("button", {
      name: "Confirmar registro",
    }),
  );
  await screen.findByText(
    "Este insumo ya no está disponible para el proveedor.",
  );
  expect(api.mock.calls.some(([path]) => path === "/compras")).toBe(false);
});

it("muestra detail y errores de campo de un rechazo de Spring", async () => {
  const user = mostrarFormulario();
  await cargarRenglon(user);
  api.mockImplementation(async (path: string) => {
    if (path === "/proveedores/1") return proveedores[0];
    if (path === "/proveedores/1/insumos")
      return paginar([{ insumoId: 7, insumoNombre: "Jabón" }]);
    throw new ApiError(400, "Revisá los datos enviados.", [
      { campo: "detalles[0].precioCompra", mensaje: "Precio inválido." },
    ]);
  });
  await user.click(screen.getByRole("button", { name: "Revisar compra" }));
  const dialogo = screen.getByRole("alertdialog");
  expect(within(dialogo).getByText("Total estimado 15,43")).toBeInTheDocument();
  await user.click(
    within(dialogo).getByRole("button", { name: "Confirmar registro" }),
  );
  await screen.findByText("Revisá los datos enviados.");
  expect(screen.getByLabelText("Precio de compra")).toHaveAccessibleDescription(
    "Precio inválido.",
  );
});

it("aplica los dos roles, respeta el saldo y distingue un resultado incierto", () => {
  expect(puedeGestionarCompras("ADMINISTRADOR")).toBe(true);
  expect(puedeGestionarCompras("EMPLEADO")).toBe(true);
  expect(puedeGestionarCompras()).toBe(false);
  const compra = { estadoPagoCodigo: "PENDIENTE" } as Compra;
  const saldo = {
    estadoPagoCodigo: "PENDIENTE",
    totalPagado: 0,
  } as SaldoCompra;
  expect(compraEditable(compra, saldo)).toBe(true);
  expect(compraEditable(compra, { ...saldo, totalPagado: 1 })).toBe(false);
  expect(compraEditable(compra)).toBe(false);
  expect(
    compraEditable(compra, { ...saldo, estadoPagoCodigo: "PARCIAL" }),
  ).toBe(false);
  expect(resultadoIncierto(new ApiError(403, "Prohibido"))).toBe(false);
  expect(resultadoIncierto(new ApiError(504, "Timeout"))).toBe(true);
  expect(resultadoIncierto(new DOMException("Timeout", "TimeoutError"))).toBe(
    true,
  );
});
