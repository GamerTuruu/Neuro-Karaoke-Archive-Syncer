package com.neurok.syncer.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatLocalTime(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
