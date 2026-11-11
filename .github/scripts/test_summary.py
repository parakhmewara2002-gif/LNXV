import glob, re
tests = failures = errors = 0
for path in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    s = open(path, encoding='utf-8').read()
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', s)
    if m:
        tests += int(m.group(1)); failures += int(m.group(3)); errors += int(m.group(4))
print(f"::notice title=Unit tests::{tests} run, {failures} failed, {errors} errors")
