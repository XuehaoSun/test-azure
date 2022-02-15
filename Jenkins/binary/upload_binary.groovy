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

lpot_url="https://github.com/intel-innersource/frameworks.ai.lpot.intel-lpot"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

lpot_branch = ''
if ('lpot_branch' in params && params.lpot_branch != '') {
    lpot_branch = params.lpot_branch
}
echo "lpot_branch: $lpot_branch"

pypi_version = ''
if ('pypi_version' in params && params.pypi_version != '') {
    pypi_version = params.pypi_version
}
echo "pypi_version: $pypi_version"

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

        stage('Build binary') {

            List binaryBuildParams = [
                    string(name: "lpot_url", value: "${lpot_url}"),
                    string(name: "lpot_branch", value: "${lpot_branch}"),
                    string(name: "val_branch", value: "${val_branch}"),
                    string(name: "pypi_version", value: "${pypi_version}")
            ]
            downstreamJob = build job: "lpot-nightly-release-wheel-build", propagate: false, parameters: binaryBuildParams

            binary_build_job = downstreamJob.getNumber()
            echo "binary_build_job: ${binary_build_job}"
            echo "downstreamJob.getResult(): ${downstreamJob.getResult()}"
            if (downstreamJob.getResult() != "SUCCESS") {
                currentBuild.result = "FAILURE"
                failed_build_url = downstreamJob.absoluteUrl
                echo "failed_build_url: ${failed_build_url}"
                error("---- lpot wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
            }
        }


        stage('Copy binary') {
            catchError {
                copyArtifacts(
                        projectName: 'lpot-nightly-release-wheel-build',
                        selector: specific("${binary_build_job}"),
                        filter: 'neural_compressor*.whl, neural_compressor*.tar.gz',
                        fingerprintArtifacts: true,
                        target: "${WORKSPACE}/dist")

                archiveArtifacts artifacts: "dist/**", allowEmptyArchive: true
            }
        }

        stage('Upload binary'){
            if ("${CPU_NAME}" != ""){
                conda_env="${conda_env}-${CPU_NAME}"
            }
            println("full conda_env_name = " + conda_env)
            withEnv(["conda_env=${conda_env}"]) {
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
        }

    }catch(e){
        currentBuild.result = "FAILURE"
        throw e
    }

}