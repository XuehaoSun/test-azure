import json
import os

import utils as utils
from dataset import Dataset

args = utils.parse_args(default_config="configs/datasets.json")
mode = "remote" if args.remote else "local"

def main():
    checksums = {}

    with open(args.config) as json_file:
        datasets_config = json.load(json_file)

    if datasets_config is None:
        raise Exception(f"[ ERROR ] Could not load datasets config. Make sure that there is a \"{args.config}\" file in the same directory.")
    
    for framework, groups in datasets_config.items():
        if args.framework not in ["all", framework]:
            continue
        for group, datasets in groups.items():
            if args.group not in ["all", group]:
                continue
            for dataset in datasets:
                dataset = Dataset(dataset)
                print(f"\n\n[ INFO ] Checking {dataset.name} dataset for {framework} {group}")
                config = f"{framework}-{group}-{dataset.name}"
                
                dataset.datadir = os.path.join(args.lpot_repo, "examples", framework, group)
                if framework == "pytorch" and group == "object_detection":
                    dataset.datadir = os.path.join(dataset.datadir, "yolo_v3")
                elif framework == "tensorflow" and group == "recommendation":
                    dataset.datadir = os.path.join(dataset.datadir, "wide_deep_large_ds")

                if dataset.skip:
                    print("[ INFO ] Skipping dataset.")
                    continue

                if dataset.built_in:
                    print("[ INFO ] Dataset download is integrated in tuning script.")
                    continue
                
                if mode == "local":  # Do not download dataset on data storage server
                    downloaded = False
                    for source in dataset.sources:
                        downloaded = source.get_source(dataset.datadir, args.test)

                    if not downloaded and len(dataset.execution) <= 0:
                        print("[ ERROR ] Missing execution command for dataset preprocessing.")
                        continue
                    
                    try:
                        dataset.execute(args.test)
                    except Exception as err:
                        print(f"[ ERROR ] {err}")
                        continue

                # Calculate checksum for newly prepared dataset
                dataset_summary = {}
                checksum = dataset.calculate_md5(mode, args.test)
                dataset_summary[mode] = checksum

                print(f"[ INFO ] Checksum for {config}:\n{checksum}")
                checksums[config] = dataset_summary

                file_path = f"dataset_checksums-{args.framework}-{args.group}-{mode}.json"
                if args.test:
                    file_path = f"test_{file_path}"
                utils.save_to_json(checksums, file_path)


if __name__ == "__main__":
    main()
