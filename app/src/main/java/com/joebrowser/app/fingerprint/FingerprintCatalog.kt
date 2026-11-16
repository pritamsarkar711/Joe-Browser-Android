package com.joebrowser.app.fingerprint

/**
 * The tables the fingerprint engine rolls from.
 *
 * Every list here is a real-world table, not a guess: the GPU pools are ordered from the
 * newest silicon to the oldest so the "newest hardware is the most common answer" bias in
 * [FingerprintFactory] lands on a plausible machine, and the device tables pair a viewport
 * with the handset that actually ships it, so a profile can never claim a screen size that
 * no phone has.
 */
object FingerprintCatalog {

    // ---------------------------------------------------------------------------------------
    // Operating systems
    // ---------------------------------------------------------------------------------------

    const val OS_SYSTEM_DEFAULT = "System default"
    const val OS_WINDOWS_11 = "Windows 11"
    const val OS_WINDOWS_10 = "Windows 10"
    const val OS_MACOS = "macOS"
    const val OS_LINUX = "Linux"
    const val OS_ANDROID = "Android"
    const val OS_IPHONE = "iPhone"
    const val OS_IPAD = "iPad"

    val OS_LIST = listOf(
        OS_SYSTEM_DEFAULT, OS_WINDOWS_11, OS_WINDOWS_10, OS_MACOS,
        OS_LINUX, OS_ANDROID, OS_IPHONE, OS_IPAD,
    )

    fun isMac(os: String) = os.startsWith("macOS", ignoreCase = true)
    fun isLinux(os: String) = os.startsWith("Linux", ignoreCase = true)
    fun isAndroid(os: String) = os.startsWith("Android", ignoreCase = true)
    fun isIphoneOnly(os: String) = os.startsWith("iPhone", ignoreCase = true)
    fun isIos(os: String) = isIphoneOnly(os) || os.startsWith("iPad", ignoreCase = true)
    fun isMobileOs(os: String) = isAndroid(os) || isIos(os)
    fun isWindows(os: String) = os.startsWith("Windows", ignoreCase = true)
    fun isWindows11(os: String) = os.startsWith("Windows 11", ignoreCase = true)

    fun deviceTypeFor(os: String): String = when {
        isAndroid(os) || isIphoneOnly(os) -> "mobile"
        os.startsWith("iPad", ignoreCase = true) -> "tablet"
        else -> "desktop"
    }

    /** The OS the app itself is running on. Android is the only platform this build ships. */
    fun systemOsName(): String = OS_ANDROID

    /** The GPU pool key for an operating system. */
    fun gpuKey(os: String): String = when {
        isMac(os) -> "macOS"
        isLinux(os) -> "Linux"
        isAndroid(os) -> "Android"
        isIos(os) -> "iOS"
        else -> "Windows"
    }

    // ---------------------------------------------------------------------------------------
    // Screens: css width, css height, device pixel ratio
    // ---------------------------------------------------------------------------------------

    val SCREENS: Map<String, List<DoubleArray>> = mapOf(
        OS_WINDOWS_11 to listOf(
            doubleArrayOf(1920.0, 1080.0, 1.0),
            doubleArrayOf(2560.0, 1440.0, 1.0),
            doubleArrayOf(3840.0, 2160.0, 1.5),
            doubleArrayOf(1536.0, 864.0, 1.25),
            doubleArrayOf(1366.0, 768.0, 1.0),
            doubleArrayOf(1920.0, 1200.0, 1.0),
            doubleArrayOf(1280.0, 720.0, 1.0),
            doubleArrayOf(1600.0, 900.0, 1.0),
            doubleArrayOf(1440.0, 900.0, 1.0),
            doubleArrayOf(2560.0, 1600.0, 1.0),
            doubleArrayOf(3440.0, 1440.0, 1.0),
            doubleArrayOf(1280.0, 1024.0, 1.0),
            doubleArrayOf(2880.0, 1800.0, 1.5),
            doubleArrayOf(1920.0, 1080.0, 1.25),
            doubleArrayOf(1680.0, 1050.0, 1.0),
        ),
        OS_WINDOWS_10 to listOf(
            doubleArrayOf(1366.0, 768.0, 1.0),
            doubleArrayOf(1920.0, 1080.0, 1.0),
            doubleArrayOf(1600.0, 900.0, 1.0),
            doubleArrayOf(2560.0, 1440.0, 1.0),
            doubleArrayOf(1440.0, 900.0, 1.0),
            doubleArrayOf(1280.0, 720.0, 1.0),
            doubleArrayOf(1600.0, 1200.0, 1.0),
            doubleArrayOf(1920.0, 1200.0, 1.0),
            doubleArrayOf(1280.0, 1024.0, 1.0),
            doubleArrayOf(1024.0, 768.0, 1.0),
            doubleArrayOf(2560.0, 1080.0, 1.0),
            doubleArrayOf(3840.0, 2160.0, 1.5),
        ),
        OS_MACOS to listOf(
            doubleArrayOf(1512.0, 982.0, 2.0),
            doubleArrayOf(1728.0, 1117.0, 2.0),
            doubleArrayOf(1440.0, 900.0, 2.0),
            doubleArrayOf(2560.0, 1440.0, 2.0),
            doubleArrayOf(2304.0, 1296.0, 2.0),
            doubleArrayOf(1680.0, 1050.0, 2.0),
            doubleArrayOf(1920.0, 1080.0, 2.0),
            doubleArrayOf(2560.0, 1600.0, 2.0),
            doubleArrayOf(3024.0, 1964.0, 2.0),
            doubleArrayOf(1440.0, 960.0, 2.0),
        ),
        OS_LINUX to listOf(
            doubleArrayOf(1920.0, 1080.0, 1.0),
            doubleArrayOf(1366.0, 768.0, 1.0),
            doubleArrayOf(2560.0, 1440.0, 1.0),
            doubleArrayOf(1600.0, 900.0, 1.0),
            doubleArrayOf(3840.0, 2160.0, 1.0),
            doubleArrayOf(1280.0, 1024.0, 1.0),
            doubleArrayOf(1920.0, 1200.0, 1.0),
            doubleArrayOf(1680.0, 1050.0, 1.0),
        ),
        OS_ANDROID to listOf(
            // The viewport and the scale belong together: the physical panel is the
            // viewport multiplied by the scale, and a pair that does not multiply out to
            // a panel anybody ships is a contradiction a page can find.
            //
            // The order is the order of how often the handset is met on the street. The
            // common ones come first, because a rare identity is an identity that a
            // checker's table of real machines will not have, and a machine it has never
            // seen looks unreliable however well it is put together.
            doubleArrayOf(412.0, 915.0, 2.625),
            doubleArrayOf(412.0, 915.0, 2.625),
            doubleArrayOf(360.0, 780.0, 3.0),
            doubleArrayOf(360.0, 780.0, 3.0),
            doubleArrayOf(393.0, 873.0, 2.75),
            doubleArrayOf(393.0, 873.0, 2.75),
            doubleArrayOf(384.0, 824.0, 3.0),
            doubleArrayOf(384.0, 824.0, 3.0),
            doubleArrayOf(411.0, 915.0, 2.625),
            doubleArrayOf(412.0, 892.0, 3.5),
            doubleArrayOf(384.0, 854.0, 3.0),
            doubleArrayOf(411.0, 869.0, 3.5),
            doubleArrayOf(411.0, 891.0, 3.5),
            doubleArrayOf(427.0, 952.0, 3.0),
        ),
        OS_IPHONE to listOf(
            doubleArrayOf(393.0, 852.0, 3.0),
            doubleArrayOf(390.0, 844.0, 3.0),
            doubleArrayOf(430.0, 932.0, 3.0),
            doubleArrayOf(420.0, 912.0, 3.0),
            doubleArrayOf(375.0, 812.0, 3.0),
        ),
        OS_IPAD to listOf(
            doubleArrayOf(820.0, 1180.0, 2.0),
            doubleArrayOf(1024.0, 1366.0, 2.0),
            doubleArrayOf(1194.0, 834.0, 2.0),
            doubleArrayOf(744.0, 1133.0, 2.0),
        ),
    )

