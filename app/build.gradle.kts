plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.zylogi_motoristas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.zylogi_motoristas"
        minSdk = 23
        targetSdk = 35
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
            // buildConfigField("String", "API_BASE_URL", "\"https://api.vblog.zylogi.com/\"")

            // Variável de ambiente para homologação
            buildConfigField("String", "API_BASE_URL", "\"https://api.homolog.zylogi.com/\"")
            // Endpoint local para desenvolvimento (usando IP direto)
            // buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3001/\"")
            // buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.11:3001/\"")
        }
        // ADICIONADO: Bloco para o modo de desenvolvimento
        debug {
            //  buildConfigField("String", "API_BASE_URL", "\"https://api.vblog.zylogi.com/\"")

            // Variável de ambiente para homologação (desenvolvimento)
            buildConfigField("String", "API_BASE_URL", "\"https://api.homolog.zylogi.com/\"")
            // Endpoint local para desenvolvimento (usando IP direto)
            // buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3001/\"")
            // buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.11:3001/\"")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // CORREÇÃO: buildFeatures movido para o local correto
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
 
}

dependencies {
    // Dependências existentes
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("com.auth0.android:jwtdecode:2.0.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // ADICIONADO: Dependências para chamadas de API (Retrofit)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(libs.play.services.cast.framework)
    implementation(libs.security.crypto)
    implementation(libs.biometric)
    implementation(libs.swiperefreshlayout)
    implementation("com.google.android.gms:play-services-location:21.0.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // ADICIONADO: Dependências para a arquitetura MVVM (ViewModel e LiveData)
    val lifecycleVersion = "2.8.3"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")

    // ADICIONADO: Dependências para funcionalidade offline
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.work.runtime)
    
    // Conectividade de rede
    implementation("androidx.core:core-ktx:1.12.0")


    // Dependências de teste
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}