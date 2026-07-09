# -*- coding: utf-8 -*-
"""Харнес валидации алгоритма ректификации.

Меряет БАЗОВУЮ точность движка `astrology.rectification_report` на датасете
реальных персон с AA-рейтингом (benchmark/dataset.json).

Защита от утечки: истинное время рождения НЕ передаётся в поиск —
алгоритм ищет по полному дню (0..1439). Истинное время используется только
для расчёта ошибки и истинного знака Асцендента.

Запуск (из корня проекта, venv с кириллицей — кавычки обязательны):
    "<...>/venv/Scripts/python.exe" benchmark/run.py
"""
import json
import os
import statistics
import sys
import time
import traceback

# --- Вставка корня проекта в sys.path (импорт `from backend import ...`) ---
_HERE = os.path.dirname(os.path.abspath(__file__))
_ROOT = os.path.dirname(_HERE)
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from backend import astrology  # noqa: E402

DATASET_PATH = os.path.join(_HERE, "dataset.json")
RESULTS_DIR = os.path.join(_HERE, "results")
BASELINE_PATH = os.path.join(RESULTS_DIR, "baseline.json")


def _parse_hhmm(s: str) -> int:
    """'HH:MM' -> минуты от полуночи."""
    h, m = s.split(":")
    return int(h) * 60 + int(m)


def _circular_err(pred: int, true: int) -> int:
    """Ошибка по кругу суток в минутах."""
    d = abs(pred - true)
    return min(d, 1440 - d)


def _true_asc_code(birth: dict) -> str:
    """Истинный знак Асцендента по ИСТИННОМУ времени рождения."""
    subj = astrology.build_subject(
        name="true",
        year=birth["year"], month=birth["month"], day=birth["day"],
        hour=birth["hour"], minute=birth["minute"],
        lat=birth["lat"], lng=birth["lng"],
        tz_str=birth.get("tz_str"), city=birth.get("city", ""),
    )
    return subj.first_house.sign


def evaluate(dataset, step_minute=10, params_override=None, verbose=True) -> dict:
    """Прогоняет ректификацию по каждому кейсу и агрегирует метрики.

    dataset: dict вида {"meta":..., "cases": [...]} или список кейсов.
    params_override: dict|None — временно подменяет числовые атрибуты модуля
        astrology по имени ключа (задел для тюнера). Через try/finally.
    """
    cases = dataset["cases"] if isinstance(dataset, dict) else dataset

    # --- Временная подмена модульных констант astrology (для тюнинга) ---
    saved = {}
    if params_override:
        for k, v in params_override.items():
            if hasattr(astrology, k):
                saved[k] = getattr(astrology, k)
                setattr(astrology, k, v)
            elif verbose:
                print(f"[override] пропущен неизвестный атрибут: {k}")

    results = []
    try:
        for i, case in enumerate(cases, 1):
            name = case.get("name", f"case_{i}")
            birth = case["birth"]
            true_minute = birth["hour"] * 60 + birth["minute"]
            true_time = f"{birth['hour']:02d}:{birth['minute']:02d}"

            if verbose:
                print(f"[{i}/{len(cases)}] {name} ... ", end="", flush=True)
            t0 = time.time()
            try:
                # Истинное НЕ передаём в поиск: hour=12, minute=0.
                natal_params = {
                    "name": name,
                    "year": birth["year"], "month": birth["month"], "day": birth["day"],
                    "hour": 12, "minute": 0,
                    "lat": birth["lat"], "lng": birth["lng"],
                    "tz_str": birth.get("tz_str"), "city": birth.get("city", ""),
                    "lang": "ru",
                }
                report = astrology.rectification_report(
                    natal_params,
                    case.get("events", []),
                    asc_traits=None,
                    predispositions=case.get("predispositions", []),
                    parent_sun_signs=case.get("parent_sun_signs", []),
                    start_minute=0, end_minute=1439, step_minute=step_minute,
                    center_minute=None, window_minutes=None,
                )
                best = report["best"]
                meta = report.get("meta", {})
                pred_time = best["time"]
                pred_minute = _parse_hhmm(pred_time)
                err_min = _circular_err(pred_minute, true_minute)

                true_asc = _true_asc_code(birth)
                pred_asc = best.get("_asc")
                asc_ok = (true_asc == pred_asc)

                results.append({
                    "name": name,
                    "true_time": true_time,
                    "predicted_time": pred_time,
                    "err_min": err_min,
                    "true_asc": true_asc,
                    "predicted_asc": pred_asc,
                    "asc_ok": asc_ok,
                    "reliability": meta.get("reliability"),
                    "best_score": best.get("score"),
                    "events_used": meta.get("events_used"),
                    "preds_used": meta.get("preds_used"),
                })
                if verbose:
                    print(f"pred={pred_time} true={true_time} err={err_min}m "
                          f"asc={'OK' if asc_ok else 'x'} ({time.time()-t0:.0f}s)")
            except Exception as exc:  # noqa: BLE001 — не роняем весь прогон
                results.append({
                    "name": name,
                    "true_time": true_time,
                    "error": f"{type(exc).__name__}: {exc}",
                })
                if verbose:
                    print(f"ОШИБКА: {type(exc).__name__}: {exc}")
                    traceback.print_exc()
    finally:
        for k, v in saved.items():
            setattr(astrology, k, v)

    # --- Агрегация (только по успешно посчитанным кейсам) ---
    ok = [r for r in results if "error" not in r]
    errs = [r["err_min"] for r in ok]
    n = len(ok)

    def _frac(pred):
        return round(sum(1 for e in errs if pred(e)) / n, 4) if n else 0.0

    metrics = {
        "n": n,
        "n_total": len(results),
        "n_errors": len(results) - n,
        "median_err": round(statistics.median(errs), 1) if errs else None,
        "mean_err": round(statistics.mean(errs), 1) if errs else None,
        "within_15": _frac(lambda e: e <= 15),
        "within_30": _frac(lambda e: e <= 30),
        "within_60": _frac(lambda e: e <= 60),
        "asc_accuracy": round(sum(1 for r in ok if r["asc_ok"]) / n, 4) if n else 0.0,
        "step_minute": step_minute,
    }
    return {"metrics": metrics, "cases": results}


