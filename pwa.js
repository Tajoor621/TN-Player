"use strict";

document.documentElement.classList.add("js");

const menuButton = document.querySelector(".menu-button");
const primaryNav = document.querySelector("#primary-nav");
if (menuButton && primaryNav) {
    const setMenu = (open) => {
        document.documentElement.classList.toggle("nav-open", open);
        menuButton.setAttribute("aria-expanded", open ? "true" : "false");
    };
    menuButton.addEventListener("click", () => {
        setMenu(menuButton.getAttribute("aria-expanded") !== "true");
    });
    primaryNav.addEventListener("click", (event) => {
        if (event.target.closest("a")) setMenu(false);
    });
    window.addEventListener("keydown", (event) => {
        if (event.key === "Escape") setMenu(false);
    });
}

if ("windowControlsOverlay" in navigator && navigator.windowControlsOverlay) {
    const overlay = navigator.windowControlsOverlay;
    const syncOverlay = () => {
        document.documentElement.classList.toggle("wco-visible", overlay.visible);
    };
    overlay.addEventListener("geometrychange", syncOverlay);
    syncOverlay();
}

function formatBytes(bytes) {
    if (!Number.isFinite(bytes) || bytes < 0) return "";
    if (bytes < 1024) return `${bytes} B`;
    const units = ["KB", "MB", "GB"];
    let value = bytes / 1024;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
        value /= 1024;
        unit += 1;
    }
    const digits = value >= 100 ? 0 : 1;
    return `${value.toFixed(digits)} ${units[unit]}`;
}

function formatTime(seconds) {
    if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
    const total = Math.floor(seconds);
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const secs = total % 60;
    const padded = String(secs).padStart(2, "0");
    if (hours) return `${hours}:${String(minutes).padStart(2, "0")}:${padded}`;
    return `${minutes}:${padded}`;
}

const input = document.querySelector("#media-file");
const media = document.querySelector("#media");
const status = document.querySelector("#status");
const queueEl = document.querySelector("#queue");
const tools = document.querySelector("#tools");
const rate = document.querySelector("#rate");
const repeatMode = document.querySelector("#repeat");
const sleep = document.querySelector("#sleep");
const pip = document.querySelector("#pip");
const clearQueue = document.querySelector("#clear-queue");
const subtitleInput = document.querySelector("#subtitle-file");

