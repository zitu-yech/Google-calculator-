package com.example.calculator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.HistoryEntity
import com.example.data.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

data class GoogleUser(
    val email: String,
    val displayName: String,
    val photoUrl: String? = null
)

class CalculatorViewModel(private val repository: HistoryRepository) : ViewModel() {

    enum class AppMode {
        CALCULATOR, CONVERTER, AGE, CURRENCY, HISTORY
    }

    enum class ConverterType {
        LENGTH, TEMPERATURE, WEIGHT
    }

    // Google Sign-In state
    private val _currentUser = MutableStateFlow<GoogleUser?>(null)
    val currentUser: StateFlow<GoogleUser?> = _currentUser.asStateFlow()

    fun signInUser(user: GoogleUser) {
        _currentUser.value = user
    }

    fun signOutUser() {
        _currentUser.value = null
    }

    // Theme state (true = dark mode, false = light mode)
    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleTheme() {
        _isDarkMode.value = !_isDarkMode.value
    }

    // App Navigation Mode
    private val _currentMode = MutableStateFlow(AppMode.CALCULATOR)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    fun setAppMode(mode: AppMode) {
        _currentMode.value = mode
    }

    // ----------------------------------------------------
    // CALCULATOR STATE
    // ----------------------------------------------------
    private val _expression = MutableStateFlow("")
    val expression: StateFlow<String> = _expression.asStateFlow()

    private val _calculationResult = MutableStateFlow("")
    val calculationResult: StateFlow<String> = _calculationResult.asStateFlow()

    private val _isDegreeMode = MutableStateFlow(true)
    val isDegreeMode: StateFlow<Boolean> = _isDegreeMode.asStateFlow()

    // Expand state for advanced scientific drawer
    private val _isScientificExpanded = MutableStateFlow(false)
    val isScientificExpanded: StateFlow<Boolean> = _isScientificExpanded.asStateFlow()

    fun toggleScientific() {
        _isScientificExpanded.value = !_isScientificExpanded.value
    }

    fun toggleAngleMode() {
        _isDegreeMode.value = !_isDegreeMode.value
    }

    fun onCalculatorKeyPress(key: String) {
        val currentExp = _expression.value
        when (key) {
            "C" -> {
                _expression.value = ""
                _calculationResult.value = ""
            }
            "⌫" -> {
                if (currentExp.isNotEmpty()) {
                    _expression.value = currentExp.substring(0, currentExp.length - 1)
                }
            }
            "=" -> {
                if (currentExp.isNotEmpty()) {
                    evaluateExpression(currentExp)
                }
            }
            "sin", "cos", "tan", "log", "ln", "sqrt" -> {
                _expression.value = currentExp + "$key("
            }
            "π" -> {
                _expression.value = currentExp + "π"
            }
            "e" -> {
                _expression.value = currentExp + "e"
            }
            else -> {
                _expression.value = currentExp + key
            }
        }
    }

    private fun evaluateExpression(expr: String) {
        viewModelScope.launch {
            try {
                val resDouble = MathEvaluator.evaluate(expr, _isDegreeMode.value)
                
                // Format output
                val resStr = if (resDouble.isNaN()) {
                    "Error"
                } else if (resDouble.isInfinite()) {
                    "Infinity"
                } else {
                    val formatted = String.format(Locale.US, "%.8f", resDouble)
                    // Trim trailing zeros and decimal point
                    if (formatted.contains(".")) {
                        var trimmed = formatted.trimEnd('0')
                        if (trimmed.endsWith(".")) {
                            trimmed = trimmed.substring(0, trimmed.length - 1)
                        }
                        trimmed
                    } else {
                        formatted
                    }
                }
                
                _calculationResult.value = resStr
                
                // Save to Database history
                repository.insert(
                    HistoryEntity(
                        expression = expr,
                        result = resStr,
                        type = "scientific"
                    )
                )
            } catch (e: Exception) {
                _calculationResult.value = "Error"
            }
        }
    }

