# Prebuilt AndroidJUnitRunner calls tracing by its original binary name.
# Used only by -PemroozReleaseTests; publication shrinking is unchanged.
-keep class androidx.tracing.** { *; }
