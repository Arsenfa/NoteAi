package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.AppDatabase
import com.example.data.Note
import com.example.data.NoteRepository
import com.example.data.AuthRepository
import com.example.ui.theme.*
import com.example.viewmodel.ChatMessage
import com.example.viewmodel.NoteViewModel
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.AuthUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val database = AppDatabase.getDatabase(context)
            val repository = NoteRepository(database.noteDao())
            
            // Factory injection to provide Repository to NoteViewModel
            val authRepository = remember { AuthRepository() }
            val noteViewModel: NoteViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return NoteViewModel(repository, context.applicationContext, authRepository) as T
                }
            })
            val authViewModel: AuthViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AuthViewModel(authRepository, context.applicationContext) as T
                }
            })

            val currentTheme by noteViewModel.appTheme.collectAsStateWithLifecycle()
            val isDarkTheme = when (currentTheme) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme, dynamicColor = false) {
                Crossfade(targetState = isDarkTheme, animationSpec = tween(300)) { targetDark ->
                    MainAppHost(viewModel = noteViewModel, authViewModel = authViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainAppHost(viewModel: NoteViewModel, authViewModel: AuthViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    // Read Firebase Auth state directly so the splash route is correct on cold
    // start with a persisted session. DataStore-backed flows can lag.
    val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    val isLoggedIn = currentUser != null && !currentUser.isAnonymous
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                when {
                    targetState == "editor" -> {
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it / 3 } + fadeOut()
                    }
                    initialState == "editor" -> {
                        slideInHorizontally { -it / 3 } + fadeIn() togetherWith
                                slideOutHorizontally { it } + fadeOut()
                    }
                    else -> {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    }
                }
            },
            label = "screenTransition"
        ) { screen ->
            when (screen) {
                "splash" -> SplashScreen(onTimeout = {
                    if (isLoggedIn) {
                        viewModel.navigateTo("home_list")
                    } else {
                        viewModel.navigateTo("onboarding1")
                    }
                })
                "onboarding1" -> Onboarding1Screen(viewModel)
                "onboarding2" -> Onboarding2Screen(viewModel)
                "onboarding3" -> Onboarding3Screen(viewModel)
                "login" -> LoginScreen(viewModel, authViewModel)
                "home_list" -> MainDashboard(viewModel, isGrid = false)
                "home_grid" -> MainDashboard(viewModel, isGrid = true)
                "editor" -> EditorScreen(viewModel)
                "briefing" -> DailyBriefingScreen(viewModel, authViewModel)
                "search" -> SearchScreen(viewModel)
                "settings" -> SettingsScreen(viewModel, authViewModel)
                "ocr" -> WhiteboardOcrScreen(viewModel)
            }
        }
    }
}

// ----------------------------------------------------
// 1. SPLASH SCREEN
// ----------------------------------------------------
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1800)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfacePrimary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.brand_note),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.brand_ai),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = ButterYellow
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                fontSize = 14.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

