package com.github.fakegps.model

import android.annotation.SuppressLint
import com.litesuits.orm.db.annotation.Column
import com.litesuits.orm.db.annotation.NotNull
import com.litesuits.orm.db.annotation.PrimaryKey
import com.litesuits.orm.db.annotation.Table
import com.litesuits.orm.db.enums.AssignType
import java.io.Serializable

/**
 * Bookmark for a location.
 *
 * <p><b>Persistence contract:</b> lite-orm is field-reflective. The table name,
 * the private field names {@code id}/{@code mName}/{@code mLocPoint} and the
 * annotations define the schema of {@code fake_gps.db}. The public computed
 * properties keep the Java bean API without creating extra columns.</p>
 */
// lite-orm's @NotNull describes the SQL column, not Kotlin nullness: legacy rows
// may still contain null, so the field stays String?.
@SuppressLint("KotlinNullnessAnnotation")
@Table("LocBookmark")
class LocBookmark : Serializable {

    @field:PrimaryKey(AssignType.AUTO_INCREMENT)
    @field:Column("_id")
    var id: Int = 0
        private set

    @field:NotNull
    private var mName: String? = null

    private var mLocPoint: LocPoint? = null

    constructor(name: String?, locPoint: LocPoint?) {
        mName = name
        mLocPoint = locPoint
    }

    constructor() : this(null, null)

    var name: String?
        get() = mName
        set(value) {
            mName = value
        }

    var locPoint: LocPoint?
        get() = mLocPoint
        set(value) {
            mLocPoint = value
        }

    override fun toString(): String {
        val builder = StringBuilder("LocBookmark: ")
        builder.append(mName)
        builder.append(" ")
        builder.append(mLocPoint?.toString() ?: "loc null")
        return builder.toString()
    }

    private companion object {
        private const val serialVersionUID = -1770575152720897666L
    }
}
