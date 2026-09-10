package com.generacionarcade.speccyos

import com.google.firebase.Timestamp

data class BugReport(
    val reportId: String = "",
    val userId: String = "",
    val description: String = "",
    val deviceModel: String = android.os.Build.MODEL,
    val androidVersion: Int = android.os.Build.VERSION.SDK_INT,
    val activeTheme: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: String = "OPEN" // OPEN, FIXED, IGNORED
)
