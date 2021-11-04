credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

node_label = "non-perf"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

deepengine_url="git@github.com:intel-innersource/frameworks.ai.lpot.intel-lpot.git"
if ('deepengine_url' in params && params.deepengine_url != ''){
    deepengine_url = params.deepengine_url
}
echo "deepengine_url is ${deepengine_url}"

deepengine_branch = ''
PR_source_branch = ''
PR_target_branch = ''
if ('deepengine_branch' in params && params.deepengine_branch != '') {
    deepengine_branch = params.deepengine_branch

}else{
    PR_source_branch = params.PR_source_branch
    PR_target_branch = params.PR_target_branch
}
echo "deepengine_branch: $deepengine_branch"
echo "PR_source_branch: $PR_source_branch"
echo "PR_target_branch: $PR_target_branch"

conda_env = "deep-engine-ut"
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

python_version = "3.6"
if ('python_version' in params && params.python_version != '') {
    python_version = params.python_version
}
echo "Python version: ${python_version}"


def cleanup() {
    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        rm -rf *
        rm -rf .git
        sudo rm -rf *
        sudo rm -rf .git
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
                            [$class: 'CloneOption', timeout: 10],
                            [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${PR_target_branch}"]]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${deepengine_url}"]
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
                                [$class: 'CloneOption', timeout: 10],
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                 url          : "${deepengine_url}"]
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
                    branches                         : [[name: "${deepengine_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "deep-engine"],
                            [$class: 'CloneOption', timeout: 10]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${deepengine_url}"]
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
        ut_log_name="${WORKSPACE}/unit_test_pytest.log"
        coverage_package='coverage_results'
        coverage_summary_log='coverage_summary.log'
    }
    if (repo_name == 'deep-engine-base'){
        ut_log_name="${WORKSPACE}/unit_test_pytest_base.log"
        coverage_package='coverage_results_base'
        coverage_summary_log='coverage_summary_base.log'
    }
    withEnv(["repo_name=${repo_name}","ut_log_name=${ut_log_name}", "coverage_package=${coverage_package}", "coverage_summary_log=${coverage_summary_log}"]){
        ut_status = sh(returnStatus: true, script: '''#!/bin/bash
        export PATH=${HOME}/miniconda3/bin/:$PATH
        source activate ${conda_env}
        pip install coverage
        
        cd ${WORKSPACE}/${repo_name}/engine/test/pytest
        
        export COVERAGE_RCFILE=${WORKSPACE}/lpot-validation/deep-engine/.coveragerc
        cat ${COVERAGE_RCFILE}
        
        engine_path=$(python -c 'import engine.converter as engine; import os; print(os.path.dirname(engine.__file__))')
        find . -name "test*.py" | sed 's,\\.\\/,coverage run --source='"${engine_path}"' --append ,g' | sed 's/$/ --verbose/'> run.sh
        coverage erase
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
                        string(name: "lpot_url", value: "${deepengine_url}"),
                        string(name: "lpot_branch", value: "${deepengine_branch}"),
                        string(name: "MR_source_branch", value: "${PR_source_branch}"),
                        string(name: "MR_target_branch", value: "${PR_target_branch}"),
                        string(name: "val_branch", value: "${val_branch}")
                ]
                downstreamJob = build job: "lpot-release-wheel-build", propagate: false, parameters: binaryBuildParams

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
                        projectName: 'lpot-release-wheel-build',
                        selector: specific("${binary_build_job}"),
                        filter: 'neural_compressor*.whl',
                        fingerprintArtifacts: true,
                        target: "${WORKSPACE}")
            }
        }

        stage('build env'){
            retry(3){
                sh(returnStatus: true, script: '''#!/bin/bash
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    if [ $(conda info -e | grep ${conda_env} | wc -l) != 0 ]; then
                       conda remove --name ${conda_env} --all -y  
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
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    source activate ${conda_env}
                    cd ${WORKSPACE}
                    pip install neural_compressor*.whl 2>&1 | tee $WORKSPACE/binary_install.log
                    echo "pip list after install neural_compressor..."
                    pip list
                ''')
            }

            if (unit_test_mode=='pytest'){
                retry(3){
                    sh(returnStatus: true, script: '''#!/bin/bash
                        export PATH=${HOME}/miniconda3/bin/:$PATH
                        source activate ${conda_env}
                        
                        if [ ${python_version} == '3.6' ]; then
                            pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp36-cp36m-manylinux2010_x86_64.whl
                        elif [ ${python_version} == '3.7' ]; then
                            pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp37-cp37m-manylinux2010_x86_64.whl
                        elif [ ${python_version} == '3.5' ]; then
                            pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp35-cp35m-manylinux2010_x86_64.whl
                        else
                            echo "!!! TF 1.15UP2 do not support ${python_version}"
                        fi
                        
                        cd ${WORKSPACE}/deep-engine/engine/test/pytest
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

        stage('unit test'){
            timeout(30){
                if (unit_test_mode == 'gtest'){
                    echo "+---------------- gtest ----------------+"
                    ut_status = sh(returnStatus: true, script: '''#!/bin/bash
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    source activate ${conda_env}
                    
                    cd ${WORKSPACE}/deep-engine/engine/test/gtest
                    mkdir build && cd build && cmake .. && make -j 2>&1 | tee -a $WORKSPACE/gtest_cmake_build.log
                    
                    find . -name "test*" > run.sh
                    ut_log_name=$WORKSPACE/unit_test_gtest.log
                    bash run.sh 2>&1 | tee ${ut_log_name}
                    if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "PASSED" ${ut_log_name}) == 0 ];then
                        exit 1
                    fi
                    ''')
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
                        export PATH=${HOME}/miniconda3/bin/:$PATH
                        source activate ${conda_env}
                        echo "Current conda ENV is ${conda_env}..."
                        python ${WORKSPACE}/lpot-validation/scripts/get_coverage_summary.py \
                            --cov-xml=${WORKSPACE}/coverage_results/coverage.xml \
                            --summary-file=${WORKSPACE}/coverage_summary.log
                        '''
                            lines_coverage = Float.parseFloat(sh(
                                    script: "grep 'lines_coverage' ${WORKSPACE}/coverage_summary.log | cut -d ',' -f 4",
                                    returnStdout: true
                            ).trim())
                            println("Lines coverage: " + lines_coverage)

                            branches_coverage = Float.parseFloat(sh(
                                    script: "grep 'branches_coverage' ${WORKSPACE}/coverage_summary.log | cut -d ',' -f 4",
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
                            echo "Writing SUCCESS to file: ${WORKSPACE}/coverage_status.txt"
                            writeFile file: "${WORKSPACE}/coverage_status.txt", text: "coverage_status,SUCCESS"
                            } catch (e) {
                                echo "Writing FAILURE to file: ${WORKSPACE}/coverage_status.txt"
                                writeFile file: "${WORKSPACE}/coverage_status.txt", text: "coverage_status,FAILURE"
                            }
                        }else{
                            echo "+---------------- PR pytest coverage ----------------+"
                            run_pytest_with_coverage_count('deep-engine-base')
                            lines_coverage_base = Float.parseFloat(sh(
                                    script: "grep 'lines_coverage' ${WORKSPACE}/coverage_summary_base.log | cut -d ',' -f 4",
                                    returnStdout: true
                            ).trim())
                            branches_coverage_base = Float.parseFloat(sh(
                                    script: "grep 'branches_coverage' ${WORKSPACE}/coverage_summary_base.log | cut -d ',' -f 4",
                                    returnStdout: true
                            ).trim())
                            try {
                                if (lines_coverage < lines_coverage_base) {
                                    error("Lines coverage decreased!")
                                }

                                if (branches_coverage < branches_coverage_base) {
                                    error("Branches coverage decreased!")
                                }

                                echo "Writing SUCCESS to file: ${WORKSPACE}/coverage_status.txt"
                                writeFile file: "${WORKSPACE}/coverage_status.txt", text: "coverage_status,SUCCESS"
                            } catch (e) {
                                echo "Writing FAILURE to file: ${WORKSPACE}/coverage_status.txt"
                                writeFile file: "${WORKSPACE}/coverage_status.txt", text: "coverage_status,FAILURE"
                            }
                        }

                    }else{
                        echo "+---------------- pytest ----------------+"
                        ut_status = sh(returnStatus: true, script: '''#!/bin/bash
                            export PATH=${HOME}/miniconda3/bin/:$PATH
                            source activate ${conda_env}
                            echo "Current conda ENV is ${conda_env}..."
                            
                            cd ${WORKSPACE}/deep-engine/engine/test/pytest
                            echo "==================run pytest=================="
                            find . -name "test*.py" | sed 's,\\.\\/,python ,g' | sed 's/$/ --verbose/'  > run.sh
                            ut_log_name=$WORKSPACE/unit_test_pytest.log
                            bash run.sh 2>&1 | tee ${ut_log_name}
                            if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
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

    }catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log, coverage_status.txt, **/coverage_results/**/*', excludes: null
            fingerprint: true
        }
    }
}