from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
root = Path("source/BluetoothTimer")
pkg = root / "app/src/main/java/com/example/bluetoothtimer"
for name in ("MainActivity.kt", "TimerService.kt", "PrivilegedBluetooth.kt"):
    shutil.copyfile("shizuku/" + name, pkg / name)
(pkg / "BluetoothAccessibilityService.kt").unlink(missing_ok=True)
aidl = root / "app/src/main/aidl/com/example/bluetoothtimer"
aidl.mkdir(parents=True, exist_ok=True)
shutil.copyfile("shizuku/IBluetoothOff.aidl", aidl / "IBluetoothOff.aidl")
p = root / "app/build.gradle.kts"
s = p.read_text().replace("android {", """android {
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
""", 1).replace("compileSdk = 35", "compileSdk = 36").replace("targetSdk = 35", "targetSdk = 36").replace("versionCode = 1", "versionCode = 3").replace('versionName = "1.0"', 'versionName = "2.0-shizuku"')
s = s.replace("defaultConfig {", 'defaultConfig {\n        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"', 1)
s += """
dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
"""
p.write_text(s)
ns = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ns)
a = lambda key: "{" + ns + "}" + key
p = root / "app/src/main/AndroidManifest.xml"
tree = ET.parse(p)
manifest = tree.getroot()
app = manifest.find("application")
for service in list(app.findall("service")):
    if service.get(a("name")) == ".BluetoothAccessibilityService":
        app.remove(service)
seen = set()
for permission in list(manifest.findall("uses-permission")):
    name = permission.get(a("name"))
    if name in seen: manifest.remove(permission)
    seen.add(name)
for name in ("android.permission.SCHEDULE_EXACT_ALARM", "moe.shizuku.manager.permission.API_V23"):
    if name not in seen: ET.SubElement(manifest, "uses-permission", {a("name"): name})
ET.SubElement(app, "provider", {
    a("name"): "rikka.shizuku.ShizukuProvider",
    a("authorities"): "com.example.bluetoothtimer.shizuku",
    a("multiprocess"): "false", a("enabled"): "true", a("exported"): "true",
    a("permission"): "android.permission.INTERACT_ACROSS_USERS_FULL"
})
activity = app.find("activity")
activity.set(a("windowSoftInputMode"), "adjustResize")
tree.write(p, encoding="utf-8", xml_declaration=True)
testdir = root / "app/src/androidTest/java/com/example/bluetoothtimer"
testdir.mkdir(parents=True, exist_ok=True)
shutil.copyfile("shizuku/LayoutTest.kt", testdir / "LayoutTest.kt")
# Guard against accidental restoration of the old accessibility/toggle path.
assert not any(x.get(a("name")) == ".BluetoothAccessibilityService" for x in app.findall("service"))
code = (pkg / "PrivilegedBluetooth.kt").read_text()
assert '"bluetooth_manager", "disable"' in code
assert '"enable"' not in code and '"toggle"' not in code
assert "ACTION_BLUETOOTH_SETTINGS" not in (pkg / "TimerService.kt").read_text()
