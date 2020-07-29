import yaml
import argparse

parser = argparse.ArgumentParser()
parser.add_argument("--yaml", type=str, required=True, help="Path to yaml config.")
parser.add_argument("--strategy", type=str, required=True, help="Strategy to update.")
args = parser.parse_args()

tuning_config = {}
with open(args.yaml) as yaml_file:
    yaml_config = yaml.load(yaml_file)

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

print(f"Saving yaml config back to {args.yaml}")
with open(args.yaml, 'w') as output_file:
    yaml.dump(yaml_config, output_file, default_flow_style=False)
