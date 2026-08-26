package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.TerminalLine
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryEmerald
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDimText
import com.example.ui.theme.TerminalFontFamily
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalOrange
import com.example.ui.theme.TerminalPurple
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow
import org.json.JSONObject

enum class XtermTheme(val id: String, val label: String, val dotColor: Color) {
  CYBER("cyber", "Cyber Neon", TerminalCyan),
  MONOKAI("monokai", "Monokai", TerminalYellow),
  MATRIX("matrix", "Matrix Green", TerminalGreen),
  DRACULA("dracula", "Dracula", TerminalPurple),
  AMBER("amber", "VT220 Amber", TerminalOrange),
  SOLARIZED("solarized", "Solarized", Color(0xFF2AA198))
}

class TerminalJsBridge(
  private val onCommand: (String) -> Unit,
  private val onTabKey: (String) -> Unit,
  private val onCtrlCKey: () -> Unit,
  private val onBufferChanged: (String) -> Unit,
  private val onReadyCallback: (cols: Int, rows: Int) -> Unit
) {
  private val mainHandler = Handler(Looper.getMainLooper())

  @JavascriptInterface
  fun onCommand(command: String) {
    mainHandler.post { onCommand(command) }
  }

  @JavascriptInterface
  fun onTab(currentBuffer: String) {
    mainHandler.post { onTabKey(currentBuffer) }
  }

  @JavascriptInterface
  fun onCtrlC() {
    mainHandler.post { onCtrlCKey() }
  }

  @JavascriptInterface
  fun onBufferChange(buffer: String) {
    mainHandler.post { onBufferChanged(buffer) }
  }

  @JavascriptInterface
  fun onReady(cols: Int, rows: Int) {
    mainHandler.post { onReadyCallback(cols, rows) }
  }

  @JavascriptInterface
  fun onResize(cols: Int, rows: Int) {
    // Resize notification
  }
}

