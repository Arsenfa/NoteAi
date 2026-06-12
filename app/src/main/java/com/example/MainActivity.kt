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
            val noteViewModel: NoteViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return NoteViewModel(repository, context.applicationContext) as T
                }
            })

            val authRepository = remember { AuthRepository() }
            val authViewModel: AuthViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AuthViewModel(authRepository, context.applicationContext) as T
                }
            })

            val currentTheme by noteViewModel.appTheme.collectAsState()
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

@Composable
fun MainAppHost(viewModel: NoteViewModel, authViewModel: AuthViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isLoggedIn by authViewModel.isLoggedInFlow.collectAsState(initial = false)
    
    // Simple state-driven cross-fade simulator
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (currentScreen) {
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
            "briefing" -> DailyBriefingScreen(viewModel)
            "search" -> SearchScreen(viewModel)
            "settings" -> SettingsScreen(viewModel, authViewModel)
            "ocr" -> WhiteboardOcrScreen(viewModel)
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
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "note",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "ai",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = ButterYellow
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Think in notes.",
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
            .background(Color.White)
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
                Text("Skip", color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Phone Frame Mockup
        Box(
            modifier = Modifier
                .width(240.dp)
                .height(380.dp)
                .border(6.dp, Color(0xFF1A1A1A), RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFF9F9F8))
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
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(10.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Inset Note
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Meeting notes — Q4 planning",
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
                            Text("WORK", fontSize = 9.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color(0xFFEDEDED), CircleShape))
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth(0.7f).height(4.dp).background(Color(0xFFEDEDED), CircleShape))

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(ButterYellow, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(10.dp), tint = Color.White)
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
                        .border(1.dp, Color(0x338B5CF6), RoundedCornerShape(20.dp))
                        .background(Color(0xFFF0F4FF))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✨ Summarize discussion", fontSize = 10.sp, color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        Text("Capture anything", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Text, voice, photos, checklists — all in one calm place.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.weight(0.5f))

        // Pager Indicators
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(8.dp).background(ButterYellow, CircleShape))
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFE2E2D9), CircleShape))
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFE2E2D9), CircleShape))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.navigateTo("onboarding2") },
            colors = ButtonDefaults.buttonColors(containerColor = ButterYellow),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("CONTINUE", color = TextPrimary, fontWeight = FontWeight.Bold)
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
            .background(Color.White)
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
                Text("Skip", color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Phone Frame Mockup
        Box(
            modifier = Modifier
                .width(240.dp)
                .height(380.dp)
                .border(6.dp, Color(0xFF1A1A1A), RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFF9F9F8))
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
                    Box(modifier = Modifier.border(1.dp, Color(0x1F000000), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("work", fontSize = 9.sp, color = TextSecondary)
                    }
                    Box(modifier = Modifier.border(1.dp, Color(0x1F000000), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("ideas", fontSize = 9.sp, color = TextSecondary)
                    }
                    Box(modifier = Modifier.background(Color(0xFFD0E4E2), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("meeting", fontSize = 9.sp, color = Color(0xFF0D1E1E), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("Smart Folders", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                // Folders setup list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnboardingFolderRow("Action items", true)
                    OnboardingFolderRow("Ideas (12 notes)", false)
                    OnboardingFolderRow("Meeting notes (8 notes)", false)
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        Text("Organized by AI", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Smart tags and folders — without you lifting a finger.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.weight(0.5f))

        // Pager Indicators
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFE2E2D9), CircleShape))
            Box(modifier = Modifier.size(8.dp).background(ButterYellow, CircleShape))
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFE2E2D9), CircleShape))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.navigateTo("onboarding3") },
            colors = ButtonDefaults.buttonColors(containerColor = ButterYellow),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("CONTINUE", color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OnboardingFolderRow(title: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x1F000000), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(0xFFF3EFE6), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(12.dp), tint = TextSecondary)
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
            .background(Color.White)
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
                Text("Skip", color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Phone Frame Mockup
        Box(
            modifier = Modifier
                .width(240.dp)
                .height(380.dp)
                .border(6.dp, Color(0xFF1A1A1A), RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFF9F9F8))
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
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFEDEAE3), RoundedCornerShape(16.dp))
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Box(modifier = Modifier.background(ButterSoft, RoundedCornerShape(12.dp)).padding(8.dp)) {
                                Text("Summarize my week", fontSize = 9.sp, color = TextPrimary)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Box(modifier = Modifier.background(SurfaceTertiary, RoundedCornerShape(12.dp)).padding(8.dp)) {
                                Text("✨ Collected 12 research links and identified 3 action items.", fontSize = 9.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        Text("Ask your notes anything", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "A quiet collaborator that summarizes, extracts tasks, and answers from your library.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.weight(0.5f))

        // Pager Indicators
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFE2E2D9), CircleShape))
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFE2E2D9), CircleShape))
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
            Text("GET STARTED", color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

// ----------------------------------------------------
// 5. LOGIN SCREEN
// ----------------------------------------------------
@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun LoginScreen(viewModel: NoteViewModel, authViewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }
    
    // Error States
    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var confirmPasswordError by remember { mutableStateOf("") }
    var generalError by remember { mutableStateOf("") }
    
    var isLocalLoading by remember { mutableStateOf(false) }
    var showGoogleAccountPicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()

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

    // Google Account Picker Dialog
    if (showGoogleAccountPicker) {
        AlertDialog(
            onDismissRequest = { showGoogleAccountPicker = false },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = "https://lh3.googleusercontent.com/COxitS2COVK94UPxoZOFJW5vP-R3g52M1ON06QqfZgHocSOfR97v211xsI93bhg9KGA=s48",
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Sign in with Google", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("to continue to noteai", fontSize = 13.sp, color = TextSecondary)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    // Main Google profile link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showGoogleAccountPicker = false
                                isLocalLoading = true
                                authViewModel.signInWithGoogle("mock_google_token", {
                                    isLocalLoading = false
                                    viewModel.isLoggedIn.value = true
                                    viewModel.navigateTo("home_list")
                                }, {
                                    isLocalLoading = false
                                    generalError = it
                                })
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(ButterYellow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Alfan Januar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("alfanjanuar50@gmail.com", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    
                    HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Add another account option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showGoogleAccountPicker = false
                                isLocalLoading = true
                                authViewModel.signInWithGoogle("mock_google_token_new", {
                                    isLocalLoading = false
                                    viewModel.isLoggedIn.value = true
                                    viewModel.navigateTo("home_list")
                                }, {
                                    isLocalLoading = false
                                    generalError = it
                                })
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp).padding(4.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Use another account", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
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
                text = "note",
                fontSize = 48.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "ai",
                fontSize = 48.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                color = ButterYellow
            )
        }

        // 2. Subtitle Description
        Text(
            text = if (isRegisterMode) "Sign up to sync your notes" else "Sign in to sync your notes",
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
                text = "Email",
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
                    Text("Enter your email", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 15.sp)
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
                text = "Password",
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
                        text = if (showPassword) "Hide" else "Show",
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
                    text = "Confirm Password",
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
                            text = if (showConfirmPassword) "Hide" else "Show",
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "Forgot password?",
                    color = AccentDeep,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Password reset email sent (simulation)", Toast.LENGTH_SHORT).show()
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

                emailError = if (email.isBlank()) "Email cannot be empty" else if (!isEmailValid) "Invalid email format" else ""
                passwordError = if (password.isBlank()) "Password cannot be empty" else if (!isPasswordValid) "Password must be at least 6 characters" else ""

                if (isRegisterMode) {
                    val passwordsMatch = password == confirmPassword
                    confirmPasswordError = if (confirmPassword.isBlank()) "Please confirm your password" else if (!passwordsMatch) "Passwords do not match" else ""
                }

                if (emailError.isNotEmpty() || passwordError.isNotEmpty() || (isRegisterMode && confirmPasswordError.isNotEmpty())) {
                    return@Button
                }

                // AuthViewModel API Submission
                isLocalLoading = true
                generalError = ""
                if (isRegisterMode) {
                    authViewModel.signUpWithEmail(email, password, {
                        isLocalLoading = false
                        viewModel.isLoggedIn.value = true
                        viewModel.navigateTo("home_list")
                    }, {
                        isLocalLoading = false
                        generalError = it
                    })
                } else {
                    authViewModel.signInWithEmail(email, password, {
                        isLocalLoading = false
                        viewModel.isLoggedIn.value = true
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
            if (isLocalLoading || authState is AuthUiState.Loading) {
                CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = if (isRegisterMode) "Sign Up" else "Sign In",
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
                text = "or",
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
                generalError = ""
                showGoogleAccountPicker = true
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
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Continue with Google",
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
                    generalError = ""
                    isLocalLoading = true
                    authViewModel.signInAnonymously({
                        isLocalLoading = false
                        viewModel.isLoggedIn.value = true
                        viewModel.navigateTo("home_list")
                    }, {
                        isLocalLoading = false
                        generalError = it
                    })
                }
        ) {
            Text(
                text = "Try without an account",
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
                text = if (isRegisterMode) "Already have an account? " else "Don't have an account? ",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isRegisterMode) "Sign in" else "Sign up",
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
            text = "By continuing, you agree to our Terms & Privacy Policy",
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
    val notes by viewModel.filteredNotes.collectAsState()
    val filterChip by viewModel.filterChip.collectAsState()
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
                .background(Color.White)
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
                    Text("Good morning", fontSize = 14.sp, color = TextSecondary)
                    Text("Notes", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val syncing by viewModel.isSyncing.collectAsState()
                    val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
                    val syncRotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                        label = "syncRotation"
                    )
                    IconButton(
                        onClick = { if (!syncing) viewModel.triggerSync() },
                        enabled = !syncing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync now",
                            tint = if (syncing) AccentDeep else TextPrimary,
                            modifier = if (syncing) Modifier.graphicsLayer { rotationZ = syncRotation } else Modifier
                        )
                    }
                    IconButton(onClick = {
                        val nextGrid = !isGrid
                        viewModel.isGridView.value = nextGrid
                        viewModel.navigateTo(if (nextGrid) "home_grid" else "home_list")
                    }) {
                        Icon(
                            imageVector = if (isGrid) Icons.Default.Info else Icons.Default.Refresh, // simulated grid/list icon
                            contentDescription = "Toggle View",
                            tint = if (isGrid) ButterYellow else TextPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.navigateTo("search") }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
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
                CategoryChip("All", filterChip == "All", onClick = { viewModel.filterChip.value = "All" })
                CategoryChip("Today", filterChip == "Today", onClick = { viewModel.filterChip.value = "Today" })
                CategoryChip("Action items", filterChip == "Action items", onClick = { viewModel.filterChip.value = "Action items" })
                CategoryChip("Meeting notes", filterChip == "Meeting notes", onClick = { viewModel.filterChip.value = "Meeting notes" })
                CategoryChip("Ideas", filterChip == "Ideas", onClick = { viewModel.filterChip.value = "Ideas" })
                CategoryChip("Briefing", false, onClick = { viewModel.navigateTo("briefing") })
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No notes yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Touch the '+' button below to add.", fontSize = 14.sp, color = TextSecondary)
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
                        NoteListRow(note = note, onClick = { viewModel.selectActiveNote(note.id) }, viewModel = viewModel)
                    }
                }
            } else {
                // Grid/Masonry card layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    // Two column stack
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            notes.filterIndexed { index, _ -> index % 2 == 0 }.forEach { note ->
                                NoteGridCard(note = note, onClick = { viewModel.selectActiveNote(note.id) }, viewModel = viewModel)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            notes.filterIndexed { index, _ -> index % 2 != 0 }.forEach { note ->
                                NoteGridCard(note = note, onClick = { viewModel.selectActiveNote(note.id) }, viewModel = viewModel)
                            }
                        }
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
fun NoteListRow(note: Note, onClick: () -> Unit, viewModel: NoteViewModel) {
    Column(
        modifier = Modifier
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
                text = if (note.title.isBlank()) "Untitled note" else note.title,
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
                    contentDescription = if (note.isPinned) "Unpin" else "Pin",
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
                text = if (note.body.isBlank()) "Empty content..." else note.body.replace("\n", " "),
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    model = note.imageUrl,
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
                    text = if (note.title.isBlank()) "Untitled note" else note.title,
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
                        contentDescription = if (note.isPinned) "Unpin" else "Pin",
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
                    text = if (note.body.isBlank()) "Empty content..." else note.body,
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
    val note by viewModel.activeNote.collectAsState()
    var sheetOpen by remember { mutableStateOf(false) } // Ask NoteAi Chat Sheet
    var voiceSheetOpen by remember { mutableStateOf(false) } // Voice Dictator

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.generateTitleFromContent() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "AI Suggest Title")
                    }
                    IconButton(onClick = { viewModel.deleteActiveNote() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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
                .background(Color.White)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp, bottom = 16.dp)
            ) {
                // Time updated tag
                val formattedUpdate = remember(note?.updatedAt) {
                    val diff = System.currentTimeMillis() - (note?.updatedAt ?: 0)
                    if (diff < 60000) "Edited just now" else "Edited ${diff / 60000}m ago"
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
                    Text("Key decisions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    val array = JSONArray(note?.checklistJson)
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
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = Color.White)
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
                        model = note?.imageUrl,
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
                    ActionSuggestButton(title = "AI Tags", onClick = { viewModel.autoTagActiveNote() })
                    ActionSuggestButton(title = "Summarize", onClick = {
                        viewModel.summarizeActiveNote()
                        sheetOpen = true
                    })
                    ActionSuggestButton(title = "Task Checklist", onClick = { viewModel.extractTasksFromActiveNote() })
                    IconButton(onClick = { voiceSheetOpen = true }) {
                        Icon(Icons.Default.Person, contentDescription = "Voice Dictate", tint = ButterYellow) // represents voice dictation trigger in styling
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tags chips (FlowRow to enable wrapping list flow)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.split(",").filter { it.isNotBlank() }.map { it.trim() }.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(ButterSoft, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(AccentDeep, CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tag, fontSize = 12.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Simulated manual edit tag button
                    IconButton(onClick = {
                        tags += if (tags.isEmpty()) "project" else ",project"
                    }) {
                        Icon(Icons.Default.Add, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Manual Tag Editor Text Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = tags,
                        onValueChange = { input ->
                            // Limits each individual tag to 20 characters max
                            val processed = input.split(",").joinToString(",") {
                                if (it.length > 20) it.take(20) else it
                            }
                            tags = processed
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            color = AccentDeep,
                            fontWeight = FontWeight.Medium
                        ),
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (tags.isEmpty()) {
                                Text("Edit tags manually (comma separated, max 20 chars per tag)...", fontSize = 13.sp, color = TextTertiary)
                            }
                            innerTextField()
                        }
                    )
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
                Icon(Icons.Default.Refresh, contentDescription = "Ask NoteAi", tint = Color.White) // simulated sparkles
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
fun ActionSuggestButton(title: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceTertiary),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(title, color = AccentDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                imageVector = if (isPlaying) Icons.Default.Refresh else Icons.Default.Info,  // Play/Pause simulation
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
            h.forEach { heightVal ->
                val scaleHeight = animateDpAsState(
                    targetValue = if (isPlaying) (heightVal + (0..10).random()).dp else heightVal.dp,
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
@Composable
fun VoiceNoteDictatorOverlay(viewModel: NoteViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val seconds by viewModel.recordingSeconds.collectAsState()
    val transcript by viewModel.liveTranscript.collectAsState()

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable {
                speechHelper.stop()
                viewModel.stopDictation()
                onClose()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .background(Color.White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(enabled = false) { }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).background(BorderStrong, CircleShape))
            Spacer(modifier = Modifier.height(16.dp))

            Text("Voice note", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.fillMaxWidth())

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
                            Text("Listening...", fontSize = 11.sp, color = TextTertiary)
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
                    Text("Cancel", color = AccentDeep, fontWeight = FontWeight.Bold)
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
                    Text("Done", color = AccentDeep, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ----------------------------------------------------
// 9. DAILY BRIEFING SCREEN
// ----------------------------------------------------
@Composable
fun DailyBriefingScreen(viewModel: NoteViewModel) {
    val notes by viewModel.allNotes.collectAsState()
    val yesterdayNotes = remember(notes) { notes.take(3) }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Daily Briefing", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text("Good morning, Aru", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()),
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Highlights row
            Text("Yesterday's highlights", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            // Horizontally scrolls notes summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (yesterdayNotes.isEmpty()) {
                    Box(modifier = Modifier.width(240.dp).height(140.dp).background(SurfaceTertiary, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Text("No edited notes yesterday", fontSize = 12.sp, color = TextTertiary)
                    }
                } else {
                    yesterdayNotes.forEach { note ->
                        Box(
                            modifier = Modifier
                                .width(240.dp)
                                .height(140.dp)
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                .clickable { viewModel.selectActiveNote(note.id) }
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                                Column {
                                    val tag = note.tags.split(",").firstOrNull() ?: "work"
                                    Box(modifier = Modifier.background(Color(0xFFE2E7E5), RoundedCornerShape(12.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text(tag, fontSize = 9.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(note.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text("Edited recently", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Open Action items
            Text("Open action items", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            // Prepopulated checklists list card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActionCheckRow("Book venue for offsite", "from Q4 planning • June 11")
                    ActionCheckRow("Call Rina about design review", "pending action item")
                    ActionCheckRow("Send follow-up email to investors", "investor outreach")
                    ActionCheckRow("Renew domain name — noteai.app", "billing alert")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Today's schedule
            Text("Today's plan", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TodayScheduleItem("09:00", "Standup with engineering (30m)")
                TodayScheduleItem("13:30", "Lunch with Priya")
                TodayScheduleItem("16:00", "Design review with team")
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
fun ActionCheckRow(title: String, subtitle: String) {
    var checked by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked = !checked },
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
                .border(2.dp, if (checked) SuccessSage else BorderStrong, RoundedCornerShape(4.dp))
                .background(if (checked) SuccessSage else Color.Transparent, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp), tint = Color.White)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 15.sp, color = if (checked) TextSecondary else TextPrimary, textDecoration = if (checked) TextDecoration.LineThrough else null)
            Text(subtitle, fontSize = 11.sp, color = TextTertiary)
        }
    }
}

@Composable
fun TodayScheduleItem(time: String, title: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(time, fontSize = 12.sp, color = ButterYellow, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
        Text(title, fontSize = 14.sp, color = TextPrimary)
    }
}

// ----------------------------------------------------
// 10. ASK NOTEAI CHAT OVERLAY BOTTOM SHEET
// ----------------------------------------------------
@Composable
fun AskNoteAiChatOverlay(viewModel: NoteViewModel, onClose: () -> Unit) {
    val history by viewModel.chatHistory.collectAsState()
    val thinking by viewModel.isAiThinking.collectAsState()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Chat Sheet layout container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .background(Color.White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(enabled = false) { }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).background(BorderStrong, CircleShape))
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Ask NoteAi", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("ANSWERS FROM YOUR LIBRARY", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
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
                            Text("Ask NoteAi any question", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            // Suggestion chips
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                ChatSuggestChip("Summarize my notes from this week") {
                                    viewModel.speakToAI("Summarize my notes from this week")
                                }
                                ChatSuggestChip("What should I prioritize today?") {
                                    viewModel.speakToAI("What should I prioritize today?")
                                }
                            }
                        }
                    }
                } else {
                    items(history) { message ->
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
                                Text("Quiet Intelligence thinking...", fontSize = 11.sp, color = TextTertiary)
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
                    placeholder = { Text("Ask anything about your notes...", color = TextTertiary) },
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
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputMessage.isNotBlank()) Color.White else TextTertiary
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
    var query by remember { mutableStateOf("") }
    val notes by viewModel.filteredNotes.collectAsState()
    val filterChip by viewModel.filterChip.collectAsState()

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.searchQuery.value = it
                    },
                    placeholder = { Text("Search notes, tasks, ideas...", color = TextTertiary) },
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
                .background(Color.White)
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
                CategoryChip("All", filterChip == "All", onClick = { viewModel.filterChip.value = "All" })
                CategoryChip("Notes", filterChip == "Ideas", onClick = { viewModel.filterChip.value = "Ideas" })
                CategoryChip("Tasks", filterChip == "Action items", onClick = { viewModel.filterChip.value = "Action items" })
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lists entries
            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No matching results", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Try searching for topics, words, or tags.", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            } else {
                Text("Recent", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
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
                            Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(20.dp)) // simulated schedule/recent clock icon
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (note.title.isBlank()) "Untitled note" else note.title,
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
            val textColor = if (isSelected) Color.White else TextSecondary
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
            val textColor = if (isSelected) Color.White else TextSecondary
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
    val context = LocalContext.current
    val isOffline by viewModel.isOfflineMode.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val dateFormat by viewModel.dateFormatting.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val authState by authViewModel.authState.collectAsState()

    val mockEmail by authViewModel.mockUserEmail.collectAsState()

    // Determine current user display info
    val accountName = remember(authState, mockEmail) {
        val user = authViewModel.currentUser
        val email = user?.email ?: mockEmail ?: ""
        if (email.isBlank()) "Guest User" else email.substringBefore("@")
    }
    val accountEmail = remember(authState, mockEmail) {
        val user = authViewModel.currentUser
        val email = user?.email ?: mockEmail ?: ""
        if (email.isBlank()) "Offline guest mode" else email
    }
    val planLabel = remember(authState, mockEmail) {
        val user = authViewModel.currentUser
        if (user != null) {
            val isAnon = user.isAnonymous
            if (isAnon) "Guest plan" else "Pro plan"
        } else {
            if (mockEmail == "guest_user@example.com" || mockEmail.isNullOrBlank()) {
                "Guest plan"
            } else {
                "Pro plan"
            }
        }
    }

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
                .background(Color.White)
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateTo("home_list") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(androidx.compose.ui.res.stringResource(id = R.string.settings), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            // User Info Account Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFAF8F3), RoundedCornerShape(16.dp))
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
                            Text(planLabel, fontSize = 10.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(accountEmail, fontSize = 12.sp, color = TextSecondary)
                }
                Icon(Icons.Default.Check, null, tint = TextTertiary) // arrow trailing simulation
            }

            Spacer(modifier = Modifier.height(24.dp))

            // PREFERENCES
            SettingsSectionHeader("PREFERENCES")
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
            SettingsRowClick("Date format", dateFormat) {
                viewModel.setDateFormat(if (dateFormat == "DD/MM/YYYY") "MM/DD/YYYY" else "DD/MM/YYYY")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SYNC & STORAGE
            SettingsSectionHeader("SYNC & STORAGE")
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Storage used", fontSize = 14.sp, color = TextPrimary)
                    Text("1.2 GB of 5 GB", fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(Color(0xFFFAF8F3), CircleShape)) {
                    Box(modifier = Modifier.fillMaxWidth(0.24f).height(6.dp).background(ButterYellow, CircleShape))
                }
            }
            SettingsRowAction("Export all notes") {
                Toast.makeText(context, "All notes exported!", Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI SETTINGS
            SettingsSectionHeader("AI CONFIGURATIONS")
            SettingsRowClick("Model", selectedModel) {
                viewModel.setSelectedModel("Gemini 3.5 Flash")
            }

            // Gemini API Key Input
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text("Gemini API Key", fontSize = 14.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                var apiKeyInput by remember { mutableStateOf(geminiApiKey) }
                var showApiKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste your Gemini API key here", fontSize = 12.sp, color = TextTertiary) },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.Check else Icons.Default.Info,
                                contentDescription = "Toggle visibility",
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
                        "Get free key at aistudio.google.com",
                        fontSize = 11.sp,
                        color = AccentDeep,
                        textDecoration = TextDecoration.Underline
                    )
                    Button(
                        onClick = {
                            viewModel.setGeminiApiKey(apiKeyInput.trim())
                            Toast.makeText(context, "API key saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ButterYellow),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                        enabled = apiKeyInput.isNotBlank()
                    ) {
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (apiKeyInput.isNotBlank()) TextPrimary else TextTertiary)
                    }
                }
                if (geminiApiKey.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Key: ...${geminiApiKey.takeLast(6)}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text("Data usage", fontSize = 14.sp, color = TextPrimary)
                Text("Your notes stay on-device unless you ask", fontSize = 11.sp, color = TextSecondary)
            }
            SettingsRowAction("Regenerate smart tags") {
                Toast.makeText(context, "Smart tags successfully regenerated!", Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Logout & Legal
            Button(
                onClick = {
                    authViewModel.signOut {
                        viewModel.isLoggedIn.value = false
                        viewModel.navigateTo("login")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF0E0E0)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(androidx.compose.ui.res.stringResource(id = R.string.log_out), color = ErrorRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Terms · Privacy · Open-source licenses",
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
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ButterYellow)
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
            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = TextSecondary)
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
        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = TextSecondary)
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

    val capturedPath by viewModel.capturedImagePath.collectAsState()
    val imageOcrText by viewModel.imageOcrText.collectAsState()
    val imageDescription by viewModel.imageDescription.collectAsState()
    val isVisionLoading by viewModel.isVisionLoading.collectAsState()

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
                Toast.makeText(context, "Error reading image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = TextPrimary)
                }
                Text("AI Scanner", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = {
                    showCameraPreview = true
                    viewModel.capturedImagePath.value = null
                    viewModel.imageOcrText.value = ""
                    viewModel.imageDescription.value = ""
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset camera", tint = TextPrimary)
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
                                Icon(Icons.Default.Menu, contentDescription = "Gallery", tint = Color.White)
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
                                                                Toast.makeText(context, "Error capturing: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                                        Toast.makeText(context, "Camera capture error: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                                Icon(Icons.Default.Info, contentDescription = "Flash Toggle", tint = Color.White)
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Camera usage is disabled until permissions granted.", color = Color.White, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                                Text("Grant camera permission", color = Color.White)
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
                            model = capturedPath,
                            contentDescription = "Captured image",
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
                    Text("EXTRACTED TEXT:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF3EFE6), RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = if (imageOcrText.isEmpty()) "Scanning and reading text form factor..." else imageOcrText,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Image assistant description text block
                    if (imageDescription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("IMAGE DESCRIPTION:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
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

                    // Column of modern 44dp capsule rounded actionable buttons
                    Button(
                        onClick = {
                            if (imageOcrText.isNotEmpty()) {
                                viewModel.saveOcrImageAsNote()
                                Toast.makeText(context, "Saved scan successfully!", Toast.LENGTH_SHORT).show()
                                viewModel.navigateTo("home_list")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ButterYellow),
                        shape = RoundedCornerShape(9999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text("Save as note", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(imageOcrText))
                                Toast.makeText(context, "Text copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceTertiary),
                            shape = RoundedCornerShape(9999.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text("Copy text", color = TextPrimary, fontWeight = FontWeight.Medium)
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
                            Text("Describe image", color = TextPrimary, fontWeight = FontWeight.Medium)
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
                                    Toast.makeText(context, "Searching related notes in AI Chat history...", Toast.LENGTH_LONG).show()
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
                            Text("Search Related Notes", color = TextPrimary, fontWeight = FontWeight.Medium)
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
    val context = LocalContext.current
    val noteViewModel: NoteViewModel = viewModel()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .width(280.dp)
                .height(64.dp)
                .background(Color.White, RoundedCornerShape(9999.dp))
                .border(1.dp, Color(0xFFF0F0F0), RoundedCornerShape(999.dp)),
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
                    contentDescription = "Home",
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
                    .clickable(onClick = { onAddClick() }),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            // Settings/Profile
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = { onProfileClick() })
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Profile",
                    tint = if (activeTab == "profile") ButterYellow else TextSecondary
                )
                if (activeTab == "profile") {
                    Box(modifier = Modifier.padding(top = 2.dp).size(4.dp).background(ButterYellow, CircleShape))
                }
            }
        }
    }
}
