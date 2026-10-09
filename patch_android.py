#!/usr/bin/env python3
"""Copies the Blocker sources into a fresh React Native 0.73 project and patches its Android files.
Usage: patch_android.py <project_dir> <source_dir>   (safe to run more than once)"""
import re
import shutil
import sys
from pathlib import Path

proj, src = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
here = Path(__file__).resolve().parent
main = proj / "android" / "app" / "src" / "main"
warnings = []


def read(p): return p.read_text(encoding="utf-8")
def write(p, t): p.write_text(t, encoding="utf-8")


# 1. JS side
shutil.copy(src / "App.tsx", proj / "App.tsx")
shutil.copytree(src / "src", proj / "src", dirs_exist_ok=True)

# 2. Kotlin files (use whatever package the generated project has)
app_kt = next(main.glob("java/**/MainApplication.kt"), None)
if app_kt is None:
    sys.exit("ERROR: MainApplication.kt not found. Was the project created with React Native 0.73?")
pkg = re.search(r"^package\s+([\w.]+)", read(app_kt), re.M).group(1)
for kt in (src / "android").glob("*.kt"):
    write(app_kt.parent / kt.name, re.sub(r"^package\s+[\w.]+", f"package {pkg}", read(kt), count=1, flags=re.M))
for ks in (src / "android").glob("*.keystore"):
    shutil.copy(ks, proj / "android" / "app" / ks.name)

# 3. XML resources
xml_dir = main / "res" / "xml"
xml_dir.mkdir(parents=True, exist_ok=True)
write(xml_dir / "accessibility_service_config.xml", """<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagIncludeNotImportantViews|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="20"
    android:description="@string/a11y_desc"/>
""")
write(xml_dir / "device_admin.xml", """<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <force-lock/>
    </uses-policies>
</device-admin>
""")
write(xml_dir / "device_admin_policies.xml", """<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <limit-password />
        <watch-login />
        <reset-password />
        <force-lock />
        <wipe-data />
        <expire-password />
        <encrypted-storage />
        <disable-camera />
        <disable-keyguard-features />
    </uses-policies>
</device-admin>
""")
write(xml_dir / "file_paths.xml", """<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="apk_updates" path="updates/"/>
</paths>
""")
strings = main / "res" / "values" / "strings.xml"
s = read(strings)
if "a11y_desc" not in s:
    s = s.replace("</resources>", '    <string name="a11y_desc">Blocks adult sites and selected apps, scans the screen for blocked keywords, and protects Blocker settings from tampering.</string>\n</resources>')
    write(strings, s)

# App name is always "Blocker", regardless of what name the project was initialized with.
s = read(strings)
s = re.sub(r'(<string name="app_name">)[^<]*(</string>)', r"\1Blocker\2", s, count=1)
if "app_name" not in s:
    s = s.replace("<resources>", '<resources>\n    <string name="app_name">Blocker</string>', 1)
write(strings, s)

# 3b. App icon, generated from assets/app-icon-source.png by generate_icons.py. Only the legacy
# per-density PNGs are used (both ic_launcher.png and ic_launcher_round.png are the same uncropped,
# unmasked artwork) \u2014 any adaptive-icon XML from the RN template is removed so Android doesn't
# ignore them in favor of the template's own foreground/background icon on API 26+.
icons_dir = here / "android_icons"
if icons_dir.exists():
    res_dir = main / "res"
    anydpi = res_dir / "mipmap-anydpi-v26"
    if anydpi.exists():
        shutil.rmtree(anydpi)
    for mipmap_dir in icons_dir.glob("mipmap-*"):
        dest = res_dir / mipmap_dir.name
        dest.mkdir(parents=True, exist_ok=True)
        for f in mipmap_dir.iterdir():
            shutil.copy(f, dest / f.name)
        # remove any leftover RN-template foreground/background files for this density
        for stale in ("ic_launcher_foreground.png", "ic_launcher_foreground.webp", "ic_launcher_background.webp"):
            (dest / stale).unlink(missing_ok=True)
    # Legacy per-density folders may still have the RN default mipmap in a flat "mipmap" folder on
    # some template versions; remove it so ours is used everywhere.
    for legacy in (res_dir / "mipmap").glob("ic_launcher*.png"):
        legacy.unlink()
else:
    warnings.append("No android_icons/ found \u2014 run ./generate_icons.py first to build the app icon from assets/app-icon-source.png.")