if (input && media && status && queueEl) {
    const maxQueue = 100;
    const resumeKey = "tn-player-resume";
    let queue = [];
    let index = -1;
    let subtitleUrl = null;
    let sleepTimer = 0;
    let suppressError = false;
    let lastSave = 0;

    const say = (text) => { status.textContent = text; };

    const forgetResume = () => {
        try { sessionStorage.removeItem(resumeKey); }
        catch (error) { /* Storage can be blocked. Playback still works. */ }
    };

    const remember = () => {
        const item = queue[index];
        if (!item || !Number.isFinite(media.currentTime) || media.currentTime < 1) return;
        try {
            sessionStorage.setItem(resumeKey, JSON.stringify({
                name: item.file.name,
                size: item.file.size,
                modified: item.file.lastModified,
                time: media.currentTime
            }));
        } catch (error) { /* Resume is optional. */ }
    };

    const restore = (file) => {
        let saved;
        try { saved = JSON.parse(sessionStorage.getItem(resumeKey) || "null"); }
        catch (error) { forgetResume(); return; }
        if (!saved || saved.name !== file.name || saved.size !== file.size || saved.modified !== file.lastModified) return;
        const apply = () => {
            if (saved.time > 1 && Number.isFinite(media.duration) && saved.time < media.duration - 1) {
                media.currentTime = saved.time;
                say(`Selected ${file.name}. Resumed at ${formatTime(saved.time)} for this tab only. Press Play.`);
            }
        };
        if (media.readyState >= 1) apply();
        else media.addEventListener("loadedmetadata", apply, { once: true });
    };

    const revokeSubtitle = () => {
        if (subtitleUrl) URL.revokeObjectURL(subtitleUrl);
        subtitleUrl = null;
        const existing = media.querySelector("track");
        if (existing) existing.remove();
    };

    const srtToVtt = (text) => {
        const cleaned = text.replace(/^\uFEFF/, "").replace(/\r\n?/g, "\n").trim();
        if (/^WEBVTT\b/.test(cleaned)) return cleaned.endsWith("\n") ? cleaned : `${cleaned}\n`;
        const blocks = cleaned.split(/\n{2,}/).map((block) => {
            const lines = block.split("\n");
            if (/^\d+$/.test(lines[0] || "")) lines.shift();
            if (!lines.length || !lines[0].includes("-->")) return "";
            lines[0] = lines[0].replace(/,/g, ".");
            for (let i = 1; i < lines.length; i += 1) {
                lines[i] = lines[i].replace(/&/g, "&amp;").replace(/</g, "&lt;");
            }
            return lines.join("\n");
        }).filter(Boolean);
        return `WEBVTT\n\n${blocks.join("\n\n")}\n`;
    };

    const attachTrack = (vttText, label) => {
        revokeSubtitle();
        subtitleUrl = URL.createObjectURL(new Blob([vttText], { type: "text/vtt" }));
        const track = document.createElement("track");
        track.kind = "subtitles";
        track.label = label || "Local subtitles";
        track.srclang = "und";
        track.src = subtitleUrl;
        track.default = true;
        media.appendChild(track);
        track.addEventListener("load", () => {
            if (track.track) track.track.mode = "showing";
        });
    };

    const renderQueue = () => {
        queueEl.replaceChildren();
        queue.forEach((item, itemIndex) => {
            const li = document.createElement("li");
            if (itemIndex === index) li.setAttribute("aria-current", "true");
            const play = document.createElement("button");
            play.type = "button";
            play.className = "queue-play";
            play.textContent = item.file.name;
            play.setAttribute("aria-label", itemIndex === index ? `Now playing ${item.file.name}` : `Play ${item.file.name}`);
            play.addEventListener("click", () => playAt(itemIndex, false));
            const meta = document.createElement("span");
            meta.className = "queue-meta";
            meta.textContent = formatBytes(item.file.size);
            const remove = document.createElement("button");
            remove.type = "button";
            remove.className = "queue-remove";
            remove.textContent = "Remove";
            remove.setAttribute("aria-label", `Remove ${item.file.name} from the queue`);
            remove.addEventListener("click", () => removeAt(itemIndex));
            li.append(play, meta, remove);
            queueEl.appendChild(li);
        });
        const empty = queue.length === 0;
        queueEl.hidden = empty;
        if (tools) tools.hidden = empty;
    };

    const applyRate = () => {
        if (rate) media.playbackRate = Number(rate.value) || 1;
    };

    const playAt = (itemIndex, autoplay) => {
        const item = queue[itemIndex];
        if (!item) return;
        suppressError = false;
        index = itemIndex;
        const audioOnly = (item.file.type || "").startsWith("audio/") || /\.(mp3|m4a|flac|wav|aac|opus)$/i.test(item.file.name);
        if (audioOnly) media.poster = "assets/tn-player-icon.png";
        else media.removeAttribute("poster");
        media.src = item.url;
        media.hidden = false;
        applyRate();
        renderQueue();
        const count = queue.length > 1 ? ` File ${itemIndex + 1} of ${queue.length}.` : "";
        say(`Selected ${item.file.name}.${count} Press Play to start.`);
        restore(item.file);
        if (autoplay) {
            media.play().catch(() => say(`Selected ${item.file.name}. Press Play to start.`));
        }
    };

    const stopMedia = () => {
        suppressError = true;
        index = -1;
        media.pause();
        media.removeAttribute("src");
        media.hidden = true;
        media.load();
        revokeSubtitle();
    };

    const removeAt = (itemIndex) => {
        const [removed] = queue.splice(itemIndex, 1);
        if (!removed) return;
        const removingCurrent = itemIndex === index;
        if (!queue.length) {
            stopMedia();
            URL.revokeObjectURL(removed.url);
            if (sleepTimer) window.clearTimeout(sleepTimer);
            sleepTimer = 0;
            renderQueue();
            say("Queue cleared. No file selected.");
            return;
        }
        if (removingCurrent) {
            media.pause();
            playAt(Math.min(itemIndex, queue.length - 1), false);
            URL.revokeObjectURL(removed.url);
            return;
        }
        if (itemIndex < index) index -= 1;
        URL.revokeObjectURL(removed.url);
        renderQueue();
        const current = queue[index];
        if (current) say(`Selected ${current.file.name}. File ${index + 1} of ${queue.length}. Press Play to start.`);
    };

    const loadSubtitleFile = (file) => {
        if (file.size > 2 * 1024 * 1024) {
            say("That subtitle file is larger than 2 MB. Choose a smaller WebVTT or plain SRT file.");
            return;
        }
        const reader = new FileReader();
        reader.addEventListener("load", () => {
            const vtt = srtToVtt(String(reader.result || ""));
            if (!vtt.includes("-->")) {
                say("That subtitle file has no cues this page can read. Use WebVTT or plain SRT.");
                return;
            }
            attachTrack(vtt, file.name);
            say(`Subtitles from ${file.name} are ready in this tab. Press Play if playback has not started.`);
        });
        reader.addEventListener("error", () => say("The subtitle file could not be read."));
        reader.readAsText(file);
    };

    const takeFiles = (fileList) => {
        const incoming = [...fileList];
        const playable = incoming.filter((file) => !/\.(srt|vtt)$/i.test(file.name));
        const sidecars = incoming.filter((file) => /\.(srt|vtt)$/i.test(file.name));
        if (!playable.length && sidecars.length && index >= 0) {
            loadSubtitleFile(sidecars[0]);
            return;
        }
        if (!playable.length) {
            say("No playable video or audio was in that selection.");
            return;
        }
        if (playable.length > maxQueue) {
            say(`Choose ${maxQueue} files or fewer. The current queue was left unchanged, and nothing was uploaded.`);
            return;
        }
        queue.forEach((item) => URL.revokeObjectURL(item.url));
        revokeSubtitle();
        queue = playable.map((file) => ({ file, url: URL.createObjectURL(file) }));
        playAt(0, false);
        if (sidecars.length) loadSubtitleFile(sidecars[0]);
    };

    input.addEventListener("change", () => {
        if (input.files && input.files.length) takeFiles(input.files);
        input.value = "";
    });

    if (subtitleInput) {
        subtitleInput.addEventListener("change", () => {
            const file = subtitleInput.files && subtitleInput.files[0];
            if (!file) return;
            if (index < 0) {
                say("Choose a video before adding subtitles.");
                return;
            }
            loadSubtitleFile(file);
        });
    }

    media.addEventListener("loadedmetadata", applyRate);
        media.addEventListener("error", () => {
        if (suppressError || index < 0) return;
        const item = queue[index];
        if (!item || (media.currentSrc && media.currentSrc !== item.url)) return;
        say(`${item.file.name} could not be played. This browser may not support its format or codec. The Android app can play more formats.`);
    });
    media.addEventListener("ended", () => {
        const mode = repeatMode ? repeatMode.value : "off";
        if (mode === "one" && index >= 0) {
            media.currentTime = 0;
            media.play().catch(() => say("Press Play to repeat this file."));
            return;
        }
        if (index >= 0 && index + 1 < queue.length) {
            playAt(index + 1, true);
            return;
        }
        if (mode === "all" && queue.length) {
            playAt(0, true);
            return;
        }
        forgetResume();
        say("Playback finished.");
    });
    media.addEventListener("pause", remember);
    media.addEventListener("timeupdate", () => {
        const now = Date.now();
        if (now - lastSave < 2000) return;
        lastSave = now;
        remember();
    });

    if (rate) rate.addEventListener("change", applyRate);
    if (sleep) {
        sleep.addEventListener("change", () => {
            if (sleepTimer) window.clearTimeout(sleepTimer);
            sleepTimer = 0;
            const minutes = Number(sleep.value);
            if (!minutes) {
                say("Sleep timer off.");
                return;
            }
            sleepTimer = window.setTimeout(() => {
                media.pause();
                sleep.value = "0";
                say(`Sleep timer paused playback after ${minutes} minutes.`);
            }, minutes * 60 * 1000);
            say(`Sleep timer set for ${minutes} minutes in this tab.`);
        });
    }
    if (pip) {
        const supported = document.pictureInPictureEnabled && typeof media.requestPictureInPicture === "function";
        if (!supported) pip.hidden = true;
        pip.addEventListener("click", async () => {
            try {
                if (document.pictureInPictureElement) await document.exitPictureInPicture();
                else if (!media.src) say("Choose a file before using picture in picture.");
                else await media.requestPictureInPicture();
            } catch (error) {
                say("Picture in picture is not available for this file in this browser.");
            }
        });
    }
    if (clearQueue) {
        clearQueue.addEventListener("click", () => {
            queue.forEach((item) => URL.revokeObjectURL(item.url));
            queue = [];
            if (sleepTimer) window.clearTimeout(sleepTimer);
            sleepTimer = 0;
            stopMedia();
            renderQueue();
            say("Queue cleared. No file selected.");
        });
    }

    window.addEventListener("keydown", (event) => {
        if (event.altKey || event.ctrlKey || event.metaKey || index < 0) return;
        const target = event.target;
        if (target && target.closest && target.closest("input, textarea, select, button, a, summary, video")) return;
        const key = event.key;
        if (key === " " || key === "k" || key === "K") {
            event.preventDefault();
            if (media.paused) media.play().catch(() => say("Press Play on the video controls."));
            else media.pause();
        } else if (key === "ArrowLeft") {
            event.preventDefault();
            media.currentTime = Math.max(0, (media.currentTime || 0) - 10);
        } else if (key === "ArrowRight") {
            event.preventDefault();
            const next = (media.currentTime || 0) + 10;
            media.currentTime = Number.isFinite(media.duration) ? Math.min(media.duration, next) : next;
        } else if (key === "m" || key === "M") {
            media.muted = !media.muted;
            const name = queue[index] ? queue[index].file.name : "Playback";
            say(media.muted ? `Muted. ${name} stays selected.` : `Sound on. ${name} stays selected.`);
        } else if (key === "f" || key === "F") {
            if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
            else if (media.requestFullscreen) media.requestFullscreen().catch(() => say("Full screen is not available."));
        } else if ((key === "n" || key === "N") && index + 1 < queue.length) {
            playAt(index + 1, true);
        } else if ((key === "p" || key === "P") && index > 0) {
            playAt(index - 1, true);
        }
    });

    const hasFiles = (event) => event.dataTransfer && [...event.dataTransfer.types].includes("Files");
    let dragDepth = 0;
    window.addEventListener("dragenter", (event) => {
        if (!hasFiles(event)) return;
        event.preventDefault();
        dragDepth += 1;
        document.body.classList.add("is-dropping");
    });
    window.addEventListener("dragover", (event) => {
        if (hasFiles(event)) event.preventDefault();
    });
    window.addEventListener("dragleave", () => {
        dragDepth = Math.max(0, dragDepth - 1);
        if (!dragDepth) document.body.classList.remove("is-dropping");
    });
    window.addEventListener("drop", (event) => {
        if (!hasFiles(event)) return;
        event.preventDefault();
        dragDepth = 0;
        document.body.classList.remove("is-dropping");
        takeFiles(event.dataTransfer.files);
    });

    window.addEventListener("pagehide", (event) => {
        if (event.persisted) return;
        queue.forEach((item) => URL.revokeObjectURL(item.url));
        revokeSubtitle();
    });
}

