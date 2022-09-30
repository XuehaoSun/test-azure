credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

conda_env = "pyt_wheel_build"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running ut on ${conda_env}"

pyt_url="https://github.com/intel-innersource/frameworks.ai.pytorch.private-cpu.git"
if ('pyt_url' in params && params.pyt_url != ''){
    pyt_url = params.pyt_url
}
echo "pyt_url is ${pyt_url}"

ipex_url="https://github.com/intel-innersource/frameworks.ai.pytorch.ipex-cpu.git"
if ('ipex_url' in params && params.ipex_url != ''){
    ipex_url = params.ipex_url
}
echo "ipex_url is ${ipex_url}"

torchvision_url="https://github.com/pytorch/vision.git"
if ('torchvision_url' in params && params.torchvision_url != ''){
    torchvision_url = params.torchvision_url
}
echo "torchvision_url is ${torchvision_url}"

pyt_branch = ''
if ('pyt_branch' in params && params.pyt_branch != '') {
    pyt_branch = params.pyt_branch
}
echo "pyt_branch: $pyt_branch"

ipex_branch = ''
if ('ipex_branch' in params && params.ipex_branch != '') {
    ipex_branch = params.ipex_branch
}
echo "ipex_branch: $ipex_branch"

torchvision_branch = ''
if ('torchvision_branch' in params && params.torchvision_branch != '') {
    torchvision_branch = params.torchvision_branch
}
echo "torchvision_branch: $torchvision_branch"

val_branch="main"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

python_version="3.8"
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
        rm -rf /root/.cache/bazel
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

            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${pyt_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "private-pyt"],
                            [$class: 'CloneOption', timeout: 60]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${pyt_url}"]
                    ]
            ]

             checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${ipex_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "private-ipex"],
                            [$class: 'CloneOption', timeout: 60]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${ipex_url}"]
                    ]
            ]

            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${torchvision_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "private-torchvision"],
                            [$class: 'CloneOption', timeout: 60]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${torchvision_url}"]
                    ]
            ]
        }
    }
}

def install_bazel() {
    sh'''#!/bin/bash
        set -xe
        wget https://github.com/bazelbuild/bazel/releases/download/${bazel_version}/bazel-${bazel_version}-installer-linux-x86_64.sh
        chmod +x bazel-${bazel_version}-installer-linux-x86_64.sh
        ./bazel-${bazel_version}-installer-linux-x86_64.sh
    '''
}

def do_binary_build() {
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
                pip install -U pip numpy wheel
                pip install cmake
                pip install sklearn onnx
                pip install lark-parser hypothesis
        
                conda install numpy ninja pyyaml mkl mkl-include setuptools cmake cffi typing_extensions future six requests dataclasses psutil
                export CMAKE_PREFIX_PATH=${CONDA_PREFIX:-"$(dirname $(which conda))/../"}
                export work_space=/home/sdp  
                ls # check directory
                cd private-pyt
                git checkout dev
                git submodule sync
                git submodule update --init --recursive
                echo "Build pypi binary..."
                ls # check directory
                # cd WORKSPACE/pytorch
                python setup.py bdist_wheel
                pip install dist/torch*.whl
                cp dist/torch*.whl ${WORKSPACE}/
                
                echo "Convert binary to manylinux..."
                #pip install auditwheel
                # ls # check directory
                #ls dist # check dist directory
                #auditwheel repair dist/torch*.whl
                #cp wheelhouse/torch*.whl ${WORKSPACE}/
                
                ls # check directory
                cd ../private-ipex
                git checkout cpu-device
                git submodule sync
                git submodule update --init --recursive
                python setup.py bdist_wheel
                pip install dist/intel_extension_for_pytorch*.whl
                cp dist/intel_extension_for_pytorch*.whl ${WORKSPACE}/

                echo "Convert binary to manylinux..."
                #pip install auditwheel
                #ls dist # check dist directory
                #auditwheel repair dist/intel_extension_for_pytorch*.whl
                #cp wheelhouse/intel_extension_for_pytorch*.whl ${WORKSPACE}/

                ls # check directory
                cd ../private-torchvision
                git checkout main
                git submodule sync
                git submodule update --init --recursive
                python setup.py bdist_wheel
                pip install dist/torchvision*.whl
                cp dist/torchvision*.whl ${WORKSPACE}/

                echo "Convert binary to manylinux..."
                #pip install auditwheel
                #ls dist # check dist directory
                #auditwheel repair dist/torchvision*.whl
                #cp wheelhouse/torchvision*.whl ${WORKSPACE}/
                
                pip list
            '''
        }
}

node(node_label){
    try{
        cleanup()
        stage("download"){
            download()
        }

        stage("binary build") {
            echo "+---------------- binary build ----------------+"
            do_binary_build()
        }
    }catch(e){
        currentBuild.result = "FAILURE"
        throw e
    }finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: 'torch*.whl,intel_extension_for_pytorch*.whl,torchvision*.whl', excludes: null
            fingerprint: true
        }
    }
}
