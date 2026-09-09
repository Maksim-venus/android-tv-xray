const state = {
  nodes: [],
  subs: [],
  status: null,
  logs: [],
  lastProbe: null,
  view: "nodes",
  modalMode: "import",
  demo: false,
};

const $ = (id) => document.getElementById(id);

function toast(msg) {
  const el = $("toast");
  el.textContent = msg;
  el.classList.remove("hidden");
  setTimeout(() => el.classList.add("hidden"), 2800);
}

async function api(path, options) {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : {};
  if (!res.ok) throw new Error(data.error || res.statusText || "请求失败");
  return data;
}

function demoData() {
  state.demo = true;
  state.status = {
    running: false,
    usingStub: false,
    selectedNodeId: null,
    selectedNodeName: null,
    allowInsecureSsl: false,
    httpEditEnabled: true,
    lanUrl: location.origin,
    message: "预览模式（未连接设备 API）",
  };
  state.nodes = [];
  state.subs = [];
  state.logs = [];
}

async function loadAll() {
  try {
    state.status = await api("/api/status");
    state.nodes = await api("/api/nodes");
    state.subs = await api("/api/subscriptions");
    await loadLogs();
    state.demo = false;
  } catch (err) {
    demoData();
    toast("当前为静态预览，API 将在电视端 Ktor 服务中可用");
  }
  render();
}

async function loadLogs() {
  try {
    const onlyErr = $("logErrorsOnly")?.checked;
    const q = onlyErr ? "?level=error" : "";
    const data = await api("/api/logs" + q);
    state.logs = data.items || [];
    if (data.latestError && state.status) {
      state.status.lastError = data.latestError.message;
      state.status.lastErrorAt = data.latestError.at;
    }
  } catch {
    if (!state.demo) return;
    state.logs = [];
  }
}

function render() {
  const running = !!state.status?.running;
  $("runDot").classList.toggle("on", running);
  $("runText").textContent = running ? "Passwall 已运行" : "Passwall 已停止";
  $("lanHint").textContent = state.status?.lanUrl
    ? `已连接局域网 · ${state.status.lanUrl.replace(/^https?:\/\//, "")}`
    : "未连接局域网";
  $("optInsecure").checked = !!state.status?.allowInsecureSsl;
  $("optHttp").checked = !!state.status?.httpEditEnabled;
  $("sysUrl").textContent = state.status?.lanUrl || "—";
  $("sysGeo").textContent = state.status?.routingAssetsUpdatedAt
    ? new Date(state.status.routingAssetsUpdatedAt).toLocaleString()
    : "尚未更新（使用 APK 内置）";
  if (state.lastProbe) showLastProbe(state.lastProbe);
  const err = state.status?.lastError;
  const showErr = !running && !!err;
  const errText = showErr ? `启动失败：${err}` : "";
  ["lastErrorBox", "sysError"].forEach((id) => {
    const el = $(id);
    if (!el) return;
    el.textContent = errText;
    el.classList.toggle("hidden", !showErr);
  });
  renderLogs();

  const q = ($("search").value || "").toLowerCase();
  const proto = $("protoFilter").value;
  const rows = state.nodes.filter((n) => {
    const hit = !q || `${n.name} ${n.host}`.toLowerCase().includes(q);
    return hit && (!proto || n.protocol === proto);
  });
  $("nodeBody").innerHTML = rows.map((n) => {
    const lat = n.latencyMs == null ? "—" : `${n.latencyMs} ms`;
    const latCls = n.latencyMs == null ? "fail" : n.latencyMs > 120 ? "slow" : "";
    return `<tr>
      <td><div>${escapeHtml(n.name)}${n.selected ? " · 当前" : ""}</div><div class="host">${escapeHtml(n.host)}:${n.port}</div></td>
      <td><span class="badge ${escapeHtml(n.protocol)}">${escapeHtml(n.protocol)}</span></td>
      <td class="latency ${latCls}">${lat}</td>
      <td>
        <button class="btn tiny" data-act="select" data-id="${n.id}">使用</button>
        <button class="btn tiny" data-act="ping" data-id="${n.id}">Ping</button>
        <button class="btn tiny" data-act="del" data-id="${n.id}">删除</button>
      </td>
    </tr>`;
  }).join("");
  $("nodeEmpty").classList.toggle("hidden", rows.length > 0);

  const subHtml = (state.subs || []).map((s) => `
    <div class="sub-item">
      <div>
        <div>${escapeHtml(s.name)}</div>
        <div class="sub-url">${escapeHtml(maskUrl(s.url))} · ${s.nodeCount} 个节点</div>
      </div>
      <div>
        <button class="btn tiny" data-sub="refresh" data-id="${s.id}">刷新</button>
        <button class="btn tiny" data-sub="del" data-id="${s.id}">删除</button>
      </div>
    </div>`).join("");
  $("subList").innerHTML = subHtml;
  $("subList2").innerHTML = subHtml || "";
  $("subEmpty").classList.toggle("hidden", (state.subs || []).length > 0);
}

