package com.mobicloud.domain.models

enum class ServiceStatus {
    STOPPED,    // Service non démarré (état initial)
    STARTING,   // Appel startForegroundService() en cours
    RUNNING,    // Service actif, MulticastLock acquis
    ERROR       // Échec démarrage (ex: ForegroundServiceStartNotAllowedException)
}
