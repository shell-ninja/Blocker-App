package com.blocker.data

import android.content.Context
import com.blocker.NativeBlockerBridge
import java.io.File

data class CompiledRuleSnapshot(
    val blockedApps: Set<String> = emptySet(),
    val blockedDomains: Set<String> = emptySet(),
    val blockedKeywords: Set<String> = emptySet(),
    val isProtectionActive: Boolean = false,
    val isShieldActive: Boolean = true
)

object RuleSnapshotHolder {
    @Volatile
    var snapshot: CompiledRuleSnapshot = CompiledRuleSnapshot()
        private set

    fun update(newSnapshot: CompiledRuleSnapshot) {
        snapshot = newSnapshot
    }
}

object MappedBlocklistEngine {
    fun load(context: Context): Boolean {
        return runCatching {
            val binFile = File(context.filesDir, "blocklist.bin")
            if (!binFile.exists() || binFile.length() == 0L) {
                runCatching {
                    context.assets.open("blocklist.bin").use { input ->
                        binFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            if (binFile.exists() && binFile.length() > 0L) {
                NativeBlockerBridge.load(binFile)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