function renderLogs() {
  const list = $("logList");
  const empty = $("logEmpty");
  if (!list) return;
  const items = state.logs || [];
  empty?.classList.toggle("hidden", items.length > 0);
  list.innerHTML = items.map((e) => {
    const t = formatTime(e.at);
    const lvl = e.level === "error" ? "错误" : e.level === "warn" ? "警告" : "信息";
    return `<li class="log-line ${escapeHtml(e.level)}">
      <span class="log-time">${t}</span>
      <span class="log-lvl">${lvl}</span>
      <span class="log-src">${escapeHtml(e.source || "")}</span>
      <span class="log-msg">${escapeHtml(e.message)}</span>
    </li>`;
  }).join("");
  const meta = $("logMeta");
  if (meta) meta.textContent = items.length ? `${items.length} 条 · 仅保留 7 天 · 每 4 秒刷新` : "仅保留 7 天 · 自动刷新中";
}

function formatTime(ms) {
  if (!ms) return "—";
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, "0");
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function escapeHtml(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[c]));
}

function maskUrl(url) {
  try {
    const u = new URL(url);
    return `${u.origin}/****`;
  } catch {
    return url.slice(0, 24) + "…";
  }
}

function showModal(mode) {
  state.modalMode = mode;
  $("modal").classList.remove("hidden");
  if (mode === "import") {
    $("modalTitle").textContent = "导入节点链接";
    $("modalHint").textContent = "分享链接（支持 ss://、vmess://、vless://、trojan://、hysteria2:// 等）。每行一个链接，导入后将自动解析并添加到节点列表。";
    $("modalText").placeholder = "vless://uuid@host:443?security=tls&type=tcp#东京-1";
    $("modalOk").textContent = "导入";
  } else {
    $("modalTitle").textContent = "添加订阅";
    $("modalHint").textContent = "第一行填写名称，第二行填写订阅 URL。拉取目前为 Stub，解析器已就绪。";
    $("modalText").placeholder = "机场订阅 A\nhttps://example.com/sub";
    $("modalOk").textContent = "添加";
  }
}

function hideModal() {
  $("modal").classList.add("hidden");
  $("modalText").value = "";
}

document.querySelectorAll(".nav-item").forEach((btn) => {
  btn.addEventListener("click", () => {
    state.view = btn.dataset.view;
    document.querySelectorAll(".nav-item").forEach((b) => b.classList.toggle("active", b === btn));
    document.querySelectorAll(".view").forEach((v) => v.classList.add("hidden"));
    const viewId = state.view === "subs" ? "subs"
      : state.view === "system" ? "system"
      : state.view === "logs" ? "logs"
      : "nodes";
    $("view-" + viewId).classList.remove("hidden");
    $("pageTitle").textContent = ({
      subs: "订阅管理",
      system: "系统",
      logs: "日志",
    })[state.view] || "节点管理";
    $("pageSub").textContent = state.view === "system"
      ? "代理启停、allowInsecure（Xray 26.1.13）与 HTTP 编辑。外网探测走 SOCKS 查 ipinfo.io。"
      : state.view === "logs"
        ? "VPN / Xray 运行记录，仅保留 7 天；清空会删全部"
        : "集中管理您的代理节点、订阅与网络配置";
    if (state.view === "logs") loadLogs().then(renderLogs);
  });
});

$("logErrorsOnly")?.addEventListener("change", async () => {
  await loadLogs();
  renderLogs();
});
$("btnRefreshLogs")?.addEventListener("click", async () => {
  await loadLogs();
  render();
});
$("btnClearLogs")?.addEventListener("click", async () => {
  try {
    await api("/api/logs", { method: "DELETE" });
    await loadLogs();
    render();
    toast("日志已清空");
  } catch (e) {
    toast(state.demo ? "预览模式无法清空" : e.message);
  }
});

setInterval(async () => {
  if (state.demo) return;
  try {
    state.status = await api("/api/status");
    if (state.view === "logs" || state.status?.lastError) await loadLogs();
    render();
  } catch { /* keep last snapshot */ }
}, 4000);

$("btnImport").onclick = () => showModal("import");
$("btnAddSub").onclick = () => showModal("sub");
$("modalCancel").onclick = hideModal;
$("refreshBtn").onclick = loadAll;
$("search").oninput = render;
$("protoFilter").onchange = render;

