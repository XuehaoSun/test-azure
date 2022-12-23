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

binary_build_job_nlp=""
if ('binary_build_job_nlp' in params && params.binary_build_job_nlp != ''){
    binary_build_job_nlp = params.binary_build_job_nlp
}
echo "binary_build_job_nlp is ${binary_build_job_nlp}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

nlp_url="https://github.com/intel/neural-compressor"
if ('nlp_url' in params && params.nlp_url != ''){
    nlp_url = params.nlp_url
}
echo "nlp_url is ${nlp_url}"

nlp_branch = ''
if ('nlp_branch' in params && params.nlp_branch != '') {
    nlp_branch = params.nlp_branch
}
echo "nlp_branch: $nlp_branch"

pypi_version = ''
if ('pypi_version' in params && params.pypi_version != '') {
    pypi_version = params.pypi_version
}
echo "pypi_version: $pypi_version"

python_version = ''
if ('python_version' in params && params.python_version != '') {
    python_version = params.python_version
}
echo "python_version: $python_version"

binary_mode = "full"
if ('binary_mode' in params && params.binary_mode != '') {
    binary_mode = params.binary_mode
}
echo "binary_mode: $binary_mode"

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
        if (binary_build_job_nlp == ""){
            stage('Build binary') {
                List binaryBuildParams = [
                        string(name: "nlp_url", value: "${nlp_url}"),
                        string(name: "nlp_branch", value: "${nlp_branch}"),
                        string(name: "val_branch", value: "${val_branch}"),
                        string(name: "pypi_version", value: "${pypi_version}"),
                        string(name: "python_version", value: "${python_version}"),
                        string(name: "binary_mode", value: "${binary_mode}")
                ]
                downstreamJob = build job: "nlp-toolkit-release-wheel-build", propagate: false, parameters: binaryBuildParams

                binary_build_job_nlp = downstreamJob.getNumber()
                echo "binary_build_job_nlp: ${binary_build_job_nlp}"
                echo "downstreamJob.getResult(): ${downstreamJob.getResult()}"
                if (downstreamJob.getResult() != "SUCCESS") {
                    currentBuild.result = "FAILURE"
                    failed_build_url = downstreamJob.absoluteUrl
                    echo "failed_build_url: ${failed_build_url}"
                    error("---- nlp wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
                }
            }
        }

        stage('Copy binary') {
            catchError {
                copyArtifacts(
                        projectName: 'nlp-toolkit-release-wheel-build',
                        selector: specific("${binary_build_job_nlp}"),
                        filter: 'intel_extension_for_transformers*.whl, intel_extension_for_transformers*.tar.bz2, intel_extension_for_transformers-*.tar.gz',
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
            withEnv(["conda_env=${conda_env}", "python_version=${python_version}"]) {
                sh '''#!/bin/bash
                set -xe
                echo "Create conda env..."
                export PATH=${HOME}/miniconda3/bin/:$PATH
                if [ $(conda info -e | grep ${conda_env} | wc -l) != 0 ]; then
                    echo "${conda_env} exist!"
                else
                    conda create python=3.8 -y -n ${conda_env}
                fi

                source activate ${conda_env}

                # Upgrade pip
                pip install -U pip
                pip install twine
                cp ${WORKSPACE}/lpot-validation/config/.pypinlprc $HOME/.pypirc
                if [[ ${python_version} == "3.8" ]]; then
                    twine upload --repository testpypi dist/*
                else
                    twine upload --repository testpypi dist/intel_extension_for_transformers*.whl
                fi
                '''
            }
        }

    }catch(e){
        currentBuild.result = "FAILURE"
        throw e
    }

}