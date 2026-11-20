import { describe, expect, it } from "vitest";
import { clienteSchema } from "./cliente";

const valid = {
  nombre: " Ana ",
  apellido: " Núñez ",
  dni: "01234567",
  telefono: "",
  email: "",
};

describe("ClienteRequest", () => {
  it("conserva el DNI como texto y envía los datos opcionales vacíos como null", () => {
    expect(clienteSchema.parse(valid)).toEqual({
      nombre: "Ana",
      apellido: "Núñez",
      dni: "01234567",
      telefono: null,
      email: null,
    });
  });

  it.each([
    ["nombre", "  "],
    ["nombre", "x".repeat(101)],
    ["apellido", ""],
    ["apellido", "x".repeat(101)],
    ["dni", "12.345.678"],
    ["dni", "123456"],
    ["dni", " 1234567"],
    ["telefono", "1".repeat(31)],
    ["email", "ana@"],
    ["email", "a".repeat(250) + "@a.test"],
  ])("valida el campo %s", (campo, valor) => {
    const result = clienteSchema.safeParse({ ...valid, [campo]: valor });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0].path).toEqual([campo]);
  });

  it("acepta un DNI de siete dígitos y un teléfono con formato", () => {
    expect(
      clienteSchema.parse({
        ...valid,
        dni: "1234567",
        telefono: " +54 11 5555-1234 ",
        email: "ana.nunez@example.test",
      }),
    ).toMatchObject({
      dni: "1234567",
      telefono: "+54 11 5555-1234",
      email: "ana.nunez@example.test",
    });
  });
});
