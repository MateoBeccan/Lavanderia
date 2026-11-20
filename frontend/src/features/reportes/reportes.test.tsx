import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, createApi } from "@/shared/api/http";
import { fechaPedido } from "@/features/pedidos/rules";
import { consultarReporte, decodificarReporte } from "./api/reportes";
import { ConsultaReporte } from "./components/ConsultaReporte";
import { importeReporte } from "./components/formato";
import { leerFiltros, leerPaginacion, validarFiltros } from "./schemas/filtros";

const sesion = vi.hoisted(() => ({
  api: vi.fn(),
  user: { rol: "ADMINISTRADOR" },
}));
vi.mock("@/features/auth/session", () => ({ useSession: () => sesion }));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sesion.api.mockReset();
  sesion.user.rol = "ADMINISTRADOR";
});

describe("contrato de Reportes", () => {
  it.each([
    "2025-02-29",
    "0000-01-01",
    "2024-04-31",
    "2024-2-1",
    "10000-01-01",
  ])("rechaza la fecha %s", (fechaDesde) =>
    expect(validarFiltros({ fechaDesde })).toMatch(/fecha desde/),
  );

  it("acepta límites iguales, bisiestos y extremos abiertos con semántica comercial", () => {
    expect(
      validarFiltros({ fechaDesde: "2024-02-29", fechaHasta: "2024-02-29" }),
    ).toBeUndefined();
    expect(validarFiltros({ fechaHasta: "9999-12-31" })).toBeUndefined();
    expect(
      validarFiltros({ fechaDesde: "2024-03-01", fechaHasta: "2024-02-29" }),
    ).toMatch(/anterior o igual/);
    expect(fechaPedido("2024-02-29T23:59:59.999999", true)).toMatch(
      /29\/2\/24.*(?:23:59|11:59\s*p)/i,
    );
  });

  it("valida IDs de 64 bits y permite únicamente los campos habilitados", () => {
    expect(
      validarFiltros({ clienteId: "9223372036854775807" }),
    ).toBeUndefined();
    expect(validarFiltros({ proveedorId: "9223372036854775808" })).toMatch(
      /ID de proveedor/,
    );
    expect(
      leerFiltros(
        new URLSearchParams("clienteId=14&cajaId=8&estadoPagoCodigo="),
        ["clienteId", "estadoPagoCodigo"],
      ),
    ).toEqual({ clienteId: "14" });
  });

  it("valida la paginación base cero y sus límites", () => {
    expect(leerPaginacion(new URLSearchParams("page=2&size=100"))).toEqual({
      page: 2,
      size: 100,
    });
    expect(
      leerPaginacion(new URLSearchParams("page=2147483648&size=101")),
    ).toEqual({ page: 0, size: 20 });
    expect(leerPaginacion(new URLSearchParams("page=-1&size=1.5"))).toEqual({
      page: 0,
      size: 20,
    });
  });

  it("preserva las cifras decimales y el texto escapado", () => {
    const respuesta = decodificarReporte(
      String.raw`{"resumen":{"saldoNeto":-9007199254740993.07,"totalIngresos":0.10},"datos":[{"movimientoId":9223372036854775807,"descripcion":"incluye \"monto\": 9.87","monto":0.10}]}`,
    );
    expect(respuesta).toEqual({
      resumen: { saldoNeto: "-9007199254740993.07", totalIngresos: "0.10" },
      datos: [
        {
          movimientoId: "9223372036854775807",
          descripcion: 'incluye "monto": 9.87',
          monto: "0.10",
        },
      ],
    });
    expect(importeReporte("-9007199254740993.07")).toMatch(
      /-\$\s9\.007\.199\.254\.740\.993,07/,
    );
    expect(importeReporte("-0.01")).toMatch(/-\$\s0,01/);
    expect(() => decodificarReporte('{"totalGeneral":0.001}')).toThrow();
  });

  it.each(["pedidos", "compras", "movimientos-caja"] as const)(
    "envía los parámetros y el JWT por el cliente centralizado para %s",
    async (tipo) => {
      const fetch = vi
        .fn()
        .mockResolvedValue(new Response('{"resumen":{"totalGeneral":1.01}}'));
      vi.stubGlobal("fetch", fetch);
      const signal = new AbortController().signal;
      const respuesta = await consultarReporte(
        createApi(() => "jwt-sintetico"),
        tipo,
        { fechaHasta: "2024-02-29", page: 1, size: 20 },
        signal,
      );
      expect(fetch).toHaveBeenCalledWith(
        `/api/reportes/${tipo}?fechaHasta=2024-02-29&page=1&size=20`,
        expect.objectContaining({ headers: expect.any(Headers), signal }),
      );
      expect(fetch.mock.calls[0][1].headers.get("Authorization")).toBe(
        "Bearer jwt-sintetico",
      );
      expect(respuesta.resumen).toEqual({ totalGeneral: "1.01" });
    },
  );
});

function renderConsulta(url = "/") {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter initialEntries={[url]}>
        <ConsultaReporte
          tipo="pedidos"
          titulo="Pedidos del reporte"
          nota="Importe de pedidos."
          campos={[]}
          columns={[{ header: "Pedido", accessorKey: "pedidoId" }]}
          getRowId={({ pedidoId }) => String(pedidoId)}
          indicadores={({ totalGeneral }) => importeReporte(totalGeneral)}
        />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const vacio = {
  datos: [],
  resumen: { cantidadPedidos: 0, totalGeneral: "0.00" },
  pagina: 0,
  tamanio: 20,
  totalElementos: 0,
  totalPaginas: 0,
};

describe("consulta renderizada", () => {
  it("muestra errores de Spring y permite recuperar un resultado vacío", async () => {
    sesion.api
      .mockRejectedValueOnce(new ApiError(503, "Servidor en mantenimiento."))
      .mockResolvedValueOnce(vacio);
    renderConsulta();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Servidor en mantenimiento.",
    );
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));
    expect(
      await screen.findByRole("heading", { name: "Sin resultados" }),
    ).toBeVisible();
    expect(screen.getByText(/\$\s0,00/)).toBeVisible();
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("permite volver de una página vacía conservando el filtro", async () => {
    sesion.api
      .mockResolvedValueOnce({
        ...vacio,
        pagina: 4,
        totalElementos: 1,
        totalPaginas: 1,
      })
      .mockResolvedValueOnce({
        ...vacio,
        datos: [{ pedidoId: "73" }],
        totalElementos: 1,
        totalPaginas: 1,
      });
    renderConsulta("/?fechaHasta=2024-02-29&page=4");
    await userEvent.click(
      await screen.findByRole("button", { name: "Ir a la primera página" }),
    );
    expect(
      await screen.findByRole("table", { name: "Pedidos del reporte" }),
    ).toHaveTextContent("73");
    expect(sesion.api.mock.calls[1][1].params).toEqual({
      fechaHasta: "2024-02-29",
      page: 0,
      size: 20,
    });
  });

  it("bloquea una URL inválida antes de consultar", () => {
    renderConsulta("/?fechaDesde=2024-02-31");
    expect(screen.getByRole("alert")).toHaveTextContent("fecha desde válida");
    expect(sesion.api).not.toHaveBeenCalled();
  });

  it("no consulta reportes si falta el rol administrativo", () => {
    sesion.user.rol = "EMPLEADO";
    renderConsulta();
    expect(sesion.api).not.toHaveBeenCalled();
  });
});
