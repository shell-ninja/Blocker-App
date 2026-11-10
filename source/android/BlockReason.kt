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
            BlockCategory.DOMAIN_URL -> "Reason: Blocked URL — $triggerValue"
            BlockCategory.KEYWORD -> "Reason: Matched Keyword — \"$triggerValue\""
            BlockCategory.SHORT_FORM_VIDEO -> "Reason: Distraction Shield — $triggerValue"
            BlockCategory.BLOCKED_APP -> "Reason: Blocked App — $triggerValue"
            BlockCategory.STUDY_LOCKDOWN -> if (triggerValue.isNotBlank()) "Reason: Focus Mode Lockdown — $triggerValue" else "Reason: Focus Mode Lockdown"
            BlockCategory.TAMPER_GUARD -> if (triggerValue.isNotBlank()) "Reason: System Settings Protected — $triggerValue" else "Reason: System Settings Protected"
        }
    }

    fun toBangla(): String {
        return when (category) {
            BlockCategory.DOMAIN_URL -> "কারণ: ব্লককৃত ওয়েবসাইট — $triggerValue"
            BlockCategory.KEYWORD -> "কারণ: নিষিদ্ধ কীওয়ার্ড — \"$triggerValue\""
            BlockCategory.SHORT_FORM_VIDEO -> "কারণ: রিলস শিল্ড — $triggerValue"
            BlockCategory.BLOCKED_APP -> "কারণ: নিষিদ্ধ অ্যাপ্লিকেশন — $triggerValue"
            BlockCategory.STUDY_LOCKDOWN -> if (triggerValue.isNotBlank()) "কারণ: ফোকাস মোড সক্রিয় — $triggerValue" else "কারণ: ফোকাস মোড সক্রিয়"
            BlockCategory.TAMPER_GUARD -> if (triggerValue.isNotBlank()) "কারণ: সিস্টেম সেটিংস সুরক্ষিত — $triggerValue" else "কারণ: সিস্টেম সেটিংস সুরক্ষিত"
        }
    }

    fun toHudText(isBangla: Boolean = false): String {
        return if (isBangla) toBangla() else toEnglish()
    }
}
