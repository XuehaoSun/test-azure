import ruamel.yaml as yaml
import argparse
import re


parser = argparse.ArgumentParser()
parser.add_argument("--yaml", type=str, required=True, help="Path to yaml config.")
parser.add_argument("--strategy", type=str, required=False, help="Strategy to update.")
parser.add_argument("--calib-data", type=str, required=False, help="Path to calibration dataset.")
parser.add_argument("--eval-data", type=str, required=False, help="Path to evaluation dataset.")
args = parser.parse_args()

if not (args.strategy or args.calib_data or args.eval_data):
    raise Exception("No changes. Please specify strategy, calib_data or eval_data parameter to make changes in yaml file.")

tuning_config = {}
with open(args.yaml) as yaml_file:
    yaml_config = yaml.round_trip_load(yaml_file, preserve_quotes=True)

if args.strategy:
    tuning_config = yaml_config.get("tuning", {})
    if isinstance(tuning_config, list):
        for config in tuning_config:
            strategy = config.get("strategy", None)
            config.update({"strategy": args.strategy})
            print(f"Changed {strategy} to {args.strategy}")
    else:
        strategy = tuning_config.get("strategy", None)
        tuning_config.update({"strategy": args.strategy})
        print(f"Changed {strategy} to {args.strategy}")

if args.calib_data:
    try:
        dataset = yaml_config.get("calibration", {}).get("dataloader", {}).get("dataset", {})
        for item in dataset:
            if "root" in item.keys():
                calib_data = item.get("root")
                item.update({"root": args.calib_data})
                print(f"Replaced calibration dataset path from {calib_data} to {args.calib_data}.")
    except Exception as e:
        print(f"[ WARNING ] {e}")

if args.eval_data:
    try:
        dataset = yaml_config.get("evaluation", {}).get("dataloader", {}).get("dataset", {})
        for item in dataset:
            if "root" in item.keys():
                eval_data = item.get("root")
                item.update({"root": args.eval_data})
                print(f"Replaced evaluation dataset path from {eval_data} to {args.eval_data}.")
    except Exception as e:
        print(f"[ WARNING ] {e}")

print(f"Saving yaml config back to {args.yaml}")

yaml_content = yaml.round_trip_dump(yaml_config)

with open(args.yaml, 'w') as output_file:
    output_file.write(yaml_content)
