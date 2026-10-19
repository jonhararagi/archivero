import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the keystore path explicitly to make debug APK signatures stable across runners.
// Local builds leave this unset and continue using Android's normal debug-signing behavior.
val ciDebugKeystorePath = System.getenv("ARCHIVERO_CI_DEBUG_KEYSTORE")
android {
    signingConfigs {
        create("ciDebug") {
            if (!ciDebugKeystorePath.isNullOrBlank()) {
                storeFile = file(ciDebugKeystorePath)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (!ciDebugKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("ciDebug")
            }
        }
    }
    namespace = "com.jonhararagi.archivero"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.jonhararagi.archivero"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.3.7"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

 
// Keep the original uploaded PNG unchanged. Generate a deterministic app variant
// whose hood is smooth and animal-ear-free, plus a square launcher crop of that same character.
val generatedMascotResDir = layout.buildDirectory.dir("generated/res/mascot")
android.sourceSets.getByName("main").res.srcDir(generatedMascotResDir)

val prepareMascotResources by tasks.registering {
    val sourceFile = layout.projectDirectory.file("src/main/res/drawable-nodpi/mascota_capibara.png")
    val outputRoot = generatedMascotResDir
    inputs.file(sourceFile)
    outputs.dir(outputRoot)

    doLast {
        val original = ImageIO.read(sourceFile.asFile)
            ?: error("No se pudo leer la mascota PNG original.")
        val w = original.width
        val h = original.height
        val clean = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val copy = clean.createGraphics()
        copy.composite = AlphaComposite.Src
        copy.drawImage(original, 0, 0, null)
        copy.dispose()

        fun bezier(
            p0: Pair<Double, Double>,
            p1: Pair<Double, Double>,
            p2: Pair<Double, Double>,
            p3: Pair<Double, Double>,
            t: Double
        ): Pair<Double, Double> {
            val u = 1.0 - t
            return Pair(
                u * u * u * p0.first + 3 * u * u * t * p1.first +
                    3 * u * t * t * p2.first + t * t * t * p3.first,
                u * u * u * p0.second + 3 * u * u * t * p1.second +
                    3 * u * t * t * p2.second + t * t * t * p3.second
            )
        }

        fun samples(
            p0: Pair<Double, Double>,
            p1: Pair<Double, Double>,
            p2: Pair<Double, Double>,
            p3: Pair<Double, Double>,
            count: Int = 240
        ): List<Pair<Double, Double>> = (0..count).map { i ->
            bezier(p0, p1, p2, p3, i.toDouble() / count.toDouble())
        }

        fun curve(
            p0: Pair<Double, Double>,
            p1: Pair<Double, Double>,
            p2: Pair<Double, Double>,
            p3: Pair<Double, Double>
        ) = Path2D.Double().apply {
            moveTo(p0.first, p0.second)
            curveTo(p1.first, p1.second, p2.first, p2.second, p3.first, p3.second)
        }

        fun nearestX(points: List<Pair<Double, Double>>, y: Int): Double =
            points.minByOrNull { abs(it.second - y.toDouble()) }!!.first

        fun distanceTo(points: List<Pair<Double, Double>>, x: Int, y: Int): Double =
            points.minOf { hypot(it.first - x.toDouble(), it.second - y.toDouble()) }

        val outerLeft = curve(
            Pair(142.0, 165.0), Pair(153.0, 94.0), Pair(220.0, 17.0), Pair(320.0, 12.0)
        )
        val outerRight = curve(
            Pair(320.0, 12.0), Pair(420.0, 17.0), Pair(487.0, 94.0), Pair(498.0, 165.0)
        )
        val innerLeft = curve(
            Pair(210.0, 119.0), Pair(226.0, 81.0), Pair(272.0, 39.0), Pair(320.0, 31.0)
        )
        val innerRight = curve(
            Pair(320.0, 31.0), Pair(368.0, 39.0), Pair(414.0, 81.0), Pair(451.0, 119.0)
        )
        val bottomLeft = curve(
            Pair(210.0, 119.0), Pair(185.0, 122.0), Pair(153.0, 144.0), Pair(142.0, 165.0)
        )
        val bottomRight = curve(
            Pair(498.0, 165.0), Pair(487.0, 144.0), Pair(466.0, 122.0), Pair(451.0, 119.0)
        )

        val patchShape = Path2D.Double().apply {
            moveTo(142.0, 165.0)
            curveTo(153.0, 94.0, 220.0, 17.0, 320.0, 12.0)
            curveTo(420.0, 17.0, 487.0, 94.0, 498.0, 165.0)
            curveTo(487.0, 144.0, 466.0, 122.0, 451.0, 119.0)
            curveTo(414.0, 81.0, 368.0, 39.0, 320.0, 31.0)
            curveTo(272.0, 39.0, 226.0, 81.0, 210.0, 119.0)
            curveTo(185.0, 122.0, 153.0, 144.0, 142.0, 165.0)
            closePath()
        }
        val patch = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val pg = patch.createGraphics()
        pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        pg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        pg.paint = RadialGradientPaint(
            Point2D.Float(320f, 12f),
            240f,
            floatArrayOf(0f, 0.52f, 1f),
            arrayOf(Color(139, 105, 95), Color(126, 92, 83), Color(107, 80, 73))
        )
        pg.fill(patchShape)
        pg.clip = patchShape
        pg.paint = java.awt.GradientPaint(
            0f, 75f, Color(45, 24, 20, 0),
            0f, 165f, Color(45, 24, 20, 30)
        )
        pg.fillRect(120, 70, 410, 110)
        pg.stroke = BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        pg.color = Color(62, 44, 40, 215)
        pg.draw(innerLeft)
        pg.draw(innerRight)
        pg.stroke = BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        pg.color = Color(177, 137, 125, 75)
        pg.draw(curve(
            Pair(178.0, 109.0), Pair(210.0, 59.0), Pair(268.0, 28.0), Pair(320.0, 21.0)
        ))
        pg.draw(curve(
            Pair(320.0, 21.0), Pair(372.0, 28.0), Pair(430.0, 59.0), Pair(462.0, 109.0)
        ))
        pg.dispose()

        val outerLeftSamples = samples(
            Pair(142.0, 165.0), Pair(153.0, 94.0), Pair(220.0, 17.0), Pair(320.0, 12.0)
        )
        val outerRightSamples = samples(
            Pair(320.0, 12.0), Pair(420.0, 17.0), Pair(487.0, 94.0), Pair(498.0, 165.0)
        )
        val bottomLeftSamples = samples(
            Pair(210.0, 119.0), Pair(185.0, 122.0), Pair(153.0, 144.0), Pair(142.0, 165.0), 90
        )
        val bottomRightSamples = samples(
            Pair(498.0, 165.0), Pair(487.0, 144.0), Pair(466.0, 122.0), Pair(451.0, 119.0), 90
        )

        // Feather only the lower side returns so the edited hood blends into the untouched artwork.
        for (y in 102 until min(h, 175)) {
            for (x in 120 until min(w, 520)) {
                val pixel = patch.getRGB(x, y)
                val alpha = pixel ushr 24 and 0xff
                if (alpha == 0) continue
                val d = min(distanceTo(bottomLeftSamples, x, y), distanceTo(bottomRightSamples, x, y))
                val fade = (d / 14.0).coerceIn(0.0, 1.0)
                if (fade < 1.0) {
                    val nextAlpha = (alpha * fade).roundToInt().coerceIn(0, 255)
                    patch.setRGB(x, y, (pixel and 0x00ffffff) or (nextAlpha shl 24))
                }
            }
        }

        val composite = clean.createGraphics()
        composite.composite = AlphaComposite.SrcOver
        composite.drawImage(patch, 0, 0, null)
        composite.dispose()

        // Clip the original animal-ear bumps to the newly rounded hood silhouette.
        for (y in 12 until min(h, 166)) {
            val leftX = nearestX(outerLeftSamples, y).toInt().coerceIn(0, w)
            val rightX = nearestX(outerRightSamples, y).toInt().coerceIn(0, w)
            for (x in 0 until leftX) clean.setRGB(x, y, clean.getRGB(x, y) and 0x00ffffff)
            for (x in rightX until w) clean.setRGB(x, y, clean.getRGB(x, y) and 0x00ffffff)
        }

        val outline = clean.createGraphics()
        outline.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        outline.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        outline.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        outline.color = Color(32, 24, 23, 245)
        outline.draw(outerLeft)
        outline.draw(outerRight)
        outline.dispose()

        val outputDir = outputRoot.get().asFile
        outputDir.deleteRecursively()
        val mascotDir = File(outputDir, "drawable-nodpi").apply { mkdirs() }
        val launcherDir = File(outputDir, "drawable").apply { mkdirs() }
        check(ImageIO.write(clean, "png", File(mascotDir, "mascota_capibara_clean.png"))) {
            "No se pudo escribir la mascota limpia."
        }
        val cropSize = 540
        check(w >= 590 && h >= cropSize) { "La imagen de la mascota es menor que el recorte previsto." }
        val launcher = clean.getSubimage(50, 0, cropSize, cropSize)
        check(ImageIO.write(launcher, "png", File(launcherDir, "mascota_launcher.png"))) {
            "No se pudo escribir el icono recortado de la mascota."
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareMascotResources)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core:1.15.0")
    // Keep ExoPlayer, MediaSession and PlayerView on the same Media3 release.
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-session:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
