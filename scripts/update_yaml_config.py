import ruamel.yaml as yaml
import argparse
import re


parser = argparse.ArgumentParser()
parser.add_argument("--yaml", type=str, required=True, help="Path to yaml config.")
parser.add_argument("--strategy", type=str, required=False, help="Strategy to update.")
parser.add_argument("--calib-data", type=str, required=False, help="Path to calibration dataset.")
parser.add_argument("--eval-data", type=str, required=False, help="Path to evaluation dataset.")
parser.add_argument("--benchmark-data", type=str, required=False, help="Path to benchmark dataset.")
parser.add_argument("--batch-size", type=int, required=False, help="Benchmark batch size.")
parser.add_argument("--iteration", type=int, required=False, help="Benchmark iteration")

args = parser.parse_args()

tuning_config = {}
with open(args.yaml) as yaml_file:
    yaml_config = yaml.round_trip_load(yaml_file, preserve_quotes=True)

if args.strategy:

    tuning_config = yaml_config.get("tuning", {})
    strategy = {}
    if isinstance(tuning_config, list):
        # "-" mode
        for config in tuning_config:
            strategy = config.get("strategy", {})
            if not strategy:
                config.update({"strategy": {}})
                strategy = config.get("strategy", {})
            strategy_name = strategy.get("name", None)
            strategy.update({"name": args.strategy})
            print(f"Changed {strategy_name} to {args.strategy}")
    else:
        strategy = tuning_config.get("strategy", {})
        if not strategy:
            tuning_config.update({"strategy": {}})
            strategy = tuning_config.get("strategy", {})
        strategy_name = strategy.get("name", None)
        strategy.update({"name": args.strategy})
        print(f"Changed {strategy_name} to {args.strategy}")

# benchmark batch_size replace
if args.batch_size:
    try:
        benchmark = yaml_config.get("benchmark", {})
        if not benchmark:
            yaml_config.update({"benchmark": {}})
            benchmark = yaml_config.get("benchmark", {})
        dataloader = benchmark.get("dataloader", {})
        if not dataloader:
            benchmark.update({"dataloader": {}})
            dataloader = benchmark.get("dataloader", {})
        batch_size = dataloader.get("batch_size", None)
        dataloader.update({"batch_size": args.batch_size})
        print(f"Changed batch size from {batch_size} to {args.batch_size}")
    except Exception as e:
        print(f"[ WARNING ] {e}")

# benchmark iteration replace
if args.iteration:
    try:
        benchmark = yaml_config.get("benchmark", {})
        if not benchmark:
            yaml_config.update({"benchmark": {}})
            benchmark = yaml_config.get("benchmark", {})
        iteration = benchmark.get("iteration", None)
        benchmark.update({"iteration": args.iteration})
        print(f"Changed batch size from {iteration} to {args.iteration}")
    except Exception as e:
        print(f"[ WARNING ] {e}")


# for tuning dataset replace
if args.calib_data:

    try:
        dataset = yaml_config.get("dataloader", {}).get("dataset", {})
        root = dataset.get("root", None)
        dataset.update({"root": args.calib_data})
        print(f"Replaced dataset path {root} to {args.calib_data}")
    except Exception as e:
        print(f"[ WARNING ] {e}")

    # "-" mode such as pytorch example
    try:
        dataset = yaml_config.get("calibration", {}).get("dataloader", {}).get("dataset", {})
        for item in dataset:
            if "root" in item.keys():
                calib_data = item.get("root")
                item.update({"root": args.calib_data})
                print(f"Replaced calibration dataset path from {calib_data} to {args.calib_data}.")
    except Exception as e:
        print(f"[ WARNING ] {e}")

    # non "-" mode such as tf example
    try:
        dataset = yaml_config.get("calibration", {}).get("dataloader", {}).get("dataset", {})
        calib_data = dataset.get("root", None)
        if calib_data:
            dataset.update({"root": args.calib_data})
            print(f"Replaced calibration dataset path from {calib_data} to {args.calib_data}.")
    except Exception as e:
        print(f"[ WARNING ] {e}")

    try:
        dataset = yaml_config.get("evaluation", {}).get("dataloader", {}).get("dataset", {})
        for item in dataset:
            if "root" in item.keys():
                eval_data = item.get("root")
                item.update({"root": args.eval_data})
                print(f"Replaced evaluation dataset path from {eval_data} to {args.eval_data}.")
    except Exception as e:
        print(f"[ WARNING ] {e}")

    try:
        dataset = yaml_config.get("evaluation", {}).get("dataloader", {}).get("dataset", {})
        eval_data = dataset.get("root", None)
        if eval_data:
            dataset.update({"root": args.eval_data})
            print(f"Replaced evaluation dataset path from {eval_data} to {args.eval_data}.")
    except Exception as e:
        print(f"[ WARNING ] {e}")

# for benchmark dataset replace
if args.benchmark_data:
    try:
        dataset = yaml_config.get("benchmark", {}).get("dataloader", {}).get("dataset", {})
        benchmark_data = dataset.get("root", None)
        if benchmark_data:
            dataset.update({"root": args.benchmark_data})
            print(f"Replaced benchmark dataset path from {benchmark_data} to {args.benchmark_data}.")
    except Exception as e:
        print(f"[ WARNING ] {e}")

print(f"Saving yaml config back to {args.yaml}")

yaml_content = yaml.round_trip_dump(yaml_config)

with open(args.yaml, 'w') as output_file:
    output_file.write(yaml_content)
