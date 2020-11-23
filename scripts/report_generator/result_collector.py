import csv
from typing import Any, Dict, List, Optional

from result import Result
from utils import JsonSerializer


class ResultCollector(JsonSerializer):
    def __init__(self, data: Optional[Dict[str, Any]] = {}):
        super().__init__()
        self.results: List[Result] = []
        self.additional_data = data

    def read_perf(self, perf_log_path: str):
        with open(perf_log_path, newline="") as summary_file:
            header = summary_file.readline().lower().strip().split(";")
            summary_lower = (line.lower() for line in summary_file)
            reader = csv.DictReader(summary_lower, fieldnames=header, delimiter=";")
            for row in reader:
                new_config = True
                result, mode, precision, value, url = self.parse_perf_result(row)
                # Check if config already exists
                search_result = self.get_result_by_hash(result.config_hash)
                if search_result:
                    new_config = False
                    result = search_result

                if mode in ["latency", "throughput"]:
                    result.update_perf_data(mode, precision, value, url)
                elif mode == "accuracy":
                    result.update_accuracy_data(precision, value, url)
                else:
                    raise Exception(f"Mode '{mode}' not recognized.")
                if new_config:
                    self.results.append(result)

    def read_tuning(self, tuning_file_path: str):
        """Read tuning data and store result in list."""
        with open(tuning_file_path, newline="") as summary_file:
            next(summary_file)
            for line in summary_file:
                new_config = True
                result, strategy, time, trials, model_size_ratio, url = self.parse_tuning_result(line)
                # Check if config already exists
                search_result = self.get_result_by_hash(result.config_hash)
                if search_result:
                    new_config = False
                    result = search_result

                result.update_tuning_data(strategy, time, trials, model_size_ratio, url)

                if new_config:
                    self.results.append(result)

    def get_result_by_hash(self, config_hash: str) -> Optional[Result]:
        """Search for config in results table."""
        for result in self.results:
            if result.config_hash == config_hash:
                return result
        return None

    def parse_perf_result(self, raw_data):
        result = Result()
        result.framework = raw_data.get("framework")
        result.version = self.additional_data.get(f"{result.framework}_version", "")
        result.platform = raw_data.get("platform")
        result.model = raw_data.get("model")

        precision = raw_data.get("precision")
        mode = raw_data.get("type")
        value = raw_data.get("value")
        url = raw_data.get("url")

        return result, mode, precision, value, url

    def parse_tuning_result(self, line):
        """Parse tuning entry."""
        data = line.strip().lower().split(";")
        result = Result()
        result.framework = data[0]
        result.version = self.additional_data.get(f"{result.framework}_version", "")
        result.model = data[1]

        strategy = data[2]
        try:
            time = int(data[3])
        except:
            time = ""
        
        try:
            trials = int(data[4])
        except:
            trials = ""

        url = data[5]

        if data[6] != "" and data[7] != "":
            model_size_ratio = float(data[6]) / int(data[7])
        else:
            model_size_ratio = "NaN"
        return result, strategy, time, trials, model_size_ratio, url
