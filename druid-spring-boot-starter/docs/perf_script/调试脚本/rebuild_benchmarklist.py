# -*- coding: utf-8 -*-
"""Rebuild META-INF/BenchmarkList correctly."""
import zipfile, shutil

jar_path = "../druid-ak-1.2.27-jar-with-dependencies.jar"

# Read ALL stub classes from the JAR to build entries
stubs = {}
with zipfile.ZipFile(jar_path) as z:
    for name in z.namelist():
        if name.startswith("com/ankki") and name.endswith("_jmhTest.class"):
            # Extract: benchmark class, method, stub class
            path_parts = name.replace(".class", "").split("/")
            stub_pkg = ".".join(path_parts[:-1])
            stub_class = ".".join(path_parts)
            # Determine benchmark class from stub naming pattern
            # Pattern: ..._jmhTest class → based on benchmark class + method
            pass  # Complex parsing, let me use a different approach

# Instead: generate entries for the AllSqlBenchmark only, append to original
with zipfile.ZipFile(jar_path) as z:
    bl_orig = z.read("META-INF/BenchmarkList").decode("ascii")
    # Find our stub classes
    our_stubs = [n for n in z.namelist() if "AllSqlBenchmark" in n and "_jmhTest" in n]
    for s in our_stubs:
        print(f"  Stub: {s}")

# Build entries for AllSqlBenchmark
base_class = "com.ankki.druid.parser.utils.LightweightCachedAkOutputVisitorUtilsAllSqlBenchmark"
short_name = base_class.split(".")[-1]

# 5 benchmark methods, each with 2 modes (Thrpt, Avgt)
methods_modes = [
    ("mixedScenario", "Throughput"),
    ("mixedScenario", "AverageTime"),
    ("insertOnly", "Throughput"),
    ("insertOnly", "AverageTime"),
    ("updateOnly", "Throughput"),
    ("updateOnly", "AverageTime"),
    ("selectOnly", "Throughput"),
    ("selectOnly", "AverageTime"),
    ("beginOnly", "Throughput"),
    ("beginOnly", "AverageTime"),
]

new_entries = []
for method, mode in methods_modes:
    mode_len = len(mode)
    time_unit = "MICROSECONDS"
    tu_len = len(time_unit)
    stub_class_name = f"com.ankki.druid.parser.utils.generated.{short_name}_{method}_jmhTest"
    stub_len = len(stub_class_name)
    # Format: JMH S <len> <class> S <len> <stub> S <len> <method> S <len> <mode> E A 1 1 1 E I 1 3 T 3 2 s E I 1 5 T 4 5 s E I 1 1 E E E E E E U <tu_len> <time_unit> E E
    entry = f"JMH S {len(base_class)} {base_class} S {stub_len} {stub_class_name} S {len(method)} {method} S {mode_len} {mode} E A 1 1 1 E I 1 3 T 3 2 s E I 1 5 T 4 5 s E I 1 1 E E E E E E U {tu_len} {time_unit} E E \r\n"
    new_entries.append(entry)

# Append to original BenchmarkList
new_bl = bl_orig.rstrip("\r\n ") + "\r\n" + "\r\n".join(new_entries)

# Write JAR with updated BenchmarkList
tmp_jar = jar_path + ".tmp"
with zipfile.ZipFile(jar_path) as zin:
    with zipfile.ZipFile(tmp_jar, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename == "META-INF/BenchmarkList":
                zout.writestr(item, new_bl)
            else:
                zout.writestr(item, zin.read(item.filename))

# Also verify stubs exist
with zipfile.ZipFile(tmp_jar) as z:
    for n in z.namelist():
        if "AllSqlBenchmark" in n and "_jmhTest" in n:
            print(f"  VERIFIED: {n}")

shutil.move(tmp_jar, jar_path)
print(f"Updated BenchmarkList: {len(bl_orig.splitlines())} orig + {len(new_entries)} new entries")
