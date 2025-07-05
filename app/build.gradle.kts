plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.zylogi_motoristas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.zylogi_motoristas"
        minSdk = 21
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
            // Variável de ambiente para produção (exemplo)
            buildConfigField("String", "API_BASE_URL", "\"https://api.producao.com/\"")
        }
        // ADICIONADO: Bloco para o modo de desenvolvimento
        debug {
            // Variável de ambiente para desenvolvimento (localhost do seu PC)
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3001/\"")
        }
    }
    compileOptions {
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

    // ADICIONADO: Dependências para chamadas de API (Retrofit)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // ADICIONADO: Dependências para a arquitetura MVVM (ViewModel e LiveData)
    val lifecycleVersion = "2.8.3"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")


    // Dependências de teste
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}