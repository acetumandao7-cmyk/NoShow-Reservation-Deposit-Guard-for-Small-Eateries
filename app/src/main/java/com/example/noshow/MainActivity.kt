package com.example.noshow

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.noshow.data.AppDatabase
import com.example.noshow.data.Deposit
import com.example.noshow.data.DepositSettings
import com.example.noshow.data.Reservation
import com.example.noshow.data.ReservationSettings
import com.example.noshow.data.RolePermission
import com.example.noshow.data.User
import com.example.noshow.ui.theme.NoShowTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            NoShowTheme {
                NoShowApp()
            }
        }
    }
}


suspend fun ensurePermissionDefaults(database: AppDatabase) {
    val permissions = listOf(
        "USER_MANAGEMENT",
        "RESERVATION_MANAGEMENT",
        "DEPOSIT_MANAGEMENT",
        "SETTINGS_ACCESS"
    )
    val roles = listOf("Customer", "Staff", "Admin")
    val missingDefaults = mutableListOf<RolePermission>()

    roles.forEach { role ->
        val existing = database.rolePermissionDao()
            .getPermissionsForRole(role)
            .map { it.permission }
            .toSet()

        permissions.forEach { permission ->
            if (permission !in existing) {
                missingDefaults.add(
                    RolePermission(
                        role = role,
                        permission = permission,
                        enabled = when (role) {
                            "Admin" -> true
                            "Staff" -> permission == "RESERVATION_MANAGEMENT" ||
                                    permission == "DEPOSIT_MANAGEMENT"
                            else -> false
                        }
                    )
                )
            }
        }
    }

    if (missingDefaults.isNotEmpty()) {
        database.rolePermissionDao().insertAll(missingDefaults)
    }
}

suspend fun ensureDepositSettings(database: AppDatabase): DepositSettings {
    return database.depositSettingsDao().getSettings() ?: DepositSettings(
        id = 1,
        depositRequired = false,
        defaultDepositAmount = 500.0,
        minimumDeposit = 100.0,
        updatedAt = ""
    ).also {
        database.depositSettingsDao().saveSettings(it)
    }
}


suspend fun ensureReservationSettings(database: AppDatabase): ReservationSettings {
    val existing = database.reservationSettingsDao().getSettings()
    if (existing == null) {
        return ReservationSettings(
            id = 1,
            maxPartySize = 50,
            openingTime = "10:00 AM",
            closingTime = "10:00 PM",
            minimumAdvanceMinutes = 30,
            updatedAt = ""
        ).also { database.reservationSettingsDao().saveSettings(it) }
    }

    val normalizedOpening = normalizeTimeValue(existing.openingTime)
    val normalizedClosing = normalizeTimeValue(existing.closingTime)

    if (normalizedOpening != existing.openingTime || normalizedClosing != existing.closingTime) {
        val normalized = existing.copy(
            openingTime = normalizedOpening ?: existing.openingTime,
            closingTime = normalizedClosing ?: existing.closingTime
        )
        database.reservationSettingsDao().saveSettings(normalized)
        return normalized
    }

    return existing
}

fun timeToMinutes(value: String): Int? {
    return try {
        val parsed = SimpleDateFormat("h:mm a", Locale.getDefault()).parse(value) ?: return null
        val calendar = Calendar.getInstance().apply { time = parsed }
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    } catch (_: Exception) { null }
}

fun normalizeTimeValue(value: String): String? {
    return try {
        SimpleDateFormat("h:mm a", Locale.getDefault()).parse(value)?.let {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
        }
    } catch (_: Exception) {
        try {
            SimpleDateFormat("HH:mm", Locale.getDefault()).parse(value)?.let {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
            }
        } catch (_: Exception) {
            null
        }
    }
}

fun parseTimeForPicker(value: String): Pair<Int, Int> {
    val normalized = normalizeTimeValue(value) ?: "10:00 AM"
    val parsed = SimpleDateFormat("h:mm a", Locale.getDefault()).parse(normalized)
    val calendar = Calendar.getInstance()
    if (parsed != null) calendar.time = parsed
    return calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
}

fun formatPickerTime(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
}


