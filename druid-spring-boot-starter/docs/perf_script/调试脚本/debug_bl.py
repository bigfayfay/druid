# -*- coding: utf-8 -*-
import zipfile
with zipfile.ZipFile("../druid-ak-1.2.27-jar-with-dependencies.jar") as z:
    bl = z.read("META-INF/BenchmarkList").decode("ascii", errors="replace")
    # Find the position of "AllSql" entries
    idx = bl.find("AllSql")
    if idx >= 0:
        print(f"AllSqlBenchmark entries start at position {idx}")
        print(f"Context before:\n{bl[max(0,idx-50):idx+50]}")
        print(f"\n---\nFull BenchmarkList around AllSql:\n{bl[idx-5:]}")
    else:
        print("AllSqlBenchmark NOT FOUND in BenchmarkList!")
        # Check last 500 chars
        print(f"Last 500 chars:\n{bl[-500:]}")
