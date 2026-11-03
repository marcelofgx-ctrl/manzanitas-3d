plugins { id("com.android.application") }
android {
 namespace = "uy.vuelos.uruguay"
 compileSdk = 35
 defaultConfig {
   applicationId = "uy.vuelos.uruguay"
   minSdk = 26
   targetSdk = 34
   versionCode = 4
   versionName = "1.3"
 }
 compileOptions {
   sourceCompatibility = JavaVersion.VERSION_17
   targetCompatibility = JavaVersion.VERSION_17
 }
}