@Composable
fun NoShowApp() {

    var currentScreen by remember {
        mutableStateOf("login")
    }

    var loggedInUser by remember {
        mutableStateOf<User?>(null)
    }

    var selectedReservationId by remember {
        mutableStateOf(0)
    }

    NoShowBackground {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                (fadeIn() + slideInHorizontally(initialOffsetX = { it / 12 })) togetherWith
                        (fadeOut() + slideOutHorizontally(targetOffsetX = { -it / 12 }))
            },
            label = "screenTransition"
        ) { screen ->
            when (screen) {

        "login" -> {

            LoginScreen(
                onRegisterClick = {
                    currentScreen = "register"
                },
                onLoginSuccess = { user ->
                    loggedInUser = user
                    currentScreen = when (user.role) {
                        "Admin" -> "adminHome"
                        "Staff" -> "staffHome"
                        else -> "customerHome"
                    }
                }
            )
        }

        "register" -> {

            RegisterScreen(
                onLoginClick = {
                    currentScreen = "login"
                }
            )
        }

        "customerHome" -> {

            loggedInUser?.let { user ->

                CustomerHomeScreen(
                    user = user,

                    onCreateReservation = {
                        currentScreen = "createReservation"
                    },

                    onMyReservations = {
                        currentScreen = "myReservations"
                    },

                    onProfile = {
                        currentScreen = "customerProfile"
                    },

                    onLogout = {
                        loggedInUser = null
                        currentScreen = "login"
                    }
                )
            }
        }

        "createReservation" -> {

            loggedInUser?.let { user ->

                CreateReservationScreen(
                    user = user,

                    onBack = {
                        currentScreen = "customerHome"
                    },

                    onReservationCreated = {
                        currentScreen = "customerHome"
                    }
                )
            }
        }

        "myReservations" -> {

            loggedInUser?.let { user ->

                MyReservationsScreen(
                    user = user,

                    onBack = {
                        currentScreen = "customerHome"
                    },

                    onReservationClick = { reservationId ->

                        selectedReservationId = reservationId

                        currentScreen = "reservationDetails"
                    }
                )
            }
        }

        "reservationDetails" -> {

            loggedInUser?.let {

                ReservationDetailsScreen(
                    reservationId = selectedReservationId,

                    onBack = {
                        currentScreen = "myReservations"
                    }
                )
            }
        }

        "customerProfile" -> {

            loggedInUser?.let { user ->

                CustomerProfileScreen(
                    user = user,
                    onBack = {
                        currentScreen = "customerHome"
                    },
                    onProfileUpdated = { updatedUser ->
                        loggedInUser = updatedUser
                    }
                )
            }
        }

        "adminHome" -> {

            loggedInUser?.let { user ->

                AdminHomeScreen(
                    user = user,
                    onUsers = {
                        currentScreen = "adminUsers"
                    },
                    onReservations = {
                        currentScreen = "adminReservations"
                    },
                    onDeposits = {
                        currentScreen = "adminDeposits"
                    },
                    onSettings = {
                        currentScreen = "adminSettings"
                    },
                    onLogout = {
                        loggedInUser = null
                        currentScreen = "login"
                    }
                )
            }
        }

        "adminUsers" -> {

            AdminUsersScreen(
                onBack = {
                    currentScreen = "adminHome"
                }
            )
        }

        "adminReservations" -> {

            StaffReservationsScreen(
                onBack = {
                    currentScreen = "adminHome"
                },
                onReservationClick = { reservationId ->
                    selectedReservationId = reservationId
                    currentScreen = "adminReservationDetails"
                }
            )
        }

        "adminReservationDetails" -> {

            StaffReservationDetailsScreen(
                reservationId = selectedReservationId,
                onBack = {
                    currentScreen = "adminReservations"
                }
            )
        }

        "adminDeposits" -> {

            StaffDepositsScreen(
                onBack = {
                    currentScreen = "adminHome"
                }
            )
        }

        "adminSettings" -> {

            AdminSystemSettingsScreen(
                onBack = {
                    currentScreen = "adminHome"
                }
            )
        }

        "staffHome" -> {

            loggedInUser?.let { user ->

                StaffHomeScreen(
                    user = user,
                    onProfile = {
                        currentScreen = "staffProfile"
                    },
                    onManageReservations = {
                        currentScreen = "staffReservations"
                    },
                    onManageDeposits = {
                        currentScreen = "staffDeposits"
                    },
                    onManageUsers = {
                        currentScreen = "staffUsers"
                    },
                    onSettings = {
                        currentScreen = "staffSettings"
                    },
                    onLogout = {
                        loggedInUser = null
                        currentScreen = "login"
                    }
                )
            }
        }

        "staffUsers" -> {

            AdminUsersScreen(
                onBack = {
                    currentScreen = "staffHome"
                }
            )
        }

        "staffSettings" -> {

            AdminSystemSettingsScreen(
                onBack = {
                    currentScreen = "staffHome"
                }
            )
        }

        "staffProfile" -> {

            loggedInUser?.let { user ->

                StaffProfileScreen(
                    user = user,
                    onBack = {
                        currentScreen = "staffHome"
                    }
                )
            }
        }

        "staffReservations" -> {

            StaffReservationsScreen(
                onBack = {
                    currentScreen = "staffHome"
                },
                onReservationClick = { reservationId ->
                    selectedReservationId = reservationId
                    currentScreen = "staffReservationDetails"
                }
            )
        }

        "staffReservationDetails" -> {

            StaffReservationDetailsScreen(
                reservationId = selectedReservationId,
                onBack = {
                    currentScreen = "staffReservations"
                }
            )
        }

        "staffDeposits" -> {

            StaffDepositsScreen(
                onBack = {
                    currentScreen = "staffHome"
                }
            )
        }
            }
        }
    }
}


@Composable
fun NoShowBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(NoShowMainBackground)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(bottomStart = 72.dp, bottomEnd = 72.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(NoShowNavy, NoShowSecondary, NoShowNavy)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.TopEnd)
                .background(NoShowSky.copy(alpha = 0.12f), RoundedCornerShape(90.dp))
        )
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

val NoShowNavy = Color(0xFF0F172A)
val NoShowSecondary = Color(0xFF1E3A5F)
val NoShowMainBg = Color(0xFFE8F1F8)
val NoShowSky = Color(0xFF38BDF8)
val NoShowDarkText = Color(0xFF0F172A)
val NoShowMutedText = Color(0xFF475569)
val NoShowHeaderText = Color.White
val NoShowHeaderMuted = Color(0xFFE2E8F0)
val NoShowMainBackground = Brush.verticalGradient(
    colors = listOf(NoShowMainBg, NoShowMainBg, Color.White)
)


