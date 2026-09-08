# Trailer Extraction — Maintenance Guide

Background trailers play **natively in ExoPlayer**. YouTube video IDs are resolved to direct
stream URLs with **NewPipeExtractor** (there is no WebView). Because YouTube periodically changes
its player/signature logic, extraction can break over time — when it does, the fix is almost always
a **NewPipeExtractor version bump** (no app logic changes).

## Symptom that means "bump the version"

- Trailers stop appearing on Home / Detail (backdrop stays; app does **not** crash — failure is graceful).
- Logcat shows resolver failures:
  ```
  adb logcat | grep -iE "YtTrailerResolver|ReCaptcha|newpipe.*Exception"
  # e.g. "Failed to resolve trailer <id>: ..."
  ```

## How to bump NewPipeExtractor

1. Find the latest release tag: https://github.com/TeamNewPipe/NewPipeExtractor/releases
2. Update the version in [gradle/libs.versions.toml](../gradle/libs.versions.toml):
   ```toml
   newpipeExtractor = "v0.26.5"   # <-- change to the new tag, e.g. "v0.27.0"
   ```
   It is consumed via JitPack as `com.github.teamnewpipe:NewPipeExtractor` in
   [app/build.gradle.kts](../app/build.gradle.kts).
3. Rebuild + install on a device and verify (see below).

That's it for the common case. NewPipeExtractor is pure-JVM (no native libs), so bumping is low-risk.

## Verify after bumping

```bash
# Build + install release (R8/minify is where extraction must also work)
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  ./gradlew :app:installRelease -x lint --no-daemon

# Launch, wait for a trailer to resolve, then check
D=<device-ip>:5555   # e.g. 192.168.29.211:5555
adb -s $D shell am start -n com.mystream.app/.MainActivity
sleep 16
# Expect: hardware decoders engaged, NO resolve-failure lines
adb -s $D logcat -d | grep -iE "video.decoder|aac.decoder|YtTrailerResolver"
```
Success = video/audio decoders set up and **no** "Failed to resolve" lines.

## If a version bump is not enough

If the newest release still fails (YouTube rolled out something new that NewPipe hasn't patched yet):
- Check NewPipeExtractor issues/PRs for the client change; a `-SNAPSHOT` from Maven Central snapshots
  may already contain the fix (see their README "Maven Central" section).
- As a last resort, the resolver degrades gracefully (no trailer), so the app stays fully usable.

## Key files / configuration

| Concern | Location |
|---|---|
| Version | [gradle/libs.versions.toml](../gradle/libs.versions.toml) (`newpipeExtractor`) |
| Dependency + desugaring | [app/build.gradle.kts](../app/build.gradle.kts) |
| Resolver (id → stream URL, 4h cache) | [app/src/main/java/com/mystream/app/data/youtube/YouTubeTrailerResolver.kt](../app/src/main/java/com/mystream/app/data/youtube/YouTubeTrailerResolver.kt) |
| OkHttp downloader | [app/src/main/java/com/mystream/app/data/youtube/NewPipeDownloader.kt](../app/src/main/java/com/mystream/app/data/youtube/NewPipeDownloader.kt) |
| `NewPipe.init(...)` | [app/src/main/java/com/mystream/app/MyStreamApplication.kt](../app/src/main/java/com/mystream/app/MyStreamApplication.kt) |
| Native player (ExoPlayer + PlayerView) | [app/src/main/java/com/mystream/app/ui/components/BackgroundTrailerPlayer.kt](../app/src/main/java/com/mystream/app/ui/components/BackgroundTrailerPlayer.kt) |
| PlayerView layout (texture_view, zoom) | [app/src/main/res/layout/view_background_trailer.xml](../app/src/main/res/layout/view_background_trailer.xml) |
| State holder | [app/src/main/java/com/mystream/app/ui/components/TrailerPlaybackManager.kt](../app/src/main/java/com/mystream/app/ui/components/TrailerPlaybackManager.kt) |

## Required build config (do not remove)

In [app/proguard-rules.pro](../app/proguard-rules.pro) — needed for NewPipeExtractor's Rhino JS engine under R8:
```proguard
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
```

In [app/build.gradle.kts](../app/build.gradle.kts) — core library desugaring is required because `minSdk = 30` (< 33), which NewPipeExtractor needs:
```kotlin
compileOptions { isCoreLibraryDesugaringEnabled = true }
// ...
coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
```

## Notes

- **License:** NewPipeExtractor is GPLv3; this makes the app subject to GPLv3.
- **Resolver behaviour:** prefers a muxed progressive stream (~420p target — low decode cost on weak
  TV); falls back to separate video-only + audio streams merged in ExoPlayer. Results cached ~4h
  (YouTube URLs expire ~6h).
- **No WebView:** verified on real Android TV (single app process — no sandboxed Chromium renderer).
