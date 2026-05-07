"""
Génère les graphes de benchmark MobiCloud à partir des CSV produits par NetworkScaleBenchmarkTest.
Usage : python plot_benchmark.py
Les CSV doivent être dans : app/build/reports/benchmark/
Les graphes sont sauvegardés dans : app/build/reports/benchmark/
"""

import csv
import os
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np

CSV_DIR = os.path.join("app", "build", "reports", "benchmark")
OUT_DIR = CSV_DIR

BLUE   = "#2196F3"
GREEN  = "#4CAF50"
ORANGE = "#FF9800"
RED    = "#F44336"
GREY   = "#90A4AE"

def read_csv(filename):
    path = os.path.join(CSV_DIR, filename)
    if not os.path.exists(path):
        print(f"[SKIP] Fichier introuvable : {path}")
        return None
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        return list(reader)

def save(fig, name):
    path = os.path.join(OUT_DIR, name)
    fig.savefig(path, dpi=150, bbox_inches="tight")
    print(f"[OK] Graphe sauvegardé : {path}")
    plt.close(fig)

# ─────────────────────────────────────────────────────────────────────────────
# Graphe 1 — Temps de distribution vs Nombre de nœuds
# ─────────────────────────────────────────────────────────────────────────────
def plot_benchmark1():
    rows = read_csv("benchmark1_nodes_vs_time.csv")
    if not rows:
        return

    nodes = [int(r["noeuds"])   for r in rows]
    mins  = [int(r["min_ms"])   for r in rows]
    avgs  = [int(r["avg_ms"])   for r in rows]
    maxs  = [int(r["max_ms"])   for r in rows]

    fig, ax = plt.subplots(figsize=(9, 5))

    ax.fill_between(nodes, mins, maxs, alpha=0.15, color=BLUE, label="Intervalle min–max")
    ax.plot(nodes, avgs, "o-", color=BLUE, linewidth=2.5, markersize=7, label="Temps moyen")
    ax.plot(nodes, mins, "--", color=GREY, linewidth=1, label="Min")
    ax.plot(nodes, maxs, "--", color=GREY, linewidth=1, label="Max")

    ax.set_xlabel("Nombre de nœuds dans le cluster", fontsize=12)
    ax.set_ylabel("Temps de distribution (ms)", fontsize=12)
    ax.set_title("MobiCloud — Scalabilité : Temps de distribution vs Nombre de nœuds", fontsize=13, fontweight="bold")
    ax.legend(fontsize=10)
    ax.grid(True, linestyle="--", alpha=0.4)
    ax.set_xticks(nodes)

    save(fig, "graph1_nodes_vs_time.png")

# ─────────────────────────────────────────────────────────────────────────────
# Graphe 2 — Taux de succès vs Taux de panne
# ─────────────────────────────────────────────────────────────────────────────
def plot_benchmark2():
    rows = read_csv("benchmark2_failure_vs_success.csv")
    if not rows:
        return

    fail_rates    = [int(r["failure_rate_pct"]) for r in rows]
    success_rates = [int(r["success_rate_pct"]) for r in rows]

    fig, ax = plt.subplots(figsize=(9, 5))

    colors = [GREEN if s >= 80 else ORANGE if s >= 50 else RED for s in success_rates]
    bars = ax.bar(fail_rates, success_rates, width=7, color=colors, edgecolor="white", linewidth=0.8)

    ax.axhline(y=80, color=ORANGE, linestyle="--", linewidth=1.5, label="Seuil acceptable (80%)")
    ax.axhline(y=100, color=GREEN, linestyle=":", linewidth=1, alpha=0.5)

    for bar, val in zip(bars, success_rates):
        ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 1.5,
                f"{val}%", ha="center", va="bottom", fontsize=9, fontweight="bold")

    ax.set_xlabel("Taux de panne des nœuds (%)", fontsize=12)
    ax.set_ylabel("Taux de succès de distribution (%)", fontsize=12)
    ax.set_title("MobiCloud — Résilience : Taux de succès vs Taux de panne", fontsize=13, fontweight="bold")
    ax.set_ylim(0, 115)
    ax.set_xticks(fail_rates)
    ax.set_xticklabels([f"{r}%" for r in fail_rates])

    green_patch  = mpatches.Patch(color=GREEN,  label="Succès ≥ 80%")
    orange_patch = mpatches.Patch(color=ORANGE, label="Succès 50–79%")
    red_patch    = mpatches.Patch(color=RED,    label="Succès < 50%")
    ax.legend(handles=[green_patch, orange_patch, red_patch], fontsize=10)
    ax.grid(True, axis="y", linestyle="--", alpha=0.4)

    save(fig, "graph2_failure_vs_success.png")

# ─────────────────────────────────────────────────────────────────────────────
# Graphe 3 — Temps de distribution inter-cluster vs Nombre de clusters
# ─────────────────────────────────────────────────────────────────────────────
def plot_benchmark3():
    rows = read_csv("benchmark3_clusters_vs_time.csv")
    if not rows:
        return

    clusters = [int(r["clusters_distants"]) for r in rows]
    avgs     = [int(r["avg_ms"])            for r in rows]
    mins     = [int(r["min_ms"])            for r in rows]
    maxs     = [int(r["max_ms"])            for r in rows]

    fig, ax = plt.subplots(figsize=(9, 5))

    ax.fill_between(clusters, mins, maxs, alpha=0.15, color=ORANGE, label="Intervalle min–max")
    ax.plot(clusters, avgs, "s-", color=ORANGE, linewidth=2.5, markersize=7, label="Temps moyen (inter-cluster)")

    ax.set_xlabel("Nombre de clusters distants disponibles", fontsize=12)
    ax.set_ylabel("Temps de distribution (ms)", fontsize=12)
    ax.set_title("MobiCloud — Fédération inter-cluster : Temps vs Nombre de clusters", fontsize=13, fontweight="bold")
    ax.legend(fontsize=10)
    ax.grid(True, linestyle="--", alpha=0.4)
    ax.set_xticks(clusters)

    save(fig, "graph3_clusters_vs_time.png")

# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    print(f"Lecture des CSV depuis : {os.path.abspath(CSV_DIR)}\n")
    plot_benchmark1()
    plot_benchmark2()
    plot_benchmark3()
    print("\nTerminé. Ouvre les PNG dans app/build/reports/benchmark/")
