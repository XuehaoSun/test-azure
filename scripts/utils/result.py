import json
import os
from typing import Any, Dict, List, Optional

from report_generator.utils import JsonSerializer


class TuningData(JsonSerializer):
    """Interface for tuning information."""

    def __init__(self) -> None:
        """Initialize TuningData class."""
        super().__init__()
        self.strategy = None
        self.time = None
        self.trials = 0
        self.baseline_acc = None
        self.tuned_acc = None
        self.fp32_model_size = None
        self.int8_model_size = None
        self.total_mem_size = None
        self.max_mem_size = None
        self.log = None

    @property
    def model_size_ratio(self):
        """Get model size ratio."""
        if self.fp32_model_size and self.int8_model_size:
            return round(self.fp32_model_size / self.int8_model_size, 2)
    @property
    def mem_percentage(self):
        """Get memory usage in percents."""
        if self.total_mem_size and self.max_mem_size:
            return f"{round(self.max_mem_size / self.total_mem_size * 100, 4)}%"

class Measurement(JsonSerializer):
    """Interface for measurement."""

    def __init__(self, data = {}) -> None:
        super().__init__()
        self.batch_size = data.get("batch_size")
        self.instances = data.get("instances")
        self.mode = data.get("mode")
        self.precision = data.get("precision")
        self.value = data.get("value")
        self.log = data.get("log")

class Result(JsonSerializer):
    def __init__(self) -> None:
        super().__init__()
        self.platform = None
        self.os = None
        self.python = None
        self.framework = None
        self.version = None
        self.model = None
        self.tuning = TuningData()
        self.benchmarks: List[Measurement] = []


    def to_old_format(self, mode) -> str:
        if mode == "tuning":
            return ";".join([xstr(item) for item in [
                self.os,
                self.platform,
                self.framework,
                self.model,
                self.tuning.strategy,
                self.tuning.time,
                self.tuning.trials,
                self.tuning.log,
                self.tuning.fp32_model_size,
                self.tuning.int8_model_size,
                self.tuning.mem_percentage
            ]])
        if mode == "performance":
            data = []
            for benchmark in self.benchmarks:
                line = ";".join([xstr(item) for item in [
                    self.os,
                    self.platform,
                    self.framework,
                    benchmark.precision.upper(),
                    self.model,
                    "Inference",
                    benchmark.mode.capitalize(),
                    benchmark.batch_size,
                    benchmark.value,
                    benchmark.log
                ]])
                data.append(line)
            return data

    def save_summary(self, output_dir):
        os.makedirs(output_dir, exist_ok=True)
        
        result_file = os.path.join(output_dir, f"{self.framework}-{self.model}-{self.os}-{self.platform}.json")
        with open(result_file, "w") as f:
            json.dump(self.serialize(), f, indent=4)

        summary_file = os.path.join(output_dir, "summary.log")
        summary_data = self.to_old_format("performance")
        with open(summary_file, "w") as f:
            f.writelines([line + '\n' for line in summary_data])

        tuning_info_file = os.path.join(output_dir, "tuning_info.log")
        with open(tuning_info_file, "w") as f:
            f.write(self.to_old_format("tuning") + "\n")

def xstr(s):
    if s is None:
        return ""
    return str(s)
