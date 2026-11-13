package com.github.fakegps.model

import java.io.Serializable
import java.util.Locale

/**
 * Latitude/longitude value object.
 *
 * <p><b>Persistence contract:</b> instances are written into lite-orm's
 * {@code mLocPoint} column with Java serialization. The serialized form embeds
 * the class name, {@code serialVersionUID} and the field names, so the private
 * legacy fields {@code mLatitude}/{@code mLongitude} must not be renamed. The
 * public properties keep the Java {@code getLatitude()/setLatitude()} API for
 * the remaining Java call sites.</p>
 */
class LocPoint : Serializable {

    private var mLatitude: Double
    private var mLongitude: Double

    constructor(latitude: Double, longitude: Double) {
        mLatitude = latitude
        mLongitude = longitude
    }

    constructor(locPoint: LocPoint) : this(locPoint.latitude, locPoint.longitude)

    var latitude: Double
        get() = mLatitude
        set(value) {
            mLatitude = value
        }

    var longitude: Double
        get() = mLongitude
        set(value) {
            mLongitude = value
        }

    override fun toString(): String =
        String.format(Locale.US, "(%.6f , %.6f)", mLatitude, mLongitude)

    private companion object {
        private const val serialVersionUID = -1770575152720897533L
    }
}
