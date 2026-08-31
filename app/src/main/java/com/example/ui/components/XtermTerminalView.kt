package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import org.json.JSONTokener

enum class XtermTheme(val id: String, val label: String, val dotColor: Color) {
  CYBER("cyber", "Cyber Neon", TerminalCyan),
  MONOKAI("monokai", "Monokai", TerminalYellow),
  MATRIX("matrix", "Matrix Green", TerminalGreen),
  DRACULA("dracula", "Dracula", TerminalPurple),
  AMBER("amber", "VT220 Amber", TerminalOrange),
  SOLARIZED("solarized", "Solarized", Color(0xFF2AA198))
}

/**
 * JS -> Kotlin bridge for the real xterm.js terminal.
 * All payloads are base64-framed UTF-8 so every byte (and every escape
 * sequence) survives the boundary exactly as the guest produced it.
 */
class TerminalJsBridge(
  private val onData: (String) -> Unit,
  private val onReadyCallback: (cols: Int, rows: Int) -> Unit,
  private val onResized: (cols: Int, rows: Int) -> Unit,
  private val onModifiersChanged: (ctrl: Boolean, alt: Boolean) -> Unit,
  private val onFontSizeChanged: (px: Int) -> Unit
) {
  private val mainHandler = Handler(Looper.getMainLooper())

  @JavascriptInterface
  fun onData(base64Data: String) {
    mainHandler.post { onData(base64Data) }
  }

  @JavascriptInterface
  fun onReady(cols: Int, rows: Int) {
    mainHandler.post { onReadyCallback(cols, rows) }
  }

  @JavascriptInterface
  fun onResize(cols: Int, rows: Int) {
    mainHandler.post { onResized(cols, rows) }
  }

  @JavascriptInterface
  fun onModifiersChanged(ctrl: Boolean, alt: Boolean) {
    mainHandler.post { onModifiersChanged(ctrl, alt) }
  }

  @JavascriptInterface
  fun onFontSizeChanged(px: Int) {
    mainHandler.post { onFontSizeChanged(px) }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun XtermTerminalView(
  terminalChunks: SharedFlow<String>,
  fontSizeSp: Int,
  onTerminalData: (String) -> Unit,
  onTerminalResized: (cols: Int, rows: Int) -> Unit,
  onFontSizeChanged: (sp: Int) -> Unit,
  modifier: Modifier = Modifier
) {
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var isTerminalReady by remember { mutableStateOf(false) }
  var selectedTheme by remember { mutableStateOf(XtermTheme.CYBER) }
  var showThemeSelector by remember { mutableStateOf(false) }
  var termDimensions by remember { mutableStateOf("—") }
  var showExtraKeys by remember { mutableStateOf(true) }
  var ctrlActive by remember { mutableStateOf(false) }
  var altActive by remember { mutableStateOf(false) }
  val clipboardManager = LocalClipboardManager.current

  // Chunks that arrive before the WebView finished loading are buffered and
  // flushed in one shot once xterm.js reports ready.
  val pendingChunks = remember { ArrayDeque<String>() }

  fun pushModifiers(ctrl: Boolean, alt: Boolean) {
    webViewInstance?.evaluateJavascript("window.termSetModifiers($ctrl, $alt);", null)
  }

  fun sendKey(name: String) {
    webViewInstance?.evaluateJavascript("window.termSendKey('$name');", null)
  }

  fun sendLiteral(text: String) {
    val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    webViewInstance?.evaluateJavascript("window.termSendLiteral('$b64');", null)
  }

  val bridge = remember {
    TerminalJsBridge(
      onData = { b64 -> onTerminalData(b64) },
      onReadyCallback = { cols, rows ->
        isTerminalReady = true
        termDimensions = "${cols}x${rows}"
        onTerminalResized(cols, rows)
      },
      onResized = { cols, rows ->
        termDimensions = "${cols}x${rows}"
        onTerminalResized(cols, rows)
      },
      onModifiersChanged = { ctrl, alt ->
        ctrlActive = ctrl
        altActive = alt
      },
      onFontSizeChanged = { px ->
        // The header state is in sp; the terminal reports px (1.15 ratio).
        onFontSizeChanged(((px / 1.15f).toInt()).coerceIn(8, 28))
      }
    )
  }

  // Stream guest output into xterm.js as it arrives. Base64 (NO_WRAP) only
  // contains [A-Za-z0-9+/=], so it is safe to inline in a JS string literal.
  LaunchedEffect(Unit) {
    terminalChunks.collect { chunk ->
      val webView = webViewInstance
      if (isTerminalReady && webView != null) {
        webView.evaluateJavascript("window.termWrite(\"$chunk\");", null)
      } else {
        if (pendingChunks.size > 400) pendingChunks.removeFirst()
        pendingChunks.addLast(chunk)
      }
    }
  }

  // Flush buffered output once the terminal is ready.
  LaunchedEffect(isTerminalReady) {
    if (isTerminalReady && pendingChunks.isNotEmpty()) {
      val js = buildString {
        while (pendingChunks.isNotEmpty()) {
          append("window.termWrite(\"")
          append(pendingChunks.removeFirst())
          append("\");")
        }
      }
      webViewInstance?.evaluateJavascript(js, null)
    }
  }

  // Font size sync (Kotlin is the source of truth for the header buttons).
  LaunchedEffect(fontSizeSp, isTerminalReady) {
    if (isTerminalReady) {
      val px = (fontSizeSp * 1.15f).toInt().coerceIn(9, 32)
      webViewInstance?.evaluateJavascript("window.termSetFontSize($px);", null)
    }
  }

  // Theme sync.
  LaunchedEffect(selectedTheme, isTerminalReady) {
    if (isTerminalReady) {
      webViewInstance?.evaluateJavascript("window.termSetTheme('${selectedTheme.id}');", null)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(CyberBackground)
  ) {
    // Terminal header: window dots, size, theme, copy/paste, zoom, keyboard toggle.
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = CyberSurface,
      border = BorderStroke(1.dp, CyberBorder)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
          // Copy selection (falls back to nothing when empty).
          IconButton(
            onClick = {
              webViewInstance?.evaluateJavascript("(function(){return window.termGetSelection();})()") { result ->
                val text = result?.let { JSONTokener(it).nextValue() as? String } ?: ""
                if (text.isNotEmpty()) {
                  clipboardManager.setText(AnnotatedString(text))
                }
              }
            },
            modifier = Modifier.size(32.dp).testTag("xterm_copy")
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Copy selection",
              tint = TerminalDimText,
              modifier = Modifier.size(16.dp)
            )
          }

          // Paste clipboard into the terminal (bracketed paste via xterm).
          IconButton(
            onClick = {
              val text = clipboardManager.getText()?.text ?: ""
              if (text.isNotEmpty()) {
                val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                webViewInstance?.evaluateJavascript("window.termPaste('$b64');", null)
              }
            },
            modifier = Modifier.size(32.dp).testTag("xterm_paste")
          ) {
            Icon(
              imageVector = Icons.Default.ContentPaste,
              contentDescription = "Paste into terminal",
              tint = TerminalDimText,
              modifier = Modifier.size(16.dp)
            )
          }

          // Extra keys row toggle.
          IconButton(
            onClick = { showExtraKeys = !showExtraKeys },
            modifier = Modifier.size(32.dp).testTag("xterm_toggle_input")
          ) {
            Icon(
              imageVector = Icons.Default.Keyboard,
              contentDescription = "Toggle extra keys",
              tint = if (showExtraKeys) SecondaryEmerald else TerminalDimText,
              modifier = Modifier.size(17.dp)
            )
          }

          // Theme.
          IconButton(
            onClick = { showThemeSelector = !showThemeSelector },
            modifier = Modifier.size(32.dp).testTag("xterm_theme_button")
          ) {
            Icon(
              imageVector = Icons.Default.Palette,
              contentDescription = "Switch terminal theme",
              tint = selectedTheme.dotColor,
              modifier = Modifier.size(16.dp)
            )
          }

          // Zoom.
          IconButton(
            onClick = { onFontSizeChanged(fontSizeSp - 2) },
            modifier = Modifier.size(32.dp).testTag("xterm_font_dec")
          ) {
            Text("A-", color = TerminalWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFontFamily)
          }
          IconButton(
            onClick = { onFontSizeChanged(fontSizeSp + 2) },
            modifier = Modifier.size(32.dp).testTag("xterm_font_inc")
          ) {
            Text("A+", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFontFamily)
          }

          // Clear screen.
          IconButton(
            onClick = { webViewInstance?.evaluateJavascript("window.termClear();", null) },
            modifier = Modifier.size(32.dp).testTag("xterm_clear_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Clear,
              contentDescription = "Clear terminal screen",
              tint = TerminalDimText,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }

    // Theme selector row.
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

    // The terminal itself.
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(CyberBackground)
    ) {
      AndroidView(
        factory = { ctx ->
          createTerminalWebView(ctx, bridge).also { webViewInstance = it }
        },
        modifier = Modifier.fillMaxSize().testTag("xterm_webview")
      )
    }

    // Termux-style extra keys.
    if (showExtraKeys) {
      ExtraKeysRow(
        ctrlActive = ctrlActive,
        altActive = altActive,
        onKey = { name -> sendKey(name) },
        onLiteral = { text -> sendLiteral(text) },
        onToggleCtrl = {
          pushModifiers(!ctrlActive, altActive)
        },
        onToggleAlt = {
          pushModifiers(ctrlActive, !altActive)
        },
        modifier = Modifier
          .fillMaxWidth()
          .imePadding()
          .navigationBarsPadding()
      )
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createTerminalWebView(context: Context, bridge: TerminalJsBridge): WebView {
  return WebView(context).apply {
    layoutParams = android.view.ViewGroup.LayoutParams(
      android.view.ViewGroup.LayoutParams.MATCH_PARENT,
      android.view.ViewGroup.LayoutParams.MATCH_PARENT
    )
    setBackgroundColor(android.graphics.Color.parseColor("#06090F"))
    isFocusable = true
    isFocusableInTouchMode = true
    settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      loadWithOverviewMode = true
      cacheMode = WebSettings.LOAD_NO_CACHE
      allowFileAccess = true
      builtInZoomControls = false
      displayZoomControls = false
    }
    addJavascriptInterface(bridge, "AndroidTerminal")
    webViewClient = object : WebViewClient() {}
    loadUrl("file:///android_asset/xterm/xterm_terminal.html")
  }
}

/**
 * Termux-style touch key rows: modifiers (sticky), navigation keys and the
 * symbols terminal work needs most. Modifier keys arm the NEXT keypress from
 * the soft keyboard (Ctrl+X, Alt+Tab style chords).
 */
@Composable
fun ExtraKeysRow(
  ctrlActive: Boolean,
  altActive: Boolean,
  onKey: (String) -> Unit,
  onLiteral: (String) -> Unit,
  onToggleCtrl: () -> Unit,
  onToggleAlt: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier,
    color = CyberSurface,
    border = BorderStroke(1.dp, CyberBorder)
  ) {
    Column(modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)) {
      // Row 1: modifiers + navigation
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        ExtraKey("ESC", onKey = { onKey("ESC") }, highlighted = false)
        ExtraKey(
          "CTRL",
          onKey = onToggleCtrl,
          highlighted = ctrlActive,
          highlightedColor = TerminalRed
        )
        ExtraKey(
          "ALT",
          onKey = onToggleAlt,
          highlighted = altActive,
          highlightedColor = TerminalOrange
        )
        ExtraKey("TAB", onKey = { onKey("TAB") })
        ExtraKey("↑", onKey = { onKey("UP") })
        ExtraKey("↓", onKey = { onKey("DOWN") })
        ExtraKey("←", onKey = { onKey("LEFT") })
        ExtraKey("→", onKey = { onKey("RIGHT") })
        ExtraKey("HOME", onKey = { onKey("HOME") })
        ExtraKey("END", onKey = { onKey("END") })
        ExtraKey("PGUP", onKey = { onKey("PGUP") })
        ExtraKey("PGDN", onKey = { onKey("PGDN") })
      }

      Spacer(modifier = Modifier.height(3.dp))

      // Row 2: symbols and shortcuts developers actually type
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        ExtraKey("|", onKey = { onLiteral("|") })
        ExtraKey("/", onKey = { onLiteral("/") })
        ExtraKey("\\", onKey = { onLiteral("\\") })
        ExtraKey("-", onKey = { onLiteral("-") })
        ExtraKey("~", onKey = { onLiteral("~") })
        ExtraKey("$", onKey = { onLiteral("$") })
        ExtraKey("\"", onKey = { onLiteral("\"") })
        ExtraKey("'", onKey = { onLiteral("'") })
        ExtraKey("(", onKey = { onLiteral("(") })
        ExtraKey(")", onKey = { onLiteral(")") })
        ExtraKey(":", onKey = { onLiteral(":") })
        ExtraKey(";", onKey = { onLiteral(";") })
        ExtraKey("^C", onKey = { onKey("CTRL_C") }, highlightedColor = TerminalRed)
        ExtraKey("^D", onKey = { onKey("CTRL_D") }, highlightedColor = TerminalRed)
        ExtraKey("^L", onKey = { onKey("CTRL_L") })
        ExtraKey("^Z", onKey = { onKey("CTRL_Z") })
        ExtraKey("DEL", onKey = { onKey("DEL") })
        ExtraKey("BKSP", onKey = { onKey("BKSP") })
      }
    }
  }
}

@Composable
private fun ExtraKey(
  label: String,
  onKey: () -> Unit,
  highlighted: Boolean = false,
  highlightedColor: Color = PrimaryCyan
) {
  Box(
    modifier = Modifier
      .height(34.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(
        when {
          highlighted -> highlightedColor.copy(alpha = 0.25f)
          else -> CyberSurfaceVariant
        }
      )
      .border(
        BorderStroke(1.dp, if (highlighted) highlightedColor else CyberBorder),
        RoundedCornerShape(6.dp)
      )
      .clickable(onClick = onKey)
      .padding(horizontal = 10.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      color = if (highlighted) highlightedColor else TerminalWhite,
      fontSize = 12.sp,
      fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
      fontFamily = TerminalFontFamily
    )
  }
}
