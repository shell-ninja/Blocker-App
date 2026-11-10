package com.blocker

enum class BlockCategory {
    DOMAIN_URL,
    KEYWORD,
    SHORT_FORM_VIDEO,
    BLOCKED_APP,
    STUDY_LOCKDOWN,
    TAMPER_GUARD
}

data class BlockReason(
    val category: BlockCategory,
    val triggerValue: String = ""
) {
    fun toEnglish(): String {
        return when (category) {
            BlockCategory.DOMAIN_URL -> "Blocked URL: $triggerValue"
            BlockCategory.KEYWORD -> "Blocked Keyword: \"$triggerValue\""
            BlockCategory.SHORT_FORM_VIDEO -> "Blocked Feed: $triggerValue"
            BlockCategory.BLOCKED_APP -> "Blocked App: $triggerValue"
            BlockCategory.STUDY_LOCKDOWN -> if (triggerValue.isNotBlank()) "Reason: Focus Mode Lockdown — $triggerValue" else "Reason: Focus Mode Lockdown"
            BlockCategory.TAMPER_GUARD -> if (triggerValue.isNotBlank()) "Reason: System Settings Protected — $triggerValue" else "Reason: System Settings Protected"
        }
    }

    fun toBangla(): String {
        return when (category) {
            BlockCategory.DOMAIN_URL -> "ব্লককৃত লিংক: $triggerValue"
            BlockCategory.KEYWORD -> "নিষিদ্ধ কীওয়ার্ড: \"$triggerValue\""
            BlockCategory.SHORT_FORM_VIDEO -> "ব্লককৃত ফিড: $triggerValue"
            BlockCategory.BLOCKED_APP -> "ব্লককৃত অ্যাপ: $triggerValue"
            BlockCategory.STUDY_LOCKDOWN -> if (triggerValue.isNotBlank()) "কারণ: ফোকাস মোড সক্রিয় — $triggerValue" else "কারণ: ফোকাস মোড সক্রিয়"
            BlockCategory.TAMPER_GUARD -> if (triggerValue.isNotBlank()) "কারণ: সিস্টেম সেটিংস সুরক্ষিত — $triggerValue" else "কারণ: সিস্টেম সেটিংস সুরক্ষিত"
        }
    }

    fun toHudText(isBangla: Boolean = false): String {
        return if (isBangla) toBangla() else toEnglish()
    }
}
