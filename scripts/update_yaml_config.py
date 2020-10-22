import ruamel.yaml as yaml
import argparse
import re


parser = argparse.ArgumentParser()
parser.add_argument("--yaml", type=str, required=True, help="Path to yaml config.")
parser.add_argument("--strategy", type=str, required=False, help="Strategy to update.")
parser.add_argument("--mode", type=str, required=False, help="Benchmark mode.")
parser.add_argument("--batch-size", type=int, required=False, help="Benchmark batch size.")
parser.add_argument("--iteration", type=int, required=False, help="Benchmark iteration")

args = parser.parse_args()

tuning_config = {}
with open(args.yaml) as yaml_file:
    yaml_config = yaml.round_trip_load(yaml_file, preserve_quotes=True)

if args.strategy:
    try:
        tuning_config = yaml_config.get("tuning", {})
        strategy = tuning_config.get("strategy", {})
        if not strategy:
            tuning_config.update({"strategy": {}})
            strategy = tuning_config.get("strategy", {})
        strategy_name = strategy.get("name", None)
        strategy.update({"name": args.strategy})
        print(f"Changed {strategy_name} to {args.strategy}")
    except Exception as e:
        print(f"[ WARNING ] {e}")

if args.mode == 'accuracy':
    try:
        # delete performance part in yaml if exist
        performance = yaml_config.get("evaluation", {}).get("performance", {})
        if performance:
            yaml_config.get("evaluation", {}).pop("performance", {})
        # accuracy batch_size replace
        if args.batch_size:
            try:
                dataloader = yaml_config.get("evaluation", {}).get("accuracy", {}).get("dataloader", {})
                batch_size = dataloader.get("batch_size", None)
                dataloader.update({"batch_size": args.batch_size})
                print(f"Changed accuracy batch size from {batch_size} to {args.batch_size}")
            except Exception as e:
                print(f"[ WARNING ] {e}")
    except Exception as e:
        print(f"[ WARNING ] {e}")
elif args.mode:
    try:
        # delete accuracy part in yaml if exist
        accuracy = yaml_config.get("evaluation", {}).get("accuracy", {})
        if accuracy:
            yaml_config.get("evaluation", {}).pop("accuracy", {})
        # performance iteration replace
        if args.iteration:
            try:
                performance = yaml_config.get("evaluation", {}).get("performance", {})
                iteration = performance.get("iteration", None)
                performance.update({"iteration": args.iteration})
                print(f"Changed performance batch size from {iteration} to {args.iteration}")
            except Exception as e:
                print(f"[ WARNING ] {e}")
    except Exception as e:
        print(f"[ WARNING ] {e}")

print(f"Saving yaml config back to {args.yaml}")

yaml_content = yaml.round_trip_dump(yaml_config)

with open(args.yaml, 'w') as output_file:
    output_file.write(yaml_content)
