/*
 * Copyright 2023 Atick Faisal
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

import org.gradle.kotlin.dsl.compileOnly
import org.gradle.kotlin.dsl.gradlePlugin
import org.gradle.kotlin.dsl.libs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.mobicloud.build.logic"

val javaVersion = libs.versions.java.get().toInt()

java {
    sourceCompatibility = JavaVersion.values()[javaVersion - 1]
    targetCompatibility = JavaVersion.values()[javaVersion - 1]
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.valueOf("JVM_$javaVersion"))
    }
}

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.dokka.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("library") {
            id = "com.mobicloud.library"
            implementationClass = "LibraryConventionPlugin"
        }
        register("uiLibrary") {
            id = "com.mobicloud.ui.library"
            implementationClass = "UiLibraryConventionPlugin"
        }
        register("application") {
            id = "com.mobicloud.application"
            implementationClass = "ApplicationConventionPlugin"
        }
        register("daggerHilt") {
            id = "com.mobicloud.dagger.hilt"
            implementationClass = "DaggerHiltConventionPlugin"
        }
        register("firebase") {
            id = "com.mobicloud.firebase"
            implementationClass = "FirebaseConventionPlugin"
        }
        register("dokka") {
            id = "com.mobicloud.dokka"
            implementationClass = "DokkaConventionPlugin"
        }
    }
}