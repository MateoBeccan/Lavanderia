import { z } from "zod";

// String.strip de Java excluye los espacios no separables.
const espaciosJava =
  // eslint-disable-next-line no-control-regex -- Character.isWhitespace incluye separadores de control.
  /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu;

export const stripJava = (texto: string) => texto.replace(espaciosJava, "");

export const nombreCatalogo = (maximo: number) =>
  z
    .string()
    .refine((nombre) => stripJava(nombre).length > 0, "Ingresá un nombre.")
    .refine(
      (nombre) => stripJava(nombre).length <= maximo,
      `Usá hasta ${maximo} caracteres.`,
    );

export function idCatalogoValido(id: string) {
  return /^[1-9][0-9]{0,18}$/.test(id) && BigInt(id) <= 9223372036854775807n;
}
