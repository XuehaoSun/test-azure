credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

node_label = "non-perf"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

lpot_url = "https://github.com/intel/neural-compressor.git"
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

unit_test_mode = "gtest"
if ('unit_test_mode' in params && params.unit_test_mode != '') {
    unit_test_mode = params.unit_test_mode
}
echo "Running ut with ${unit_test_mode}"

run_coverage=false
if (params.run_coverage != null){
    run_coverage=params.run_coverage
}
echo "run_coverage = ${run_coverage}"

lines_coverage_threshold = 60
branches_coverage_threshold = 50

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

torch_version="1.11.0+cpu"
if ('torch_version' in params && params.torch_version != '') {
    torch_version = params.torch_version
}
echo "torch version: ${torch_version}"

binary_mode = "full"
if ('binary_mode' in params && params.binary_mode != '') {
    binary_mode = params.binary_mode
}
echo "binary_mode: $binary_mode"

test_install_backend = false
if (params.test_install_backend != null) {
    test_install_backend=params.test_install_backend
}
echo "test_install_backend is ${test_install_backend}"

set_HF_offline = false
if (params.set_HF_offline != null) {
    set_HF_offline=params.set_HF_offline
}
echo "HF_offline is ${set_HF_offline}"

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

            if (run_coverage){
                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${PR_target_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "deep-engine-base"],
                                [$class: 'CloneOption', timeout: 5],
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                 url          : "${nlp_url}"]
                        ]
                ]
                retry(3){
                    sh '''#!/bin/bash
                        if [ ! -d ${WORKSPACE}/deep-engine-base ]; then
                            echo "\\"deep-engine-base\\" not found. Exiting..."
                            exit 1
                        fi
                        cd ${WORKSPACE}/deep-engine-base
                        git submodule update --init --recursive
                    '''
                }    
            }

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

