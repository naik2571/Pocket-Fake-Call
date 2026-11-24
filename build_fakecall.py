import os
import subprocess
import sys

project_dir = r"c:\Users\nek\Downloads\pocket_fake_call"
env = os.environ.copy()
env["JAVA_HOME"] = r"C:\Program Files\Android\Android Studio\jbr"
env["ANDROID_HOME"] = r"C:\Users\nek\AppData\Local\Android\Sdk"
env["PATH"] = os.path.join(env["JAVA_HOME"], "bin") + os.pathsep + env.get("PATH", "")

gradlew_cmd = os.path.join(project_dir, "gradlew.bat")
cmd = [gradlew_cmd, "assembleDebug", "--no-daemon", "--stacktrace"]

print(f"Executing: {' '.join(cmd)}")
process = subprocess.Popen(
    cmd,
    cwd=project_dir,
    env=env,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
    text=True,
    encoding="utf-8",
    errors="replace"
)

for line in process.stdout:
    sys.stdout.write(line)

process.wait()
print(f"Build exited with code: {process.returncode}")
sys.exit(process.returncode)
