import argparse
import re
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--cov-xml", type=str, required=True, help="Path to coverage xml report.")
parser.add_argument("--summary-file", type=str, default="coverage_summary.log", help="Path to coverage summary file.")
args = parser.parse_args()

coverage_data = ET.parse(args.cov_xml).getroot().attrib

lines_covered = int(coverage_data.get("lines-covered", None))
lines_valid = int(coverage_data.get("lines-valid", None))
lines_coverage = 100*lines_covered/lines_valid if lines_valid > 0 else 0

branches_covered = int(coverage_data.get("branches-covered", None))
branches_valid = int(coverage_data.get("branches-valid", None))
branches_coverage = 100*branches_covered/branches_valid if branches_valid > 0 else 0

print(f"Lines coverage: {lines_covered}/{lines_valid} ({lines_coverage:.2f}%)")
print(f"Branches coverage: {branches_covered}/{branches_valid} ({branches_coverage:.2f}%)")

with open(args.summary_file, "w") as summary:
    summary.write(f"lines_coverage,{lines_covered},{lines_valid},{lines_coverage:.2f}\n")
    summary.write(f"branches_coverage,{branches_covered},{branches_valid},{branches_coverage:.2f}\n")
