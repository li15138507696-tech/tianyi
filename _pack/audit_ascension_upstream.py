"""Reproduce the pinned upstream progression inventory and potential caller graph.

Default mode checks committed evidence; --write updates it after a reviewed scan.
The tool never modifies the upstream JAR or launches Minecraft.
"""
import argparse
import collections
import csv
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
REFERENCES = ROOT / "_pack" / "refs" / "ascension"
PINNED_SHA256 = "b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc"
RANK_FIELDS = {"zhuanshu", "jieduan"}
JDK21 = Path(r"C:\Users\ADMIN\.jdks\temurin-21\bin")
OUTPUT_NAMES = ("upstream-field-uses.txt", "upstream-rank-callers.tsv", "upstream-rank-summary.json")


def run(command):
    result = subprocess.run([str(part) for part in command], cwd=ROOT, capture_output=True,
                            text=True, encoding="utf-8", errors="replace")
    for output in (result.stdout, result.stderr):
        if output:
            print(output, end="" if output.endswith("\n") else "\n")
    return result.returncode


def summary(uses_path, callers_path, asm_name):
    uses = [line.split("\t") for line in uses_path.read_text(encoding="utf-8").splitlines()]
    with callers_path.open(encoding="utf-8", newline="") as stream:
        callers = list(csv.DictReader(stream, delimiter="\t"))
    writers = collections.defaultdict(set)
    for owner, method, operation, field, descriptor in uses:
        if operation == "PUTFIELD" and field in RANK_FIELDS:
            writers[(owner, method)].add(field)
    references = collections.defaultdict(list)
    for row in callers:
        references[(row["targetClass"], row["targetMethod"])].append(row)
    stats = {}
    for field in sorted({row[3] for row in uses}):
        stats[field] = {}
        for operation in ("GETFIELD", "PUTFIELD"):
            rows = [row for row in uses if row[2:4] == [operation, field]]
            stats[field][operation] = {"classes": len({row[0] for row in rows}), "tuples": len(rows)}
    return {
        "schema": 1,
        "upstreamSha256": PINNED_SHA256,
        "asmLibrary": asm_name,
        "fieldTuples": len(uses),
        "fields": stats,
        "rankStageWriterClasses": len({owner for owner, method in writers}),
        "rankStageWriterMethods": len(writers),
        "potentialCallerReferences": len(callers),
        "referenceKinds": dict(sorted(collections.Counter(row["referenceKind"] for row in callers).items())),
        "writerMethods": [
            {"class": owner, "method": method, "fields": sorted(fields),
             "inJarReferences": len(references[(owner, method)])}
            for (owner, method), fields in sorted(writers.items())
        ],
        "limitations": [
            "Only direct field instructions for the exact PlayerVariables owner are inventoried.",
            "References are potential routes, not proof of execution or an entity argument's runtime type.",
            "Method owners and descriptors match exactly; inherited/interface dispatch is not resolved.",
            "Reflection, external JAR callers and registration by strings are not resolved.",
            "Repeated identical class/method/op/field tuples and reference edges are deduplicated.",
            "No rank-six, essence, UI or inventory compatibility capability is enabled by this report.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream", type=Path, default=ROOT / "_pack/libs/guzhenren-0.1-neoforge-1.21.1.jar")
    minecraft = Path(os.environ.get("TIANYI_MINECRAFT_DIR", "D:/game/mcmod/1/.minecraft"))
    parser.add_argument("--profile", type=Path, default=minecraft / "versions/1.21.1-NeoForge_21.1.263/1.21.1-NeoForge_21.1.263.json")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--write", action="store_true", help="update evidence after a successful pinned scan")
    mode.add_argument("--check", action="store_true", help="check evidence (the default)")
    args = parser.parse_args()
    if not args.upstream.is_file():
        parser.error(f"missing upstream JAR: {args.upstream}")
    with args.upstream.open("rb") as stream:
        actual_hash = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual_hash != PINNED_SHA256:
        print(f"FAIL upstream SHA-256 mismatch: expected {PINNED_SHA256}; got {actual_hash}")
        return 1
    if not args.profile.is_file():
        parser.error(f"missing fixed NeoForge 21.1.263 profile; supply --profile: {args.profile}")
    profile = json.loads(args.profile.read_text(encoding="utf-8"))
    asm_libraries = [library for library in profile.get("libraries", [])
                     if library.get("name", "").split(":")[:2] == ["org.ow2.asm", "asm"]]
    if len(asm_libraries) != 1:
        parser.error("the selected profile must declare exactly one org.ow2.asm:asm artifact")
    artifact = asm_libraries[0].get("downloads", {}).get("artifact", {}).get("path")
    if not artifact:
        parser.error("the selected ASM library has no artifact path")
    asm_jar = args.profile.parent.parent.parent / "libraries" / artifact
    if not asm_jar.is_file():
        parser.error(f"missing installed ASM artifact: {asm_jar}")
    javac = Path(os.environ.get("TIANYI_JAVAC", str(JDK21 / "javac.exe")))
    java = Path(os.environ.get("TIANYI_JAVA", str(JDK21 / "java.exe")))
    with tempfile.TemporaryDirectory(prefix="tianyi-upstream-audit-") as directory:
        temporary = Path(directory)
        compile_result = run([javac, "-encoding", "UTF-8", "--release", "21", "-proc:none",
                              "-cp", asm_jar, "-d", temporary,
                              REFERENCES / "PlayerVariableFieldScanner.java",
                              REFERENCES / "PlayerVariableFieldScannerTest.java"])
        if compile_result:
            return compile_result
        classpath = os.pathsep.join((str(temporary), str(asm_jar)))
        result = run([java, "-cp", classpath, "PlayerVariableFieldScannerTest"])
        if result:
            return result
        result = run([java, "-cp", classpath, "PlayerVariableFieldScanner", args.upstream,
                      temporary / OUTPUT_NAMES[0], temporary / OUTPUT_NAMES[1]])
        if result:
            return result
        report = summary(temporary / OUTPUT_NAMES[0], temporary / OUTPUT_NAMES[1], asm_libraries[0]["name"])
        (temporary / OUTPUT_NAMES[2]).write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        stale = []
        for name in OUTPUT_NAMES:
            generated = (temporary / name).read_text(encoding="utf-8")
            target = REFERENCES / name
            if target.is_file() and target.read_text(encoding="utf-8") == generated:
                continue
            if args.write:
                target.write_text(generated, encoding="utf-8", newline="\n")
                print(f"UPDATED {target.relative_to(ROOT)}")
            else:
                stale.append(name)
        if stale:
            print("FAIL stale or absent evidence: " + ", ".join(stale))
            return 1
        print(f"PASS pinned upstream audit: {report['rankStageWriterMethods']} rank/stage writer methods; "
              f"{report['potentialCallerReferences']} potential caller references")
    return 0


if __name__ == "__main__":
    sys.exit(main())
