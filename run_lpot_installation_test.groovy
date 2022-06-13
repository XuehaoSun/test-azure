credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

node_label = "non-perf"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

lpot_url="https://github.com/intel-innersource/frameworks.ai.lpot.intel-lpot.git"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

python_version="3.6"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"
python_version_list = parseStrToList(python_version)

lpot_branch="developer"
if ('lpot_branch' in params && params.lpot_branch != ''){
    lpot_branch=params.lpot_branch
}
echo "lpot_branch: ${lpot_branch}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

source_install=true
if (params.source_install != null){
    source_install=params.source_install
}
echo "source_install = ${source_install}"

pip_install=true
if (params.pip_install != null){
    pip_install=params.pip_install
}
echo "pip_install = ${pip_install}"

binary_build_job=""
if (params.binary_build_job != null){
    binary_build_job=params.binary_build_job
}
echo "binary_build_job = ${binary_build_job}"

def parseStrToList(srtingElements, delimiter=',') {
    if (srtingElements == ''){
        return []
    }
    return srtingElements[0..srtingElements.length()-1].tokenize(delimiter)
}

def cleanup() {
    stage("Cleanup") {
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
            echo "ERROR: Exception caught in cleanup()"
            echo "ERROR: ${e}"
            echo "==============================================="

            echo ' '
            echo "Error while doing cleanup"
        }
    }
}

def download() {
    stage("Download") {
        dir(WORKSPACE) {
            retry(5) {
                checkout scm
                checkout changelog: true, poll: true, scm: [
                        $class: 'GitSCM',
                        branches: [[name: "${lpot_branch}"]],
                        browser: [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "LPOT"],
                                [$class: 'CloneOption', timeout: 5]
                        ],
                        submoduleCfg: [],
                        userRemoteConfigs: [
                                [credentialsId: "${credential}",
                                 url: "${lpot_url}"]
                        ]
                ]
            }
        }
    }
}

node(node_label) {
    try {
        cleanup()
        download()
        if (source_install){
            stage("Source code build") {
                echo "---------------------------------------------------------"
                echo "-----------------  Source code build  -----------------"
                echo "---------------------------------------------------------"

                dir("$WORKSPACE/LPOT") {
                    python_version_list.each { per_python_version ->

                        withEnv(["python_version=${per_python_version}", "CPU_NAME=${CPU_NAME}"]) {
                            sh '''#!/bin/bash
                            set -x
                            export PATH=${HOME}/miniconda3/bin/:$PATH
                            conda_env_name=lpot-install-${python_version}-test
                            if [[ -n ${CPU_NAME} ]]; then
                                conda_env_name="${conda_env_name}-${CPU_NAME}"
                            fi
                            if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
                                conda remove --name ${conda_env_name} --all -y
                            fi
                        
                            conda_dir=$(dirname $(dirname $(which conda)))
                            if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
                                rm -rf ${conda_dir}/envs/${conda_env_name}
                            fi
                        
                            conda create python=${python_version} -y -n ${conda_env_name}
                            if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
                                source activate ${conda_env_name}
                            else
                                echo "test conda build failed on Python ${python_version} \n" >> ${WORKSPACE}/source_code_build_test.log
                                exit 0
                            fi
                            
                            # Upgrade pip
                            # pip install -U pip
                            python -V
                            echo "-----pip list before requirements.txt install..."
                            pip list
                            pip install -r requirements.txt --no-cache-dir
                            echo "-----pip list after requirements.txt install..."
                            pip list
                            git clean -df
                            python setup.py install 2>&1 | tee ${WORKSPACE}/source_install_${python_version}.log
                            echo "-----pip list after setup.py install..."
                            pip list 
                            cd ${WORKSPACE}
                            lpot_path=$(python -c 'import neural_compressor; import os; print(os.path.dirname(lpot.__file__))')
                            echo "lpot_path:  ${lpot_path}"
                            if [ ${lpot_path} == '' ]; then
                                test_status="failed"
                            else
                                test_status="pass" 
                            fi 
                            echo "test ${test_status} on Python ${python_version} \n" >> ${WORKSPACE}/source_code_build_test.log
                        '''
                        }
                    }
                }
            }
        }
        if (pip_install){
            if ("${binary_build_job}" == "") {
                stage('Build binary') {
                    List binaryBuildParams = [
                        string(name: "inc_url", value: "${lpot_url}"),
                        string(name: "inc_branch", value: "${lpot_branch}"),
                        string(name: "val_branch", value: "${val_branch}"),
                        string(name: "LINUX_BINARY_CLASSES", value: "wheel"),
                        string(name: "LINUX_PYTHON_VERSIONS", value: "${python_version}"),
                        string(name: "WINDOWS_BINARY_CLASSES", value: ""),
                        string(name: "WINDOWS_PYTHON_VERSIONS", value: ""),
                    ]
                    downstreamJob = build job: "lpot-release-build", propagate: false, parameters: binaryBuildParams

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
                }
            }

            stage("pip install") {
                echo "---------------------------------------------------------"
                echo "-----------------  pip install  -----------------"
                echo "---------------------------------------------------------"

                dir("$WORKSPACE") {
                    python_version_list.each { per_python_version ->

                        withEnv(["python_version=${per_python_version}", "CPU_NAME=${CPU_NAME}"]) {
                            sh '''#!/bin/bash
                            set -x
                            export PATH=${HOME}/miniconda3/bin/:$PATH
                            conda_env_name=lpot-install-${python_version}-test
                            if [[ -n ${CPU_NAME} ]]; then
                                conda_env_name="${conda_env_name}-${CPU_NAME}"
                            fi
                            if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
                                conda remove --name ${conda_env_name} --all -y
                            fi
                        
                            conda_dir=$(dirname $(dirname $(which conda)))
                            if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
                                rm -rf ${conda_dir}/envs/${conda_env_name}
                            fi
                        
                            conda create python=${python_version} -y -n ${conda_env_name}
                            if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
                                source activate ${conda_env_name}
                            else
                                echo "test conda build failed on Python ${python_version} \n" >> ${WORKSPACE}/pip_install_test.log
                                exit 0
                            fi
                            
                            pip install neural_compressor*.whl --no-cache-dir 2>&1 | tee ${WORKSPACE}/pip_install_${python_version}.log
                            
                            pip list 
                            lpot_path=$(python -c 'import neural_compressor; import os; print(os.path.dirname(lpot.__file__))')
                            echo "lpot_path:  ${lpot_path}"
                            if [ ${lpot_path} == '' ]; then
                                test_status="failed"
                            else
                                test_status="pass" 
                            fi 
                            echo "test ${test_status} on Python ${python_version} \n" >> ${WORKSPACE}/pip_install_test.log
                        '''
                        }
                    }
                }
            }
        }

    } catch(e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log', excludes: null, allowEmptyArchive: true
            fingerprint: true
        }
    }
}
