'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const app = fs.readFileSync(path.join(__dirname, '../frontend/js/app.js'), 'utf8');
const start = app.indexOf('function renderForecast(data) {');
const end = app.indexOf('\nfunction fcSphereCard(s)', start);
const renderSource = app.slice(start, end) + '\nglobalThis.renderForecast = renderForecast;';

function renderHarness(token = null) {
  const values = {};
  const calls = [];
  const unlock = { addEventListener: (_name, listener) => { unlock.click = listener; } };
  const blocks = { innerHTML: '', set value(html) { this.innerHTML = html; } };
  const element = (id) => values[id] ||= { classList: { add() {}, remove() {} }, innerHTML: '', textContent: '', style: {} };
  const labels = {
    t_fc_for: 'Forecast for', t_fc_period: 'Period', forecast_location: 'Location', sum_tz: 'Timezone',
    t_fc_age: 'Age', t_fc_age_unit: 'yrs', t_fc_house_year: 'House of the year', t_fc_lord_year: 'Lord of the year',
    t_fc_profection: 'Annual profection', t_fc_prog_moon: 'Progressed Moon', fc_locked_title: 'Full forecast',
    fc_locked_text: 'Unlock all life areas and advice.', fc_unlock: 'Unlock the full forecast',
    fc_spheres_title: 'Life areas', fc_spheres_hint: 'Trends, not certainties.',
  };
  const context = {
    showResults() {},
    $: (id) => id === 'forecast-unlock' ? (element('forecast-blocks').innerHTML.includes('id="forecast-unlock"') ? unlock : null) : element(id),
    t: (key) => labels[key] || key,
    summaryBlock: () => '', formatDate: (value) => value, houseShort: (value) => String(value),
    fcSphereCard: () => '<article>Sphere preview</article>',
    getToken: () => token, openPaywall: (status) => calls.push(status),
    document: { querySelector: () => ({ style: {}, textContent: '' }) },
  };
  vm.createContext(context);
  vm.runInContext(renderSource, context);
  context.renderForecast({
    access_mode: 'brief', natal_meta: { name: 'QA' }, location_meta: { city: 'Ulm', tz_str: 'Europe/Berlin' },
    start: '2026-10-19', end: '2027-10-19', summary: 'Brief summary',
    sphere_forecast: [{ text: 'Preview', highlights: [] }], events: [],
  });
  return { values, calls, unlock };
}

test('brief forecast renders safely and offers login-to-premium CTA to guests', () => {
  const { values, calls, unlock } = renderHarness();
  assert.match(values['forecast-summary'].innerHTML, /Brief summary/);
  assert.match(values['forecast-blocks'].innerHTML, /Full forecast/);
  assert.match(values['forecast-blocks'].innerHTML, /Unlock the full forecast/);
  unlock.click();
  assert.deepEqual(calls, [401]);
});

test('brief forecast CTA opens Premium for a signed-in non-premium user', () => {
  const { calls, unlock } = renderHarness('expired-or-free-token');
  unlock.click();
  assert.deepEqual(calls, [402]);
});
