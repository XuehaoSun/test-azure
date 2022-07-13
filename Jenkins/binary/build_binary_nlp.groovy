credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

conda_env = "HOSTNAME"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running ut on ${conda_env}"

nlp_url="https://github.com/intel-innersource/frameworks.ai.nlp-toolkit.intel-nlp-toolkit.git"
if ('nlp_url' in params && params.nlp_url != ''){
    nlp_url = params.nlp_url
}
echo "nlp_url is ${nlp_url}"

nlp_branch = ''
MR_source_branch = ''
MR_target_branch = ''
if ('nlp_branch' in params && params.nlp_branch != '') {
    nlp_branch = params.nlp_branch

}else{
    MR_source_branch = params.MR_source_branch
    MR_target_branch = params.MR_target_branch
}
echo "nlp_branch: $nlp_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

binary_class = "wheel"
if ('binary_class' in params && params.binary_class != ''){
    binary_class = params.binary_class
}
echo "binary_class is ${binary_class}"

pypi_version = "default"
if ('pypi_version' in params && params.pypi_version != ''){
    pypi_version = params.pypi_version
}
echo "pypi_version is ${pypi_version}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

python_version="3.7"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        rm -rf *
        rm -rf .git
        git config --global user.email "sys_lpot_val@intel.com"
        git config --global user.name "sys-lpot-val" 
        '''
    } catch(e) {
        echo "==============================================="
        echo "ERROR: Exception caught in cleanup()           "
        echo "ERROR: ${e}"
        echo "==============================================="

        echo ' '
        echo "Error while doing cleanup"
    }

}

def download() {
    dir(WORKSPACE) {
        retry(5) {
            dir('lpot-validation') {
                checkout scm
            }

            if(MR_source_branch != ''){
                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${MR_source_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "nlp_repo"],
                                [$class: 'CloneOption', timeout: 60],
                                [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${MR_target_branch}"]]
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                url          : "${nlp_url}"]
                        ]
                ]
            }
            else {
                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${nlp_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "nlp_repo"],
                                [$class: 'CloneOption', timeout: 60]
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                url          : "${nlp_url}"]
                        ]
                ]
            }
        }

        retry(3){
            sh '''#!/bin/bash
            if [ ! -d ${WORKSPACE}/nlp_repo ]; then
                echo "\\"nlp_repo\\" not found. Exiting..."
                exit 1
            fi
            cd ${WORKSPACE}/nlp_repo
            git submodule update --init --recursive
            '''
        }
    }
}

def do_binary_build() {
    println("full conda_env_name = " + conda_env)
    if (binary_class == 'wheel') {
        withEnv(["pypi_version=${pypi_version}", "conda_env=${conda_env}"]) {
            sh '''#!/bin/bash
                set -xe
                echo "Create conda env..."
                export PATH=${HOME}/miniconda3/bin/:$PATH
                if [ $(conda info -e | grep ${conda_env} | wc -l) != 0 ]; then
                    (conda remove --name ${conda_env} --all -y) || true
                fi
                
                conda_dir=$(dirname $(dirname $(which conda)))
                if [ -d ${conda_dir}/envs/${conda_env} ]; then
                    rm -rf ${conda_dir}/envs/${conda_env}
                fi
                
                conda create python=${python_version} -y -n ${conda_env}
    
                source activate ${conda_env}
    
                # Upgrade pip
                pip install -U pip
                pip install cmake
                cmake_path=$(which cmake)
                ln -s ${cmake_path} ${cmake_path}3 || true
    
                echo "Build Pypi binary..."
                cd nlp_repo
                if [ "${pypi_version}" != "default" ]; then
                    cd nlp_toolkit
                    sed -i '/__version__ =/d' version.py
                    sed -i '$a\\__version__ = \\"'$pypi_version'\\"' version.py
                    cat version.py
                    cd -
                fi
                python3 setup.py sdist bdist_wheel
                pip install auditwheel
                auditwheel repair dist/nlp_toolkit*.whl
                cp wheelhouse/nlp_toolkit*.whl ${WORKSPACE}/
                cp dist/nlp_toolkit*.tar.gz ${WORKSPACE}/
            '''
        }
    } else if (binary_class == 'conda') {
        withEnv(["conda_env=${conda_env}"]) {
            sh '''#!/bin/bash
                set -xe
                echo "Create conda env..."
                export PATH=${HOME}/miniconda3/bin/:$PATH
                if [ $(conda info -e | grep ${conda_env} | wc -l) != 0 ]; then
                    (conda remove --name ${conda_env} --all -y) || true
                fi

                conda_dir=$(dirname $(dirname $(which conda)))
                if [ -d ${conda_dir}/envs/${conda_env} ]; then
                  rm -rf ${conda_dir}/envs/${conda_env}
                fi

                conda create python=${python_version} -y -n ${conda_env}

                source activate ${conda_env}

                # Upgrade pip
                pip install -U pip
                pip install cmake
                cmake_path=$(which cmake)
                ln -s ${cmake_path} ${cmake_path}3 || true

                echo "Build Pypi binary..."
                cd nlp_repo

                python3 setup.py sdist bdist_wheel
                pip install auditwheel
                auditwheel repair dist/nlp_toolkit*.whl
                cp wheelhouse/nlp_toolkit*.whl ${WORKSPACE}/
                cp dist/nlp_toolkit*.tar.gz ${WORKSPACE}/

                echo "Build Conda binary..."
                conda clean -i
                conda_py=$(echo ${python_version} | tr -d '.')
                nc_whl_path=${WORKSPACE}/nlp_repo/wheelhouse/nlp_toolkit*.whl
                export NC_WHL=${nc_whl_path}
                conda install patchelf conda-build conda-verify -y
                conda config --add channels conda-forge
                conda config --add channels fastai
                conda build meta.yaml --python=${conda_py}
                cp ${HOME}/miniconda3/envs/${conda_env}/conda-bld/linux-64/nlp-toolkit-*.tar.bz2 ${WORKSPACE}/
            '''
        }
    } else {
        echo "DO NOT support ${binary_class} build!!!"
    }
}

node(node_label){
    try{
        cleanup()
        stage("download"){
            download()
        }
        stage("binary build") {
            echo "+---------------- ${binary_class} build ----------------+"
            retry(5){
                do_binary_build()
            }
        }
    }catch(e){
        currentBuild.result = "FAILURE"
        throw e
    }finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: 'nlp_toolkit*.whl, nlp-toolkit-*.tar.bz2, nlp_toolkit-*.tar.gz', excludes: null
            fingerprint: true
        }
    }
}
