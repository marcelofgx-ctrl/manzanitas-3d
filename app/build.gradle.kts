plugins { id("com.android.application") }
android {
 namespace = "uy.vuelos.uruguay"
 compileSdk = 35
 defaultConfig {
   applicationId = "uy.vuelos.uruguay"
   minSdk = 26
   targetSdk = 34
   versionCode = 16
   versionName = "1.12.0"
 }
 compileOptions {
   sourceCompatibility = JavaVersion.VERSION_17
   targetCompatibility = JavaVersion.VERSION_17
 }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
