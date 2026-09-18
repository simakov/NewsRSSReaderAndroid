# The photo viewer's AGSL sharpening is the only code in the app that names
# android.graphics.RuntimeShader / android.graphics.RenderEffect, which exist only on API 33+.
# Those references are quarantined in AgslImageSharpener (see ui/photo/ImageSharpening.kt) so that
# a device below API 33 never loads a class mentioning them: R8 had inlined the previous, inline
# `if (SDK_INT >= TIRAMISU) RuntimeShader(...)` into PhotoViewerScreen's own method body, and ART
# on an API 29 Huawei tablet then killed the app with NoClassDefFoundError the moment the photo
# screen opened - before the version check could run.
#
# Keeping the class and the interface it is reached through stops R8 from merging them into, or
# devirtualizing their calls into, code that also runs below API 33. The runtime guards in
# ImageSharpening.kt are the actual safety net; these rules keep the quarantine boundary intact so
# that net is never needed.
-keep class com.newsrssreader.ui.photo.AgslImageSharpener { *; }
-keep interface com.newsrssreader.ui.photo.GpuImageSharpener { *; }
