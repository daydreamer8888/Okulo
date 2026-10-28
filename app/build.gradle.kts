import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

val runtimeAar = rootProject.layout.projectDirectory.file(".gradle/okulo-runtime/onnxruntime-android.aar")

abstract class PrepareLicenseAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val notices: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copyNotices() {
        val directory = outputDirectory.get().asFile
        directory.mkdirs()
        notices.files.forEach { it.copyTo(directory.resolve(it.name), overwrite = true) }
    }
}

android {
    namespace = "com.example.okulo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.okulo"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.2.0"
        ndk {
            abiFilters += providers.gradleProperty("okuloAbis").orElse("arm64-v8a").get().split(",")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    sourceSets.getByName("test").resources.srcDir("src/androidTest/assets")
    sourceSets.getByName("test").java.srcDir("src/sharedTest/java")
    sourceSets.getByName("androidTest").java.srcDir("src/sharedTest/java")
    sourceSets.getByName("main").assets.srcDir(
        providers.gradleProperty("okuloModelAssets").orElse("model-assets")
    )
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "1g" }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(files(runtimeAar))
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.onnxruntime.jvm)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    detektPlugins(libs.detekt.formatting)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt.yml"))
    ignoreFailures = false
}

val modelAssetRoot = providers.gradleProperty("okuloModelAssets").orElse("model-assets")
val verifyRuntime by tasks.registering {
    inputs.file(runtimeAar)
    doLast {
        check(runtimeAar.asFile.isFile) {
            "Missing Android runtime. See docs/model-assets.md to prepare inference dependencies."
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyRuntime) }

val verifyModelAssets by tasks.registering {
    val models = listOf("s2c.onnx", "yolo26n.onnx")
    inputs.files(models.map { file("${modelAssetRoot.get()}/models/$it") })
    doLast {
        models.forEach { name ->
            check(file("${modelAssetRoot.get()}/models/$name").isFile) {
                "Missing $name. See docs/model-assets.md to prepare model assets."
            }
        }
    }
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(verifyModelAssets)
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val notices = tasks.register<PrepareLicenseAssets>(
            "prepare${variant.name.replaceFirstChar { it.uppercase() }}LicenseAssets"
        ) {
            notices.from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(notices, PrepareLicenseAssets::outputDirectory)
    }
}
