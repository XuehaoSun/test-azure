import argparse
import json
import os

import utils as utils
from checksum_summary import ChecksumEncoder, ChecksumSummary

parser = argparse.ArgumentParser()
parser.add_argument("--framework", choices=["all", "mxnet", "pytorch", "tensorflow"], default="all", help="Framework.")
parser.add_argument("--group", type=str, choices=["all", "image_recognition", "object_detection", "language_translation", "recommendation"], default="all", help="Model class.")
parser.add_argument("--resource-type", type=str, choices=["dataset", "model"], required=True, help="Resource type: model or dataset.")
parser.add_argument("--logs-dir", type=str, default="results", help="Path to json logs with calculated checksums.")
args = parser.parse_args()

def main():
    configs = {}
    for mode in ["local", "remote"]:
        config_path = os.path.join(args.logs_dir, f"{args.resource_type}_checksums-{args.framework}-{args.group}-{mode}.json")
        with open(config_path, "r") as resources:
            configs.update({mode: json.load(resources) })


    # Collect config list
    config_list = []
    for mode in ["local", "remote"]:
        for config, _ in configs.get(mode, {}).items():
            config_list.append(config)

    config_list = list(dict.fromkeys(config_list))
    config_list.sort()

    concat_configs = {}
    for config in config_list:
        concat_configs[config] = {}
        remote_entry = ChecksumSummary(configs.get("remote", {}).get(config, {}).get("remote", {}))
        local_entry = ChecksumSummary(configs.get("local", {}).get(config, {}).get("local", {}))

        status = "SKIPPED"
        if remote_entry.checksum and local_entry.checksum:
            if remote_entry.checksum != local_entry.checksum:
                status = "INVALID"
                print(f"{config}\t:\t {status}\t:\tremote ({remote_entry.checksum}) vs local ({local_entry.checksum})")
            else:
                status = "VALID"
                print(f"{config}\t:\t {status}")
        else:
            print(f"{config}\t:\t {status}")

        concat_configs[config].update({"local": local_entry})
        concat_configs[config].update({ "remote": remote_entry })
        concat_configs[config].update({"status": status})

    filename = f"concat_checksums-{args.framework}-{args.group}"
    utils.save_to_json(concat_configs, f"{filename}.json", cls=ChecksumEncoder)

if __name__ == "__main__":
    main()
