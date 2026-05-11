# Simulation PeerSim de MobiCloud DHT

Modele PeerSim du protocole **DHT replique + gossip CRDT LWW** de MobiCloud,
permettant de simuler la convergence du systeme de 10 a 10000 noeuds.

## Setup (15 minutes)

### 1. Telecharger PeerSim 1.0.5

- Aller sur http://peersim.sourceforge.net/
- Telecharger `peersim-1.0.5.zip` (ou `.tar.gz`)
- Extraire le contenu dans `lib/`

Tu dois avoir :
```
lib/
  peersim-1.0.5.jar
  jep-2.3.0.jar
  djep-1.0.0.jar
```

### 2. Compiler les sources MobiCloud

Sur Windows PowerShell :
```powershell
cd simulation\peersim-mobicloud
mkdir build
javac -cp "lib\peersim-1.0.5.jar;lib\jep-2.3.0.jar;lib\djep-1.0.0.jar" -d build src\*.java
```

### 3. Lancer une simulation

```powershell
java -cp "lib\peersim-1.0.5.jar;lib\jep-2.3.0.jar;lib\djep-1.0.0.jar;build" peersim.Simulator config\mobicloud-100.txt > results-100.csv
```

Sortie attendue (~5 secondes d'execution) : un CSV avec une ligne par cycle :
```
0;1.00;1;100
1;2.94;1;100
2;7.88;2;100
3;19.45;5;100
...
```

Colonnes : `cycle ; taille_moyenne_DHT ; nb_noeuds_converges ; total_noeuds`.

### 4. Generer les graphiques

```powershell
cd scripts
python plot.py ..\results-100.csv
```

Genere `convergence.png` et `converged-pct.png` que tu peux integrer
directement dans ton rapport.

## Lancer plusieurs tailles

Duplique `config\mobicloud-100.txt` en `mobicloud-500.txt` et change juste :
```
network.size 500
control.observer.targetSize 100   # ou 500 si tu veux test sur 500 blocs
```

Puis :
```powershell
java -cp "..." peersim.Simulator config\mobicloud-500.txt > results-500.csv
java -cp "..." peersim.Simulator config\mobicloud-1000.txt > results-1000.csv

python scripts\plot.py results-100.csv results-500.csv results-1000.csv
```

Le graphe affiche les 3 courbes superposees.

## Ce que ca demontre pour ta these

1. **Convergence epidemique** : la DHT converge en O(log N) cycles de gossip,
   peu importe la taille du reseau. C'est la propriete cle du gossip.
2. **Robustesse** : meme avec fan-out=2 (chaque noeud parle a 2 voisins par
   cycle), la convergence est rapide.
3. **Scalabilite** : mesure quantifiee jusqu'a 10000 noeuds.

## Pour aller plus loin

- Ajouter `control.churn` pour simuler des entrees/sorties de noeuds
- Ajouter un protocole Bully election a comparer
- Simuler des partitions reseau (DynamicNetwork.minsize/maxsize)
