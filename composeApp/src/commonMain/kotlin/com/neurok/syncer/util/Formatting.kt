package com.neurok.syncer.util

/** Format a Unix-epoch millisecond timestamp as a local-time date/time string (e.g. "2025-01-31 02:15"). */
expect fun formatLocalTime(ms: Long): String
