"""
Visualise l'impact du churn sur la convergence DHT et la re-election Bully.

Usage :
    # Comparaison churn rates (DHT)
    python plot_churn.py --dht results-1000.csv churn10.csv churn20.csv churn30.csv

    # Re-election apres panne super-peer (Bully)
    python plot_churn.py --bully bully-churn-results-1000.csv

Genere :
    convergence-churn.png   : % convergence vs cycles sous differents churn rates
    bully-reelection.png    : nb super-peers avant/apres panne (si --bully)
"""
import sys
import csv
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches


def detect_encoding(path):
    with open(path, 'rb') as f:
        head = f.read(4)
    if head.startswith(b'\xff\xfe'):
        return 'utf-16-le'
    if head.startswith(b'\xfe\xff'):
        return 'utf-16-be'
    if head.startswith(b'\xef\xbb\xbf'):
        return 'utf-8-sig'
    return 'utf-8'


def read_convergence_csv(path):
    """Lit un CSV DHT : cycle;avg;converged;alive_nodes"""
    encoding = detect_encoding(path)
    cycles, pct = [], []
    with open(path, encoding=encoding) as f:
        for row in csv.reader(f, delimiter=';'):
            if not row or len(row) < 4:
                continue
            try:
                cycle   = int(row[0])
                conv    = int(row[2])
                alive   = int(row[3])
                if alive > 0:
                    cycles.append(cycle)
                    pct.append(100.0 * conv / alive)
            except ValueError:
                continue
    return cycles, pct


def read_bully_csv(path):
    """Lit un CSV Bully : cycle;nb_superpeers;score_max;total"""
    encoding = detect_encoding(path)
    cycles, counts = [], []
    with open(path, encoding=encoding) as f:
        for row in csv.reader(f, delimiter=';'):
            if not row or len(row) < 4:
                continue
            try:
                cycles.append(int(row[0]))
                counts.append(int(row[1]))
            except ValueError:
                continue
    return cycles, counts


# ── Parsing args ─────────────────────────────────────────────────────────────
if len(sys.argv) < 3:
    print(__doc__)
    sys.exit(1)

mode  = sys.argv[1]   # --dht ou --bully
files = sys.argv[2:]

# ── Mode DHT churn ────────────────────────────────────────────────────────────
if mode == '--dht':
    labels = ['No churn (0%)', 'Churn 10%', 'Churn 20%', 'Churn 30%']
    colors = ['#2ecc71', '#3498db', '#e67e22', '#e74c3c']

    fig, ax = plt.subplots(figsize=(11, 6))

    for i, path in enumerate(files):
        cycles, pct = read_convergence_csv(path)
        label = labels[i] if i < len(labels) else path
        color = colors[i] if i < len(colors) else None
        ax.plot(cycles, pct, label=label, color=color,
                linewidth=2, marker='o', markersize=3)

    ax.axhline(y=95, color='gray', linestyle='--', alpha=0.5,
               label='95% threshold (operational)')

    ax.set_xlabel('Gossip cycles (1 cycle = 2 simulated seconds)', fontsize=12)
    ax.set_ylabel('% of active nodes with complete DHT', fontsize=12)
    ax.set_title('DHT Robustness Under Mobile Churn\n'
                 '(N=1000 nodes, fanout=2, blocks=100)', fontsize=13)
    ax.legend(loc='lower right', fontsize=10)
    ax.set_ylim(-5, 105)
    ax.grid(True, alpha=0.3)
    fig.tight_layout()
    fig.savefig('convergence-churn.png', dpi=150)
    print('Saved convergence-churn.png')

# ── Mode Bully re-election ────────────────────────────────────────────────────
elif mode == '--bully':
    fig, ax = plt.subplots(figsize=(11, 6))

    for path in files:
        cycles, counts = read_bully_csv(path)
        ax.plot(cycles, counts, linewidth=2, color='#3498db',
                marker='o', markersize=4, label='Active super-peers')

    ax.axvline(x=20, color='#e74c3c', linestyle='--', linewidth=1.5,
               label='Super-peer failure (cycle 20)')
    ax.annotate('Super-peer\nfailure', xy=(20, 1), xytext=(22, 1.3),
                fontsize=9, color='#e74c3c',
                arrowprops=dict(arrowstyle='->', color='#e74c3c'))

    # Zoom sur la fenêtre autour de la panne pour mieux voir le dip/recovery
    kill_cycle = 20
    x_min = max(0, kill_cycle - 10)
    x_max = kill_cycle + 15
    visible_counts = [c for cyc, c in zip(cycles, counts)
                      if x_min <= cyc <= x_max]
    y_base   = max(visible_counts) if visible_counts else 1
    y_margin = max(3, int(y_base * 0.10))

    ax.set_xlabel('Bully election cycles (1 cycle = 2 simulated seconds)', fontsize=12)
    ax.set_ylabel('Number of active super-peers', fontsize=12)
    ax.set_title('Bully Re-election After Super-Peer Failure\n'
                 '(N=1000 nodes, failure at cycle 20 — zoom cycles 10–35)', fontsize=13)
    ax.legend(loc='upper right', fontsize=10)
    ax.set_xlim(x_min, x_max)
    ax.set_ylim(max(0, y_base - y_margin - 1), y_base + y_margin)
    ax.grid(True, alpha=0.3)
    fig.tight_layout()
    fig.savefig('bully-reelection.png', dpi=150)
    print('Saved bully-reelection.png')

else:
    print(f"Mode inconnu : {mode}  (utiliser --dht ou --bully)")
    sys.exit(1)
