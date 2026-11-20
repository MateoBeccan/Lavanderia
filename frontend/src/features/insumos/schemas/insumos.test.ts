import { describe, expect, it } from "vitest";
import { categoriaInsumoSchema, insumoSchema } from "./insumos";

describe("DTO de insumos y categorías", () => {
  it("recorta el nombre y genera solo los campos admitidos por Spring", () => {
    expect(
      categoriaInsumoSchema.parse({ nombre: "  Higiene  ", activo: false }),
    ).toEqual({ nombre: "Higiene" });
    expect(
      insumoSchema.parse({ nombre: "  Jabón  ", categoriaId: 12, id: 7 }),
    ).toEqual({ nombre: "Jabón", categoriaId: 12 });
  });

  it.each(["", " \t ", "x".repeat(51)])(
    "rechaza el nombre inválido de una categoría (%j)",
    (nombre) => {
      expect(categoriaInsumoSchema.safeParse({ nombre }).success).toBe(false);
    },
  );

  it("aplica límites de 50 y 100 caracteres después de recortar", () => {
    expect(
      categoriaInsumoSchema.safeParse({ nombre: " " + "x".repeat(50) + "\t" })
        .success,
    ).toBe(true);
    expect(
      insumoSchema.safeParse({ nombre: "x".repeat(100), categoriaId: 1 })
        .success,
    ).toBe(true);
    expect(
      insumoSchema.safeParse({ nombre: "x".repeat(101), categoriaId: 1 })
        .success,
    ).toBe(false);
  });

  it.each([null, 0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1, "12"])(
    "rechaza el ID de categoría inválido (%j)",
    (categoriaId) => {
      expect(
        insumoSchema.safeParse({ nombre: "Jabón", categoriaId }).success,
      ).toBe(false);
    },
  );
});
