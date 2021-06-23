import argparse
from typing import Any, List

import xlsxwriter

from result import Result
from result_collector import ResultCollector

parser = argparse.ArgumentParser()
parser.add_argument("--tuning-log", type=str, required=True)
parser.add_argument("--summary-log", type=str, required=True)
args = parser.parse_args()

result_collector = ResultCollector()
result_collector.read_tuning(args.tuning_log)
result_collector.read_perf(args.summary_log)

workbook = xlsxwriter.Workbook('lpot_report.xlsx')
worksheet = workbook.add_worksheet()


def main():
    write_header()

    row = 2
    for result in result_collector.results:
        write_row(row, result)
        row += 1

    workbook.close()


def write_header():
    header_format = workbook.add_format({'bold': 1, 'align': 'center'})
    worksheet.merge_range("D1:G1", "Tuning", header_format)
    worksheet.merge_range("H1:J1", "Accuracy", header_format)
    worksheet.merge_range("K1:R1", "Performance", header_format)

    header = [
        {"header": "Platform"},
        {"header": "System"},
        {"header": "Framework"},
        {"header": "version"},
        {"header": "model"},
        {"header": "Tuning Strategy"},
        {"header": "Tuning Time(s)"},
        {"header": "Tuning count"},
        {"header": "Model size ratio[FP32/INT8]"},
        {"header": "INT8 Tuning Accuracy"},
        {"header": "FP32 Accuracy Baseline"},
        {"header": "Acc Ratio[(INT8-FP32)/FP32]"},
        {"header": "INT8 realtime(ms)"},
        {"header": "FP32 realtime(ms)"},
        {"header": "Realtime Latency Ratio[FP32/INT8]"},
        {"header": "INT8 throughput(fps)"},
        {"header": "FP32 throughput(fps))"},
        {"header": "Throughput Ratio[INT8/FP32]"},
    ]

    worksheet.add_table(1, 0, len(result_collector.results) + 1, len(header)-1, {"header_row": True, "columns": header})


def write_row(row, result):
    model_size_ratio = "NaN"
    if isinstance(result.tuning.model_size_ratio, float):
        model_size_ratio = result.tuning.model_size_ratio

    int8_acc = result.accuracy.int8.get("value", None)
    fp32_acc = result.accuracy.fp32.get("value", None)
    try:
        acc_ratio = (float(int8_acc) - float(fp32_acc)) / float(fp32_acc)
    except:
        acc_ratio = "N/A"

    int8_latency = result.performance.latency.get("int8", {}).get("value", "")
    fp32_latency = result.performance.latency.get("fp32", {}).get("value", "")

    try:
        latency_ratio = float(fp32_latency) / float(int8_latency)
    except:
        latency_ratio = "N/A"

    int8_throughput = result.performance.throughput.get("int8", {}).get("value", "")
    fp32_throughput = result.performance.throughput.get("fp32", {}).get("value", "")

    try:
        throughput_ratio = float(int8_throughput) / float(fp32_throughput)
    except:
        throughput_ratio = "N/A"

    default_format = workbook.add_format()
    default_format.set_text_wrap()

    model_size_format = workbook.add_format({'num_format': '0.00x'})
    accuracy_format = workbook.add_format({'num_format': '0.00%'})
    latency_format = workbook.add_format({'num_format': '0.00x'})
    throughput_format = workbook.add_format({'num_format': '0.00x'})
    data = {
        "platform": result.platform,
        "os": result.os,
        "framework": result.framework,
        "version": result.version,
        "model": result.model,
        "strategy": result.tuning.strategy,
        "tuning_time": result.tuning.time,
        "tuning_trials": result.tuning.trials,
        "model_size_ratio": model_size_ratio,
        "int8_acc": int8_acc,
        "fp32_acc": fp32_acc,
        "acc_ratio": acc_ratio,
        "int8_latency": int8_latency,
        "fp32_latency": fp32_latency,
        "latency_ratio": latency_ratio,
        "int8_throughput": int8_throughput,
        "fp32_throughput": fp32_throughput,
        "throughput_ratio": throughput_ratio
    }

    col = 0
    format_map = {
        "model_size_ratio": model_size_format,
        "acc_ratio": accuracy_format,
        "latency_ratio": latency_format,
        "throughput_ratio": throughput_format
    }
    for key, value in data.items():
        cell_format = format_map.get(key, default_format)
        worksheet.write(row, col, value, cell_format)
        col += 1


if __name__ == "__main__":
    main()