const install = document.querySelector("#install");
if (install) {
    let prompt;
    window.addEventListener("beforeinstallprompt", (event) => {
        event.preventDefault();
        prompt = event;
        install.hidden = false;
    });
    install.addEventListener("click", async () => {
        const event = prompt;
        prompt = null;
        install.hidden = true;
        if (!event) return;
        try { await event.prompt(); }
        catch (error) { console.warn("TN-Player install prompt failed:", error); }
    });
    window.addEventListener("appinstalled", () => {
        prompt = null;
        install.hidden = true;
    });
}

const buildMeta = document.querySelector("#build-meta");
const checksum = document.querySelector("#checksum");
const copyChecksum = document.querySelector("#copy-checksum");
const checksumStatus = document.querySelector("#checksum-status");

if (buildMeta) {
    fetch("./build-info.json", { cache: "no-store" }).then((response) => {
        if (!response.ok) throw new Error(String(response.status));
        return response.json();
    }).then((info) => {
        const version = info.version || "1.0.0";
        const size = formatBytes(Number(info.apk_bytes));
        buildMeta.replaceChildren();
        buildMeta.append(`Version ${version} · Development APK · Android 5.0+`);
        if (size) buildMeta.append(` · ${size}`);
        if (typeof info.commit === "string" && /^[a-f0-9]{7,40}$/i.test(info.commit)) {
            buildMeta.append(" · published source ");
            const link = document.createElement("a");
            link.href = `https://github.com/Tajoor621/TN-Player/commit/${info.commit}`;
            link.textContent = info.commit.slice(0, 7);
            buildMeta.append(link);
        }
    }).catch(() => {});
}

