# -*- coding: utf-8 -*-
import zipfile
with zipfile.ZipFile("../druid-ak-1.2.27-jar-with-dependencies.jar") as z:
    bl = z.read("META-INF/BenchmarkList")
    print(f"Total size: {len(bl)}")
    crlf_count = bl.count(b"\r\n")
    lf_count = bl.count(b"\n")
    print(f"CRLF count: {crlf_count}")
    print(f"LF count: {lf_count}")
    print(f"Ends with: {repr(bl[-30:])}")
    # Show last 2 entries
    entries = [b"JMH" + e for e in bl.split(b"JMH")[1:]]
    print(f"Number of entries: {len(entries)}")
    for i, e in enumerate(entries[-2:], len(entries)-1):
        print(f"Entry {i}: {e[:100]}...")
