package com.example.scalp

import android.util.Log
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.system.exitProcess

/**
 * Shizuku UserService (UID 2000) implementation of [ITouchService].
 * Keeps a persistent `sh` process alive to execute low-latency `input tap` and `input swipe` commands.
 */
class TouchUserService : ITouchService.Stub() {

    private val lock = ReentrantLock()
    private var shellProcess: Process? = null
    private var shellWriter: BufferedWriter? = null

    private fun ensureShellLocked(): BufferedWriter? {
        val current = shellProcess
        val writer = shellWriter
        if (current != null && writer != null) {
            val isAlive = try {
                current.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
            if (isAlive) return writer
        }
        closeShellLocked()
        return try {
            val process = ProcessBuilder("sh")
                .redirectErrorStream(true)
                .start()
            val bw = BufferedWriter(OutputStreamWriter(process.outputStream))
            shellProcess = process
            shellWriter = bw
            bw
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start persistent sh process", t)
            null
        }
    }

    private fun closeShellLocked() {
        try {
            shellWriter?.write("exit\n")
            shellWriter?.flush()
        } catch (_: Throwable) {
        }
        try {
            shellWriter?.close()
        } catch (_: Throwable) {
        }
        try {
            shellProcess?.destroy()
        } catch (_: Throwable) {
        }
        shellWriter = null
        shellProcess = null
    }

    override fun tap(x: Int, y: Int) {
        val safeX = x.coerceIn(0, 10000)
        val safeY = y.coerceIn(0, 10000)
        executeCommand("input tap $safeX $safeY")
    }

    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int) {
        val sx1 = x1.coerceIn(0, 10000)
        val sy1 = y1.coerceIn(0, 10000)
        val sx2 = x2.coerceIn(0, 10000)
        val sy2 = y2.coerceIn(0, 10000)
        val dur = durationMs.coerceIn(20, 5000)
        executeCommand("input swipe $sx1 $sy1 $sx2 $sy2 $dur")
    }

    private fun executeCommand(cmd: String) {
        lock.withLock {
            try {
                val writer = ensureShellLocked() ?: return
                writer.write(cmd)
                writer.newLine()
                writer.flush()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to execute shell command: $cmd", t)
                closeShellLocked()
            }
        }
    }

    override fun destroy() {
        lock.withLock {
            closeShellLocked()
        }
        exitProcess(0)
    }

    companion object {
        private const val TAG = "TouchUserService"
    }
}
