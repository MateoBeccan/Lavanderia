import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { loadEnv } from "vite";
import type { ProxyOptions } from "vite";
import { defineConfig } from "vitest/config";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const proxy = {
    "/api": {
      target: env.API_PROXY_TARGET || "http://localhost:8080",
      changeOrigin: true,
      configure(server) {
        server.on("proxyReq", (upstream, request) => {
          // Spring ve el host del destino. El navegador envía el origen de Vite en los POST.
          if (request.headers.origin === "http://" + request.headers.host) {
            upstream.removeHeader("origin");
          }
        });
      },
    },
  } satisfies Record<string, ProxyOptions>;
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
    },
    server: { proxy },
    preview: { proxy },
    test: { environment: "jsdom", include: ["src/**/*.test.{ts,tsx}"] },
  };
});
