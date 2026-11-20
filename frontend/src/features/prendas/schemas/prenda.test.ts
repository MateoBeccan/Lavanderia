import { expect, it } from "vitest";
import { prendaSchema } from "./prenda";

it("valida el nombre sin cambiar el texto enviado a Spring", () => {
  const nombre = "  " + "Á".repeat(50) + "  ";
  expect(prendaSchema.parse({ nombre, precio: 100 })).toEqual({ nombre });
  expect(prendaSchema.safeParse({ nombre: "x".repeat(51) }).success).toBe(
    false,
  );
  expect(prendaSchema.safeParse({ nombre: " \t " }).success).toBe(false);
});
