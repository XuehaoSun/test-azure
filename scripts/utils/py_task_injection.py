import argparse
import re
from typing import Any

parser = argparse.ArgumentParser(allow_abbrev=False)
parser.add_argument("--file_name", type=str, required=True)
parser.add_argument("--task", type=str, required=True)
parser.add_argument("--params", type=str, nargs='+')
args = parser.parse_args()


class InsertCode():
    def __init__(self, file_name: str, code_content: str, pattern: str) -> None:
        self.file_path = self.get_inc_file_path(file_name)
        self.code_content = code_content
        self.pattern = pattern

    def insert(self) -> None:
        original_code = self.get_source_code(self.file_path)
        if self.exists(original_code):
            print("[VAL INFO] code exists, reinsert")
            original_code = original_code.split("# insert code start")[0] + original_code.split(
                "# insert code end")[-1]

        replacement = r'\1\n{}\n'.format(self.code_content)
        new_code = re.sub(self.pattern, replacement, original_code)
        with open(self.file_path, 'w') as f:
            f.write(new_code)
        print("[VAL INFO] insert succeed")

    def __call__(self, *args: Any, **kwds: Any) -> Any:
        self.insert()

    def replace_params(self, params: str, params_flag: str) -> None:
        self.code_content = self.code_content.replace(params_flag, params)

    @staticmethod
    def get_source_code(file_path: str) -> str:
        with open(file_path, 'r') as f:
            original_code = f.read()
        return original_code

    @staticmethod
    def exists(target: str) -> bool:
        return "# insert code" in target

    @staticmethod
    def get_inc_file_path(file_name: str) -> str:
        import os
        import neural_compressor
        env_path = os.path.dirname(neural_compressor.__file__)
        file = os.path.join(env_path, file_name)
        return file


config = {
    "get_cpu_memory_info": {
        "code_content": '''
            # insert code start
            import psutil
            import time

            repo_dir = subprocess.Popen(["git", "rev-parse", "--show-toplevel"], stdout=subprocess.PIPE).communicate()[0].rstrip().decode("utf-8")
            output_file = os.path.join(repo_dir, "..", f"<framework>_<performance_precision>_cpu_memory_usage.log")
            core_list = []
            total_cpu_percent_dict = {"sum": 0, "count": 0}
            total_mem_percent_dict = {"sum": 0, "count": 0}

            with open(output_file, "w") as f:
                f.write(f"{'Time':<35}{'CPU (%)':^12}{'Memory (%)':^12}{os.linesep}")

            for i in range(0, num_of_instance):
                if sys.platform in ['linux'] and get_architecture() == 'x86_64':
                    core_list_idx = np.arange(0, cores_per_instance) + i * cores_per_instance
                    core_list.extend(np.array(bounded_threads)[core_list_idx])
                else:
                    core_list.extend(np.arange(0, cores_per_instance) + i * cores_per_instance)

            while p.poll() is None:
                cpu_percent = psutil.cpu_percent(percpu=True)
                cpu_percent = [cpu_percent[index] for index in core_list]
                total_cpu_percent_dict["sum"] += (total_cpu_percent := sum(cpu_percent) / len(cpu_percent))
                total_mem_percent_dict["sum"] += (mem_percent := psutil.virtual_memory().percent)
                total_cpu_percent_dict["count"] += 1
                total_mem_percent_dict["count"] += 1
                current_time = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
                with open(output_file, "a") as f:
                    f.write(f"{current_time:<35}{total_cpu_percent:^12.2f}{mem_percent:^12.2f}{os.linesep}")
                time.sleep(1)

            if (total_cpu_percent_dict.get('count')==0 or total_mem_percent_dict.get('count')==0):
                avg_cpu_usage, avg_memory_usage = 0, 0
            else:
                avg = lambda x: x.get('sum')/x.get('count')
                avg_cpu_usage = avg(total_cpu_percent_dict)
                avg_memory_usage = avg(total_mem_percent_dict)

            print(f"[VAL INFO] {avg_cpu_usage=:.3f}, {avg_memory_usage=:.3f}")

            with open(output_file, "a") as f:
                f.write(f"avg cpu usage: {avg_cpu_usage:.2f} %")
                f.write(os.linesep)
                f.write(f"avg memory usage: {avg_memory_usage:.2f} %")
            # insert code end
        ''',
        "pattern":
        r'(if sys\.platform in \[\'linux\'\]:\n\s+p = subprocess\.Popen\(multi_instance_cmd, preexec_fn=os\.setsid, shell=True\) # nosec)',
        "params": args.params,
        "params_flag": ["<framework>", "<performance_precision>"]
    },
}

if __name__ == "__main__":
    code_params = config.get(args.task, None)
    if code_params:
        code_inserter = InsertCode(args.file_name, code_params.get("code_content"), code_params.get("pattern"))
        for parameter, flag in zip(code_params.get("params"), code_params.get("params_flag")):
            code_inserter.replace_params(parameter, flag)
        code_inserter.insert()
    else:
        raise ValueError(f"invalid task: {args.task}")
