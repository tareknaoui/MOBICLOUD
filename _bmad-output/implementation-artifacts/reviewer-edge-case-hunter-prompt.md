# Code Review - Edge Case Hunter Prompt

**Mode:** Edge Case Hunter (With project access for reading codebase)

**Story Context:** Story 4.1 - DHT Local Partition Modeling & Persistence

**Diff Changes Summary:**
- Complete removal of UDP Multicast heartbeat discovery (UdpHeartbeatBroadcaster, UdpHeartbeatReceiver)
- Migration from hybrid (local UDP + Firebase) to Firebase-only discovery
- Removal of multicast lock and permission handling
- Default peer discovery source changed from LOCAL_UDP to REMOTE_FIREBASE
- Firebase announce logic simplified (removed conditional delay)
- Removal of stability monitor and network monitoring loops dependent on UDP
- Architectural shift from Story 2.1 UDP discovery to Story 2.1 Firebase signaling only

**Your Task:**

Perform exhaustive path tracing on the diff hunks. Walk every branching path and boundary condition reachable from the changed code. Identify paths that lack explicit guards or error handling. Report ONLY unhandled paths.

**Areas to Analyze:**

1. **Peer Registration Without UDP Source:**
   - UDP heartbeats previously registered peers with `DiscoverySource.LOCAL_UDP`
   - Path: What happens when NO peers are discovered via Firebase within first 10 seconds?
   - Boundary: Empty peer list → decision points in peer selection, consensus, TCP connection initiation

2. **Firebase Dependency Path:**
   - Firebase is now the ONLY discovery mechanism
   - Path: Firebase unavailable/timeout → cascading effect on peer registry
   - Boundary: How many seconds before system recognizes firebase failure? What's the fallback?

3. **Reliability Score Distribution:**
   - Previously: included in UDP heartbeat broadcast
   - Now: score computed but where is it announced to peers?
   - Path: Score change → propagation to cluster → other nodes' awareness
   - Boundary: Score update latency, race conditions with elections/migrations

4. **Multicast Lock Removal:**
   - Was: explicitly acquired/released
   - Now: removed entirely
   - Path: WiFi multicast packets under low battery or OS pressure → filtering behavior
   - Boundary: Does Firebase still work when OS silently blocks multicast? (residual dependency?)

5. **Default Peer Source Enum Change:**
   - Path: Code that pattern-matches on `DiscoverySource.LOCAL_UDP`
   - Boundary: Grep for `LOCAL_UDP` references in codebase — do any branches assume it still exists?

6. **Stability Monitor Loop Removal:**
   - Was: monitored `isActive` peers and triggered `heartbeatBroadcaster.setStable()`
   - Now: removed entirely
   - Path: Connection stability → broadcast interval adjustment → absent
   - Boundary: High churn scenarios, rapid peer transitions → unchanged interval?

7. **Network State Listener Removal:**
   - Was: `networkUtils.getCurrentState().collect()` → reset broadcast backoff
   - Now: removed
   - Path: Network switch (WiFi↔4G) → broadcast adaptation absent
   - Boundary: App on 4G, switches to WiFi → Firebase registration timing unchanged?

8. **Firebase Announce Delay Logic:**
   - Was: `delay(10_000L)` before Firebase announce if local peers detected
   - Now: delay removed, unconditional announce
   - Path: App startup with existing local peers (from prior session in memory) → Firebase announce timing
   - Boundary: Race: Firebase announce fires immediately while peer eviction hasn't run yet

9. **Error Message Changes:**
   - Changes from "Firebase indisponible — mode local" to just "Firebase indisponible"
   - Path: Fallback behavior unclear — do logs match code reality?
   - Boundary: Operator debugging networked system, sees logs, expects local UDP discovery to still work

10. **Hilt Injection Removal:**
    - @Inject for heartbeatBroadcaster and heartbeatReceiver removed
    - Path: Any other component still injecting these in other modules?
    - Boundary: Compile success but runtime NPE if leftover @Inject in another service

---

**Output Format (JSON):**

Return findings as JSON array. Each object:
- `location`: file:line or file:start-end
- `trigger_condition`: one-line description (max 15 words)
- `guard_snippet`: minimal code to fix (single-line escaped string)
- `potential_consequence`: actual risk (max 15 words)

Example:
```json
[
  {
    "location": "MobicloudP2PService.kt:160",
    "trigger_condition": "Firebase registerNode fails with no fallback logic",
    "guard_snippet": "if (result.isFailure) fallbackToUdpAnnounce()",
    "potential_consequence": "Peer isolated if Firebase down, no local discovery"
  }
]
```

Empty array `[]` if all paths are handled.
