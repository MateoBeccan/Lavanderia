import { afterEach, describe, expect, it, vi } from "vitest";
import { apiUrl, createApi } from "./http";

afterEach(() => vi.unstubAllGlobals());

describe("cliente HTTP", () => {
  it("conserva valores falsy y filtros repetidos", () => {
    const url = new URL(
      apiUrl("/pedidos", {
        page: 0,
        activo: false,
        sort: ["id,asc", "total,desc"],
        estado: null,
      }),
      "http://localhost",
    );
    expect(url.searchParams.get("page")).toBe("0");
    expect(url.searchParams.get("activo")).toBe("false");
    expect(url.searchParams.getAll("sort")).toEqual(["id,asc", "total,desc"]);
    expect(url.searchParams.has("estado")).toBe(false);
  });

  it.each([401, 403])("distingue %i y mantiene el detalle", async (status) => {
    const fetcher = vi.fn().mockResolvedValue(
      Response.json(
        {
          detail: "Detalle",
          errores: [{ campo: "nombre", mensaje: "Inválido" }],
        },
        { status },
      ),
    );
    const unauthorized = vi.fn();
    vi.stubGlobal("fetch", fetcher);
    await expect(
      createApi(() => "jwt-de-prueba", unauthorized)("/clientes"),
    ).rejects.toMatchObject({
      status,
      message: "Detalle",
      fields: [{ campo: "nombre", mensaje: "Inválido" }],
    });
    expect(fetcher.mock.calls[0][1].headers.get("Authorization")).toBe(
      "Bearer jwt-de-prueba",
    );
    if (status === 401)
      expect(unauthorized).toHaveBeenCalledWith("jwt-de-prueba");
    else expect(unauthorized).not.toHaveBeenCalled();
  });

  it("normaliza un fallo de red y conserva la cancelación", async () => {
    const aborted = new DOMException("Cancelado", "AbortError");
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockRejectedValueOnce(new TypeError("Failed to fetch"))
        .mockRejectedValueOnce(aborted),
    );
    const api = createApi();
    await expect(api("/clientes")).rejects.toMatchObject({ status: 0 });
    const controller = new AbortController();
    controller.abort();
    await expect(api("/clientes", { signal: controller.signal })).rejects.toBe(
      aborted,
    );
  });

  it("admite respuestas 204", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
    );
    await expect(
      createApi()("/clientes/1", { method: "DELETE" }),
    ).resolves.toBeUndefined();
  });

  it("conserva el timeout al leer un conflicto financiero", async () => {
    const controller = new AbortController();
    const timeout = new DOMException("Tiempo agotado", "TimeoutError");
    const response = new Response(null, { status: 409 });
    vi.spyOn(response, "json").mockImplementation(async () => {
      controller.abort(timeout);
      throw timeout;
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));

    await expect(
      createApi()("/pedidos/1/cobros", {
        method: "POST",
        idempotencyKey: "4d51adaf-7660-4085-aebf-74fe4375fe85",
        expectedStatus: 201,
        signal: controller.signal,
      }),
    ).rejects.toBe(timeout);
  });
});
