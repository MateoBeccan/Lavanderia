import type { FullConfig } from "@playwright/test";

export default async function verificarAislamiento(config: FullConfig) {
  const nonce = process.env.E2E_ISOLATION_NONCE;
  const destinos = [
    process.env.API_PROXY_TARGET,
    config.projects[0].use.baseURL,
  ];
  for (const destino of destinos) {
    const respuesta = await fetch(destino + "/api/__e2e_isolated", {
      redirect: "error",
      signal: AbortSignal.timeout(5_000),
    });
    if (!respuesta.ok || (await respuesta.text()) !== nonce) {
      throw new Error(
        "Spring no acreditó el aislamiento de la base de pruebas.",
      );
    }
  }
}
