credential = "lab_tfbot"

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

lpot_url="https://gitlab.devtools.intel.com/chuanqiw/auto-tuning.git"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

lpot_branch = ''
MR_source_branch = ''
MR_target_branch = ''
if ('lpot_branch' in params && params.lpot_branch != '') {
    lpot_branch = params.lpot_branch

}else{
    MR_source_branch = params.MR_source_branch
    MR_target_branch = params.MR_target_branch
}
echo "lpot_branch: $lpot_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

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

// setting tensorflow_version
tensorflow_version = '1.15.2'
if ('tensorflow_version' in params && params.tensorflow_version != '') {
    tensorflow_version = params.tensorflow_version
}
echo "tensorflow_version: ${tensorflow_version}"

// setting mxnet_version
mxnet_version = '1.6.0'
if ('mxnet_version' in params && params.mxnet_version != '') {
    mxnet_version = params.mxnet_version
}
echo "mxnet_version: ${mxnet_version}"

// setting pytorch_version
pytorch_version = '1.5.0+cpu'
if ('pytorch_version' in params && params.pytorch_version != '') {
    pytorch_version = params.pytorch_version
}
echo "pytorch_version: ${pytorch_version}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

run_coverage=true
if (params.run_coverage != null){
    run_coverage=params.run_coverage
}
echo "run_coverage = ${run_coverage}"

torchvision_versions = [
    "1.8.0": "0.9.0",
    "1.7.0": "0.8.0",
    "1.6.0": "0.7.0",
    "1.5.1": "0.6.1",
    "1.5.0": "0.6.0",
    "1.4.0": "0.5.0",
    "1.3.1": "0.4.2",
    "1.3.0": "0.4.1",
    "1.2.0": "0.4.0",
    "1.1.0": "0.3.0",
]

pytorch_version_base = pytorch_version.split('\\+')[0]
try {
    pytorch_version_postfix = pytorch_version.split('\\+')[1]
} catch(e) {
    pytorch_version_postfix = ""
}

torchvision_version = torchvision_versions[pytorch_version_base]

if (!torchvision_version) {
    error("Could not found torchvision for pytorch " + pytorch_version)
}

if (pytorch_version_postfix != "") {
    torchvision_version = torchvision_version + "+" + pytorch_version_postfix
}
println("torchvision_version: " + torchvision_version)


// setting onnx and onnxruntime version
onnx_version = '1.7.0'
if ('onnx_version' in params && params.onnx_version != '') {
    onnx_version = params.onnx_version
}
echo "onnx_version: ${onnx_version}"

onnxruntime_version = '1.5.2'
if ('onnxruntime_version' in params && params.onnxruntime_version != '') {
    onnxruntime_version = params.onnxruntime_version
}
println("onnxruntime_version: " + onnxruntime_version)

lines_coverage_threshold = 80
branches_coverage_threshold = 75


def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        rm -rf *
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
    dir(WORKSPACE) {
        retry(5) {

            dir('lpot-validation') {
                checkout scm
            }

            if(MR_source_branch != '') {

                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${MR_target_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models-base"],
                                [$class: 'CloneOption', timeout: 60]
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                 url          : "${lpot_url}"]
                        ]
                ]

                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${MR_source_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                                [$class: 'CloneOption', timeout: 60],
                                [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${MR_target_branch}"]]
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                url          : "${lpot_url}"]
                        ]
                ]

            }
            else {
                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${lpot_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                                [$class: 'CloneOption', timeout: 60]
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                url          : "${lpot_url}"]
                        ]
                ]
            }
        }
    }
}

def build_conda_env() {
    withEnv([
        "torchvision_version=${torchvision_version}",
        "tensorflow_version=${tensorflow_version}",
        "onnx_version=${onnx_version}",
        "onnxruntime_version=${onnxruntime_version}",
        "conda_env_name=${conda_env}",
        "python_version=${python_version}"]) {
        retry(5) {
            sh'''#!/bin/bash
                set -xe
                echo "Create new conda env for UT..."
                bash ${WORKSPACE}/lpot-validation/scripts/create_conda_env.sh \
                    --conda_env_name=${conda_env_name} \
                    --python_version=${python_version} \
                    --torchvision_version=${torchvision_version} \
                    --tensorflow_version=${tensorflow_version} \
                    --onnxruntime_version=${onnxruntime_version} \
                    --onnx_version=${onnx_version} \
                    --run_ut=true
            '''
        }
    }
}

