# -*- coding: utf-8 -*-
import zipfile
with zipfile.ZipFile("../druid-ak-1.2.27-jar-with-dependencies.jar") as z:
    bl = z.read("META-INF/BenchmarkList").decode("ascii", errors="replace")
    # Find all '=' characters
    eq_positions = [i for i, c in enumerate(bl) if c == "="]
    print(f"Found {len(eq_positions)} '=' characters in BenchmarkList")
    for pos in eq_positions:
        context = bl[max(0,pos-30):pos+30]
        print(f"  pos {pos}: ...{repr(context)}...")
