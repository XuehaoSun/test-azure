node_label = "inteltf-clx6248-306.sh.intel.com"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

conda_env = "model_verify"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running model_verify on ${conda_env}"

model_path = ""
if ('model_path' in params && params.model_path != '') {
    model_path = params.model_path
}
echo "tensorflow oob model path is ${model_path}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

tf_binary_build_job=""
if ('tf_binary_build_job' in params && params.tf_binary_build_job != ''){
    tf_binary_build_job = params.tf_binary_build_job
}
echo "tf_binary_build_job is ${tf_binary_build_job}"

python_version="3.8"
if ('python_version' in params && params.python_version != ''){
    python_version=params.python_version
}
echo "python_version: ${python_version}"

tensorflow_version="2.9.1"
if ('tensorflow_version' in params && params.tensorflow_version != ''){
    tensorflow_version=params.tensorflow_version
}
echo "tensorflow_version: ${tensorflow_version}"

def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        rm -rf *
        rm -rf .git
        sudo rm -rf *
        sudo rm -rf .git
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
        }
    }
}

def build_conda_env() {
    if ("${python_version}" != ""){
        conda_env="${conda_env}-${python_version}"
    }
    println("full conda_env_name = " + conda_env)
    withEnv([
            "tensorflow_version=${tensorflow_version}",
            "conda_env_name=${conda_env}",
            "python_version=${python_version}"]) {
        retry(3) {
            sh'''#!/bin/bash
                set -xe
                echo "Create new conda env for ..."
                if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
                    (conda remove --name ${conda_env_name} --all -y) || true
                fi
                conda_dir=$(dirname $(dirname $(which conda)))
                if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
                    rm -rf ${conda_dir}/envs/${conda_env_name}
                fi
                conda config --add channels defaults
                conda create python=${python_version} -y -n ${conda_env_name}

                source activate ${conda_env_name}

                # Upgrade pip
                pip install -U pip
            '''
        }
    }
}

def binary_install() {
    withEnv(["conda_env=${conda_env}"]) {
        sh'''#!/bin/bash
            export PATH=${HOME}/miniconda3/bin/:$PATH
            source activate ${conda_env}

            echo "Install neural_compressor binary..."
            n=0
            until [ "$n" -ge 5 ]
            do
                pip install neural_compressor*.whl && break
                n=$((n+1))
                sleep 5
            done

            # re-install pycocotools resolve the issue with numpy
            echo "re-install pycocotools resolve the issue with numpy..."
            pip uninstall pycocotools -y
            pip install --no-cache-dir pycocotools
            echo "re-install horovod resolve the issue with fwk..."
            pip uninstall horovod -y
            pip install --no-cache-dir horovod

            echo "Install tensorflow binary..."
            n=0
            until [ "$n" -ge 5 ]
            do
                pip install tensorflow*.whl && break
                n=$((n+1))
                sleep 5
            done
        '''
    }
}

node(node_label){
    try {
        cleanup()
        stage('download') {
            download()
        }

        stage('Copy binary') {
            catchError {
                copyArtifacts(
                        projectName: 'lpot-release-build',
                        selector: specific("${binary_build_job}"),
                        filter: "linux_binaries/wheel/${python_version}/neural_compressor*.whl, linux_binaries/wheel/${python_version}/neural_compressor*.tar.gz, linux_binaries/wheel/${python_version}/neural-compressor*.tar.bz2",
                        fingerprintArtifacts: true,
                        flatten: true,
                        target: "${WORKSPACE}")
                copyArtifacts(
                        projectName: 'TF-spr-base-wheel-build',
                        selector: specific("${tf_binary_build_job}"),
                        filter: 'tensorflow*.whl',
                        fingerprintArtifacts: true,
                        flatten: true,
                        target: "${WORKSPACE}")
            }
        }

        stage('env_build') {
            build_conda_env()
            println "now conda env is " + conda_env
            binary_install()
        }

        stage('model verify'){
            run_model_verify_scripts = "${WORKSPACE}/lpot-validation/tools/scripts/model_verify.py"
            println("run model verify...")
            withEnv(["run_model_verify_scripts=${run_model_verify_scripts}", "conda_env=${conda_env}"]){
                    sh'''#!/bin/bash
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    source activate ${conda_env}

                    mkdir logfile
                    cd ${WORKSPACE}/lpot-validation/tools
                    
                    if [ -f "${run_model_verify_scripts}" ]; then
                        cat ${run_model_verify_scripts}
                        python ${run_model_verify_scripts} --model_path ${model_path} --logfile_path ${WORKSPACE}/logfile
                    fi
                '''
            }
        }
    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: 'logfile/*', excludes: null
            fingerprint: true
        }
    }
}
