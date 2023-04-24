import argparse
import xlsxwriter
from result_collector import ResultCollector
from hardware_metrics_collector import HardwareMetricsResultCollector

parser = argparse.ArgumentParser()
parser.add_argument("--tuning-log", type=str, required=True)
parser.add_argument("--summary-log", type=str, required=True)
parser.add_argument("--hardware_metrics", type=str, default=False, required=False)
args = parser.parse_args()

result_collector = ResultCollector()
result_collector.read_tuning(args.tuning_log)
result_collector.read_perf(args.summary_log)

workbook = xlsxwriter.Workbook('lpot_report.xlsx', {'strings_to_numbers': True})
worksheet = workbook.add_worksheet()

args.hardware_metrics = args.hardware_metrics if (args.hardware_metrics != "None") else False
if args.hardware_metrics:
    hardware_metrics_results = HardwareMetricsResultCollector()
    hardware_metrics_results = hardware_metrics_results.read(args.hardware_metrics)


def main():
    write_header()

    row = 2
    for result in result_collector.results:
        write_row(row, result)
        row += 1

    workbook.close()


def write_header():
    header_format = workbook.add_format({'bold': 1, 'align': 'center'})
    worksheet.merge_range("F1:H1", "Tuning", header_format)
    worksheet.merge_range("I1:K1", "Accuracy", header_format)
    worksheet.merge_range("L1:N1", "Performance", header_format)

    header = [
        {"header": "Platform"},
        {"header": "System"},
        {"header": "Framework"},
        {"header": "version"},
        {"header": "model"},
        {"header": "Tuning Strategy"},
        {"header": "Tuning Time(s)"},
        {"header": "Tuning count"},
        {"header": "INT8 Tuning Accuracy"},
        {"header": "FP32 Accuracy Baseline"},
        {"header": "Acc Ratio[(INT8-FP32)/FP32]"},
        {"header": "INT8 throughput(fps)"},
        {"header": "FP32 throughput(fps))"},
        {"header": "Throughput Ratio[INT8/FP32]"},
    ]

    if args.hardware_metrics:
        worksheet.merge_range("O1:P1", "Average CPU usage", header_format)
        worksheet.merge_range("Q1:R1", "Average memory usage", header_format)
        header.extend([
            {"header": "INT8 CPU usage(%)"},
            {"header": "FP32 CPU usage(%)"},
            {"header": "INT8 memory usage(%)"},
            {"header": "FP32 memory usage(%)"},
        ])

    worksheet.add_table(1, 0, len(result_collector.results) + 1, len(header)-1, {"header_row": True, "columns": header})


def write_row(row, result):

    int8_acc = result.accuracy.int8.get("value", None)
    fp32_acc = result.accuracy.fp32.get("value", None)
    try:
        acc_ratio = (float(int8_acc) - float(fp32_acc)) / float(fp32_acc)
    except:
        acc_ratio = "N/A"

    int8_throughput = result.performance.performance.get("int8", {}).get("value", "")
    fp32_throughput = result.performance.performance.get("fp32", {}).get("value", "")

    try:
        throughput_ratio = float(int8_throughput) / float(fp32_throughput)
    except:
        throughput_ratio = "N/A"

    default_format = workbook.add_format()
    default_format.set_text_wrap()

    accuracy_format = workbook.add_format({'num_format': '0.00%'})
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
        "int8_acc": int8_acc,
        "fp32_acc": fp32_acc,
        "acc_ratio": acc_ratio,
        "int8_throughput": int8_throughput,
        "fp32_throughput": fp32_throughput,
        "throughput_ratio": throughput_ratio
    }

    if args.hardware_metrics:
        hardware_metrics_result: dict = hardware_metrics_results.get(
            f"{data['os']}_{data['framework']}_{data['model']}")
        data.update(hardware_metrics_result)

    col = 0
    format_map = {
        "acc_ratio": accuracy_format,
        "throughput_ratio": throughput_format,
        "int8_avg_cpu_usage": accuracy_format,
        "fp32_avg_cpu_usage": accuracy_format,
        "int8_avg_memory_usage": accuracy_format,
        "fp32_avg_memory_usage": accuracy_format,
    }

    for key, value in data.items():
        cell_format = format_map.get(key, default_format)
        worksheet.write(row, col, value, cell_format)
        col += 1


if __name__ == "__main__":
    main()
