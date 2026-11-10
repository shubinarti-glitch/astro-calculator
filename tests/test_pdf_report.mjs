import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import { execFileSync } from 'node:child_process';

const source = readFileSync(new URL('../frontend/js/app.js', import.meta.url), 'utf8');
const start = source.indexOf('function reportLabel(');
const end = source.indexOf('const EDITORIAL_PRINT_STYLE', start);
function context(lang = 'en') {
  const ctx = {
    LANG: lang, t: key => key,
    FC_NATURE: { harmonious: 'good', tense: 'bad' },
    escapeHtml: value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;'),
    buildDeepReport: () => '<h2>NATAL ANALYSIS</h2><p>Full natal narrative</p>',
  };
  vm.createContext(ctx);
  vm.runInContext(source.slice(start, end), ctx);
  // The browser owns DOMParser. Keep this unit suite dependency-free and test
  // routing/data completeness separately from the browser's HTML normalization.
  ctx.editorialDeepHtml = html => html;
  return ctx;
}

test('natal prints narrative once and only curated calculation appendices', () => {
  const ctx = context();
  const data = { meta: { name: 'Person', lat: 0, internal_id: 'PRIVATE' }, deep: { extra: 'RAW DEEP' }, planets: [{ name_ru: 'Sun', deg: 12, min: 3, interp_full: [{ text: 'Full natal narrative' }], internal_id: 'PRIVATE' }], aspects: [{ p1_ru: 'Sun', p2_ru: 'Moon', aspect_ru: 'Trine', orbit: 0, interp: 'Full natal narrative' }] };
  const html = ctx.buildEditorialReport(data, 'natal', 'Natal report');
  for (const text of ['NATAL ANALYSIS', 'Person', '12°03′', 'Trine', '0°']) assert.ok(html.includes(text));
  assert.equal(html.split('Full natal narrative').length - 1, 1);
  assert.ok(!/PRIVATE|RAW DEEP|internal_id|interp_full/.test(html));
  const ru = context('ru').natalReportAppendices(data);
  assert.ok(ru.includes('Таблица аспектов'));
  assert.ok(!/Calculation|Parameter|Retrograde|Internal|Interp/.test(ru));
});

for (const tool of ['transit', 'synastry', 'return', 'progression', 'calendar', 'forecast', 'rectification', 'vedic']) {
  test(`${tool} has its own complete narrative and no natal substitution`, () => {
    const ctx = context();
    ctx.buildDeepReport = () => { throw new Error('Must not call natal builder'); };
    const text = 'Complete technique interpretation. '.repeat(200);
    const list = Array.from({ length: 31 }, (_, i) => ({ date: `Day ${i + 1}`, text: `Interpretation ${i + 1}` }));
    const data = { overview: { text }, summary: text, theme: { text }, couple: { text }, highlights: { text }, best: { text }, days: [{ text }, ...list], events: [{ text }, ...list], aspects: [{ text }, ...list], top: list };
    const html = ctx.buildEditorialReport(data, tool, tool);
    assert.ok(html.replace(/<[^>]*>/g, '').includes(text), 'paragraph layout must retain every character');
    assert.ok(html.includes('Interpretation 31'));
    assert.ok(!/card|button|input|guest-teaser|NATAL ANALYSIS/.test(html));
  });
}