$("modalOk").onclick = async () => {
  const text = $("modalText").value.trim();
  if (!text) return toast("请输入内容");
  try {
    if (state.modalMode === "import") {
      const r = await api("/api/nodes/import", { method: "POST", body: JSON.stringify({ text }) });
      toast(`已导入 ${r.imported} 条` + (r.errors?.length ? `，${r.errors.length} 条失败` : ""));
    } else {
      const [name, url] = text.split("\n").map((s) => s.trim());
      await api("/api/subscriptions", { method: "POST", body: JSON.stringify({ name: name || "订阅", url: url || name }) });
      toast("已添加订阅");
    }
    hideModal();
    await loadAll();
  } catch (e) {
    if (state.demo) {
      toast("预览模式：请在电视端开启 HTTP 编辑后使用真实导入");
      hideModal();
    } else toast(e.message);
  }
};

$("btnLatency").onclick = async () => {
  try {
    await api("/api/nodes/latency", { method: "POST", body: "{}" });
    await loadAll();
    toast("延迟测试完成");
  } catch (e) { toast(state.demo ? "预览模式无法测延迟" : e.message); }
};

$("btnTcpPing").onclick = async () => {
  try {
    const r = await api("/api/nodes/tcp-ping", { method: "POST", body: "{}" });
    toast(r.ok ? `TCP Ping ${r.latencyMs} ms（仅节点端口）` : `失败：${r.error}`);
    await loadAll();
  } catch (e) { toast(state.demo ? "预览模式无法 TCP Ping" : e.message); }
};

function formatProbe(r) {
  if (r?.message) return r.message;
  if (r?.ok && r.exitIp) return `${r.flag || "🌐"} 出口 ${r.exitIp}`;
  return r?.ok ? "外网可达" : (r?.error || "外网不可达");
}

function showLastProbe(r) {
  state.lastProbe = r;
  const el = $("sysProbe");
  if (!el || !r) return;
  el.textContent = formatProbe(r);
}

async function runOutboundProbe() {
  try {
    const r = await api("/api/proxy/probe", { method: "POST", body: "{}" });
    showLastProbe(r);
    toast(formatProbe(r));
    await loadAll();
    showLastProbe(r);
  } catch (e) { toast(state.demo ? "预览模式无法探测外网" : e.message); }
}
if ($("btnProbe")) $("btnProbe").onclick = runOutboundProbe;
$("btnSysProbe")?.addEventListener("click", runOutboundProbe);

$("nodeBody").onclick = async (ev) => {
  const btn = ev.target.closest("button[data-act]");
  if (!btn) return;
  const id = btn.dataset.id;
  try {
    if (btn.dataset.act === "select") await api(`/api/nodes/${id}/select`, { method: "POST", body: "{}" });
    if (btn.dataset.act === "ping") {
      const r = await api(`/api/nodes/${id}/ping`, { method: "POST", body: "{}" });
      toast(r.ok ? `${r.latencyMs} ms` : r.error);
    }
    if (btn.dataset.act === "del") await api(`/api/nodes/${id}`, { method: "DELETE" });
    await loadAll();
  } catch (e) { toast(state.demo ? "预览模式：只读演示" : e.message); }
};

const onSubClick = async (ev) => {
  const btn = ev.target.closest("button[data-sub]");
  if (!btn) return;
  try {
    if (btn.dataset.sub === "refresh") await api(`/api/subscriptions/${btn.dataset.id}/refresh`, { method: "POST", body: "{}" });
    if (btn.dataset.sub === "del") await api(`/api/subscriptions/${btn.dataset.id}`, { method: "DELETE" });
    await loadAll();
  } catch (e) { toast(e.message); }
};
$("subList").onclick = onSubClick;
$("subList2").onclick = onSubClick;

$("optInsecure").onchange = async (e) => {
  try { await api("/api/settings", { method: "PUT", body: JSON.stringify({ allowInsecureSsl: e.target.checked }) }); }
  catch (err) { toast(err.message); }
};
$("optHttp").onchange = async (e) => {
  try { await api("/api/settings", { method: "PUT", body: JSON.stringify({ httpEditEnabled: e.target.checked }) }); }
  catch (err) { toast(err.message); }
};
$("btnStart").onclick = async () => {
  try { await api("/api/proxy/start", { method: "POST", body: "{}" }); await loadAll(); }
  catch (e) { toast(e.message); }
};
$("btnStop").onclick = async () => {
  try { await api("/api/proxy/stop", { method: "POST", body: "{}" }); await loadAll(); }
  catch (e) { toast(e.message); }
};

loadAll();
