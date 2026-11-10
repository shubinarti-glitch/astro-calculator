(function (root) {
  "use strict";
  const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  const EVENTS = new Set(['web_visit', 'web_chart_success', 'web_signup_success', 'web_pricing_view', 'web_checkout_start', 'web_checkout_error']);
  const UTM = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_content'];
  function createAnalytics(env) {
    const read = (store, key) => { try { return env[store].getItem(key); } catch (_) { return null; } };
    const write = (store, key, value) => { try { env[store].setItem(key, value); } catch (_) {} };
    const uuid = () => env.crypto.randomUUID();
    let device = read('localStorage', 'astro_sales_device');
    if (!UUID.test(device || '')) { device = uuid(); write('localStorage', 'astro_sales_device', device); }
    const freshSession = () => {
      const params = new URLSearchParams(env.location.search);
      const value = { id: uuid(), source: params.get('source') === 'app' || env.location.hash === '#premium' ? 'app' : 'web', updated: Date.now(), utm: {} };
      UTM.forEach(key => { const campaign = params.get(key); if (campaign && /^[A-Za-z0-9_-]{1,40}$/.test(campaign)) value.utm[key] = campaign; });
      return value;
    };
    const contextFor = value => {
      const context = { session_id: value.id, source: value.source === 'app' ? 'app' : 'web' };
      UTM.forEach(key => { const campaign = (value.utm || {})[key]; if (typeof campaign === 'string' && /^[A-Za-z0-9_-]{1,40}$/.test(campaign)) context[key] = campaign; });
      return context;
    };
    let session;
    try { session = JSON.parse(read('sessionStorage', 'astro_sales_session')); } catch (_) {}
    if (!session || !UUID.test(session.id || '') || !Number.isFinite(session.updated) || session.updated > Date.now() || Date.now() - session.updated > 1800000) session = freshSession();
    let context = contextFor(session);
    session.updated = Date.now(); write('sessionStorage', 'astro_sales_session', JSON.stringify(session));
    let queue = [], flight = null, timer = null, getHeaders = () => ({});
    function flush() {
      if (flight) return flight;
      if (!queue.length) return Promise.resolve();
      const batch = queue.slice(0, 20);
      let delivered = false;
      flight = Promise.resolve().then(() => env.fetch('/api/events', { method: 'POST', headers: { 'Content-Type': 'application/json', ...getHeaders() }, body: JSON.stringify({ device_id: device, events: batch }), keepalive: true }))
        .then(response => { if (response.ok) { queue.splice(0, batch.length); delivered = true; } })
        .catch(() => {})
        .finally(() => { flight = null; if (delivered && queue.length) return flush(); });
      return flight;
    }
    function track(name, props = {}) {
      if (!EVENTS.has(name)) return;
      if (Date.now() - session.updated > 1800000) {
        session = freshSession(); context = contextFor(session);
        if (name !== 'web_visit' && queue.length < 100) queue.push({ name: 'web_visit', props: { ...context } });
      }
      const clean = { ...context };
      ['kind', 'plan'].forEach(key => { if (typeof props[key] === 'string' && /^[a-z0-9_-]{1,40}$/.test(props[key])) clean[key] = props[key]; });
      if (queue.length < 100) queue.push({ name, props: clean });
      session.updated = Date.now(); write('sessionStorage', 'astro_sales_session', JSON.stringify(session));
      if (!timer) timer = env.setTimeout(() => { timer = null; void flush(); }, 100);
    }
    env.addEventListener('pagehide', () => { void flush(); });
    env.addEventListener('online', () => { void flush(); });
    if (env.document) env.document.addEventListener('visibilitychange', () => { if (env.document.visibilityState === 'hidden') void flush(); });
    return { track, flush, setHeaders(fn) { getHeaders = fn; }, attribution: () => ({ device_id: device, ...context }) };
  }
  function paymentResult(data, english) {
    const say = (ru, en) => english ? en : ru;
    const payment = data.payment;
    if (!payment) return { terminal: false, text: say('Проверка завершена. Старая ссылка не позволяет определить конкретную покупку. Актуальные доступы показаны в тарифах. Если покупка не появилась, повторите проверку или обратитесь в поддержку.', 'Check complete. This older link does not identify a specific purchase. Current access is shown in Plans. If your purchase is missing, retry or contact support.') };
    if (payment.status === 'canceled') return { terminal: true, text: say('Платёж отменён. Доступ по этой покупке не выдан.', 'Payment canceled. This purchase has not granted access.') };
    if (payment.status !== 'succeeded') return { terminal: false, text: say('Платёж ещё обрабатывается. Повторите проверку через несколько секунд.', 'Payment is still processing. Check again in a few seconds.') };
    let text;
    if (payment.plan === 'report') text = say('Оплата PDF-отчёта подтверждена. Рассчитайте карту и нажмите «PDF», чтобы получить отчёт.', 'PDF report payment confirmed. Calculate a chart and select PDF to get your report.');
    else if (payment.plan === 'consult') text = say('Оплата консультации подтверждена. Свяжитесь с астрологом для записи.', 'Consultation payment confirmed. Contact the astrologer to schedule it.');
    else {
      const expires = data.subscription && data.subscription.expires_at;
      const date = expires ? new Date(expires * 1000).toLocaleDateString(english ? 'en-GB' : 'ru-RU') : '';
      text = say('Оплата премиума подтверждена.', 'Premium payment confirmed.') + (date ? say(' Доступ до ', ' Access until ') + date + '.' : '');
      if (data.consultation) text += say(' Для записи на консультацию свяжитесь с астрологом.', ' Contact the astrologer to schedule your consultation.');
    }
    return { terminal: true, text };
  }
  const api = { createAnalytics, paymentResult };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else {
    root.AstroSales = api;
    try { root.sales = createAnalytics(root); } catch (_) { root.sales = { track() {}, flush: () => Promise.resolve(), setHeaders() {}, attribution: () => undefined }; }
    root.sales.track('web_visit');
  }
})(typeof window !== 'undefined' ? window : globalThis);
