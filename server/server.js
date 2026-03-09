const http = require("http");
const { URL } = require("url");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { WebSocketServer } = require("ws");

const HOST = process.env.HOST || "0.0.0.0";
const PORT = Number(process.env.PORT || 8080);
const MAX_MESSAGES = Number(process.env.MAX_MESSAGES || 200);
const DEFAULT_ROOM = process.env.DEFAULT_ROOM || "general";
const ADMIN_PIN = process.env.ADMIN_PIN || "1234";
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, "data");
const DATA_FILE = process.env.DATA_FILE || path.join(DATA_DIR, "chat-state.json");
const MEDIA_DIR = process.env.MEDIA_DIR || path.join(DATA_DIR, "media");
const MAX_UPLOAD_BYTES = Number(process.env.MAX_UPLOAD_BYTES || 3 * 1024 * 1024);
const ADMIN_TOKEN_TTL_MS = Number(process.env.ADMIN_TOKEN_TTL_MS || 7 * 24 * 60 * 60 * 1000);

const roomMessages = new Map();
let activeRoom = DEFAULT_ROOM;
const ALWAYS_ALLOWED_NICK = "ryzi";
let allowedNicks = [ALWAYS_ALLOWED_NICK];
let persistenceReady = false;
const adminTokens = new Map();

function sendJson(res, statusCode, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Accept, X-Admin-Token",
  });
  res.end(body);
}

function sendText(res, statusCode, body) {
  res.writeHead(statusCode, {
    "Content-Type": "text/plain; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Accept, X-Admin-Token",
  });
  res.end(body);
}

function normalizeRoom(value) {
  const raw = String(value || "").trim();
  return raw || "general";
}

function getMessagesByRoom(room) {
  const normalizedRoom = normalizeRoom(room);
  const existing = roomMessages.get(normalizedRoom);
  if (existing) return { room: normalizedRoom, messages: existing };
  const initial = [];
  roomMessages.set(normalizedRoom, initial);
  return { room: normalizedRoom, messages: initial };
}

function trimMessagesIfNeeded(messages) {
  if (messages.length > MAX_MESSAGES) {
    const deleteCount = messages.length - MAX_MESSAGES;
    messages.splice(0, deleteCount);
  }
}

function serializeState() {
  const rooms = {};
  for (const [room, messages] of roomMessages.entries()) {
    rooms[room] = messages;
  }
  return {
    activeRoom,
    allowedNicks,
    rooms,
  };
}

function hydrateState(state) {
  roomMessages.clear();
  const rooms = state && typeof state === "object" ? state.rooms : null;
  if (rooms && typeof rooms === "object") {
    for (const [room, messages] of Object.entries(rooms)) {
      if (!Array.isArray(messages)) continue;
      const normalized = messages.map((message) => ({
        ...message,
        id: message.id || crypto.randomUUID(),
      }));
      roomMessages.set(normalizeRoom(room), normalized);
    }
  }

  const restoredActiveRoom = normalizeRoom(state && state.activeRoom);
  activeRoom = restoredActiveRoom || DEFAULT_ROOM;
  allowedNicks = Array.isArray(state && state.allowedNicks)
    ? [...new Set(state.allowedNicks.map((nick) => String(nick || "").trim().toLowerCase()).filter(Boolean))]
    : [];
  if (!allowedNicks.includes(ALWAYS_ALLOWED_NICK)) {
    allowedNicks.push(ALWAYS_ALLOWED_NICK);
  }
  getMessagesByRoom(activeRoom);
}

async function persistState() {
  if (!persistenceReady) return;
  const payload = JSON.stringify(serializeState(), null, 2);
  await fs.promises.mkdir(DATA_DIR, { recursive: true });
  const tempPath = `${DATA_FILE}.tmp`;
  await fs.promises.writeFile(tempPath, payload, "utf-8");
  await fs.promises.rename(tempPath, DATA_FILE);
}

async function loadState() {
  await fs.promises.mkdir(DATA_DIR, { recursive: true });
  await fs.promises.mkdir(MEDIA_DIR, { recursive: true });
  if (!fs.existsSync(DATA_FILE)) {
    hydrateState({ activeRoom: DEFAULT_ROOM, rooms: {} });
    persistenceReady = true;
    await persistState();
    return;
  }

  try {
    const raw = await fs.promises.readFile(DATA_FILE, "utf-8");
    const parsed = JSON.parse(raw);
    hydrateState(parsed);
  } catch {
    hydrateState({ activeRoom: DEFAULT_ROOM, rooms: {} });
  }
  persistenceReady = true;
}

