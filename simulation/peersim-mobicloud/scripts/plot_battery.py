"""
Visualise l'impact du fan-out gossip sur la consommation batterie.

Usage :
    python plot_battery.py battery-fanout2.csv battery-fanout3.csv battery-fanout5.csv

Genere :
    battery-survival.png  : % noeuds encore actifs vs cycles (pour chaque fanout)
    battery-level.png     : niveau moyen de batterie vs cycles
"""
import sys
import csv
import matplotlib.pyplot as plt


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


def read_battery_csv(path):
    """Lit un CSV BatteryObserver : cycle;avg_battery;alive;dead_battery;total"""
    encoding = detect_encoding(path)
    cycles, avg_bat, survival = [], [], []
    with open(path, encoding=encoding) as f:
        for row in csv.reader(f, delimiter=';'):
            if not row or len(row) < 5:
                continue
            try:
                cycle   = int(row[0])
                avg_b   = float(row[1])
                alive   = int(row[2])
                total   = int(row[4])
                cycles.append(cycle)
                avg_bat.append(avg_b)
                survival.append(100.0 * alive / total if total > 0 else 0)
            except ValueError:
                continue
    return cycles, avg_bat, survival


if len(sys.argv) < 2:
    print(__doc__)
    sys.exit(1)

files  = sys.argv[1:]
labels = ['Fanout=2 (conservateur)', 'Fanout=3 (equilibre)', 'Fanout=5 (agressif)']
colors = ['#2ecc71', '#3498db', '#e74c3c']

all_data = []
for path in files:
    cycles, avg_bat, survival = read_battery_csv(path)
    all_data.append((cycles, avg_bat, survival))

# ── Graphe 1 : Survie des noeuds (batterie > 0) ──────────────────────────────
fig, ax = plt.subplots(figsize=(11, 6))

for i, (cycles, avg_bat, survival) in enumerate(all_data):
    label = labels[i] if i < len(labels) else files[i]
    color = colors[i] if i < len(colors) else None
    ax.plot(cycles, survival, label=label, color=color,
            linewidth=2, marker='o', markersize=3)

ax.axhline(y=80, color='gray', linestyle='--', alpha=0.5,
           label='Seuil 80% noeuds actifs')

ax.set_xlabel('Cycles de gossip (1 cycle = 2s simulees)', fontsize=12)
ax.set_ylabel('% noeuds encore actifs (batterie > 0)', fontsize=12)
ax.set_title('Impact du fan-out sur la survie des noeuds mobiles\n'
             '(N=1000, batterie initiale=100%, costSend=0.5%, costRecv=0.3%)', fontsize=13)
ax.legend(loc='lower left', fontsize=10)
ax.set_ylim(0, 105)
ax.grid(True, alpha=0.3)
fig.tight_layout()
fig.savefig('battery-survival.png', dpi=150)
print('Saved battery-survival.png')

# ── Graphe 2 : Niveau moyen de batterie ──────────────────────────────────────
fig2, ax2 = plt.subplots(figsize=(11, 6))

for i, (cycles, avg_bat, survival) in enumerate(all_data):
    label = labels[i] if i < len(labels) else files[i]
    color = colors[i] if i < len(colors) else None
    ax2.plot(cycles, avg_bat, label=label, color=color,
             linewidth=2, marker='o', markersize=3)

ax2.set_xlabel('Cycles de gossip (1 cycle = 2s simulees)', fontsize=12)
ax2.set_ylabel('Niveau moyen de batterie des noeuds actifs (%)', fontsize=12)
ax2.set_title('Declin du niveau moyen de batterie selon le fan-out\n'
              '(N=1000, noeuds DOWN exclus de la moyenne)', fontsize=13)
ax2.legend(loc='upper right', fontsize=10)
ax2.set_ylim(0, 105)
ax2.grid(True, alpha=0.3)
fig2.tight_layout()
fig2.savefig('battery-level.png', dpi=150)
print('Saved battery-level.png')
