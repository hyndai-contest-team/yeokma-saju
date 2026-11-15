# Pleos SDK는 AIDL과 리플렉션을 쓰므로 그대로 둔다
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**

# Pleos SDK 안에서 Gson이 제네릭 타입 정보(TypeToken)를 읽는다. 지우면 차량 제어가 실패한다
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.gson.**
