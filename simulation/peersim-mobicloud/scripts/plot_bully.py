"""
Visualise le resultat de la simulation Bully Election.

Usage :
    python plot_bully.py bully-results-100.csv [bully-results-1000.csv ...]

Genere :
    bully-superpeers.png : nombre de super-peers vs cycles
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


if len(sys.argv) < 2:
    print("Usage: python plot_bully.py bully-results-N.csv [...]")
    sys.exit(1)

fig, ax = plt.subplots(figsize=(10, 6))

for path in sys.argv[1:]:
    encoding = detect_encoding(path)
    print(f"Lecture {path} (encoding={encoding})")
    cycles, nb_super = [], []
    with open(path, encoding=encoding) as f:
        reader = csv.reader(f, delimiter=';')
        for row in reader:
            if not row or len(row) < 4:
                continue
            try:
                cycle = int(row[0])
                count = int(row[1])
            except ValueError:
                continue
            cycles.append(cycle)
            nb_super.append(count)

    label = path.replace('bully-results-', 'N=').replace('.csv', ' nodes')
    ax.plot(cycles, nb_super, marker='o', label=label, markersize=5)

# Ligne horizontale a y=1 (cible : 1 seul super-peer)
ax.axhline(y=1, color='red', linestyle='--', alpha=0.5,
           label='Cible : 1 seul super-peer')

ax.set_xlabel('Cycles d\'election Bully (1 cycle = 2s simulees)')
ax.set_ylabel('Nombre de super-peers actifs dans le reseau')
ax.set_title('Convergence Bully Election (MobiCloud)')
ax.legend(loc='upper right')
ax.grid(True, alpha=0.3)
fig.tight_layout()
fig.savefig('bully-superpeers.png', dpi=150)
print('Saved bully-superpeers.png')
