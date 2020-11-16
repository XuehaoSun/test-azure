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
  pip config set global.index-url https://pypi.douban.com/simple/
  create_conda_env 2.3.0

  # 2. train for pre-train models
  cd ${WORKSPACE}/ilit-models/examples/helloworld || return
  python train.py

  # 3. test for keras models tuning
  helloworld_keras 2>&1 | tee ${WORKSPACE}/helloworld_keras.log

  # 4. test for frozen pb tuning
  helloworld_pb 2>&1 | tee ${WORKSPACE}/helloworld_pb.log

  # 5. test for timeout function
  helloworld_timeout 2>&1 | tee ${WORKSPACE}/helloworld_timeout.log
}

function helloworld_timeout {
  # update timeout
  yaml=${WORKSPACE}/ilit-models/examples/helloworld/tf2.x/conf.yaml
  python ${WORKSPACE}/ilit-validation/scripts/update_yaml_config.py --yaml=${yaml} --timeout=1
  echo "yaml after update timeout...."
  cat ${yaml}
  helloworld_keras
  python ${WORKSPACE}/ilit-validation/scripts/update_yaml_config.py --yaml=${yaml} --timeout=200
  echo "yaml after update timeout...."
  cat ${yaml}
  helloworld_keras
}

function helloworld_pb {
  cd ${WORKSPACE}/ilit-models/examples/helloworld || return
  if [ ! -d frozen_models ]; then
    echo " frozen pb not generated. Exiting..."
    return
  fi
  create_conda_env 1.15.2
  ilit_install
  cd ${WORKSPACE}/ilit-models/examples/helloworld/tf1.x || return
  python test.py
}

function helloworld_keras {
  cd ${WORKSPACE}/ilit-models/examples/helloworld || return
  if [ ! -d models ]; then
    echo " keras models not generated. Exiting..."
    return
  fi
  create_conda_env 2.3.0
  ilit_install
  cd ${WORKSPACE}/ilit-models/examples/helloworld/tf2.x || return
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
  pip install ruamel.yaml
  pip list

  if [ ! -d ${WORKSPACE}/ilit-models ]; then
      echo "\"ilit-model\" not found. Exiting..."
      exit 1
  fi
  cd ${WORKSPACE}/ilit-models || return
}

function ilit_install {
  echo "Checking ilit..."
  python -V
  c_ilit=$(pip list | grep -c 'ilit') || true  # Prevent from exiting when 'ilit' not found
  if [ ${c_ilit} != 0 ]; then
      pip uninstall ilit -y
      pip list
  fi
  pip install ${WORKSPACE}/ilit*.whl
  pip list
}

main