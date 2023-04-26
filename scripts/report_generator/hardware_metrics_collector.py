class HardwareMetricsResultCollector():
    def __init__(self) -> None:
        pass

    def read(self, log_path: str) -> dict:
        with open(log_path) as f:
            res = [self.parse(line) for line in f]
        return self.result(res)

    def result(self, information: list) -> dict:
        results = {}
        for info in information:
            key = f"{info['os']}_{info['framework']}_{info['model']}"
            results.setdefault(
                key, {
                    "int8_avg_cpu_usage": "N/A",
                    "fp32_avg_cpu_usage": "N/A",
                    "int8_avg_memory_usage": "N/A",
                    "fp32_avg_memory_usage": "N/A",
                })
            results[key].update({
                f"{info['precision']}_avg_cpu_usage": self.convert_string(info["avg_cpu_usage"]),
                f"{info['precision']}_avg_memory_usage": self.convert_string(info["avg_memory_usage"]),
            })
        return results

    def parse(self, line: str) -> dict:
        info = line.split(";")
        keys = ["os", "framework", "precision", "model", "avg_cpu_usage", "avg_memory_usage"]
        return dict(zip(keys, info))
    
    @staticmethod
    def convert_string(usage):
        try:
            return float(usage)/100
        except:
            return "N/A"
