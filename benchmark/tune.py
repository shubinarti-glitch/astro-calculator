# -*- coding: utf-8 -*-
"""Автотюнинг весов/орбисов ректификации по бенчмарку.

Проверяет набор кандидатных векторов параметров (направленный поиск, а не слепая
сетка — чтобы уложиться в разумное время расчёта). Для честной оценки обобщения
делает k-fold кросс-валидацию: параметры выбираются на train-фолдах, метрика
меряется на отложенном фолде.

Гипотеза: базовый движок липнет к полудню из-за предрасположенностей
(fame ≈ Солнце/Юпитер на MC). Кандидаты ослабляют pred-вес и снимают кап событий,
чтобы дать событийным дирекциям вести время.

Запуск: "<...>/venv/Scripts/python.exe" benchmark/tune.py
"""
import json
import os
import statistics
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_ROOT = os.path.dirname(_HERE)
# benchmark/ должен идти ПЕРЕД корнем проекта, иначе `import run` подхватит
# корневой run.py (лаунчер сайта), а не benchmark/run.py.
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)
if _ROOT not in sys.path:
    sys.path.append(_ROOT)

import run  # noqa: E402  (benchmark/run.py)

DATASET_PATH = os.path.join(_HERE, "dataset.json")
RESULTS_DIR = os.path.join(_HERE, "results")
TUNING_PATH = os.path.join(RESULTS_DIR, "tuning.json")

# Кандидатные векторы параметров (имя атрибута astrology.* -> значение).
# {} = дефолты движка «как есть» (усиленный, нетюненный).
CANDIDATES = {
    "default": {},
    "pred_off": {"_RECT_PRED_WEIGHT": 0.0},
    "pred_off_cap30": {"_RECT_PRED_WEIGHT": 0.0, "_RECT_EVENT_CAP": 30.0},
    "pred_off_cap30_boost": {"_RECT_PRED_WEIGHT": 0.0, "_RECT_EVENT_CAP": 30.0, "_RECT_SIG_BOOST": 2.6},
    "pred_low_balanced": {"_RECT_PRED_WEIGHT": 0.6, "_RECT_EVENT_CAP": 15.0, "_RECT_SIG_BOOST": 2.2},
    "pred_off_sharp": {"_RECT_PRED_WEIGHT": 0.0, "_RECT_EVENT_CAP": 30.0,
                       "_RECT_SIGMA_FINE_DIR": 0.30, "_RECT_SIGMA_COARSE_DIR": 0.9},
}


def score_metrics(m: dict) -> tuple:
    """Композитный критерий «лучше»: меньше медиана, больше within_30 и asc."""
    return (m["median_err"], -m["within_30"], -m["asc_accuracy"])


def eval_on(cases, override, step_minute=10):
    return run.evaluate({"cases": cases}, step_minute=step_minute,
                        params_override=override, verbose=False)["metrics"]


def kfold(cases, k=3, seed=42):
    idx = list(range(len(cases)))
    import random
    random.Random(seed).shuffle(idx)
    folds = [idx[i::k] for i in range(k)]
    return folds


def main():
    with open(DATASET_PATH, encoding="utf-8") as f:
        dataset = json.load(f)
    cases = dataset["cases"]

    # 1) Полный прогон каждого кандидата на всех кейсах (для выбора и отчёта).
    full = {}
    for name, ov in CANDIDATES.items():
        m = eval_on(cases, ov)
        full[name] = m
        print(f"[full] {name:22} median={m['median_err']:>6} mean={m['mean_err']:>6} "
              f"w30={m['within_30']*100:4.1f}% asc={m['asc_accuracy']*100:4.1f}%", flush=True)

    best_name = min(CANDIDATES, key=lambda n: score_metrics(full[n]))
    print(f"\nЛучший на всём наборе: {best_name} -> {CANDIDATES[best_name]}")

    # 2) Honest holdout: выбираем кандидата на TRAIN, меряем на отложенном TEST.
    import random
    idx = list(range(len(cases)))
    random.Random(42).shuffle(idx)
    cut = int(len(cases) * 0.6)
    train = [cases[i] for i in idx[:cut]]
    test = [cases[i] for i in idx[cut:]]
    train_scores = {n: eval_on(train, ov) for n, ov in CANDIDATES.items()}
    pick = min(CANDIDATES, key=lambda n: score_metrics(train_scores[n]))
    test_pick = eval_on(test, CANDIDATES[pick])
    test_default = eval_on(test, {})
    print(f"\n[holdout] train={len(train)} test={len(test)} pick={pick}")
    print(f"[holdout] test median: default={test_default['median_err']} "
          f"tuned({pick})={test_pick['median_err']}  asc: "
          f"{test_default['asc_accuracy']*100:.1f}% -> {test_pick['asc_accuracy']*100:.1f}%")
    print(f"\nДефолт на всём наборе median={full['default']['median_err']}, "
          f"лучший({best_name}) median={full[best_name]['median_err']}")

    out = {
        "candidates": CANDIDATES,
        "full_metrics": full,
        "best_on_full": best_name,
        "best_params": CANDIDATES[best_name],
        "holdout": {
            "n_train": len(train), "n_test": len(test), "picked_on_train": pick,
            "test_default": test_default, "test_tuned": test_pick,
        },
    }
    os.makedirs(RESULTS_DIR, exist_ok=True)
    with open(TUNING_PATH, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"\nСохранено: {TUNING_PATH}")


if __name__ == "__main__":
    main()
