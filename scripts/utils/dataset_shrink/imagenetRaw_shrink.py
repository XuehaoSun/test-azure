import argparse
import os
import re
from typing import Tuple
import shutil

IMAGES_DIR = "ILSVRC2012_img_val"

def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--dataset_location",
        type=str,
        required=True,
        help=f"Path to ImagenetRaw dataset directory. \
        Dataset dir should contain val.txt file and folder with images named {IMAGES_DIR}"
    )
    parser.add_argument(
        "--dest",
        type=str,
        required=True,
        help="Destination path for shrinked dataset."
    )
    parser.add_argument(
        "--samples_decrease_ratio",
        type=int,
        default=10,
        help="Ratio of decreasing number of samples per class."
    )
    return parser.parse_args()


def read_images_info(dataset_location) -> Tuple[dict, int]:
    images_info = {}
    info_pattern = r"(.*)\s(\d+)"

    with open(os.path.join(dataset_location, "val.txt")) as val_file:
        for line in val_file:
            search = re.search(info_pattern, line)
            if search:
                img_file = search.group(1)
                img_class = search.group(2)
            
                images_info.update({img_file: img_class})
    return images_info


def shrink_dataset(dataset_location, destination, images_info, images_per_class):

    shrinked_images_info = {}
    shrinked_images_by_class = {}
    for image, img_class in images_info.items():
        class_samples = len(shrinked_images_by_class.get(img_class, []))
        if class_samples >= images_per_class:
            continue
        if img_class not in shrinked_images_by_class:
            shrinked_images_by_class.update({img_class: []})
        shrinked_images_by_class.get(img_class).append(image)
        shrinked_images_info.update({image: img_class})
        
    return shrinked_images_info

def save_dataset(dataset_location, dest, samples_info):
    images_path = os.path.join(dest, IMAGES_DIR)
    val_path = os.path.join(dest, "val.txt")

    os.makedirs(images_path, exist_ok=True)
    if os.listdir(images_path):
        raise Exception("Images path is not empty!")

    print(f"Saving val file to: {val_path}")
    with open(val_path, "w") as val_file:
        for image, img_class in samples_info.items():
            val_file.write(f"{image} {img_class}\n")

    print(f"Copying images to {os.path.join(dest,IMAGES_DIR)} directory")
    for img_name in samples_info.keys():
        shutil.copyfile(
            src=os.path.join(dataset_location, IMAGES_DIR, img_name),
            dst=os.path.join(dest, IMAGES_DIR, img_name)
        )

def main(dataset_location, destination, samples_decrease_ratio):
    current_samples = read_images_info(dataset_location)
    classes = set(img_classes for img_classes in current_samples.values())
    class_num = len(classes)
    images_per_class = len(current_samples.keys())//class_num
    final_images_per_class = images_per_class//samples_decrease_ratio
    print(f"Final images per class: {final_images_per_class}")
    print(f"Source dataset:\n\tClasses found: {class_num}\n\tImages per class: {images_per_class}")
    if samples_decrease_ratio > images_per_class:
        raise Exception(f"Cannot shrink more than {images_per_class} times.")

    print(f"Shrinking {samples_decrease_ratio} times.")
    shrinked_samples = shrink_dataset(dataset_location, destination, current_samples, final_images_per_class)

    shrinked_classes = set(img_classes for img_classes in shrinked_samples.values())
    shrinked_class_num = len(classes)
    shrinked_images_per_class = len(shrinked_samples.keys())//class_num
    print(f"Shrinked dataset:\n\tClasses found: {shrinked_class_num}\n\tImages per class: {shrinked_images_per_class}\n\tTotal images: {len(shrinked_samples.keys())}")
    print(f"Shrinked {len(current_samples.keys())} samples to {len(shrinked_samples.keys())} samples")

    save_dataset(dataset_location, destination, shrinked_samples)



if __name__ == "__main__":
    args = parse_args()
    main(
        dataset_location=args.dataset_location,
        samples_decrease_ratio=args.samples_decrease_ratio,
        destination=args.dest,
        )
