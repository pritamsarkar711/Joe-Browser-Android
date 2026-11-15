package com.joebrowser.app.browser

import android.content.Context
import com.joebrowser.app.data.model.Profile
import com.joebrowser.app.fingerprint.FingerprintCatalog
import com.joebrowser.app.fingerprint.FingerprintFactory
import com.joebrowser.app.fingerprint.UserAgentBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds the scripts that are injected into every page.
 *
 * Three pieces come together: the fingerprint runtime, the tools runtime and the worker
 * runtime. The worker runtime is folded into the fingerprint runtime as a string, because
 * a Blob that carries JavaScript is the only door into a worker and the page runtime is
 * what wraps the Blob constructor.
 */
class RuntimeBuilder(private val context: Context) {

    private val pageTemplate: String by lazy { asset("runtime/page.js") }
    private val workerTemplate: String by lazy { asset("runtime/worker.js") }
    private val toolsTemplate: String by lazy { asset("runtime/tools.js") }

    private fun asset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    /**
     * The fingerprint runtime, with the configuration folded in.
     */
    fun buildFingerprintScript(profile: Profile): String {
        val fp = profile.fp
        val resolved = UserAgentBuilder.resolve(fp, profile.browserKind, profile.browserVersion)
        val workerConfig = buildJsonObject {
            put("ua", resolved.userAgent)
            put("platform", resolved.platform)
            put("language", fp.language)
            put("languages", buildJsonArray {
                val dash = fp.language.indexOf('-')
                if (dash > 0) {
                    add(JsonPrimitive(fp.language))
                    add(JsonPrimitive(fp.language.substring(0, dash)))
                } else {
                    add(JsonPrimitive(fp.language))
                }
            })
            put("cores", fp.cores)
            put("memory", minOf(8, if (fp.memory > 0) fp.memory else 8))
            put("touch", fp.touchPoints)
            put("tz", fp.timezone)
            put("brand", resolved.brand)
            put("glVendor", fp.webGlVendor)
            put("glRenderer", fp.webGlRenderer)
            put("canvas", fp.canvasNoise)
            put("seed", fp.seed)
            put("safari", FingerprintCatalog.isIos(fp.os))
        }

        val workerScript = workerTemplate.replace("__CFG__", workerConfig.toString())

        val config = buildJsonObject {
            put("ua", resolved.userAgent)
            put("platform", resolved.platform)
            put("language", fp.language)
            put("languages", buildJsonArray {
                val dash = fp.language.indexOf('-')
                if (dash > 0) {
                    add(JsonPrimitive(fp.language))
                    add(JsonPrimitive(fp.language.substring(0, dash)))
                } else {
                    add(JsonPrimitive(fp.language))
                }
            })
            put("cores", fp.cores)
            put("memory", minOf(8, if (fp.memory > 0) fp.memory else 8))
            put("sw", fp.screenW)
            put("sh", fp.screenH)
            put("aw", fp.availW)
            put("ah", fp.availH)
            put("cd", if (fp.colorDepth > 0) fp.colorDepth else 24)
            put("dpr", fp.pixelRatio)
            put("glVendor", fp.webGlVendor)
            put("glRenderer", fp.webGlRenderer)
            put("canvas", fp.canvasNoise)
            put("audio", fp.audioNoise)
            put("fonts", fp.fontsNoise)
            put("dnt", if (fp.doNotTrack) JsonPrimitive("1") else JsonNull)
            put("battery", fp.spoofBattery)
            put("seed", fp.seed)
            put("touch", fp.touchPoints)
            put("sbw", FingerprintFactory.scrollbarWidthFor(fp))
            put("mobile", fp.deviceType != "desktop")
            put("safari", FingerprintCatalog.isIos(fp.os))
            put("webrtc", fp.webRtcMode.lowercase())
            put("brand", resolved.brand)
            put("os", fp.os)
            put("tz", fp.timezone)
            put("geo", fp.geoMode)
            put("fontSet", fp.fontSet.ifEmpty { "auto" })
            put("brands", brandsJson(resolved.brands))
            put("fullVersionList", brandsJson(resolved.fullVersionList))
            put("uaFullVersion", resolved.fullVersion)
            put("platformVersion", resolved.platformVersion)
            put("architecture", resolved.architecture)
            put("bitness", resolved.bitness)
            put("model", resolved.model)
            put("wow64", resolved.wow64)
            put("wjs", workerScript)
        }

        val fonts = buildJsonObject {
            for ((key, list) in FingerprintCatalog.FONT_SETS) {
                put(key, buildJsonArray { list.forEach { add(JsonPrimitive(it)) } })
            }
        }

        val script = pageTemplate
            .replace("__CFG__", config.toString())
            .replace("__FONTS__", fonts.toString())

        // The generated runtime is written next to the profile so it can be read when
        // something in it has to be diagnosed.
        runCatching {
            val file = java.io.File(com.joebrowser.app.core.AppPaths.root(context), "runtime-fingerprint.js")
            file.writeText(script)
        }
        return script
    }

    private fun brandsJson(list: List<Pair<String, String>>): JsonArray = buildJsonArray {
        for ((brand, version) in list) {
            add(buildJsonObject {
                put("brand", brand)
                put("version", version)
            })
        }
    }

    /**
     * The tools runtime: Paste as Human, the proxy guard and the right-click guard.
     */
    fun buildToolsScript(profile: Profile): String {
        val tools = profile.tools
        val config = buildJsonObject {
            put("humanPaste", tools.humanPaste)
            put("speed", tools.humanPasteSpeed)
            put("watch", tools.proxyWatch)
            put("hasProxy", profile.proxy.enabled)
            put("interval", tools.proxyWatchInterval.coerceIn(30, 900))
            put("rightClick", tools.enableRightClick)
            put("unlockSelect", tools.unlockSelection)
            put("profile", profile.name)
        }
        val script = toolsTemplate.replace("__TOOLS__", config.toString())
        runCatching {
            val file = java.io.File(com.joebrowser.app.core.AppPaths.root(context), "runtime-tools.js")
            file.writeText(script)
        }
        return script
    }

    /** The script that types a string into the focused field, character by character. */
    fun buildPasteScript(text: String): String {
        val json = kotlinx.serialization.json.Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(text))
        return "try{window.__jbPasteText($json);}catch(e){}"
    }
}