test('calendar PDF has separate full-calendar-month and calendar-year formats', () => {
  const ctx = context('en');
  const mode = ctx.calendarPdfMode;
  assert.equal(mode({ start: '2024-02-01', end: '2024-02-29' }), 'detailed');
  assert.equal(mode({ start: '2026-01-01', end: '2026-01-31' }), 'detailed');
  assert.equal(mode({ start: '2026-01-15', end: '2026-02-14' }), 'invalid');
  assert.equal(mode({ start: '2026-01-31', end: '2026-02-28' }), 'invalid');
  assert.equal(mode({ start: '2026-01-01', end: '2026-12-31' }), 'compact');
  assert.equal(mode({ start: '2024-01-01', end: '2024-12-31' }), 'compact');
  assert.equal(mode({ start: '2026-02-01', end: '2027-01-31' }), 'invalid');
  assert.equal(mode({ start: '2026-01-01', end: '2027-01-01' }), 'invalid');
  assert.equal(mode({ start: '2026-04-01', end: '2026-03-31' }), 'invalid');

  const event = (date, interp, orb = 0.12) => ({ date, exact_datetime: `${date}T12:34:00`, p1_ru: 'Марс', aspect_ru: 'Трин', p2_ru: 'Венера', orb, interp });
  const data = {
    start: '2026-01-01', end: '2026-02-28',
    location_meta: { tz_str: 'Asia/Krasnoyarsk' },
    events: [event('2026-01-04', 'LONG PRIVATE INTERPRETATION'), event('2026-02-09', 'ANOTHER PRIVATE INTERPRETATION', 0)],
    lunar: {
      '2026-01-01': { phase_ru: 'Новолуние', sign_ru: 'Козерог', mood: 'Long daily mood' },
      '2026-01-02': { phase_ru: 'Новолуние', sign_ru: 'Козерог', mood: 'Long daily mood' },
      '2026-01-03': { phase_ru: 'Новолуние', sign_ru: 'Водолей' },
      '2026-01-08': { phase_ru: 'Первая четверть', sign_ru: 'Водолей', advice: 'Long daily advice' },
      '2026-02-01': { phase_ru: 'Полнолуние', sign_ru: 'Рыбы', mood: 'Long daily mood' },
    },
  };
  const compact = ctx.compactCalendarPdf(data);
  assert.ok(compact.includes('<th>Sign</th>'));
  for (const text of ['January 2026', 'February 2026', '04/01/2026, 19:34', '09/02/2026, 19:34', 'Марс', 'Трин', 'Венера', '0.12°', '0.00°', 'Новолуние', 'Первая четверть', 'Полнолуние', 'Козерог', 'Водолей', 'Рыбы']) assert.ok(compact.includes(text), text);
  assert.ok(!compact.includes('2026-01-04T12:34:00'));
  const lunarDates = [...compact.matchAll(/<td>(\d{2}\.\d{2}\.2026)<\/td>/g)].map((match) => match[1]);
  assert.deepEqual(lunarDates, ['01.01.2026', '03.01.2026', '08.01.2026', '01.02.2026']);
  for (const text of ['LONG PRIVATE INTERPRETATION', 'ANOTHER PRIVATE INTERPRETATION', 'Long daily mood', 'Long daily advice']) assert.ok(!compact.includes(text), text);

  const detailed = ctx.buildEditorialReport({ ...data, end: '2026-01-31' }, 'calendar');
  assert.ok(detailed.includes('LONG PRIVATE INTERPRETATION'));
  assert.ok(detailed.includes('Long daily mood'));
  const annual = ctx.buildEditorialReport({ ...data, end: '2026-12-31' }, 'calendar');
  assert.ok(annual.includes('January 2026'));
  assert.ok(annual.includes('December 2026'));
  assert.ok(annual.includes('pdf_calendar_compact_note'));
  assert.ok(annual.includes('Водолей'));
  assert.ok(!annual.includes('LONG PRIVATE INTERPRETATION'));
});

