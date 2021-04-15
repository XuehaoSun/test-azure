import argparse
from typing import Any, List
import glob
import os
import re

import xlsxwriter
from xlsxwriter.utility import xl_col_to_name

from result import Result
from result_collector import ResultCollector

parser = argparse.ArgumentParser()
parser.add_argument("--logs-dir", type=str, required=False, default=".")
parser.add_argument("--tuning-log-name", type=str, default="tuning_info.log")
parser.add_argument("--commit", type=str, default="")
parser.add_argument("--tensorflow-version", type=str, default="")
parser.add_argument("--mxnet-version", type=str, default="")
parser.add_argument("--pytorch-version", type=str, default="")
parser.add_argument("--onnxruntime-version", type=str, default="")
parser.add_argument("--WW", type=str, default="xx")
args = parser.parse_args()

strategies = [
    "basic",
    "mse",
    "bayesian",
    "exhaustive",
    "random",
    "tpe"
]

result_collector = ResultCollector({
    "tensorflow_version": args.tensorflow_version,
    "mxnet_version": args.mxnet_version,
    "pytorch_version": args.pytorch_version,
    "onnxrt_version": args.onnxruntime_version
})

for log in glob.glob(os.path.join(args.logs_dir, "**", args.tuning_log_name)):
    result_collector.read_tuning(log)
print(f"Found {len(result_collector.results)} results")

frameworks = {}
for result in result_collector.results:
    framework_versions = frameworks.get(result.framework, [])
    if result.version not in framework_versions:
        framework_versions.append(result.version)
    frameworks.update({ result.framework: framework_versions })

num_frameworks = 0
for framework, versions in frameworks.items():
    for version in versions:
        num_frameworks += 1

report_path = os.path.join(args.logs_dir, f"lpot_strategy_test_WW{args.WW}.xlsx")
workbook = xlsxwriter.Workbook(report_path)
header_format = workbook.add_format({
    "bold": 1,
    "border": 1,
    "align": "center",
    "valign": "vcenter",
    "text_wrap": True })

green_bg = workbook.add_format({"bg_color": "#c6e0b4", "align": "center", "border": 1})
red_bg = workbook.add_format({"bg_color": "#ff5050", "align": "center", "border": 1})

def main():
    for framework, versions in frameworks.items():
        for version in versions:
            worksheet = workbook.add_worksheet(f"{framework} {version}")
            write_header(worksheet)
            row = 1
            results = result_collector.get_results_by_version(framework, version)
            results.sort(key=lambda x: x.model)
            models = sorted(list(set([result.model for result in results])))
            for result in results:
                write_row(worksheet, row, result)
                row += 1
            
            for idx, model in enumerate(models):
                worksheet.merge_range(1 + idx*len(strategies), 0, (idx+1) * len(strategies), 0, model, cell_format=header_format)
    
    write_summary(workbook)
    workbook.close()


def write_header(worksheet):
    header = [
        "Model",
        "Strategy",
        "Tuning time with log link",
        "Tuning trials",
        "Commit",
        "Status",
        "Failure type",
        "Comment"
    ]

    worksheet.write_row(0, 0, header, header_format)


def write_row(worksheet, row, result):
    status = "SUCCESS" if result.tuning.time and result.tuning.trials > 0 else "FAILURE"
    failure_type = "-" if status == "SUCCESS" else ""
    cell_format = green_bg
    tuning_time = result.tuning.time

    if status != "SUCCESS": 
         cell_format = red_bg
         tuning_time = "failure"


    data = {
        "model": result.model,
        "strategy": result.tuning.strategy,
        "tuning_time": tuning_time,
        "tuning_trials": result.tuning.trials,
        "commit": args.commit,
        "status": status,
        "failure_type": failure_type
    }

    col = 0
    for key, value in data.items():
        if key == "tuning_time":
            worksheet.write_url(
                row,
                col,
                result.tuning.url,
                string=f"{value}",
                cell_format=cell_format)
        else:
            worksheet.write(row, col, value, cell_format)
        col += 1

