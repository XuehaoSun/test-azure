#!/bin/bash

set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='

for i in "$@"
do
    case $i in
        --framework=*)
            framework=`echo $i | sed "s/${PATTERN}//"`;;
        --model=*)
            model=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_mode=*)
            conda_env_mode=`echo $i | sed "s/${PATTERN}//"`;;
        --log_level=*)
            log_level=`echo $i | sed "s/${PATTERN}//"`;;
        --itex_mode=*)
            itex_mode=`echo $i | sed "s/${PATTERN}//"`;;
        --install_inc=*)
            install_inc=`echo $i | sed "s/${PATTERN}//"`;;
        --install_nlp_toolkit=*)
            install_nlp_toolkit=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

# ------------------------------------- Environment -------------------------------------

function set_TF_env {
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export TF_MKL_OPTIMIZE_PRIMITIVE_MEMUSE=false

    export PATH=${HOME}/miniconda3/bin/:$PATH
    echo "Activating ${conda_env_name} env"
    source activate ${conda_env_name}

    if [[ "${itex_mode}" == "native" ]]; then
        echo "export ITEX_ONEDNN_GRAPH=0 ..."
        export ITEX_ONEDNN_GRAPH=0
        compiler_path=${HOME}/intel/oneapi/compiler/latest/env/vars.sh
        if [ -f "${compiler_path}" ]; then
            source ${compiler_path}
        fi
        tbb_path=${HOME}/intel/oneapi/tbb/latest/env/vars.sh
        if [ -f "${tbb_path}" ]; then
            source ${tbb_path}
        fi
        mkl_path=${HOME}/intel/oneapi/mkl/latest/env/vars.sh
        if [ -f "${mkl_path}" ]; then
            source ${mkl_path}
        fi
    elif [[ "${itex_mode}" == "onednn_graph" ]]; then
        echo "export ITEX_ONEDNN_GRAPH=1 ..."
        export ITEX_ONEDNN_GRAPH=1
    fi

    tf_version=$(python -c "import tensorflow as tf; print(tf.__version__)")
    echo "tf_version: \"${tf_version}\""
    if [[ "${tf_version}" = "2.5.0" ]]; then
        # default use block format
        export TF_ENABLE_MKL_NATIVE_FORMAT=0
        echo "export TF_ENABLE_MKL_NATIVE_FORMAT=0 ..."
    fi

    if [[ "${tf_version}" = "2.11.0202242" ]]; then
        export TF_ONEDNN_ENABLE_FAST_CONV=1
        export TF_ONEDNN_THREADPOOL_USE_CALLER_THREAD=true
        export TF_ONEDNN_THREAD_PINNING_MODE=none
        echo "export TF_ONEDNN_ENABLE_FAST_CONV=1, TF_ONEDNN_THREADPOOL_USE_CALLER_THREAD=true, TF_ONEDNN_THREAD_PINNING_MODE=none ..."
    fi

    intel_tf=$(pip list | grep 'tensorflow' | grep -c 'intel') || true
    if [[ "${tf_version}" = "2.6.1" ]] || [[ "${tf_version}" = "2.6.2" ]] || [[ "${intel_tf}" = "0" ]]; then
        # default use block format
        echo "export TF_ENABLE_ONEDNN_OPTS=1 ..."
        export TF_ENABLE_ONEDNN_OPTS=1
    fi

}

function set_MXNet_env {
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export OMP_NUM_THREADS=28

    export PATH=${HOME}/miniconda3/bin/:$PATH
    echo "Activating ${conda_env_name} env"
    source activate ${conda_env_name}
}

function set_PT_env {
    export OMP_NUM_THREADS=28
    if [[ "${model}" = "dlrm"* ]]; then
      export PATH=${HOME}/anaconda3/bin/:$PATH
      export https_proxy=http://proxy-prc.intel.com:913
      export http_proxy=http://proxy-prc.intel.com:913
    else
      export PATH=${HOME}/miniconda3/bin/:$PATH
    fi

    echo "Activating ${conda_env_name} env"
    source activate ${conda_env_name}
    if [ "${conda_env_mode}" == "conda" ];then
        pip install opencv-python
    fi
}

