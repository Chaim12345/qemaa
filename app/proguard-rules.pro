# Keep the WebView JS bridge: methods are invoked from JavaScript by name.
-keepclassmembers class com.example.ui.components.TerminalJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# xterm.js asset filenames are resolved at runtime, not by class name.
-dontwarn com.example.**

# Robolectric/Compose ship references only used at test time.
-dontwarn org.robolectric.**
