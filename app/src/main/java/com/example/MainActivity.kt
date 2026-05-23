package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0C0C0C)),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    ConsoleApp(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Installation States
sealed class AppState {
    object InitialPrompt : AppState()
    data class Installing(
        val logLines: List<String> = emptyList(),
        val progressText: String = "Инициализация...",
        val downloadPercent: Int? = null
    ) : AppState()
    data class StorageError(val requiredSpace: String, val availableSpace: String) : AppState()
    data class NetworkError(val errorDetail: String) : AppState()
    object TerminalActive : AppState()
}

@Composable
fun ConsoleApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("murmux_prefs", Context.MODE_PRIVATE) }
    
    // Installation state check
    var currentAppState by remember {
        mutableStateOf<AppState>(
            if (sharedPrefs.getBoolean("is_installed", false)) {
                AppState.TerminalActive
            } else {
                AppState.InitialPrompt
            }
        )
    }

    val coroutineScope = rememberCoroutineScope()

    // Render corresponding UI based on State
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C))
    ) {
        when (val state = currentAppState) {
            is AppState.InitialPrompt -> {
                InitialSetupDialog(
                    onAccept = {
                        // Start setup procedure
                        val initialLog = mutableListOf("Запуск инициализации среды...")
                        currentAppState = AppState.Installing(
                            logLines = initialLog,
                            progressText = "Инициализация..."
                        )

                        coroutineScope.launch {
                            setupUbuntuEnvironment(
                                context = context,
                                onLog = { newLog ->
                                    val current = currentAppState as? AppState.Installing
                                    if (current != null) {
                                        currentAppState = current.copy(
                                            logLines = current.logLines + newLog
                                        )
                                    }
                                },
                                onProgressText = { prgText ->
                                    val current = currentAppState as? AppState.Installing
                                    if (current != null) {
                                        currentAppState = current.copy(progressText = prgText)
                                    }
                                },
                                onPercent = { pct ->
                                    val current = currentAppState as? AppState.Installing
                                    if (current != null) {
                                        currentAppState = current.copy(downloadPercent = pct)
                                    }
                                },
                                onStorageError = { req, avail ->
                                    currentAppState = AppState.StorageError(req, avail)
                                },
                                onNetworkError = { detail ->
                                    currentAppState = AppState.NetworkError(detail)
                                },
                                onSuccess = {
                                    sharedPrefs.edit().putBoolean("is_installed", true).apply()
                                    currentAppState = AppState.TerminalActive
                                }
                            )
                        }
                    },
                    onExit = {
                        var act = context
                        while (act is android.content.ContextWrapper) {
                            if (act is ComponentActivity) {
                                break
                            }
                            act = act.baseContext
                        }
                        (act as? ComponentActivity)?.finishAndRemoveTask()
                    }
                )
            }

            is AppState.Installing -> {
                UbuntuInstallationView(
                    logLines = state.logLines,
                    progressTitle = state.progressText,
                    percentProgress = state.downloadPercent
                )
            }

            is AppState.StorageError -> {
                StorageErrorView(
                    requiredSpace = state.requiredSpace,
                    availableSpace = state.availableSpace,
                    onRetry = {
                        currentAppState = AppState.InitialPrompt
                    },
                    onExit = {
                        var act = context
                        while (act is android.content.ContextWrapper) {
                            if (act is ComponentActivity) {
                                break
                            }
                            act = act.baseContext
                        }
                        (act as? ComponentActivity)?.finishAndRemoveTask()
                    }
                )
            }

            is AppState.NetworkError -> {
                NetworkErrorView(
                    errorDetail = state.errorDetail,
                    onRetry = {
                        currentAppState = AppState.InitialPrompt
                    },
                    onExit = {
                        var act = context
                        while (act is android.content.ContextWrapper) {
                            if (act is ComponentActivity) {
                                break
                            }
                            act = act.baseContext
                        }
                        (act as? ComponentActivity)?.finishAndRemoveTask()
                    }
                )
            }

            is AppState.TerminalActive -> {
                TerminalConsoleView()
            }
        }
    }
}

/**
 * 1. Introduction Welcome confirmation Dialog
 */
