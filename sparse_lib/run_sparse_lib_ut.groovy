credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

node_label = "non-perf"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

lpot_url = "https://github.com/intel-innersource/frameworks.ai.lpot.intel-lpot.git"
nlp_url = "https://github.com/intel-innersource/frameworks.ai.nlp-toolkit.intel-nlp-toolkit.git"
if ('nlp_url' in params && params.nlp_url != ''){
    nlp_url = params.nlp_url
}
echo "nlp_url is ${nlp_url}"

lpot_branch = 'master'
if ('lpot_branch' in params && params.lpot_branch) {
    lpot_branch=params.lpot_branch
}
nlp_branch = ''
PR_source_branch = ''
PR_target_branch = ''
if ('nlp_branch' in params && params.nlp_branch != '') {
    nlp_branch = params.nlp_branch

}else{
    PR_source_branch = params.PR_source_branch
    PR_target_branch = params.PR_target_branch
}
echo "nlp_branch: $nlp_branch"
echo "PR_source_branch: $PR_source_branch"
echo "PR_target_branch: $PR_target_branch"

conda_env = "nlp-engine-ut"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running ut on ${conda_env}"

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

binary_build_job_nlp=""
if ('binary_build_job_nlp' in params && params.binary_build_job_nlp != ''){
    binary_build_job_nlp = params.binary_build_job_nlp
}
echo "binary_build_job_nlp is ${binary_build_job_nlp}"

python_version = "3.7"
if ('python_version' in params && params.python_version != '') {
    python_version = params.python_version
}
echo "Python version: ${python_version}"

tensorflow_version="2.9.1"
if ('tensorflow_version' in params && params.tensorflow_version != '') {
    tensorflow_version = params.tensorflow_version
}
echo "Tensorflow version: ${tensorflow_version}"

def cleanup() {
    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        if [[ "${CPU_NAME}" == "clx8280-07"* ]] || [[ "${CPU_NAME}" == "clx8260-"* ]]; then
            rm -rf *
            rm -rf .git
        else
            sudo rm -rf *
            sudo rm -rf .git
            rm -rf *
            rm -rf .git
        fi
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
    retry(5) {
        if(PR_source_branch != ''){
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${PR_source_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "deep-engine"],
                            [$class: 'CloneOption', timeout: 5],
                            [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${PR_target_branch}"]]
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
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "deep-engine"],
                            [$class: 'CloneOption', timeout: 5]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${nlp_url}"]
                    ]
            ]
        }
    }
    retry(5){
        sh '''#!/bin/bash
            if [ ! -d ${WORKSPACE}/deep-engine ]; then
                echo "\\"deep-engine\\" not found. Exiting..."
                exit 1
            fi
            cd ${WORKSPACE}/deep-engine
            git submodule update --init --recursive
        '''
    }

}

