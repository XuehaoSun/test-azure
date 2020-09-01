import argparse
import csv
import json
import re
from typing import Any, Dict, List, Optional, Union


def parse_args(default_config: str) -> argparse.Namespace:
    """Parse input arguments."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--ilit-repo", type=str, required=True, help="Path to iLiT repository.")
    parser.add_argument("--framework", choices=["all", "mxnet", "pytorch", "tensorflow"], default="all", help="Framework.")
    parser.add_argument("--group", type=str, choices=["all", "image_recognition", "object_detection", "language_translation", "recommendation"], default="all", help="Model class.")
    parser.add_argument("--remote", action="store_true", help="If collecting checksums from data storage. Skips downloading when set.")
    parser.add_argument("--test", action="store_true", help="Flag for printing commands without execution.")
    parser.add_argument("--config", type=str, default=default_config, help="Path to json config file.")
    return parser.parse_args()


def save_to_json(results: dict, filename: str, cls = None) -> None:
    """Write results to file."""
    with open(filename, "w") as json_file:
        json.dump(results, json_file, indent=4, cls=cls)


def map_local_directory(framework: str, group: str, topology: str) -> Optional[str]:
    """Map local directory for not regular structure."""
    directory_map = {
        "tensorflow": {
            "image_recognition": {
                "inception_v1": "",
                "inception_v2": "",
                "inception_v3": "inception_v3.pb",
                "inception_v4": "inception_v4.pb",
                "mobilenet_v1": "mobilenet_v1/mobilenet_v1_1.0_224_frozen.pb",
                "mobilenet_v2": "mobilenet_v2/mobilenet_v2_1.0_224_frozen.pb",
                "mobilenet_v3": "",
                "resnet50_v1": "resnet_v1_50.pb",
                "resnet101_v1": "resnet_v1_101.pb",
                "resnet_v2_50": "",
                "resnet_v2_101": "",
                "resnet_v2_152": ""
            },
            "object_detection": {
                "ssd_resnet50_v1": "ssd_resnet50_v1/ssd_resnet50_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03/frozen_inference_graph.pb",
                "ssd_mobilenet_v1": "ssd_mobilenet_v1/ssd_mobilenet_v1_coco_2018_01_28/frozen_inference_graph.pb"

            }
        }
    }
    return directory_map.get(framework, {}).get(group, {}).get(topology, None)


def map_remote_directory(framework: str, group: str, topology: str) -> Optional[str]:
    """Map remote directory for not regular structure."""
    directory_map = {
        "tensorflow": {
            "image_recognition": {
                "inception_v1": "/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_inception_v1.pb",
                "inception_v2": "/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_inception_v2.pb",
                "inception_v3": "/tf_dataset/pre-trained-models/inceptionv3/fp32/freezed_inceptionv3.pb",
                "inception_v4": "/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_inception_v4.pb",
                "mobilenet_v1": "/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_mobilenet_v1.pb",
                "mobilenet_v2": "/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_mobilenet_v2.pb",
                "resnet50_v1": "/tf_dataset/pre-trained-models/resnet50/fp32/freezed_resnet50.pb",
                "resnet101_v1": "/tf_dataset/pre-trained-models/resnet101/fp32/optimized_graph.pb",
                "resnet_v2_50": "/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_resnet_v2_50.pb",
                "resnet_v2_101": "/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_resnet_v2_101.pb",
                "resnet_v2_152": "/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_resnet_v2_152.pb"
            }
        }
    }

    return directory_map.get(framework, {}).get(group, {}).get(topology, None)
