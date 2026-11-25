"""F-Droid source scan, as fdroiddata runs it. Usage: python -I fdroid_scan_source.py <fresh clone>. Strips the signing config first (remove_signing_keys) and deletes the Gradle wrapper, as F-Droid does; build the copy with ./gradlew -p <copy> :app:assembleRelease afterwards."""
import sys, logging
logging.basicConfig(level=logging.WARNING)
from fdroidserver import common, scanner, metadata
src = sys.argv[1]
common.config = {"sdk_path": "/opt/android-sdk"}
common.fill_config_defaults(common.config)
scanner.config = common.config
common.options = type("O", (), {"verbose": False, "quiet": True})()
scanner.options = type("O", (), {"json": False, "verbose": False, "quiet": True})()
common.remove_signing_keys(src)
build = metadata.Build()
build.versionName = "1.0.7-candidate"; build.versionCode = 8; build.subdir = "app"; build.gradle = ["yes"]
n = scanner.scan_source(src, build)
print("scan_source problems:", n)
