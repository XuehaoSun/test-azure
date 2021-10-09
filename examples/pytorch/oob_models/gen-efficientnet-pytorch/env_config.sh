conda activate oob_pytorch
which python
conda install numpy ninja pyyaml mkl mkl-include setuptools cmake cffi typing_extensions future six requests dataclasses pandas

## pytorchipex install start
## pytorch
if [ ! -d "pytorch" ]; then
    git clone --recursive https://github.com/pytorch/pytorch
fi
cd pytorch
pytorch_directory=$(pwd)

git checkout v1.5.0-rc3
git reset HEAD --hard
git submodule sync
git submodule update --init --recursive
cd ../


if [ ! -d "intel-extension-for-pytorch" ]; then
    git clone https://github.com/intel/intel-extension-for-pytorch.git 
fi

cd intel-extension-for-pytorch
intel_extension_for_pytorch_directory=$(pwd)
git reset 5f3f38c --hard
git submodule sync
git submodule update --init --recursive

cd ${pytorch_directory}
git apply ${intel_extension_for_pytorch_directory}/torch_patches/dpcpp-v1.5-rc3.patch


cd ${pytorch_directory}
python setup.py install

pip install lark-parser hypothesis
cd ${intel_extension_for_pytorch_directory}
python setup.py install
cd ../

## pytorch-ipex install done
