# install dependency package

install torch and torchvision, you can ignore it if you install these packages already.

```
pip install -r requirements.txt
```

# dataset

imagenet

## patch

```
cd PT/gen-efficientnet-pytorch
cp ../workload/gen-efficientnet-pytorch/main.py .
python setup.py install
```

# run training and inference

```
python ./main.py -e \
    --performance \
    --pretrained \
    --no-cuda \
    --ipex \
    -j 1 \
    -a ${model} \
    -b 32 \
    --dummy \
    --jit \
    --precision float32 \
```

In run_EfficientNet.sh, you can set different models by set -a $model_name which model_name is one of "efficientnet-b0" ~ "efficientnet-b8", "efficientnet-l2", and all torchvision models.

## help info

```python
python main.py -h
usage: main.py [-h] [--data DIR] [-a ARCH] [-j N] [--epochs N]
               [--start-epoch N] [-b N] [--lr LR] [--momentum M] [--wd W]
               [-p N] [--resume PATH] [-e] [--pretrained]
               [--world-size WORLD_SIZE] [--rank RANK] [--ppn PPN]
               [--dist-url DIST_URL] [--dist-backend DIST_BACKEND]
               [--seed SEED] [--gpu GPU] [--image_size IMAGE_SIZE] [--advprop]
               [--multiprocessing-distributed] [--ipex] [--jit]
               [--precision PRECISION] [--no-cuda] [-i N] [-w N] [-t]
               [--performance] [--dummy] [--num-classes NUM_CLASSES]

PyTorch ImageNet Training

optional arguments:
  -h, --help            show this help message and exit
  --data DIR            path to dataset
  -a ARCH, --arch ARCH  model architecture (default: resnet18)
  -j N, --workers N     number of data loading workers (default: 1)
  --epochs N            number of total epochs to run
  --start-epoch N       manual epoch number (useful on restarts)
  -b N, --batch-size N  mini-batch size (default: 256), this is the total
                        batch size of all GPUs on the current node when using
                        Data Parallel or Distributed Data Parallel
  --lr LR, --learning-rate LR
                        initial learning rate
  --momentum M          momentum
  --wd W, --weight-decay W
                        weight decay (default: 1e-4)
  -p N, --print-freq N  print frequency (default: 10)
  --resume PATH         path to latest checkpoint (default: none)
  -e, --evaluate        evaluate model on validation set
  --pretrained          use pre-trained model
  --world-size WORLD_SIZE
                        number of nodes for distributed training
  --rank RANK           node rank for distributed training
  --ppn PPN             number of processes on each node of distributed
                        training
  --dist-url DIST_URL   url used to set up distributed training
  --dist-backend DIST_BACKEND
                        distributed backend
  --seed SEED           seed for initializing training.
  --gpu GPU             GPU id to use.
  --image_size IMAGE_SIZE
                        image size
  --advprop             use advprop or not
  --multiprocessing-distributed
                        Use multi-processing distributed training to launch N
                        processes per node, which has N GPUs. This is the
                        fastest way to use PyTorch for either single node or
                        multi node data parallel training
  --ipex                use ipex weight cache
  --jit                 enable Intel_PyTorch_Extension JIT path
  --precision PRECISION
                        precision, float32, int8, bfloat16
  --no-cuda             disable CUDA
  -i N, --iterations N  number of total iterations to run
  -w N, --warmup-iterations N
                        number of warmup iterations to run
  -t, --profile         Trigger profile on current topology.
  --performance         measure performance only, no accuracy.
  --dummy               using dummu data to test the performance of inference
  --num-classes NUM_CLASSES
                        Number classes in dataset
```
