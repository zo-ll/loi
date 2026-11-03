# LiteRT-LM 0.18.0 ships no keep rules. Its JNI code creates and reads SDK classes by name,
# so R8 in an app that uses loi must keep them. Issue #8 checks a minified sample build.
-keep class com.google.ai.edge.litertlm.** { *; }
