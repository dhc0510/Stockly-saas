import { createHash } from "node:crypto";
import type { Plugin } from "vite";

export function offlineWorker(): Plugin {
  return {
    name: "stockly-offline-worker",
    apply: "build",
    enforce: "post",
    generateBundle(_, bundle) {
      const assets = Object.keys(bundle).filter(name => name.startsWith("assets/")).sort();
      const version = createHash("sha256").update(assets.join("|")).digest("hex").slice(0, 16);
      const urls = ["/index.html", "/brand/stockly-caja.svg", "/stockly-offline.webmanifest", ...assets.map(name => "/" + name)];
      this.emitFile({ type: "asset", fileName: "stockly-offline-worker.js", source: `
const cacheName = "stockly-shell-${version}";
const assets = ${JSON.stringify(urls)};
self.addEventListener("install", event => {
  event.waitUntil(caches.open(cacheName).then(cache => cache.addAll(assets)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key =>
    key.startsWith("stockly-shell-") && key !== cacheName
  ).map(key => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== "GET" || url.origin !== self.location.origin || url.pathname.startsWith("/api/")) return;
  if (request.mode === "navigate") {
    event.respondWith(fetch(request).catch(() => caches.open(cacheName).then(cache => cache.match("/index.html"))));
  } else if (assets.includes(url.pathname)) {
    event.respondWith(caches.open(cacheName).then(cache => cache.match(url.pathname)).then(response => response || fetch(request)));
  }
});` });
    },
  };
}
