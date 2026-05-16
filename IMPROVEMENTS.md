# Améliorations futures — MobiCloud V5

## Stabilité découverte clusters distants (Tracker)

**Symptôme**: Les clusters distants apparaissent/disparaissent intermittement dans l'UI.

**Cause**: Le relais Render a un TTL pour les entrées SP (~60s). Si `REGISTER_PEER` keepalive arrive en retard, l'entrée expire → disparaît de `GET_PEERS` → réapparaît au prochain keepalive.

**Impact**: Mineur — affecte uniquement la *découverte* des clusters distants. Les membres déjà dans leur cluster restent connectés (heartbeat/liveness interne, indépendant du tracker).

**Solutions**:
1. **Réduire intervalle GET_PEERS** : Actuellement 10s dans `MobicloudP2PService` (ligne 557)
   ```kotlin
   // Avant
   delay(10_000L)  // GET_PEERS toutes les 10s
   
   // Après (plus agressif)
   delay(5_000L)   // GET_PEERS toutes les 5s
   ```

2. **Augmenter fréquence REGISTER_PEER keepalive**: Actuellement via `registerSuperPeerUseCase` keepalive loop
   - Vérifier intervalle dans `RegisterSuperPeerUseCase`
   - Réduire si > 10s

3. **Augmenter TTL relay**: Si possible côté relais (config Render)
   - Demander TTL ≥ 90s (aligné sur `SP_TIMEOUT_MS`)

**Effort**: ~2h (option 1) — juste des constantes, pas de refactoring logic.

**Priorité**: Low. OK pour demo/thèse. À considérer pour V5.1 prod.
