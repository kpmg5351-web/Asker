package com.example.core

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File

object ShopLogoStorage {

    private const val LOGO_DIR = "shop_logos"

    /**
     * Copies a temporary Photo Picker / content:// URI into app-private persistent storage
     * (`filesDir/shop_logos/`) so the shop logo survives app restarts and process death.
     */
    fun persistLogo(context: Context, tenantId: String, sourceUri: Uri): Result<String> = runCatching {
        val appCtx = context.applicationContext
        val dir = File(appCtx.filesDir, LOGO_DIR).apply { if (!exists()) mkdirs() }
        val mime = appCtx.contentResolver.getType(sourceUri)
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.lowercase()
            ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp") }
            ?: "jpg"

        val prefix = "logo_${tenantId}_"
        val destFile = File(dir, "$prefix${System.currentTimeMillis()}.$ext")

        appCtx.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                val copied = input.copyTo(output)
                require(copied > 0L) { "ملف الصورة فارغ" }
            }
        } ?: error("تعذر قراءة ملف الصورة المختار")

        // Remove older logo files for this tenant to prevent storage buildup
        dir.listFiles()?.forEach { old ->
            if (old.isFile && old.name.startsWith(prefix) && old.absolutePath != destFile.absolutePath) {
                old.delete()
            }
        }

        Uri.fromFile(destFile).toString()
    }

    fun isReadableLogoUri(context: Context, logoUri: String?): Boolean {
        if (logoUri.isNullOrBlank()) return false
        val parsed = runCatching { Uri.parse(logoUri) }.getOrNull() ?: return false
        return if (parsed.scheme == "file" || parsed.scheme == null) {
            val path = parsed.path ?: return false
            val f = File(path)
            f.exists() && f.isFile && f.length() > 0L
        } else {
            runCatching {
                context.contentResolver.openInputStream(parsed)?.use { true } ?: false
            }.getOrDefault(false)
        }
    }

    fun deleteTenantLogos(context: Context, tenantId: String) {
        val dir = File(context.applicationContext.filesDir, LOGO_DIR)
        if (!dir.exists()) return
        val prefix = "logo_${tenantId}_"
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith(prefix)) {
                file.delete()
            }
        }
    }
}
