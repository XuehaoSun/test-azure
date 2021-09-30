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

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        rm -rf *
        sudo rm -rf *
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

node(node_label) {
    try{
        cleanup()

        dir('lpot-validation') {
            checkout scm
        }

        stage('Copy binary') {
            catchError {
                copyArtifacts(
                        projectName: 'lpot-release-wheel-build',
                        selector: specific("${binary_build_job}"),
                        filter: 'neural_compressor*.whl, neural_compressor*.tar.gz',
                        fingerprintArtifacts: true,
                        target: "${WORKSPACE}/dist")

                archiveArtifacts artifacts: "dist/**", allowEmptyArchive: true
            }
        }

        stage('Upload binary'){
            sh '''#!/bin/bash
            set -xe
            echo "Create conda env..."
            export PATH=${HOME}/miniconda3/bin/:$PATH
            if [ $(conda info -e | grep ${conda_env} | wc -l) != 0 ]; then
                echo "${conda_env} exist!"
            else
                conda create python=3.6.9 -y -n ${conda_env}
            fi
            
            source activate ${conda_env}
            
            # Upgrade pip
            pip install -U pip
            pip install twine
            cp ${WORKSPACE}/lpot-validation/config/.pypirc $HOME
            twine upload --repository testpypi dist/*
            '''
        }

    }catch(e){
        currentBuild.result = "FAILURE"
        throw e
    }

}