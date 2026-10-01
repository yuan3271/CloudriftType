#!/bin/sh
# Runs the benchmark and prints only the scores, so tuning can be driven from a script (or a
# later run of this agent) instead of by asking the user to type sentences one at a time.
#
#   ./tools/tune/score.sh            # 全拼 / 首字母 top1 / top5
#
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
. /Users/astrearc/Dev/DevPath/env.sh 2>/dev/null || true
cd "$ROOT"
./gradlew testDebugUnitTest --offline --rerun-tasks --tests "*PinyinBenchmarkTest*" >/tmp/cloudrift-tune.log 2>&1 || true
python3 - <<'PY'
import glob
import xml.etree.ElementTree as ET

for path in glob.glob('app/build/test-results/**/TEST-*PinyinBenchmarkTest.xml', recursive=True):
    root = ET.parse(path).getroot()
    print(f"{root.get('tests')} 个用例, {root.get('failures')} 失败")
    for out in root.iter('system-out'):
        for line in (out.text or '').splitlines():
            if 'top1=' in line or line.strip().startswith('miss'):
                print(line)
PY
