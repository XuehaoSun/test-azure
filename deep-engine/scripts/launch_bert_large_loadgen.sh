#!/bin/bash -x

test_mode=$1
each_model=$2
weight=$3
config=$4
ncores_per_instance=$5
bs=$6
each_precision=$7

export PATH=${HOME}/miniconda3/bin/:$PATH
# use accuracy env which has deps installed
source activate deep-engine-accuracy
git clone --recurse-submodules https://github.com/mlcommons/inference.git mlperf_inference
cd mlperf_inference && git checkout r1.1 && git submodule update --init --recursive && cd loadgen
CFLAGS="-std=c++14" python setup.py install
cd ../..

pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp37-cp37m-manylinux2010_x86_64.whl
pip install transformers cmake absl-py

cd ${WORKSPACE}/deep-engine/deep_engine/examples/mlperf_v1.1
cp ${WORKSPACE}/deep-engine/deep_engine/executor/build/engine_py.*.so .
cp ${WORKSPACE}/deep-engine/deep_engine/executor/build/libengine.so .
cp ${weight} ./bert_large_loadgen.bin

export GLOG_minloglevel=2
mkdir -p ${WORKSPACE}/${each_model}

sockets=$(lscpu | grep 'Socket(s)' | cut -d: -f2 | xargs echo -n)
ncores_per_socket=$( lscpu | grep 'Core(s) per socket' | cut -d: -f2 | xargs echo -n)
cores=$(($sockets*$ncores_per_socket))
num_instance=$(($cores/$ncores_per_instance))

if [ ${test_mode} = 'benchmark' ]; then
  python run_engine.py --scenario=Offline --batch-size=${bs} --num-instance=${num_instance} --num-phy-cpus=${cores} --model=${config} --weight=./bert_large_loadgen.bin \
  2>&1|tee ${WORKSPACE}/${each_model}/${each_model}_${cores}_${ncores_per_instance}_${bs}_${each_precision}.log

else
  num_instance=$(($cores/$ncores_per_socket))
  python run_engine.py --scenario=Offline --batch-size=${bs} --num-instance=${num_instance} --num-phy-cpus=${cores} --accuracy --model=${config} --weight=./bert_large_loadgen.bin \
  2>&1|tee ${WORKSPACE}/${each_model}/${each_model}_accuracy_${each_precision}.log
fi

if [ ${test_mode} = 'benchmark' ]; then
  echo "benchmark log operate"
else
  accuracy=`grep 'f1' ${WORKSPACE}/${each_model}/${each_model}_accuracy_${each_precision}.log | cut -d':' -f3 | awk -F '}' '{printf("%.3f",$1)}'`
  echo "accuracy,${each_model},${each_precision},${accuracy}" >> ${WORKSPACE}/summary.log
fi