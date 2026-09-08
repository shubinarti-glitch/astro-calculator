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

test('both export paths use common frame; frame never reads UI HTML', () => {
  const frame = source.slice(source.indexOf('function buildPrintFrame('), source.indexOf('function printFrom('));
  assert.ok(frame.includes('buildEditorialReport(reportData, reportTool, title)'));
  assert.ok(!frame.includes('src.innerHTML'));
  assert.match(source.slice(source.indexOf('function printFrom(')), /buildPrintFrame\(srcId, title\)/);
  assert.match(source.slice(source.indexOf('function downloadPdf(')), /buildPrintFrame\(srcId, title, extraHead\)/);
  assert.ok(!source.slice(start, end).includes('fetch('));
});

test('long PDFs rasterize bounded pages sequentially and release canvases', async () => {
  const ctx = context();
  vm.runInContext(source.slice(source.indexOf('async function renderPagedReportPdf('), source.indexOf('function downloadPdf(')), ctx);
  let attached = true, count = 0;
  const slices = [], canvases = [];
  const overlay = { remove() { attached = false; } };
  const pdf = { deletePage() {}, addPage() { count++; }, addImage() {}, setFontSize() {}, setTextColor() {}, text() {} };
  const container = { scrollHeight: 59000, getBoundingClientRect: () => ({ width: 720 }) };
  const win = {
    document: { fonts: { ready: Promise.resolve() }, body: { contains: () => attached, appendChild: () => { attached = true; } }, createElement: () => ({}) },
    html2pdf() {
      const state = { container, overlay, pageSize: { inner: { px: { height: 1000 }, width: 190, height: 277 } }, pdf };
      return {
        set(opts) { Object.assign(state, opts); return this; }, from() { return this; }, toContainer() { return this; }, toPdf() { return this; },
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
  const result = await ctx.renderPagedReportPdf(win, {}, { html2canvas: { scale: 2 }, image: { quality: .98 } });
  assert.equal(result, pdf);
  assert.equal(count, 59);
  assert.equal(slices.length, 59);
  slices.forEach((slice, index) => assert.deepEqual(slice, { height: 1000, y: index * 1000, scale: 2 }));
  assert.ok(canvases.every(canvas => canvas.width === 0 && canvas.height === 0));
  assert.equal(attached, false);
});

test('export title follows last calculated mode, not selected tab', () => {
  const ctx = context();
  ctx.lastMode = 'transit';
  ctx.document = { querySelector: () => { throw new Error('Must not read selected tab'); } };
  vm.runInContext(source.slice(source.indexOf('function activeToolTitle('), source.indexOf('// Кнопка системной печати результата')), ctx);
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