@Composable
fun InitialSetupDialog(
    onAccept: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .padding(24.dp)
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0xFF8E8E8E))
                .padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Sophisticated setup header
            Text(
                text = "ENVIRONMENT SETUP",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Dynamic mini high-resolution whitespace indicator: h-1 w-8 bg-white
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(1.dp)
                    .background(Color.White)
                    .padding(bottom = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Установка окружения. Вы уверены, что готовы загрузить и установить образ Ubuntu 20.04 (ARM64)? Процесс потребует стабильного интернет-соединения.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF8E8E8E),
                    lineHeight = 22.sp
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Vertical button layout mimicking the Sophisticated Dark template
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary Accent Button (INSTALL)
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("install_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RectangleShape
                ) {
                    Text(
                        text = "УСТАНОВИТЬ",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }

                // Secondary Monochromatic Button (EXIT)
                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, Color(0xFF8E8E8E))
                        .testTag("exit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF8E8E8E)
                    ),
                    shape = RectangleShape
                ) {
                    Text(
                        text = "ВЫХОД",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFF8E8E8E),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * 2. Strict Minimalist Progress View
 */
@Composable
fun UbuntuInstallationView(
    logLines: List<String>,
    progressTitle: String,
    percentProgress: Int?
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.animateScrollToItem(logLines.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C))
            .padding(16.dp)
    ) {
        // Console Header
        Text(
            text = "SYSTEM CONFIGURATION / SETUP MANAGER",
            style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Divider(color = Color(0xFF444444), modifier = Modifier.padding(bottom = 12.dp))

        // Logging Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF151515))
                .border(1.dp, Color(0xFF444444))
                .padding(12.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logLines) { line ->
                    Text(
                        text = "↳ $line",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (line.contains("Успешно") || line.contains("успешно")) Color(0xFF00FF00) else Color(0xFF8E8E8E)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress Status Indicator
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0xFF8E8E8E))
                .padding(16.dp)
        ) {
            // Percent counter and Title
            val percentageText = if (percentProgress != null) "Загрузка rootfs [Ubuntu 20.04 ARM64]: $percentProgress%" else progressTitle
            Text(
                text = percentageText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Linear styling indicator
            if (percentProgress != null) {
                LinearProgressIndicator(
                    progress = percentProgress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color.White,
                    trackColor = Color(0xFF444444)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color(0xFF8E8E8E),
                    trackColor = Color(0xFF1E1E1E)
                )
            }
        }
    }
}

/**
 * 3. Strict Storage Error Window Layout
 */