node(node_label){
    try{
        cleanup()
        dir('lpot-validation') {
            checkout scm
        }
        stage('download') {
            download()
        }

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
                def downstreamJob = build job: "lpot-release-build", propagate: false, parameters: binaryBuildParams
                binary_build_job = downstreamJob.getNumber()
                if (downstreamJob.getResult() != "SUCCESS") {
                    currentBuild.result = "FAILURE"
                    failed_build_url = downstreamJob.absoluteUrl
                    error("---- lpot wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
                }
            }
        }
        if ("${binary_build_job_nlp}" == "") {
            stage("build Binary NLP"){
                List binaryBuildParamsNLP = [
                        string(name: "python_version", value: "${python_version}"),
                        string(name: "nlp_url", value: "${nlp_url}"),
                        string(name: "nlp_branch", value: "${nlp_branch}"),
                        string(name: "MR_source_branch", value: "${PR_source_branch}"),
                        string(name: "MR_target_branch", value: "${PR_target_branch}"),
                        string(name: "val_branch", value: "${val_branch}")
                ]
                downstreamJob = build job: "nlp-toolkit-release-wheel-build", propagate: false, parameters: binaryBuildParamsNLP
                binary_build_job_nlp = downstreamJob.getNumber()
                echo "binary_build_job_nlp: ${binary_build_job_nlp}"
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
            catchError {
                    copyArtifacts(
                            projectName: 'nlp-toolkit-release-wheel-build',
                            selector: specific("${binary_build_job_nlp}"),
                            filter: 'intel_extension_for_transformers*.whl, nlp-toolkit-*.tar.bz2, intel_extension_for_transformers-*.tar.gz',
                            fingerprintArtifacts: true,
                            target: "${WORKSPACE}")
            }
        }

        stage('build env'){
            if ("${CPU_NAME}" != ""){
                conda_env="${conda_env}-${CPU_NAME}"
            }
            println("full conda_env_name = " + conda_env)
            withEnv(["conda_env=${conda_env}", "tensorflow_version=${tensorflow_version}"]) {
                retry(3){
                    sh(returnStatus: true, script: '''#!/bin/bash
                        [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                        [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                        export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env}/lib/:$LD_LIBRARY_PATH
                        if [ $(conda info -e | grep ${conda_env} | wc -l) != 0 ]; then
                           (conda remove --name ${conda_env} --all -y) || true
                        fi
                        conda_dir=$(dirname $(dirname $(which conda)))
                        if [ -d ${conda_dir}/envs/${conda_env} ]; then
                            rm -rf ${conda_dir}/envs/${conda_env}
                        fi
                        conda create python=${python_version} -y -n ${conda_env}
                        source activate ${conda_env}
                    ''')
                }
                retry(3) {
                    sh(returnStatus: true, script: '''#!/bin/bash
                        [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                        [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                        export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env}/lib/:$LD_LIBRARY_PATH
                        source activate ${conda_env}
                        cd ${WORKSPACE}
                        pip install nlpaug
                        pip install intel_extension_for_transformers*.whl 2>&1 | tee $WORKSPACE/binary_install.log
                        pip install neural_compressor*.whl 2>&1 | tee -a $WORKSPACE/binary_install.log
                        echo "pip list after install..."
                        pip list
                    ''')
                }
            }
        }

        stage('unit test'){
            withEnv(["conda_env=${conda_env}"]) {
                timeout(30){
                    echo "+---------------- gtest for sparseLib ----------------+"
                    ut_status = sh(returnStatus: true, script: '''#!/bin/bash
                    [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                    [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                    export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env}/lib/:$LD_LIBRARY_PATH
                    source activate ${conda_env}
                    cd ${WORKSPACE}/deep-engine/intel_extension_for_transformers/backends/neural_engine/test/gtest/kernels
                    conda install -c conda-forge gxx gcc sysroot_linux-64 -y
                    pip install cmake
                    echo "SparseLib gtest build..."  2>&1 | tee -a $WORKSPACE/gtest_cmake_build.log 
                    mkdir build && cd build 
                    cmake .. -DSPARSE_LIB_USE_AMX=True
                    make -j 2>&1 | tee -a $WORKSPACE/gtest_cmake_build.log
                    
                    if [ $(find . -maxdepth 1 -name "test*" | wc -l) == 0 ]; then
                        echo "--------build failure---------"
                        exit 1
                    fi
                    find . -maxdepth 1 -name "test*" > run.sh
                    ut_log_name=$WORKSPACE/unit_test_gtest.log
                    echo " ----- SparseLib gtest log ------ " 2>&1 | tee -a ${ut_log_name}
                    
                    bash run.sh 2>&1 | tee -a ${ut_log_name}
                    if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] ||
                        [ $(grep -c "PASSED" ${ut_log_name}) == 0 ] ||
                        [ $(grep -c "Segmentation fault" ${ut_log_name}) != 0 ] ||
                        [ $(grep -c "core dumped" ${ut_log_name}) != 0 ] ||
                        [ $(grep -c "==ERROR:" ${ut_log_name}) != 0 ]; then
                        exit 1
                    fi
                    ''')
                    if (ut_status != 0) {
                        currentBuild.result = 'FAILURE'
                        error("sparse lib test failed!")
                    }
                }

            }
        }

    }catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log', excludes: null
            fingerprint: true
        }
    }
}