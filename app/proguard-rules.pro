# Читаемые стектрейсы в крэш-репортах (маппинг: app/build/outputs/mapping/release/mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Колбэки WebViewClient / WebChromeClient вызываются фреймворком. Переопределения методов
# системных классов R8 и так сохраняет, правило — страховка при рефакторинге.
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public *;
}

# addJavascriptInterface не используется. Если добавите мост в JS, раскомментируйте:
# -keepclassmembers class * {
#     @android.webkit.JavascriptInterface <methods>;
# }
# -keepattributes JavascriptInterface
