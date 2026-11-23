package io.github.morningtv.weather

/** Weather theme categories corresponding to WMO weather code groups. */
enum class WeatherTheme { CLEAR, PARTLY, CLOUDY, RAIN, SNOW, STORM, FOG }

/** Maps a WMO weather code to a [WeatherTheme]. */
fun weatherTheme(code: Int): WeatherTheme = when (code) {
    0 -> WeatherTheme.CLEAR
    1, 2 -> WeatherTheme.PARTLY
    45, 48 -> WeatherTheme.FOG
    in 51..67, in 80..82 -> WeatherTheme.RAIN
    in 71..77, 85, 86 -> WeatherTheme.SNOW
    in 95..99 -> WeatherTheme.STORM
    else -> WeatherTheme.CLOUDY
}

/** 3 ARGB gradient colors for the given [theme], top-left to bottom-right. */
fun gradientColors(theme: WeatherTheme): IntArray = when (theme) {
    WeatherTheme.CLEAR -> intArrayOf(0xFF5B2BD6.toInt(), 0xFFC13A8F.toInt(), 0xFFFF7A3D.toInt())
    WeatherTheme.PARTLY -> intArrayOf(0xFF3B3FD0.toInt(), 0xFF7C6BE0.toInt(), 0xFFF2A06B.toInt())
    WeatherTheme.CLOUDY -> intArrayOf(0xFF47556F.toInt(), 0xFF69789A.toInt(), 0xFF8E9AB4.toInt())
    WeatherTheme.RAIN -> intArrayOf(0xFF1B3466.toInt(), 0xFF2F5C9E.toInt(), 0xFF4E86B8.toInt())
    WeatherTheme.SNOW -> intArrayOf(0xFF46689A.toInt(), 0xFF7B9CC4.toInt(), 0xFFA9C3E0.toInt())
    WeatherTheme.STORM -> intArrayOf(0xFF241657.toInt(), 0xFF4B2C8F.toInt(), 0xFF7A4FB0.toInt())
    WeatherTheme.FOG -> intArrayOf(0xFF5E6879.toInt(), 0xFF818B9C.toInt(), 0xFFA3ACB9.toInt())
}