    fun loadExpressionFromHistory(expr: String) {
        _expression.value = expr
        _calculationResult.value = ""
        _currentMode.value = AppMode.CALCULATOR
    }

    // ----------------------------------------------------
    // MEASUREMENTS CONVERTER STATE
    // ----------------------------------------------------
    private val _converterType = MutableStateFlow(ConverterType.LENGTH)
    val converterType: StateFlow<ConverterType> = _converterType.asStateFlow()

    fun setConverterType(type: ConverterType) {
        _converterType.value = type
        resetConverterInputs()
    }

    private val _converterInputVal = MutableStateFlow("1")
    val converterInputVal: StateFlow<String> = _converterInputVal.asStateFlow()

    private val _converterOutputVal = MutableStateFlow("1")
    val converterOutputVal: StateFlow<String> = _converterOutputVal.asStateFlow()

    // Units
    private val _selectedLengthFrom = MutableStateFlow(Converters.LengthUnit.METER)
    val selectedLengthFrom: StateFlow<Converters.LengthUnit> = _selectedLengthFrom.asStateFlow()

    private val _selectedLengthTo = MutableStateFlow(Converters.LengthUnit.CENTIMETER)
    val selectedLengthTo: StateFlow<Converters.LengthUnit> = _selectedLengthTo.asStateFlow()

    private val _selectedTempFrom = MutableStateFlow(Converters.TempUnit.CELSIUS)
    val selectedTempFrom: StateFlow<Converters.TempUnit> = _selectedTempFrom.asStateFlow()

    private val _selectedTempTo = MutableStateFlow(Converters.TempUnit.FAHRENHEIT)
    val selectedTempTo: StateFlow<Converters.TempUnit> = _selectedTempTo.asStateFlow()

    private val _selectedWeightFrom = MutableStateFlow(Converters.WeightUnit.KILOGRAM)
    val selectedWeightFrom: StateFlow<Converters.WeightUnit> = _selectedWeightFrom.asStateFlow()

    private val _selectedWeightTo = MutableStateFlow(Converters.WeightUnit.POUND)
    val selectedWeightTo: StateFlow<Converters.WeightUnit> = _selectedWeightTo.asStateFlow()

    fun updateConverterInput(newVal: String) {
        if (newVal.all { it.isDigit() || it == '.' || it == '-' }) {
            // Protect against multiple decimals
            if (newVal.count { it == '.' } <= 1) {
                _converterInputVal.value = newVal
                performConversion()
            }
        }
    }

    fun updateLengthFromUnit(unit: Converters.LengthUnit) {
        _selectedLengthFrom.value = unit
        performConversion()
    }

    fun updateLengthToUnit(unit: Converters.LengthUnit) {
        _selectedLengthTo.value = unit
        performConversion()
    }

    fun updateTempFromUnit(unit: Converters.TempUnit) {
        _selectedTempFrom.value = unit
        performConversion()
    }

    fun updateTempToUnit(unit: Converters.TempUnit) {
        _selectedTempTo.value = unit
        performConversion()
    }

    fun updateWeightFromUnit(unit: Converters.WeightUnit) {
        _selectedWeightFrom.value = unit
        performConversion()
    }

    fun updateWeightToUnit(unit: Converters.WeightUnit) {
        _selectedWeightTo.value = unit
        performConversion()
    }

    private fun performConversion() {
        val input = _converterInputVal.value.toDoubleOrNull() ?: 0.0
        val result: Double = when (_converterType.value) {
            ConverterType.LENGTH -> {
                Converters.convertLength(input, _selectedLengthFrom.value, _selectedLengthTo.value)
            }
            ConverterType.TEMPERATURE -> {
                Converters.convertTemp(input, _selectedTempFrom.value, _selectedTempTo.value)
            }
            ConverterType.WEIGHT -> {
                Converters.convertWeight(input, _selectedWeightFrom.value, _selectedWeightTo.value)
            }
        }
        _converterOutputVal.value = String.format(Locale.US, "%.6f", result).trimEnd('0').trimEnd('.')
    }

