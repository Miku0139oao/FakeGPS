package com.github.fakegps

import android.content.Context
import android.content.res.Configuration
import com.github.fakegps.model.LocBookmark
import com.github.fakegps.model.LocPoint
import java.util.Locale

/**
 * Naming rules for bookmarks.
 *
 * <p>Older versions pre-filled every bookmark with the generic, localized word
 * for "Bookmark" ("書籤", "Lesezeichen", "Favorito", ...). Those names are
 * stored in the database, so comparing against the <em>current</em> language's
 * string breaks as soon as the user switches the app language: every legacy
 * row keeps the old-language word and duplicates become indistinguishable.
 * [isGenericName] therefore compares against the generic word of every locale
 * the app ships.</p>
 */
object BookmarkNames {

    @Volatile
    private var genericNames: Set<String>? = null

    /** True for blank names and for the legacy "just the word Bookmark" names. */
    @JvmStatic
    fun isGenericName(context: Context, name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return true
        }
        return genericNames(context).any { it.equals(trimmed, ignoreCase = true) }
    }

    /**
     * Same check, plus names that are just the bookmark's own coordinates.
     * One-tap saving and the rename prefill both produce those, and showing
     * them as a title above the coordinate subtitle would repeat the point.
     */
    @JvmStatic
    fun isGenericName(context: Context, bookmark: LocBookmark): Boolean {
        val point = bookmark.locPoint
        if (point != null && isAutoName(bookmark.name, point)) {
            return true
        }
        return isGenericName(context, bookmark.name)
    }

    private fun isAutoName(name: String?, point: LocPoint): Boolean {
        val compact = name?.replace(" ", "").orEmpty()
        if (compact.isEmpty()) {
            return true
        }
        return compact == autoName(point).replace(" ", "") ||
            compact == point.toString().replace(" ", "")
    }

    /** Unique, locale-independent default name derived from the coordinates. */
    @JvmStatic
    fun autoName(locPoint: LocPoint): String =
        String.format(Locale.US, "%.4f, %.4f", locPoint.latitude, locPoint.longitude)

    /**
     * Name to show for a bookmark: the user's name when it means something,
     * otherwise the coordinates (legacy rows were all called "Bookmark").
     */
    @JvmStatic
    fun displayName(context: Context, bookmark: LocBookmark): String {
        val coordinates = bookmark.locPoint?.toString().orEmpty()
        return if (isGenericName(context, bookmark.name)) coordinates else bookmark.name.orEmpty()
    }

    private fun genericNames(context: Context): Set<String> {
        genericNames?.let { return it }
        val appContext = context.applicationContext
        val baseConfig = appContext.resources.configuration
        val names = LinkedHashSet<String>()
        for (tag in LOCALE_TAGS) {
            val config = Configuration(baseConfig)
            config.setLocale(if (tag.isEmpty()) Locale.ROOT else Locale.forLanguageTag(tag))
            names.add(appContext.createConfigurationContext(config).getString(R.string.bookmark))
        }
        genericNames = names
        return names
    }

    /** The default locale plus every {@code values-*} folder in this project. */
    private val LOCALE_TAGS = arrayOf(
        "", "zh-TW", "zh-CN", "ja", "ko", "es", "fr", "de", "pt-BR"
    )
}
