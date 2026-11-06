import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { demoMiddleware } from "./demo/server.ts";

export default defineConfig(({ mode }) => ({
  plugins: [react(), ...(mode === 'demo' ? [{ name: 'stockly-local-demo', configureServer(server: import('vite').ViteDevServer) { server.middlewares.use(demoMiddleware()) } }] : [])],
  server: { proxy: mode === 'demo' ? undefined : { "/api": "http://localhost:8080" } },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
}));
