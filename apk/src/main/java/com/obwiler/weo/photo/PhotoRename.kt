package com.obwiler.weo.photo

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

object PhotoRename {

    private const val TAG = "WEO/Photo"
    private val CHINESE_WORD = Pattern.compile("[\u4e00-\u9fff]{2,5}")
    private val STOP_WORDS = setOf(
        "这是", "一个", "一张", "图片", "照片", "图中", "可以看到",
        "里面有", "显示", "内容", "可能", "应该", "看起来", "像是",
        "分析", "识别", "结果", "根据"
    )

    /**
     * Generate a smart short filename from AI analysis result.
     *
     * Format: {keyword≤5chars}{MMdd}_{HHmm}.jpg
     *
     * Strategy:
     *  1. Extract first meaningful Chinese phrase (2-5 chars)
     *  2. Filter out stop words ("这是", "图片", etc.)
     *  3. If no Chinese found, use first 5 chars of response or "未命名"
     *  4. Append MMDD_HHmm from current time
     */
    fun generateName(aiAnswer: String, timestampMs: Long = System.currentTimeMillis()): String {
        val keyword = extractKeyword(aiAnswer)
        val date = Date(timestampMs)
        val mmdd = SimpleDateFormat("MMdd", Locale.US).format(date)
        val hhmm = SimpleDateFormat("HHmm", Locale.US).format(date)
        val safe = keyword.replace(Regex("[/\\\\:*?\"<>|]"), "")
        return "${safe}${mmdd}_${hhmm}.jpg"
    }

    private fun extractKeyword(text: String): String {
        if (text.isBlank()) return "未命名"

        // Find all Chinese word candidates
        val matcher = CHINESE_WORD.matcher(text)
        val candidates = mutableListOf<String>()
        while (matcher.find()) {
            candidates.add(matcher.group())
        }

        // Filter stop words and pick first meaningful one
        for (c in candidates) {
            val clean = c.replace(Regex("[的了吗呢啊吧]"), "")
            if (clean.length >= 2 && !STOP_WORDS.contains(c)) {
                val result = clean.take(5)
                Log.d(TAG, "Keyword: '$result' from '$text'")
                return result
            }
        }

        // Fallback: use first non-stop candidate
        for (c in candidates) {
            if (c.length >= 2) {
                val result = c.take(5)
                Log.d(TAG, "Fallback keyword: '$result'")
                return result
            }
        }

        // Last resort: first 5 chars of text, or "未命名"
        val fallback = text.take(5).trim()
        return if (fallback.isNotEmpty()) fallback else "未命名"
    }
}
