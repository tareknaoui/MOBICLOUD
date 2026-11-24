#!/usr/bin/env python3
"""Statistiques des campagnes de mesures MobiCloud (réponse à R2-11).

Regroupe les lignes de measurements.csv par configuration (label, opération, réseau,
taille, k, n) et donne, pour la durée de transfert, le débit utile et l'énergie par Mo :
le nombre de mesures, la médiane, l'intervalle interquartile, la moyenne et l'intervalle
de confiance à 95 % (loi de Student). Les mesures faites téléphone branché sont exclues
des statistiques d'énergie. Bibliothèque standard uniquement.

    python scripts/experiments/summarize.py measurements/*.csv
    python scripts/experiments/summarize.py measurements/a.csv measurements/b.csv --label wifi_10mb --out summary.csv
"""
import argparse
import csv
import glob
import math
import statistics
import sys

# Quantile t de Student bilatéral à 95 % pour df = 1..30 ; au-delà, approximation normale.
T95 = [12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
       2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
       2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042]

METRICS = [
    ("transfer_ms", "transfert (ms)", False),
    ("goodput_mbps", "débit utile (Mbit/s)", False),
    ("energy_mj_per_mb", "énergie (mJ/Mo)", True),
]


def t95(df):
    if df < 1:
        return float("nan")
    return T95[df - 1] if df <= len(T95) else 1.960


def describe(values):
    n = len(values)
    if n == 0:
        return None
    mean = statistics.fmean(values)
    median = statistics.median(values)
    if n >= 2:
        q1, _, q3 = statistics.quantiles(values, n=4, method="inclusive")
        half = t95(n - 1) * statistics.stdev(values) / math.sqrt(n)
    else:
        q1 = q3 = values[0]
        half = float("nan")
    return {"n": n, "median": median, "q1": q1, "q3": q3, "mean": mean, "ci95": half}


def to_float(text):
    try:
        return float(text)
    except (TypeError, ValueError):
        return None


def load(paths):
    rows = []
    for pattern in paths:
        for path in sorted(glob.glob(pattern)) or [pattern]:
            with open(path, newline="", encoding="utf-8") as f:
                for row in csv.DictReader(f):
                    row["_source"] = path
                    rows.append(row)
    return rows


def summarize(rows):
    groups = {}
    for r in rows:
        key = (r["label"], r["operation"], r["network_type"], r["file_bytes"], r["k"], r["n"])
        groups.setdefault(key, []).append(r)
    out = []
    for key in sorted(groups):
        group = groups[key]
        ok = [r for r in group if r["success"] == "true"]
        unplugged = [r for r in ok if r["was_charging"] != "true"]
        entry = {
            "label": key[0], "operation": key[1], "network": key[2],
            "file_mb": int(key[3]) / 1e6, "k": key[4], "n": key[5],
            "runs": len(group), "ok": len(ok), "charging_excluded": len(ok) - len(unplugged),
        }
        for column, _, energy_only in METRICS:
            source = unplugged if energy_only else ok
            values = [v for v in (to_float(r.get(column)) for r in source) if v is not None and v >= 0]
            entry[column] = describe(values)
        out.append(entry)
    return out


def fmt(stats, decimals=2):
    if stats is None:
        return "-"
    ci = "" if math.isnan(stats["ci95"]) else f" ± {stats['ci95']:.{decimals}f}"
    return (f"{stats['median']:.{decimals}f} [{stats['q1']:.{decimals}f}–{stats['q3']:.{decimals}f}]"
            f" (moy. {stats['mean']:.{decimals}f}{ci}, n={stats['n']})")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("csv", nargs="+", help="fichiers measurements.csv (motifs glob acceptés)")
    parser.add_argument("--label", help="ne garder qu'un label")
    parser.add_argument("--out", help="écrire le résumé dans ce CSV")
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # accents lisibles sous Windows

    rows = load(args.csv)
    if args.label:
        rows = [r for r in rows if r["label"] == args.label]
    if not rows:
        sys.exit("Aucune mesure trouvée.")

    summary = summarize(rows)
    print("Médiane [Q1–Q3] (moyenne ± IC 95 %, n)\n")
    for s in summary:
        print(f"## {s['label']} · {s['operation']} · {s['network']} · {s['file_mb']:.2f} Mo · RS({s['k']},{s['n']})")
        print(f"   réussites : {s['ok']}/{s['runs']}" +
              (f" · {s['charging_excluded']} exclue(s) de l'énergie (en charge)" if s["charging_excluded"] else ""))
        for column, title, _ in METRICS:
            print(f"   {title:<22} {fmt(s[column])}")
        print()

    if args.out:
        fields = ["label", "operation", "network", "file_mb", "k", "n", "runs", "ok", "charging_excluded"]
        stat_fields = [f"{c}_{s}" for c, _, _ in METRICS for s in ("n", "median", "q1", "q3", "mean", "ci95")]
        with open(args.out, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fields + stat_fields)
            writer.writeheader()
            for s in summary:
                row = {k: s[k] for k in fields}
                for column, _, _ in METRICS:
                    for stat in ("n", "median", "q1", "q3", "mean", "ci95"):
                        row[f"{column}_{stat}"] = "" if s[column] is None else s[column][stat]
                writer.writerow(row)
        print(f"Résumé écrit dans {args.out}")


if __name__ == "__main__":
    main()
