#!/bin/bash

output_log_dir=$1

conda_env_name="sparse_lib"
export PATH=${HOME}/miniconda3/bin/:$PATH
conda activate $conda_env_name || source activate $conda_env_name
conda install -c conda-forge gxx gcc cmake -y

rm -rf ${WORKSPACE}/lpot-models/nlp_toolkit/backends/neural_engine/build
cd ${WORKSPACE}/lpot-models
git submodule update --init --recursive
cd nlp_toolkit/backends/neural_engine
mkdir build
cd build
if [[ -n $(lscpu | grep amx_tile) ]]; then cmake_amx="-DSPARSE_LIB_USE_AMX=True"; fi
CC=gcc CXX=g++ cmake .. -DNE_WITH_SPARSELIB=ON -DNE_WITH_TESTS=ON -DNE_WITH_SPARSELIB_BENCHMARK=ON -DPYTHON_EXECUTABLE=$(which python) $cmake_amx 
make -j
cd bin
bash -x ${WORKSPACE}/lpot-models/nlp_toolkit/backends/neural_engine/test/SparseLib/benchmark/ci/run_ci.sh $output_log_dir
for caselog in $(find $output_log_dir/*); do
    case_name=$(echo $caselog | sed -e 's/\.log$//')
    echo "case_name=$case_name"
    bash -x ${WORKSPACE}/lpot-models/nlp_toolkit/backends/neural_engine/test/SparseLib/benchmark/ci/to_summary.sh $caselog | tee "${case_name}_summary.log"
done
