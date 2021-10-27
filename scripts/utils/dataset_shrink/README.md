# Scripts in this directory can be used to create mini dataset from the original one.

## FashionMnist
Usage:
```shell
python3 fashionMnist_shrink.py \
    --dataset_location=/path/to/fashionMnist/dataset \
    --samples_decrease_ratio=compress_ratio
```

## ImagenetRaw
Usage:
```shell
python3 imagenetRaw_shrink.py \
    --dataset_location=/path/to/ImagenetRaw/dataset \
    --dest=/path/to/output/dataset \
    --samples_decrease_ratio=compress_ratio

```
> Dataset location should contain `val.txt` file and `ILSVRC2012_img_val` directory with images.
