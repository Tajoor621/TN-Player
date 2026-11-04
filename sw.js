"use strict";

const scope = self.registration.scope;
const prefix = `tn-player-shell:${scope}:`;
const cacheName = `${prefix}v2`;
const shell = [
    "./", "./index.html", "./player.html", "./manifest.webmanifest", "./pwa.js",
    "./assets/tn-player-icon.png", "./assets/tn-player-brand.png", "./assets/tn-player-maskable.png",
    "./assets/tn-player-icon.png?brand=tn-gold-cyan-20261018",
    "./assets/tn-player-brand.png?brand=tn-gold-cyan-20261018"
].map(path => new URL(path, scope).href);
const known = new Set(shell);

self.addEventListener("install", event => {
    event.waitUntil(caches.open(cacheName).then(cache => cache.addAll(shell)));
});

self.addEventListener("activate", event => {
    event.waitUntil(caches.keys().then(keys => Promise.all(
        keys.filter(key => key.startsWith(prefix) && key !== cacheName).map(key => caches.delete(key))
    )).then(() => self.clients.claim()));
});

self.addEventListener("fetch", event => {
    const { request } = event;
    const url = new URL(request.url);
    if (request.method !== "GET" || !url.href.startsWith(scope)) return;
    const isPage = request.mode === "navigate";
    const key = isPage ? url.origin + url.pathname : url.href;
    if (!known.has(key)) return;
    // Leave downloads, local media blobs, and third-party resources outside the cache.
    event.respondWith(fetch(request).catch(async error => {
        const cached = await (await caches.open(cacheName)).match(key);
        if (!cached) throw error;
        return cached;
    }));
});
