# -*- coding: utf-8 -*-
import zipfile, shutil, os

jar_path = "../druid-ak-1.2.27-jar-with-dependencies.jar"

# Read existing BenchmarkList
with zipfile.ZipFile(jar_path, "r") as z:
    bl = z.read("META-INF/BenchmarkList").decode("utf-8")

# Add entries for AllSqlBenchmark (5 methods x 2 modes = 10 entries)
new_entries = ""
base_class = "com.ankki.druid.parser.utils.LightweightCachedAkOutputVisitorUtilsAllSqlBenchmark"

for method in ["mixedScenario", "insertOnly", "updateOnly", "selectOnly", "beginOnly"]:
    short_name = base_class.split(".")[-1]
    stub_class = f"com.ankki.druid.parser.utils.generated.{short_name}_{method}_jmhTest"
    
    for mode, mode_name, time_unit in [("thrpt", "Throughput", "MICROSECONDS"), ("avgt", "AverageTime", "MICROSECONDS")]:
        entry = f"JMH S {len(base_class)} {base_class} S {len(stub_class)} {stub_class} S {len(method)} {method} S {len(mode_name)} {mode_name} E A 1 1 1 E I 1 3 T 3 2 s E I 1 5 T 4 5 s E I 1 1 E E E E E E U {len(time_unit)} {time_unit} E E \r\n"
        new_entries += entry

bl += new_entries

# Rebuild JAR with updated BenchmarkList
tmp_jar = jar_path + ".tmp"
with zipfile.ZipFile(jar_path, "r") as zin:
    with zipfile.ZipFile(tmp_jar, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == "META-INF/BenchmarkList":
                zout.writestr(item, bl)
            else:
                zout.writestr(item, data)

shutil.move(tmp_jar, jar_path)
print(f"Updated BenchmarkList with {len(new_entries.splitlines())} new entries")
print(f"Total BenchmarkList size: {len(bl)} chars")
