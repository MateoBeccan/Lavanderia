import { z } from "zod";

const baseUrl = (import.meta.env.VITE_API_BASE_URL || "/api").replace(
  /\/+$/,
  "",
);
const problem = z.object({
  detail: z.string().optional(),
  codigo: z.string().optional(),
  errores: z
    .array(z.object({ campo: z.string(), mensaje: z.string() }))
    .optional(),
});

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly fields: { campo: string; mensaje: string }[] = [],
    public readonly code?: string,
    public readonly retryAfter?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type Scalar = string | number | boolean;
type Params = Record<string, Scalar | readonly Scalar[] | null | undefined>;
type Options = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
  params?: Params;
  idempotencyKey?: string;
  expectedStatus?: number;
  decodeJson?: (body: string) => unknown;
  responseType?: "pdf";
};

export interface PagedResponse<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export function apiUrl(path: string, params: Params = {}) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value == null) continue;
    for (const item of Array.isArray(value) ? value : [value])
      query.append(key, String(item));
  }
  return baseUrl + path + (query.size ? "?" + query.toString() : "");
}

export function createApi(
  getToken: () => string | undefined = () => undefined,
  onUnauthorized?: (token: string) => void,
) {
  return async function request<T>(
    path: string,
    options: Options = {},
  ): Promise<T> {
    const token = getToken();
    const headers = new Headers({
      Accept:
        options.responseType === "pdf"
          ? "application/pdf, application/problem+json"
          : "application/json, application/problem+json",
    });
    if (token) headers.set("Authorization", "Bearer " + token);
    if (options.idempotencyKey)
      headers.set("Idempotency-Key", options.idempotencyKey);
    const body =
      options.body === undefined ? undefined : JSON.stringify(options.body);
    if (body !== undefined) headers.set("Content-Type", "application/json");
    let response: Response;
    try {
      response = await fetch(apiUrl(path, options.params), {
        method: options.method,
        headers,
        body,
        signal: options.signal,
        credentials: "omit",
        redirect: "error",
      });
    } catch (error) {
      if (options.signal?.aborted) throw error;
      throw new ApiError(
        0,
        "No pudimos conectar con el servidor. Intentá nuevamente.",
      );
    }
    if (!response.ok) {
      if (response.status === 401 && token) onUnauthorized?.(token);
      const parsed = problem.safeParse(await response.json().catch(() => null));
      options.signal?.throwIfAborted();
      const details = parsed.success ? parsed.data : undefined;
      const fallback =
        response.status === 401
          ? "Credenciales o sesión inválidas."
          : response.status === 403
            ? "No tenés permiso para acceder."
            : response.status >= 500
              ? "El servidor no está disponible temporalmente."
              : "La solicitud no pudo completarse.";
      throw new ApiError(
        response.status,
        details?.detail || fallback,
        details?.errores,
        details?.codigo,
        response.headers.get("Retry-After") ?? undefined,
      );
    }
    if (
      options.expectedStatus !== undefined &&
      response.status !== options.expectedStatus
    ) {
      throw new ApiError(
        response.status,
        "El servidor devolvió una respuesta inesperada.",
      );
    }
    if (options.responseType === "pdf") {
      const tipo = response.headers.get("Content-Type")?.split(";")[0];
      if (tipo !== "application/pdf") {
        throw new ApiError(response.status, "El servidor no devolvió un PDF.");
      }
      const blob = await response.blob();
      const firma = await blob.slice(0, 5).text();
      if (firma !== "%PDF-" || blob.size > 8 * 1024 * 1024) {
        throw new ApiError(
          response.status,
          "El PDF recibido está vacío o es inválido.",
        );
      }
      const nombre = response.headers
        .get("Content-Disposition")
        ?.match(/filename="?([a-z0-9-]+\.pdf)"?/i)?.[1];
      return { blob, nombre } as T;
    }
    if (response.status === 204) return undefined as T;
    try {
      return (
        options.decodeJson
          ? options.decodeJson(await response.text())
          : await response.json()
      ) as T;
    } catch (error) {
      if (options.signal?.aborted) throw error;
      throw new ApiError(
        response.status,
        "El servidor devolvió una respuesta inválida.",
      );
    }
  };
}

export type ApiClient = ReturnType<typeof createApi>;
