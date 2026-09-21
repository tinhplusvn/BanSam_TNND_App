package vn.bansam.tnnd.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "children")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dob: String = "",
    val gender: String = "",
    val address: String = "",
    val className: String = "",
    val father: String = "",
    val mother: String = "",
    val phone: String = ""
)

fun ageOnDate(iso: String): Int? {
    if (iso.isBlank()) return null
    return try {
        val d = java.time.LocalDate.parse(iso)
        val today = java.time.LocalDate.now()
        java.time.Period.between(d, today).years.coerceAtLeast(0)
    } catch (_: Exception) { null }
}

fun groupForAge(age: Int?): String = when (age) {
    null -> "Chưa xác định"
    in 3..5 -> "Mầm non"
    in 6..10 -> "Tiểu học"
    in 11..15 -> "THCS"
    else -> if (age < 3) "Dưới 3 tuổi" else "Trên 15 tuổi"
}
