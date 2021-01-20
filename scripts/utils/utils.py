import glob
import os
import platform
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import List, Optional
try:
    import ruamel.yaml as yaml
except:
    import ruamel_yaml as yaml

def get_size(path: str, unit: str = "MB", add_unit: bool = False):
    supported_units = {
        "B": 1,
        "KB": 1024,
        "MB": 1024**2,
        "GB": 1024**3,
    }

    unit_modifier = supported_units.get(unit, None)
    if unit_modifier is None:
        raise Exception("Unit not supported. Select one of following: " + supported_units.keys())

    root_dir = Path(path)
    if root_dir.is_file():
        size = root_dir.stat().st_size
    else:
        size = sum(f.stat().st_size for f in root_dir.glob('**/*') if f.is_file())

    size = int(round(size / unit_modifier))
    if add_unit:
        size = f"{size}{unit[0]}"

    return size

def replace_line(file_path, regex, string):
    with open(file_path, "r") as file:
            lines = file.readlines()

    with open(file_path, "w") as file:
        for line in lines:
            file.write(re.sub(regex, string, line))

def insert_line(file_path, line_pattern, string):
    with open(file_path, "r") as file:
        lines = file.readlines()

    with open(file_path, "w") as file:
        for line in lines:
            if line_pattern in line:
                line = line + "\n" + string + "\n"
            file.write(line)

def copy_files(files_pattern, dest):
    for file in glob.glob(files_pattern):
        print(f"Copying '{file}' to '{dest}'...")
        shutil.copy(file, dest)

def install_requirements(requirements_file: str, exclude: List[str]) -> None:
    print("\nInstalling model requirements...")
    
    if os.path.isfile(requirements_file):
        options = []
        requirements = []
        with open(requirements_file, "r") as req_file:
            for line in req_file:
                if line.startswith("--"):
                    options.append(line.strip())
                if not any(excuded in line for excuded in exclude):
                    requirements.append(line.strip())
                else:
                    print(f"Skipping requirement: {line}")

        # Write requirements back to file
        if requirements:
            requirements.extend(options)
            with open(requirements_file, "w") as req_file:
                for line in requirements:
                    req_file.write(line + os.linesep)

            cmd = [
                sys.executable,
                "-m",
                "pip",
                "install",
                "-r",
                requirements_file]
                
            print(f"[ EXEC ] {cmd}")
            subprocess.run(cmd, check=True)

            cmd = [
                sys.executable,
                "-m",
                "pip",
                "list"]

            print(f"[ EXEC ] {cmd}")
            subprocess.run(cmd, check=True)
        else:
            print("No requirements to install.")
    else:
        print("Not found requirements.txt file.")


def execute_command(args: List[str], cwd = None, file = None, shell = False, universal_newlines = False):
    cmd = " ".join(map(str, args))
    proc = subprocess.Popen(cmd,
                            cwd=cwd,
                            stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT,
                            shell=shell,
                            universal_newlines=universal_newlines)

    if not file:
        print(f"[ COMMAND ] {cmd}")
        for line in proc.stdout:
                decoded_line = line.decode("utf-8", errors="ignore").strip()
                print(decoded_line)
    else:
        with open(file, "w", 1, encoding="utf-8") as log_file:
            log_file.write(f"[ COMMAND ] {cmd}")
            print(f"[ COMMAND ] {cmd}")
            for line in proc.stdout:
                decoded_line = line.decode("utf-8", errors="ignore").strip()
                log_file.write(decoded_line + "\n")
                print(decoded_line)

    proc.wait()

    return proc


def get_executable(mode: str):
    executable_map = {
        "Linux": {
            "tuning": ["run_tuning.sh"],
            "benchmark": ["run_benchmark.sh"],
        },
        "Windows": {
            "tuning": ["run_tuning.bat"],
            "benchmark": ["python", "main.py"],
        }
    }
    system = platform.system()
    executable = executable_map.get(system, {}).get(mode, None)
    if executable is None:
        raise Exception(f"Could not found {mode} executable for {system} OS.")
    return executable

def get_number_of_sockets():
    system = platform.system()
    if system == "Windows":
        cmd = "wmic cpu get DeviceID | find /c \"CPU\""
    if system == "Linux":
        cmd = "lscpu | grep 'Socket(s)' | cut -d ':' -f 2"

    proc = subprocess.Popen(args=cmd,
                            shell=True,
                            stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT,
                            universal_newlines=False)
    proc.wait()

    for line in proc.stdout:
        return int(line.decode("utf-8", errors="ignore").strip())
    
    return 0


