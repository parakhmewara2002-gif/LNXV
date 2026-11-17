import glob, re
tests = failures = errors = 0
# Flavored variants write to test<Flavor>DebugUnitTest (e.g. testSideloadDebugUnitTest), so match any *UnitTest dir.
for path in glob.glob('app/build/test-results/test*UnitTest/*.xml'):
    s = open(path, encoding='utf-8').read()
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', s)
    if m:
        tests += int(m.group(1)); failures += int(m.group(3)); errors += int(m.group(4))
        name = re.search(r'<testsuite name="([^"]+)"', s)
        print(f"{m.group(1):>4} tests  {name.group(1) if name else path}")
print(f"::notice title=Unit tests::{tests} run, {failures} failed, {errors} errors")
