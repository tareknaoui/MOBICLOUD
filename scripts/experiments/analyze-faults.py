#!/usr/bin/env python3
"""Analyse d'une campagne d'injection de pannes (sortie de fault-injection.ps1).

Pour chaque cycle de coupure et chaque téléphone observateur :
  - détection : délai entre la coupure et le premier pair vu inactif ;
  - ouverture : délai entre la coupure et l'ouverture du circuit (ou « fermé ») ;
  - fermeture : délai entre le rétablissement et la fermeture du circuit ;
  - réparations émises, mises en file, abandonnées, relâchées à la fermeture.
Les ouvertures avant la première coupure sont signalées comme fausses alertes.
Les horloges des téléphones sont ramenées à celle du PC (précision ~1 s).

    python scripts/experiments/analyze-faults.py measurements/faults/20261121-140000
"""
import csv
import os
import re
import statistics
import sys


def safe(serial):
    return re.sub(r"[^A-Za-z0-9]", "_", serial)


def read_csv(path):
    with open(path, newline="", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def load(run_dir):
    offsets = {r["serial"]: int(r["offset_ms"]) for r in read_csv(os.path.join(run_dir, "offsets.csv"))}
    injections = read_csv(os.path.join(run_dir, "injections.csv"))
    victims = sorted({r["serial"] for r in injections})
    cycles = {}
    for r in injections:
        c = cycles.setdefault(int(r["cycle"]), {"cut": None, "restore": None})
        t = int(r["host_epoch_ms"])
        if r["action"] == "cut":
            c["cut"] = t if c["cut"] is None else min(c["cut"], t)
        else:
            c["restore"] = t if c["restore"] is None else max(c["restore"], t)
    events = {}
    for serial, offset in offsets.items():
        path = os.path.join(run_dir, f"events_{safe(serial)}.csv")
        if not os.path.exists(path):
            continue
        events[serial] = sorted(
            ({"t": int(r["timestamp_ms"]) - offset, "type": r["type"], "value": float(r["value"] or 0)}
             for r in read_csv(path)),
            key=lambda e: e["t"],
        )
    return victims, [{**cycles[k], "cycle": k} for k in sorted(cycles)], events


def first(evts, kind, start, end):
    return next((e["t"] for e in evts if e["type"] == kind and start <= e["t"] < end), None)


def analyze(victims, cycles, events):
    observers = [s for s in events if s not in victims]
    results = []
    for i, c in enumerate(cycles):
        end = cycles[i + 1]["cut"] if i + 1 < len(cycles) else float("inf")
        for s in observers:
            evts = events[s]
            window = [e for e in evts if c["cut"] <= e["t"] < end]
            detect = first(evts, "PEER_INACTIVE", c["cut"], end)
            opened = first(evts, "CIRCUIT_OPEN", c["cut"], end)
            closed = first(evts, "CIRCUIT_CLOSE", opened, end) if opened else None
            results.append({
                "cycle": c["cycle"], "observer": s,
                "detect_s": None if detect is None else (detect - c["cut"]) / 1000,
                "open_s": None if opened is None else (opened - c["cut"]) / 1000,
                "close_after_restore_s": None if closed is None else (closed - c["restore"]) / 1000,
                "emitted": sum(1 for e in window if e["type"] == "REPAIR_EMITTED"),
                "buffered": sum(1 for e in window if e["type"] == "REPAIR_BUFFERED"),
                "dropped": sum(1 for e in window if e["type"] == "REPAIR_DROPPED"),
                "drained": int(sum(e["value"] for e in window if e["type"] == "REPAIR_DRAINED")),
            })
    first_cut = cycles[0]["cut"] if cycles else float("inf")
    false_alarms = {s: sum(1 for e in events[s] if e["type"] == "CIRCUIT_OPEN" and e["t"] < first_cut) for s in observers}
    return observers, results, false_alarms


def show(v):
    return "-" if v is None else (f"{v:.1f}" if isinstance(v, float) else str(v))


def median(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    victims, cycles, events = load(sys.argv[1])
    if not cycles:
        sys.exit("Aucune injection enregistrée.")
    observers, results, false_alarms = analyze(victims, cycles, events)
    if not observers:
        sys.exit("Aucun journal d'observateur récupéré.")

    cols = ["cycle", "observer", "detect_s", "open_s", "close_after_restore_s", "emitted", "buffered", "dropped", "drained"]
    print(" | ".join(cols))
    for r in results:
        print(" | ".join(show(r[c]) for c in cols))

    print("\nSynthèse par observateur (médianes sur les cycles)")
    for s in observers:
        rs = [r for r in results if r["observer"] == s]
        opened = sum(1 for r in rs if r["open_s"] is not None)
        print(f"  {s} : circuit ouvert {opened}/{len(rs)} cycles · détection {show(median([r['detect_s'] for r in rs]))} s"
              f" · ouverture {show(median([r['open_s'] for r in rs]))} s"
              f" · fermeture après retour {show(median([r['close_after_restore_s'] for r in rs]))} s"
              f" · réparations émises {sum(r['emitted'] for r in rs)}, retenues {sum(r['buffered'] for r in rs)},"
              f" abandonnées {sum(r['dropped'] for r in rs)} · fausses alertes avant injection {false_alarms[s]}")

    out = os.path.join(sys.argv[1], "fault_summary.csv")
    with open(out, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=cols)
        writer.writeheader()
        writer.writerows(results)
    print(f"\nDétail écrit dans {out}")


if __name__ == "__main__":
    main()
