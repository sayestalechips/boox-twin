#!/usr/bin/env python3
"""
PalmaMirror Phase Gate Check
Validates that phase requirements are met before committing.
Usage: python scripts/gate_check.py --phase N
"""

import argparse
import os
import sys
import subprocess

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def check_file_exists(path, description):
    full_path = os.path.join(PROJECT_ROOT, path)
    exists = os.path.exists(full_path)
    status = "PASS" if exists else "FAIL"
    print(f"  [{status}] {description}: {path}")
    return exists


def check_dir_has_files(path, min_count, description):
    full_path = os.path.join(PROJECT_ROOT, path)
    if not os.path.isdir(full_path):
        print(f"  [FAIL] {description}: directory {path} not found")
        return False
    count = len([f for f in os.listdir(full_path) if os.path.isfile(os.path.join(full_path, f))])
    passed = count >= min_count
    status = "PASS" if passed else "FAIL"
    print(f"  [{status}] {description}: {count}/{min_count} files in {path}")
    return passed


def check_apk_exists():
    apk_path = os.path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
    exists = os.path.exists(apk_path)
    if exists:
        size_mb = os.path.getsize(apk_path) / (1024 * 1024)
        print(f"  [PASS] Debug APK exists ({size_mb:.1f} MB)")
        return True
    else:
        print("  [FAIL] Debug APK not found — run ./gradlew assembleDebug")
        return False


def check_apk_size(max_mb=20):
    for variant in ["release", "debug"]:
        apk_path = os.path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", variant, f"app-{variant}.apk")
        if os.path.exists(apk_path):
            size_mb = os.path.getsize(apk_path) / (1024 * 1024)
            passed = size_mb < max_mb
            status = "PASS" if passed else "FAIL"
            print(f"  [{status}] APK size: {size_mb:.1f} MB (max {max_mb} MB)")
            return passed
    print("  [FAIL] No APK found")
    return False


def run_tests():
    print("  Running tests...")
    result = subprocess.run(
        ["./gradlew", "test", "--no-daemon", "--max-workers=1"],
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True,
        env={**os.environ, "ANDROID_HOME": os.path.expanduser("~/android-sdk")}
    )
    passed = result.returncode == 0
    status = "PASS" if passed else "FAIL"
    print(f"  [{status}] Unit tests")
    if not passed:
        # Print failure details
        for line in result.stdout.split("\n"):
            if "FAILED" in line:
                print(f"    {line.strip()}")
    return passed


def gate_phase_0():
    print("\n=== Phase 0: Research & Environment Setup ===\n")
    results = []
    results.append(check_dir_has_files("docs/research", 5, "Research documents"))
    results.append(check_file_exists("docs/research/ancs_protocol.md", "ANCS protocol research"))
    results.append(check_file_exists("docs/research/ancs_spec_notes.md", "ANCS spec notes"))
    results.append(check_file_exists("docs/ARCHITECTURE.md", "Architecture document"))
    results.append(check_apk_exists())
    return all(results)


def gate_phase_1():
    print("\n=== Phase 1: BLE Connection & ANCS Discovery ===\n")
    results = []
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/ble/BleConnectionManager.kt", "BleConnectionManager"))
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/ble/AncsService.kt", "AncsService"))
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/ble/BleReconnector.kt", "BleReconnector"))
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/service/MirrorForegroundService.kt", "Foreground Service"))
    results.append(check_file_exists("app/src/test/kotlin/com/stalechips/palmamirror/ble/BleConnectionManagerTest.kt", "BLE tests"))
    results.append(run_tests())
    results.append(check_apk_exists())
    return all(results)


def gate_phase_2():
    print("\n=== Phase 2: ANCS Protocol Parser ===\n")
    results = []
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/ancs/AncsEventParser.kt", "Event parser"))
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/ancs/AncsAttributeParser.kt", "Attribute parser"))
    results.append(check_file_exists("app/src/test/kotlin/com/stalechips/palmamirror/ancs/AncsParserFuzzTest.kt", "Fuzz tests"))
    results.append(run_tests())
    results.append(check_apk_exists())
    return all(results)


def gate_phase_3():
    print("\n=== Phase 3: Notification Actions ===\n")
    results = []
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/ancs/CallStateManager.kt", "Call state manager"))
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/data/CannedReplyRepository.kt", "Canned replies"))
    results.append(check_file_exists("app/src/test/kotlin/com/stalechips/palmamirror/ancs/CallStateManagerTest.kt", "Call state tests"))
    results.append(run_tests())
    results.append(check_apk_exists())
    return all(results)


def gate_phase_4():
    print("\n=== Phase 4: E-Ink Optimized UI ===\n")
    results = []
    results.append(check_file_exists("app/src/main/res/layout/activity_main.xml", "Home screen"))
    results.append(check_file_exists("app/src/main/res/layout/activity_call.xml", "Call screen"))
    results.append(check_file_exists("app/src/main/res/layout/activity_detail.xml", "Detail screen"))
    results.append(check_file_exists("app/src/main/res/layout/activity_settings.xml", "Settings screen"))
    results.append(check_file_exists("app/src/main/res/layout/item_notification.xml", "Notification card"))
    results.append(check_apk_exists())
    return all(results)


def gate_phase_5():
    print("\n=== Phase 5: Persistence, Settings & Polish ===\n")
    results = []
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/data/AppDatabase.kt", "Room database"))
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/data/NotificationDao.kt", "Notification DAO"))
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/data/PreferencesManager.kt", "Preferences"))
    results.append(check_file_exists("app/src/main/kotlin/com/stalechips/palmamirror/ui/setup/SetupWizardActivity.kt", "Setup wizard"))
    results.append(run_tests())
    results.append(check_apk_exists())
    return all(results)


def gate_phase_6():
    print("\n=== Phase 6: Integration, Security & Final Build ===\n")
    results = []
    results.append(check_file_exists("README.md", "README"))
    results.append(check_file_exists("app/src/test/kotlin/com/stalechips/palmamirror/integration/FullFlowTest.kt", "Integration tests"))
    results.append(run_tests())
    results.append(check_apk_exists())
    results.append(check_apk_size(20))
    return all(results)


GATES = {
    0: gate_phase_0,
    1: gate_phase_1,
    2: gate_phase_2,
    3: gate_phase_3,
    4: gate_phase_4,
    5: gate_phase_5,
    6: gate_phase_6,
}


def main():
    parser = argparse.ArgumentParser(description="PalmaMirror Phase Gate Check")
    parser.add_argument("--phase", type=int, required=True, help="Phase number (0-6)")
    args = parser.parse_args()

    if args.phase not in GATES:
        print(f"Unknown phase: {args.phase}")
        sys.exit(1)

    passed = GATES[args.phase]()

    print(f"\n{'=' * 50}")
    if passed:
        print(f"GATE PASSED — Phase {args.phase} is complete!")
    else:
        print(f"GATE FAILED — Phase {args.phase} has unmet requirements.")
    print(f"{'=' * 50}\n")

    sys.exit(0 if passed else 1)


if __name__ == "__main__":
    main()
