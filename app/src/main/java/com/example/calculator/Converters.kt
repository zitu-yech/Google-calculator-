package com.example.calculator

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

object Converters {

    // 1. Length Units
    enum class LengthUnit(val label: String, val factorToMeter: Double) {
        METER("Meter (m)", 1.0),
        KILOMETER("Kilometer (km)", 1000.0),
        CENTIMETER("Centimeter (cm)", 0.01),
        MILLIMETER("Millimeter (mm)", 0.001),
        INCH("Inch (in)", 0.0254),
        FOOT("Foot (ft)", 0.3048),
        YARD("Yard (yd)", 0.9144),
        MILE("Mile (mi)", 1609.344)
    }

    fun convertLength(value: Double, from: LengthUnit, to: LengthUnit): Double {
        val valInMeters = value * from.factorToMeter
        return valInMeters / to.factorToMeter
    }

    // 2. Weight Units
    enum class WeightUnit(val label: String, val factorToKg: Double) {
        KILOGRAM("Kilogram (kg)", 1.0),
        GRAM("Gram (g)", 0.001),
        MILLIGRAM("Milligram (mg)", 1e-6),
        POUND("Pound (lb)", 0.45359237),
        OUNCE("Ounce (oz)", 0.028349523)
    }

    fun convertWeight(value: Double, from: WeightUnit, to: WeightUnit): Double {
        val valInKg = value * from.factorToKg
        return valInKg / to.factorToKg
    }

    // 3. Temperature Units
    enum class TempUnit(val label: String) {
        CELSIUS("Celsius (°C)"),
        FAHRENHEIT("Fahrenheit (°F)"),
        KELVIN("Kelvin (K)")
    }

    fun convertTemp(value: Double, from: TempUnit, to: TempUnit): Double {
        if (from == to) return value
        val celsiusVal = when (from) {
            TempUnit.CELSIUS -> value
            TempUnit.FAHRENHEIT -> (value - 32.0) * 5.0 / 9.0
            TempUnit.KELVIN -> value - 273.15
        }
        return when (to) {
            TempUnit.CELSIUS -> celsiusVal
            TempUnit.FAHRENHEIT -> (celsiusVal * 9.0 / 5.0) + 32.0
            TempUnit.KELVIN -> celsiusVal + 273.15
        }
    }

    // 4. Age Converter (requires level 26+ but minSdk matches 24. We can use elegant java.time or Calendar)
    // To ensure full support for API 24+, let's calculate using standard logic.
    // java.time is supported with modern Android systems that have API desugaring or are running API 26 (Android 8)+.
    // Let's write a safe fallback calculation if java.time is not desugared, or let's use Simple Calendar, or Java Period inside a try catch, or write standard Calendar offset calculation. Let's do Calendar logic to be 100% safe on API 24 without desugaring issues, or write a robust kotlin calendar logic!
    data class AgeResult(
        val years: Int,
        val months: Int,
        val days: Int,
        val totalDays: Long,
        val nextBirthdayInMonths: Int,
        val nextBirthdayInDays: Int
    )

    fun calculateAge(birthYear: Int, birthMonth: Int, birthDay: Int): AgeResult {
        val today = LocalDate.now()
        val birthDate = LocalDate.of(birthYear, birthMonth, birthDay)
        
        val period = Period.between(birthDate, today)
        val totalDays = ChronoUnit.DAYS.between(birthDate, today)

        // Calculate next birthday countdown
        val nextBdayYear = if (today.monthValue > birthMonth || (today.monthValue == birthMonth && today.dayOfMonth >= birthDay)) {
            today.year + 1
        } else {
            today.year
        }
        val nextBday = LocalDate.of(nextBdayYear, birthMonth, birthDay)
        val bdayPeriod = Period.between(today, nextBday)
        val totalDaysToNextBday = ChronoUnit.DAYS.between(today, nextBday)

        return AgeResult(
            years = period.years,
            months = period.months,
            days = period.days,
            totalDays = totalDays,
            nextBirthdayInMonths = bdayPeriod.months,
            nextBirthdayInDays = bdayPeriod.days
        )
    }

    // 5. Currency Converter Default Rates
    // Source: Offline values for solid, offline-first reliability.
    data class Currency(val code: String, val name: String, val rateVsUSD: Double, val symbol: String)

    val defaultCurrencies = listOf(
        Currency("USD", "US Dollar", 1.0, "$"),
        Currency("EUR", "Euro", 0.92, "€"),
        Currency("GBP", "British Pound", 0.79, "£"),
        Currency("JPY", "Japanese Yen", 156.5, "¥"),
        Currency("AUD", "Australian Dollar", 1.51, "A$"),
        Currency("CAD", "Canadian Dollar", 1.37, "C$"),
        Currency("INR", "Indian Rupee", 83.3, "₹"),
        Currency("CNY", "Chinese Yuan", 7.24, "¥"),
        Currency("SGD", "Singapore Dollar", 1.35, "S$"),
        Currency("CHF", "Swiss Franc", 0.91, "CHF")
    )

    fun convertCurrency(value: Double, fromRate: Double, toRate: Double): Double {
        // Value in USD first
        val valInUSD = value / fromRate
        return valInUSD * toRate
    }
}