def _print_summary(out):
    metrics = out["metrics"]
    cases = out["cases"]
    print("\n" + "=" * 78)
    print("ТАБЛИЦА ПО КЕЙСАМ")
    print("=" * 78)
    print(f"{'Имя':<26}{'истина':>7}{'предск':>8}{'ошибка':>8}{'asc':>5}  надёжн.")
    print("-" * 78)
    for r in cases:
        if "error" in r:
            print(f"{r['name'][:25]:<26}{r['true_time']:>7}{'—':>8}{'ERR':>8}{'—':>5}  {r['error'][:20]}")
            continue
        print(f"{r['name'][:25]:<26}{r['true_time']:>7}{r['predicted_time']:>8}"
              f"{str(r['err_min'])+'m':>8}{('OK' if r['asc_ok'] else 'x'):>5}  {r.get('reliability')}")

    print("\n" + "=" * 78)
    print("АГРЕГАТ")
    print("=" * 78)
    print(f"кейсов посчитано:   {metrics['n']} / {metrics['n_total']} (ошибок: {metrics['n_errors']})")
    print(f"медианная ошибка:   {metrics['median_err']} мин")
    print(f"средняя ошибка:     {metrics['mean_err']} мин")
    print(f"within 15 мин:      {metrics['within_15']*100:.1f}%")
    print(f"within 30 мин:      {metrics['within_30']*100:.1f}%")
    print(f"within 60 мин:      {metrics['within_60']*100:.1f}%")
    print(f"точность Асцендента: {metrics['asc_accuracy']*100:.1f}%")
    print("=" * 78)


if __name__ == "__main__":
    with open(DATASET_PATH, encoding="utf-8") as f:
        dataset = json.load(f)

    out = evaluate(dataset, step_minute=10, verbose=True)
    _print_summary(out)

    os.makedirs(RESULTS_DIR, exist_ok=True)
    with open(BASELINE_PATH, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"\nПолный результат сохранён: {BASELINE_PATH}")
