"use strict";

if ("windowControlsOverlay" in navigator && navigator.windowControlsOverlay) {
    const overlay = navigator.windowControlsOverlay;
    const syncOverlay = () => {
        document.documentElement.classList.toggle("wco-visible", overlay.visible);
    };
    overlay.addEventListener("geometrychange", syncOverlay);
    syncOverlay();
}

const input = document.querySelector("#media-file");
const media = document.querySelector("#media");
const status = document.querySelector("#status");
if (input && media && status) {
    let currentUrl;
    input.addEventListener("change", () => {
        const file = input.files[0];
        if (!file) return;
        media.pause();
        if (currentUrl) URL.revokeObjectURL(currentUrl);
        currentUrl = URL.createObjectURL(file);
        media.src = currentUrl;
        media.hidden = false;
        status.textContent = `Selected: ${file.name}. Press Play to start.`;
    });
    media.addEventListener("error", () => {
        status.textContent = "This file could not be played. Your browser may not support its format or codec.";
    });
    window.addEventListener("pagehide", event => {
        if (!event.persisted && currentUrl) URL.revokeObjectURL(currentUrl);
    });
}

const install = document.querySelector("#install");
if (install) {
    let prompt;
    window.addEventListener("beforeinstallprompt", event => {
        event.preventDefault();
        prompt = event;
        install.hidden = false;
    });
    install.addEventListener("click", async () => {
        const event = prompt;
        prompt = null;
        install.hidden = true;
        if (event) {
            try { await event.prompt(); }
            catch (error) { console.warn("TN-Player install prompt failed:", error); }
        }
    });
    window.addEventListener("appinstalled", () => {
        prompt = null;
        install.hidden = true;
    });
}