// ----------------------------------------------------
// 2. ONBOARDING SCREEN 1
// ----------------------------------------------------
@Composable
fun Onboarding1Screen(viewModel: NoteViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfacePrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { viewModel.navigateTo("login") }) {
                Text(stringResource(R.string.onboarding_skip), color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Phone Frame Mockup
        Box(
            modifier = Modifier
                .width(240.dp)
                .height(380.dp)
                .border(6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceTertiary)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("9:41", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.SignalCellularAlt, null, modifier = Modifier.size(10.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Inset Note
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.onboarding_mock_note_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .background(ButterSoft, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(R.string.onboarding_mock_chip), fontSize = 9.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth(0.7f).height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(ButterYellow, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send follow-up email", fontSize = 10.sp, color = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Suggestion chip inside frame
                Box(
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .background(ButterSoft)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.onboarding_mock_summarize), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        Text(stringResource(R.string.onboarding1_title), style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding1_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.weight(0.5f))

        // Pager Indicators
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(8.dp).background(ButterYellow, CircleShape))
            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.navigateTo("onboarding2") },
            colors = ButtonDefaults.buttonColors(containerColor = ButterYellow),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(stringResource(R.string.onboarding_continue), color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

// ----------------------------------------------------
// 3. ONBOARDING SCREEN 2
// ----------------------------------------------------
@Composable
fun Onboarding2Screen(viewModel: NoteViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfacePrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { viewModel.navigateTo("login") }) {
                Text(stringResource(R.string.onboarding_skip), color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Phone Frame Mockup
        Box(
            modifier = Modifier
                .width(240.dp)
                .height(380.dp)
                .border(6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceTertiary)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("12:30", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scroll tokens
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("work", fontSize = 9.sp, color = TextSecondary)
                    }
                    Box(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("ideas", fontSize = 9.sp, color = TextSecondary)
                    }
                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("meeting", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(stringResource(R.string.smart_folders), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                // Folders setup list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnboardingFolderRow(stringResource(R.string.onboarding_mock_folder_action), true)
                    OnboardingFolderRow(stringResource(R.string.onboarding_mock_folder_ideas), false)
                    OnboardingFolderRow(stringResource(R.string.onboarding_mock_folder_meeting), false)
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        Text(stringResource(R.string.onboarding2_title), style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding2_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.weight(0.5f))

        // Pager Indicators
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
            Box(modifier = Modifier.size(8.dp).background(ButterYellow, CircleShape))
            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.navigateTo("onboarding3") },
            colors = ButtonDefaults.buttonColors(containerColor = ButterYellow),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(stringResource(R.string.onboarding_continue), color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OnboardingFolderRow(title: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(SurfaceTertiary, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Folder, null, modifier = Modifier.size(12.dp), tint = TextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
        if (active) {
            Box(modifier = Modifier.size(6.dp).background(ButterYellow, CircleShape))
        }
    }
}

// ----------------------------------------------------
// 4. ONBOARDING SCREEN 3
// ----------------------------------------------------
@Composable
fun Onboarding3Screen(viewModel: NoteViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfacePrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { viewModel.navigateTo("login") }) {
                Text(stringResource(R.string.onboarding_skip), color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Phone Frame Mockup
        Box(
            modifier = Modifier
                .width(240.dp)
                .height(380.dp)
                .border(6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceTertiary)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("9:41", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Chat container simulation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(SurfaceElevated, RoundedCornerShape(16.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Box(modifier = Modifier.background(ButterSoft, RoundedCornerShape(12.dp)).padding(8.dp)) {
                                Text(stringResource(R.string.onboarding_mock_summarize_request), fontSize = 9.sp, color = TextPrimary)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Box(modifier = Modifier.background(SurfaceTertiary, RoundedCornerShape(12.dp)).padding(8.dp)) {
                                Text(stringResource(R.string.onboarding_mock_summarize_response), fontSize = 9.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        Text(stringResource(R.string.onboarding3_title), style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding3_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.weight(0.5f))

        // Pager Indicators
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
            Box(modifier = Modifier.size(8.dp).background(ButterYellow, CircleShape))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.completedOnboarding.value = true
                viewModel.navigateTo("login")
            },
            colors = ButtonDefaults.buttonColors(containerColor = ButterYellow),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(stringResource(R.string.onboarding_get_started), color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

// ----------------------------------------------------
// 5. LOGIN SCREEN
// ----------------------------------------------------
@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun LoginScreen(viewModel: NoteViewModel, authViewModel: AuthViewModel) {
    val focusManager = LocalFocusManager.current

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showConfirmPassword by rememberSaveable { mutableStateOf(false) }
    var isRegisterMode by rememberSaveable { mutableStateOf(false) }

    // Error States
    var emailError by rememberSaveable { mutableStateOf("") }
    var passwordError by rememberSaveable { mutableStateOf("") }
    var confirmPasswordError by rememberSaveable { mutableStateOf("") }
    var generalError by rememberSaveable { mutableStateOf("") }

    // Hoisted validation strings (non-composable context inside onClick lambda)
    val errEmailEmpty = stringResource(R.string.err_email_empty)
    val errEmailInvalid = stringResource(R.string.err_email_invalid)
    val errPasswordEmpty = stringResource(R.string.err_password_empty)
    val errPasswordWeak = stringResource(R.string.err_password_weak)
    val errConfirmEmpty = stringResource(R.string.err_confirm_empty)
    val errPasswordsMismatch = stringResource(R.string.err_passwords_mismatch)

    var isLocalLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val googleAuthHelper = remember(context) { com.example.data.GoogleAuthHelper(context) }
    val scope = rememberCoroutineScope()

    // Observe loading or error states
    LaunchedEffect(authState) {
        when (authState) {
            is AuthUiState.LoggedIn -> {
                viewModel.navigateTo("home_list")
            }
            is AuthUiState.Error -> {
                generalError = (authState as AuthUiState.Error).message
                isLocalLoading = false
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // 1. Wordmark "noteai"
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.brand_note),
                fontSize = 48.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.brand_ai),
                fontSize = 48.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                color = ButterYellow
            )
        }

        // 2. Subtitle Description
        Text(
            text = if (isRegisterMode) stringResource(R.string.auth_subtitle_sign_up) else stringResource(R.string.auth_subtitle_sign_in),
            fontSize = 14.sp,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // General Error banner
        if (generalError.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .background(ErrorRed.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(generalError, color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        // 3. Email TextField
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.label_email),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = ""
                    generalError = ""
                },
                placeholder = {
                    Text(stringResource(R.string.placeholder_email), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 15.sp)
                },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                isError = emailError.isNotEmpty(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ButterYellow,
                    unfocusedBorderColor = Color.Transparent,
                    errorBorderColor = ErrorRed,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    errorTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )
            if (emailError.isNotEmpty()) {
                Text(
                    text = emailError,
                    color = ErrorRed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Password TextField
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.label_password),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = ""
                    generalError = ""
                },
                placeholder = {
                    Text("••••••••", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 15.sp)
                },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                isError = passwordError.isNotEmpty(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ButterYellow,
                    unfocusedBorderColor = Color.Transparent,
                    errorBorderColor = ErrorRed,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    errorTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    imeAction = if (isRegisterMode) ImeAction.Next else ImeAction.Done
                ),
                trailingIcon = {
                    Text(
                        text = if (showPassword) stringResource(R.string.auth_hide) else stringResource(R.string.auth_show),
                        color = AccentDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { showPassword = !showPassword }
                            .padding(end = 12.dp)
                    )
                }
            )
            if (passwordError.isNotEmpty()) {
                Text(
                    text = passwordError,
                    color = ErrorRed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Confirm Password field after Password (only shown in Sign Up Mode)
        if (isRegisterMode) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.label_confirm_password),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        confirmPasswordError = ""
                        generalError = ""
                    },
                    placeholder = {
                        Text("••••••••", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 15.sp)
                    },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif
                    ),
                    isError = confirmPasswordError.isNotEmpty(),
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ButterYellow,
                        unfocusedBorderColor = Color.Transparent,
                        errorBorderColor = ErrorRed,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        errorTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        Text(
                            text = if (showConfirmPassword) stringResource(R.string.auth_hide) else stringResource(R.string.auth_show),
                            color = AccentDeep,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { showConfirmPassword = !showConfirmPassword }
                                .padding(end = 12.dp)
                        )
                    }
                )
                if (confirmPasswordError.isNotEmpty()) {
                    Text(
                        text = confirmPasswordError,
                        color = ErrorRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 5. "Forgot password?" link (Only on Sign In Mode)
        if (!isRegisterMode) {
            val passwordResetSentMsg = stringResource(R.string.password_reset_sent, email)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = stringResource(R.string.forgot_password),
                    color = AccentDeep,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        if (email.isBlank()) {
                            Toast.makeText(context, errEmailEmpty, Toast.LENGTH_SHORT).show()
                        } else {
                            authViewModel.sendPasswordResetEmail(
                                email = email,
                                onSuccess = {
                                    Toast.makeText(
                                        context,
                                        passwordResetSentMsg,
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                onError = { errorMsg ->
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                )
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 6. Main Submit Button ("Sign In" / "Sign Up")
        Button(
            onClick = {
                // Front-end Local Validation
                val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                val isPasswordValid = password.length >= 6

                emailError = if (email.isBlank()) errEmailEmpty else if (!isEmailValid) errEmailInvalid else ""
                passwordError = if (password.isBlank()) errPasswordEmpty else if (!isPasswordValid) errPasswordWeak else ""

                if (isRegisterMode) {
                    val passwordsMatch = password == confirmPassword
                    confirmPasswordError = if (confirmPassword.isBlank()) errConfirmEmpty else if (!passwordsMatch) errPasswordsMismatch else ""
                }

                if (emailError.isNotEmpty() || passwordError.isNotEmpty() || (isRegisterMode && confirmPasswordError.isNotEmpty())) {
                    return@Button
                }

                // AuthViewModel API Submission
                focusManager.clearFocus()
                isLocalLoading = true
                generalError = ""
                if (isRegisterMode) {
                    authViewModel.signUpWithEmail(email, password, {
                        isLocalLoading = false
                        viewModel.navigateTo("home_list")
                    }, {
                        isLocalLoading = false
                        generalError = it
                    })
                } else {
                    authViewModel.signInWithEmail(email, password, {
                        isLocalLoading = false
                        viewModel.navigateTo("home_list")
                    }, {
                        isLocalLoading = false
                        generalError = it
                    })
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = ButterYellow,
                contentColor = TextPrimary
            ),
            shape = RoundedCornerShape(9999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isLocalLoading) {
                CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = if (isRegisterMode) stringResource(R.string.auth_sign_up) else stringResource(R.string.auth_sign_in),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 7. Divider Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(BorderSubtle)
            )
            Text(
                text = stringResource(R.string.auth_or_divider),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(BorderSubtle)
            )
        }

        // 8. "Continue with Google" Button
        Button(
            onClick = {
                focusManager.clearFocus()
                generalError = ""
                isLocalLoading = true
                scope.launch {
                    val tokenResult = googleAuthHelper.signIn().recoverCatching {
                        googleAuthHelper.signInExistingOnly().getOrThrow()
                    }
                    tokenResult.fold(
                        onSuccess = { idToken ->
                            authViewModel.signInWithGoogle(idToken, {
                                isLocalLoading = false
                                        viewModel.navigateTo("home_list")
                            }, { msg ->
                                isLocalLoading = false
                                generalError = msg
                            })
                        },
                        onFailure = { err ->
                            isLocalLoading = false
                            generalError = err.message ?: context.getString(R.string.google_err_default)
                        }
                    )
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(9999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = "https://lh3.googleusercontent.com/COxitS2COVK94UPxoZOFJW5vP-R3g52M1ON06QqfZgHocSOfR97v211xsI93bhg9KGA=s48",
                    contentDescription = stringResource(R.string.cd_google_logo),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.auth_continue_with_google),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 9. "Try without an account" ghost link
        Box(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .clickable {
                    focusManager.clearFocus()
                    generalError = ""
                    isLocalLoading = true
                    authViewModel.signInAnonymously({
                        isLocalLoading = false
                        viewModel.navigateTo("home_list")
                    }, {
                        isLocalLoading = false
                        generalError = it
                    })
                }
        ) {
            Text(
                text = stringResource(R.string.auth_try_guest),
                color = AccentDeep,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 10. Bottom toggle text
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isRegisterMode) "${stringResource(R.string.auth_have_account)} " else "${stringResource(R.string.auth_no_account)} ",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isRegisterMode) stringResource(R.string.auth_sign_in_link) else stringResource(R.string.auth_sign_up_link),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AccentDeep,
                modifier = Modifier.clickable {
                    isRegisterMode = !isRegisterMode
                    emailError = ""
                    passwordError = ""
                    confirmPasswordError = ""
                    generalError = ""
                }
            )
        }

        // 11. Legal text
        Text(
            text = stringResource(R.string.auth_legal),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

// ----------------------------------------------------
// 6. MAIN DASHBOARD (HOME LIST & GRID)
// ----------------------------------------------------
@Composable
fun MainDashboard(viewModel: NoteViewModel, isGrid: Boolean) {
    val notes by viewModel.filteredNotes.collectAsStateWithLifecycle()
    val filterChip by viewModel.filterChip.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            FloatingDock(
                activeTab = "home",
                onHomeClick = { viewModel.navigateTo(if (viewModel.isGridView.value) "home_grid" else "home_list") },
                onAddClick = { viewModel.createEmptyNote() },
                onProfileClick = { viewModel.navigateTo("settings") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfacePrimary)
                .statusBarsPadding()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Header Screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    val greetingRes = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
                        in 12..17 -> R.string.greeting_afternoon
                        in 18..4 -> R.string.greeting_evening
                        else -> R.string.greeting_morning
                    }
                    Text(stringResource(greetingRes), fontSize = 14.sp, color = TextSecondary)
                    Text(stringResource(R.string.title_notes), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val syncing by viewModel.isSyncing.collectAsStateWithLifecycle()
                    val syncError by viewModel.lastSyncError.collectAsStateWithLifecycle()
                    val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
                    val syncRotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                        label = "syncRotation"
                    )
                    val syncIconTint = when {
                        syncing -> AccentDeep
                        syncError != null -> ErrorRed
                        else -> TextPrimary
                    }
                    IconButton(
                        onClick = { if (!syncing) viewModel.triggerSync() },
                        enabled = !syncing
                    ) {
                        Icon(
                            imageVector = if (syncError != null && !syncing) Icons.Default.SyncProblem else Icons.Default.Sync,
                            contentDescription = stringResource(R.string.cd_sync_now),
                            tint = syncIconTint,
                            modifier = if (syncing) Modifier.graphicsLayer { rotationZ = syncRotation } else Modifier
                        )
                    }
                    IconButton(onClick = {
                        val nextGrid = !isGrid
                        viewModel.isGridView.value = nextGrid
                        viewModel.navigateTo(if (nextGrid) "home_grid" else "home_list")
                    }) {
                        Icon(
                            imageVector = if (isGrid) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = stringResource(R.string.cd_toggle_view),
                            tint = if (isGrid) ButterYellow else TextPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.navigateTo("search") }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search), tint = TextPrimary)
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options), tint = TextPrimary)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(SurfaceElevated)
                        ) {
                            val clearedEmptyNotesMsg = stringResource(R.string.cleared_empty_notes)
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_empty_notes), color = TextPrimary) },
                                onClick = {
                                    viewModel.deleteEmptyNotes()
                                    showMenu = false
                                    Toast.makeText(context, clearedEmptyNotesMsg, Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }
            }

            // Categories horizontal scroll chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(stringResource(R.string.filter_all), filterChip == "All", onClick = { viewModel.filterChip.value = "All" })
                CategoryChip(stringResource(R.string.filter_today), filterChip == "Today", onClick = { viewModel.filterChip.value = "Today" })
                CategoryChip(stringResource(R.string.filter_action_items), filterChip == "Action items", onClick = { viewModel.filterChip.value = "Action items" })
                CategoryChip(stringResource(R.string.filter_meeting_notes), filterChip == "Meeting notes", onClick = { viewModel.filterChip.value = "Meeting notes" })
                CategoryChip(stringResource(R.string.filter_ideas), filterChip == "Ideas", onClick = { viewModel.filterChip.value = "Ideas" })
                CategoryChip(stringResource(R.string.filter_briefing), false, onClick = { viewModel.navigateTo("briefing") })
            }

            val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
            val isGuest = !isLoggedIn
            val syncError by viewModel.lastSyncError.collectAsStateWithLifecycle()
            if (syncError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .background(ErrorRed.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SyncProblem,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val syncErrorMessage = syncError ?: ""
                    Text(
                        text = stringResource(R.string.sync_failed, syncErrorMessage),
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.lastSyncError.value = null },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_dismiss), tint = ErrorRed, modifier = Modifier.size(14.dp))
                    }
                }
            } else if (isGuest) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .background(ButterSoft.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentDeep,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.guest_mode_banner),
                        color = AccentDeep,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { viewModel.navigateTo("settings") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(stringResource(R.string.auth_sign_in), color = AccentDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📝", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.no_notes), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(stringResource(R.string.empty_state_hint), fontSize = 14.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.createEmptyNote() },
                            colors = ButtonDefaults.buttonColors(containerColor = ButterYellow),
                            shape = RoundedCornerShape(9999.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.create_first_note), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            } else if (!isGrid) {
                // List row details
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteListRow(note = note, onClick = { viewModel.selectActiveNote(note.id) }, viewModel = viewModel, modifier = Modifier.animateItem())
                    }
                }
            } else {
                // Grid/Masonry card layout with lazy loading
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteGridCard(note = note, onClick = { viewModel.selectActiveNote(note.id) }, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) ButterSoft else SurfaceTertiary)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (selected) "$title, selected" else title
                role = Role.Button
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) AccentDeep else TextSecondary
        )
    }
}

@Composable
fun NoteListRow(note: Note, onClick: () -> Unit, viewModel: NoteViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (note.title.isBlank()) stringResource(R.string.note_untitled) else note.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val formattedDate = remember(note.updatedAt) {
                val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                sdf.format(Date(note.updatedAt))
            }
            Text(formattedDate, fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(start = 8.dp))
            IconButton(
                onClick = { viewModel.toggleNotePin(note.id) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Default.BookmarkAdd,
                    contentDescription = if (note.isPinned) stringResource(R.string.note_unpin) else stringResource(R.string.note_pin),
                    tint = if (note.isPinned) AccentDeep else TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (note.body.isBlank()) stringResource(R.string.note_empty_body) else note.body.replace("\n", " "),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            )
            if (note.tags.isNotEmpty()) {
                val firstTag = note.tags.split(",").firstOrNull() ?: ""
                Box(
                    modifier = Modifier
                        .background(ButterSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(firstTag.uppercase(), fontSize = 9.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteGridCard(note: Note, onClick: () -> Unit, viewModel: NoteViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (note.imageUrl != null) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(note.imageUrl)
                        .size(600, 400)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.5f)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (note.title.isBlank()) stringResource(R.string.note_untitled) else note.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.toggleNotePin(note.id) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Default.BookmarkAdd,
                        contentDescription = if (note.isPinned) stringResource(R.string.note_unpin) else stringResource(R.string.note_pin),
                        tint = if (note.isPinned) AccentDeep else TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Checklist state preview inside grid card
            if (note.checklistJson != null) {
                val array = remember(note.checklistJson) { JSONArray(note.checklistJson) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0 until minOf(array.length(), 3)) {
                        val obj = array.getJSONObject(i)
                        val text = obj.optString("text")
                        val checked = obj.optBoolean("checked")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (checked) Icons.Default.Check else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (checked) AccentDeep else TextTertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (checked) TextSecondary else TextPrimary,
                                textDecoration = if (checked) TextDecoration.LineThrough else null
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = if (note.body.isBlank()) stringResource(R.string.note_empty_body) else note.body,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (note.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    note.tags.split(",").filter { it.isNotBlank() }.map { it.trim() }.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(ButterSoft, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(tag.uppercase(), fontSize = 8.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            val formattedDate = remember(note.updatedAt) {
                val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                sdf.format(Date(note.updatedAt))
            }
            Text(
                text = formattedDate,
                fontSize = 11.sp,
                color = TextTertiary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ----------------------------------------------------
// 7. NOTE EDITOR SCREEN
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(viewModel: NoteViewModel) {
    val note by viewModel.activeNote.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) } // Ask NoteAi Chat Sheet
    var voiceSheetOpen by remember { mutableStateOf(false) } // Voice Dictator

    androidx.activity.compose.BackHandler {
        viewModel.navigateTo("home_list")
    }

    if (note == null) return

    val focusManager = LocalFocusManager.current
    var title by remember(note?.id) { mutableStateOf(note?.title ?: "") }
    var body by remember(note?.id) { mutableStateOf(note?.body ?: "") }
    var tags by remember(note?.id) { mutableStateOf(note?.tags ?: "") }

    // Auto-save debounced handler
    LaunchedEffect(title, body, tags) {
        viewModel.updateActiveNote(title, body, tags, note?.checklistJson)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("home_list") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.generateTitleFromContent() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.ai_suggest_title))
                    }
                    IconButton(onClick = { viewModel.deleteActiveNote() }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfacePrimary)
            )
        },
        bottomBar = {
            FloatingDock(
                activeTab = "home",
                onHomeClick = { viewModel.navigateTo("home_list") },
                onAddClick = { viewModel.createEmptyNote() },
                onProfileClick = { viewModel.navigateTo("settings") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfacePrimary)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                // Time updated tag
                val diff = System.currentTimeMillis() - (note?.updatedAt ?: 0)
                val formattedUpdate = if (diff < 60000) {
                    stringResource(R.string.edited_just_now)
                } else {
                    stringResource(R.string.edited_minutes_ago, diff / 60000)
                }
                Text(formattedUpdate, fontSize = 11.sp, color = TextTertiary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(10.dp))

                // Editable Title
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (title.isEmpty()) {
                            Text("Q4 planning meeting", fontSize = 28.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        }
                        innerTextField()
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Editable Body
                BasicTextField(
                    value = body,
                    onValueChange = { body = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        lineHeight = 24.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 120.dp),
                    decorationBox = { innerTextField ->
                        if (body.isEmpty()) {
                            Text("Lorem ipsum dolor sit amet, consectetur adipiscing elit...", fontSize = 16.sp, color = TextTertiary)
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Checklist Tasks Extracted Render
                if (note?.checklistJson != null) {
                    Text(stringResource(R.string.key_decisions), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))

                    val array = remember(note?.checklistJson) { JSONArray(note?.checklistJson) }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val text = obj.optString("text")
                            val checked = obj.optBoolean("checked")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleChecklistItem(note!!.id, i) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(2.dp, if (checked) SuccessSage else BorderStrong, RoundedCornerShape(4.dp))
                                        .background(if (checked) SuccessSage else Color.Transparent, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (checked) {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = text,
                                    fontSize = 16.sp,
                                    color = if (checked) TextSecondary else TextPrimary,
                                    textDecoration = if (checked) TextDecoration.LineThrough else null
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Inline audio visually if audioDuration exists
                if (note?.audioDuration != null) {
                    VoiceMemoViewBlock(duration = note?.audioDuration ?: "")
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Inline scanned image visualization
                if (note?.imageUrl != null) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(note?.imageUrl)
                            .size(800, 360)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Mini Smart Format Floating Toolbar & AI trigger options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isAiThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
                    ActionSuggestButton(
                        title = stringResource(R.string.ai_tags),
                        icon = Icons.Default.Sell,
                        isThinking = isAiThinking,
                        onClick = { viewModel.autoTagActiveNote() }
                    )
                    ActionSuggestButton(
                        title = stringResource(R.string.summarize),
                        icon = Icons.Default.Description,
                        isThinking = isAiThinking,
                        onClick = {
                            viewModel.summarizeActiveNote()
                            sheetOpen = true
                        }
                    )
                    ActionSuggestButton(
                        title = stringResource(R.string.task_checklist),
                        icon = Icons.Default.Task,
                        isThinking = isAiThinking,
                        onClick = { viewModel.extractTasksFromActiveNote() }
                    )
                    IconButton(onClick = { voiceSheetOpen = true }) {
                        Icon(Icons.Default.Person, contentDescription = stringResource(R.string.voice_dictate), tint = ButterYellow)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tags chips (FlowRow to enable wrapping list flow)
                var showTagInput by remember { mutableStateOf(false) }
                var newTagInput by remember { mutableStateOf("") }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.split(",").filter { it.isNotBlank() }.map { it.trim() }.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(ButterSoft, RoundedCornerShape(20.dp))
                                .clickable {
                                    // Remove tag on tap
                                    val tagList = tags.split(",").map { it.trim() }.toMutableList()
                                    tagList.remove(tag)
                                    tags = tagList.filter { it.isNotBlank() }.joinToString(",")
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(AccentDeep, CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tag, fontSize = 12.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Close, null, tint = AccentDeep.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    // Add tag button
                    IconButton(onClick = { showTagInput = !showTagInput }) {
                        Icon(
                            if (showTagInput) Icons.Default.Close else Icons.Default.Add,
                            null, tint = TextTertiary, modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Inline tag input (shown on + tap)
                AnimatedVisibility(visible = showTagInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = newTagInput,
                            onValueChange = { if (it.length <= 20) newTagInput = it },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                color = AccentDeep,
                                fontWeight = FontWeight.Medium
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .background(SurfaceTertiary, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    if (newTagInput.isEmpty()) {
                                        Text(stringResource(R.string.add_tag_placeholder), fontSize = 13.sp, color = TextTertiary)
                                    }
                                    innerTextField()
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val trimmed = newTagInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    tags = if (tags.isEmpty()) trimmed else "$tags,$trimmed"
                                    newTagInput = ""
                                    showTagInput = false
                                }
                            })
                        )
                    }
                }

                Spacer(modifier = Modifier.height(64.dp))
            }

            // Quick trigger "Ask NoteAi" overlay floating button
            IconButton(
                onClick = { sheetOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp)
                    .size(56.dp)
                    .background(ButterYellow, CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.ask_noteai), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    // Modal bottoms overlays inside box for high fidelity layout
    if (sheetOpen) {
        AskNoteAiChatOverlay(viewModel = viewModel, onClose = { sheetOpen = false })
    }
    
    if (voiceSheetOpen) {
        VoiceNoteDictatorOverlay(viewModel = viewModel, onClose = { voiceSheetOpen = false })
    }
}

@Composable
fun ActionSuggestButton(
    title: String,
    icon: ImageVector,
    isThinking: Boolean = false,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by if (isThinking) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceTertiary.copy(alpha = alpha)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentDeep,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(title, color = AccentDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VoiceMemoViewBlock(duration: String) {
    var isPlaying by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(SurfaceTertiary, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier
                .size(32.dp)
                .background(ButterSoft, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = AccentDeep,
                modifier = Modifier.size(20.dp)
            )
        }

        // Animated speech amplitude visualizer
        Row(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val h = listOf(12, 18, 14, 24, 16, 20, 12, 22, 14, 18, 10, 20, 14)
            val randomOffsets = remember { h.map { it + (0..10).random() } }
            h.forEachIndexed { index, heightVal ->
                val scaleHeight = animateDpAsState(
                    targetValue = if (isPlaying) randomOffsets[index].dp else heightVal.dp,
                    animationSpec = tween(300)
                )
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(scaleHeight.value)
                        .background(ButterSoft, CircleShape)
                )
            }
        }

        Text(duration, fontSize = 12.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
    }
}

// ----------------------------------------------------
// 8. VOICE RECORDER BOTTOM SHEET
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceNoteDictatorOverlay(viewModel: NoteViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val seconds by viewModel.recordingSeconds.collectAsStateWithLifecycle()
    val transcript by viewModel.liveTranscript.collectAsStateWithLifecycle()

    val speechHelper = remember {
        com.example.data.SpeechRecognitionHelper(
            context = context,
            onResult = { text -> viewModel.updateLiveTranscript(text) },
            onError = { error ->
                android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
            },
            onPartial = { text -> viewModel.appendPartialTranscript(text) }
        )
    }

    DisposableEffect(Unit) {
        onDispose { speechHelper.cancel() }
    }

    ModalBottomSheet(
        onDismissRequest = {
            speechHelper.stop()
            viewModel.stopDictation()
            onClose()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).background(BorderStrong, CircleShape))
            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.voice_note), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))

            // transcript rendering scroll box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Column {
                    Text(transcript, fontSize = 16.sp, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    if (isRecording) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.listening), fontSize = 11.sp, color = TextTertiary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(modifier = Modifier.size(4.dp).background(ButterYellow, CircleShape))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(viewModel.getDurationString(seconds), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    speechHelper.stop()
                    viewModel.stopDictation()
                    onClose()
                }) {
                    Text(stringResource(R.string.cancel), color = AccentDeep, fontWeight = FontWeight.Bold)
                }

                // Large record button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(ButterSoft, CircleShape)
                        .clickable {
                            if (isRecording) {
                                speechHelper.stop()
                                viewModel.stopDictation()
                            } else {
                                viewModel.startDictation()
                                speechHelper.start()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isRecording) 24.dp else 48.dp)
                            .background(ButterYellow, if (isRecording) RoundedCornerShape(4.dp) else CircleShape)
                    )
                }

                TextButton(onClick = {
                    speechHelper.stop()
                    viewModel.saveDictationAsNote()
                    onClose()
                }) {
                    Text(stringResource(R.string.done), color = AccentDeep, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ----------------------------------------------------
// 9. DAILY BRIEFING SCREEN
// ----------------------------------------------------
@Composable
fun DailyBriefingScreen(viewModel: NoteViewModel, authViewModel: AuthViewModel) {
    androidx.activity.compose.BackHandler {
        viewModel.navigateTo("home_list")
    }

    val notes by viewModel.allNotes.collectAsStateWithLifecycle()

    // Derive greeting + user display name from Firebase Auth + time of day.
    val user = authViewModel.currentUser
    val userName = remember(user) {
        user?.displayName?.substringBefore(" ")
            ?: user?.email?.substringBefore("@")
            ?: "there"
    }
    val greetingResId = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }

    // Recent notes edited in the last 7 days, most recent first.
    val recentNotes = remember(notes) {
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        notes
            .filter { it.updatedAt >= sevenDaysAgo && (it.title.isNotBlank() || it.body.isNotBlank()) }
            .sortedByDescending { it.updatedAt }
            .take(5)
    }

    // Open action items: unchecked checklist entries from any note.
    val untitledLabel = stringResource(R.string.untitled_note)
    val openActions = remember(notes) {
        data class OpenAction(val noteId: Long, val text: String, val noteTitle: String)
        val list = mutableListOf<OpenAction>()
        for (note in notes) {
            val json = note.checklistJson ?: continue
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (!obj.optBoolean("checked")) {
                        val text = obj.optString("text").trim()
                        if (text.isNotEmpty()) {
                            list += OpenAction(
                                noteId = note.id,
                                text = text,
                                noteTitle = note.title.ifBlank { untitledLabel }
                            )
                        }
                    }
                }
            } catch (_: Exception) { /* ignore malformed JSON */ }
            if (list.size >= 6) break
        }
        list.take(6)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateTo("home_list") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_go_back), tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.daily_briefing), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfacePrimary)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text("${stringResource(greetingResId)}, $userName", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()),
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Highlights row — recent notes edited in the last 7 days.
            Text(stringResource(R.string.recent_highlights), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (recentNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .width(240.dp)
                            .height(140.dp)
                            .background(SurfaceTertiary, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_recent_notes), fontSize = 12.sp, color = TextTertiary)
                    }
                } else {
                    recentNotes.forEach { note ->
                        Box(
                            modifier = Modifier
                                .width(240.dp)
                                .height(140.dp)
                                .background(SurfaceElevated, RoundedCornerShape(16.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                .clickable { viewModel.selectActiveNote(note.id) }
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                                Column {
                                    val tag = note.tags.split(",").firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: "note"
                                    Box(
                                        modifier = Modifier
                                            .background(SurfaceTertiary, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(tag, fontSize = 9.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = note.title.ifBlank { stringResource(R.string.note_untitled) },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(note.updatedAt)),
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Open action items — unchecked checklist entries pulled from user notes.
            Text(stringResource(R.string.open_action_items), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (openActions.isEmpty()) {
                        Text(
                            stringResource(R.string.no_action_items),
                            fontSize = 13.sp,
                            color = TextTertiary
                        )
                    } else {
                        openActions.forEach { action ->
                            ActionCheckRow(
                                text = action.text,
                                subtitle = action.noteTitle,
                                onToggle = { viewModel.toggleChecklistItemByText(action.noteId, action.text) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
fun ActionCheckRow(
    text: String,
    subtitle: String,
    onToggle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
                .border(2.dp, BorderStrong, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = BorderStrong,
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text, fontSize = 15.sp, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextTertiary)
        }
    }
}

// ----------------------------------------------------
// 10. ASK NOTEAI CHAT OVERLAY BOTTOM SHEET
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskNoteAiChatOverlay(viewModel: NoteViewModel, onClose: () -> Unit) {
    val history by viewModel.chatHistory.collectAsStateWithLifecycle()
    val thinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
    var inputMessage by remember { mutableStateOf("") }
    var pendingMessageToSend by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingMessageToSend) {
        val msg = pendingMessageToSend
        if (msg != null && msg.isNotBlank()) {
            viewModel.isAiThinking.value = true
            viewModel.speakToAI(msg)
            pendingMessageToSend = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        // Chat Sheet layout container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).background(BorderStrong, CircleShape))
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.ask_noteai), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(stringResource(R.string.answers_from_library), fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null, tint = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
            Spacer(modifier = Modifier.height(12.dp))

            // Message stream
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (history.isEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.ask_any_question), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            // Suggestion chips
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                ChatSuggestChip(stringResource(R.string.suggest_summarize_week)) {
                                    viewModel.speakToAI("Summarize my notes from this week")
                                }
                                ChatSuggestChip(stringResource(R.string.suggest_prioritize_today)) {
                                    viewModel.speakToAI("What should I prioritize today?")
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(history, key = { index, _ -> index }) { _, message ->
                        if (message.sender == "user") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Box(
                                    modifier = Modifier
                                        .background(ButterSoft, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 0.dp))
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                        .widthIn(max = 240.dp)
                                ) {
                                    Text(message.content, fontSize = 15.sp, color = TextPrimary)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(6.dp)
                                        .background(ButterYellow, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(SurfaceTertiary, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 20.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .widthIn(max = 280.dp)
                                ) {
                                    Text(message.content, fontSize = 14.sp, color = TextPrimary)
                                }
                            }
                        }
                    }

                    if (thinking) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(ButterYellow, CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.thinking_status), fontSize = 11.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Message keyboard input row
            val isSendingMessage = thinking || pendingMessageToSend != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Attach file simulated
                IconButton(
                    onClick = { },
                    modifier = Modifier
                        .size(40.dp)
                        .background(SurfaceTertiary, CircleShape)
                ) {
                    Icon(Icons.Default.Add, null, tint = TextSecondary)
                }

                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { if (!isSendingMessage) inputMessage = it },
                    placeholder = { Text(stringResource(R.string.ask_placeholder), color = TextTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ButterYellow,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = SurfaceTertiary,
                        unfocusedContainerColor = SurfaceTertiary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isSendingMessage,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputMessage.isNotBlank() && !isSendingMessage) {
                            pendingMessageToSend = inputMessage
                            inputMessage = ""
                        }
                    })
                )

                // Large yellow send button
                IconButton(
                    onClick = {
                        if (inputMessage.isNotBlank() && !isSendingMessage) {
                            pendingMessageToSend = inputMessage
                            inputMessage = ""
                        }
                    },
                    enabled = inputMessage.isNotBlank() && !isSendingMessage,
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (inputMessage.isNotBlank() && !isSendingMessage) ButterYellow else SurfaceTertiary, CircleShape)
                ) {
                    if (isSendingMessage) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.cd_send),
                            tint = if (inputMessage.isNotBlank()) MaterialTheme.colorScheme.onPrimary else TextTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatSuggestChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

// ----------------------------------------------------
// 11. SEARCH SCREEN
// ----------------------------------------------------
@Composable
fun SearchScreen(viewModel: NoteViewModel) {
    androidx.activity.compose.BackHandler {
        viewModel.navigateTo("home_list")
    }

    var query by remember { mutableStateOf("") }
    val notes by viewModel.filteredNotes.collectAsStateWithLifecycle()
    val filterChip by viewModel.filterChip.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateTo("home_list") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_go_back))
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.searchQuery.value = it
                    },
                    placeholder = { Text(stringResource(R.string.search_placeholder), color = TextTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ButterYellow,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = SurfaceTertiary,
                        unfocusedContainerColor = SurfaceTertiary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = TextTertiary)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                viewModel.searchQuery.value = ""
                            }) {
                                Icon(Icons.Default.Close, null, tint = TextTertiary)
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfacePrimary)
                .padding(innerPadding)
        ) {
            // Category scroll filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(stringResource(R.string.filter_all), filterChip == "All", onClick = { viewModel.filterChip.value = "All" })
                CategoryChip(stringResource(R.string.search_chip_notes), filterChip == "Ideas", onClick = { viewModel.filterChip.value = "Ideas" })
                CategoryChip(stringResource(R.string.search_chip_tasks), filterChip == "Action items", onClick = { viewModel.filterChip.value = "Action items" })
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lists entries
            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.no_results_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(stringResource(R.string.no_results_hint), fontSize = 12.sp, color = TextSecondary)
                    }
                }
            } else {
                Text(stringResource(R.string.recent), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectActiveNote(note.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (note.title.isBlank()) stringResource(R.string.note_untitled) else note.title,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 12. SETTINGS SCREEN
// ----------------------------------------------------
@Composable
fun SegmentedThemeSelector(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit
) {
    val options = listOf("Light", "Dark", "Auto")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(SurfaceTertiary, RoundedCornerShape(9999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = selectedTheme == option
            val backgroundColor = if (isSelected) ButterYellow else Color.Transparent
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else TextSecondary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(backgroundColor, RoundedCornerShape(9999.dp))
                    .clickable { onThemeSelected(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun SegmentedLanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    val options = listOf("English", "Bahasa Indonesia")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(SurfaceTertiary, RoundedCornerShape(9999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = selectedLanguage == option
            val backgroundColor = if (isSelected) ButterYellow else Color.Transparent
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else TextSecondary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(backgroundColor, RoundedCornerShape(9999.dp))
                    .clickable { onLanguageSelected(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: NoteViewModel, authViewModel: AuthViewModel) {
    androidx.activity.compose.BackHandler {
        viewModel.navigateTo("home_list")
    }

    val context = LocalContext.current
    val isOffline by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val dateFormat by viewModel.dateFormatting.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    // Derive current user display info from Firebase Auth state only.
    val user = authViewModel.currentUser
    val accountName = remember(user) {
        val email = user?.email ?: ""
        if (email.isBlank()) null else email.substringBefore("@")
    } ?: stringResource(R.string.guest_user)
    val accountEmail = remember(user) {
        val email = user?.email ?: ""
        if (email.isBlank()) null else email
    } ?: stringResource(R.string.guest_mode_desc)
    val planLabel = remember(user) {
        when {
            user == null -> false
            user.isAnonymous -> false
            else -> true
        }
    }
    val planText = if (planLabel) stringResource(R.string.pro_plan) else stringResource(R.string.guest_plan)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        bottomBar = {
            FloatingDock(
                activeTab = "profile",
                onHomeClick = { viewModel.navigateTo("home_list") },
                onAddClick = { viewModel.createEmptyNote() },
                onProfileClick = { viewModel.navigateTo("settings") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfacePrimary)
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateTo("home_list") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_go_back))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(androidx.compose.ui.res.stringResource(id = R.string.settings), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            // User Info Account Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceTertiary, RoundedCornerShape(16.dp))
                    .clickable { }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(ButterSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(accountName.take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AccentDeep)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(accountName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.background(ButterSoft, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(planText, fontSize = 10.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(accountEmail, fontSize = 12.sp, color = TextSecondary)
                }
                Icon(Icons.Filled.KeyboardArrowRight, null, tint = TextTertiary)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // PREFERENCES
            SettingsSectionHeader(stringResource(R.string.preferences))
            SettingsRowToggle(androidx.compose.ui.res.stringResource(id = R.string.offline_mode), checked = isOffline, onToggle = { viewModel.setOfflineMode(it) })
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(androidx.compose.ui.res.stringResource(id = R.string.language), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
            SegmentedLanguageSelector(selectedLanguage = currentLanguage) {
                viewModel.setLanguage(it)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(androidx.compose.ui.res.stringResource(id = R.string.theme), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
            SegmentedThemeSelector(selectedTheme = appTheme) {
                viewModel.setAppTheme(it)
            }

            Spacer(modifier = Modifier.height(16.dp))
            SettingsRowClick(stringResource(R.string.date_format), dateFormat) {
                viewModel.setDateFormat(if (dateFormat == "DD/MM/YYYY") "MM/DD/YYYY" else "DD/MM/YYYY")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SYNC & STORAGE
            SettingsSectionHeader(stringResource(R.string.sync_storage))
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.storage_used), fontSize = 14.sp, color = TextPrimary)
                    Text(stringResource(R.string.storage_value), fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                val animatedProgress by animateFloatAsState(
                    targetValue = 0.24f,
                    animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                    label = "storageProgress"
                )
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(SurfaceTertiary, CircleShape)) {
                    Box(modifier = Modifier.fillMaxWidth(animatedProgress).height(6.dp).background(ButterYellow, CircleShape))
                }
            }
            val exportMsg = stringResource(R.string.notes_exported)
            SettingsRowAction(stringResource(R.string.export_notes)) {
                Toast.makeText(context, exportMsg, Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI SETTINGS
            SettingsSectionHeader(stringResource(R.string.ai_configurations))
            SettingsRowClick(stringResource(R.string.model), selectedModel) {
                val next = viewModel.availableModels
                val idx = next.indexOf(selectedModel).coerceAtLeast(0)
                viewModel.setSelectedModel(next[(idx + 1) % next.size])
            }

            // Gemini API Key Input
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(stringResource(R.string.gemini_api_key), fontSize = 14.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                var apiKeyInput by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
                var showApiKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.api_key_placeholder), fontSize = 12.sp, color = TextTertiary) },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.Check else Icons.Default.Info,
                                contentDescription = stringResource(R.string.cd_toggle_visibility),
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ButterYellow,
                        unfocusedBorderColor = BorderSubtle
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.get_free_key),
                        fontSize = 11.sp,
                        color = AccentDeep,
                        textDecoration = TextDecoration.Underline
                    )
                    val apiKeySavedMsg = stringResource(R.string.api_key_saved)
                    Button(
                        onClick = {
                            viewModel.setGeminiApiKey(apiKeyInput.trim())
                            Toast.makeText(context, apiKeySavedMsg, Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ButterYellow),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                        enabled = apiKeyInput.isNotBlank()
                    ) {
                        Text(stringResource(R.string.save), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (apiKeyInput.isNotBlank()) TextPrimary else TextTertiary)
                    }
                }
                if (geminiApiKey.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.key_format, geminiApiKey.takeLast(6)),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(stringResource(R.string.data_usage), fontSize = 14.sp, color = TextPrimary)
                Text(stringResource(R.string.data_usage_hint), fontSize = 11.sp, color = TextSecondary)
            }
            val tagsRegenMsg = stringResource(R.string.tags_regenerated)
            SettingsRowAction(stringResource(R.string.regenerate_tags)) {
                Toast.makeText(context, tagsRegenMsg, Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Logout & Legal
            Button(
                onClick = {
                    authViewModel.signOut {
                        viewModel.navigateTo("login")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(androidx.compose.ui.res.stringResource(id = R.string.log_out), color = ErrorRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.terms_legal),
                fontSize = 11.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp)
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun SettingsRowToggle(title: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = ButterYellow)
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
}

@Composable
fun SettingsRowClick(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, color = TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 14.sp, color = TextSecondary)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.KeyboardArrowRight, null, modifier = Modifier.size(16.dp), tint = TextSecondary)
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
}

@Composable
fun SettingsRowAction(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, color = TextPrimary)
        Icon(Icons.Filled.KeyboardArrowRight, null, modifier = Modifier.size(16.dp), tint = TextSecondary)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
}

// ----------------------------------------------------
// 13. WHITEBOARD OCR / Vision SCANNER SCREEN
// ----------------------------------------------------
@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun WhiteboardOcrScreen(viewModel: NoteViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val capturedPath by viewModel.capturedImagePath.collectAsStateWithLifecycle()
    val imageOcrText by viewModel.imageOcrText.collectAsStateWithLifecycle()
    val imageDescription by viewModel.imageDescription.collectAsStateWithLifecycle()
    val isVisionLoading by viewModel.isVisionLoading.collectAsStateWithLifecycle()

    var showCameraPreview by remember { mutableStateOf(capturedPath == null) }
    var flashOn by remember { mutableStateOf(false) }

    // CameraX instance references
    val cameraProviderFuture = remember { androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context) }
    var imageCapture: androidx.camera.core.ImageCapture? by remember { mutableStateOf(null) }
    val cameraPermissionState = com.google.accompanist.permissions.rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    // Launcher for photo gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    val tempFile = java.io.File(context.filesDir, "images/GALLERY_${System.currentTimeMillis()}.jpg")
                    tempFile.parentFile?.mkdirs()
                    tempFile.writeBytes(bytes)
                    viewModel.processCapturedImage(bytes, tempFile.absolutePath)
                    showCameraPreview = false
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.error_reading_image, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (capturedPath == null) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.navigateTo("home_list") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_go_back), tint = TextPrimary)
                }
                Text(stringResource(R.string.ai_scanner), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = {
                    showCameraPreview = true
                    viewModel.capturedImagePath.value = null
                    viewModel.imageOcrText.value = ""
                    viewModel.imageDescription.value = ""
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_reset_camera), tint = TextPrimary)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showCameraPreview) {
                if (cameraPermissionState.status == com.google.accompanist.permissions.PermissionStatus.Granted) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Camera viewfinder
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                val previewView = androidx.camera.view.PreviewView(ctx)
                                val executor = androidx.core.content.ContextCompat.getMainExecutor(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = androidx.camera.core.Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    imageCapture = androidx.camera.core.ImageCapture.Builder()
                                        .setFlashMode(if (flashOn) androidx.camera.core.ImageCapture.FLASH_MODE_ON else androidx.camera.core.ImageCapture.FLASH_MODE_OFF)
                                        .build()

                                    val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview,
                                            imageCapture
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("CameraPreviewView", "UseCase binding failed", e)
                                    }
                                }, executor)
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Central guide layout frame
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(0.85f)
                                .height(260.dp)
                                .border(2.dp, ButterYellow, RoundedCornerShape(16.dp))
                        )

                        // Hoisted string templates for camera callbacks (non-composable context)
                        val errorCapturingTemplate = stringResource(R.string.error_capturing_image)
                        val cameraCaptureErrorTemplate = stringResource(R.string.camera_capture_error)

                        // Bottom visual dock for shutter triggers
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(vertical = 24.dp, horizontal = 32.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Gallery Option
                            IconButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = stringResource(R.string.cd_gallery), tint = Color.White)
                            }

                            // Capture Shutter Button
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .background(Color.White, CircleShape)
                                    .border(4.dp, ButterYellow, CircleShape)
                                    .clickable {
                                        val capture = imageCapture
                                        if (capture != null) {
                                            val photoFile = java.io.File(
                                                context.filesDir,
                                                "images/IMG_${System.currentTimeMillis()}.jpg"
                                            )
                                            photoFile.parentFile?.mkdirs()
                                            val outputOptions = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                            
                                            capture.takePicture(
                                                outputOptions,
                                                androidx.core.content.ContextCompat.getMainExecutor(context),
                                                object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                                                    override fun onImageSaved(outputFileResults: androidx.camera.core.ImageCapture.OutputFileResults) {
                                                        scope.launch {
                                                            try {
                                                                val bytes = photoFile.readBytes()
                                                                viewModel.processCapturedImage(bytes, photoFile.absolutePath)
                                                                showCameraPreview = false
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, errorCapturingTemplate.replace("%s", e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                                        Toast.makeText(context, cameraCaptureErrorTemplate.replace("%s", exception.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(28.dp).background(ButterYellow, CircleShape))
                            }

                            // Flash Toggle Icon
                            IconButton(
                                onClick = { flashOn = !flashOn },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(if (flashOn) ButterYellow else Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = stringResource(R.string.cd_flash_toggle), tint = Color.White)
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.camera_disabled), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                                Text(stringResource(R.string.grant_camera), color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            } else {
                // REVIEW/PREVIEW RESULT LAYOUT
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    // Show captured image preview
                    if (capturedPath != null) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                .data(capturedPath)
                                .size(800, 440)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.cd_captured_image),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Progress Loader / Shimmer
                    if (isVisionLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = ButterYellow, modifier = Modifier.size(36.dp))
                        }
                    }

                    // OCR selectable text card
                    Text(stringResource(R.string.extracted_text), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceTertiary, RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = if (imageOcrText.isEmpty()) stringResource(R.string.scanning_placeholder) else imageOcrText,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Image assistant description text block
                    if (imageDescription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.image_description_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ButterSoft, RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = imageDescription,
                                fontSize = 14.sp,
                                color = AccentDeep
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Hoisted action messages (non-composable context inside onClick)
                    val savedScanMsg = stringResource(R.string.saved_scan)
                    val copiedMsg = stringResource(R.string.copied_clipboard)
                    val searchingMsg = stringResource(R.string.searching_related)

                    // Column of modern 44dp capsule rounded actionable buttons
                    Button(
                        onClick = {
                            if (imageOcrText.isNotEmpty()) {
                                viewModel.saveOcrImageAsNote()
                                Toast.makeText(context, savedScanMsg, Toast.LENGTH_SHORT).show()
                                viewModel.navigateTo("home_list")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ButterYellow),
                        shape = RoundedCornerShape(9999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(stringResource(R.string.save_as_note), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(imageOcrText))
                                Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceTertiary),
                            shape = RoundedCornerShape(9999.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(stringResource(R.string.copy_text), color = TextPrimary, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                val path = capturedPath
                                if (path != null) {
                                    val file = java.io.File(path)
                                    if (file.exists()) {
                                        viewModel.describeCapturedImage(file.readBytes())
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceTertiary),
                            shape = RoundedCornerShape(9999.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(stringResource(R.string.describe_image), color = TextPrimary, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val path = capturedPath
                            if (path != null) {
                                val file = java.io.File(path)
                                if (file.exists()) {
                                    viewModel.searchRelatedNotes(file.readBytes())
                                    Toast.makeText(context, searchingMsg, Toast.LENGTH_LONG).show()
                                    viewModel.navigateTo("home_list")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceTertiary),
                        shape = RoundedCornerShape(9999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.search_related_notes), color = TextPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// SHARED COMPONENTS
// ----------------------------------------------------
@Composable
fun FloatingDock(
    activeTab: String,
    onHomeClick: () -> Unit,
    onAddClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(9999.dp),
            color = SurfaceElevated,
            modifier = Modifier
                .width(280.dp)
                .height(64.dp)
                .border(1.dp, BorderSubtle, RoundedCornerShape(999.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = { onHomeClick() })
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = stringResource(R.string.cd_home),
                        tint = if (activeTab == "home") ButterYellow else TextSecondary
                    )
                    if (activeTab == "home") {
                        Box(modifier = Modifier.padding(top = 2.dp).size(4.dp).background(ButterYellow, CircleShape))
                    }
                }

                // Floating Add FAB
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(ButterYellow, CircleShape)
                        .clickable(onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onAddClick()
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                }

                // Settings/Profile
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = { onProfileClick() })
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.cd_profile),
                        tint = if (activeTab == "profile") ButterYellow else TextSecondary
                    )
                    if (activeTab == "profile") {
                        Box(modifier = Modifier.padding(top = 2.dp).size(4.dp).background(ButterYellow, CircleShape))
                    }
                }
            }
        }
    }
}