def write_summary(workbook):
    """Add summary tab to workbook."""
    worksheet = workbook.add_worksheet("SUMMARY")

    default_format = workbook.add_format({"border": 1})
    percentage_format = workbook.add_format({"num_format": "0.00%", "border": 1})

    header = ["Framework", "Version"]
    strategy_columns = [
        {
            "name": "SUCCESS",
            "formula": "=COUNTIFS('{worksheet_name}'!B2:B{last_row}, C{strategy_row},'{worksheet_name}'!F2:F{last_row}, \"SUCCESS\")",
            "format": default_format,
        },
        {
            "name": "ACC THRESHOLD FAILURES",
            "formula": "=COUNTIFS('{worksheet_name}'!B2:B{last_row}, C{strategy_row},'{worksheet_name}'!F2:F{last_row},\"FAILURE\",'{worksheet_name}'!G2:G{last_row},\"ACCURACY THRESHOLD\")",
            "format": default_format
        },
        {
            "name": "CODE ERRORS\nOR\nINVALID ACC",
            "formula": "=COUNTIFS('{worksheet_name}'!B2:B{last_row}, C{strategy_row},'{worksheet_name}'!F2:F{last_row},\"FAILURE\",'{worksheet_name}'!G2:G{last_row},\"CODE\")+COUNTIFS('{worksheet_name}'!B2:B{last_row},C{strategy_row},'{worksheet_name}'!F2:F{last_row},\"FAILURE\",'{worksheet_name}'!G2:G{last_row},\"ACCURACY INVALID\")",
            "format": default_format
        },
        {
            "name": "TIMEOUT\n(WIP)",
            "formula": "=COUNTIFS('{worksheet_name}'!B2:B{last_row}, C{strategy_row},'{worksheet_name}'!F2:F{last_row},\"FAILURE\",'{worksheet_name}'!G2:G{last_row},\"*TIMEOUT*\")",
            "format": default_format
        },
        {
            "name": "PROGRESS",
            "formula": "=SUM(C{row}:{code_err_column}{row})/{num_models}",
            "format": percentage_format
        },
        {
            "name": "PASS\n/\nPROGRESS",
            "formula": "=IF(SUM(C{row}:{code_err_column}{row})<>0,SUM(C{row}:{acc_threshold_column}{row})/SUM(C{row}:{code_err_column}{row}), \"N/A\")",
            "format": percentage_format
        },
        {
            "name": "SUCCESS/TOTAL",
            "formula": "=SUM(C{row},{code_err_column}{row})/COUNTIF('{worksheet_name}'!B2:B{last_row}, C{strategy_row})",
            "format": percentage_format
        }
    ] 
    header.extend([column.get("name") for column in strategy_columns])

    for idx, strategy in enumerate(strategies):
        row = idx * (num_frameworks+4)

        strategy_row = row + 1 
        worksheet.merge_range(row, 2, row, len(strategy_columns)+1, strategy, header_format)
        row += 1
        worksheet.write_row(row, 0, header, header_format)
        for framework, versions in frameworks.items():
            for version in versions:
                row += 1
                models = list(set([result.model for result in result_collector.get_results_by_version(framework, version)]))

                # Replace strings to variables
                worksheet.write_row(row, 0, [framework, version], header_format)
                col = 2
                replacements = {
                    "{worksheet_name}": f"{framework} {version}",
                    "{last_row}": f"{len(strategies)*len(models) + 1}",
                    "{strategy_row}": f"{strategy_row}",
                    "{acc_threshold_column}": xl_col_to_name(col + 1),
                    "{code_err_column}": xl_col_to_name(col + 2),
                    "{num_models}": f"{len(models)}",
                    "{row}": f"{row + 1}"
                }
                replacements = dict((re.escape(k), v) for k, v in replacements.items()) 
                pattern = re.compile("|".join(replacements.keys()))
                for column in strategy_columns:
                    formula = column.get("formula")
                    cell_formula = pattern.sub(lambda m: replacements[re.escape(m.group(0))], formula)
                    worksheet.write_formula(row, col, cell_formula, cell_format=column.get("format"))
                    col += 1
                for i in range(1,4):
                    worksheet.conditional_format(row, col-i, row, col-i, {
                        "type": "3_color_scale",
                        "min_color": "#f86969",
                        "min_type": "num",
                        "min_value": 0,                        
                        "mid_color": "#ffeb84",
                        "mid_type": "num",
                        "mid_value": 0.5,
                        "max_color": "#63be7b",
                        "max_type": "num",
                        "max_value": 1,})

if __name__ == "__main__":
    main()
