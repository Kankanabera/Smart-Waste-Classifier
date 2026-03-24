plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hello.ecosortai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hello.ecosortai"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // Prevent the build tools from compressing the .tflite flatbuffer.
    // If compressed, MappedByteBuffer cannot mmap it directly from the APK.
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // CardView — used in activity_main.xml results card
    implementation("androidx.cardview:cardview:1.0.0")

    // RecyclerView — used for the Recent Scans history list
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // TensorFlow Lite runtime (2.6.0 is the latest before the API module split, bypassing AGP 8 crashes)
    implementation("org.tensorflow:tensorflow-lite:2.6.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Workaround for TFLite namespace collisions in AGP 8+
// TFLite deps have internal modules with identical namespaces. This rewrites them at resolution time.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.tensorflow") {
            if (requested.name == "tensorflow-lite-api") {
                useTarget("org.tensorflow:tensorflow-lite-api:2.13.0")
            }
            if (requested.name == "tensorflow-lite-support-api") {
                useTarget("org.tensorflow:tensorflow-lite-support-api:0.4.4")
            }
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        afterEvaluate {
            val taskPrefix = variant.name.replaceFirstChar { it.uppercase() }
            tasks.named("process${taskPrefix}MainManifest") {
                doFirst {
                    val aars = project.configurations.getByName("${variant.name}RuntimeClasspath")
                        .resolvedConfiguration.resolvedArtifacts
                        .filter { it.extension == "aar" && it.moduleVersion.id.group == "org.tensorflow" }
                    
                    aars.forEach { artifact ->
                        val manifestFile = file("${artifact.file.parent}/AndroidManifest.xml")
                        if (manifestFile.exists()) {
                            var content = manifestFile.readText()
                            if (artifact.moduleVersion.id.name.endsWith("-api")) {
                                content = content.replace("package=\"org.tensorflow.lite\"", "package=\"org.tensorflow.lite.api\"")
                                content = content.replace("package=\"org.tensorflow.lite.support\"", "package=\"org.tensorflow.lite.support.api\"")
                                manifestFile.writeText(content)
                            }
                        }
                    }
                }
            }
        }
    }
}
