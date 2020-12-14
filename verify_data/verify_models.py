import json
import os
from typing import Optional

import utils as utils
from model import Model

args = utils.parse_args(default_config="configs/models.json")

mode = "remote" if args.remote else "local"

checksums = {}

def main():
    with open(args.config) as json_file:
        models_config = json.load(json_file)

    if models_config is None:
        raise Exception(f"[ ERROR ] Could not load models config. Make sure that there is a \"{args.config}\" file in the same directory.")
    
    for framework, groups in models_config.items():
        if args.framework not in ["all", framework]:
            continue
        for group, models in groups.items():
            if args.group not in ["all", group]:
                continue

            for model in models:
                model = Model(model)
                print(f"\n\n[ INFO ] Getting {model.name} model for {framework} {group}")

                if not valid_model(model):
                    continue
                
                model_dir_tree = [args.lpot_repo, "examples", framework, group, model.name]
                model.datadir = os.path.join(*model_dir_tree)
                while not os.path.isdir(model.datadir):
                    model_dir_tree.pop()
                    model.datadir = os.path.join(*model_dir_tree)


                if len(model.topologies) > 0:
                    for topology in model.topologies:
                        # Do preprocessing for topology
                        print(f"\n\n[ INFO ] Getting {topology} model for {framework} {group} {model.name}")
                        model.update_local_path(framework, group, topology)
                        if model.local_path == "":
                            print("[ INFO ] Could not get local path.")
                            continue
                        model.update_remote_path(framework, group, topology)
                        config = f"{framework}-{group}-{topology}"
                        if model.name == "bert_base_glue":
                            config += "-base"
                        elif model.name == "bert_large_glue":
                            config += "-large"

                        check_model(model, config, topology)
                else:
                    config = f"{framework}-{group}-{model.name}"
                    check_model(model, config)
                    


def valid_model(model: Model) -> bool:
    """Check if model can be preprocesed."""
    if model.skip:
        print("[ INFO ] Skipping model.")
        return False

    if model.built_in:
        print("[ INFO ] Model download is integrated in tuning script.")
        return False
    
    if model.name == "generic" and len(model.topologies) <= 0:
        print(f"[ WARNING ] No specified topologies for generic group.")
        return False
    return True


def check_model(model: Model, config: str, topology: Optional[str] = None) -> None:
    """Check model."""
    processed = process_model(model, topology)
    if not processed:
        raise Exception("Could not process model.")

    # Calculate checksum for model
    model_summary = {}
    checksum = model.calculate_md5(mode, topology, args.test)
    model_summary[mode] = checksum

    print(f"[ INFO ] Checksum for {config}:\n{checksum}")
    checksums[config] = model_summary

    file_path = f"model_checksums-{args.framework}-{args.group}-{mode}.json"
    if args.test:
        file_path = f"test_{file_path}"
    utils.save_to_json(checksums, file_path)

def process_model(model: Model, topology: Optional[str] = None) -> bool:
    """Process model."""
    if mode == "remote":  # Do not download model on data storage server
        return True
    downloaded = False
    for source in model.sources:
        downloaded = source.get_source(model.datadir, args.test)

    if downloaded and len(model.execution) <= 0:
        return True

    if not downloaded and len(model.execution) <= 0:
        print("[ ERROR ] Missing execution command for model preprocessing.")
        return False

    try:
        model.execute(topology, args.test)
        return True
    except Exception as err:
        print(f"[ ERROR ] {err}")
        return False


if __name__ == "__main__":
    main()
