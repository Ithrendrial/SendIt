"""Run evaluation locally, or use --project PROJECT_ID for Firebase Test Lab."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from datetime import datetime

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.example.sendit.lightweight"
METHODS = {"processing": ["aProcessing", "bReload", "zLatencyCalibration"], "interactions": ["zLatencyCalibration", "cInteractions"],
           "invalid": ["dInvalidVideo"], "footprint": ["eFootprint", "fFootprintReload"], "reload": ["bReload"],
           "calibration": ["zLatencyCalibration"], "preparation": ["yRoutePreparation"]}
METHODS["all"] = ["zLatencyCalibration", "aProcessing", "bReload", "cInteractions", "dInvalidVideo", "eFootprint", "fFootprintReload"]

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("videos", nargs="*", type=Path, default=[ROOT / "evaluation/videos" / (name + ".mp4")
                                                          for name in ("first", "second", "third")])
parser.add_argument("--suite", choices=METHODS, default="processing")
parser.add_argument("--repetitions", type=int, default=5)
parser.add_argument("--interaction-repetitions", type=int, default=30)
parser.add_argument("--phase", choices=["pilot", "main"], default="pilot")
parser.add_argument("--serial")
parser.add_argument("--project", help="Run in Test Lab using this Firebase project ID")
parser.add_argument("--device", action="append", help="Test Lab device; repeat for multiple phones (default: Pixel 7a/API 33)")
args = parser.parse_args()
args.device = args.device or ["model=lynx,version=33,locale=en,orientation=portrait"]
if args.repetitions < 1 or args.interaction_repetitions < 1:
    parser.error("Repetitions must be positive")
if any(not p.is_file() or not p.name.replace("-", "").replace("_", "").replace(".", "").isalnum() for p in args.videos):
    parser.error("Supply existing video files with simple filenames")

adb = [shutil.which("adb") or str(Path.home() / "Library/Android/sdk/platform-tools/adb")]
if args.serial:
    adb += ["-s", args.serial]

def run(command, **options):
    return subprocess.run([str(x) for x in command], check=True, **options)

app = ROOT / "app/build/outputs/apk/evaluation/app-evaluation.apk"
test = ROOT / "app/build/outputs/apk/androidTest/evaluation/app-evaluation-androidTest.apk"
names = ["sendit-eval-" + p.name for p in args.videos]
folder = ROOT.parent / "testing/lightweight-results" / datetime.now().strftime("%Y%m%d-%H%M%S")
folder.mkdir(parents=True)
assets = folder / "assets"
(assets / "evaluation").mkdir(parents=True)
for path, name in zip(args.videos, names):
    shutil.copy2(path, assets / "evaluation" / name)
invalid = assets / "evaluation/sendit-eval-invalid.mp4"
with args.videos[0].open("rb") as stream:
    invalid.write_bytes(stream.read(512))
run([ROOT / "gradlew", "-q", "-I", ROOT / "evaluation/init.gradle", "-PevaluationAssets=" + str(assets),
     ":app:assembleEvaluation", ":app:assembleEvaluationAndroidTest"], cwd=ROOT)
build = hashlib.sha256(app.read_bytes()).hexdigest()
(folder / "build.json").write_text(json.dumps({"apk_sha256": build,
    "test_apk_sha256": hashlib.sha256(test.read_bytes()).hexdigest(), "phase": args.phase, "videos": names,
    "suite": args.suite, "methods": METHODS[args.suite], "video_repetitions": args.repetitions,
    "requested_test_lab_devices": args.device if args.project else [],
    "interaction_repetitions": args.interaction_repetitions,
    "fixtures": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in args.videos},
    "model_sha256": hashlib.sha256((ROOT / "app/src/main/assets/pose_landmarker_full.task").read_bytes()).hexdigest(),
    "inference": "MediaPipe VIDEO, CPU, one pose; library default confidence thresholds",
    "sampling": "All distinct nonnegative millisecond timestamps; longest image dimension at most 640 px",
    "starting_state": "New process per test; empty attempt database per processing trial; model cache may be warm"}))
if args.project:
    environment = ("^;^videos=" + ",".join(names) + ";videoRepetitions=" + str(args.repetitions)
        + ";interactionRepetitions=" + str(args.interaction_repetitions) + ";phase=" + args.phase + ";buildId=" + build
        + ";clearPackageData=false")
    command = ["gcloud", "firebase", "test", "android", "run", "--project", args.project,
        "--app", str(app), "--test", str(test), "--use-orchestrator",
        "--test-targets", ",".join("class com.example.sendit.lightweight.EvaluationTest#" + m for m in METHODS[args.suite]),
        "--environment-variables", environment,
        "--directories-to-pull", "/sdcard/Android/data/" + PACKAGE + "/files", "--results-dir", folder.name,
        "--timeout", "45m", "--num-flaky-test-attempts", "0", "--no-record-video", "--no-performance-metrics"]
    for device in args.device:
        command += ["--device", device]
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    print(result.stdout)
    (folder / "test-lab.txt").write_text(result.stdout)
    raise SystemExit(result.returncode)

for apk in [app, test]:
    run(adb + ["install", "-r", apk])
external = "/sdcard/Android/data/" + PACKAGE + "/files/"
run(adb + ["shell", "rm", "-f", external + "evaluation.jsonl", external + "*.pftrace",
           external + "*.samples.jsonl", external + "*.config.pbtxt", external + "evaluation-ui.xml"])
failed = False
for method in METHODS[args.suite]:
    print("Running", method, flush=True)
    run(adb + ["shell", "am", "force-stop", PACKAGE])
    command = adb + ["shell", "am", "instrument", "-w", "-e", "class",
        "com.example.sendit.lightweight.EvaluationTest#" + method, "-e", "videos", ",".join(names),
        "-e", "videoRepetitions", str(args.repetitions), "-e", "interactionRepetitions", str(args.interaction_repetitions),
        "-e", "phase", args.phase, "-e", "buildId", build, PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"]
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    print(result.stdout)
    (folder / (method + ".txt")).write_text(result.stdout)
    subprocess.run(adb + ["pull", "/sdcard/Android/data/" + PACKAGE + "/files/.", str(folder / "device")])
    snapshot = folder / "device/evaluation-ui.xml"
    if snapshot.exists():
        shutil.copy2(snapshot, folder / (method + ".ui.xml"))
    failed |= result.returncode != 0 or "OK (" not in result.stdout
print("Results:", folder)
if failed:
    raise SystemExit("Tests failed; see the raw results above")