if (checksum) {
    fetch("./downloads/SHA256SUMS.txt", { cache: "no-store" }).then((response) => {
        if (!response.ok) throw new Error(String(response.status));
        return response.text();
    }).then((text) => {
        const line = text.split(/\r?\n/).find((row) => row.includes("TN-Player-1.0.0-debug.apk"));
        const sum = line ? line.trim().split(/\s+/)[0] : "";
        if (/^[a-f0-9]{64}$/i.test(sum)) checksum.textContent = sum;
    }).catch(() => {});
}

if (copyChecksum && checksum && checksumStatus) {
    copyChecksum.addEventListener("click", async () => {
        const sum = checksum.textContent.trim();
        if (!/^[a-f0-9]{64}$/i.test(sum)) {
            checksumStatus.textContent = "Checksum file has not loaded yet. Open SHA256SUMS.txt.";
            return;
        }
        const selectChecksum = () => {
            const range = document.createRange();
            range.selectNodeContents(checksum);
            const selection = window.getSelection();
            selection.removeAllRanges();
            selection.addRange(range);
        };
        try {
            await navigator.clipboard.writeText(sum);
            checksumStatus.textContent = "Checksum copied.";
        } catch (error) {
            selectChecksum();
            let copied = false;
            try { copied = document.execCommand("copy"); }
            catch (copyError) { copied = false; }
            checksumStatus.textContent = copied ? "Checksum copied." : "The checksum is selected. Copy it from the page.";
        }
    });
}
