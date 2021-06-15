#!/bin/bash -x
###########################################################################################################
# This feature test scripts is for timeout function test on "helloworld" example:
# 1. create conda env for both tf1.15.2 and tf2.3.0
# 2. test base functionality
# 3. test timeout for keras running
###########################################################################################################

function main {
  # 1. create conda env
  export PATH=${HOME}/miniconda3/bin/:$PATH
  # pip config set global.index-url https://pypi.douban.com/simple/
  create_conda_env 2.3.0

  # 2. train for pre-train models
  cd ${WORKSPACE}/lpot-models/examples/helloworld || return
  python train.py

  # 5. test for timeout function
  helloworld_timeout 2>&1 | tee ${WORKSPACE}/helloworld_timeout.log
}

function helloworld_timeout {
  # update timeout
  yaml=${WORKSPACE}/lpot-models/examples/helloworld/tf_example2/conf.yaml
  python ${WORKSPACE}/lpot-validation/scripts/update_yaml_config.py --yaml=${yaml} --timeout=10
  echo "yaml after update timeout...."
  cat ${yaml}
  cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example2 || return
  lpot_install
  python test.py
}

function create_conda_env {
  tensorflow_version=$1
  python_version=3.6
  conda_env_name=tf${tensorflow_version}-py${python_version}-timeout

  if [ $(conda info -e | grep ${conda_env_name} | wc -l) == 0 ]; then
      conda create python=${python_version} -y -n ${conda_env_name}
  fi
  # make sure no more conda nested
  conda deactivate
  conda deactivate
  source activate ${conda_env_name}
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
  c_lpot=$(pip list | grep -c 'lpot') || true  # Prevent from exiting when 'lpot' not found
  if [ ${c_lpot} != 0 ]; then
      pip uninstall lpot -y
      pip list
  fi
  pip install ${WORKSPACE}/lpot*.whl
  pip list
}

main
