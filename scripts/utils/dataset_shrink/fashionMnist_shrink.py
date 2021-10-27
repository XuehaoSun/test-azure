import argparse
import os
import gzip
import numpy as np

NUM_CLASSES = 10

def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset_location", type=str, required=True, help="Path to FashionMNIST dataset directory.")
    parser.add_argument("--samples_decrease_ratio", type=int, default=10, help="Ratio of decreasing number of samples per class.")
    return parser.parse_args()


def load_dataset(dataset_location, kind):
    labels_path = os.path.join(dataset_location, f"{kind}-labels-idx1-ubyte.gz")
    images_path = os.path.join(dataset_location, f"{kind}-images-idx3-ubyte.gz")

    with gzip.open(labels_path, 'rb') as lbpath:
        offset = 8
        data = np.frombuffer(lbpath.read(), dtype=np.uint8)
        labels_header = data[:offset]
        labels = data[offset:]
    with gzip.open(images_path, 'rb') as imgpath:
        offset = 16
        data = np.frombuffer(imgpath.read(), dtype=np.uint8)
        images_header = data[:offset]
        images = data[offset:].reshape(len(labels), 784)

    return images, labels, images_header, labels_header

def save_dataset(images, labels, images_header, labels_header, dataset_location, kind):
    dataset_location_small = os.path.join(dataset_location, "small")
    os.makedirs(dataset_location_small, exist_ok=True)
    labels_path = os.path.join(dataset_location_small, f"{kind}-labels-idx1-ubyte.gz")
    images_path = os.path.join(dataset_location_small, f"{kind}-images-idx3-ubyte.gz")

    labels_data = np.concatenate((labels_header, labels))
    with gzip.open(labels_path, 'wb') as lbpath:
        lbpath.write(labels_data)
    print(f"Saved labels file to {labels_path}")

    images_data = np.concatenate((images_header, images.flatten()))
    with gzip.open(images_path, 'wb') as imgpath:
        imgpath.write(images_data)
    print(f"Saved images file to {images_path}")


def get_class_indices(np_label_list):
    indices = {}
    for idx in range(0, 10):
        indices.update({idx: np.where(np_label_list == idx)[0]})
    
    return indices


def shrink_dataset(samples, labels, target_samples):
    samples_per_class = target_samples//NUM_CLASSES
    samples_small = np.empty(shape=(target_samples, samples.shape[1]), dtype="uint8")
    labels_small = np.empty(shape=target_samples, dtype="uint8")

    shrinked_idx = 0

    for idx in range(0, samples.shape[0]):
        try:
            if np.where(labels_small == labels[idx])[0].size < samples_per_class:
                labels_small[shrinked_idx] = labels[idx]
                samples_small[shrinked_idx] = samples[idx]
                shrinked_idx += 1
        except Exception as e:
            print(f"Exception: {e}")
        if shrinked_idx == target_samples:
            break

    return samples_small, labels_small


def main(dataset_location, samples_decrease_ratio):
    X_test, y_test, X_test_header, y_test_header = load_dataset(dataset_location, kind='t10k')
    X_train, y_train, X_train_header, y_train_header = load_dataset(dataset_location, kind='train')

    target_test_samples = X_test.shape[0] // samples_decrease_ratio
    target_train_samples = X_train.shape[0] // samples_decrease_ratio

    samples_test_small, labels_test_small = shrink_dataset(X_test, y_test, target_test_samples)
    save_dataset(
        images=samples_test_small,
        labels=labels_test_small,
        images_header=X_test_header,
        labels_header=y_test_header,
        dataset_location=dataset_location,
        kind='t10k',
        )

    samples_train_small, labels_train_small = shrink_dataset(X_train, y_train, target_train_samples)
    save_dataset(
        images=samples_train_small,
        labels=labels_train_small,
        images_header=X_train_header,
        labels_header=y_train_header,
        dataset_location=dataset_location,
        kind="train",
        )


if __name__ == "__main__":
    args = parse_args()
    main(
        dataset_location=args.dataset_location,
        samples_decrease_ratio=args.samples_decrease_ratio,
        )