enum class MobileKeypadCategory(val title: String) {
  CORE("Terminal"),
  DEV("Dev & Code"),
  UNIX("Pipes & Files"),
  QEMU("VM & Debug")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun XtermTerminalView(
  terminalLines: List<TerminalLine>,
  activePrompt: String,
  terminalInput: String,
  fontSizeSp: Int,
  onInputChange: (String) -> Unit,
  onSendCommand: (String?) -> Unit,
  onClearTerminal: () -> Unit,
  onIncreaseFontSize: () -> Unit,
  onDecreaseFontSize: () -> Unit,
  onKeyTab: () -> Unit,
  onKeyCtrlC: () -> Unit,
  onKeyHistoryPrev: () -> Unit,
  onKeyHistoryNext: () -> Unit,
  onInsertText: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var isTerminalReady by remember { mutableStateOf(false) }
  var lastRenderedLineCount by remember { mutableIntStateOf(0) }
  var selectedTheme by remember { mutableStateOf(XtermTheme.CYBER) }
  var showThemeSelector by remember { mutableStateOf(false) }
  var termDimensions by remember { mutableStateOf("80x24") }
  val clipboardManager = LocalClipboardManager.current

  var showMobileInputBar by remember { mutableStateOf(true) }
  var selectedCategory by remember { mutableStateOf(MobileKeypadCategory.CORE) }
  var isCtrlLocked by remember { mutableStateOf(false) }
  var isAltLocked by remember { mutableStateOf(false) }

  val bridge = remember {
    TerminalJsBridge(
      onCommand = { cmd -> onSendCommand(cmd) },
      onTabKey = { _ -> onKeyTab() },
      onCtrlCKey = { onKeyCtrlC() },
      onBufferChanged = { buffer -> onInputChange(buffer) },
      onReadyCallback = { cols, rows ->
        isTerminalReady = true
        termDimensions = "${cols}x${rows}"
      }
    )
  }

  // Update prompt in Xterm.js when active prompt changes
  LaunchedEffect(activePrompt, isTerminalReady) {
    if (isTerminalReady && webViewInstance != null) {
      val safePrompt = JSONObject.quote(activePrompt)
      webViewInstance?.evaluateJavascript("window.xtermSetPrompt($safePrompt);", null)
    }
  }

  // Update font size in Xterm.js
  LaunchedEffect(fontSizeSp, isTerminalReady) {
    if (isTerminalReady && webViewInstance != null) {
      val px = (fontSizeSp * 1.15).toInt().coerceIn(10, 24)
      webViewInstance?.evaluateJavascript("window.xtermSetFontSize($px);", null)
    }
  }

  // Sync terminal lines incrementally into Xterm.js
  LaunchedEffect(terminalLines, isTerminalReady) {
    if (!isTerminalReady || webViewInstance == null) return@LaunchedEffect

    if (terminalLines.isEmpty()) {
      lastRenderedLineCount = 0
      webViewInstance?.evaluateJavascript("window.xtermClear();", null)
      return@LaunchedEffect
    }

    if (terminalLines.size < lastRenderedLineCount) {
      webViewInstance?.evaluateJavascript("window.xtermClear();", null)
      lastRenderedLineCount = 0
    }

    val newLines = terminalLines.drop(lastRenderedLineCount)
    if (newLines.isNotEmpty()) {
      val jsBuilder = StringBuilder()
      for (line in newLines) {
        val ansiText = convertToAnsi(line)
        val safeJson = JSONObject.quote(ansiText)
        jsBuilder.append("window.xtermWriteln($safeJson);\n")
      }
      webViewInstance?.evaluateJavascript(jsBuilder.toString(), null)
      lastRenderedLineCount = terminalLines.size
    }
  }

  // Theme switch effect
  LaunchedEffect(selectedTheme, isTerminalReady) {
    if (isTerminalReady && webViewInstance != null) {
      webViewInstance?.evaluateJavascript("window.xtermSetTheme('${selectedTheme.id}');", null)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(CyberBackground)
  ) {
    // Modern Mobile Terminal Header Bar
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = CyberSurface,
      border = BorderStroke(1.dp, CyberBorder)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Terminal Window Dots
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF27C93F)))
          }

          Text(
            text = "xterm.js",
            color = PrimaryCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TerminalFontFamily
          )

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(TerminalGreen.copy(alpha = 0.15f))
              .padding(horizontal = 5.dp, vertical = 2.dp)
          ) {
            Text(
              text = "TTY $termDimensions",
              color = TerminalGreen,
              fontSize = 9.sp,
              fontFamily = TerminalFontFamily
            )
          }
        }

        // Action Toolbar (Theme, Zoom +/-, Toggle Mobile Keyboard Bar, Clear)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          // Toggle input toolbar
          IconButton(
            onClick = { showMobileInputBar = !showMobileInputBar },
            modifier = Modifier.size(32.dp).testTag("xterm_toggle_input")
          ) {
            Icon(
              imageVector = Icons.Default.Keyboard,
              contentDescription = "Toggle Virtual Mobile Keyboard",
              tint = if (showMobileInputBar) SecondaryEmerald else TerminalDimText,
              modifier = Modifier.size(17.dp)
            )
          }

          // Theme button
          IconButton(
            onClick = { showThemeSelector = !showThemeSelector },
            modifier = Modifier.size(32.dp).testTag("xterm_theme_button")
          ) {
            Icon(
              imageVector = Icons.Default.Palette,
              contentDescription = "Switch Terminal Theme",
              tint = selectedTheme.dotColor,
              modifier = Modifier.size(16.dp)
            )
          }

          // Font controls
          IconButton(
            onClick = onDecreaseFontSize,
            modifier = Modifier.size(32.dp).testTag("xterm_font_dec")
          ) {
            Text("A-", color = TerminalWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFontFamily)
          }

          IconButton(
            onClick = onIncreaseFontSize,
            modifier = Modifier.size(32.dp).testTag("xterm_font_inc")
          ) {
            Text("A+", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFontFamily)
          }

          IconButton(
            onClick = {
              onClearTerminal()
              webViewInstance?.evaluateJavascript("window.xtermClear();", null)
            },
            modifier = Modifier.size(32.dp).testTag("xterm_clear_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Clear,
              contentDescription = "Clear Terminal Screen",
              tint = TerminalDimText,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }

    // Theme selector popup row
    AnimatedVisibility(visible = showThemeSelector) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(CyberSurfaceVariant)
          .padding(horizontal = 8.dp, vertical = 5.dp)
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Theme:", color = TerminalDimText, fontSize = 11.sp, fontFamily = TerminalFontFamily)
        XtermTheme.values().forEach { theme ->
          FilterChip(
            selected = selectedTheme == theme,
            onClick = {
              selectedTheme = theme
              showThemeSelector = false
            },
            label = {
              Text(
                theme.label,
                fontSize = 10.sp,
                fontFamily = TerminalFontFamily,
                color = if (selectedTheme == theme) TerminalBlack else TerminalWhite
              )
            },
            leadingIcon = {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(theme.dotColor)
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = PrimaryCyan,
              containerColor = CyberSurface
            ),
            modifier = Modifier.height(28.dp)
          )
        }
      }
    }

    // Embed Xterm.js WebView container with full touch gesture support
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(CyberBackground)
        .padding(horizontal = 2.dp, vertical = 1.dp)
    ) {
      AndroidView(
        factory = { ctx ->
          WebView(ctx).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
              android.view.ViewGroup.LayoutParams.MATCH_PARENT,
              android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              useWideViewPort = true
              loadWithOverviewMode = true
              cacheMode = WebSettings.LOAD_NO_CACHE
              allowFileAccess = true
              builtInZoomControls = false
              displayZoomControls = false
            }
            addJavascriptInterface(bridge, "AndroidTerminal")
            webViewClient = object : WebViewClient() {
              override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val safePrompt = JSONObject.quote(activePrompt)
                view?.evaluateJavascript("window.xtermSetPrompt($safePrompt);", null)
                val px = (fontSizeSp * 1.15).toInt().coerceIn(10, 24)
                view?.evaluateJavascript("window.xtermSetFontSize($px);", null)
                view?.evaluateJavascript("window.xtermSetTheme('${selectedTheme.id}');", null)

                if (terminalLines.isNotEmpty()) {
                  val sb = StringBuilder()
                  for (line in terminalLines) {
                    val ansiText = convertToAnsi(line)
                    val safeJson = JSONObject.quote(ansiText)
                    sb.append("window.xtermWriteln($safeJson);\n")
                  }
                  view?.evaluateJavascript(sb.toString(), null)
                  lastRenderedLineCount = terminalLines.size
                }
              }
            }
            loadUrl("file:///android_asset/xterm/xterm_terminal.html")
            webViewInstance = this
          }
        },
        modifier = Modifier.fillMaxSize().testTag("xterm_webview")
      )
    }

    // Interactive Mobile & Touch Optimized Toolbar System
    if (showMobileInputBar) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .imePadding(),
        color = CyberSurface,
        border = BorderStroke(1.dp, CyberBorder)
      ) {
        Column(
          modifier = Modifier.fillMaxWidth()
        ) {
          // Category selector tabs for touch key groupings
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(CyberSurfaceVariant)
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            MobileKeypadCategory.values().forEach { cat ->
              val isSelected = selectedCategory == cat
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (isSelected) PrimaryCyan.copy(alpha = 0.2f) else Color.Transparent)
                  .border(1.dp, if (isSelected) PrimaryCyan else Color.Transparent, RoundedCornerShape(4.dp))
                  .clickable { selectedCategory = cat }
                  .padding(horizontal = 7.dp, vertical = 3.dp)
              ) {
                Text(
                  text = cat.title,
                  color = if (isSelected) PrimaryCyan else TerminalDimText,
                  fontSize = 10.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  fontFamily = TerminalFontFamily
                )
              }
            }
          }

          // Category-Specific Touch Buttons Row
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(CyberSurfaceVariant.copy(alpha = 0.6f))
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            when (selectedCategory) {
              MobileKeypadCategory.CORE -> {
                TerminalKeyButton(
                  label = "CTRL",
                  isAccent = isCtrlLocked,
                  onClick = { isCtrlLocked = !isCtrlLocked }
                )
                TerminalKeyButton(
                  label = "ALT",
                  isAccent = isAltLocked,
                  onClick = { isAltLocked = !isAltLocked }
                )
                TerminalKeyButton(label = "ESC", onClick = {
                  webViewInstance?.evaluateJavascript("window.xtermSendKey('ESC');", null)
                })
                TerminalKeyButton(label = "TAB", isAccent = true, onClick = {
                  onKeyTab()
                  webViewInstance?.evaluateJavascript("window.xtermSendKey('TAB');", null)
                })
                TerminalKeyButton(label = "^C", isDanger = true, onClick = {
                  onKeyCtrlC()
                  webViewInstance?.evaluateJavascript("window.xtermSendKey('CTRL_C');", null)
                })
                TerminalKeyButton(label = "^D", onClick = {
                  onSendCommand("exit")
                })
                TerminalKeyButton(label = "^Z", onClick = {
                  webViewInstance?.evaluateJavascript("window.xtermWriteln('^Z [Stopped]');", null)
                })
                TerminalKeyButton(label = "▲", onClick = {
                  onKeyHistoryPrev()
                  webViewInstance?.evaluateJavascript("window.xtermSendKey('ARROW_UP');", null)
                })
                TerminalKeyButton(label = "▼", onClick = {
                  onKeyHistoryNext()
                  webViewInstance?.evaluateJavascript("window.xtermSendKey('ARROW_DOWN');", null)
                })
                TerminalKeyButton(label = "⌫", onClick = {
                  webViewInstance?.evaluateJavascript("window.xtermSendKey('BACKSPACE');", null)
                })
              }
              MobileKeypadCategory.DEV -> {
                XtermTouchChip(label = "python3") {
                  onInsertText("python3 -c \"print('Hello World')\"")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('python3 ');", null)
                }
                XtermTouchChip(label = "go run") {
                  onInsertText("go run main.go")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('go run ');", null)
                }
                XtermTouchChip(label = "rustc") {
                  onInsertText("rustc --version")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('rustc ');", null)
                }
                XtermTouchChip(label = "node") {
                  onInsertText("node -e \"console.log('Node ready')\"")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('node ');", null)
                }
                XtermTouchChip(label = "gcc") {
                  onInsertText("gcc -Wall -O2 main.c -o app")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('gcc ');", null)
                }
                XtermTouchChip(label = "git status") { onSendCommand("git status") }
                XtermTouchChip(label = "git log") { onSendCommand("git log --oneline -n 5") }
                XtermTouchChip(label = "nano") {
                  onInsertText("nano main.py")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('nano ');", null)
                }
              }
              MobileKeypadCategory.UNIX -> {
                TerminalKeyButton(label = "|", onClick = {
                  onInsertText("| ")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('| ');", null)
                })
                TerminalKeyButton(label = "/", onClick = {
                  onInsertText("/")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('/');", null)
                })
                TerminalKeyButton(label = "-", onClick = {
                  onInsertText("-")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('-');", null)
                })
                TerminalKeyButton(label = "~", onClick = {
                  onInsertText("~")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('~');", null)
                })
                TerminalKeyButton(label = ">", onClick = {
                  onInsertText(" > ")
                  webViewInstance?.evaluateJavascript("window.xtermInsert(' > ');", null)
                })
                TerminalKeyButton(label = ">>", onClick = {
                  onInsertText(" >> ")
                  webViewInstance?.evaluateJavascript("window.xtermInsert(' >> ');", null)
                })
                TerminalKeyButton(label = "&&", onClick = {
                  onInsertText(" && ")
                  webViewInstance?.evaluateJavascript("window.xtermInsert(' && ');", null)
                })
                TerminalKeyButton(label = "$", onClick = {
                  onInsertText("$")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('$');", null)
                })
                XtermTouchChip(label = "sudo") {
                  onInsertText("sudo ")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('sudo ');", null)
                }
                XtermTouchChip(label = "grep") {
                  onInsertText("grep -rn \"text\" .")
                  webViewInstance?.evaluateJavascript("window.xtermInsert('grep ');", null)
                }
                XtermTouchChip(label = "ls -la") { onSendCommand("ls -la") }
                XtermTouchChip(label = "df -h") { onSendCommand("df -h") }
                XtermTouchChip(label = "free -m") { onSendCommand("free -m") }
              }
              MobileKeypadCategory.QEMU -> {
                XtermTouchChip(label = "neofetch") { onSendCommand("neofetch") }
                XtermTouchChip(label = "top") { onSendCommand("top") }
                XtermTouchChip(label = "apk update") { onSendCommand("apk update") }
                XtermTouchChip(label = "info cpus") { onSendCommand("info cpus") }
                XtermTouchChip(label = "info block") { onSendCommand("info block") }
                XtermTouchChip(label = "info kvm") { onSendCommand("info kvm") }
                XtermTouchChip(label = "rc-status") { onSendCommand("rc-status") }
                XtermTouchChip(label = "lbu commit") { onSendCommand("lbu commit") }
                XtermTouchChip(label = "setup-alpine") { onSendCommand("setup-alpine") }
                XtermTouchChip(label = "help") { onSendCommand("help") }
              }
            }
          }

          // Touch-Optimized Command Input Field & Quick Action Buttons
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = terminalInput,
              onValueChange = { newText ->
                onInputChange(newText)
                val safeText = JSONObject.quote(newText)
                webViewInstance?.evaluateJavascript("window.xtermSetInput($safeText);", null)
              },
              modifier = Modifier
                .weight(1f)
                .testTag("terminal_mobile_input"),
              placeholder = {
                Text(
                  "Type touch command...",
                  color = TerminalDimText,
                  fontFamily = TerminalFontFamily,
                  fontSize = 12.sp
                )
              },
              textStyle = TextStyle(
                fontFamily = TerminalFontFamily,
                fontSize = 13.sp,
                color = TerminalWhite
              ),
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
              keyboardActions = KeyboardActions(
                onSend = {
                  onSendCommand(null)
                  webViewInstance?.evaluateJavascript("window.xtermSetInput('');", null)
                }
              ),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = CyberBorder,
                focusedContainerColor = TerminalBlack,
                unfocusedContainerColor = TerminalBlack
              ),
              shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Quick Send Button
            Button(
              onClick = {
                onSendCommand(null)
                webViewInstance?.evaluateJavascript("window.xtermSetInput('');", null)
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = SecondaryEmerald,
                contentColor = TerminalBlack
              ),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              modifier = Modifier.testTag("terminal_send_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Execute Command",
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TerminalKeyButton(
  label: String,
  isAccent: Boolean = false,
  isDanger: Boolean = false,
  onClick: () -> Unit
) {
  val bgColor = when {
    isDanger -> TerminalRed.copy(alpha = 0.2f)
    isAccent -> PrimaryCyan.copy(alpha = 0.25f)
    else -> CyberSurface
  }
  val textColor = when {
    isDanger -> TerminalRed
    isAccent -> PrimaryCyan
    else -> TerminalWhite
  }
  val borderColor = when {
    isDanger -> TerminalRed.copy(alpha = 0.5f)
    isAccent -> PrimaryCyan.copy(alpha = 0.7f)
    else -> CyberBorder
  }

  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(bgColor)
      .border(1.dp, borderColor, RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 7.dp)
  ) {
    Text(
      text = label,
      color = textColor,
      fontFamily = TerminalFontFamily,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
private fun XtermTouchChip(
  label: String,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(CyberSurface)
      .border(1.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 9.dp, vertical = 6.dp)
  ) {
    Text(
      text = label,
      color = PrimaryCyan,
      fontFamily = TerminalFontFamily,
      fontSize = 11.sp,
      fontWeight = FontWeight.Medium
    )
  }
}

private fun convertToAnsi(line: TerminalLine): String {
  val text = line.text
  return when {
    line.isPrompt -> "\u001B[1;32m$text\u001B[0m"
    line.isError -> "\u001B[1;31m$text\u001B[0m"
    line.isSuccess -> "\u001B[1;32m$text\u001B[0m"
    line.isSystem -> "\u001B[1;36m$text\u001B[0m"
    line.color == TerminalCyan -> "\u001B[36m$text\u001B[0m"
    line.color == TerminalGreen -> "\u001B[32m$text\u001B[0m"
    line.color == TerminalYellow -> "\u001B[33m$text\u001B[0m"
    line.color == TerminalRed -> "\u001B[31m$text\u001B[0m"
    line.color == TerminalDimText -> "\u001B[90m$text\u001B[0m"
    line.color == TerminalPurple -> "\u001B[35m$text\u001B[0m"
    line.color == TerminalOrange -> "\u001B[33m$text\u001B[0m"
    else -> text
  }
}
