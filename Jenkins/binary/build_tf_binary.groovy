credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

conda_env = "tf_wheel_build"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running ut on ${conda_env}"

tf_url=""
if ('tf_url' in params && params.tf_url != ''){
    tf_url = params.tf_url
}
echo "tf_url is ${tf_url}"

tf_branch = ''
if ('tf_branch' in params && params.tf_branch != '') {
    tf_branch = params.tf_branch
}
echo "tf_branch: $tf_branch"

val_branch="main"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

python_version="3.7"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

bazel_version="5.0.0"
if ('bazel_version' in params && params.bazel_version != ''){
    bazel_version = params.bazel_version
}
echo "bazel_version is ${bazel_version}"

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
                    branches                         : [[name: "${tf_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "private-tf"],
                            [$class: 'CloneOption', timeout: 60]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${tf_url}"]
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
                # walkaround for deps issue
                pip install tensorflow packaging
                pip uninstall -y tensorflow
                cmake_path=$(which cmake)
                ln -s ${cmake_path} ${cmake_path}3 || true
    
                echo "Build pypi binary..."
                cd private-tf
                yes "" | python configure.py
                bazel build -c opt --config=mkl --cxxopt=-D_GLIBCXX_USE_CXX11_ABI=0 --copt=-O3 --copt=-march=skylake-avx512 --copt=-Wformat --copt=-Wformat-security --copt=-fstack-protector --copt=-fPIC --copt=-fpic --linkopt=-znoexecstack --linkopt=-zrelro --linkopt=-znow --linkopt=-fstack-protector tensorflow/tools/pip_package:build_pip_package                
                bazel-bin/tensorflow/tools/pip_package/build_pip_package dist
                
                echo "Convert binary to manylinux..."
                pip install auditwheel
                auditwheel repair dist/tensorflow*.whl
                cp wheelhouse/tensorflow*.whl ${WORKSPACE}/
            '''
        }
}

node(node_label){
    try{
        cleanup()
        stage("download"){
            download()
        }

        stage("install bazel") {
            echo "+---------------- install bazel ----------------+"
            install_bazel()
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
            archiveArtifacts artifacts: 'tensorflow*.whl', excludes: null
            fingerprint: true
        }
    }
}
