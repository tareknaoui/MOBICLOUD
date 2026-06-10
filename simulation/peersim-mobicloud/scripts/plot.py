"""
Lit les CSV produits par la simulation PeerSim et trace les courbes.

Usage :
    python plot.py results-100.csv results-500.csv results-1000.csv

Genere :
    convergence.png   : courbe avg DHT size vs cycles
    converged-pct.png : % noeuds converges vs cycles
"""
import sys
import csv
import matplotlib.pyplot as plt


def detect_encoding(path):
    """Detecte l'encodage en lisant le BOM (UTF-8, UTF-16-LE, UTF-16-BE).
    PowerShell sur Windows utilise UTF-16-LE par defaut quand on fait `>`.
    """
    with open(path, 'rb') as f:
        head = f.read(4)
    if head.startswith(b'\xff\xfe'):
        return 'utf-16-le'
    if head.startswith(b'\xfe\xff'):
        return 'utf-16-be'
    if head.startswith(b'\xef\xbb\xbf'):
        return 'utf-8-sig'
    return 'utf-8'


if len(sys.argv) < 2:
    print("Usage: python plot.py results-N.csv [results-M.csv ...]")
    sys.exit(1)

fig1, ax1 = plt.subplots(figsize=(10, 6))
fig2, ax2 = plt.subplots(figsize=(10, 6))

# On stocke aussi le cycle ou la convergence atteint 99% pour zoom auto
max_convergence_cycle = 0

for path in sys.argv[1:]:
    encoding = detect_encoding(path)
    print(f"Lecture {path} (encoding={encoding})")
    cycles, avg_sizes, converged_pct = [], [], []
    with open(path, encoding=encoding) as f:
        reader = csv.reader(f, delimiter=';')
        for row in reader:
            if not row or len(row) < 4:
                continue
            try:
                cycle = int(row[0])
                avg = float(row[1])
                conv = int(row[2])
                total = int(row[3])
            except ValueError:
                continue
            cycles.append(cycle)
            avg_sizes.append(avg)
            converged_pct.append(100.0 * conv / total)

    label = path.replace('results-', 'N=').replace('.csv', ' nodes')
    ax1.plot(cycles, avg_sizes, marker='o', label=label, markersize=4)
    ax2.plot(cycles, converged_pct, marker='s', label=label, markersize=4)

    # Trouver le cycle ou on atteint 99% de convergence (pour zoom auto)
    for c, pct in zip(cycles, converged_pct):
        if pct >= 99.0:
            max_convergence_cycle = max(max_convergence_cycle, c)
            break

# Zoom auto : on borne l'axe X a 1.5x le cycle de convergence le plus tardif
# (avec un minimum de 10 et maximum de 50 pour rester lisible)
zoom_x = max(10, min(50, int(max_convergence_cycle * 1.5) + 1)) if max_convergence_cycle > 0 else 30

ax1.set_xlabel('Gossip cycles (1 cycle = 2 simulated seconds)')
ax1.set_ylabel('Average DHT size per node (out of 100 blocks)')
ax1.set_title('DHT Gossip Convergence (MobiCloud) -- ZOOM')
ax1.legend(loc='lower right')
ax1.grid(True, alpha=0.3)
ax1.set_xlim(0, zoom_x)
fig1.tight_layout()
fig1.savefig('convergence.png', dpi=150)
print(f'Saved convergence.png (X axis: 0 a {zoom_x} cycles)')

ax2.set_xlabel('Gossip cycles (1 cycle = 2 simulated seconds)')
ax2.set_ylabel('% of nodes with complete DHT')
ax2.set_title('Percentage of Converged Nodes -- ZOOM')
ax2.legend(loc='lower right')
ax2.grid(True, alpha=0.3)
ax2.set_xlim(0, zoom_x)
ax2.set_ylim(-5, 105)
fig2.tight_layout()
fig2.savefig('converged-pct.png', dpi=150)
print(f'Saved converged-pct.png (X axis: 0 a {zoom_x} cycles)')

# Bonus : on cree aussi un graphique en echelle LOG pour mieux voir
# les differences entre les tailles aux premiers cycles
fig3, ax3 = plt.subplots(figsize=(10, 6))
for path in sys.argv[1:]:
    encoding = detect_encoding(path)
    cycles, converged_pct = [], []
    with open(path, encoding=encoding) as f:
        reader = csv.reader(f, delimiter=';')
        for row in reader:
            if not row or len(row) < 4:
                continue
            try:
                cycle = int(row[0])
                conv = int(row[2])
                total = int(row[3])
            except ValueError:
                continue
            if cycle == 0:
                continue  # log scale n'aime pas 0
            cycles.append(cycle)
            converged_pct.append(100.0 * conv / total)
    label = path.replace('results-', 'N=').replace('.csv', ' nodes')
    ax3.plot(cycles, converged_pct, marker='o', label=label, markersize=5)

ax3.set_xscale('log')
ax3.set_xlabel('Gossip cycles (log scale)')
ax3.set_ylabel('% of nodes with complete DHT')
ax3.set_title('Convergence (log scale) -- differences between network sizes')
ax3.legend(loc='lower right')
ax3.grid(True, alpha=0.3, which='both')
ax3.set_ylim(-5, 105)
fig3.tight_layout()
fig3.savefig('converged-log.png', dpi=150)
print('Saved converged-log.png (X axis en echelle logarithmique)')
