package com.mobicloud.data.repository

import com.mobicloud.domain.models.NetworkLogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkEventRepositoryImplTest {

    private lateinit var repository: NetworkEventRepositoryImpl

    @Before
    fun setUp() {
        repository = NetworkEventRepositoryImpl()
    }

    @Test
    fun `pushEvent ajoute un événement en tête de liste`() {
        repository.pushEvent("Premier")
        repository.pushEvent("Deuxième")

        val events = repository.events.value
        assertEquals(2, events.size)
        assertEquals("Deuxième", events[0].message)
        assertEquals("Premier", events[1].message)
    }

    @Test
    fun `pushEvent respecte le ring buffer de 50 événements max`() {
        repeat(60) { i ->
            repository.pushEvent("Event $i")
        }

        val events = repository.events.value
        assertEquals(50, events.size)
        // Le plus récent est en tête
        assertEquals("Event 59", events[0].message)
        // Le plus ancien conservé est Event 10
        assertEquals("Event 10", events[49].message)
    }

    @Test
    fun `pushEvent est thread-safe — pas de corruption avec plusieurs threads`() {
        val threads = (1..10).map { threadIndex ->
            Thread {
                repeat(10) { i ->
                    repository.pushEvent("Thread$threadIndex-Event$i")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val events = repository.events.value
        // Exactement 50 événements max (100 pushés au total, ring buffer = 50)
        assertEquals(50, events.size)
    }

    @Test
    fun `pushEvent crée NetworkLogEvent avec timestamp valide`() {
        val before = System.currentTimeMillis()
        repository.pushEvent("Test timestamp")
        val after = System.currentTimeMillis()

        val event = repository.events.value.first()
        assertEquals("Test timestamp", event.message)
        assertTrue(event.timestampMs in before..after)
    }

    @Test
    fun `events démarre avec une liste vide`() {
        assertTrue(repository.events.value.isEmpty())
    }

    @Test
    fun `ordering — le dernier événement poussé est toujours en position 0`() {
        repository.pushEvent("A")
        repository.pushEvent("B")
        repository.pushEvent("C")

        assertEquals("C", repository.events.value[0].message)
        assertEquals("B", repository.events.value[1].message)
        assertEquals("A", repository.events.value[2].message)
    }
}
