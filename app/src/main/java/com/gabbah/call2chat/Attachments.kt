package com.gabbah.call2chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Allegati inviati insieme al messaggio: fino a 3 foto, 1 video e 1 PDF.
 * I file vengono copiati dentro l'app, così restano disponibili anche se l'originale sparisce.
 */
object Attachments {
    enum class Kind(val id: String, val mime: String, val max: Int) {
        PHOTO("photo", "image/*", 3), VIDEO("video", "video/*", 1), PDF("pdf", "application/pdf", 1)
    }

    data class Item(val kind: Kind, val file: String, val name: String)

    private fun dir(ctx: Context) = File(ctx.filesDir, "attach").apply { mkdirs() }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("call2chat", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Item> {
        val arr = JSONArray(prefs(ctx).getString("attachments", "[]"))
        return (0 until arr.length()).mapNotNull {
            val o = arr.getJSONObject(it)
            val k = Kind.entries.firstOrNull { k -> k.id == o.optString("k") } ?: return@mapNotNull null
            Item(k, o.optString("f"), o.optString("n"))
        }.filter { File(dir(ctx), it.file).exists() }
    }

    private fun save(ctx: Context, list: List<Item>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("k", it.kind.id).put("f", it.file).put("n", it.name)) }
        prefs(ctx).edit().putString("attachments", arr.toString()).apply()
    }

    fun count(ctx: Context, k: Kind) = list(ctx).count { it.kind == k }
    fun canAdd(ctx: Context, k: Kind) = k.max == 1 || count(ctx, k) < k.max

    /** Copia il file scelto. Video e PDF sostituiscono quello che c'era. */
    fun add(ctx: Context, k: Kind, uri: Uri): Boolean {
        var list = list(ctx)
        if (k.max == 1) list.filter { it.kind == k }.forEach { File(dir(ctx), it.file).delete() }
        if (k.max == 1) list = list.filter { it.kind != k }
        if (list.count { it.kind == k } >= k.max) return false
        val cr = ctx.contentResolver
        var name = "file"
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        val ext = name.substringAfterLast('.', "").takeIf { it.length in 1..5 }
            ?: when (k) { Kind.PHOTO -> "jpg"; Kind.VIDEO -> "mp4"; Kind.PDF -> "pdf" }
        val file = "${k.id}_${System.currentTimeMillis()}.$ext"
        return try {
            cr.openInputStream(uri)?.use { input -> File(dir(ctx), file).outputStream().use { input.copyTo(it) } }
                ?: return false
            save(ctx, list + Item(k, file, name))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun remove(ctx: Context, item: Item) {
        File(dir(ctx), item.file).delete()
        save(ctx, list(ctx).filter { it.file != item.file })
    }

    fun file(ctx: Context, item: Item) = File(dir(ctx), item.file)

    fun uris(ctx: Context): ArrayList<Uri> = ArrayList(list(ctx).map {
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file(ctx, it))
    })

    /** Riassunto breve, es. "3 foto · video · PDF" */
    fun summary(ctx: Context): String? {
        val l = list(ctx)
        if (l.isEmpty()) return null
        val parts = mutableListOf<String>()
        val p = l.count { it.kind == Kind.PHOTO }
        if (p > 0) parts += ctx.resources.getQuantityString(R.plurals.att_photos, p, p)
        if (l.any { it.kind == Kind.VIDEO }) parts += ctx.getString(R.string.att_video)
        if (l.any { it.kind == Kind.PDF }) parts += ctx.getString(R.string.att_pdf)
        return parts.joinToString(" · ")
    }
}
