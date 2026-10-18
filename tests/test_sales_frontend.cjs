'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { createAnalytics, paymentResult } = require('../frontend/js/sales.js');
const app = fs.readFileSync(require('node:path').join(__dirname, '../frontend/js/app.js'), 'utf8');
function environment(blocked = false) {
  const calls = [], listeners = {}, stored = new Map();
  const storage = { getItem: key => stored.get(key), setItem: (key, value) => stored.set(key, value) };
  const env = {
    crypto: require('node:crypto'), location: { search: '?utm_source=insta&utm_medium=reels&utm_campaign=autumn&email-token=secret&utm_content=bad%40mail.ru', hash: '' },
    localStorage: storage, sessionStorage: storage, document: { addEventListener() {} },
    addEventListener: (name, fn) => { listeners[name] = fn; }, setTimeout() { return 1; },
    fetch: async (url, options) => { calls.push({ url, ...options }); return { ok: true }; },
  };
  if (blocked) ['localStorage', 'sessionStorage'].forEach(key => Object.defineProperty(env, key, { get() { throw Error('blocked'); } }));
  return { env, calls, listeners, stored };
}
test('analytics only sends allowlisted campaign and product fields with UUIDs', async () => {
  const { env, calls } = environment(); const sales = createAnalytics(env);
  sales.track('web_visit', { email: 'secret@x.ru', kind: 'natal', full_url: 'private' });
  sales.track('payment_succeeded');
  await sales.flush();
  const body = JSON.parse(calls[0].body);
  assert.equal(body.events.length, 1);
  assert.deepEqual(Object.keys(body.events[0].props).sort(), ['kind', 'session_id', 'source', 'utm_campaign', 'utm_medium', 'utm_source']);
  assert.match(body.device_id, /^[0-9a-f-]{36}$/);
  assert.match(body.events[0].props.session_id, /^[0-9a-f-]{36}$/);
  assert.equal(calls[0].keepalive, true);
  assert.equal(sales.attribution().utm_source, 'insta');
});
test('blocked storage works; analytics network failure is isolated and retries retain order', async () => {
  const { env, calls } = environment(true); const sales = createAnalytics(env);
  env.fetch = async () => { throw Error('offline'); };
  sales.track('web_visit'); await sales.flush();
  env.fetch = async (url, options) => { calls.push(JSON.parse(options.body)); return { ok: true }; };
  sales.track('web_chart_success', { kind: 'natal' }); await sales.flush();
  assert.deepEqual(calls[0].events.map(e => e.name), ['web_visit', 'web_chart_success']);
});
test('batch concurrency is serial and queue stays bounded at 100', async () => {
  const { env, calls } = environment(); const sales = createAnalytics(env);
  for (let i = 0; i < 150; i++) sales.track('web_pricing_view');
  await Promise.all([sales.flush(), sales.flush()]);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.length, 5);
  assert.equal(calls.reduce((n, call) => n + JSON.parse(call.body).events.length, 0), 100);
  assert.ok(calls.every(call => JSON.parse(call.body).events.length <= 20));
});
test('first-touch attribution survives return URL and app source stays separate', () => {
  const { env } = environment(); env.location.hash = '#premium';
  const original = createAnalytics(env).attribution();
  env.location.search = '?payment=check&attempt=abc'; env.location.hash = '';
  assert.deepEqual(createAnalytics(env).attribution(), original);
  assert.equal(original.source, 'app');
});
test('success message describes purchased product, never infers success from premium', () => {
  assert.equal(paymentResult({ payment: null, premium: true }, false).terminal, false);
  for (const status of ['pending', 'waiting_for_capture']) assert.equal(paymentResult({ payment: { status, plan: 'month' }, premium: true }, true).terminal, false);
  assert.match(paymentResult({ payment: { status: 'succeeded', plan: 'report' } }, false).text, /PDF/);
  assert.match(paymentResult({ payment: { status: 'succeeded', plan: 'consult' } }, false).text, /консультации/);
  assert.match(paymentResult({ payment: { status: 'succeeded', plan: 'month' }, subscription: { expires_at: 1800000000 } }, true).text, /Access until/);
  assert.equal(paymentResult({ payment: { status: 'canceled' } }, false).terminal, true);
});
function paymentHarness(response, token = 'token') {
  const notices = [], replacements = [], requests = [];
  const context = {
    URL, AbortController, setTimeout, clearTimeout, window: { location: { href: 'https://astrosmap.ru/?payment=check&attempt=018e4679-865c-4d91-8bc0-eae080123456&utm_source=insta#premium' } },
    LANG: 'ru', getToken: () => token, clearToken: () => { token = null; }, authHeaders: () => ({}),
    showPaymentNotice: (...args) => notices.push(args), refreshPremiumBtn() {}, AstroSales: { paymentResult },
    history: { replaceState: (...args) => replacements.push(args[2]) }, paymentCheckBusy: false,
    fetch: async (url, options) => { requests.push(JSON.parse(options.body)); if (response instanceof Error) throw response; return response; },
  };
  vm.createContext(context);
  vm.runInContext(app.slice(app.indexOf('async function checkPaymentReturn() {'), app.indexOf('// Ссылки из писем:')), context);
  return { context, notices, replacements, requests };
}
test('webhook already processed success updates rights and removes only payment query', async () => {
  const harness = paymentHarness({ ok: true, json: async () => ({ activated: false, premium: false, report_credits: 1, payment: { status: 'succeeded', plan: 'report' } }) });
  await harness.context.checkPaymentReturn();
  assert.match(harness.notices.at(-1)[0], /PDF/);
  assert.equal(harness.context.REPORT_CREDITS, 1);
  assert.deepEqual(harness.replacements, ['/?utm_source=insta#premium']);
  assert.equal(harness.requests[0].attempt, '018e4679-865c-4d91-8bc0-eae080123456');
});
test('pending and network failure keep link and offer retry', async () => {
  for (const response of [new Error('offline'), { ok: true, json: async () => ({ premium: true, payment: { status: 'pending', plan: 'month' } }) }, { ok: false, status: 404 }]) {
    const h = paymentHarness(response); await h.context.checkPaymentReturn();
    assert.equal(h.replacements.length, 0); assert.equal(h.notices.at(-1)[1], true);
    assert.equal(h.context.paymentCheckBusy, false);
  }
});
test('anonymous and expired sessions offer login without losing attempt', async () => {
  for (const [response, token] of [[{}, null], [{ ok: false, status: 401 }, 'expired']]) {
    const h = paymentHarness(response, token); await h.context.checkPaymentReturn();
    assert.equal(h.replacements.length, 0); assert.equal(h.notices.at(-1)[2], true);
  }
});
test('auth token operations have in-memory fallback under blocked storage', () => {
  const context = {}; Object.defineProperty(context, 'localStorage', { get() { throw Error('blocked'); } });
  vm.createContext(context);
  const start = app.indexOf('const TOKEN_KEY ='); const end = app.indexOf('sales.setHeaders(authHeaders);', start);
  vm.runInContext(app.slice(start, end) + '\nsetToken("ok"); globalThis.first = getToken(); clearToken(); globalThis.second = getToken();', context);
  assert.equal(context.first, 'ok'); assert.equal(context.second, null);
});

test('invalid session timestamps reset instead of persisting corrupt attribution', () => {
  for (const updated of [null, 'bad', Date.now() + 3600000]) {
    const { env, stored } = environment();
    const badId = require('node:crypto').randomUUID();
    stored.set('astro_sales_session', JSON.stringify({ id: badId, updated, source: 'web' }));
    assert.notEqual(createAnalytics(env).attribution().session_id, badId);
  }
});
test('idle session renews in same page and records visit before the next step', async () => {
  const realNow = Date.now; let now = realNow();
  Date.now = () => now;
  try {
    const { env, calls } = environment(); const sales = createAnalytics(env);
    const first = sales.attribution().session_id;
    now += 1800001;
    sales.track('web_checkout_start', { plan: 'month' }); await sales.flush();
    const events = JSON.parse(calls[0].body).events;
    assert.deepEqual(events.map(e => e.name), ['web_visit', 'web_checkout_start']);
    assert.notEqual(events[0].props.session_id, first);
    assert.equal(events[0].props.session_id, sales.attribution().session_id);
  } finally { Date.now = realNow; }
});
