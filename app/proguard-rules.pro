# Keep the input method service entry point reachable from the framework.
-keep class com.yuan3271.cloudrift.ime.CloudriftImeService { *; }

# Release stack traces stay readable. R8 strips these two attributes by default, which turns
# every logcat trace from a release build into obfuscated names with no line numbers at all -
# and the keyboard reports its own failures through Log.e, so those traces are the only way to
# diagnose a release build. `-renamesourcefileattribute` keeps the original file name out of the
# APK while `retrace` still maps the line numbers back.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