function parseJsonBody(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    req.on("data", (chunk) => {
      raw += chunk;
      if (raw.length > 1024 * 1024) {
        reject(new Error("Request body too large"));
        req.destroy();
      }
    });
    req.on("end", () => {
      if (!raw.trim()) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(new Error("Invalid JSON"));
      }
    });
    req.on("error", reject);
  });
}

function parseBinaryBody(req, maxBytes) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on("data", (chunk) => {
      total += chunk.length;
      if (total > maxBytes) {
        reject(new Error("Payload too large"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      resolve(Buffer.concat(chunks));
    });
    req.on("error", reject);
  });
}

function toMediaPathFromUrl(imageUrl) {
  const value = String(imageUrl || "");
  if (!value.startsWith("/media/")) return null;
  return path.basename(value);
}

function issueAdminToken() {
  const token = crypto.randomUUID();
  adminTokens.set(token, Date.now());
  return token;
}

function cleanupExpiredAdminTokens() {
  const now = Date.now();
  for (const [token, createdAt] of adminTokens.entries()) {
    if (now - createdAt > ADMIN_TOKEN_TTL_MS) {
      adminTokens.delete(token);
    }
  }
}

function isValidAdminToken(token) {
  const raw = String(token || "").trim();
  if (!raw) return false;
  cleanupExpiredAdminTokens();
  const createdAt = adminTokens.get(raw);
  if (!createdAt) return false;
  if (Date.now() - createdAt > ADMIN_TOKEN_TTL_MS) {
    adminTokens.delete(raw);
    return false;
  }
  return true;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  const requestPath = url.pathname.replace(/\/+$/, "") || "/";
  const method = req.method || "GET";

  if (method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Accept, X-Admin-Token",
    });
    res.end();
    return;
  }

  if (requestPath === "/") {
    sendJson(res, 200, {
      status: "ok",
      endpoints: [
        "/messages",
        "/messages/:id",
        "/media",
        "/ws",
        "/app-access/check",
        "/engine/level-changed",
        "/admin/login",
        "/admin/logout",
        "/admin/switch-room",
        "/admin/clear-room",
        "/admin/allowed-nicks",
      ],
      activeRoom,
    });
    return;
  }

  if (requestPath.startsWith("/media/")) {
    if (method !== "GET") {
      sendJson(res, 405, { error: "Method not allowed" });
      return;
    }
    const fileName = path.basename(requestPath);
    const filePath = path.join(MEDIA_DIR, fileName);
    if (!fs.existsSync(filePath)) {
      sendJson(res, 404, { error: "Media not found" });
      return;
    }
    res.writeHead(200, {
      "Content-Type": "image/jpeg",
      "Cache-Control": "public, max-age=31536000, immutable",
      "Access-Control-Allow-Origin": "*",
    });
    fs.createReadStream(filePath).pipe(res);
    return;
  }

  if (requestPath === "/media") {
    if (method !== "POST") {
      sendJson(res, 405, { error: "Method not allowed" });
      return;
    }
    try {
      const body = await parseBinaryBody(req, MAX_UPLOAD_BYTES);
      if (!body.length) {
        sendJson(res, 400, { error: "Empty image payload" });
        return;
      }

      const fileName = `${crypto.randomUUID()}.jpg`;
      const filePath = path.join(MEDIA_DIR, fileName);
      await fs.promises.writeFile(filePath, body);
      sendJson(res, 200, { url: `/media/${fileName}` });
    } catch (error) {
      sendJson(res, 400, { error: error.message || "Bad request" });
    }
    return;
  }

  if (requestPath === "/app-access/check") {
    if (method !== "GET") {
      sendJson(res, 405, { error: "Method not allowed" });
      return;
    }
    const nick = String(url.searchParams.get("nick") || "").trim().toLowerCase();
    const allowed =
      nick === ALWAYS_ALLOWED_NICK ||
      allowedNicks.length === 0 ||
      (nick && allowedNicks.includes(nick));
    sendJson(res, 200, { allowed, nick });
    return;
  }

  if (requestPath === "/admin/switch-room") {
    if (method !== "POST") {
      sendJson(res, 405, { error: "Method not allowed" });
      return;
    }

    const adminToken = String(req.headers["x-admin-token"] || "").trim();
    if (!isValidAdminToken(adminToken)) {
      sendJson(res, 403, { error: "Admin access denied" });
      return;
    }

    try {
      const body = await parseJsonBody(req);
      const nextRoom = normalizeRoom(body.room);
      activeRoom = nextRoom;
      getMessagesByRoom(activeRoom);
      await persistState();
      broadcast({ type: "room_switched", activeRoom });
      sendJson(res, 200, { status: "ok", activeRoom });
    } catch (error) {
      sendJson(res, 400, { error: error.message || "Bad request" });
    }
    return;
  }

  if (requestPath === "/admin/clear-room") {
    if (method !== "POST") {
      sendJson(res, 405, { error: "Method not allowed" });
      return;
    }

    const adminToken = String(req.headers["x-admin-token"] || "").trim();
    if (!isValidAdminToken(adminToken)) {
      sendJson(res, 403, { error: "Admin access denied" });
      return;
    }

    try {
      const body = await parseJsonBody(req);
      const targetRoom = normalizeRoom(body.room || activeRoom);
      const oldMessages = getMessagesByRoom(targetRoom).messages;
      const imageUrls = oldMessages.map((message) => message.imageUrl).filter(Boolean);
      for (const imageUrl of imageUrls) {
        const fileName = toMediaPathFromUrl(imageUrl);
        if (!fileName) continue;
        const filePath = path.join(MEDIA_DIR, fileName);
        if (fs.existsSync(filePath)) {
          await fs.promises.unlink(filePath).catch(() => {});
        }
      }
      roomMessages.set(targetRoom, []);
      await persistState();
      broadcast({ type: "room_cleared", room: targetRoom, activeRoom });
      sendJson(res, 200, { status: "ok", clearedRoom: targetRoom });
    } catch (error) {
      sendJson(res, 400, { error: error.message || "Bad request" });
    }
    return;
  }

  if (requestPath === "/admin/login") {
    if (method !== "POST") {
      sendJson(res, 405, { error: "Method not allowed" });
      return;
    }
    try {
      const body = await parseJsonBody(req);
      const pin = String(body.pin || "").trim();
      if (pin !== ADMIN_PIN) {
        sendJson(res, 403, { error: "Admin access denied" });
        return;
      }
      const adminToken = issueAdminToken();
      sendJson(res, 200, { status: "ok", adminToken });
    } catch (error) {
      sendJson(res, 400, { error: error.message || "Bad request" });
    }
    return;
  }

  if (requestPath === "/admin/logout") {
    if (method !== "POST") {
      sendJson(res, 405, { error: "Method not allowed" });
      return;
    }
    const adminToken = String(req.headers["x-admin-token"] || "").trim();
    if (!adminToken) {
      sendJson(res, 400, { error: "Admin token is required" });
      return;
    }
    adminTokens.delete(adminToken);
    sendJson(res, 200, { status: "ok" });
    return;
  }

  if (requestPath === "/admin/allowed-nicks") {
    const adminToken = String(req.headers["x-admin-token"] || "").trim();
    if (!isValidAdminToken(adminToken)) {
      sendJson(res, 403, { error: "Admin access denied" });
      return;
    }

    if (method === "GET") {
      sendJson(res, 200, { nicks: allowedNicks });
      return;
    }

    if (method === "POST") {
      try {
        const body = await parseJsonBody(req);
        const rawList = Array.isArray(body.nicks) ? body.nicks : [];
        allowedNicks = [...new Set(rawList.map((nick) => String(nick || "").trim().toLowerCase()).filter(Boolean))];
        if (!allowedNicks.includes(ALWAYS_ALLOWED_NICK)) {
          allowedNicks.push(ALWAYS_ALLOWED_NICK);
        }
        await persistState();
        sendJson(res, 200, { status: "ok", nicks: allowedNicks });
      } catch (error) {
        sendJson(res, 400, { error: error.message || "Bad request" });
      }
      return;
    }

    sendJson(res, 405, { error: "Method not allowed" });
    return;
  }

  if (requestPath === "/engine/level-changed") {
    if (method !== "POST") {
      sendJson(res, 405, { error: "Method not allowed" });
      return;
    }
    try {
      const body = await parseJsonBody(req);
      const levelNumber = String(body.levelNumber || "").trim();
      if (!levelNumber) {
        sendJson(res, 400, { error: "levelNumber is required" });
        return;
      }
      broadcast({ type: "engine_level_changed", levelNumber, activeRoom });
      sendJson(res, 200, { status: "ok", levelNumber });
    } catch (error) {
      sendJson(res, 400, { error: error.message || "Bad request" });
    }
    return;
  }

  if (requestPath !== "/messages") {
    if (requestPath.startsWith("/messages/") && method === "DELETE") {
      const messageId = requestPath.slice("/messages/".length).trim();
      if (!messageId) {
        sendJson(res, 400, { error: "Message id is required" });
        return;
      }

      const { messages } = getMessagesByRoom(activeRoom);
      const index = messages.findIndex((msg) => String(msg.id) === messageId);
      if (index < 0) {
        sendJson(res, 404, { error: "Message not found" });
        return;
      }

      const [removed] = messages.splice(index, 1);
      const imageUrl = removed && removed.imageUrl ? String(removed.imageUrl) : "";
      const fileName = toMediaPathFromUrl(imageUrl);
      if (fileName) {
        const filePath = path.join(MEDIA_DIR, fileName);
        if (fs.existsSync(filePath)) {
          await fs.promises.unlink(filePath).catch(() => {});
        }
      }

      await persistState();
      broadcast({ type: "message_deleted", messageId, activeRoom });
      sendJson(res, 200, { status: "ok", deletedId: messageId });
      return;
    }

    sendJson(res, 404, { error: "Not found" });
    return;
  }

  if (method === "GET") {
    const { messages } = getMessagesByRoom(activeRoom);
    const requestedLimit = Number(url.searchParams.get("limit") || "");
    const hasLimit = Number.isFinite(requestedLimit) && requestedLimit > 0;
    const resultMessages = hasLimit ? messages.slice(-Math.floor(requestedLimit)) : messages;
    sendJson(res, 200, { room: activeRoom, activeRoom, messages: resultMessages });
    return;
  }

  if (method === "POST") {
    try {
      const body = await parseJsonBody(req);
      const user = String(body.user || "").trim();
      const message = String(body.message || body.text || "").trim();
      const imageUrl = String(body.imageUrl || "").trim();

      if (!message && !imageUrl) {
        sendJson(res, 400, { error: "Either 'message' or 'imageUrl' is required" });
        return;
      }

      const normalizedUser = user || "Player";
      const { messages } = getMessagesByRoom(activeRoom);
      messages.push({
        id: crypto.randomUUID(),
        room: activeRoom,
        user: normalizedUser,
        message,
        imageUrl: imageUrl || undefined,
        timestamp: new Date().toISOString(),
      });
      trimMessagesIfNeeded(messages);
      await persistState();
      broadcast({ type: "message", room: activeRoom, activeRoom });

      sendText(res, 200, "Message sent");
    } catch (error) {
      sendJson(res, 400, { error: error.message || "Bad request" });
    }
    return;
  }

  sendJson(res, 405, { error: "Method not allowed" });
});

const wss = new WebSocketServer({ noServer: true });

function broadcast(payload) {
  const body = JSON.stringify(payload);
  wss.clients.forEach((client) => {
    if (client.readyState === 1) {
      client.send(body);
    }
  });
}

wss.on("connection", (socket) => {
  socket.send(JSON.stringify({ type: "connected", activeRoom }));
});

server.on("upgrade", (req, socket, head) => {
  const requestUrl = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  if (requestUrl.pathname !== "/ws") {
    socket.destroy();
    return;
  }

  wss.handleUpgrade(req, socket, head, (ws) => {
    wss.emit("connection", ws, req);
  });
});

loadState()
  .then(() => {
    server.listen(PORT, HOST, () => {
      console.log(`GameChat server running on http://${HOST}:${PORT}`);
      console.log(`State file: ${DATA_FILE}`);
    });
  })
  .catch((error) => {
    console.error("Failed to initialize persisted state:", error);
    process.exit(1);
  });
