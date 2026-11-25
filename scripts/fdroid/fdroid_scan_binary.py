"""F-Droid APK scan. Usage: python -I fdroid_scan_binary.py <apk>."""
import sys
from fdroidserver import common, scanner
common.config = {"sdk_path": "/opt/android-sdk"}
common.fill_config_defaults(common.config)
scanner.config = common.config
scanner.options = type("O", (), {"json": False, "verbose": False, "quiet": True})()
print("scan_binary problems:", scanner.scan_binary(sys.argv[1]))
