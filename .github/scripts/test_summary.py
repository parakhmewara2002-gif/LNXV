import glob, re
import xml.etree.ElementTree as ET
tests = failures = errors = 0
# Flavored variants write to test<Flavor>DebugUnitTest (e.g. testSideloadDebugUnitTest), so match any *UnitTest dir.
for path in glob.glob('app/build/test-results/test*UnitTest/*.xml'):
    s = open(path, encoding='utf-8').read()
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', s)
    if m:
        tests += int(m.group(1)); failures += int(m.group(3)); errors += int(m.group(4))
        name = re.search(r'<testsuite name="([^"]+)"', s)
        print(f"{m.group(1):>4} tests  {name.group(1) if name else path}")
        # Include actionable failure details in CI logs; counts alone hide the cause.
        root = ET.parse(path).getroot()
        for case in root.iter("testcase"):
            for kind in ("failure", "error"):
                failure = case.find(kind)
                if failure is not None:
                    detail = " ".join((failure.text or "").split())[:1200]
                    message = failure.attrib.get("message", "")
                    print(
                        f"::error title=Unit test {kind}::"
                        f"{case.attrib.get('classname', '')}#{case.attrib.get('name', '')}: "
                        f"{message} {detail}"
                    )
print(f"::notice title=Unit tests::{tests} run, {failures} failed, {errors} errors")