node(node_label){
    try {
        cleanup()
        stage('download') {
            download()
        }

        if ("${binary_build_job}" == "") {
            stage('Build binary') {
                List binaryBuildParams = [
                        string(name: "lpot_url", value: "${lpot_url}"),
                        string(name: "lpot_branch", value: "${lpot_branch}"),
                        string(name: "MR_source_branch", value: "${MR_source_branch}"),
                        string(name: "MR_target_branch", value: "${MR_target_branch}"),
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
                        filter: 'lpot*.whl',
                        fingerprintArtifacts: true,
                        target: "${WORKSPACE}")

                archiveArtifacts artifacts: "lpot*.whl"
            }
        }
        
        stage('env_build') {
            build_conda_env()
        }

        if (run_coverage){
            stage('unit test') {
                // ut test
                timeout(30) {
                    withCredentials([string(credentialsId: '2f98cfad-c470-4c49-a85a-43c236507236', variable: 'SIGOPT_TOKEN')]) {
                        echo "+---------------- unit test For TF ${tensorflow_version} ----------------+"
                        ut_status = sh(returnStatus: true, script: '''#!/bin/bash
                        export PATH=${HOME}/miniconda3/bin/:$PATH
                        source activate ${conda_env}
                        # pip config set global.index-url https://pypi.douban.com/simple/
                        
                        echo "Checking lpot..."
                        python -V
                        pip list
                        c_lpot=$(pip list | grep -c 'lpot') || true  # Prevent from exiting when 'lpot' not found
                        if [ ${c_lpot} != 0 ]; then
                            pip uninstall lpot -y
                            pip list
                        fi
                                        
                        echo "Install lpot binary..."
                        n=0
                        until [ "$n" -ge 5 ]
                        do
                            pip install lpot*.whl && break
                            n=$((n+1))
                            sleep 5
                        done
                        
                        if [ ! -d ${WORKSPACE}/lpot-models ]; then
                            echo "\\"lpot-model\\" not found. Exiting..."
                            exit 1
                        fi
                        
                        echo -e "\\nInstalling ut requirements..."
                        cd ${WORKSPACE}/lpot-models/test
                        if [ -f "requirements.txt" ]; then
                            sed -i '/^lpot/d' requirements.txt
                            sed -i '/^intel-tensorflow/d' requirements.txt
                            sed -i '/find-links https:\\/\\/download.pytorch.org\\/whl\\/torch_stable.html/d' requirements.txt
                            sed -i '/^torch/d' requirements.txt
                            sed -i '/^mxnet-mkl/d' requirements.txt
                            sed -i '/^onnx/d;/onnxruntime/d' requirements.txt

                            n=0
                            until [ "$n" -ge 5 ]
                            do
                                python -m pip install -r requirements.txt && pip install coverage && break
                                n=$((n+1))
                                sleep 5
                            done

                            pip list
                        else
                            echo "Not found requirements.txt file."
                        fi

                        export COVERAGE_RCFILE=${WORKSPACE}/lpot-validation/.coveragerc
                        cat ${COVERAGE_RCFILE}

                        echo "Setting SigOpt strategy env variables"
                        export SIGOPT_API_TOKEN="${SIGOPT_TOKEN}"
                        export SIGOPT_PROJECT_ID="lpot"
                        if [ ${tensorflow_version} == '2.5.0' ]; then
                            export TF_ENABLE_MKL_NATIVE_FORMAT=0
                        fi

                        lpot_path=$(python -c 'import lpot; import os; print(os.path.dirname(lpot.__file__))')
                        find . -name "test*.py" | sed 's,\\.\\/,coverage run --source='"${lpot_path}"' --append ,g' > run.sh
                        ut_log_name=${WORKSPACE}/unit_test_${tensorflow_version}.log
                        coverage erase
                        bash run.sh 2>&1 | tee ${ut_log_name}
                        coverage report -m --rcfile=${COVERAGE_RCFILE}
                        coverage html -d ${WORKSPACE}/coverage_results/htmlcov --rcfile=${COVERAGE_RCFILE}
                        coverage xml -o ${WORKSPACE}/coverage_results/coverage.xml --rcfile=${COVERAGE_RCFILE}
                        if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
                            exit 1
                        fi
                        ''')
                        if (ut_status != 0) {
                            currentBuild.result = 'FAILURE'
                            error("Unit test failed!")
                        }
                    }
                }
                // Coverage status check
                timeout(30) {
                    branch = lpot_branch
                    if (MR_source_branch != "") {
                        branch = MR_source_branch
                    }
                    println("Getting coverage on branch \"" + branch + "\"")
                    // Get coverage summary
                    sh '''#!/bin/bash
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    source activate ${conda_env}
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

                    if (MR_source_branch == "") {
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
                    } else {
                        println("Getting base coverage on branch \"" + MR_target_branch + "\"")
                        withCredentials([string(credentialsId: '2f98cfad-c470-4c49-a85a-43c236507236', variable: 'SIGOPT_TOKEN')]) {
                            sh '''#!/bin/bash
                            export PATH=${HOME}/miniconda3/bin/:$PATH
                            source activate ${conda_env}

                            pip uninstall lpot -y
                            cd ${WORKSPACE}/lpot-models-base
                            python setup.py install
                            pip list

                            cd ${WORKSPACE}/lpot-models-base/test
                            if [ -f "requirements.txt" ]; then
                                sed -i '/^lpot/d' requirements.txt
                                sed -i '/^intel-tensorflow/d' requirements.txt
                                sed -i '/find-links https:\\/\\/download.pytorch.org\\/whl\\/torch_stable.html/d' requirements.txt
                                sed -i '/^torch/d' requirements.txt
                                sed -i '/^mxnet-mkl/d' requirements.txt
                                sed -i '/^onnx/d;/onnxruntime/d' requirements.txt

                                n=0
                                until [ "$n" -ge 5 ]
                                do
                                python -m pip install -r requirements.txt && break
                                n=$((n+1))
                                sleep 5
                                done

                                pip list
                            else
                                echo "Not found requirements.txt file."
                            fi
                        
                            export COVERAGE_RCFILE=${WORKSPACE}/lpot-validation/.coveragerc
                            cat ${COVERAGE_RCFILE}

                            echo "Setting SigOpt strategy env variables"
                            export SIGOPT_API_TOKEN="${SIGOPT_TOKEN}"
                            export SIGOPT_PROJECT_ID="lpot"
                            if [ ${tensorflow_version} == '2.5.0' ]; then
                                export TF_ENABLE_MKL_NATIVE_FORMAT=0
                            fi

                            lpot_path=$(python -c 'import lpot; import os; print(os.path.dirname(lpot.__file__))')
                            find . -name "test*.py" | sed 's,\\.\\/,coverage run --source='"${lpot_path}"' --append ,g' > run.sh
                            ut_log_name=${WORKSPACE}/unit_test_base.log
                            coverage erase
                            bash run.sh 2>&1 | tee ${ut_log_name}
                            coverage report -m --rcfile=${COVERAGE_RCFILE}
                            coverage xml -o ${WORKSPACE}/coverage_results_base/coverage.xml --rcfile=${COVERAGE_RCFILE}

                            python ${WORKSPACE}/lpot-validation/scripts/get_coverage_summary.py \
                                --cov-xml=${WORKSPACE}/coverage_results_base/coverage.xml \
                                --summary-file=${WORKSPACE}/coverage_summary_base.log

                            '''
                        }
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
                }
            }
        }else {
            stage("unit test") {
                timeout(30) {

                    echo "+---------------- unit test For TF ${tensorflow_version} ----------------+"
                    withEnv(["tensorflow_version=${tensorflow_version}"]){
                        timeout(30) {
                            withCredentials([string(credentialsId: '2f98cfad-c470-4c49-a85a-43c236507236', variable: 'SIGOPT_TOKEN')]) {
                                ut_status = sh(returnStatus: true, script: '''#!/bin/bash
                                export PATH=${HOME}/miniconda3/bin/:$PATH
                                source activate ${conda_env}
                        
                                echo "Checking lpot..."
                                python -V
                                pip list
                                c_lpot=$(pip list | grep -c 'lpot') || true  # Prevent from exiting when 'lpot' not found
                                if [ ${c_lpot} != 0 ]; then
                                    pip uninstall lpot -y
                                    pip list
                                fi
                                        
                                echo "Install lpot binary..."
                                n=0
                                until [ "$n" -ge 5 ]
                                do
                                    pip install lpot*.whl && break
                                    n=$((n+1))
                                    sleep 5
                                done
                        
                                if [ ! -d ${WORKSPACE}/lpot-models ]; then
                                    echo "\\"lpot-model\\" not found. Exiting..."
                                    exit 1
                                fi
                        
                                echo -e "\\nInstalling ut requirements..."
                                cd ${WORKSPACE}/lpot-models/test
                                if [ -f "requirements.txt" ]; then
                                    sed -i '/^lpot/d' requirements.txt
                                    sed -i '/^intel-tensorflow/d' requirements.txt
                                    sed -i '/find-links https:\\/\\/download.pytorch.org\\/whl\\/torch_stable.html/d' requirements.txt
                                    sed -i '/^torch/d' requirements.txt
                                    sed -i '/^mxnet-mkl/d' requirements.txt
                                    sed -i '/^onnx/d;/onnxruntime/d' requirements.txt
            
                                    n=0
                                    until [ "$n" -ge 5 ]
                                    do
                                        python -m pip install -r requirements.txt && break
                                        n=$((n+1))
                                        sleep 5
                                    done
            
                                    pip list
                                else
                                    echo "Not found requirements.txt file."
                                fi

                                echo "Setting SigOpt strategy env variables"
                                export SIGOPT_API_TOKEN="${SIGOPT_TOKEN}"
                                export SIGOPT_PROJECT_ID="lpot"
                                if [ ${tensorflow_version} == '2.5.0' ]; then
                                    export TF_ENABLE_MKL_NATIVE_FORMAT=0
                                fi
                                
                                find . -name "test*.py" | sed 's,\\.\\/,python ,g' > run.sh
                                ut_log_name=${WORKSPACE}/unit_test_${tensorflow_version}.log
                                bash run.sh 2>&1 | tee ${ut_log_name}
                                if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
                                    exit 1
                                fi
                                ''')
                            }
                            if (ut_status != 0) {
                                currentBuild.result = 'FAILURE'
                                error("Unit test extension failed!")
                            }
                        }
                    }
                }
            }
        }

    } catch (e) {
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