function set_ONNXRT_env {
    export KMP_AFFINITY=granularity=fine,noduplicates,compact,1,0
    export OMP_NUM_THREADS=28
    export PATH=${HOME}/miniconda3/bin/:$PATH
    echo "Activating ${conda_env_name} env"
    source activate ${conda_env_name}
    if [ "${conda_env_mode}" == "conda" ];then
        pip install opencv-python
    fi
}

function set_ENGINE_env {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    echo "Activating ${conda_env_name} env"
    source activate ${conda_env_name}
}

function set_environment {
    case "${framework}" in
        tensorflow)
            set_TF_env;;
        mxnet)
            set_MXNet_env;;
        pytorch)
            set_PT_env;;
        onnxrt)
            set_ONNXRT_env;;
        baremetal)
            set_ENGINE_env;;
        engine)
            set_ENGINE_env;;
        ipex)
            set_PT_env;;
        *)
            echo "Framework ${framework} not recognized."; exit 1;;
    esac

    cd ${WORKSPACE}

    if [[ "$install_nlp_toolkit" == "true" ]]; then
        echo "Install nlp-toolkit binary..."
        n=0
        until [ "$n" -ge 5 ]
        do
            [[ $(echo ${WORKSPACE} | grep "304") ]] && [[ -d "/home/linuxbrew/.linuxbrew/bin" ]] && export PATH="/home/linuxbrew/.linuxbrew/bin:"$PATH
            pip install nlpaug
            pip install intel_extension_for_transformers*.whl && break

            n=$((n+1))
            sleep 5
        done
    fi

    if [[ "$install_inc" == "true" ]]; then
        echo "Install neural-compressor binary..."
        n=0
        until [ "$n" -ge 5 ]
        do
            if [ "${conda_env_mode}" == "conda" ];then
                export PATH=$PATH:/usr/lib64/openmpi/bin
                cd ${WORKSPACE}/lpot-models
                lpot_bz2_path="$(find ${WORKSPACE} -name neural-compressor*.tar.* |tail -1)"
                lpot_bz2_file="$(basename ${lpot_bz2_path})"
                lpot_version="$(echo ${lpot_bz2_file} |sed 's/\.tar\..*//' |awk -F '-' '{print $3}')"
                lpot_build="$(echo ${lpot_bz2_file} |sed 's/\.tar\..*//' |awk -F '-' '{print $4}')"
                sed -i "s+LPOT_BZ2_FILE+${lpot_bz2_file}+g" ${WORKSPACE}/lpot-validation/config/conda/noarch/repodata.json
                sed -i "s+LPOT_VERSION+${lpot_version}+g" ${WORKSPACE}/lpot-validation/config/conda/noarch/repodata.json
                sed -i "s+LPOT_BUILD+${lpot_build}+g" ${WORKSPACE}/lpot-validation/config/conda/noarch/repodata.json
                cp ${lpot_bz2_path} ${WORKSPACE}/lpot-validation/config/conda/noarch/
                pip uninstall neural-compressor-full -y || true
                conda install neural-compressor-conda -c file:/${WORKSPACE}/lpot-validation/config/conda -c conda-forge -c intel -y && break
            elif [ "${conda_env_mode}" == "source" ];then
                cd ${WORKSPACE}/lpot-models
                pip install -r requirements.txt
                python setup.py clean || true
                python setup.py install && break
                cd -
            else
                pip install neural_compressor*.whl && break
            fi
            n=$((n+1))
            sleep 5
        done
    fi

    echo "Checking lpot..."
    if [[ "${conda_env_mode}" == "conda" ]]; then
        #python_version=$(python --version | grep -Po [0-9]+.[0-9]+)
        if [[ $(pip list | grep scipy) ]]; then
            pip uninstall scipy -y
            pip install --no-cache scipy    
        fi
        
        if [[ ! $(pip list | grep opencv-python) ]]; then
            pip install opencv-python
        fi
        if [[ ! $(conda list | grep ffmpeg) ]]; then
            conda install ffmpeg -c conda-forge -y
        fi
        export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:${HOME}/miniconda3/bin/
        cp ${HOME}/miniconda3/envs/${conda_env_name}/lib/libopenh264.so.6 ${HOME}/miniconda3/envs/${conda_env_name}/lib/libopenh264.so.5
        conda list --show-channel-urls
    else
        pip list
    fi

    if [[ "${log_level}" != "" ]] && [[ "${log_level}" != "default" ]]; then
        export LOGLEVEL=${log_level}
    fi
}
