package com.github.quiltservertools.ledger.database

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actions.ActionType
import com.github.quiltservertools.ledger.config.DatabaseSpec
import com.github.quiltservertools.ledger.logWarn
import com.github.quiltservertools.ledger.utility.ticks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue

object ActionQueueService {
    private val queue = LinkedBlockingQueue<ActionType>()
    private lateinit var job: Job

    val size: Int get() = queue.size

    fun start() {
        job = Ledger.launch {
            prepareNextBatch()
        }
    }

    fun addToQueue(action: ActionType): Boolean {
        if (action.isBlacklisted()) return false

        return queue.add(action)
    }

    suspend fun drainAll() {
        job.cancel()
        while (queue.isNotEmpty()) {
            drainBatch()
        }
    }

    private suspend fun drainBatch() {
        val batch = mutableListOf<ActionType>()
        queue.drainTo(batch, Ledger.config[DatabaseSpec.batchSize])

        saveBatch(batch)
    }

    /** A batch is saved all or nothing, so if it fails, its actions get another chance without the one at fault. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun saveBatch(batch: List<ActionType>) {
        try {
            DatabaseManager.logActionBatch(batch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DatabaseManager.reloadCache()
            if (batch.size == 1) {
                logWarn("Could not save an action, it has been lost: ${batch.single().identifier}", e)
            } else {
                logWarn("Could not save a batch of ${batch.size} actions, saving them one at a time", e)
                batch.forEach { saveBatch(listOf(it)) }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun prepareNextBatch() {
        job = Ledger.launch {
            if (queue.size < Ledger.config[DatabaseSpec.batchSize]) {
                delay(Ledger.config[DatabaseSpec.batchDelay].ticks)
            }
            try {
                if (queue.isNotEmpty()) drainBatch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Without this the queue stops for good, and nothing else is logged until a restart.
                logWarn("Could not save actions", e)
            }
            prepareNextBatch()
        }
    }
}
