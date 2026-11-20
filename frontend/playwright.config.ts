import { defineConfig, devices } from "@playwright/test";

const port = Number(process.env.E2E_PORT || 5183);
const baseURL = "http://127.0.0.1:" + port;
const outDir = "../target/frontend-e2e-dist-" + port;

const instancias = {
  E2E_CLIENTES_ISOLATED: 18091,
  E2E_CATALOGOS_ISOLATED: 18092,
  E2E_PEDIDOS_ISOLATED: 18093,
  E2E_CAJA_ISOLATED: 18094,
  E2E_COBROS_ISOLATED: 18095,
  E2E_PROVEEDORES_ISOLATED: 18096,
  E2E_INSUMOS_ISOLATED: 18097,
  E2E_COMPRAS_ISOLATED: 18098,
  E2E_PAGOS_PROVEEDOR_ISOLATED: 18099,
  E2E_COMPROBANTES_ISOLATED: 18051,
  E2E_REPORTES_ISOLATED: 18052,
  E2E_LOGIN_ISOLATED: 18053,
};
const activas = Object.entries(instancias).filter(
  ([bandera]) => process.env[bandera] === "1",
);
const destino = activas.length
  ? "http://127.0.0.1:" + activas[0][1]
  : "http://127.0.0.1:1";
if (
  activas.length > 1 ||
  (activas.length === 1 &&
    (process.env.API_PROXY_TARGET !== destino ||
      process.env.E2E_PREVIEW_EXISTING === "1" ||
      !/^[0-9a-f]{32,64}$/.test(process.env.E2E_ISOLATION_NONCE || "")))
) {
  throw new Error(
    "Playwright real requiere una sola suite, su proxy local y un nonce del lanzador aislado.",
  );
}

export default defineConfig({
  testDir: "./e2e",
  expect: { timeout: 12_000 },
  globalSetup: activas.length ? "./e2e/verificarAislamiento.ts" : undefined,
  use: { baseURL, trace: "retain-on-failure" },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        channel: process.env.E2E_BROWSER_CHANNEL,
      },
    },
  ],
  webServer:
    process.env.E2E_PREVIEW_EXISTING === "1"
      ? undefined
      : {
          command: "node e2e/preview.mjs " + port + " " + outDir,
          url: baseURL,
          env: { VITE_API_BASE_URL: "/api", API_PROXY_TARGET: destino },
          reuseExistingServer: false,
          stdout: "pipe",
        },
});
