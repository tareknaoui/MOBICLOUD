# Code Review - Blind Hunter Prompt

**Mode:** Blind Hunter (No context, diff only)

**Diff Content:**

Story 4.1 Implementation - DHT Local Partition Modeling & Persistence (7150 lines)

Primary changes:
- Removed UDP Multicast heartbeat components (UdpHeartbeatBroadcaster.kt, UdpHeartbeatReceiver.kt - 180 lines deleted)
- Removed HeartbeatMessage.kt and HeartbeatPayload.kt models
- Removed UDP-related tests (229 + 75 lines deleted)
- Modified MobicloudP2PService.kt to remove multicast lock logic (~112 lines modified)
- Updated AndroidManifest.xml to remove CHANGE_WIFI_MULTICAST_STATE permission
- Updated epics.md: renamed "Karma" system to "Weight", removed UDP multicast from functional requirements, added Epic 9 (AI Prediction)
- Updated Peer.kt and PeerRepository.kt to change default discovery source from LOCAL_UDP to REMOTE_FIREBASE
- Deleted build artifacts and test output files

**Task:** Perform cynical adversarial review. Find at least 10 issues or concerns with the code changes. Report only descriptions.

---

DIFF EXCERPT (key sections):

## 1. MobicloudP2PService.kt Changes

```diff
-import android.net.wifi.WifiManager
-import com.mobicloud.data.p2p.UdpHeartbeatBroadcaster
-import com.mobicloud.data.p2p.UdpHeartbeatReceiver
-import com.mobicloud.domain.models.HeartbeatPayload

-private var multicastLock: WifiManager.MulticastLock? = null

-@Inject lateinit var heartbeatBroadcaster: UdpHeartbeatBroadcaster
-@Inject lateinit var heartbeatReceiver: UdpHeartbeatReceiver

-acquireMulticastLock()

-// Loop 1: Broadcaster
-launch {
-    val result = heartbeatBroadcaster.startBroadcasting(heartbeatPayload, reliabilityScoreFlow)
-    if (result.isFailure) {
-        Log.w("MobicloudP2PService", "Broadcast failed", result.exceptionOrNull())
-    }
-}

-// Loop 2: Receiver
-launch {
-    heartbeatReceiver.receiveHeartbeats().collect { result ->
-        if (result.isSuccess) {
-            val msg = result.getOrThrow()
-            if (msg.identity.nodeId != identity.nodeId) {
-                peerRepository.registerOrUpdatePeer(
-                    identity = msg.identity,
-                    timestampMs = SystemClock.elapsedRealtime(),
-                    ipAddress = msg.senderIp,
-                    port = msg.tcpPort
-                )
-            }
-        }
-    }
-}

-// Loop 4: Stability Monitor — filtre sur isActive
-launch {
-    peerRepository.peers.collect { peers ->
-        val hasActivePeers = peers.any { it.isActive }
-        heartbeatBroadcaster.setStable(hasActivePeers)
-        if (!hasActivePeers) {
-            heartbeatBroadcaster.resetBackoff()
-        }
-    }
-}

-// Loop 5: Network Monitoring
-launch {
-    networkUtils.getCurrentState().collect {
-        heartbeatBroadcaster.resetBackoff()
-    }
-}
```

## 2. AndroidManifest.xml Changes

```diff
-<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

## 3. Peer.kt Changes

```diff
enum class DiscoverySource {
-    LOCAL_UDP,
     REMOTE_FIREBASE
 }

 data class Peer(
     val identity: NodeIdentity,
     val lastSeenTimestampMs: Long,
-    val source: DiscoverySource = DiscoverySource.LOCAL_UDP,
+    val source: DiscoverySource = DiscoverySource.REMOTE_FIREBASE,
```

## 4. Firebase Logic Changes

```diff
-// Firebase announce — délai 10s, seulement si aucun pair local actif (AC1)
+// Firebase announce — publie l'IP publique sur Firebase pour la fédération inter-réseaux
 launch {
-    delay(10_000L)
-    if (peerRepository.peers.value.any { it.isActive }) {
-        Log.d("MobicloudP2PService", "Pairs locaux actifs détectés — announce Firebase ignorée")
-        return@launch
-    }
```

---

**Instructions:**
1. Review the diff changes adversarially
2. List at least 10 findings (issues, concerns, risks, missing handling)
3. Format as Markdown list with one-line descriptions
4. Focus on code correctness, architecture risks, missing error handling, edge cases, backwards compatibility
