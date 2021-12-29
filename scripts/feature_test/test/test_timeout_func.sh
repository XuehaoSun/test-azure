#!/bin/bash -x
###########################################################################################################
# This feature test scripts is for timeout function test on "resnet50_v1.5" example:
# 1. create conda env for both tf1.15.2 and tf2.3.0
# 2. test base functionality
# 3. test timeout for resnet50_v1.5 running
###########################################################################################################

PATTERN='[-a-zA-Z0-9_]*='
for i in "$@"
do
    case $i in
        --python_version=*)
            python_version=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

function main {
  # 1. create conda env
  # export PATH=${HOME}/miniconda3/bin/:$PATH
  if [ -f "${HOME}/miniconda3/etc/profile.d/conda.sh" ]; then
      . "${HOME}/miniconda3/etc/profile.d/conda.sh"
  else
      export PATH="${HOME}/miniconda3/bin:$PATH"
  fi
  # pip config set global.index-url https://pypi.douban.com/simple/
  create_conda_env 2.7.0
  lpot_install

  # 2. prepare
  cd ${WORKSPACE}/lpot-models/examples/tensorflow/image_recognition/tensorflow_models/quantization/ptq || return
  input_model="/tf_dataset/sh_models/PB_dir/resnet50_v15/resnet50_v1.pb"
  quantized_model="./models/quantized_resnet50_v1.pb"
  yaml="resnet50_v1_5.yaml"
  dataset="/tf_dataset/dataset/TF_Imagenet_Mini_val"

  # 5. test for timeout function
  test_timeout 2>&1 | tee ${WORKSPACE}/test_timeout_.log
}

function test_timeout {
    # update yaml
    sed -i "s+root:.*+root: ${dataset}+g" ${yaml}
    python ${WORKSPACE}/lpot-validation/scripts/update_yaml_config.py --yaml=${yaml} --timeout=300
    echo "yaml after update timeout $1 ...."
    cat ${yaml}
    # run
    bash run_tuning.sh --input_model=${input_model} --output_model=${quantized_model} --config=${yaml}
    if [ -f ${quantized_model} ];then
       bash run_benchmark.sh --config=${yaml} --mode=accuracy --input_model=${quantized_model}
    fi
}

function create_conda_env {
  tensorflow_version=$1
  conda_env_name=tf${tensorflow_version}-py${python_version}-timeout

  if [ $(conda info -e | grep ${conda_env_name} | wc -l) == 0 ]; then
      conda create python=${python_version} -y -n ${conda_env_name}
  fi
  # make sure no more conda nested
  conda deactivate
  conda activate ${conda_env_name}
  conda info -e
  pip install intel-tensorflow==${tensorflow_version}
  pip install ruamel.yaml==0.17.4
  pip list

  if [ ! -d ${WORKSPACE}/lpot-models ]; then
      echo "\"lpot-model\" not found. Exiting..."
      exit 1
  fi
  cd ${WORKSPACE}/lpot-models || return
}

function lpot_install {
  echo "Checking lpot..."
  python -V
  c_lpot=$(pip list | grep -c 'neural-compressor') || true  # Prevent from exiting when 'lpot' not found
  if [ ${c_lpot} != 0 ]; then
      pip uninstall neural-compressor -y
      pip list
  fi
  pip install ${WORKSPACE}/neural_compressor*.whl
  pip list
}

main
