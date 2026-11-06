import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { demoMiddleware } from "./demo/server.ts";
import { stk005DemoMiddleware } from "./demo/stk005-server.ts";

export default defineConfig(({ mode }) => ({
  plugins: [react(), ...(mode === 'demo' ? [{ name: 'stockly-local-demo', configureServer(server: import('vite').ViteDevServer) { server.middlewares.use(demoMiddleware()) } }] : []), ...(mode === 'stk005-demo' ? [{ name: 'stockly-stk005-local-demo', configureServer(server: import('vite').ViteDevServer) { server.middlewares.use(stk005DemoMiddleware()) } }] : []), ...(mode === 'stk007-demo' ? [{ name: 'stockly-stk007-local-demo', configureServer(server: import('vite').ViteDevServer) { server.middlewares.use(stk005DemoMiddleware(true)) } }] : [])],
  server: { proxy: mode === 'demo' || mode === 'stk007-demo' ? undefined : { "/api": "http://localhost:8080" } },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
}));
