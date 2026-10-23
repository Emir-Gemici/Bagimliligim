package com.bagimliligim.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.bagimliligim.app.domain.AppPlatform

@Entity(tableName = "swipe_logs", indices = [
    Index(value = ["dateString", "hourOfDay"]), Index(value = ["platform", "dateString"]),
    Index(value = ["batchId"], unique = true),
])
data class SwipeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: String,
    val platform: AppPlatform,
    val swipeCount: Int,
    val distanceMeters: Float,
    val timestamp: Long,
    val dateString: String,
    val hourOfDay: Int,
)

class PlatformConverters {
    @TypeConverter fun encode(value: AppPlatform): String = value.name
    @TypeConverter fun decode(value: String): AppPlatform = AppPlatform.valueOf(value)
}