def update_yaml(yaml, framework, dataset_location = None, strategy = None, max_trials = None):
    if not os.path.isfile(yaml):
        raise Exception(f"Not found yaml config at '{yaml}' location.")

    # Update dataset
    if framework != "pytorch":
        print("Reading config")
        with open(yaml, "r") as config:
            lines = config.readlines()

        print("Saving config")
        with open(yaml, "w") as config:
            for line in lines:
                # TMP change to run perf tests
                # Increase accuracy threshold
                if "relative: " in line:
                    config.write(re.sub(r'relative:\s+(\d.\d+)', "relative: 0.03", line))
                else:
                    config.write(re.sub(r'root:.*/path/to/(calibration|evaluation)/dataset/?', f"root: {dataset_location}", line))
                # ------------------------


    update_yaml_config(
        yaml_file=yaml,
        strategy=strategy,
        max_trials=max_trials
    )
    print(f"Tuning strategy: {strategy}")


def update_yaml_config(yaml_file: str, strategy: Optional[str] = None, mode: Optional[str] = None,
    batch_size: Optional[int] = None, iteration: Optional[int] = None,
    max_trials: Optional[int] = None, algorithm: Optional[str] = None,
    timeout: Optional[int] = None):
    tuning_config = {}
    with open(yaml_file) as f:
        yaml_config = yaml.round_trip_load(f, preserve_quotes=True)

    if algorithm:
        try:
            model_wise = yaml_config.get("quantization", {}).get("model_wise", {})
            prev_activation = model_wise.get("activation", {})
            if not prev_activation:
                model_wise.update({"activation": {}})
                prev_activation = model_wise.get("activation", {})
            prev_activation.update({"algorithm": algorithm})

            weight = model_wise.get("weight", {})
            if not weight:
                model_wise.update({"weight": {}})
                weight = model_wise.get("weight", {})
            weight.update({"algorithm": algorithm})
        except Exception as e:
            print(f"[ WARNING ] {e}")

    if timeout:
        try:
            exit_policy = yaml_config.get("tuning", {}).get("exit_policy", {})
            prev_timeout = exit_policy.get("timeout", None)
            exit_policy.update({"timeout": timeout})
            print(f"Changed {prev_timeout} to {timeout}")
        except Exception as e:
            print(f"[ WARNING ] {e}")

    if strategy:
        try:
            tuning_config = yaml_config.get("tuning", {})
            prev_strategy = tuning_config.get("strategy", {})
            if not prev_strategy:
                tuning_config.update({"strategy": {}})
                prev_strategy = tuning_config.get("strategy", {})
            strategy_name = prev_strategy.get("name", None)
            prev_strategy.update({"name": strategy})
            print(f"Changed {strategy_name} to {strategy}")
        except Exception as e:
            print(f"[ WARNING ] {e}")

    if max_trials and max_trials > 0:
        try:
            tuning_config = yaml_config.get("tuning", {})
            prev_exit_policy = tuning_config.get("exit_policy", {})
            if not prev_exit_policy:
                tuning_config.update({"exit_policy": {
                    "max_trials": max_trials
                }})
            else:
                prev_max_trials = prev_exit_policy.get("max_trials", None)
                prev_exit_policy.update({"max_trials": max_trials})
                print(f"Changed {prev_max_trials} to {max_trials}")
        except Exception as e:
            print(f"[ WARNING ] {e}")

    if mode == 'accuracy':
        try:
            # delete performance part in yaml if exist
            performance = yaml_config.get("evaluation", {}).get("performance", {})
            if performance:
                yaml_config.get("evaluation", {}).pop("performance", {})
            # accuracy batch_size replace
            if batch_size:
                try:
                    dataloader = yaml_config.get("evaluation", {}).get("accuracy", {}).get("dataloader", {})
                    prev_batch_size = dataloader.get("batch_size", None)
                    dataloader.update({"batch_size": batch_size})
                    print(f"Changed accuracy batch size from {prev_batch_size} to {batch_size}")
                except Exception as e:
                    print(f"[ WARNING ] {e}")
        except Exception as e:
            print(f"[ WARNING ] {e}")
    elif mode:
        try:
            # delete accuracy part in yaml if exist
            accuracy = yaml_config.get("evaluation", {}).get("accuracy", {})
            if accuracy:
                yaml_config.get("evaluation", {}).pop("accuracy", {})
            # performance iteration replace
            if iteration:
                try:
                    performance = yaml_config.get("evaluation", {}).get("performance", {})
                    prev_iteration = performance.get("iteration", None)
                    performance.update({"iteration": iteration})
                    print(f"Changed performance batch size from {prev_iteration} to {iteration}")
                except Exception as e:
                    print(f"[ WARNING ] {e}")
        except Exception as e:
            print(f"[ WARNING ] {e}")

    print(f"Saving yaml config back to {yaml_file}")

    yaml_content = yaml.round_trip_dump(yaml_config)

    with open(yaml_file, 'w') as output_file:
        output_file.write(yaml_content)