# 4. Manifest
mf = main / "AndroidManifest.xml"
t = read(mf)
if "BlockerAccessibilityService" not in t:
    if "xmlns:tools" not in t:
        t = t.replace('xmlns:android="http://schemas.android.com/apk/res/android"',
                      'xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:tools="http://schemas.android.com/tools"', 1)
    head = """<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN"/>
            <category android:name="android.intent.category.LAUNCHER"/>
        </intent>
        <intent>
            <action android:name="android.intent.action.MAIN"/>
            <category android:name="android.intent.category.HOME"/>
        </intent>
        <intent>
            <action android:name="android.settings.SETTINGS"/>
        </intent>
        <intent>
            <action android:name="android.settings.APPLICATION_DETAILS_SETTINGS"/>
            <data android:scheme="package"/>
        </intent>
    </queries>

    """
    tail = """    <service
        android:name=".BlockerAccessibilityService"
        android:exported="false"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService"/>
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config"/>
    </service>

    <receiver
        android:name=".BlockerDeviceAdminReceiver"
        android:exported="true"
        android:permission="android.permission.BIND_DEVICE_ADMIN">
        <meta-data android:name="android.app.device_admin" android:resource="@xml/device_admin"/>
        <intent-filter>
            <action android:name="android.app.action.DEVICE_ADMIN_ENABLED"/>
        </intent-filter>
    </receiver>

    <receiver
        android:name=".MyDeviceAdminReceiver"
        android:exported="true"
        android:permission="android.permission.BIND_DEVICE_ADMIN">
        <meta-data android:name="android.app.device_admin" android:resource="@xml/device_admin_policies"/>
        <intent-filter>
            <action android:name="android.app.action.DEVICE_ADMIN_ENABLED"/>
        </intent-filter>
    </receiver>
    """
    if "<application" not in t or "</application>" not in t:
        sys.exit("ERROR: unexpected AndroidManifest.xml layout")
    t = t.replace("<application", head + "<application", 1)
    t = t.replace("</application>", tail + "</application>", 1)
    write(mf, t)

# 4b. Permissions added after the first release. Kept apart from the block above (which only runs once per
# manifest) so re-running this script upgrades a manifest that was already patched.
#  - WRITE_SECURE_SETTINGS: lets the service switch USB/Wireless debugging back off. It can only be GRANTED over adb:
#  - WRITE_SECURE_SETTINGS: lets the service switch USB/Wireless debugging back off. It can only be GRANTED over adb:
#        adb shell pm grant <package> android.permission.WRITE_SECURE_SETTINGS
#  - REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: lets Settings > Background activity open the "allow background" dialog.
#  - REQUEST_INSTALL_PACKAGES: lets the app trigger in-app updates to download and install new versions.
t = read(mf)
extra = []
for perm, attrs in (
    ("android.permission.WRITE_SECURE_SETTINGS", ' tools:ignore="ProtectedPermissions"'),
    ("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", ""),
    ("android.permission.REQUEST_INSTALL_PACKAGES", ""),
):
    if perm not in t:
        extra.append(f'<uses-permission android:name="{perm}"{attrs}/>')
if extra:
    if "xmlns:tools" not in t:
        t = t.replace('xmlns:android="http://schemas.android.com/apk/res/android"',
                      'xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:tools="http://schemas.android.com/tools"', 1)
    t = t.replace("<application", "\n    ".join(extra) + "\n    <application", 1)
    write(mf, t)

# FileProvider for in-app APK installer
t = read(mf)
if "androidx.core.content.FileProvider" not in t:
    provider_xml = """    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
    """
    t = t.replace("</application>", provider_xml + "</application>", 1)
    write(mf, t)

# 5. app/build.gradle
gr = proj / "android" / "app" / "build.gradle"
g = read(gr)
if "core-ktx" not in g:
    g, n = re.subn(r"dependencies\s*\{", 'dependencies {\n    implementation("androidx.core:core-ktx:1.12.0")', g, count=1)
    if not n:
        warnings.append("Could not add core-ktx to android/app/build.gradle (add: implementation(\"androidx.core:core-ktx:1.12.0\"))")
if "debuggableVariants = []" not in g:
    g, n = re.subn(r"^react\s*\{", "react {\n    debuggableVariants = []", g, count=1, flags=re.M)
    if not n:
        warnings.append("Could not bundle JS into debug builds (add 'debuggableVariants = []' inside the react { } block)")

# versionCode/versionName, from VERSION and .build_number (set_version.py keeps these current)
version_file = here / "VERSION"
build_file = here / ".build_number"
app_version = version_file.read_text(encoding="utf-8").strip() if version_file.exists() else "1.0.0"
try:
    build_number = int(build_file.read_text(encoding="utf-8").strip()) if build_file.exists() else 0
except ValueError:
    build_number = 0
# Android requires versionCode to be a strictly positive integer (> 0)
version_code = max(1, build_number + 1)
g, n1 = re.subn(r"versionCode\s+\d+", f"versionCode {version_code}", g, count=1)
g, n2 = re.subn(r'versionName\s+"[^"]*"', f'versionName "{app_version}"', g, count=1)
if not n1 or not n2:
    warnings.append(
        f"Could not find versionCode/versionName in android/app/build.gradle to set them to "
        f"{build_number}/\"{app_version}\" \u2014 set them there by hand."
    )
if "versionCodeOverride" not in g:
    variant_hook = """
    applicationVariants.all { variant ->
        if (variant.buildType.name == "release") {
            variant.outputs.each { output ->
                output.versionCodeOverride = defaultConfig.versionCode + 100000
            }
        }
    }
}
"""
    g = re.sub(r"\n\}\s*\n(dependencies\s*\{)", variant_hook + r"\1", g, count=1)
write(gr, g)

# 6. Register the native package
a = read(app_kt)
if "BlockerPackage()" not in a:
    a, n = re.subn(r"//\s*add\(MyReactNativePackage\(\)\)", "add(BlockerPackage())", a, count=1)
    if n:
        write(app_kt, a)
    else:
        warnings.append(f"Add `add(BlockerPackage())` inside PackageList(this).packages.apply {{ ... }} in {app_kt}")

print("Patched project:", proj)
for w in warnings:
    print("WARNING:", w)
sys.exit(2 if warnings else 0)