@Composable
fun LoginScreen(
    onRegisterClick: () -> Unit,
    onLoginSuccess: (User) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var loginMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "NoShow",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Reservation Deposit Guard",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "for Small Eateries",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(50.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Welcome back",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Log in to manage your reservations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = email,
                        onValueChange = {
                            email = it
                            emailError = ""
                            loginMessage = ""
                        },
                        label = { Text("Email") },
                        isError = emailError.isNotEmpty(),
                        supportingText = {
                            if (emailError.isNotEmpty()) Text(emailError)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = ""
                            loginMessage = ""
                        },
                        label = { Text("Password") },
                        isError = passwordError.isNotEmpty(),
                        supportingText = {
                            if (passwordError.isNotEmpty()) Text(passwordError)
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Hide" else "Show")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        onClick = {
                            emailError = ""
                            passwordError = ""
                            loginMessage = ""
                            if (email.isBlank()) emailError = "Please enter your email."
                            if (password.isBlank()) passwordError = "Please enter your password."
                            if (email.isNotBlank() && password.isNotBlank()) {
                                val cleanEmail = email.trim().lowercase()
                                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                                    emailError = "Please enter a valid email address."
                                    return@Button
                                }
                                scope.launch {
                                    val user = when {
                                        cleanEmail == "admin@noshow.com" && password == "admin123" -> {
                                            User(-2, "Administrator", cleanEmail, hashPassword(password), "Admin")
                                        }
                                        cleanEmail == "staff@noshow.com" && password == "staff123" -> {
                                            User(-1, "Staff", cleanEmail, hashPassword(password), "Staff")
                                        }
                                        else -> {
                                            database.userDao().login(cleanEmail, hashPassword(password))
                                        }
                                    }
                                    if (user != null) {
                                        loginMessage = "Login successful."
                                        Toast.makeText(context, "Logged in successfully.", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess(user)
                                    } else {
                                        loginMessage = "Invalid email or password."
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Log In")
                    }

                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRegisterClick
                    ) {
                        Text("Don't have an account? Register")
                    }

                    if (loginMessage.isNotEmpty()) {
                        Text(
                            text = loginMessage,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun RegisterScreen(
    onLoginClick: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var registrationMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("NoShow", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text("Create your account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Get started", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Register as a customer to manage your reservations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    TextField(
                        modifier = Modifier.fillMaxWidth(), value = fullName,
                        onValueChange = { fullName = it; registrationMessage = "" },
                        label = { Text("Full Name") }, singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        modifier = Modifier.fillMaxWidth(), value = email,
                        onValueChange = { email = it; registrationMessage = "" },
                        label = { Text("Email") }, singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        modifier = Modifier.fillMaxWidth(), value = password,
                        onValueChange = { password = it; registrationMessage = "" },
                        label = { Text("Password") }, singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) "Hide" else "Show") } },
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        modifier = Modifier.fillMaxWidth(), value = confirmPassword,
                        onValueChange = { confirmPassword = it; registrationMessage = "" },
                        label = { Text("Confirm Password") }, singleLine = true,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) { Text(if (confirmPasswordVisible) "Hide" else "Show") } },
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        onClick = {
                            when {
                                fullName.isBlank() -> registrationMessage = "Please enter your full name."
                                fullName.trim().length < 2 -> registrationMessage = "Full name must be at least 2 characters."
                                email.isBlank() -> registrationMessage = "Please enter your email."
                                !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> registrationMessage = "Please enter a valid email address."
                                password.isBlank() -> registrationMessage = "Please enter a password."
                                password.length < 6 -> registrationMessage = "Password must be at least 6 characters."
                                confirmPassword.isBlank() -> registrationMessage = "Please confirm your password."
                                password != confirmPassword -> registrationMessage = "Passwords do not match."
                                else -> {
                                    val cleanEmail = email.trim().lowercase()
                                    scope.launch {
                                        val existingUser = database.userDao().getUserByEmail(cleanEmail)
                                        if (existingUser != null) {
                                            registrationMessage = "An account with this email already exists."
                                        } else {
                                            database.userDao().insertUser(
                                                User(fullName = fullName.trim(), email = cleanEmail, password = hashPassword(password), role = "Customer")
                                            )
                                            registrationMessage = "Registration successful."
                                            Toast.makeText(context, "Registration successful.", Toast.LENGTH_SHORT).show()
                                            fullName = ""
                                            email = ""
                                            password = ""
                                            confirmPassword = ""
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Create Account")
                    }
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = onLoginClick) {
                        Text("Already have an account? Log In")
                    }
                    if (registrationMessage.isNotEmpty()) {
                        Text(registrationMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerHomeScreen(
    user: User,
    onCreateReservation: () -> Unit,
    onMyReservations: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("NoShow", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text("Customer Dashboard", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Welcome, ${user.fullName}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Manage your reservations and deposits in one place.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), onClick = onCreateReservation) { Text("Create Reservation") }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), onClick = onMyReservations) { Text("My Reservations") }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), onClick = onProfile) { Text("My Profile") }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                onClick = onLogout
            ) {
                Text("Log Out", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun CreateReservationScreen(
    user: User,
    onBack: () -> Unit,
    onReservationCreated: () -> Unit
) {
    var reservationDate by remember { mutableStateOf("") }
    var reservationTime by remember { mutableStateOf("") }
    var partySize by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var reservationMessage by remember { mutableStateOf("") }
    var reservationSettings by remember { mutableStateOf<ReservationSettings?>(null) }

    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val calendar = remember { Calendar.getInstance() }
    val dateFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    LaunchedEffect(Unit) { reservationSettings = ensureReservationSettings(database) }

    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Create Reservation", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Set your visit details below", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Schedule", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = reservationDate,
                                onValueChange = {},
                                label = { Text("Reservation Date") },
                                placeholder = { Text("Select a date") },
                                readOnly = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                            Spacer(
                                modifier = Modifier.matchParentSize().clickable {
                                    val datePicker = DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            calendar.set(year, month, dayOfMonth)
                                            reservationDate = dateFormatter.format(calendar.time)
                                            reservationMessage = ""
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    )
                                    datePicker.datePicker.minDate = System.currentTimeMillis()
                                    datePicker.show()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = reservationTime,
                                onValueChange = {},
                                label = { Text("Reservation Time") },
                                placeholder = { Text("Select a time") },
                                readOnly = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                            Spacer(
                                modifier = Modifier.matchParentSize().clickable {
                                    val timePicker = TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            calendar.set(Calendar.HOUR_OF_DAY, hour)
                                            calendar.set(Calendar.MINUTE, minute)
                                            reservationTime = timeFormatter.format(calendar.time)
                                            reservationMessage = ""
                                        },
                                        calendar.get(Calendar.HOUR_OF_DAY),
                                        calendar.get(Calendar.MINUTE),
                                        false
                                    )
                                    timePicker.show()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Reservation Details", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))

                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = partySize,
                            onValueChange = {
                                partySize = it.filter { character -> character.isDigit() }
                                reservationMessage = ""
                            },
                            label = { Text("Party Size") },
                            placeholder = { Text("Number of guests") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = details,
                            onValueChange = {
                                details = it.take(200)
                                reservationMessage = ""
                            },
                            label = { Text("Additional Details") },
                            placeholder = { Text("Optional notes for the reservation") },
                            minLines = 3,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${details.length}/200 characters", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (reservationMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = reservationMessage,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        when {
                            reservationDate.isBlank() -> reservationMessage = "Please select a reservation date."
                            reservationTime.isBlank() -> reservationMessage = "Please select a reservation time."
                            partySize.isBlank() -> reservationMessage = "Please enter the party size."
                            partySize.toIntOrNull() == null -> reservationMessage = "Party size must be a valid number."
                            partySize.toInt() <= 0 -> reservationMessage = "Party size must be greater than 0."
                            reservationSettings == null -> reservationMessage = "Reservation settings are still loading. Please try again."
                            partySize.toInt() > reservationSettings!!.maxPartySize -> reservationMessage = "Party size cannot exceed ${reservationSettings!!.maxPartySize} guests."
                            timeToMinutes(reservationTime) == null -> reservationMessage = "Reservation time is invalid."
                            timeToMinutes(reservationTime)!! < timeToMinutes(reservationSettings!!.openingTime)!! || timeToMinutes(reservationTime)!! > timeToMinutes(reservationSettings!!.closingTime)!! -> reservationMessage = "Reservations are available from ${reservationSettings!!.openingTime} to ${reservationSettings!!.closingTime}."
                            details.length > 200 -> reservationMessage = "Additional details cannot exceed 200 characters."
                            else -> {
                                scope.launch {
                                    val existingReservation = database.reservationDao().getActiveReservation(
                                        user.id,
                                        reservationDate,
                                        reservationTime
                                    )
                                    if (existingReservation != null) {
                                        reservationMessage = "You already have an active reservation for this date and time."
                                    } else {
                                        val depositSettings = ensureDepositSettings(database)
                                        val reservation = Reservation(
                                            userId = user.id,
                                            reservationDate = reservationDate,
                                            reservationTime = reservationTime,
                                            partySize = partySize.toInt(),
                                            details = details.trim(),
                                            status = "Pending"
                                        )

                                        database.reservationDao().insertReservation(reservation)

                                        val createdReservation = database.reservationDao().getActiveReservation(
                                            user.id,
                                            reservationDate,
                                            reservationTime
                                        )

                                        if (depositSettings.depositRequired && createdReservation != null) {
                                            val recordedAt = SimpleDateFormat(
                                                "MMMM d, yyyy h:mm a",
                                                Locale.getDefault()
                                            ).format(Calendar.getInstance().time)

                                            database.depositDao().insertDeposit(
                                                Deposit(
                                                    reservationId = createdReservation.id,
                                                    amount = depositSettings.defaultDepositAmount,
                                                    status = "Pending",
                                                    recordedAt = recordedAt
                                                )
                                            )
                                        }

                                        Toast.makeText(context, "Reservation created successfully.", Toast.LENGTH_SHORT).show()
                                        onReservationCreated()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Create Reservation")
                }

                Spacer(modifier = Modifier.height(20.dp))
                 Button(
                     onClick = onBack,
                     modifier = Modifier.fillMaxWidth().height(52.dp),
                     shape = RoundedCornerShape(16.dp)
                 ) {
                     Text("Back to Home")
                 }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun MyReservationsScreen(
    user: User,
    onBack: () -> Unit,
    onReservationClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var reservations by remember { mutableStateOf<List<Reservation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedStatus by remember { mutableStateOf("All") }

    suspend fun refreshReservations() {
        reservations = database.reservationDao().getReservationsByUser(user.id)
        isLoading = false
    }

    LaunchedEffect(user.id) { refreshReservations() }

    val lifecycleOwner = context as ComponentActivity
    DisposableEffect(lifecycleOwner, user.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { refreshReservations() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredReservations = if (selectedStatus == "All") reservations else reservations.filter { it.status == selectedStatus }
    val statuses = listOf("All", "Pending", "Confirmed", "Cancelled", "No-Show")

    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("My Reservations", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("View and track your reservation records", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(18.dp))

                Text("Reservation Status", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(10.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    statuses.chunked(2).forEachIndexed { index, rowStatuses ->
                         if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
                            rowStatuses.forEach { status ->
                                Button(
                                    modifier = Modifier.weight(1f).padding(end = if (status == rowStatuses.first()) 10.dp else 0.dp, start = if (status == rowStatuses.last() && rowStatuses.size == 2) 10.dp else 0.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    onClick = { selectedStatus = status }
                                ) {
                                    Text(if (selectedStatus == status) "✓ $status" else status)
                                }
                            }
                            if (rowStatuses.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoading) {
                    Text("Loading reservations...")
                } else if (reservations.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("No reservations yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Create a reservation to see it here.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (filteredReservations.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Text("No reservations match the $selectedStatus filter.", modifier = Modifier.padding(18.dp))
                    }
                } else {
                    filteredReservations.forEach { reservation ->
                        var depositStatus by remember(reservation.id) { mutableStateOf("Not recorded") }
                        LaunchedEffect(reservation.id) {
                            val currentDeposit = database.depositDao().getDepositByReservationId(reservation.id)
                            depositStatus = currentDeposit?.status ?: "Not recorded"
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onReservationClick(reservation.id) },
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Reservation #${reservation.id}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(reservation.reservationDate, style = MaterialTheme.typography.bodyLarge)
                                        Text(reservation.reservationTime, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    StatusBadge(reservation.status)
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("Party Size: ${reservation.partySize}", style = MaterialTheme.typography.bodyMedium)
                                if (reservation.details.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(reservation.details, style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Deposit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                StatusBadge(depositStatus)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Button(
                     modifier = Modifier.fillMaxWidth().height(50.dp),
                     shape = RoundedCornerShape(16.dp),
                     enabled = !isLoading,
                     onClick = {
                         scope.launch {
                             isLoading = true
                             val refreshedReservations = database.reservationDao().getReservationsByUser(user.id)
                             delay(350)
                             reservations = refreshedReservations
                             isLoading = false
                         }
                     }
                 ) {
                     if (isLoading) {
                         CircularProgressIndicator(
                             modifier = Modifier.height(22.dp),
                             strokeWidth = 2.5.dp
                         )
                     } else {
                         Text("Refresh")
                     }
                 }
                Spacer(modifier = Modifier.height(16.dp))
                 Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Back") }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}


@Composable
fun ReservationDetailsScreen(
    reservationId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var reservation by remember { mutableStateOf<Reservation?>(null) }
    var deposit by remember { mutableStateOf<Deposit?>(null) }
    var message by remember { mutableStateOf("") }

    suspend fun refreshDetails() {
        reservation = database.reservationDao().getReservationById(reservationId)
        deposit = database.depositDao().getDepositByReservationId(reservationId)
    }

    LaunchedEffect(reservationId) { refreshDetails() }

    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent) { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize().padding(innerPadding), color = Color.Transparent) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Reservation Details", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(18.dp))

                reservation?.let { currentReservation ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Reservation #${currentReservation.id}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                StatusBadge(currentReservation.status)
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            InfoRow("Date", currentReservation.reservationDate)
                            InfoRow("Time", currentReservation.reservationTime)
                            InfoRow("Party Size", currentReservation.partySize.toString())
                            if (currentReservation.details.isNotBlank()) InfoRow("Details", currentReservation.details)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Deposit Information", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            if (deposit == null) {
                                Text("No deposit has been recorded yet.")
                            } else {
                                InfoRow("Amount", "₱%.2f".format(deposit!!.amount))
                                InfoRow("Status", deposit!!.status)
                                InfoRow("Recorded", deposit!!.recordedAt)
                            }
                        }
                    }

                    if (message.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(message, color = MaterialTheme.colorScheme.primary)
                    }

                    if (currentReservation.status == "Pending") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                scope.launch {
                                    database.reservationDao().updateReservationStatus(reservationId, "Cancelled")
                                    refreshDetails()
                                    Toast.makeText(context, "Reservation cancelled successfully.", Toast.LENGTH_SHORT).show()
                                    message = "Reservation cancelled successfully."
                                }
                            }
                        ) { Text("Cancel Reservation") }
                    }
                } ?: run {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Reservation not found.")
                }

                Spacer(modifier = Modifier.height(10.dp))
                Spacer(modifier = Modifier.height(16.dp))
                 Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Back to My Reservations") }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}


@Composable
fun CustomerProfileScreen(
    user: User,
    onBack: () -> Unit,
    onProfileUpdated: (User) -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var fullName by remember { mutableStateOf(user.fullName) }
    var email by remember { mutableStateOf(user.email) }
    var message by remember { mutableStateOf("") }
     var showNameConfirm by remember { mutableStateOf(false) }
     var showEmailConfirm by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent) { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize().padding(innerPadding), color = Color.Transparent) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("My Profile", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Manage your customer information", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(user.role, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Account Information", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = fullName,
                            onValueChange = { fullName = it; message = "" },
                            label = { Text("Full Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = email,
                            onValueChange = { email = it; message = "" },
                            label = { Text("Email") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                if (message.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(message, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                     modifier = Modifier.fillMaxWidth().height(52.dp),
                     shape = RoundedCornerShape(16.dp),
                     onClick = {
                         when {
                             fullName.isBlank() -> message = "Please enter your full name."
                             email.isBlank() -> message = "Please enter your email."
                             !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> message = "Please enter a valid email address."
                             else -> {
                                 scope.launch {
                                     val cleanEmail = email.trim().lowercase()
                                     val existingUser = database.userDao().getUserByEmail(cleanEmail)
                                     if (existingUser != null && existingUser.id != user.id) {
                                         message = "That email is already in use by another account."
                                     } else if (fullName.trim() != user.fullName) {
                                         showNameConfirm = true
                                     } else if (cleanEmail != user.email) {
                                         showEmailConfirm = true
                                     } else {
                                         message = "No changes were made."
                                     }
                                 }
                             }
                         }
                     }
                 ) { Text("Save Changes") }

                Spacer(modifier = Modifier.height(16.dp))
                 Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Back") }

                if (showNameConfirm) {
                    AlertDialog(
                        onDismissRequest = { showNameConfirm = false },
                        title = { Text("Change Name") },
                        text = { Text("Do you want to change your name?") },
                        confirmButton = {
                            Button(onClick = {
                                showNameConfirm = false
                                val cleanEmail = email.trim().lowercase()
                                if (cleanEmail != user.email) {
                                    showEmailConfirm = true
                                } else {
                                    scope.launch {
                                        database.userDao().updateUserProfile(user.id, fullName.trim(), cleanEmail)
                                        val updatedUser = database.userDao().getUserByEmail(cleanEmail)
                                        if (updatedUser != null) onProfileUpdated(updatedUser)
                                        message = "Profile updated successfully."
                                    }
                                }
                            }) { Text("Yes") }
                        },
                        dismissButton = {
                            Button(onClick = { showNameConfirm = false }) { Text("No") }
                        }
                    )
                }

                if (showEmailConfirm) {
                    AlertDialog(
                        onDismissRequest = { showEmailConfirm = false },
                        title = { Text("Change Email") },
                        text = { Text("Do you want to change your email?") },
                        confirmButton = {
                            Button(onClick = {
                                showEmailConfirm = false
                                scope.launch {
                                    val cleanEmail = email.trim().lowercase()
                                    database.userDao().updateUserProfile(user.id, fullName.trim(), cleanEmail)
                                    val updatedUser = database.userDao().getUserByEmail(cleanEmail)
                                    if (updatedUser != null) onProfileUpdated(updatedUser)
                                    message = "Profile updated successfully."
                                }
                            }) { Text("Yes") }
                        },
                        dismissButton = {
                            Button(onClick = { showEmailConfirm = false }) { Text("No") }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun StaffProfileScreen(
    user: User,
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "My Profile",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NoShowHeaderText,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "View your account details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NoShowHeaderMuted
                )

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Text(
                            "Account Details",
                            style = MaterialTheme.typography.titleLarge,
                            color = NoShowDarkText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Name", style = MaterialTheme.typography.labelLarge, color = NoShowMutedText)
                        Text(user.fullName, style = MaterialTheme.typography.bodyLarge, color = NoShowDarkText)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Email", style = MaterialTheme.typography.labelLarge, color = NoShowMutedText)
                        Text(user.email, style = MaterialTheme.typography.bodyLarge, color = NoShowDarkText)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Role", style = MaterialTheme.typography.labelLarge, color = NoShowMutedText)
                        Text(user.role, style = MaterialTheme.typography.bodyLarge, color = NoShowDarkText)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onBack
                ) {
                    Text("Back")
                }
            }
        }
    }
}


@Composable
fun StaffHomeScreen(
    user: User,
    onProfile: () -> Unit,
    onManageReservations: () -> Unit,
    onManageDeposits: () -> Unit,
    onManageUsers: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    var permissions by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    LaunchedEffect(user.role) {
        ensurePermissionDefaults(database)
        permissions = database.rolePermissionDao().getPermissionsForRole(user.role)
            .associate { it.permission to it.enabled }
    }

    val canReservations = user.role == "Admin" || permissions["RESERVATION_MANAGEMENT"] == true
    val canDeposits = user.role == "Admin" || permissions["DEPOSIT_MANAGEMENT"] == true
    val canUsers = user.role == "Admin" || permissions["USER_MANAGEMENT"] == true
    val canSettings = user.role == "Admin" || permissions["SETTINGS_ACCESS"] == true

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("NoShow", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text("Staff Dashboard", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Welcome, ${user.fullName}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Manage reservations, deposits, users, and permitted settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), enabled = canReservations, onClick = onManageReservations) { Text("Manage Reservations") }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), enabled = canDeposits, onClick = onManageDeposits) { Text("Manage Deposits") }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), enabled = canUsers, onClick = onManageUsers) { Text("User Management") }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), enabled = canSettings, onClick = onSettings) { Text("System Settings") }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), onClick = onProfile) { Text("My Profile") }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                onClick = onLogout
            ) {
                Text("Log Out", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun StaffReservationsScreen(
    onBack: () -> Unit,
    onReservationClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var reservations by remember { mutableStateOf<List<Reservation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf("All") }

    suspend fun refreshReservations() {
        reservations = database.reservationDao().getAllReservations()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        refreshReservations()
    }

    val lifecycleOwner = context as ComponentActivity

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    refreshReservations()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredReservations = if (selectedStatus == "All") {
        reservations
    } else {
        reservations.filter { it.status == selectedStatus }
    }

    val statuses = listOf(
        "All",
        "Pending",
        "Confirmed",
        "Cancelled",
        "No-Show"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Manage Reservations",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Filter by Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    statuses.chunked(2).forEach { rowStatuses ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowStatuses.forEachIndexed { index, status ->
                                Button(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(
                                            start = if (index == 0) 0.dp else 4.dp,
                                            end = if (index == 0) 4.dp else 0.dp
                                        ),
                                    shape = RoundedCornerShape(14.dp),
                                    onClick = { selectedStatus = status }
                                ) {
                                    Text(if (selectedStatus == status) "✓ $status" else status)
                                }
                            }
                            if (rowStatuses.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (reservations.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                "No reservations yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Customer reservations will appear here.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (filteredReservations.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            "No reservations match the $selectedStatus filter.",
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                } else {
                    filteredReservations.forEach { reservation ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clickable { onReservationClick(reservation.id) },
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Reservation #${reservation.id}",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    StatusBadge(reservation.status)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                InfoRow("Customer ID", reservation.userId.toString())
                                InfoRow("Date", reservation.reservationDate)
                                InfoRow("Time", reservation.reservationTime)
                                InfoRow("Party Size", reservation.partySize.toString())
                                if (reservation.details.isNotBlank()) {
                                    InfoRow("Details", reservation.details)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isRefreshing,
                    onClick = {
                        scope.launch {
                            isRefreshing = true
                            val refreshedReservations = database.reservationDao().getAllReservations()
                            delay(350)
                            reservations = refreshedReservations
                            isRefreshing = false
                        }
                    }
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text("Refresh")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                     modifier = Modifier.fillMaxWidth().height(52.dp),
                     shape = RoundedCornerShape(16.dp),
                     onClick = onBack
                 ) {
                     Text("Back")
                 }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StaffReservationDetailsScreen(
    reservationId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var reservation by remember { mutableStateOf<Reservation?>(null) }
    var deposit by remember { mutableStateOf<Deposit?>(null) }
    var depositAmount by remember { mutableStateOf("") }
    var minimumDeposit by remember { mutableStateOf(0.0) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(reservationId) {
        reservation = database.reservationDao().getReservationById(reservationId)
        deposit = database.depositDao().getDepositByReservationId(reservationId)
        minimumDeposit = ensureDepositSettings(database).minimumDeposit

        deposit?.let {
            depositAmount = "%.2f".format(it.amount)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Manage Reservation",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(24.dp))

                reservation?.let { currentReservation ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Reservation #${currentReservation.id}",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                StatusBadge(currentReservation.status)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            InfoRow("Customer ID", currentReservation.userId.toString())
                            InfoRow("Date", currentReservation.reservationDate)
                            InfoRow("Time", currentReservation.reservationTime)
                            InfoRow("Party Size", currentReservation.partySize.toString())
                            if (currentReservation.details.isNotBlank()) {
                                InfoRow("Details", currentReservation.details)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (currentReservation.status == "Pending") {
                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                scope.launch {
                                    database.reservationDao().updateReservationStatus(
                                        reservationId,
                                        "Confirmed"
                                    )
                                    reservation = database.reservationDao().getReservationById(reservationId)
                                    Toast.makeText(context, "Reservation confirmed successfully.", Toast.LENGTH_SHORT).show()
                                    message = "Reservation confirmed."
                                }
                            }
                        ) {
                            Text("Confirm Reservation")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (currentReservation.status == "Pending" || currentReservation.status == "Confirmed") {
                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                scope.launch {
                                    database.reservationDao().updateReservationStatus(
                                        reservationId,
                                        "Cancelled"
                                    )
                                    reservation = database.reservationDao().getReservationById(reservationId)
                                    message = "Reservation cancelled."
                                }
                            }
                        ) {
                            Text("Cancel Reservation")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (currentReservation.status == "Confirmed") {
                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                scope.launch {
                                    database.reservationDao().updateReservationStatus(
                                        reservationId,
                                        "No-Show"
                                    )
                                    reservation = database.reservationDao().getReservationById(reservationId)
                                    message = "Reservation marked as no-show."
                                }
                            }
                        ) {
                            Text("Mark No-Show")
                        }
                    }

                    if (message.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Deposit Information",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = depositAmount,
                        onValueChange = {
                            depositAmount = it.filter { character ->
                                character.isDigit() || character == '.'
                            }
                            message = ""
                        },
                        label = {
                            Text("Deposit Amount")
                        },
                        placeholder = {
                            Text("Example: 500.00")
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (deposit == null) {
                        Text("Deposit: Not recorded")

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                val amount = depositAmount.toDoubleOrNull()

                                if (amount == null || !amount.isFinite() || amount <= 0) {
                                    message = "Please enter a valid deposit amount."
                                } else if (amount < minimumDeposit) {
                                    message = "Deposit amount must be at least ₱%.2f.".format(minimumDeposit)
                                } else {
                                    scope.launch {
                                        val existingDeposit = database.depositDao()
                                            .getDepositByReservationId(reservationId)

                                        if (existingDeposit != null) {
                                            deposit = existingDeposit
                                            depositAmount = "%.2f".format(existingDeposit.amount)
                                            message = "A deposit is already recorded for this reservation."
                                            return@launch
                                        }

                                        val recordedAt = SimpleDateFormat(
                                            "MMMM d, yyyy h:mm a",
                                            Locale.getDefault()
                                        ).format(Calendar.getInstance().time)

                                        database.depositDao().insertDeposit(
                                            Deposit(
                                                reservationId = reservationId,
                                                amount = amount,
                                                status = "Pending",
                                                recordedAt = recordedAt
                                            )
                                        )

                                        deposit = database.depositDao()
                                            .getDepositByReservationId(reservationId)

                                        message = "Deposit recorded successfully."
                                    }
                                }
                            }
                        ) {
                            Text("Record Deposit")
                        }
                    } else {
                        Text("Status: ${deposit!!.status}")
                        Text("Recorded Date: ${deposit!!.recordedAt}")

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                val amount = depositAmount.toDoubleOrNull()

                                if (amount == null || !amount.isFinite() || amount <= 0) {
                                    message = "Please enter a valid deposit amount."
                                } else if (amount < minimumDeposit) {
                                    message = "Deposit amount must be at least ₱%.2f.".format(minimumDeposit)
                                } else {
                                    scope.launch {
                                        val recordedAt = SimpleDateFormat(
                                            "MMMM d, yyyy h:mm a",
                                            Locale.getDefault()
                                        ).format(Calendar.getInstance().time)

                                        database.depositDao().updateDeposit(
                                            depositId = deposit!!.id,
                                            amount = amount,
                                            status = deposit!!.status,
                                            recordedAt = recordedAt
                                        )

                                        deposit = database.depositDao()
                                            .getDepositByReservationId(reservationId)

                                        message = "Deposit updated successfully."
                                    }
                                }
                            }
                        ) {
                            Text("Update Deposit")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                scope.launch {
                                    val currentDeposit = deposit ?: return@launch

                                    database.depositDao().updateDeposit(
                                        depositId = currentDeposit.id,
                                        amount = currentDeposit.amount,
                                        status = "Paid",
                                        recordedAt = currentDeposit.recordedAt
                                    )

                                    deposit = database.depositDao()
                                        .getDepositByReservationId(reservationId)

                                    message = "Deposit marked as paid."
                                }
                            }
                        ) {
                            Text("Mark Deposit Paid")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                scope.launch {
                                    val currentDeposit = deposit ?: return@launch

                                    database.depositDao().updateDeposit(
                                        depositId = currentDeposit.id,
                                        amount = currentDeposit.amount,
                                        status = "Pending",
                                        recordedAt = currentDeposit.recordedAt
                                    )

                                    deposit = database.depositDao()
                                        .getDepositByReservationId(reservationId)

                                    message = "Deposit marked as pending."
                                }
                            }
                        ) {
                            Text("Mark Deposit Pending")
                        }
                    }
                } ?: Text("Reservation not found.")

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                     modifier = Modifier.fillMaxWidth().height(52.dp),
                     shape = RoundedCornerShape(16.dp),
                     onClick = onBack
                 ) {
                     Text("Back")
                 }
            }
        }
    }
}

@Composable
fun StaffDepositsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    var deposits by remember { mutableStateOf<List<Deposit>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    suspend fun refreshDeposits() {
        deposits = database.depositDao().getAllDeposits()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        refreshDeposits()
    }

    val lifecycleOwner = context as ComponentActivity

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    refreshDeposits()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Manage Deposits",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (deposits.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                "No deposit records yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Recorded deposits will appear here.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    deposits.forEach { currentDeposit ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Deposit #${currentDeposit.id}",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    StatusBadge(currentDeposit.status)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                InfoRow("Reservation ID", currentDeposit.reservationId.toString())
                                InfoRow("Amount", "₱%.2f".format(currentDeposit.amount))
                                InfoRow("Recorded Date", currentDeposit.recordedAt)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isRefreshing,
                    onClick = {
                        scope.launch {
                            isRefreshing = true
                            val refreshedDeposits = database.depositDao().getAllDeposits()
                            delay(350)
                            deposits = refreshedDeposits
                            isRefreshing = false
                        }
                    }
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text("Refresh")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                     modifier = Modifier.fillMaxWidth().height(52.dp),
                     shape = RoundedCornerShape(16.dp),
                     onClick = onBack
                 ) {
                     Text("Back")
                 }
            }
        }
    }
}



@Composable
fun AdminHomeScreen(
    user: User,
    onUsers: () -> Unit,
    onReservations: () -> Unit,
    onDeposits: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    var permissions by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    LaunchedEffect(user.role) {
        ensurePermissionDefaults(database)
        permissions = database.rolePermissionDao().getPermissionsForRole(user.role)
            .associate { it.permission to it.enabled }
    }

    val canUsers = user.role == "Admin" || permissions["USER_MANAGEMENT"] == true
    val canReservations = user.role == "Admin" || permissions["RESERVATION_MANAGEMENT"] == true
    val canDeposits = user.role == "Admin" || permissions["DEPOSIT_MANAGEMENT"] == true
    val canSettings = user.role == "Admin" || permissions["SETTINGS_ACCESS"] == true

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("NoShow", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text("Admin Dashboard", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Welcome, ${user.fullName}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Manage users and system administration.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), enabled = canUsers, onClick = onUsers) { Text("User Management") }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), enabled = canReservations, onClick = onReservations) { Text("Reservation Management") }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), enabled = canDeposits, onClick = onDeposits) { Text("Deposit Management") }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), enabled = canSettings, onClick = onSettings) { Text("System Settings") }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                onClick = onLogout
            ) {
                Text("Log Out", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AdminUsersScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }

    suspend fun loadUsers() {
        users = database.userDao().getAllUsers()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadUsers()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                "User Management",
                style = MaterialTheme.typography.headlineMedium,
                color = NoShowHeaderText,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "View registered users and manage their roles.",
                style = MaterialTheme.typography.bodyMedium,
                color = NoShowHeaderMuted
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isRefreshing,
                    onClick = {
                        scope.launch {
                            isRefreshing = true
                            delay(350)
                            loadUsers()
                            isRefreshing = false
                        }
                    }
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Refresh")
                    }
                }

                TextButton(
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = onBack
                ) {
                    Text("Back")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (users.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No registered users",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Registered customer accounts will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                users.forEach { user ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedUser = user },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                user.fullName,
                                style = MaterialTheme.typography.titleLarge,
                                color = NoShowDarkText,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                user.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NoShowMutedText
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatusBadge(user.role)
                                TextButton(onClick = { selectedUser = user }) {
                                    Text("Change Role", color = NoShowNavy, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    selectedUser?.let { user ->
        AlertDialog(
            onDismissRequest = { selectedUser = null },
            title = {
                Text("Change User Role")
            },
            text = {
                Column {
                    Text(
                        user.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Select a role", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    listOf("Customer", "Staff", "Admin").forEach { role ->
                        Button(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            onClick = {
                                scope.launch {
                                    database.userDao().updateUserRole(user.id, role)
                                    users = database.userDao().getAllUsers()
                                    selectedUser = null
                                    Toast.makeText(
                                        context,
                                        "User role updated to $role.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {
                            Text(role)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdminSystemSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val roles = listOf("Customer", "Staff", "Admin")
    val permissionItems = listOf(
        "USER_MANAGEMENT" to "User Management",
        "RESERVATION_MANAGEMENT" to "Reservation Management",
        "DEPOSIT_MANAGEMENT" to "Deposit Management",
        "SETTINGS_ACCESS" to "Settings Access"
    )
    var rolePermissions by remember { mutableStateOf<Map<String, Map<String, Boolean>>>(emptyMap()) }
    var depositRequired by remember { mutableStateOf(false) }
    var defaultDepositAmount by remember { mutableStateOf("500.00") }
    var minimumDeposit by remember { mutableStateOf("100.00") }
    var maxPartySize by remember { mutableStateOf("50") }
    var openingTime by remember { mutableStateOf("10:00 AM") }
    var closingTime by remember { mutableStateOf("10:00 PM") }
    var minimumAdvanceMinutes by remember { mutableStateOf("30") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    suspend fun loadSettings() {
        ensurePermissionDefaults(database)
        val settings = ensureDepositSettings(database)
        val reservationSettings = ensureReservationSettings(database)

        rolePermissions = roles.associateWith { role ->
            database.rolePermissionDao().getPermissionsForRole(role)
                .associate { it.permission to it.enabled }
        }

        depositRequired = settings.depositRequired
        defaultDepositAmount = "%.2f".format(settings.defaultDepositAmount)
        minimumDeposit = "%.2f".format(settings.minimumDeposit)
        maxPartySize = reservationSettings.maxPartySize.toString()
        openingTime = reservationSettings.openingTime
        closingTime = reservationSettings.closingTime
        minimumAdvanceMinutes = reservationSettings.minimumAdvanceMinutes.toString()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadSettings()
    }

    fun updatePermission(role: String, permission: String, enabled: Boolean) {
        if (role == "Admin") return
        scope.launch {
            isSaving = true
            database.rolePermissionDao().setPermission(role, permission, enabled)
            rolePermissions = rolePermissions.toMutableMap().apply {
                this[role] = this[role].orEmpty().toMutableMap().apply {
                    this[permission] = enabled
                }
            }
            isSaving = false
            Toast.makeText(context, "Permission updated.", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveDepositSettings() {
        val defaultAmount = defaultDepositAmount.toDoubleOrNull()
        val minimumAmount = minimumDeposit.toDoubleOrNull()

        when {
            defaultAmount == null || !defaultAmount.isFinite() || defaultAmount <= 0 -> {
                message = "Default deposit amount must be greater than ₱0.00."
            }
            minimumAmount == null || !minimumAmount.isFinite() || minimumAmount < 0 -> {
                message = "Minimum deposit must be ₱0.00 or higher."
            }
            defaultAmount < minimumAmount -> {
                message = "Default deposit amount cannot be lower than the minimum deposit."
            }
            else -> {
                scope.launch {
                    isSaving = true
                    val recordedAt = SimpleDateFormat(
                        "MMMM d, yyyy h:mm a",
                        Locale.getDefault()
                    ).format(Calendar.getInstance().time)

                    database.depositSettingsDao().saveSettings(
                        DepositSettings(
                            id = 1,
                            depositRequired = depositRequired,
                            defaultDepositAmount = defaultAmount,
                            minimumDeposit = minimumAmount,
                            updatedAt = recordedAt
                        )
                    )

                    message = "Deposit settings saved successfully."
                    isSaving = false
                    Toast.makeText(context, "Deposit settings saved successfully.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    fun saveReservationSettings() {
        val maxParty = maxPartySize.toIntOrNull()
        val minimumAdvance = minimumAdvanceMinutes.toIntOrNull()
        val opening = timeToMinutes(openingTime)
        val closing = timeToMinutes(closingTime)
        when {
            maxParty == null || maxParty <= 0 -> message = "Maximum party size must be greater than 0."
            maxParty > 500 -> message = "Maximum party size cannot exceed 500 guests."
            opening == null -> message = "Please select a valid opening time."
            closing == null -> message = "Please select a valid closing time."
            opening >= closing -> message = "Opening time must be earlier than closing time."
            minimumAdvance == null || minimumAdvance < 0 -> message = "Minimum advance time must be 0 minutes or higher."
            minimumAdvance > 1440 -> message = "Minimum advance time cannot exceed 1440 minutes."
            else -> scope.launch {
                isSaving = true
                val recordedAt = SimpleDateFormat("MMMM d, yyyy h:mm a", Locale.getDefault()).format(Calendar.getInstance().time)
                database.reservationSettingsDao().saveSettings(ReservationSettings(1, maxParty, openingTime, closingTime, minimumAdvance, recordedAt))
                message = "Reservation parameters saved successfully."
                isSaving = false
                Toast.makeText(context, "Reservation parameters saved successfully.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                "System Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = NoShowHeaderText,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "Configure role-based permissions and deposit rules.",
                style = MaterialTheme.typography.bodyMedium,
                color = NoShowHeaderMuted
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Deposit Rules",
                            style = MaterialTheme.typography.titleLarge,
                            color = NoShowDarkText,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            "Configure the deposit requirement and amount rules for reservations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NoShowMutedText
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Deposit Required",
                                style = MaterialTheme.typography.bodyLarge,
                                color = NoShowDarkText
                            )
                            Switch(
                                checked = depositRequired,
                                enabled = !isSaving,
                                onCheckedChange = {
                                    depositRequired = it
                                    message = ""
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = defaultDepositAmount,
                            onValueChange = {
                                defaultDepositAmount = it.filter { character ->
                                    character.isDigit() || character == '.'
                                }
                                message = ""
                            },
                            label = { Text("Default Deposit Amount") },
                            placeholder = { Text("Example: 500.00") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = minimumDeposit,
                            onValueChange = {
                                minimumDeposit = it.filter { character ->
                                    character.isDigit() || character == '.'
                                }
                                message = ""
                            },
                            label = { Text("Minimum Deposit") },
                            placeholder = { Text("Example: 100.00") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isSaving,
                            onClick = { saveDepositSettings() }
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(22.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text("Save Deposit Settings")
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "Reservation Parameters",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NoShowDarkText,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Configure party size and reservation time rules.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NoShowMutedText
                                )
                    Spacer(modifier = Modifier.height(14.dp))
                    TextField(modifier = Modifier.fillMaxWidth(), value = maxPartySize, onValueChange = { maxPartySize = it.filter { c -> c.isDigit() }; message = "" }, label = { Text("Maximum Party Size") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = openingTime,
                            onValueChange = {},
                            label = { Text("Opening Time") },
                            placeholder = { Text("Select opening time") },
                            readOnly = true,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    val (hour, minute) = parseTimeForPicker(openingTime)
                                    TimePickerDialog(
                                        context,
                                        { _, selectedHour, selectedMinute ->
                                            openingTime = formatPickerTime(selectedHour, selectedMinute)
                                            message = ""
                                        },
                                        hour,
                                        minute,
                                        false
                                    ).show()
                                }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = closingTime,
                            onValueChange = {},
                            label = { Text("Closing Time") },
                            placeholder = { Text("Select closing time") },
                            readOnly = true,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    val (hour, minute) = parseTimeForPicker(closingTime)
                                    TimePickerDialog(
                                        context,
                                        { _, selectedHour, selectedMinute ->
                                            closingTime = formatPickerTime(selectedHour, selectedMinute)
                                            message = ""
                                        },
                                        hour,
                                        minute,
                                        false
                                    ).show()
                                }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(modifier = Modifier.fillMaxWidth(), value = minimumAdvanceMinutes, onValueChange = { minimumAdvanceMinutes = it.filter { c -> c.isDigit() }; message = "" }, label = { Text("Minimum Advance Time (minutes)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), onClick = { saveReservationSettings() }, enabled = !isSaving) { Text("Save Reservation Parameters") }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (message.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = message,
                                color = if (message.contains("successfully", ignoreCase = true)) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                roles.forEach { role ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                role,
                                style = MaterialTheme.typography.titleLarge,
                                color = NoShowDarkText,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                if (role == "Admin") {
                                    "Administrators always retain full access."
                                } else {
                                    "Select which modules this role can access."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NoShowMutedText
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            permissionItems.forEachIndexed { index, (permission, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )

                                    Switch(
                                        checked = rolePermissions[role]?.get(permission) == true,
                                        enabled = role != "Admin" && !isSaving,
                                        onCheckedChange = { enabled ->
                                            updatePermission(role, permission, enabled)
                                        }
                                    )
                                }

                                if (index < permissionItems.lastIndex) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            color = NoShowDarkText,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "When Deposit Required is enabled, new reservations automatically receive a pending deposit using the saved default amount. Staff deposit entries must meet the saved minimum deposit.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NoShowMutedText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = onBack
            ) {
                Text("Back")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val containerColor = when (status) {
        "Confirmed", "Paid" -> Color(0xFFDFF5E1)
        "Pending", "Not recorded" -> Color(0xFFFFF0C2)
        "Cancelled" -> Color(0xFFFFDAD6)
        "No-Show" -> Color(0xFFE0E0E0)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = when (status) {
        "Confirmed", "Paid" -> Color(0xFF1B5E20)
        "Pending", "Not recorded" -> Color(0xFF6D4C00)
        "Cancelled" -> Color(0xFF8C1D18)
        "No-Show" -> Color(0xFF424242)
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

fun hashPassword(password: String): String {

    val bytes = MessageDigest
        .getInstance("SHA-256")
        .digest(
            password.toByteArray()
        )


    return bytes.joinToString("") {

        "%02x".format(it)
    }
}


@Preview(showBackground = true)
@Composable
fun NoShowPreview() {

    NoShowTheme {

        NoShowApp()
    }
}
