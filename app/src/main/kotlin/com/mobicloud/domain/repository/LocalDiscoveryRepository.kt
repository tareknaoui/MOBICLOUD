package com.mobicloud.domain.repository

interface LocalDiscoveryRepository {
    fun start(tcpPort: Int)
    fun stop()
    fun updateTcpPort(port: Int)
}