test('detailed monthly calendar PDF starts with a complete Monday-first lunar grid', () => {
  const ctx = context('ru');
  const lunar = Object.fromEntries(Array.from({ length: 30 }, (_, index) => {
    const date = `2026-11-${String(index + 1).padStart(2, '0')}`;
    return [date, { phase_ru: index ? 'Растущая Луна' : 'Полнолуние', sign_ru: 'Стрелец', sign_symbol: '♐', emoji: '🌕' }];
  }));
  const grid = ctx.monthlyCalendarPdf({
    start: '2026-11-01', end: '2026-11-30', lunar,
    events: [
      { date: '2026-11-01', nature: 'harmonious' },
      { date: '2026-11-01', nature: 'tense' },
      { date: '2026-11-30', nature: 'other' },
      { date: '2026-12-01', nature: 'tense' },
    ],
  });
  assert.ok(grid.includes('Календарь месяца'));
  assert.ok(grid.includes('ноябрь 2026'));
  assert.ok(grid.includes('data-week-rows="6"'));
  assert.equal((grid.match(/class="print-calendar-weekday"/g) || []).length, 7);
  assert.equal((grid.match(/data-date="2026-11-/g) || []).length, 30);
  assert.ok(grid.indexOf('class="print-calendar-cell empty"') < grid.indexOf('data-date="2026-11-01"'));
  assert.ok(grid.includes('data-date="2026-11-01"'));
  assert.ok(grid.includes('data-date="2026-11-30"'));
  assert.ok(grid.includes('class="good"') && grid.includes('class="bad"') && grid.includes('class="neutral"'));
  assert.ok(grid.includes('♐') && grid.includes('Стрелец') && grid.includes('Полнолуние'));
  assert.ok(!grid.includes('data-date="2026-12-01"'));
  assert.match(source, /print-calendar-overview.*?insertAdjacentHTML\("afterend", toc\)/s);
  assert.match(source, /reportTool === "calendar" && calendarPdfMode\(reportData\) === "detailed"/);
});

test('calendar PDF keeps each event and lunar entry together with safe footer spacing', () => {
  const ctx = context('ru');
  const html = ctx.buildEditorialReport({
    start: '2026-11-01', end: '2026-11-30',
    location_meta: { tz_str: 'Asia/Krasnoyarsk' },
    events: [
      { date: '2026-11-01', exact_datetime: '2026-11-01T08:20:00', p1_ru: 'Солнце', aspect_ru: 'Трин', p2_ru: 'Асцендент', orb: 0, interp: 'Первая трактовка события.' },
      { date: '2026-11-02', exact_datetime: '2026-11-02T11:30:00', p1_ru: 'Луна', aspect_ru: 'Секстиль', p2_ru: 'Венера', orb: 0.1, interp: 'Повторяющаяся подробная трактовка события. '.repeat(8) },
    ],
    lunar: { '2026-11-01': { date: '2026-11-01', phase_ru: 'Полнолуние', sign_ru: 'Телец', mood: 'Лунная трактовка.' } },
  }, 'calendar');
  assert.equal((html.match(/class="calendar-report-record"/g) || []).length, 3);
  assert.match(html, /class="calendar-report-lead"><h2>События периода<\/h2><article class="calendar-report-record">/);
  for (const text of ['Первая трактовка события.', 'Повторяющаяся подробная трактовка события.', 'Лунная трактовка.']) assert.ok(html.includes(text));
  assert.equal((html.match(/Повторяющаяся подробная трактовка события\./g) || []).length, 8);
  assert.ok(html.includes('01.11.2026, 15:20'));
  assert.ok(html.includes('02.11.2026, 18:30'));
  assert.ok(html.includes('0,00°') && html.includes('0,10°'));
  assert.ok(!html.includes('2026-11-01T08:20:00'));
  assert.match(source, /calendar-report-lead, \.editorial-report \.calendar-report-record \{ break-inside:avoid; page-break-inside:avoid; \}/);
  assert.match(source, /margin: \[10, 10, reportTool === "calendar" \? 30 : 18, 10\]/);
});

test('annual calendar sorts exact times and repeats month and column headings on each event page', () => {
  const ctx = context('ru');
  const lunar = Object.fromEntries(Array.from({ length: 12 }, (_, i) => {
    const day = String(i + 1).padStart(2, '0');
    return [`2026-01-${day}`, { date: `2026-01-${day}`, phase_ru: i % 2 ? 'Растущая Луна' : 'Убывающая Луна', sign_ru: i % 2 ? 'Овен' : 'Телец' }];
  }));
  const events = [
    { date: '2026-01-01', exact_datetime: '2026-01-01T12:00:00Z', p1_ru: 'Позднее', aspect_ru: 'Трин', p2_ru: 'Солнце', orb: 0.3 },
    { date: '2026-01-01', exact_datetime: '2026-01-01T03:00:00Z', p1_ru: 'Ранее', aspect_ru: 'Трин', p2_ru: 'Солнце', orb: 0.1 },
    { date: '2026-01-01', exact_datetime: '2026-01-01T09:00:00Z', p1_ru: 'Среднее', aspect_ru: 'Трин', p2_ru: 'Солнце', orb: 0.2 },
    ...Array.from({ length: 22 }, (_, i) => ({ date: '2026-01-02', exact_datetime: `2026-01-02T${String(i).padStart(2, '0')}:00:00Z`, p1_ru: `Событие ${i}`, aspect_ru: 'Секстиль', p2_ru: 'Луна', orb: 0.4 })),
  ];
  const html = ctx.compactCalendarPdf({
    start: '2026-01-01', end: '2026-12-31', location_meta: { tz_str: 'Europe/Berlin' }, events, lunar,
  });
  assert.ok(html.indexOf('Ранее') < html.indexOf('Среднее') && html.indexOf('Среднее') < html.indexOf('Позднее'));
  const chunks = [...html.matchAll(/<section class="calendar-year-event-chunk[^"]*">([\s\S]*?)<\/section>/g)].map((match) => match[1]);
  assert.equal(chunks.length, 2);
  for (const chunk of chunks) assert.match(chunk, /<thead>[\s\S]*?<\/thead>/);
  assert.deepEqual(chunks.map((chunk) => (chunk.match(/<tr>/g) || []).length - 1), [20, 5]);
  for (const event of events) assert.equal((html.match(new RegExp(`<td>${event.p1_ru}</td>`, 'g')) || []).length, 1);
  assert.match(chunks[1], /январь 2026[\s\S]*продолжение/);
  assert.match(source, /\.calendar-year-event-chunk\.continued \{[^}]*page-break-before:always/);
  assert.match(source, /\.calendar-year-event-chunk \{ break-inside:avoid; page-break-inside:avoid; \}/);
  assert.match(source, /\.calendar-year-event-intro \{ break-inside:avoid; page-break-inside:avoid; \}/);
  assert.match(html, /calendar-year-event-intro"><h2>январь 2026 г\.<\/h2><h3>События периода<\/h3><section class="calendar-year-event-chunk/);
  const lunarChunks = [...html.matchAll(/<section class="calendar-year-lunar-chunk[^\"]*">([\s\S]*?)<\/section>/g)].map((match) => match[1]);
  assert.equal(lunarChunks.length, 2);
  for (const chunk of lunarChunks) assert.match(chunk, /<thead>[\s\S]*?<\/thead>/);
  assert.deepEqual(lunarChunks.map((chunk) => (chunk.match(/<tr>/g) || []).length - 1), [10, 2]);
  assert.match(lunarChunks[1], /Лунный календарь[\s\S]*январь 2026[\s\S]*продолжение/);
  for (let i = 1; i <= 12; i++) {
    const date = `${String(i).padStart(2, '0')}.01.2026`;
    assert.equal((html.match(new RegExp(`<td>${date}<\\/td>`, 'g')) || []).length, 1);
  }
  assert.match(source, /\.calendar-year-lunar-chunk\.continued \{[^}]*page-break-before:always/);
});

test('monthly calendar grid aligns weekdays for four-, five-, and six-week months including leap February', () => {
  const ctx = context('en');
  for (const [start, rows, leadingCells] of [
    ['2021-02-01', 4, 0], // Monday start, 28 days
    ['2021-01-01', 5, 4], // Friday start
    ['2026-11-01', 6, 6], // Sunday start
    ['2024-02-01', 5, 3], // leap February, Thursday start
  ]) {
    const grid = ctx.monthlyCalendarPdf({ start, end: start.slice(0, 7) + '-28', lunar: {}, events: [] });
    assert.ok(grid.includes(`data-week-rows="${rows}"`), start);
    const firstDateIndex = grid.indexOf(`data-date="${start}"`);
    assert.notEqual(firstDateIndex, -1, start);
    assert.equal((grid.slice(0, firstDateIndex).match(/class="print-calendar-cell empty"/g) || []).length, leadingCells, start);
    const dateCount = (grid.match(/data-date="\d{4}-\d{2}-\d{2}"/g) || []).length;
    assert.equal(dateCount, new Date(Date.UTC(Number(start.slice(0, 4)), Number(start.slice(5, 7)), 0)).getUTCDate(), start);
  }
});

test('calendar PDF limit is checked before any quota reservation or export branch', () => {
  const premium = source.slice(source.indexOf('async function downloadPremiumPdf('), source.indexOf('if ($("deep-pdf"))'));
  assert.ok(premium.indexOf('validateCalendarPdf(srcId)') < premium.indexOf('postJSON("/api/report/pdf/reserve"'));
  const result = source.slice(source.indexOf('if ($("result-pdf"))'), source.indexOf('async function downloadReportCreditPdf('));
  assert.ok(result.indexOf('validateCalendarPdf("results")') < result.indexOf('canPreviewPdfLocally()'));
  assert.ok(result.indexOf('validateCalendarPdf("results")') < result.indexOf('downloadPremiumPdf('));
  assert.ok(source.includes('pdf_calendar_period_format'));
});

test('one-time PDF credit is restricted to natal reports in the client flow', () => {
  const credit = source.slice(source.indexOf('async function downloadReportCreditPdf('), source.indexOf('// ---------- Рендер: транзиты ----------'));
  assert.ok(credit.includes('lastMode !== "natal"'));
  assert.ok(credit.includes('report_credit_natal_only'));
  assert.ok(credit.includes('report_type: lastMode'));
});

test('light chart is used and untrusted prose is escaped', () => {
  const html = context().buildEditorialReport({ svg_light: '<svg>LIGHT</svg>', svg: '<svg>DARK</svg>', overview: { text: '<img src=x onerror=alert(1)>' } }, 'transit');
  assert.ok(html.includes('<svg>LIGHT</svg>'));
  assert.ok(!html.includes('DARK'));
  assert.ok(html.includes('&lt;img'));
  assert.ok(!html.includes('<img'));
});

test('real report schemas from test_report_english render without raw fields', { timeout: 120000 }, () => {
  const code = `import runpy,json
m=runpy.run_path('tests/test_report_english.py')
f=m['test_english_calculation_output']
reports={}
for kind in ['natal','progression','solar','lunar','transit','synastry','forecast','calendar']:
 f.__globals__['assert_english']=lambda value,path='report': reports.update({kind:value})
 f(kind)
print(json.dumps(reports,ensure_ascii=True))`;
  const python = new URL('../venv/Scripts/python.exe', import.meta.url).pathname.replace(/^\/(\w:)/, '$1');
  const payloads = JSON.parse(execFileSync(decodeURIComponent(python), ['-c', code], { cwd: new URL('..', import.meta.url), encoding: 'utf8', timeout: 115000, maxBuffer: 20 * 1024 * 1024 }));
  for (const [kind, data] of Object.entries(payloads)) {
    const tool = ['solar', 'lunar'].includes(kind) ? 'return' : kind;
    data.internal_id = 'INTERNAL_SENTINEL';
    const ctx = context('ru');
    ctx.buildDeepReport = () => '<h2>Подробный разбор</h2><p>Трактовка</p>';
    const html = ctx.buildEditorialReport(data, tool, 'Отчёт');
    assert.ok(!/INTERNAL_SENTINEL|internal_id|is_destiny_sign|weekday_idx|_sun|Interp full/.test(html), `${kind}: ${html.match(/.{0,70}(INTERNAL_SENTINEL|internal_id|is_destiny_sign|weekday_idx|_sun|Interp full).{0,70}/)?.[0]}`);
    assert.ok(!/<h[234]>[A-Za-z_ ]+<\/h[234]>/.test(html), kind);
    if (kind === 'natal') {
      assert.ok(data.planets.every(p => Number.isFinite(p.deg) && Number.isFinite(p.min)));
      const p = data.planets[0];
      assert.ok(html.includes(`${p.deg}°${String(p.min).padStart(2,'0')}′`));
    }
  }
});

test('cover uses response metadata without form reads or timezone conversion', () => {
  const ctx = context();
  ctx.location = { origin: 'https://astrosmap.ru' };
  ctx.document = { getElementById: () => { throw new Error('Must not read edited form'); } };
  vm.runInContext(source.slice(source.indexOf('function printCover('), source.indexOf('function printToc(')), ctx);
  const html = ctx.printCover('Report', 'Brand', 'Today', { name: 'Original person', city: 'Original city', local_datetime: '1990-05-17T00:30:00+14:00', houses_system: 'Placidus' });
  for (const text of ['Original person', 'Original city', '1990-05-17', '00:30', 'Placidus']) assert.ok(html.includes(text));
});

test('Russian field labels and false/zero values survive', () => {
  const html = context('ru').reportValue({ retrograde: false, orbit: 0 });
  assert.ok(html.includes('Ретроградность'));
  assert.ok(html.includes('Нет'));
  assert.ok(html.includes('>0<'));
});

test('PDF contents keep two-digit numbering through 09 and never prefix 10–14 with an extra zero', () => {
  const ctx = context('ru');
  const start = source.indexOf('function printToc(');
  const end = source.indexOf('function ensurePrintToc(', start);
  vm.runInContext(source.slice(start, end), ctx);
  const headings = Array.from({ length: 14 }, (_, i) => ({
    tagName: 'H2', textContent: `Section ${i + 1}`,
  }));
  const html = ctx.printToc({ querySelectorAll: () => headings }, 'ru');
  for (const number of ['08', '09', '10', '11', '12', '13', '14']) {
    assert.ok(html.includes(`<span class="toc-index">${number}</span>`));
  }
  for (const number of ['010', '011', '012', '013', '014']) assert.ok(!html.includes(number));
});

test('both export paths use common frame; frame never reads UI HTML', () => {
  const frame = source.slice(source.indexOf('function buildPrintFrame('), source.indexOf('async function renderPagedReportPdf('));
  assert.ok(frame.includes('buildEditorialReport(reportData, reportTool, title)'));
  assert.ok(!frame.includes('src.innerHTML'));
  const download = source.slice(source.indexOf('function downloadPdf('), source.indexOf('async function downloadPremiumPdf('));
  assert.match(download, /buildPrintFrame\(srcId, title, extraHead\)/);
  const resultHandler = source.slice(source.indexOf('if ($("result-pdf"))'), source.indexOf('async function downloadReportCreditPdf('));
  const deepHandler = source.slice(source.indexOf('if ($("deep-pdf"))'), source.indexOf('function activeToolTitle('));
  assert.match(resultHandler, /downloadPdf\("results", activeToolTitle\(\), btn\)/);
  assert.match(deepHandler, /downloadPremiumPdf\("deep-content"/);
  assert.ok(source.includes('"/api/report/credit/reserve"'));
  assert.ok(source.includes('"/api/report/credit/complete"'));
  assert.ok(source.includes('"/api/report/credit/cancel"'));
  assert.ok(!source.slice(start, end).includes('fetch('));
});

test('paid PDF credit is reserved before render and settled after save', () => {
  const start = source.indexOf('async function downloadReportCreditPdf(');
  const end = source.indexOf('// ---------- Рендер: транзиты ----------', start);
  const flow = source.slice(start, end);
  assert.ok(flow.indexOf('/api/report/credit/reserve') < flow.indexOf('downloadPdf('));
  assert.ok(flow.includes('/api/report/credit/complete'));
  assert.ok(flow.includes('/api/report/credit/cancel'));
  assert.ok(flow.includes('premium_fallback=true'));
  const download = source.slice(source.indexOf('function downloadPdf('), source.indexOf('async function downloadPremiumPdf('));
  assert.match(download, /await onDone\(\)/);
});

test('free and preview PDFs end with the shortened-interpretations notice', () => {
  const start = source.indexOf('function printLegal(');
  const end = source.indexOf('\n}', start) + 2;
  const ctx = {};
  vm.createContext(ctx);
  vm.runInContext(source.slice(start, end), ctx);
  assert.match(ctx.printLegal('ru', 'brief'), /часть трактовок сокращена.*Полные трактовки доступны по платной подписке Premium/);
  assert.match(ctx.printLegal('en', 'brief'), /Some interpretations in this free report are shortened.*Full interpretations are available with a paid Premium subscription/);
  assert.ok(!ctx.printLegal('ru', 'full').includes('часть трактовок сокращена'));
  assert.ok(source.includes('${content}${printLegal(LANG, reportData.access_mode === "brief" || !IS_PREMIUM ? "brief" : reportData.access_mode)}'));
});

test('long PDFs batch pages by available memory, crop every page, and release canvases', async () => {
  for (const deviceMemory of [4, 8]) {
  const ctx = context();
  const watermark = { kind: 'loaded-image' };
  ctx.pdfBrandAssets = () => Promise.resolve({ mark: 'footer-mark', watermark });
  vm.runInContext(source.slice(source.indexOf('async function renderPagedReportPdf('), source.indexOf('function downloadPdf(')), ctx);
  let attached = true, count = 0;
  const slices = [], canvases = [], pageCanvases = [], crops = [], margins = [], drawOrder = [], pdfImages = [];
  const iframeUint8Array = class extends Uint8Array {};
  const overlay = { remove() { attached = false; } };
  const pdf = { deletePage() {}, addPage() { count++; }, addImage(...args) { if (args[1] === 'JPEG') { assert.ok(args[0] instanceof iframeUint8Array); assert.deepEqual(Array.from(args[0]), [1, 2, 3, 4]); } pdfImages.push(args[0]); }, setDrawColor() {}, setLineWidth() {}, rect() {}, line() {}, setFont() {}, setFontSize() {}, setTextColor() {}, text() {} };
  const container = { scrollHeight: 59000, getBoundingClientRect: () => ({ width: 720 }) };
  const iframeArray = { from(values) { const copy = Array.from(values); margins.push(copy); return copy; } };
  const makeCanvas = () => {
    const context = { globalAlpha: 1, fillRect: () => drawOrder.push('white'), drawImage(image, ...args) { if (image !== watermark) crops.push(args.slice(0, 4)); else assert.equal(context.globalAlpha, 0.11); drawOrder.push(image === watermark ? 'watermark' : 'page'); } };
    const canvas = { width: 0, height: 0, toBlob(callback, type, quality) { assert.equal(type, 'image/jpeg'); assert.equal(quality, .98); callback({ size: 4, arrayBuffer: async () => Uint8Array.from([1, 2, 3, 4]).buffer }); }, getContext: () => context };
    pageCanvases.push(canvas);
    return canvas;
  };
  const win = {
    Array: iframeArray,
    Uint8Array: iframeUint8Array,
    navigator: { deviceMemory },
    document: { fonts: { ready: Promise.resolve() }, body: { contains: () => attached, appendChild: () => { attached = true; } }, createElement: makeCanvas },
    html2pdf() {
      const state = { container, overlay, pageSize: { inner: { px: { height: 1000 }, width: 190, height: 277 } }, pdf };
      return {
        set(opts) { if (opts.margin) assert.equal(opts.margin, margins[0]); Object.assign(state, opts); return this; }, from() { return this; }, toContainer() { return this; }, toPdf() { return this; },
        toCanvas() {
          if (canvases.length) assert.equal(canvases.at(-1).width, 0, 'previous raster must be released');
          assert.ok(attached);
          const opts = state.html2canvas;
          slices.push({ height: opts.height, y: opts.y, scale: opts.scale });
          const canvas = { width: opts.width * opts.scale, height: opts.height * opts.scale, toDataURL: () => 'data:image/jpeg;base64,test' };
          canvases.push(canvas); state.canvas = canvas; attached = false; return this;
        }, get(key) { return Promise.resolve(state[key]); },
      };
    },
  };
  const result = await ctx.renderPagedReportPdf(win, {}, { margin: [10, 10, 18, 10], html2canvas: { scale: 2 }, image: { quality: .98 } }, () => {}, [3]);
  assert.equal(result, pdf);
  assert.deepEqual(margins, [[10, 10, 18, 10]]);
  assert.equal(count, 59);
  const pagesPerCanvas = 59 > 30 ? (deviceMemory >= 8 ? 3 : 2) : deviceMemory >= 8 ? 6 : 3;
  assert.equal(slices.length, Math.ceil(59 / pagesPerCanvas));
  slices.forEach((slice, index) => assert.deepEqual(slice, {
    height: Math.min(pagesPerCanvas * 1000, 59000 - index * pagesPerCanvas * 1000),
    y: index * pagesPerCanvas * 1000,
    scale: 2,
  }));
  assert.ok(canvases.every(canvas => canvas.width === 0 && canvas.height === 0));
  assert.equal(crops.length, 59);
  assert.equal(drawOrder.length, 59 * 3 - 1);
  for (let page = 1, index = 0; page <= 59; page++) {
    assert.equal(drawOrder[index++], 'white');
    assert.equal(drawOrder[index++], 'page');
    if (page !== 3) assert.equal(drawOrder[index++], 'watermark');
  }
  assert.ok(!pdfImages.includes('watermark'), 'watermark must not be painted over a finished page');
  assert.ok(pdfImages.includes('footer-mark'));
  assert.deepEqual(crops.slice(0, pagesPerCanvas + 1), [
    ...Array.from({ length: pagesPerCanvas }, (_, i) => [0, i * 2000, 1440, 2000]),
    [0, 0, 1440, 2000],
  ]);
  assert.ok(pageCanvases.every(canvas => canvas.width === 0 && canvas.height === 0));
  assert.equal(attached, false);
  }
});

test('long exports use smaller canvases and failed exports explain that access is preserved', () => {
  const renderer = source.slice(source.indexOf('async function renderPagedReportPdf('), source.indexOf('function downloadPdf('));
  const download = source.slice(source.indexOf('function downloadPdf('), source.indexOf('async function downloadPremiumPdf('));
  const brandAssets = source.slice(source.indexOf('function pdfBrandAssets('), source.indexOf('// Собрать документ для PDF'));
  assert.match(renderer, /total > 30 \? \(Number\(win\.navigator\?\.deviceMemory\) >= 8 \? 3 : 2\)/);
  assert.match(brandAssets, /watermark:\s*image/);
  assert.doesNotMatch(brandAssets, /watermark:\s*\w+Canvas\.toDataURL/);
  assert.match(download, /console\.error\("PDF export failed", error\)/);
  assert.match(download, /await onFail\(error\)/);
  assert.match(download, /PDF renderer did not load within 15 seconds/);
  const translations = readFileSync(new URL('../frontend/js/i18n.js', import.meta.url), 'utf8');
  assert.match(translations, /pdf_build_fail: "Не удалось подготовить PDF\. Скачивание не списано/);
});

test('export title follows last calculated mode, not selected tab', () => {
  const ctx = context();
  ctx.lastMode = 'transit';
  ctx.document = { querySelector: () => { throw new Error('Must not read selected tab'); } };
  const activeTitleStart = source.indexOf('function activeToolTitle(');
  const activeTitleEnd = source.indexOf('\n}', activeTitleStart) + 2;
  vm.runInContext(source.slice(activeTitleStart, activeTitleEnd), ctx);
  assert.equal(ctx.activeToolTitle(), 'tab_transit');
});

test('long RU/EN paragraphs split losslessly into bounded reading blocks', () => {
  const ctx = context();
  for (const text of [
    'Луна. ' + 'Это полное предложение о характере и возможностях человека. '.repeat(90),
    'Moon. ' + 'This complete sentence must retain its punctuation and spacing. '.repeat(70),
    'Очень длинное предложение без точки '.repeat(110),
    'x'.repeat(2200),
  ]) {
    const chunks = ctx.splitReportParagraph(text);
    assert.equal(chunks.join(''), text);
    assert.ok(chunks.length > 1);
    assert.ok(chunks.every(chunk => chunk.length <= 900));
  }
  const prose = 'Sentence with enough words to reach a useful reading length. '.repeat(60);
  const chunks = ctx.splitReportParagraph(prose);
  assert.ok(chunks.slice(0,-1).every(chunk => /\.\s+$/.test(chunk)));
  assert.equal(ctx.splitReportParagraph('Short paragraph.').join(''), 'Short paragraph.');
});

test('editorial splitting preserves inline markup and keeps bounded heading leads', () => {
  const fn = source.slice(source.indexOf('function editorialDeepHtml('), source.indexOf('function natalReportAppendices('));
  assert.ok(fn.includes('range.cloneContents()'));
  assert.ok(fn.includes('lead.append(heading, paragraph)'));
  assert.ok(fn.includes('> 1100'));
  assert.match(source, /\.report-lead \{ break-inside:avoid/);
});
