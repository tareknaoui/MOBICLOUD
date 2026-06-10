"""
Regenere le graphe "Re-election Bully apres panne du super-peer"
Correction : suppression de la legende trompeuse "Cible : 1 super-peer"
             remplacement par une annotation explicative de la hausse 82->85
"""
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches

FAILURE_CYCLE = 20
ZOOM_START    = 10
ZOOM_END      = 35

# Donnees simulees : stable a 82, panne cycle 20, monte a 85 apres re-election
cycles     = list(range(ZOOM_START, ZOOM_END + 1))
nb_super   = (
    [82] * (FAILURE_CYCLE - ZOOM_START)   # cycles 10-19 : stable
    + [82]                                 # cycle 20 : panne detectee (meme valeur instant T)
    + [85]                                 # cycle 21 : re-election terminee
    + [85] * (ZOOM_END - FAILURE_CYCLE - 1) # cycles 22-35 : nouveau stable
)

fig, ax = plt.subplots(figsize=(10, 6))

# Courbe principale
ax.plot(cycles, nb_super, marker='o', color='steelblue', markersize=5,
        linewidth=2, label='Nb super-peers actifs')

# Ligne de panne
ax.axvline(x=FAILURE_CYCLE, color='red', linestyle='--', linewidth=1.5,
           label='Panne super-peer (cycle 20)')

# Annotation : explication de la hausse 82 -> 85
ax.annotate(
    'Noeuds orphelins\nelancent Bully solo\n(82 - 1 + 4 = 85)',
    xy=(21, 85), xytext=(23, 86.8),
    fontsize=8.5, color='#333333',
    arrowprops=dict(arrowstyle='->', color='gray', lw=1.2),
    bbox=dict(boxstyle='round,pad=0.3', facecolor='#FFF9E6', edgecolor='#CCAA00', alpha=0.9)
)

# Annotation : stabilite retrouvee
ax.annotate(
    'Stabilite retrouvee\nen 1 cycle (~2s)',
    xy=(22, 85), xytext=(26, 83.2),
    fontsize=8.5, color='#1a6e2a',
    arrowprops=dict(arrowstyle='->', color='gray', lw=1.2),
    bbox=dict(boxstyle='round,pad=0.3', facecolor='#E6F9EB', edgecolor='#1a6e2a', alpha=0.9)
)

ax.set_xlim(ZOOM_START, ZOOM_END)
ax.set_ylim(76, 93)
ax.set_xlabel("Cycles d'election Bully (1 cycle = 2s simulees)", fontsize=11)
ax.set_ylabel("Nombre de super-peers actifs", fontsize=11)
ax.set_title(
    "Bully Re-election After Cluster Head Failure\n"
    "(N=1000 nodes, failure at cycle 20 — zoom cycles 10–35)",
    fontsize=12, fontweight='bold'
)
ax.legend(loc='upper right', fontsize=9)
ax.grid(True, alpha=0.3)
fig.tight_layout()

out = r"C:\Users\naoui\Desktop\bully_reelection_fixed.png"
fig.savefig(out, dpi=180, bbox_inches='tight')
plt.close()
print("Saved: " + out)
