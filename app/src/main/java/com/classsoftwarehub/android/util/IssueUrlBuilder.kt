package com.classsoftwarehub.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.classsoftwarehub.android.BuildConfig
import java.net.URLEncoder

/** GitHub Issue 预填与打开 */
object IssueUrlBuilder {

    private const val LABEL_SOFTWARE = "software-submission"
    private const val LABEL_FEEDBACK = "feedback"

    fun softwareSubmissionUrl(title: String, body: String): String =
        build(LABEL_SOFTWARE, title, body)

    fun feedbackUrl(title: String, body: String): String =
        build(LABEL_FEEDBACK, title, body)

    private fun build(labels: String, title: String, body: String): String {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedBody = URLEncoder.encode(body, "UTF-8")
        val encodedLabels = URLEncoder.encode(labels, "UTF-8")
        return "https://github.com/${BuildConfig.GITHUB_REPO}/issues/new" +
            "?title=$encodedTitle&body=$encodedBody&labels=$encodedLabels"
    }

    fun open(context: Context, url: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    }.getOrDefault(false)

    fun openUrl(context: Context, url: String): Boolean = open(context, url)

    fun deviceInfo(): String = buildString {
        append("- App 版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        append("- Android：${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        append("- 设备：${Build.MANUFACTURER} ${Build.MODEL}")
    }
}