def run_pytest_with_coverage_count(repo_name){
    if (repo_name == 'deep-engine'){
        ut_log_name="${WORKSPACE}/unit_test_pytest_${python_version}.log"
        if (test_install_backend) {
            ut_log_name="${WORKSPACE}/unit_test_pytest_backend_only_${python_version}.log"
        }
        coverage_package='coverage_results_backend'
        coverage_summary_log='coverage_summary_deploy.log'
    }
    if (repo_name == 'deep-engine-base'){
        ut_log_name="${WORKSPACE}/unit_test_pytest_base_${python_version}.log"
        if (test_install_backend) {
            ut_log_name="${WORKSPACE}/unit_test_pytest_base_backend_only_${python_version}.log"
        }
        coverage_package='coverage_results_base_backend'
        coverage_summary_log='coverage_summary_deploy_base.log'
    }
    withEnv(["torch_version=${torch_version}", "repo_name=${repo_name}","ut_log_name=${ut_log_name}", "coverage_package=${coverage_package}", "coverage_summary_log=${coverage_summary_log}", "conda_env=${conda_env}", "CPU_NAME=${CPU_NAME}"]){
        ut_status = sh(returnStatus: true, script: '''#!/bin/bash
        [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
        [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
        source activate ${conda_env}
        pip install coverage
        
        cd ${WORKSPACE}/${repo_name}/intel_extension_for_transformers/backends/neural_engine/test/pytest
        
        export COVERAGE_RCFILE=${WORKSPACE}/lpot-validation/nlp-toolkit/.coveragerc
        cat ${COVERAGE_RCFILE}
        engine_path=$(python -c 'import intel_extension_for_transformers; import os; print(os.path.dirname(intel_extension_for_transformers.__file__))')
        engine_path="${engine_path}/backends/neural_engine"
        echo "engine path is ${engine_path}"
        find . -name "test*.py" | sed 's,\\.\\/,coverage run --source='"${engine_path}"' --append ,g' | sed 's/$/ --verbose/'> run.sh
        coverage erase
        cat run.sh
        bash run.sh 2>&1 | tee ${ut_log_name}
        coverage report -m --rcfile=${COVERAGE_RCFILE}
        coverage html -d ${WORKSPACE}/${coverage_package}/htmlcov --rcfile=${COVERAGE_RCFILE}
        coverage xml -o ${WORKSPACE}/${coverage_package}/coverage.xml --rcfile=${COVERAGE_RCFILE}

        python ${WORKSPACE}/lpot-validation/scripts/get_coverage_summary.py \
                                --cov-xml=${WORKSPACE}/${coverage_package}/coverage.xml \
                                --summary-file=${WORKSPACE}/${coverage_summary_log}
                                
        if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
            exit 1
        fi
        if [ $(grep -c "core dumped" ${ut_log_name}) != 0 ] || [ $(grep -c "Segmentation fault" ${ut_log_name}) != 0 ];then
            exit 1
        fi
    ''')
    }
    if (ut_status != 0) {
        currentBuild.result = 'FAILURE'
        error("Unit test failed!")
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
                        string(name: "val_branch", value: "${val_branch}"),
                        string(name: "binary_mode", value: "${binary_mode}")
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
            if (binary_mode == "backend" && test_install_backend) {
                catchError {
                    copyArtifacts(
                        projectName: 'nlp-toolkit-release-wheel-build',
                        selector: specific("${binary_build_job_nlp}"),
                        filter: 'intel_extension_for_transformers_backends*.whl, intel_extension_for_transformers-*.tar.gz',
                        fingerprintArtifacts: true,
                        flatten: true,
                        target: "${WORKSPACE}")
                }
            } else {
                catchError {
                    copyArtifacts(
                        projectName: 'nlp-toolkit-release-wheel-build',
                        selector: specific("${binary_build_job_nlp}"),
                        filter: 'intel_extension_for_transformers-*.whl, intel_extension_for_transformers-*.tar.gz',
                        fingerprintArtifacts: true,
                        target: "${WORKSPACE}")
                }
            }
        }

        stage('build env'){
            if ("${CPU_NAME}" != ""){
                conda_env="${conda_env}-${CPU_NAME}"
            }
            println("full conda_env_name = " + conda_env)
            withEnv(["conda_env=${conda_env}", "tensorflow_version=${tensorflow_version}", "CPU_NAME=${CPU_NAME}"]) {
                retry(3){
                    sh(returnStatus: true, script: '''#!/bin/bash
                        [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                        [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                        if [[ ${CPU_NAME} != spr* ]]; then
                            export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env}/lib/:$LD_LIBRARY_PATH
                        else
                            export LD_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu/:$LD_LIBRARY_PATH
                        fi
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
                    withEnv(["test_install_backend=${test_install_backend}", "CPU_NAME=${CPU_NAME}"]){
                        sh(returnStatus: true, script: '''#!/bin/bash
                            [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                            [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                            if [[ ${CPU_NAME} != spr* ]]; then
                                export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env}/lib/:$LD_LIBRARY_PATH
                            else
                                export LD_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu/:$LD_LIBRARY_PATH
                            fi
                            source activate ${conda_env}
                            cd ${WORKSPACE}
                            if [[ ${test_install_backend} == "true" ]]; then
                                pip install intel_extension_for_transformers_backend*.whl 2>&1 | tee $WORKSPACE/binary_install.log
                            else
                                pip install intel_extension_for_transformers-*.whl 2>&1 | tee $WORKSPACE/binary_install.log
                            fi
                            pip install neural_compressor*.whl 2>&1 | tee -a $WORKSPACE/binary_install.log
                            echo "pip list after install..."
                            pip list
                        ''')
                    }
                }

                if (unit_test_mode=='pytest'){
                    retry(3){
                        sh(returnStatus: true, script: '''#!/bin/bash
                            [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                            [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                            source activate ${conda_env}
                            cd ${WORKSPACE}/deep-engine/intel_extension_for_transformers/backends/neural_engine/test/pytest
                            if [ -f "requirements.txt" ]; then
                                pip install -r requirements.txt
                                echo "pip list after install requirements.txt..."
                                pip list
                            else
                                echo "Not found requirements.txt file."
                            fi
                        ''')
                    }
                }
            }
        }

        stage('unit test'){
            withEnv(["conda_env=${conda_env}", "python_version=${python_version}", "test_install_backend=${test_install_backend}", "CPU_NAME=${CPU_NAME}", "set_HF_offline=${set_HF_offline}"]) {
                timeout(60){
                    if (unit_test_mode == 'gtest'){
                        echo "+---------------- gtest ----------------+"
                        def ut_status_engine = sh(returnStatus: true, script: '''#!/bin/bash
                        [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                        [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                        if [[ ${CPU_NAME} != spr* ]]; then
                            export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env}/lib/:$LD_LIBRARY_PATH
                            if [[ $(echo ${WORKSPACE} | grep "304") ]]; then
                                export CC=/usr/local/gcc-9.4/bin/gcc
                                export CXX=/usr/local/gcc-9.4/bin/g++
                            fi
                        else
                            export LD_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu/:$LD_LIBRARY_PATH
                        fi
                        source activate ${conda_env}
                        pip install cmake
                        cmake_path=$(which cmake)
                        ln -s ${cmake_path} ${cmake_path}3 || true
                        cd ${WORKSPACE}/deep-engine/intel_extension_for_transformers/backends/neural_engine
                        mkdir build && cd build && cmake .. -DNE_WITH_SPARSELIB=ON -DNE_WITH_TESTS=ON -DPYTHON_EXECUTABLE=$(which python) && make -j 2>&1 |
                            tee -a $WORKSPACE/gtest_cmake_build.log
                        if [[ ${test_install_backend} == "true" ]]; then
                            ut_log_name=$WORKSPACE/unit_test_gtest_backend_only_${python_version}.log
                        else
                            ut_log_name=$WORKSPACE/unit_test_gtest_${python_version}.log
                        fi
                        ctest -V -L "engine_test" 2>&1 | tee ${ut_log_name}
                        if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] ||
                            [ $(grep -c "PASSED" ${ut_log_name}) == 0 ] ||
                            [ $(grep -c "Segmentation fault" ${ut_log_name}) != 0 ] ||
                            [ $(grep -c "core dumped" ${ut_log_name}) != 0 ] ||
                            [ $(grep -c "==ERROR:" ${ut_log_name}) != 0 ]; then
                            exit 1
                        fi
                        ''')
                        
                        echo "+---------------- gtest for sparseLib ----------------+"
                        def ut_status_kernel = sh(returnStatus: true, script: '''#!/bin/bash
                        [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                        [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                        if [[ ${CPU_NAME} != spr* ]]; then
                            export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env}/lib/:$LD_LIBRARY_PATH
                            if [[ $(echo ${WORKSPACE} | grep "304") ]]; then
                                export CC=/usr/local/gcc-9.4/bin/gcc
                                export CXX=/usr/local/gcc-9.4/bin/g++
                            fi
                        else
                            export LD_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu/:$LD_LIBRARY_PATH
                        fi
                        source activate ${conda_env}
                        cd ${WORKSPACE}/deep-engine/intel_extension_for_transformers/backends/neural_engine/build
                        if [[ ${test_install_backend} == "true" ]]; then
                            ut_log_name=$WORKSPACE/unit_test_gtest_backend_only_${python_version}.log
                        else
                            ut_log_name=$WORKSPACE/unit_test_gtest_${python_version}.log
                        fi
                        echo " ----- SparseLib gtest log ------ " 2>&1 | tee -a ${ut_log_name}
                        
                        ctest -V -L "kernel_test" 2>&1 | tee -a ${ut_log_name}
                        if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] ||
                            [ $(grep -c "PASSED" ${ut_log_name}) == 0 ] ||
                            [ $(grep -c "Segmentation fault" ${ut_log_name}) != 0 ] ||
                            [ $(grep -c "core dumped" ${ut_log_name}) != 0 ] ||
                            [ $(grep -c "==ERROR:" ${ut_log_name}) != 0 ]; then
                            exit 1
                        fi
                        ''')
                        ut_status = ut_status_engine ?: ut_status_kernel
                        if (ut_status != 0) {
                            currentBuild.result = 'FAILURE'
                            error("gtest failed!")
                        }
                    }

                    if (unit_test_mode == 'pytest'){
                        if (run_coverage){
                            echo "+---------------- pytest coverage ----------------+"
                            run_pytest_with_coverage_count('deep-engine')

                            echo "+---------------- pytest coverage status check ----------------+"
                            // Get coverage summary
                            sh '''#!/bin/bash
                            [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                            [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                            export GLOG_minloglevel=2
                            if [[ ${set_HF_offline} != "false" ]]; then
                                export TRANSFORMERS_OFFLINE=1
                            fi
                            source activate ${conda_env}
                            echo "Current conda ENV is ${conda_env}..."
                            python ${WORKSPACE}/lpot-validation/scripts/get_coverage_summary.py \
                                --cov-xml=${WORKSPACE}/coverage_results_backend/coverage.xml \
                                --summary-file=${WORKSPACE}/coverage_summary_deploy.log
                            '''
                            lines_coverage = Float.parseFloat(sh(
                                    script: "grep 'lines_coverage' ${WORKSPACE}/coverage_summary_deploy.log | cut -d ',' -f 4",
                                    returnStdout: true
                            ).trim())
                            println("Lines coverage: " + lines_coverage)
                            branches_coverage = Float.parseFloat(sh(
                                    script: "grep 'branches_coverage' ${WORKSPACE}/coverage_summary_deploy.log | cut -d ',' -f 4",
                                    returnStdout: true
                            ).trim())
                            println("Branches coverage: " + branches_coverage)
                            if (PR_source_branch == ''){
                                echo "+---------------- nightly pytest coverage ----------------+"
                                try {
                                if (lines_coverage < lines_coverage_threshold) {
                                    println("Lines coverage below threshold!")
                                    error("Lines coverage below threshold!")
                                }
                                if (branches_coverage < branches_coverage_threshold) {
                                    println("Branches coverage below threshold!")
                                    error("Branches coverage below threshold!")
                                }
                                echo "Writing SUCCESS to file: ${WORKSPACE}/coverage_status_engine.txt"
                                writeFile file: "${WORKSPACE}/coverage_status_engine.txt", text: "coverage_status_engine,SUCCESS"
                                } catch (e) {
                                    echo "Writing FAILURE to file: ${WORKSPACE}/coverage_status_engine.txt"
                                    writeFile file: "${WORKSPACE}/coverage_status_engine.txt", text: "coverage_status_engine,FAILURE"
                                }
                            }else{
                                echo "+---------------- PR pytest coverage basic ----------------+"
                                sh '''#!/bin/bash
                                    [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                                    [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                                    export GLOG_minloglevel=2
                                    if [[ ${set_HF_offline} != "false" ]]; then
                                        export TRANSFORMERS_OFFLINE=1
                                    fi
                                    source activate ${conda_env}
                                    pip install cmake
                                    cmake_path=$(which cmake)
                                    ln -s ${cmake_path} ${cmake_path}3 || true
                                    pip uninstall intel_extension_for_transformers -y
                                    cd ${WORKSPACE}/deep-engine-base
                                    git submodule update --init --recursive
                                    python setup.py install
                                    pip uninstall neural-compressor -y
                                    pip install ${WORKSPACE}/neural_compressor*.whl 2>&1 | tee -a $WORKSPACE/binary_install.log
                                    pip list
                                '''

                                run_pytest_with_coverage_count('deep-engine-base')
                                lines_coverage_base = Float.parseFloat(sh(
                                        script: "grep 'lines_coverage' ${WORKSPACE}/coverage_summary_deploy_base.log | cut -d ',' -f 4",
                                        returnStdout: true
                                ).trim())
                                branches_coverage_base = Float.parseFloat(sh(
                                        script: "grep 'branches_coverage' ${WORKSPACE}/coverage_summary_deploy_base.log | cut -d ',' -f 4",
                                        returnStdout: true
                                ).trim())
                                try {
                                    if (lines_coverage < lines_coverage_base) {
                                        error("Lines coverage decreased!")
                                    }

                                    if (branches_coverage < branches_coverage_base) {
                                        error("Branches coverage decreased!")
                                    }

                                    echo "Writing SUCCESS to file: ${WORKSPACE}/coverage_status_engine.txt"
                                    writeFile file: "${WORKSPACE}/coverage_status_engine.txt", text: "coverage_status_engine,SUCCESS"
                                } catch (e) {
                                    echo "Writing FAILURE to file: ${WORKSPACE}/coverage_status_engine.txt"
                                    writeFile file: "${WORKSPACE}/coverage_status_engine.txt", text: "coverage_status_engine,FAILURE"
                                }
                            }

                        } else {
                            withEnv(["test_install_backend=${test_install_backend}"]){
                                echo "+---------------- pytest ----------------+"
                                ut_status = sh(returnStatus: true, script: '''#!/bin/bash
                                    [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                                    [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                                    export GLOG_minloglevel=2
                                    if [[ ${set_HF_offline} != "false" ]]; then
                                        export TRANSFORMERS_OFFLINE=1
                                    fi
                                    source activate ${conda_env}
                                    echo "Current conda ENV is ${conda_env}..."

                                    cd ${WORKSPACE}/deep-engine/intel_extension_for_transformers/backends/neural_engine/test/pytest
                                    echo "==================run pytest=================="
                                    find . -name "test*.py" | sed 's,\\.\\/,python ,g' | sed 's/$/ --verbose/'  > run.sh
                                    if [[ ${test_install_backend} == "true" ]]; then
                                        ut_log_name=$WORKSPACE/unit_test_pytest_backend_only_${python_version}.log
                                    else
                                        ut_log_name=$WORKSPACE/unit_test_pytest_${python_version}.log
                                    fi
                                    bash run.sh 2>&1 | tee ${ut_log_name}
                                    if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
                                        exit 1
                                    fi
                                    if [ $(grep -c "core dumped" ${ut_log_name}) != 0 ] || [ $(grep -c "Segmentation fault" ${ut_log_name}) != 0 ];then
                                        exit 1
                                    fi
                                ''')
                                if (ut_status != 0) {
                                    currentBuild.result = 'FAILURE'
                                    error("gtest failed!")
                                }
                            }
                        }
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
            archiveArtifacts artifacts: '*.log, coverage_status_engine.txt, **/coverage_results_backend/**/*, **/coverage_results_base_backend/**/*', excludes: null
            fingerprint: true
        }
    }
}