    // ---------------------------------------------------------------------------------------
    // WebGL adapters: vendor, renderer. Index 0 is the newest hardware.
    // ---------------------------------------------------------------------------------------

    val GPUS: Map<String, List<Pair<String, String>>> = mapOf(
        "Windows" to listOf(
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 5090 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 5080 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 5070 Ti Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 5070 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 4090 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 4080 SUPER Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 4070 SUPER Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 4070 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 4060 Ti Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 4060 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 3080 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 3070 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 5060 Laptop GPU Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce GTX 1660 SUPER Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 9070 XT Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 9070 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 7900 XTX Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 7800 XT Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 7700 XT Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 7600 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 6800 XT Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 580 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon 890M (Ryzen 9000 Series) Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon 780M (Ryzen 7000 Series) Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon Graphics (Raphael) Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Intel(R) Arc(TM) B580 Graphics Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Intel(R) Arc(TM) A770 Graphics Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Intel(R) Arc(TM) A380 Graphics Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Intel(R) Iris(R) Xe Graphics Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Intel(R) UHD Graphics 770 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Intel(R) UHD Graphics 630 Direct3D11 vs_5_0 ps_5_0, D3D11)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Intel(R) HD Graphics 620 Direct3D11 vs_5_0 ps_5_0, D3D11)",
        ),
        "macOS" to listOf(
            "Google Inc. (Apple)" to "ANGLE (Apple, ANGLE Metal Renderer: Apple M4 Max, Unspecified Version)",
            "Google Inc. (Apple)" to "ANGLE (Apple, ANGLE Metal Renderer: Apple M4 Pro, Unspecified Version)",
            "Google Inc. (Apple)" to "ANGLE (Apple, ANGLE Metal Renderer: Apple M4, Unspecified Version)",
            "Google Inc. (Apple)" to "ANGLE (Apple, ANGLE Metal Renderer: Apple M3 Pro, Unspecified Version)",
            "Google Inc. (Apple)" to "ANGLE (Apple, ANGLE Metal Renderer: Apple M3, Unspecified Version)",
            "Google Inc. (Apple)" to "ANGLE (Apple, ANGLE Metal Renderer: Apple M2, Unspecified Version)",
            "Google Inc. (Apple)" to "ANGLE (Apple, ANGLE Metal Renderer: Apple M1, Unspecified Version)",
            "Google Inc. (Intel)" to "ANGLE (Intel, ANGLE Metal Renderer: Intel(R) Iris(TM) Plus Graphics 655, Unspecified Version)",
            "Google Inc. (AMD)" to "ANGLE (AMD, ANGLE Metal Renderer: AMD Radeon Pro 5500M, Unspecified Version)",
        ),
        "Linux" to listOf(
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 4070 SUPER (0x00002705), OpenGL 4.6)",
            "Google Inc. (NVIDIA)" to "ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 (0x00002503), OpenGL 4.6)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 7900 XT (gfx1101, LLVM 19.1.1, DRM 3.59, 6.11.0-19-generic), OpenGL 4.6)",
            "Google Inc. (AMD)" to "ANGLE (AMD, AMD Radeon RX 6600 (NAVI23, LLVM 19.1.1, DRM 3.59, 6.11.0-19-generic), OpenGL 4.6)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Mesa Intel(R) Arc(TM) A770 Graphics (ADLM GT2), OpenGL 4.6)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Mesa Intel(R) Xe Graphics (TGL GT2), OpenGL 4.6)",
            "Google Inc. (Intel)" to "ANGLE (Intel, Mesa Intel(R) UHD Graphics 620 (KBL GT2), OpenGL 4.6)",
        ),
        "Android" to listOf(
            "Google Inc. (Qualcomm)" to "ANGLE (Qualcomm, Adreno (TM) 830, OpenGL ES 3.2)",
            "Google Inc. (ARM)" to "ANGLE (ARM, Immortalis-G720, OpenGL ES 3.2)",
            "Google Inc. (ARM)" to "ANGLE (ARM, Mali-G720, OpenGL ES 3.2)",
            "Google Inc. (Qualcomm)" to "ANGLE (Qualcomm, Adreno (TM) 750, OpenGL ES 3.2)",
            "Google Inc. (ARM)" to "ANGLE (ARM, Mali-G715, OpenGL ES 3.2)",
            "Google Inc. (Qualcomm)" to "ANGLE (Qualcomm, Adreno (TM) 730, OpenGL ES 3.2)",
            "Google Inc. (ARM)" to "ANGLE (ARM, Mali-G57, OpenGL ES 3.2)",
            "Google Inc. (ARM)" to "ANGLE (ARM, Mali-G76, OpenGL ES 3.2)",
        ),
        "iOS" to listOf(
            "Apple Inc." to "Apple GPU",
        ),
    )

    /** Infers the vendor that goes with a renderer string. Order matters: NVIDIA before AMD. */
    fun vendorForRenderer(renderer: String): String? {
        val r = renderer.lowercase()
        return when {
            r.contains("nvidia") -> "Google Inc. (NVIDIA)"
            r.contains("amd") -> "Google Inc. (AMD)"
            r.contains("intel") -> "Google Inc. (Intel)"
            r.contains("apple") -> "Google Inc. (Apple)"
            r.contains("mali") -> "Google Inc. (ARM)"
            else -> null
        }
    }

    // ---------------------------------------------------------------------------------------
    // Devices: viewport key -> { model, android version, gl vendor, gl renderer }
    // ---------------------------------------------------------------------------------------

    /**
     * The handsets a viewport belongs to.
     *
     * A profile that claims a screen size no phone has is a contradiction, and a profile
     * that claims the screen of one handset and the graphics of another is a worse one.
     * Each row therefore carries the handset, the release it runs, and the adapter it
     * actually ships with. The list is ordered by how common the handset is, because the
     * common ones are the ones a profile is most likely to be compared against.
     */
    val ANDROID_DEVICES: Map<String, List<String>> = mapOf(
        "412x915" to listOf("Pixel 7", "14", "Google Inc. (ARM)", "ANGLE (ARM, Mali-G710, OpenGL ES 3.2)"),
        "360x780" to listOf("SM-A155F", "15", "Google Inc. (ARM)", "ANGLE (ARM, Mali-G57, OpenGL ES 3.2)"),
        "393x873" to listOf("220333QAG", "14", "Google Inc. (Qualcomm)", "ANGLE (Qualcomm, Adreno (TM) 619, OpenGL ES 3.2)"),
        "384x824" to listOf("SM-A546B", "14", "Google Inc. (ARM)", "ANGLE (ARM, Mali-G68, OpenGL ES 3.2)"),
        "411x915" to listOf("Pixel 9", "16", "Google Inc. (ARM)", "ANGLE (ARM, Mali-G715, OpenGL ES 3.2)"),
        "412x892" to listOf("2201123G", "14", "Google Inc. (Qualcomm)", "ANGLE (Qualcomm, Adreno (TM) 740, OpenGL ES 3.2)"),
        "384x854" to listOf("CPH2451", "14", "Google Inc. (Qualcomm)", "ANGLE (Qualcomm, Adreno (TM) 740, OpenGL ES 3.2)"),
        "411x869" to listOf("SM-G973F", "12", "Google Inc. (ARM)", "ANGLE (ARM, Mali-G76, OpenGL ES 3.2)"),
        "411x891" to listOf("SM-S931B", "15", "Google Inc. (Qualcomm)", "ANGLE (Qualcomm, Adreno (TM) 750, OpenGL ES 3.2)"),
        "427x952" to listOf("Pixel 9 Pro XL", "16", "Google Inc. (ARM)", "ANGLE (ARM, Mali-G715, OpenGL ES 3.2)"),
    )

    val ANDROID_TABLETS: Map<String, List<String>> = mapOf(
        "600x960" to listOf("SM-X510", "15", "Google Inc. (ARM)", "ANGLE (ARM, Mali-G68, OpenGL ES 3.2)"),
        "800x1280" to listOf("SM-X810", "16", "Google Inc. (Qualcomm)", "ANGLE (Qualcomm, Adreno (TM) 830, OpenGL ES 3.2)"),
        "900x1440" to listOf("SM-X920", "16", "Google Inc. (Qualcomm)", "ANGLE (Qualcomm, Adreno (TM) 830, OpenGL ES 3.2)"),
    )

    /** iOS devices: viewport key -> { model, ios version underscored, ios version dotted }. */
    val IOS_DEVICES: Map<String, List<String>> = mapOf(
        "393x852" to listOf("iPhone 16", "26_1", "26.1"),
        "390x844" to listOf("iPhone 15", "26_1", "26.1"),
        "430x932" to listOf("iPhone 16 Pro Max", "26_1", "26.1"),
        "420x912" to listOf("iPhone 17 Pro", "26_2", "26.2"),
        "375x812" to listOf("iPhone 13 mini", "26_1", "26.1"),
        "820x1180" to listOf("iPad Air 11", "26_1", "26.1"),
        "1024x1366" to listOf("iPad Pro 13", "26_1", "26.1"),
        "1194x834" to listOf("iPad Pro 11", "26_1", "26.1"),
        "744x1133" to listOf("iPad mini 7", "26_1", "26.1"),
    )

    val ANDROID_VERSIONS = listOf("auto", "16", "15", "14", "13", "12", "11")
    val IOS_VERSIONS = listOf("auto", "26.2", "26.1", "18.3", "17.6", "16.7")

    // ---------------------------------------------------------------------------------------
    // Hardware options
    // ---------------------------------------------------------------------------------------

    val CORE_OPTIONS = intArrayOf(2, 4, 6, 8, 10, 12, 16, 24)
    val MEMORY_OPTIONS = intArrayOf(2, 4, 8)

    // ---------------------------------------------------------------------------------------
    // Timezones, languages, country locales
    // ---------------------------------------------------------------------------------------

    val TIMEZONES = listOf(
        "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Toronto",
        "America/Sao_Paulo", "America/Mexico_City", "America/Bogota", "America/Argentina/Buenos_Aires",
        "Europe/London", "Europe/Dublin", "Europe/Lisbon", "Europe/Madrid", "Europe/Paris", "Europe/Berlin",
        "Europe/Rome", "Europe/Amsterdam", "Europe/Brussels", "Europe/Vienna", "Europe/Zurich", "Europe/Warsaw",
        "Europe/Stockholm", "Europe/Oslo", "Europe/Copenhagen", "Europe/Helsinki", "Europe/Kyiv", "Europe/Moscow",
        "Europe/Istanbul", "Asia/Karachi", "Asia/Kolkata", "Asia/Dubai", "Asia/Riyadh", "Asia/Jerusalem",
        "Asia/Bangkok", "Asia/Jakarta", "Asia/Manila", "Asia/Singapore", "Asia/Kuala_Lumpur", "Asia/Hong_Kong",
        "Asia/Shanghai", "Asia/Tokyo", "Asia/Seoul", "Australia/Sydney", "Pacific/Auckland", "Africa/Cairo",
        "Africa/Lagos", "Africa/Johannesburg",
    )

    val LANGUAGES = listOf(
        "en-US", "en-GB", "en-CA", "en-AU", "de-DE", "fr-FR", "es-ES", "es-MX", "pt-BR", "pt-PT",
        "it-IT", "nl-NL", "pl-PL", "sv-SE", "da-DK", "nb-NO", "fi-FI", "tr-TR", "ru-RU", "uk-UA",
        "ro-RO", "el-GR", "ar-SA", "he-IL", "hi-IN", "ja-JP", "ko-KR", "zh-CN", "zh-HK", "id-ID", "th-TH", "vi-VN", "ms-MY",
        "bn-BD", "ur-PK", "fa-IR", "sw-TZ", "mn-MN", "cs-CZ", "hu-HU", "bg-BG", "hr-HR", "sr-RS",
        "sk-SK", "sl-SI", "et-EE", "lv-LV", "lt-LT", "is-IS", "ca-ES", "eu-ES", "gl-ES", "af-ZA",
        "sq-AL", "mk-MK", "bs-BA", "mt-MT", "cy-GB", "ga-IE", "be-BY", "ka-GE", "hy-AM", "az-AZ",
        "kk-KZ", "uz-UZ", "km-KH", "lo-LA", "my-MM", "si-LK", "ne-NP", "dv-MV", "ar-EG", "ar-AE",
        "ar-QA", "ar-KW", "ar-MA", "ar-DZ", "ar-IQ", "ar-JO", "ar-LB", "ar-IQ", "fa-AF", "ps-AF",
        "zh-TW", "es-AR", "es-CL", "es-CO", "es-PE", "es-VE", "es-EC", "es-UY", "es-PY", "es-CR",
        "es-PA", "es-DO", "es-GT", "es-SV", "es-HN", "es-NI", "es-CU", "es-PR", "en-ZA", "en-NG",
        "en-KE", "en-EG", "en-TZ", "en-UG", "en-GH", "en-SN", "en-SL", "en-LR", "en-MW", "en-ZW",
        "en-BW", "en-NA", "en-ZM", "en-MU", "en-SZ", "en-LS", "en-MZ", "en-AO", "en-CM", "en-CI",
        "en-BI", "en-RW", "en-SD", "en-SO", "en-DJ", "en-ML", "en-GN", "en-TG", "en-BF", "en-SL",
        "en-NZ", "en-IE", "en-IN", "en-PK", "en-SG", "en-PH", "en-AE", "en-US", "en-CA", "en-AU",
        "en-GB", "fr-CA", "fr-BE", "fr-CH", "fr-MA", "fr-DZ", "fr-SN", "fr-CI", "fr-CD", "fr-CG",
        "fr-BF", "fr-ML", "fr-GN", "fr-TG", "pt-AO", "pt-MZ", "de-AT", "de-CH", "nl-BE", "zh-HK",
        "zh-TW", "es-MX", "en-KZ", "en-GE", "en-NP", "en-MM", "en-LK", "en-BT",
    )

    /**
     * The language that belongs to a time zone, beyond the curated list.
     *
     * A phone can be in any zone in the world, and a profile that claims the clock of
     * Dhaka while it speaks English is the kind of small contradiction that adds up. The
     * rules are checked before the general ones, most specific first.
     */
    private val EXTRA_TIMEZONE_LANGUAGE: Map<String, String> = mapOf(
        "Asia/Dhaka" to "bn-BD",
        "Asia/Karachi" to "ur-PK",
        "Asia/Kolkata" to "en-IN",
        "Asia/Colombo" to "si-LK",
        "Asia/Kathmandu" to "ne-NP",
        "Asia/Yangon" to "my-MM",
        "Asia/Bangkok" to "th-TH",
        "Asia/Ho_Chi_Minh" to "vi-VN",
        "Asia/Jakarta" to "id-ID",
        "Asia/Makassar" to "id-ID",
        "Asia/Jayapura" to "id-ID",
        "Asia/Manila" to "en-PH",
        "Asia/Singapore" to "en-SG",
        "Asia/Kuala_Lumpur" to "ms-MY",
        "Asia/Brunei" to "ms-BN",
        "Asia/Hong_Kong" to "zh-HK",
        "Asia/Macau" to "zh-HK",
        "Asia/Shanghai" to "zh-CN",
        "Asia/Urumqi" to "zh-CN",
        "Asia/Harbin" to "zh-CN",
        "Asia/Taipei" to "zh-TW",
        "Asia/Tokyo" to "ja-JP",
        "Asia/Seoul" to "ko-KR",
        "Asia/Pyongyang" to "ko-KR",
        "Asia/Jerusalem" to "he-IL",
        "Asia/Riyadh" to "ar-SA",
        "Asia/Dubai" to "ar-AE",
        "Asia/Qatar" to "ar-QA",
        "Asia/Kuwait" to "ar-KW",
        "Asia/Bahrain" to "ar-BH",
        "Asia/Baghdad" to "ar-IQ",
        "Asia/Muscat" to "ar-OM",
        "Asia/Tehran" to "fa-IR",
        "Asia/Kabul" to "fa-AF",
        "Asia/Tashkent" to "uz-UZ",
        "Asia/Almaty" to "kk-KZ",
        "Asia/Bishkek" to "ky-KG",
        "Asia/Dushanbe" to "tg-TJ",
        "Asia/Ashgabat" to "tk-TM",
        "Asia/Yerevan" to "hy-AM",
        "Asia/Baku" to "az-AZ",
        "Asia/Tbilisi" to "ka-GE",
        "Asia/Ulaanbaatar" to "mn-MN",
        "Asia/Choibalsan" to "mn-MN",
        "Asia/Novosibirsk" to "ru-RU",
        "Asia/Omsk" to "ru-RU",
        "Asia/Krasnoyarsk" to "ru-RU",
        "Asia/Irkutsk" to "ru-RU",
        "Asia/Yakutsk" to "ru-RU",
        "Asia/Vladivostok" to "ru-RU",
        "Asia/Kamchatka" to "ru-RU",
        "Asia/Magadan" to "ru-RU",
        "Asia/Sakhalin" to "ru-RU",
        "Asia/Yekaterinburg" to "ru-RU",
        "Asia/Qostanay" to "kk-KZ",
        "Asia/Phnom_Penh" to "km-KH",
        "Asia/Vientiane" to "lo-LA",
        "Asia/Dili" to "pt-TL",
        "Asia/Guam" to "en-US",
        "Pacific/Honolulu" to "en-US",
        "Pacific/Adak" to "en-US",
        "Pacific/Pago_Pago" to "en-US",
        "Pacific/Port_Moresby" to "en-PG",
        "Pacific/Guadalcanal" to "en-SB",
        "Pacific/Tarawa" to "en-TV",
        "Pacific/Tongatapu" to "to-TO",
        "Pacific/Nuku'alofa" to "to-TO",
        "Pacific/Noumea" to "fr-NC",
        "Pacific/Tahiti" to "fr-PF",
        "Pacific/Gambier" to "fr-PF",
        "Pacific/Marquesas" to "fr-PF",
        "Pacific/Chuuk" to "en-US",
        "Pacific/Pohnpei" to "en-US",
        "Pacific/Kosrae" to "en-US",
        "Pacific/Apia" to "en-WS",
        "Pacific/Fiji" to "en-FJ",
        "Pacific/Auckland" to "en-NZ",
        "Australia/Sydney" to "en-AU",
        "Australia/Melbourne" to "en-AU",
        "Australia/Brisbane" to "en-AU",
        "Australia/Adelaide" to "en-AU",
        "Australia/Perth" to "en-AU",
        "Australia/Hobart" to "en-AU",
        "Australia/Darwin" to "en-AU",
        "Australia/Lord_Howe" to "en-AU",
        "Europe/London" to "en-GB",
        "Europe/Dublin" to "en-IE",
        "Europe/Lisbon" to "pt-PT",
        "Europe/Madrid" to "es-ES",
        "Europe/Andorra" to "ca-ES",
        "Europe/Paris" to "fr-FR",
        "Europe/Brussels" to "nl-BE",
        "Europe/Amsterdam" to "nl-NL",
        "Europe/Berlin" to "de-DE",
        "Europe/Vienna" to "de-AT",
        "Europe/Zurich" to "de-CH",
        "Europe/Rome" to "it-IT",
        "Europe/Vatican" to "it-IT",
        "Europe/Prague" to "cs-CZ",
        "Europe/Bratislava" to "sk-SK",
        "Europe/Budapest" to "hu-HU",
        "Europe/Ljubljana" to "sl-SI",
        "Europe/Zagreb" to "hr-HR",
        "Europe/Belgrade" to "sr-RS",
        "Europe/Sarajevo" to "bs-BA",
        "Europe/Skopje" to "mk-MK",
        "Europe/Podgorica" to "sr-RS",
        "Europe/Tirane" to "sq-AL",
        "Europe/Athens" to "el-GR",
        "Europe/Bucharest" to "ro-RO",
        "Europe/Sofia" to "bg-BG",
        "Europe/Kiev" to "uk-UA",
        "Europe/Kyiv" to "uk-UA",
        "Europe/Riga" to "lv-LV",
        "Europe/Vilnius" to "lt-LT",
        "Europe/Tallinn" to "et-EE",
        "Europe/Minsk" to "be-BY",
        "Europe/Moscow" to "ru-RU",
        "Europe/Simferopol" to "ru-RU",
        "Europe/Volgograd" to "ru-RU",
        "Europe/Kirov" to "ru-RU",
        "Europe/Astrakhan" to "ru-RU",
        "Europe/Samara" to "ru-RU",
        "Europe/Ulyanovsk" to "ru-RU",
        "Europe/Istanbul" to "tr-TR",
        "Europe/Helsinki" to "fi-FI",
        "Europe/Stockholm" to "sv-SE",
        "Europe/Oslo" to "nb-NO",
        "Europe/Copenhagen" to "da-DK",
        "Europe/Reykjavik" to "is-IS",
        "Europe/Warsaw" to "pl-PL",
        "Europe/Monaco" to "fr-FR",
        "Europe/Malta" to "mt-MT",
        "Europe/Gibraltar" to "en-GI",
        "Atlantic/Reykjavik" to "is-IS",
        "Atlantic/Faroe" to "fo-FO",
        "Atlantic/Azores" to "pt-PT",
        "Atlantic/Cape_Verde" to "pt-CV",
        "Atlantic/Stanley" to "en-FK",
        "America/New_York" to "en-US",
        "America/Toronto" to "en-CA",
        "America/Vancouver" to "en-CA",
        "America/Edmonton" to "en-CA",
        "America/Winnipeg" to "en-CA",
        "America/Regina" to "en-CA",
        "America/Halifax" to "en-CA",
        "America/St_Johns" to "en-CA",
        "America/Detroit" to "en-US",
        "America/Chicago" to "en-US",
        "America/Denver" to "en-US",
        "America/Los_Angeles" to "en-US",
        "America/Anchorage" to "en-US",
        "America/Phoenix" to "en-US",
        "America/Mexico_City" to "es-MX",
        "America/Cancun" to "es-MX",
        "America/Monterrey" to "es-MX",
        "America/Tijuana" to "es-MX",
        "America/Guatemala" to "es-GT",
        "America/Belize" to "en-BZ",
        "America/Managua" to "es-NI",
        "America/San_José" to "es-CR",
        "America/El_Salvador" to "es-SV",
        "America/Tegucigalpa" to "en-HN",
        "America/Managua" to "es-NI",
        "America/Costa_Rica" to "es-CR",
        "America/Panama" to "es-PA",
        "America/Havana" to "es-CU",
        "America/Port-au-Prince" to "ht-HT",
        "America/Santo_Domingo" to "es-DO",
        "America/Puerto_Rico" to "es-PR",
        "America/Santo_Domingo" to "es-DO",
        "America/Bogota" to "es-CO",
        "America/Lima" to "es-PE",
        "America/La_Paz" to "es-BO",
        "America/Santiago" to "es-CL",
        "America/Asuncion" to "es-PY",
        "America/Montevideo" to "es-UY",
        "America/Guayaquil" to "es-EC",
        "America/Caracas" to "es-VE",
        "America/Argentina/Buenos_Aires" to "es-AR",
        "America/Sao_Paulo" to "pt-BR",
        "America/Bahia" to "pt-BR",
        "America/Fortaleza" to "pt-BR",
        "America/Recife" to "pt-BR",
        "America/Belem" to "pt-BR",
        "America/Manaus" to "pt-BR",
        "America/Cuiaba" to "pt-BR",
        "America/Campo_Grande" to "pt-BR",
        "America/Porto_Velho" to "pt-BR",
        "America/Boa_Vista" to "pt-BR",
        "America/Santarem" to "pt-BR",
        "America/Noronha" to "pt-BR",
        "America/Araguaina" to "pt-BR",
        "Africa/Cairo" to "ar-EG",
        "Africa/Lagos" to "en-NG",
        "Africa/Nairobi" to "en-KE",
        "Africa/Kampala" to "en-UG",
        "Africa/Kigali" to "rw-RW",
        "Africa/Dar_es_Salaam" to "sw-TZ",
        "Africa/Addis_Ababa" to "am-ET",
        "Africa/Khartoum" to "ar-SD",
        "Africa/Tripoli" to "ar-LY",
        "Africa/Tunis" to "ar-TN",
        "Africa/Algiers" to "ar-DZ",
        "Africa/Casablanca" to "ar-MA",
        "Africa/El_Aaiun" to "ar-MA",
        "Africa/Johannesburg" to "en-ZA",
        "Africa/Harare" to "en-ZW",
        "Africa/Maputo" to "pt-MZ",
        "Africa/Gaborone" to "en-BW",
        "Africa/Maseru" to "en-LS",
        "Africa/Mbabane" to "en-SZ",
        "Africa/Lusaka" to "en-ZM",
        "Africa/Windhoek" to "en-NA",
        "Africa/Luanda" to "pt-AO",
        "Africa/Brazzaville" to "fr-CG",
        "Africa/Douala" to "fr-CM",
        "Africa/Libreville" to "fr-GA",
        "Africa/Bangui" to "fr-CF",
        "Africa/Kinshasa" to "fr-CD",
        "Africa/Niamey" to "fr-NE",
        "Africa/Nouakchott" to "ar-MR",
        "Africa/Ouagadougou" to "fr-BF",
        "Africa/Bamako" to "fr-ML",
        "Africa/Dakar" to "fr-SN",
        "Africa/Banjul" to "en-GM",
        "Africa/Freetown" to "en-SL",
        "Africa/Monrovia" to "en-LR",
        "Africa/Conakry" to "fr-GN",
        "Africa/Lome" to "fr-TG",
        "Africa/Abidjan" to "fr-CI",
        "Africa/Accra" to "en-GH",
        "Africa/Lome" to "fr-TG",
        "Indian/Maldives" to "dv-MV",
        "Indian/Mauritius" to "en-MU",
        "Indian/Reunion" to "fr-RE",
        "Indian/Comoro" to "ar-KM",
        "Indian/Antananarivo" to "mg-MG",
    )

    /** Country code -> (timezone, language). */
    val COUNTRY_LOCALE: Map<String, Pair<String, String>> = mapOf(
        "US" to ("America/New_York" to "en-US"),
        "GB" to ("Europe/London" to "en-GB"),
        "CA" to ("America/Toronto" to "en-CA"),
        "AU" to ("Australia/Sydney" to "en-AU"),
        "DE" to ("Europe/Berlin" to "de-DE"),
        "FR" to ("Europe/Paris" to "fr-FR"),
        "ES" to ("Europe/Madrid" to "es-ES"),
        "IT" to ("Europe/Rome" to "it-IT"),
        "NL" to ("Europe/Amsterdam" to "nl-NL"),
        "BE" to ("Europe/Brussels" to "nl-BE"),
        "CH" to ("Europe/Zurich" to "de-CH"),
        "AT" to ("Europe/Vienna" to "de-AT"),
        "SE" to ("Europe/Stockholm" to "sv-SE"),
        "NO" to ("Europe/Oslo" to "nb-NO"),
        "DK" to ("Europe/Copenhagen" to "da-DK"),
        "FI" to ("Europe/Helsinki" to "fi-FI"),
        "PL" to ("Europe/Warsaw" to "pl-PL"),
        "PT" to ("Europe/Lisbon" to "pt-PT"),
        "IE" to ("Europe/Dublin" to "en-IE"),
        "RU" to ("Europe/Moscow" to "ru-RU"),
        "UA" to ("Europe/Kyiv" to "uk-UA"),
        "TR" to ("Europe/Istanbul" to "tr-TR"),
        "BR" to ("America/Sao_Paulo" to "pt-BR"),
        "MX" to ("America/Mexico_City" to "es-MX"),
        "AR" to ("America/Argentina/Buenos_Aires" to "es-AR"),
        "CO" to ("America/Bogota" to "es-CO"),
        "IN" to ("Asia/Kolkata" to "en-IN"),
        "PK" to ("Asia/Karachi" to "en-PK"),
        "AE" to ("Asia/Dubai" to "en-AE"),
        "SA" to ("Asia/Riyadh" to "ar-SA"),
        "IL" to ("Asia/Jerusalem" to "he-IL"),
        "SG" to ("Asia/Singapore" to "en-SG"),
        "MY" to ("Asia/Kuala_Lumpur" to "ms-MY"),
        "TH" to ("Asia/Bangkok" to "th-TH"),
        "VN" to ("Asia/Ho_Chi_Minh" to "vi-VN"),
        "ID" to ("Asia/Jakarta" to "id-ID"),
        "PH" to ("Asia/Manila" to "en-PH"),
        "HK" to ("Asia/Hong_Kong" to "zh-HK"),
        "CN" to ("Asia/Shanghai" to "zh-CN"),
        "JP" to ("Asia/Tokyo" to "ja-JP"),
        "KR" to ("Asia/Seoul" to "ko-KR"),
        "ZA" to ("Africa/Johannesburg" to "en-ZA"),
        "NG" to ("Africa/Lagos" to "en-NG"),
        "EG" to ("Africa/Cairo" to "ar-EG"),
        "BD" to ("Asia/Dhaka" to "bn-BD"),
        "PK" to ("Asia/Karachi" to "ur-PK"),
        "LK" to ("Asia/Colombo" to "si-LK"),
        "NP" to ("Asia/Kathmandu" to "ne-NP"),
        "MM" to ("Asia/Yangon" to "my-MM"),
        "KH" to ("Asia/Bangkok" to "km-KH"),
        "LA" to ("Asia/Vientiane" to "lo-LA"),
        "BN" to ("Asia/Brunei" to "ms-BN"),
        "TW" to ("Asia/Taipei" to "zh-TW"),
        "MO" to ("Asia/Macau" to "zh-HK"),
        "KP" to ("Asia/Pyongyang" to "ko-KR"),
        "IR" to ("Asia/Tehran" to "fa-IR"),
        "AF" to ("Asia/Kabul" to "fa-AF"),
        "UZ" to ("Asia/Tashkent" to "uz-UZ"),
        "KZ" to ("Asia/Almaty" to "kk-KZ"),
        "KG" to ("Asia/Bishkek" to "ky-KG"),
        "TJ" to ("Asia/Dushanbe" to "tg-TJ"),
        "TM" to ("Asia/Ashgabat" to "tk-TM"),
        "AM" to ("Asia/Yerevan" to "hy-AM"),
        "AZ" to ("Asia/Baku" to "az-AZ"),
        "GE" to ("Asia/Tbilisi" to "ka-GE"),
        "MN" to ("Asia/Ulaanbaatar" to "mn-MN"),
        "QA" to ("Asia/Qatar" to "ar-QA"),
        "KW" to ("Asia/Kuwait" to "ar-KW"),
        "BH" to ("Asia/Bahrain" to "ar-BH"),
        "IQ" to ("Asia/Baghdad" to "ar-IQ"),
        "OM" to ("Asia/Muscat" to "ar-OM"),
        "JO" to ("Asia/Amman" to "ar-JO"),
        "LB" to ("Asia/Beirut" to "ar-LB"),
        "IL" to ("Asia/Jerusalem" to "he-IL"),
        "AL" to ("Europe/Tirane" to "sq-AL"),
        "BA" to ("Europe/Sarajevo" to "bs-BA"),
        "XK" to ("Europe/Belgrade" to "sq-AL"),
        "MK" to ("Europe/Skopje" to "mk-MK"),
        "ME" to ("Europe/Podgorica" to "sr-RS"),
        "RS" to ("Europe/Belgrade" to "sr-RS"),
        "CZ" to ("Europe/Prague" to "cs-CZ"),
        "SK" to ("Europe/Bratislava" to "sk-SK"),
        "HU" to ("Europe/Budapest" to "hu-HU"),
        "SI" to ("Europe/Ljubljana" to "sl-SI"),
        "HR" to ("Europe/Zagreb" to "hr-HR"),
        "BG" to ("Europe/Sofia" to "bg-BG"),
        "RO" to ("Europe/Bucharest" to "ro-RO"),
        "GR" to ("Europe/Athens" to "el-GR"),
        "EE" to ("Europe/Tallinn" to "et-EE"),
        "LV" to ("Europe/Riga" to "lv-LV"),
        "LT" to ("Europe/Vilnius" to "lt-LT"),
        "BY" to ("Europe/Minsk" to "be-BY"),
        "MT" to ("Europe/Malta" to "mt-MT"),
        "IS" to ("Atlantic/Reykjavik" to "is-IS"),
        "LU" to ("Europe/Luxembourg" to "fr-LU"),
        "KE" to ("Africa/Nairobi" to "en-KE"),
        "UG" to ("Africa/Kampala" to "en-UG"),
        "TZ" to ("Africa/Dar_es_Salaam" to "sw-TZ"),
        "ET" to ("Africa/Addis_Ababa" to "am-ET"),
        "SD" to ("Africa/Khartoum" to "ar-SD"),
        "LY" to ("Africa/Tripoli" to "ar-LY"),
        "TN" to ("Africa/Tunis" to "ar-TN"),
        "DZ" to ("Africa/Algiers" to "ar-DZ"),
        "MA" to ("Africa/Casablanca" to "ar-MA"),
        "ZW" to ("Africa/Harare" to "en-ZW"),
        "ZM" to ("Africa/Lusaka" to "en-ZM"),
        "MZ" to ("Africa/Maputo" to "pt-MZ"),
        "AO" to ("Africa/Luanda" to "pt-AO"),
        "BW" to ("Africa/Gaborone" to "en-BW"),
        "NA" to ("Africa/Windhoek" to "en-NA"),
        "CM" to ("Africa/Douala" to "fr-CM"),
        "CI" to ("Africa/Abidjan" to "fr-CI"),
        "SN" to ("Africa/Dakar" to "fr-SN"),
        "ML" to ("Africa/Bamako" to "fr-ML"),
        "GH" to ("Africa/Accra" to "en-GH"),
        "CD" to ("Africa/Kinshasa" to "fr-CD"),
        "CG" to ("Africa/Brazzaville" to "fr-CG"),
        "MZ" to ("Africa/Maputo" to "pt-MZ"),
        "MG" to ("Indian/Antananarivo" to "mg-MG"),
        "MV" to ("Indian/Maldives" to "dv-MV"),
        "MU" to ("Indian/Mauritius" to "en-MU"),
        "GT" to ("America/Guatemala" to "es-GT"),
        "CR" to ("America/Costa_Rica" to "es-CR"),
        "PA" to ("America/Panama" to "es-PA"),
        "DO" to ("America/Santo_Domingo" to "es-DO"),
        "CU" to ("America/Havana" to "es-CU"),
        "PR" to ("America/Puerto_Rico" to "es-PR"),
        "BO" to ("America/La_Paz" to "es-BO"),
        "PE" to ("America/Lima" to "es-PE"),
        "EC" to ("America/Guayaquil" to "es-EC"),
        "UY" to ("America/Montevideo" to "es-UY"),
        "PY" to ("America/Asuncion" to "es-PY"),
        "VE" to ("America/Caracas" to "es-VE"),
        "CO" to ("America/Bogota" to "es-CO"),
        "CL" to ("America/Santiago" to "es-CL"),
        "NZ" to ("Pacific/Auckland" to "en-NZ"),
        "FJ" to ("Pacific/Fiji" to "en-FJ"),
        "WS" to ("Pacific/Apia" to "en-WS"),
        "PG" to ("Pacific/Port_Moresby" to "en-PG"),
    )

    /**
     * The language that belongs to a timezone.
     *
     * Order matters: the first matching prefix wins, so `Europe/Berlin` is matched before
     * the generic `Europe/` rules.
     */
    fun languageForTimezone(tz: String?): String {
        if (tz.isNullOrEmpty()) return "en-US"
        // The exact zone is checked first: a phone in Dhaka has to say so.
        EXTRA_TIMEZONE_LANGUAGE[tz]?.let { return it }
        // Then the region: every zone in the region shares the region's language.
        val region = tz.substringBefore('/')
        for ((zone, language) in EXTRA_TIMEZONE_LANGUAGE) {
            if (zone.startsWith("$region/")) return language
        }
        return when {
            tz.startsWith("Europe/London") -> "en-GB"
            tz.startsWith("Europe/Dublin") -> "en-IE"
            tz.startsWith("Europe/Paris") -> "fr-FR"
            tz.startsWith("Europe/Berlin") || tz.startsWith("Europe/Vienna") || tz.startsWith("Europe/Zurich") -> "de-DE"
            tz.startsWith("Europe/Madrid") -> "es-ES"
            tz.startsWith("Europe/Rome") -> "it-IT"
            tz.startsWith("Europe/Amsterdam") || tz.startsWith("Europe/Brussels") -> "nl-NL"
            tz.startsWith("Europe/Warsaw") -> "pl-PL"
            tz.startsWith("Europe/Lisbon") -> "pt-PT"
            tz.startsWith("Europe/Moscow") -> "ru-RU"
            tz.startsWith("Europe/Kyiv") -> "uk-UA"
            tz.startsWith("Europe/Bucharest") -> "ro-RO"
            tz.startsWith("Europe/Istanbul") -> "tr-TR"
            tz.startsWith("Europe/Stockholm") -> "sv-SE"
            tz.startsWith("Europe/Oslo") -> "nb-NO"
            tz.startsWith("Europe/Copenhagen") -> "da-DK"
            tz.startsWith("Europe/Helsinki") -> "fi-FI"
            tz.startsWith("Europe/Athens") -> "el-GR"
            tz.startsWith("America/Sao_Paulo") || tz.startsWith("America/Cuiaba") -> "pt-BR"
            tz.startsWith("America/Mexico_City") -> "es-MX"
            tz.startsWith("America/Argentina") -> "es-AR"
            tz.startsWith("America/Bogota") -> "es-CO"
            tz.startsWith("America/Santiago") -> "es-CL"
            tz.startsWith("America/Anchorage") || tz.startsWith("America/Los_Angeles") -> "en-US"
            tz.startsWith("America/Toronto") || tz.startsWith("America/Halifax") -> "en-CA"
            tz.startsWith("Asia/Kolkata") -> "en-IN"
            tz.startsWith("Asia/Karachi") -> "en-PK"
            tz.startsWith("Asia/Dubai") -> "en-AE"
            tz.startsWith("Asia/Riyadh") -> "ar-SA"
            tz.startsWith("Asia/Jerusalem") -> "he-IL"
            tz.startsWith("Asia/Bangkok") -> "th-TH"
            tz.startsWith("Asia/Jakarta") -> "id-ID"
            tz.startsWith("Asia/Manila") -> "en-PH"
            tz.startsWith("Asia/Singapore") -> "en-SG"
            tz.startsWith("Asia/Kuala_Lumpur") -> "ms-MY"
            tz.startsWith("Asia/Hong_Kong") -> "zh-HK"
            tz.startsWith("Asia/Shanghai") -> "zh-CN"
            tz.startsWith("Asia/Tokyo") -> "ja-JP"
            tz.startsWith("Asia/Seoul") -> "ko-KR"
            tz.startsWith("Asia/Ho_Chi_Minh") -> "vi-VN"
            tz.startsWith("Australia/") -> "en-AU"
            tz.startsWith("Pacific/Auckland") -> "en-NZ"
            tz.startsWith("Pacific/Honolulu") -> "en-US"
            tz.startsWith("Africa/Cairo") -> "ar-EG"
            tz.startsWith("Africa/Johannesburg") -> "en-ZA"
            tz.startsWith("Africa/Lagos") -> "en-NG"
            else -> "en-US"
        }
    }

    /** `en-US` -> `en-US,en;q=0.9,en;q=0.8`. */
    fun acceptLanguageFor(language: String?): String {
        if (language.isNullOrEmpty()) return "en-US,en;q=0.9"
        val dash = language.indexOf('-')
        val base = if (dash > 0) language.substring(0, dash) else language
        return "$language,$base;q=0.9,en;q=0.8"
    }

    /** The country suffix of a locale, e.g. `en-US` -> `US`. */
    fun countryForLanguage(language: String?): String {
        if (language.isNullOrEmpty()) return "US"
        val dash = language.indexOf('-')
        if (dash <= 0 || dash + 2 > language.length) return "US"
        return language.substring(dash + 1).uppercase()
    }

    // ---------------------------------------------------------------------------------------
    // Font sets
    // ---------------------------------------------------------------------------------------

    val FONT_SET_OPTIONS = listOf(
        "auto" to "Automatic",
        "windows" to "Windows",
        "macos" to "macOS",
        "linux" to "Linux",
        "android" to "Android",
        "ios" to "iOS",
        "minimal" to "Minimal",
    )

    val FONT_SETS: Map<String, List<String>> = mapOf(
        "android" to listOf(
            "Roboto", "Noto Sans", "Noto Serif", "Noto Color Emoji", "Noto Sans Devanagari",
            "Noto Sans Hebrew", "Noto Sans Thai", "Noto Naskh Arabic", "Noto Sans Bengali",
            "Noto Sans Tamil", "Noto Sans Telugu", "Noto Sans Kannada", "Noto Sans Malayalam",
            "Noto Sans Gujarati", "Noto Sans Oriya", "Noto Sans Sinhala", "Noto Sans Myanmar",
            "Noto Sans Khmer", "Noto Sans Lao", "Noto Sans Georgian", "Noto Sans Armenian",
            "Droid Sans Mono", "Cutive Mono", "Coming Soon", "Dancing Script", "Carlito",
            "Arimo", "Droid Sans", "Droid Serif", "Noto Serif", "Noto Sans Symbols",
            "Noto Sans Math", "Noto Sans CJK", "Noto Color Emoji", "Roboto Condensed",
        ),
        "ios" to listOf(
            "Helvetica Neue", "Helvetica", "San Francisco", "Georgia", "Times New Roman",
            "Courier", "Verdana", "Arial", "Arial Black", "Arial Narrow", "Arial Rounded MT Bold",
            "Menlo", "Monaco", "Courier New", "Avenir", "Avenir Next", "Futura", "Optima",
            "Palatino", "Baskerville", "Didot", "Zapfino", "Apple Color Emoji",
            "Apple SD Gothic Neo", "PingFang SC", "PingFang TC", "Hiragino Sans", "Thonburi",
            "Sarabun", "Kailasa", "Kefa", "Mishafi", "Muna", "New Athena Unicode",
            "Oriya Sangam MN", "Tamil", "Telugu", "Noto Sans", "Noto Serif", "Geeza Pro",
            "Kailasa", "STIX Two Math", "Seravek", "Chalkboard SE", "Chalkduster",
        ),
        "windows" to listOf(
            "Arial", "Calibri", "Cambria", "Candara", "Comic Sans MS", "Consolas", "Constantia", "Corbel",
            "Courier New", "Ebrima", "Franklin Gothic Medium", "Gabriola", "Gadugi", "Georgia", "Impact",
            "Leelawadee UI", "Lucida Console", "Malgun Gothic", "Microsoft Himalaya", "Microsoft JhengHei",
            "Microsoft Sans Serif", "Microsoft Tai Le", "Microsoft YaHe", "MingLiU-ExtB", "MS Gothic", "MV Boli",
            "Myanmar Text", "Nirmala UI", "Palatino Linotype", "Segoe MDL2 Assets", "Segoe Print", "Segoe Script",
            "Segoe UI", "Segoe UI Emoji", "Segoe UI Historic", "Segoe UI Symbol", "SimSun", "Sitka", "Sylfaen",
            "Symbol", "Tahoma", "Times New Roman", "Trebuchet MS", "Verdana", "Webdings", "Wingdings", "Yu Gothic",
        ),
        "macos" to listOf(
            "American Typewriter", "Andale Mono", "Apple Chancery", "Apple Color Emoji", "Arial", "Arial Black",
            "Arial Hebrew", "Arial Rounded MT Bold", "Avenir", "Avenir Next", "Baskerville", "Big Caslon", "Bodoni 72",
            "Bradley Hand", "Brush Script MT", "Chalkboard", "Chalkduster", "Cochin", "Comic Sans MS", "Copperplate",
            "Courier New", "Didot", "DIN Alternate", "Futura", "Geneva", "Georgia", "Gill Sans", "Helvetica",
            "Helvetica Neue", "Herculanum", "Hoefler Text", "Impact", "Lucida Grande", "Luminari", "Marker Felt",
            "Menlo", "Monaco", "Noteworthy", "Optima", "Palatino", "Papyrus", "Phosphate", "Rockwell", "Savoye LET",
            "SignPainter", "Skia", "Snell Roundhand", "Tahoma", "Times New Roman", "Trattatello", "Trebuchet MS",
            "Verdana", "Zapf Dingbats",
        ),
        "linux" to listOf(
            "Arial", "Bitstream Charter", "Cantarell", "Century Schoolbook L", "Comic Sans MS", "Courier New",
            "DejaVu Sans", "DejaVu Sans Mono", "DejaVu Serif", "Droid Sans", "FreeMono", "FreeSans", "FreeSerif",
            "Georgia", "Impact", "Liberation Mono", "Liberation Sans", "Liberation Serif", "Nimbus Mono PS",
            "Nimbus Roman", "Nimbus Sans", "Noto Color Emoji", "Noto Sans", "Noto Serif", "Times New Roman",
            "Trebuchet MS", "Ubuntu", "Ubuntu Mono", "Verdana",
        ),
        "minimal" to listOf(
            "Arial", "Courier New", "Georgia", "Times New Roman", "Trebuchet MS", "Verdana",
        ),
    )

    // ---------------------------------------------------------------------------------------
    // Privacy options
    // ---------------------------------------------------------------------------------------

    val WEBRTC_OPTIONS = listOf(
        "proxy" to "Proxy only",
        "public" to "Public interface",
        "off" to "Disabled",
    )

    val GEO_OPTIONS = listOf(
        "proxy" to "Follow proxy",
        "system" to "Real device",
        "custom" to "Custom coordinates",
        "block" to "Deny access",
    )

    val PASTE_SPEEDS = listOf(
        "relaxed" to "Relaxed",
        "natural" to "Natural",
        "fast" to "Fast",
        "instant" to "Instant",
    )

    /** Typing delay windows in milliseconds, per paste speed. */
    fun pasteDelays(speed: String): Pair<Int, Int> = when (speed) {
        "relaxed" -> 90 to 240
        "fast" -> 18 to 60
        "instant" -> 0 to 6
        else -> 38 to 135
    }
}
