import zipfile
z = zipfile.ZipFile("/root/druid-ak-1.2.27-jar-with-dependencies.jar")
found = [n for n in z.namelist() if "LightweightCached" in n]
if found:
    for n in found:
        print(n)
else:
    print("NOT FOUND")
