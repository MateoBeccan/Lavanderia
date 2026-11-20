import { argv } from "node:process";
import { build, preview } from "vite";

const [port, outDir] = argv.slice(2);
const config = { mode: "e2e", build: { outDir } };
await build(config);
const server = await preview({
  ...config,
  preview: { host: "127.0.0.1", port: Number(port), strictPort: true },
});
server.printUrls();