    private fun resetConverterInputs() {
        _converterInputVal.value = "1"
        performConversion()
    }


    // ----------------------------------------------------
    // AGE CONVERTER STATE
    // ----------------------------------------------------
    private val _birthDate = MutableStateFlow(LocalDate.now().minusYears(25))
    val birthDate: StateFlow<LocalDate> = _birthDate.asStateFlow()

    private val _ageResult = MutableStateFlow<Converters.AgeResult?>(null)
    val ageResult: StateFlow<Converters.AgeResult?> = _ageResult.asStateFlow()

    init {
        calculateAge()
    }

    fun updateBirthDate(year: Int, month: Int, day: Int) {
        try {
            _birthDate.value = LocalDate.of(year, month, day)
            calculateAge()
        } catch (_: Exception) {}
    }

    fun calculateAge() {
        val dob = _birthDate.value
        val result = Converters.calculateAge(dob.year, dob.monthValue, dob.dayOfMonth)
        _ageResult.value = result
    }


    // ----------------------------------------------------
    // CURRENCY CONVERTER STATE
    // ----------------------------------------------------
    private val _currencyRates = MutableStateFlow(Converters.defaultCurrencies)
    val currencyRates: StateFlow<List<Converters.Currency>> = _currencyRates.asStateFlow()

    private val _selectedCurrencyFrom = MutableStateFlow(Converters.defaultCurrencies[0]) // USD
    val selectedCurrencyFrom: StateFlow<Converters.Currency> = _selectedCurrencyFrom.asStateFlow()

    private val _selectedCurrencyTo = MutableStateFlow(Converters.defaultCurrencies[1]) // EUR
    val selectedCurrencyTo: StateFlow<Converters.Currency> = _selectedCurrencyTo.asStateFlow()

    private val _currencyInputVal = MutableStateFlow("100")
    val currencyInputVal: StateFlow<String> = _currencyInputVal.asStateFlow()

    private val _currencyOutputVal = MutableStateFlow("")
    val currencyOutputVal: StateFlow<String> = _currencyOutputVal.asStateFlow()

    init {
        performCurrencyConversion()
    }

    fun updateCurrencyInput(value: String) {
        if (value.all { it.isDigit() || it == '.' }) {
            if (value.count { it == '.' } <= 1) {
                _currencyInputVal.value = value
                performCurrencyConversion()
            }
        }
    }

    fun updateCurrencyFrom(currency: Converters.Currency) {
        _selectedCurrencyFrom.value = currency
        performCurrencyConversion()
    }

    fun updateCurrencyTo(currency: Converters.Currency) {
        _selectedCurrencyTo.value = currency
        performCurrencyConversion()
    }

    fun updateCurrencyRate(code: String, newRate: Double) {
        val updatedList = _currencyRates.value.map {
            if (it.code == code) it.copy(rateVsUSD = newRate) else it
        }
        _currencyRates.value = updatedList
        
        // Refresh selected rate references
        _selectedCurrencyFrom.value = updatedList.first { it.code == _selectedCurrencyFrom.value.code }
        _selectedCurrencyTo.value = updatedList.first { it.code == _selectedCurrencyTo.value.code }
        
        performCurrencyConversion()
    }

    private fun performCurrencyConversion() {
        val input = _currencyInputVal.value.toDoubleOrNull() ?: 0.0
        val from = _selectedCurrencyFrom.value
        val to = _selectedCurrencyTo.value
        val converted = Converters.convertCurrency(input, from.rateVsUSD, to.rateVsUSD)
        _currencyOutputVal.value = String.format(Locale.US, "%.4f", converted).trimEnd('0').trimEnd('.')
    }


    // ----------------------------------------------------
    // HISTORY SYSTEM
    // ----------------------------------------------------
    val historyLog: StateFlow<List<HistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun clearHistory() {
        viewModelScope.launch {
            repository.clear()
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }
}

class CalculatorViewModelFactory(private val repository: HistoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalculatorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalculatorViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
