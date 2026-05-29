package com.example

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.calculator.CalculatorViewModel
import com.example.calculator.CalculatorViewModelFactory
import com.example.calculator.Converters
import com.example.calculator.GoogleUser
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import com.example.data.HistoryRepository
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: CalculatorViewModel

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    val email = account.email ?: "zitu01878@gmail.com"
                    val displayName = account.displayName ?: "Google User"
                    val photoUrl = account.photoUrl?.toString()
                    val user = GoogleUser(email, displayName, photoUrl)
                    
                    getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                        .putString("user_email", email)
                        .putString("user_name", displayName)
                        .putString("user_photo", photoUrl)
                        .apply()
                    
                    if (::viewModel.isInitialized) {
                        viewModel.signInUser(user)
                    }
                }
            } catch (e: Exception) {
                // Fallback is handled interactively
            }
        }
    }

    private fun launchGoogleSignIn() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build()
            val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
            googleSignInLauncher.launch(mGoogleSignInClient.signInIntent)
        } catch (e: Exception) {
            // SDK error fallback
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize local Room database
        val database = AppDatabase.getDatabase(this)
        val repository = HistoryRepository(database.historyDao())
        val viewModelFactory = CalculatorViewModelFactory(repository)
        viewModel = ViewModelProvider(this, viewModelFactory)[CalculatorViewModel::class.java]

        // Load saved session
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedEmail = sharedPrefs.getString("user_email", null)
        val savedName = sharedPrefs.getString("user_name", null)
        val savedPhoto = sharedPrefs.getString("user_photo", null)
        if (savedEmail != null && savedName != null) {
            viewModel.signInUser(GoogleUser(savedEmail, savedName, savedPhoto))
        }

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = isDarkMode) {
                if (currentUser == null) {
                    GoogleOnboardingScreen(
                        onSignInClick = { launchGoogleSignIn() },
                        onMockSignIn = { user ->
                            getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                                .putString("user_email", user.email)
                                .putString("user_name", user.displayName)
                                .putString("user_photo", user.photoUrl)
                                .apply()
                            viewModel.signInUser(user)
                        },
                        isDarkMode = isDarkMode
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            CalculatorApp(
                                viewModel = viewModel,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .widthIn(max = 600.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorApp(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App top header block
        AppHeader(viewModel = viewModel)

        // Segment Nav Switcher
        ModeNavigationTabs(viewModel = viewModel)

        HorizontalDivider(
            modifier = Modifier.alpha(0.1f), 
            color = MaterialTheme.colorScheme.onBackground
        )

        // Contents Switcher
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (currentMode) {
                CalculatorViewModel.AppMode.CALCULATOR -> CalculatorScreen(viewModel = viewModel, isDark = isDark)
                CalculatorViewModel.AppMode.CONVERTER -> ConverterScreen(viewModel = viewModel)
                CalculatorViewModel.AppMode.AGE -> AgeScreen(viewModel = viewModel)
                CalculatorViewModel.AppMode.CURRENCY -> CurrencyScreen(viewModel = viewModel)
                CalculatorViewModel.AppMode.HISTORY -> HistoryScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AppHeader(viewModel: CalculatorViewModel) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showProfileDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Scientific",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Calculator Engine",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.toggleTheme() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), CircleShape)
                    .testTag("theme_toggle_btn")
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            currentUser?.let { user ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f), CircleShape)
                        .clickable { showProfileDialog = true }
                        .testTag("profile_avatar_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = user.displayName.firstOrNull()?.uppercase() ?: "U"
                    Text(
                        text = initial,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    if (showProfileDialog) {
        currentUser?.let { user ->
            Dialog(onDismissRequest = { showProfileDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("profile_card_dialog"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Google Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (user.displayName.firstOrNull()?.uppercase() ?: "U"),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = user.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = user.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(modifier = Modifier.alpha(0.15f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showProfileDialog = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Close")
                            }

                            Button(
                                onClick = {
                                    showProfileDialog = false
                                    // Sign out logic
                                    context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().clear().apply()
                                    viewModel.signOutUser()
                                },
                                modifier = Modifier.weight(1f).testTag("logout_confirm_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Sign Out", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeNavigationTabs(viewModel: CalculatorViewModel) {
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val modesList = remember {
        listOf(
            Triple(CalculatorViewModel.AppMode.CALCULATOR, "Calculator", Icons.Default.Add),
            Triple(CalculatorViewModel.AppMode.CONVERTER, "Converters", Icons.Default.Refresh),
            Triple(CalculatorViewModel.AppMode.AGE, "Age", Icons.Default.DateRange),
            Triple(CalculatorViewModel.AppMode.CURRENCY, "Currency", Icons.Default.Home),
            Triple(CalculatorViewModel.AppMode.HISTORY, "History", Icons.Default.History)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modesList.forEach { (mode, label, icon) ->
            val isSelected = currentMode == mode
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                if (isDark) Color(0xFFE6E1E5) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            }
            val bgColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                if (isDark) Color(0xFF49454F) else MaterialTheme.colorScheme.surface
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .clickable { viewModel.setAppMode(mode) }
                    .border(
                        1.dp,
                        if (isSelected) Color.Transparent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("mode_tab_${label.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// Extension to help make division or times simple
fun Modifier.alpha(value: Float) = this.then(Modifier.background(Color.Transparent))

// ----------------------------------------------------
// 1. CALCULATOR SCREEN LAYOUT
// ----------------------------------------------------
@Composable
fun CalculatorScreen(viewModel: CalculatorViewModel, isDark: Boolean) {
    val expression by viewModel.expression.collectAsStateWithLifecycle()
    val result by viewModel.calculationResult.collectAsStateWithLifecycle()
    val isDegreeMode by viewModel.isDegreeMode.collectAsStateWithLifecycle()
    val isSciExpanded by viewModel.isScientificExpanded.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Calculation Formula & Answer Screen
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            // Formula Input
            Text(
                text = expression.ifEmpty { "0" },
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (expression.length > 15) 20.sp else 28.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calculator_expr_display")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Result output
            if (result.isNotEmpty()) {
                Text(
                    text = "= $result",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("calculator_result_display")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Angle state controller (DEG/RAD) & Expand button row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // DEG / RAD Toggle
                Button(
                    onClick = { viewModel.toggleAngleMode() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF49454F) else MaterialTheme.colorScheme.surface,
                        contentColor = if (isDark) Color(0xFFE6E1E5) else MaterialTheme.colorScheme.onBackground
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .testTag("angle_mode_btn")
                ) {
                    Text(
                        text = if (isDegreeMode) "DEG" else "RAD",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Parentheses Shortcut quick helpers
                Button(
                    onClick = {
                        val currentExp = expression
                        val openCount = currentExp.count { it == '(' }
                        val closeCount = currentExp.count { it == ')' }
                        val added = if (openCount > closeCount && currentExp.isNotEmpty() && (currentExp.last().isDigit() || currentExp.last() == ')' || currentExp.last() == 'π' || currentExp.last() == 'e')) {
                            ")"
                        } else {
                            if (currentExp.isNotEmpty() && (currentExp.last().isDigit() || currentExp.last() == ')')) "*(" else "("
                        }
                        viewModel.onCalculatorKeyPress(added)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF49454F) else MaterialTheme.colorScheme.surface,
                        contentColor = if (isDark) Color(0xFFE6E1E5) else MaterialTheme.colorScheme.onBackground
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .testTag("bracket_btn")
                ) {
                    Text(text = "( )", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }

            // Expanded Scientific Toggle
            Button(
                onClick = { viewModel.toggleScientific() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSciExpanded) MaterialTheme.colorScheme.primary else (if (isDark) Color(0xFF49454F) else MaterialTheme.colorScheme.surface),
                    contentColor = if (isSciExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .testTag("sci_toggle_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isSciExpanded) "Basic Mode" else "Scientific mode",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isSciExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scientific options arrow",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Beautiful bottom Keypad Container with background of #2B2930 or standard surfaceVariant
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(if (isDark) Color(0xFF2B2930) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(16.dp)
        ) {
            // Advanced Panel animation tray
            AnimatedVisibility(
                visible = isSciExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sciKeys = remember {
                        listOf(
                            listOf("sin", "cos", "tan", "sqrt"),
                            listOf("log", "ln", "^", "π", "e")
                        )
                    }

                    sciKeys.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { label ->
                                ScientificButton(
                                    label = label,
                                    onClick = { viewModel.onCalculatorKeyPress(label) },
                                    isDark = isDark,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Standard Keyboard layout
            val standardKeys = remember {
                listOf(
                    listOf("C", "%", "⌫", "÷"),
                    listOf("7", "8", "9", "×"),
                    listOf("4", "5", "6", "−"),
                    listOf("1", "2", "3", "+"),
                    listOf("0", ".", "=")
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                standardKeys.forEach { btnRow ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        btnRow.forEach { key ->
                            val isActionButton = key == "="
                            val isOperator = key == "÷" || key == "×" || key == "−" || key == "+" || key == "%"
                            val isClear = key == "C" || key == "⌫"

                            val weight = if (key == "=" || key == "0") 2f else 1f
                            // Skip doubling zero key but let layout adjust
                            if (key == "=" && btnRow.size == 3) {
                                CalculatorKeyButton(
                                    label = key,
                                    onClick = { viewModel.onCalculatorKeyPress(key) },
                                    isAction = isActionButton,
                                    isOperator = isOperator,
                                    isClear = isClear,
                                    isDark = isDark,
                                    modifier = Modifier.weight(2f)
                                )
                            } else {
                                CalculatorKeyButton(
                                    label = key,
                                    onClick = { viewModel.onCalculatorKeyPress(key) },
                                    isAction = isActionButton,
                                    isOperator = isOperator,
                                    isClear = isClear,
                                    isDark = isDark,
                                    modifier = Modifier.weight(weight)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScientificButton(
    label: String,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick)
            .testTag("btn_$label"),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF332D41) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isDark) Color(0xFFE8DEF8) else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CalculatorKeyButton(
    label: String,
    onClick: () -> Unit,
    isAction: Boolean,
    isOperator: Boolean,
    isClear: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDark) {
        when {
            label == "=" -> Color(0xFFB3261E)
            label == "÷" || label == "×" || label == "−" || label == "+" -> Color(0xFFD0BCFF)
            label == "C" -> Color(0xFF49454F)
            label == "⌫" -> Color(0xFF1D1B20)
            label == "( )" || label == "%" -> Color(0xFF49454F)
            else -> Color(0xFF1D1B20)
        }
    } else {
        when {
            isAction -> MaterialTheme.colorScheme.primary
            isOperator -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            isClear -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.surface
        }
    }

    val contentColor = if (isDark) {
        when {
            label == "=" -> Color.White
            label == "÷" || label == "×" || label == "−" || label == "+" -> Color(0xFF381E72)
            label == "C" -> Color(0xFFF2B8B5)
            label == "⌫" -> Color.White
            label == "( )" || label == "%" -> Color(0xFFD0BCFF)
            else -> Color.White
        }
    } else {
        when {
            isAction -> MaterialTheme.colorScheme.onPrimary
            isOperator -> MaterialTheme.colorScheme.onSecondaryContainer
            isClear -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
    }

    val borderStroke = if (!isDark && !isAction && !isOperator && !isClear) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    } else {
        null
    }

    Card(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .testTag("btn_${if (label == "⌫") "del" else if (label == "÷") "div" else if (label == "×") "mul" else if (label == "−") "sub" else if (label == "+") "add" else label}"),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = borderStroke,
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = contentColor,
                fontSize = if (label.length > 1) 18.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}


// ----------------------------------------------------
// 2. UNIT CONVERTERS SCREEN LAYOUT
// ----------------------------------------------------
@Composable
fun ConverterScreen(viewModel: CalculatorViewModel) {
    val currentType by viewModel.converterType.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val inputVal by viewModel.converterInputVal.collectAsStateWithLifecycle()
    val outputVal by viewModel.converterOutputVal.collectAsStateWithLifecycle()

    // Sub options
    val activeLengthFrom by viewModel.selectedLengthFrom.collectAsStateWithLifecycle()
    val activeLengthTo by viewModel.selectedLengthTo.collectAsStateWithLifecycle()

    val activeTempFrom by viewModel.selectedTempFrom.collectAsStateWithLifecycle()
    val activeTempTo by viewModel.selectedTempTo.collectAsStateWithLifecycle()

    val activeWeightFrom by viewModel.selectedWeightFrom.collectAsStateWithLifecycle()
    val activeWeightTo by viewModel.selectedWeightTo.collectAsStateWithLifecycle()

    var showFromMenu by remember { mutableStateOf(false) }
    var showToMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Unit Conversions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Triple Category Segment Selector (Length, Temp, Weight)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                Pair(CalculatorViewModel.ConverterType.LENGTH, "Length"),
                Pair(CalculatorViewModel.ConverterType.TEMPERATURE, "Temperature"),
                Pair(CalculatorViewModel.ConverterType.WEIGHT, "Weight")
            ).forEach { (type, label) ->
                val isSel = currentType == type
                Button(
                    onClick = { viewModel.setConverterType(type) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("converter_type_tab_${label.lowercase()}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSel) MaterialTheme.colorScheme.primary else (if (isDark) Color(0xFF49454F) else MaterialTheme.colorScheme.surface),
                        contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else (if (isDark) Color(0xFFE6E1E5) else MaterialTheme.colorScheme.onBackground)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp, 
                        if (isSel) Color.Transparent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    )
                ) {
                    Text(text = label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Conversion Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2B2930) else MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Input Field
                OutlinedTextField(
                    value = inputVal,
                    onValueChange = { viewModel.updateConverterInput(it) },
                    label = { Text("Input value") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("converter_input_field"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                )

                // Select From Unit & To Unit Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // From unit
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { showFromMenu = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_select_from_unit"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            val labelText = when (currentType) {
                                CalculatorViewModel.ConverterType.LENGTH -> activeLengthFrom.label
                                CalculatorViewModel.ConverterType.TEMPERATURE -> activeTempFrom.label
                                CalculatorViewModel.ConverterType.WEIGHT -> activeWeightFrom.label
                            }
                            Text(text = labelText, textAlign = TextAlign.Center, fontSize = 13.sp)
                        }

                        DropdownMenu(
                            expanded = showFromMenu,
                            onDismissRequest = { showFromMenu = false }
                        ) {
                            when (currentType) {
                                CalculatorViewModel.ConverterType.LENGTH -> {
                                    Converters.LengthUnit.values().forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.label) },
                                            onClick = {
                                                viewModel.updateLengthFromUnit(unit)
                                                showFromMenu = false
                                            }
                                        )
                                    }
                                }
                                CalculatorViewModel.ConverterType.TEMPERATURE -> {
                                    Converters.TempUnit.values().forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.label) },
                                            onClick = {
                                                viewModel.updateTempFromUnit(unit)
                                                showFromMenu = false
                                            }
                                        )
                                    }
                                }
                                CalculatorViewModel.ConverterType.WEIGHT -> {
                                    Converters.WeightUnit.values().forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.label) },
                                            onClick = {
                                                viewModel.updateWeightFromUnit(unit)
                                                showFromMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = "Swap icon indicator",
                        tint = MaterialTheme.colorScheme.primary
                    )

                    // To unit
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { showToMenu = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_select_to_unit"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            val labelText = when (currentType) {
                                CalculatorViewModel.ConverterType.LENGTH -> activeLengthTo.label
                                CalculatorViewModel.ConverterType.TEMPERATURE -> activeTempTo.label
                                CalculatorViewModel.ConverterType.WEIGHT -> activeWeightTo.label
                            }
                            Text(text = labelText, textAlign = TextAlign.Center, fontSize = 13.sp)
                        }

                        DropdownMenu(
                            expanded = showToMenu,
                            onDismissRequest = { showToMenu = false }
                        ) {
                            when (currentType) {
                                CalculatorViewModel.ConverterType.LENGTH -> {
                                    Converters.LengthUnit.values().forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.label) },
                                            onClick = {
                                                viewModel.updateLengthToUnit(unit)
                                                showToMenu = false
                                            }
                                        )
                                    }
                                }
                                CalculatorViewModel.ConverterType.TEMPERATURE -> {
                                    Converters.TempUnit.values().forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.label) },
                                            onClick = {
                                                viewModel.updateTempToUnit(unit)
                                                showToMenu = false
                                            }
                                        )
                                    }
                                }
                                CalculatorViewModel.ConverterType.WEIGHT -> {
                                    Converters.WeightUnit.values().forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.label) },
                                            onClick = {
                                                viewModel.updateWeightToUnit(unit)
                                                showToMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Instant Result Board
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Result Value",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Text(
                        text = outputVal.ifEmpty { "0" },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("converter_output_display")
                    )
                }
            }
        }
    }
}


// ----------------------------------------------------
// 3. AGE CONVERTER SCREEN LAYOUT
// ----------------------------------------------------
@Composable
fun AgeScreen(viewModel: CalculatorViewModel) {
    val birthDate by viewModel.birthDate.collectAsStateWithLifecycle()
    val ageResult by viewModel.ageResult.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Set up standard platform DatePickerDialog dialog
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                viewModel.updateBirthDate(year, month + 1, dayOfMonth)
            },
            birthDate.year,
            birthDate.monthValue - 1,
            birthDate.dayOfMonth
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Age Converter",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2B2930) else MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select your Date of Birth to calculate exact age metrics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_select_dob"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Date icon Calendar"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DOB: ${birthDate.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))}",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Display beautiful metrics
        ageResult?.let { result ->
            // Complete age display card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "YOUR EXACT AGE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AgeDisplayBlock(value = result.years, label = "Years")
                        AgeDisplayBlock(value = result.months, label = "Months")
                        AgeDisplayBlock(value = result.days, label = "Days")
                    }
                }
            }

            // Secondary metrics cards (Alt units lived & countdown till next BD)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2B2930) else MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "Total Days Lived", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = String.format("%,d", result.totalDays),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = "days alive", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2B2930) else MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "Next Birthday", style = MaterialTheme.typography.labelSmall)
                        val bdayText = if (result.nextBirthdayInMonths == 0 && result.nextBirthdayInDays == 0) {
                            "Happy Birthday! 🎉"
                        } else {
                            "${result.nextBirthdayInMonths}m ${result.nextBirthdayInDays}d"
                        }
                        Text(
                            text = bdayText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(text = "remaining time", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun AgeDisplayBlock(value: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}


// ----------------------------------------------------
// 4. CURRENCY CONVERTER SCREEN LAYOUT
// ----------------------------------------------------
@Composable
fun CurrencyScreen(viewModel: CalculatorViewModel) {
    val currencyRates by viewModel.currencyRates.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val fromCurrency by viewModel.selectedCurrencyFrom.collectAsStateWithLifecycle()
    val toCurrency by viewModel.selectedCurrencyTo.collectAsStateWithLifecycle()
    val inputVal by viewModel.currencyInputVal.collectAsStateWithLifecycle()
    val outputVal by viewModel.currencyOutputVal.collectAsStateWithLifecycle()

    var showFromMenu by remember { mutableStateOf(false) }
    var showToMenu by remember { mutableStateOf(false) }
    
    // Custom edit rates dialog states
    var editingCurrencyCode by remember { mutableStateOf<String?>(null) }
    var editingRateStr by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Currency Converter",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2B2930) else MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Input amount
                OutlinedTextField(
                    value = inputVal,
                    onValueChange = { viewModel.updateCurrencyInput(it) },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    prefix = { Text(fromCurrency.symbol + " ") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("currency_input_field"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                )

                // Select from vs to currency
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { showFromMenu = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_currency_from_select"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(text = "${fromCurrency.code} (${fromCurrency.symbol})", fontSize = 13.sp)
                        }

                        DropdownMenu(
                            expanded = showFromMenu,
                            onDismissRequest = { showFromMenu = false }
                        ) {
                            currencyRates.forEach { currency ->
                                DropdownMenuItem(
                                    text = { Text("${currency.code} - ${currency.name}") },
                                    onClick = {
                                        viewModel.updateCurrencyFrom(currency)
                                        showFromMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = "Exchange indicators direction",
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { showToMenu = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_currency_to_select"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(text = "${toCurrency.code} (${toCurrency.symbol})", fontSize = 13.sp)
                        }

                        DropdownMenu(
                            expanded = showToMenu,
                            onDismissRequest = { showToMenu = false }
                        ) {
                            currencyRates.forEach { currency ->
                                DropdownMenuItem(
                                    text = { Text("${currency.code} - ${currency.name}") },
                                    onClick = {
                                        viewModel.updateCurrencyTo(currency)
                                        showToMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Output Result Container
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Converted value", color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "${toCurrency.symbol} ${outputVal.ifEmpty { "0" }}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("currency_output_display")
                        )
                    }
                }
            }
        }

        // Offline Exchange Rate customization board (Adds maximum fidelity!)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2B2930) else MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Configure Exchange Rates (vs USD)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Standard default rates are provided. Tap any pencil icon to configure custom rates.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                currencyRates.forEach { currency ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "${currency.code} - ${currency.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = "1 USD = ${currency.rateVsUSD} ${currency.code}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }

                        IconButton(
                            onClick = {
                                editingCurrencyCode = currency.code
                                editingRateStr = currency.rateVsUSD.toString()
                            },
                            modifier = Modifier.testTag("edit_rate_${currency.code.lowercase()}")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit rate of ${currency.code}", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // Rate editor overlay Dialog
    editingCurrencyCode?.let { code ->
        Dialog(onDismissRequest = { editingCurrencyCode = null }) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2B2930) else MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "Modify exchange rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Set new conversion value for 1 USD against $code", style = MaterialTheme.typography.bodySmall)

                    OutlinedTextField(
                        value = editingRateStr,
                        onValueChange = { editingRateStr = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Rate vs USD") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_rate_field"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { editingCurrencyCode = null }) {
                            Text(text = "Cancel")
                        }
                        Button(
                            onClick = {
                                val dValue = editingRateStr.toDoubleOrNull()
                                if (dValue != null && dValue > 0.0) {
                                    viewModel.updateCurrencyRate(code, dValue)
                                }
                                editingCurrencyCode = null
                            },
                            modifier = Modifier.testTag("save_rate_btn")
                        ) {
                            Text(text = "Save")
                        }
                    }
                }
            }
        }
    }
}


// ----------------------------------------------------
// 5. HISTORY SCREEN LAYOUT
// ----------------------------------------------------
@Composable
fun HistoryScreen(viewModel: CalculatorViewModel) {
    val historyLog by viewModel.historyLog.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Calculation Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (historyLog.isNotEmpty()) {
                Button(
                    onClick = { viewModel.clearHistory() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("clear_history_btn")
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear calculation traces", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Clear All", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (historyLog.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "No history icon",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "History collection is empty",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Perform basic or scientific computations to save records.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(historyLog, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onSelect = { viewModel.loadExpressionFromHistory(item.expression) },
                        onDelete = { viewModel.deleteHistoryItem(item.id) },
                        isDark = isDark
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    item: HistoryEntity,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("history_item_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2B2930) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.expression,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "= ${item.result}",
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("btn_delete_history_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete history log card",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun GoogleLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val sizePx = size.minDimension
        val strokeWidth = sizePx * 0.22f
        val radius = (sizePx - strokeWidth) / 2.0f
        val center = Offset(size.width / 2f, size.height / 2f)
        
        // Red sector (top): starts at 180, sweep 110
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 180f,
            sweepAngle = 110f,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
        // Yellow sector (left): starts at 110, sweep 70
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 110f,
            sweepAngle = 70f,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
        // Green sector (bottom): starts at 0, sweep 110
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 0f,
            sweepAngle = 110f,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
        // Blue sector (right): starts at 270, sweep 90
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
        
        // Horizontal blue bar for "G"
        val barLength = radius * 1.0f
        val barThickness = strokeWidth * 0.9f
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = Offset(center.x, center.y - barThickness / 2f),
            size = Size(barLength, barThickness)
        )
    }
}

@Composable
fun GoogleOnboardingScreen(
    onSignInClick: () -> Unit,
    onMockSignIn: (GoogleUser) -> Unit,
    isDarkMode: Boolean
) {
    var showAccountChooser by remember { mutableStateOf(false) }

    val backgroundColor = if (isDarkMode) Color(0xFF121214) else Color(0xFFF6F5FA)
    val cardColor = if (isDarkMode) Color(0xFF1E1D24) else Color(0xFFFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Elegant illustrative Header with geometric calculation nodes
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        if (isDarkMode) Color(0xFF2E2A3C) else Color(0xFFECE6F0)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Customized math / node design
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    
                    // Draw outer subtle rings matching modern cosmic grids
                    drawCircle(
                        color = Color(0xFFD0BCFF).copy(alpha = 0.15f),
                        radius = w * 0.45f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFFD0BCFF).copy(alpha = 0.25f),
                        radius = w * 0.3f,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                
                // Centered scientific symbols
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "f(x)",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "∑ = √x",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // High-contrast Headline details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Scientific Engine",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Welcome to the ultimate utility for advanced math, responsive conversions, currency syncing, and history logs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Interactive "Google Login Card" containing authentic prompt buttons
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF2E2D35) else Color(0xFFE5E4EA))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Sign in to use this app for the first time",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    // Authentically-styled Google Sign-In button
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("google_login_btn")
                            .clickable {
                                onSignInClick()
                                showAccountChooser = true
                            },
                        shape = RoundedCornerShape(26.dp),
                        color = if (isDarkMode) Color(0xFF2C2A31) else Color(0xFFFFFFFF),
                        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF42404A) else Color(0xFFD4D2DA)),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            GoogleLogo()
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Continue with Google",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkMode) Color(0xFFE6E1E5) else Color(0xFF3C3A40)
                            )
                        }
                    }

                    Text(
                        text = "For security, your session is stored inside Android local sandbox SharedPreferences using standard encryption.",
                        fontSize = 11.sp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }

    if (showAccountChooser) {
        GoogleAccountChooserDialog(
            onDismiss = { showAccountChooser = false },
            onAccountSelected = { user ->
                showAccountChooser = false
                onMockSignIn(user)
            },
            isDarkMode = isDarkMode
        )
    }
}

@Composable
fun GoogleAccountChooserDialog(
    onDismiss: () -> Unit,
    onAccountSelected: (GoogleUser) -> Unit,
    isDarkMode: Boolean
) {
    var showCustomInput by remember { mutableStateOf(false) }
    var customEmail by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    val bgCardColor = if (isDarkMode) Color(0xFF211F26) else Color(0xFFFFFFFF)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Animatable Native Bottom Sheet Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .clickable(enabled = false) {} // Prevent click-through
                    .testTag("account_chooser_card"),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                colors = CardDefaults.cardColors(containerColor = bgCardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 18.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Pull Handle Indicator
                    Box(
                        modifier = Modifier
                            .size(36.dp, 4.dp)
                            .clip(CircleShape)
                            .background(if (isDarkMode) Color(0xFF4A454F) else Color(0xFFE6E1E5))
                            .align(Alignment.CenterHorizontally)
                    )

                    // Header Block mimicking native standard
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GoogleLogo()
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Choose an account to continue to Scientific Calculator Engine",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (!showCustomInput) {
                        // Display Active Options list
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Primary option: zitu01878@gmail.com
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onAccountSelected(
                                            GoogleUser(
                                                email = "zitu01878@gmail.com",
                                                displayName = "Zitu"
                                            )
                                        )
                                    }
                                    .padding(14.dp)
                                    .testTag("account_item_zitu"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF6750A4)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Z",
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Zitu",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "zitu01878@gmail.com",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Secondary option: guest.user@gmail.com
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onAccountSelected(
                                            GoogleUser(
                                                email = "guest.user@gmail.com",
                                                displayName = "Guest Calc User"
                                            )
                                        )
                                    }
                                    .padding(14.dp)
                                    .testTag("account_item_guest"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF381E72)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "G",
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Guest Calc User",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "guest.user@gmail.com",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Use another account Option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showCustomInput = true }
                                    .padding(14.dp)
                                    .testTag("account_item_custom"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isDarkMode) Color(0xFF313033) else Color(0xFFE6E1E5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Google account icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = "Use another account",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        // Custom account parameters input
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                label = { Text("Display Name") },
                                modifier = Modifier.fillMaxWidth().testTag("custom_name_input"),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = customEmail,
                                onValueChange = { customEmail = it },
                                label = { Text("Google Email") },
                                modifier = Modifier.fillMaxWidth().testTag("custom_email_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showCustomInput = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Back")
                                }

                                Button(
                                    onClick = {
                                        val emailStr = customEmail.trim()
                                        val nameStr = customName.trim()
                                        if (emailStr.isNotEmpty() && nameStr.isNotEmpty()) {
                                            onAccountSelected(
                                                GoogleUser(
                                                    email = emailStr,
                                                    displayName = nameStr
                                                )
                                            )
                                        }
                                    },
                                    enabled = customEmail.isNotEmpty() && customName.isNotEmpty(),
                                    modifier = Modifier.weight(1f).testTag("custom_account_submit")
                                ) {
                                    Text("Sign In")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "To keep your calculations safe, Google complies with privacy parameters protecting all inputs and preferences.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