@Composable
fun StorageErrorView(
    requiredSpace: String,
    availableSpace: String,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .padding(24.dp)
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0xFFFF0000)) // Strict Red Warning Border
                .padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "INS_INSUFFICIENT_STORAGE",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Dynamic mini high-resolution whitespace indicator: red colored
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(1.dp)
                    .background(Color(0xFFFF0000))
                    .padding(bottom = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "На вашем устройстве недостаточно свободного места. Требуется для установки: $requiredSpace.\nДоступно в памяти приложения: $availableSpace.\n\nОсвободите пространство и повторите попытку.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF8E8E8E),
                    lineHeight = 22.sp
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Vertical buttons stack for consistency in layout
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RectangleShape
                ) {
                    Text(
                        text = "ПОВТОРИТЬ",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, Color(0xFF8E8E8E)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF8E8E8E)
                    ),
                    shape = RectangleShape
                ) {
                    Text(
                        text = "ВЫХОД",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFF8E8E8E),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Network Error View Layout for clean status reporting
 */
@Composable
fun NetworkErrorView(
    errorDetail: String,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .padding(24.dp)
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0xFFFF9800)) // Warning Yellow/Orange Border
                .padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "NET_CONNECTION_FAILURE",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Mini high-resolution yellow separator
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(1.dp)
                    .background(Color(0xFFFF9800))
                    .padding(bottom = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "$errorDetail\n\nПожалуйста, убедитесь, что у вас есть подключение к Интернету, и повторите попытку.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF8E8E8E),
                    lineHeight = 22.sp
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Vertical buttons stack for consistency in layout
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RectangleShape
                ) {
                    Text(
                        text = "ПОВТОРИТЬ",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, Color(0xFF8E8E8E)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF8E8E8E)
                    ),
                    shape = RectangleShape
                ) {
                    Text(
                        text = "ВЫХОД",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFF8E8E8E),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * 4. Actual interactive Terminal Screen View (Active session console)
 */
@Composable
fun TerminalConsoleView() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Command logs list buffer
    val consoleLogs = remember { mutableStateListOf<String>() }
    var currentCommandText by remember { mutableStateOf("") }
    var currentDirRelativePath by remember { mutableStateOf("~") }
    
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Toggle states for CTRL and ALT keys
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }

    // Keyboard (IME) visibility detection to dynamically hide/collapse rows and save vertical space
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    // Initialize our Background Linux Process
    val terminalProcess = remember {
        TerminalProcess(context) { outputString ->
            scope.launch(Dispatchers.Main) {
                consoleLogs.add(outputString.trimEnd())
            }
        }
    }

    DisposableEffect(Unit) {
        terminalProcess.start()
        onDispose {
            terminalProcess.destroy()
        }
    }

    // Auto scroll down upon log appendings
    LaunchedEffect(consoleLogs.size) {
        if (consoleLogs.isNotEmpty()) {
            listState.animateScrollToItem(consoleLogs.lastIndex)
        }
    }

    // Unified command submission logic
    val sendCommand = { rawCmd: String ->
        val trimmedCmd = rawCmd.trim()
        if (trimmedCmd.isNotEmpty()) {
            if (trimmedCmd == "clear") {
                consoleLogs.clear()
            } else {
                consoleLogs.add("${currentDirRelativePath.replace("~", "root@ubuntu:~")}# $trimmedCmd")
                
                // Intercept cd commands
                val parts = trimmedCmd.split("\\s+".toRegex())
                if (parts.isNotEmpty() && parts[0] == "cd") {
                    val sysRootDir = File(context.filesDir, "sys")
                    val rootHome = File(sysRootDir, "root")
                    val currentPhysicalDir = when {
                        currentDirRelativePath == "~" -> rootHome
                        currentDirRelativePath == "/" -> sysRootDir
                        currentDirRelativePath.startsWith("~/") -> File(rootHome, currentDirRelativePath.removePrefix("~/"))
                        currentDirRelativePath.startsWith("/") -> File(sysRootDir, currentDirRelativePath.removePrefix("/"))
                        else -> File(sysRootDir, currentDirRelativePath)
                    }
                    
                    val target = if (parts.size > 1) parts[1] else "~"
                    val resolved = SystemCore.resolvePath(currentPhysicalDir, target, sysRootDir)
                    
                    if (resolved.exists() && resolved.isDirectory) {
                        val relative = resolved.absolutePath.substringAfter(sysRootDir.absolutePath)
                        currentDirRelativePath = when {
                            relative == "/root" -> "~"
                            relative.startsWith("/root/") -> "~/${relative.removePrefix("/root/")}"
                            relative.isEmpty() || relative == "/" -> "/"
                            else -> relative
                        }
                        terminalProcess.writeInput("$trimmedCmd\n")
                    } else {
                        consoleLogs.add("cd: no such file or directory: $target")
                    }
                } else {
                    // To resolve Android 10+ SELinux execute restriction, run internal script wrappers via sh
                    val sysRootDir = File(context.filesDir, "sys")
                    val binDir = File(sysRootDir, "bin")
                    val firstWord = parts.getOrNull(0) ?: ""
                    
                    fun isShellScript(file: File): Boolean {
                        if (!file.exists() || !file.isFile) return false
                        return try {
                            file.inputStream().use { fis ->
                                val header = ByteArray(4)
                                val read = fis.read(header)
                                read >= 2 && header[0] == '#'.toByte() && header[1] == '!'.toByte()
                            }
                        } catch (_: Exception) {
                            false
                        }
                    }

                    val processedCmd = if (firstWord.startsWith("./") || firstWord.startsWith("/")) {
                        val rootHome = File(sysRootDir, "root")
                        val currentPhysicalDir = when {
                            currentDirRelativePath == "~" -> rootHome
                            currentDirRelativePath == "/" -> sysRootDir
                            currentDirRelativePath.startsWith("~/") -> File(rootHome, currentDirRelativePath.removePrefix("~/"))
                            currentDirRelativePath.startsWith("/") -> File(sysRootDir, currentDirRelativePath.removePrefix("/"))
                            else -> File(sysRootDir, currentDirRelativePath)
                        }
                        val targetFile = if (firstWord.startsWith("./")) {
                            File(currentPhysicalDir, firstWord.removePrefix("./"))
                        } else {
                            File(sysRootDir, firstWord.removePrefix("/"))
                        }
                        if (targetFile.exists() && targetFile.isFile && isShellScript(targetFile)) {
                            "sh " + targetFile.absolutePath + " " + trimmedCmd.substringAfter(firstWord).trim()
                        } else {
                            trimmedCmd
                        }
                    } else {
                        val binFile = File(binDir, firstWord)
                        if (binFile.exists() && binFile.isFile && isShellScript(binFile)) {
                            "sh " + binFile.absolutePath + " " + trimmedCmd.substringAfter(firstWord).trim()
                        } else {
                            trimmedCmd
                        }
                    }
                    
                    terminalProcess.writeInput("$processedCmd\n")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C))
            .padding(8.dp)
    ) {
        // Top status info banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MURMUX // CONSOLE SESSION ACTIVE",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "[arm64-ubuntu-focal]",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF8E8E8E))
            )
        }

        Divider(color = Color(0xFF333333), modifier = Modifier.padding(bottom = 8.dp))

        // Main Display terminal screen
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0C0C0C))
                .clickable { focusRequester.requestFocus() }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(consoleLogs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (log.contains("root@ubuntu:") || log.startsWith(">")) Color.White else Color(0xFF8E8E8E)
                        )
                    )
                }
            }
        }

        // Command Entry Field Line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0xFF8E8E8E))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentDirRelativePath.replace("~", "root@ubuntu:~")}# ",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF8E8E8E),
                    fontWeight = FontWeight.Bold
                )
            )

            BasicTextField(
                value = currentCommandText,
                onValueChange = { newVal ->
                    // 1. Intercept typed letters when CTRL modifier is toggled active
                    if (isCtrlActive && newVal.length > currentCommandText.length) {
                        val lastChar = newVal.lastOrNull()
                        if (lastChar != null && lastChar.isLetter()) {
                            val charLower = lastChar.lowercaseChar()
                            terminalProcess.writeInput("ctrl+$charLower\n")
                            isCtrlActive = false
                            return@BasicTextField
                        }
                    }
                    // 2. Intercept typed letters when ALT modifier is toggled active
                    if (isAltActive && newVal.length > currentCommandText.length) {
                        val lastChar = newVal.lastOrNull()
                        if (lastChar != null && lastChar.isLetter()) {
                            val charLower = lastChar.lowercaseChar()
                            terminalProcess.writeInput("alt+$charLower\n")
                            isAltActive = false
                            return@BasicTextField
                        }
                    }
                    currentCommandText = newVal
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("command_input"),
                textStyle = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(Color.White),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    autoCorrect = false
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        sendCommand(currentCommandText)
                        currentCommandText = ""
                        isCtrlActive = false
                        isAltActive = false
                    }
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick shell command shortcut keys (Hides dynamically when keyboard is opened, to save space!)
        if (!isKeyboardVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val shortCmds = listOf("help", "neofetch", "ls -la", "uname -a", "apt update", "clear")
                shortCmds.forEach { cmd ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color(0xFF333333))
                            .background(Color(0xFF151515))
                            .clickable {
                                sendCommand(cmd)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cmd,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFC0C0C0),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Sophisticated Dark design theme Terminal Control Bar (Grid styling)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E1E1E))
                .background(Color(0xFF0C0C0C))
        ) {
            val controlKeys = listOf("ESC", "TAB", "CTRL", "ALT", "-", "+")
            controlKeys.forEachIndexed { index, label ->
                val isActive = (label == "CTRL" && isCtrlActive) || (label == "ALT" && isAltActive)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(if (isActive) Color.White else Color(0xFF0C0C0C))
                        .then(
                            if (index < controlKeys.size - 1) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = Color(0xFF1E1E1E)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable {
                            when (label) {
                                "ESC" -> {
                                    currentCommandText = ""
                                    terminalProcess.writeInput("esc\n")
                                    isCtrlActive = false
                                    isAltActive = false
                                }
                                "TAB" -> {
                                    terminalProcess.writeInput("\t")
                                    isCtrlActive = false
                                    isAltActive = false
                                }
                                "CTRL" -> {
                                    isCtrlActive = !isCtrlActive
                                    isAltActive = false
                                }
                                "ALT" -> {
                                    isAltActive = !isAltActive
                                    isCtrlActive = false
                                }
                                "-" -> {
                                    if (isCtrlActive) {
                                        terminalProcess.writeInput("ctrl+-\n")
                                        isCtrlActive = false
                                    } else if (isAltActive) {
                                        terminalProcess.writeInput("alt+-\n")
                                        isAltActive = false
                                    } else {
                                        currentCommandText += "-"
                                    }
                                }
                                "+" -> {
                                    if (isCtrlActive) {
                                        terminalProcess.writeInput("ctrl++\n")
                                        isCtrlActive = false
                                    } else if (isAltActive) {
                                        terminalProcess.writeInput("alt++\n")
                                        isAltActive = false
                                    } else {
                                        currentCommandText += "+"
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isActive) Color.Black else Color(0xFF8E8E8E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Main Setup Workflow: checks mirrors, queries storage, downloads, then initializes directories.
 */
private suspend fun setupUbuntuEnvironment(
    context: Context,
    onLog: (String) -> Unit,
    onProgressText: (String) -> Unit,
    onPercent: (Int) -> Unit,
    onStorageError: (String, String) -> Unit,
    onNetworkError: (String) -> Unit,
    onSuccess: () -> Unit
) = withContext(Dispatchers.IO) {
    try {
        // Step 1: Find Active mirrors via HTTP HEAD
        onProgressText("Поиск зеркал Ubuntu...")
        onLog("Сканирование сети для поиска активного зеркала Rootfs...")
        val checkResult = MirrorFinder.findWorkingMirror { log ->
            onLog(log)
        }

        // Setup size defaults - Prefer real Ubuntu Focal Live Server ISO as requested by the user
        var activeUrl = "https://old-releases.ubuntu.com/releases/20.04.4/ubuntu-20.04-live-server-arm64.iso"
        var archiveSizeInBytes: Long = 1387225088L // ~1.29 GB

        if (checkResult != null) {
            activeUrl = checkResult.first
            archiveSizeInBytes = checkResult.second
            onLog("Выбрано рабочее зеркало: $activeUrl (${SystemCore.formatBytes(archiveSizeInBytes)})")
        } else {
            onLog("Зеркала не дали прямого ответа. Использование основного образа системы...")
        }

        // Step 2: Verify Space allocation
        onProgressText("Анализ диска...")
        onLog("Проверка свободного дискового пространства...")
        val spaceCheck = SystemCore.checkStorageAvailability(context, archiveSizeInBytes)

        val reqSpaceText = SystemCore.formatBytes(spaceCheck.requiredBytes)
        val availSpaceText = SystemCore.formatBytes(spaceCheck.availableBytes)

        onLog("Требуется свободного места: $reqSpaceText")
        onLog("Доступно во внутренней памяти: $availSpaceText")

        if (!spaceCheck.isEnough) {
            onLog("Ошибка: Недостаточно доступной памяти. Остановка установки.")
            withContext(Dispatchers.Main) {
                onStorageError(reqSpaceText, availSpaceText)
            }
            return@withContext
        }

        onLog("Память верифицирована. Запуск загрузки rootfs файла...")

        // Step 3: Download Rootfs over network
        var downloadedFile: File? = null
        onProgressText("Скачивание...")
        
        downloadedFile = SystemCore.downloadRootfsArchive(context, activeUrl, archiveSizeInBytes) { progress ->
            onPercent(progress)
        }

        if (downloadedFile == null || !downloadedFile.exists()) {
            onLog("Ошибка: Не удалось скачать образ системы по сети.")
            onLog("Реальная установка прервана из-за сетевого сбоя.")
            withContext(Dispatchers.Main) {
                onNetworkError("Сбой сети (404/Timeout при попытке скачать $activeUrl)")
            }
            return@withContext
        } else {
            onLog("Загрузка архива успешно завершена. Файл сохранен: ${downloadedFile.name} (${SystemCore.formatBytes(downloadedFile.length())})")
        }

        // Step 4: Extract/Initialize Rootfs structures
        onProgressText("Распаковка...")
        SystemCore.initializeFilesystem(context, downloadedFile) { statusLine ->
            onLog(statusLine)
        }

        onLog("Инсталляция завершена. Запуск виртуального PTY...")
        delay(1200)

        // Return successfully
        withContext(Dispatchers.Main) {
            onSuccess()
        }
    } catch (e: Exception) {
        onLog("Критический сбой: ${e.localizedMessage}")
        Log.e("SetupProcedure", "Fatal exception", e)
        withContext(Dispatchers.Main) {
            onNetworkError("Непредвиденный критический сбой: ${e.localizedMessage}")
        }
    }
}
