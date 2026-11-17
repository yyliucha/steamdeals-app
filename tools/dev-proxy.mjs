// 本地联调代理：模拟站点仓库 CA-1 的 /api/app/* 前缀重写（docs/APP-API.md §2）。
// 用法（在 steamdeals-app 仓库根目录）：
//   node tools/dev-proxy.mjs --port 8888 --backend http://127.0.0.1:8787 [--latest tools/mock-latest.json] [--apk app/build/outputs/apk/debug/app-debug.apk]
// 行为：
//   /api/app/status  -> 本地实现（{ banned:false }；MOCK_BANNED=ip|device 可模拟封禁）
//   /api/app/latest  -> 读 --latest 指定的 JSON（内置更新联调用，缺省返回 null 字段）
//   /mock/*.apk      -> 读 --apk 指定的文件（下载 + SHA-256 联调用）
//   /api/app/<rest>  -> 去前缀转发到 backend 的 /api/<rest>（保留 method/query/body/Authorization/X-Device-Id）
//   其余路径         -> 原样转发 backend（如 /img/steam/* 封面代理）
import http from 'node:http';
import fs from 'node:fs';
import crypto from 'node:crypto';

function arg(name, def) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : def;
}

const PORT = Number(arg('--port', 8888));
const BACKEND = arg('--backend', 'http://127.0.0.1:8787');
const LATEST_FILE = arg('--latest', '');
const APK_FILE = arg('--apk', '');
const MOCK_BANNED = process.env.MOCK_BANNED || ''; // 'ip' | 'device' | ''（空 = 不封禁）

function latestPayload() {
  if (!LATEST_FILE || !fs.existsSync(LATEST_FILE)) {
    return { version: null, versionCode: null, minVersionCode: null, notes: null, apkPath: null, sha256: null, size: null, releasedAt: null };
  }
  const data = JSON.parse(fs.readFileSync(LATEST_FILE, 'utf8'));
  const notes = data.notes == null ? null : Array.isArray(data.notes) ? data.notes : [data.notes];
  return {
    version: data.version ?? null,
    versionCode: data.versionCode == null ? null : Number(data.versionCode),
    minVersionCode: data.minVersionCode == null ? null : Number(data.minVersionCode),
    notes,
    apkPath: data.apkPath ?? null,
    sha256: data.sha256 ?? null,
    size: data.size == null ? null : Number(data.size),
    releasedAt: data.releasedAt ?? null,
  };
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const path = url.pathname;

  // /api/app/status：启动封禁检查（无副作用、豁免封禁；MOCK_BANNED 模拟命中）
  if (path === '/api/app/status' && req.method === 'GET') {
    const banned = MOCK_BANNED === 'ip' || MOCK_BANNED === 'device';
    const body = JSON.stringify({ banned, type: banned ? MOCK_BANNED : null });
    res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    res.end(body);
    return;
  }

  // /api/app/latest：内置更新元数据
  if (path === '/api/app/latest' && req.method === 'GET') {
    res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    res.end(JSON.stringify(latestPayload()));
    return;
  }

  // /mock/*.apk：联调用“新版本”APK（直接回发本机 APK 文件）
  if (path.startsWith('/mock/') && path.endsWith('.apk')) {
    if (!APK_FILE || !fs.existsSync(APK_FILE)) {
      res.writeHead(404);
      res.end('no mock apk');
      return;
    }
    const buf = fs.readFileSync(APK_FILE);
    res.writeHead(200, {
      'content-type': 'application/vnd.android.package-archive',
      'content-length': buf.length,
    });
    res.end(buf);
    return;
  }

  // /api/app/<rest> → /api/<rest>（前缀重写）；其余路径原样转发
  const targetPath = path.startsWith('/api/app/') ? '/api' + path.slice('/api/app'.length) : path;
  const target = BACKEND + targetPath + (url.search ?? '');

  const chunks = [];
  for await (const c of req) chunks.push(c);
  const body = Buffer.concat(chunks);
  const headers = {};
  for (const [k, v] of Object.entries(req.headers)) {
    if (['host', 'connection', 'content-length', 'accept-encoding'].includes(k)) continue;
    headers[k] = v;
  }
  try {
    const upstream = await fetch(target, {
      method: req.method,
      headers,
      body: body.length ? body : undefined,
    });
    const buf = Buffer.from(await upstream.arrayBuffer());
    const outHeaders = {};
    for (const [k, v] of upstream.headers) outHeaders[k] = v;
    res.writeHead(upstream.status, outHeaders);
    res.end(buf);
  } catch (err) {
    res.writeHead(502, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ error: `proxy_failed: ${err.message}` }));
  }
});

server.listen(PORT, () => {
  console.log(`dev-proxy: http://127.0.0.1:${PORT} -> ${BACKEND}`);
  console.log(`  latest: ${LATEST_FILE || '(null 字段)'}  apk: ${APK_FILE || '(无)'}  MOCK_BANNED: ${MOCK_BANNED || '(不封禁)'}`);
});

// 计算 mock APK 的 sha256（联调时用）
if (APK_FILE && fs.existsSync(APK_FILE)) {
  const sha = crypto.createHash('sha256').update(fs.readFileSync(APK_FILE)).digest('hex');
  console.log(`  apk sha256: ${sha}`);
}
