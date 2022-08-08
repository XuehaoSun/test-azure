credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

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

conda_env_mode = "pypi"
if ('conda_env_mode' in params && params.conda_env_mode != '') {
    conda_env_mode = params.conda_env_mode
}
echo "Running test on ${conda_env_mode}"

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
    "1.12.0": "0.13.0",
    "1.11.0": "0.12.0",
    "1.10.1": "0.11.2",
    "1.10.0": "0.11.0",
    "1.9.0": "0.10.0",
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

tf_binary_build_job=""
if ('tf_binary_build_job' in params && params.tf_binary_build_job != ''){
    tf_binary_build_job = params.tf_binary_build_job
}
if (python_version == "3.7"){
    tf_binary_build_job = 100
}else if (python_version == "3.8"){
    tf_binary_build_job = 116
}else if (python_version == "3.9"){
    tf_binary_build_job = 101
}else if (python_version == "3.10"){
    tf_binary_build_job = 102
}

echo "tf_binary_build_job is ${tf_binary_build_job}"

lines_coverage_threshold = 80
branches_coverage_threshold = 75


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

            if(MR_source_branch != '') {

                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${MR_target_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models-base"],
                                [$class: 'CloneOption', timeout: 5]
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
                                [$class: 'CloneOption', timeout: 5],
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
                                [$class: 'CloneOption', timeout: 5]
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

def build_conda_env(conda_env_name) {
    withEnv([
        "pytorch_version=${pytorch_version}",
        "torchvision_version=${torchvision_version}",
        "tensorflow_version=${tensorflow_version}",
        "mxnet_version=${mxnet_version}",
        "onnx_version=${onnx_version}",
        "onnxruntime_version=${onnxruntime_version}",
        "conda_env_name=${conda_env_name}",
        "python_version=${python_version}"]) {
        retry(5) {
            sh'''#!/bin/bash
                set -xe
                echo "Create new conda env for UT..."
                bash ${WORKSPACE}/lpot-validation/scripts/create_conda_env.sh \
                    --conda_env_name=${conda_env_name} \
                    --python_version="${python_version}" \
                    --tensorflow_version="${tensorflow_version}" \
                    --pytorch_version="${pytorch_version}" \
                    --torchvision_version="${torchvision_version}" \
                    --mxnet_version="${mxnet_version}" \
                    --onnx_version="${onnx_version}" \
                    --onnxruntime_version="${onnxruntime_version}"
            '''
        }
    }
    // prepare env with local files to avoid network downloading problem
    sh'''#!/bin/bash
        set -xe
        declare local_file_list=("mobilenet_v1_1.0_224.tgz" "slim/inception_v1_2016_08_28.tar.gz" "saved_model.tar.gz" "ssd_resnet50_v1.tgz" "cifar-10-batches-py.tar.gz" "resnet_v2")
        local_path="/home/tensorflow/localfile"
        declare target_path=("/tmp/.neural_compressor/" "/tmp/.neural_compressor/" "/tmp/.neural_compressor/" "/tmp/.neural_compressor/" "/home/tensorflow/.keras/datasets/" "/tmp/.neural_compressor/inc_ut/")  
        mkdir -p /tmp/.neural_compressor/
        mkdir -p /home/tensorflow/.keras/datasets
        for((i=0; i<${#local_file_list[@]}; i++))
        do
            filename=${local_file_list[i]}
            [[ ! -f ${local_path}/${filename} && ! -d ${local_path}/${filename} ]] && continue
            [[ -d ${local_path}/${filename%/*} ]] && mkdir -p ${target_path[i]}${filename%/*} && cp -r ${local_path}/${filename} ${target_path[i]} && continue
            cp -r ${local_path}/${filename} ${target_path[i]}${filename}
        done
    '''
}

def run_coverage_test(is_base=false, MR_branch=""){
    withEnv(["MR_branch=${MR_branch}", "is_base=${is_base}"]){
        timeout(120) {
            withCredentials([string(credentialsId: '2f98cfad-c470-4c49-a85a-43c236507236', variable: 'SIGOPT_TOKEN')]) {
                echo "+---------------- unit test For TF ${tensorflow_version} and PT ${pytorch_version}----------------+"
                ut_status = sh(returnStatus: true, script: '''#!/bin/bash
                export PATH=${HOME}/miniconda3/bin/:$PATH
                if [[ ${is_base} == "true" ]];then
                    source activate ${conda_env}_base
                else
                    source activate ${conda_env}
                fi
                # pip config set global.index-url https://pypi.douban.com/simple/
                echo "Checking lpot..."
                python -V
                pip list
                c_lpot=$(pip list | grep -c 'neural-compressor') || true  # Prevent from exiting when 'lpot' not found
                if [ ${c_lpot} != 0 ]; then
                    pip uninstall neural-compressor-full -y
                    pip list
                fi
                echo "Install neural_compressor binary..."
                n=0
                until [ "$n" -ge 5 ]
                do
                    if [[ ${MR_branch} == "" ]] || [[ ${is_base} == "false" ]];then
                        pip install neural_compressor*.whl && break
                    else
                        cd ${WORKSPACE}/lpot-models-base
                        pip install pandas==1.3.5
                        pip install Cython<=0.29.28
                        pip install ipython==7.32.0
                        pip install threadpoolctl
                        pip install -r requirements.txt
                        python setup.py install
                    fi
                    [[ $(pip list | grep -c 'neural-compressor') ]] && break
                    n=$((n+1))
                    sleep 5
                done
                # re-install pycocotools resolve the issue with numpy
                echo "re-install pycocotools resolve the issue with numpy..."
                pip uninstall pycocotools -y
                pip install --no-cache-dir pycocotools
                if [[ ${is_base} == "true" ]];then
                    target_path=${WORKSPACE}/lpot-models-base
                    export COVERAGE_RCFILE=${WORKSPACE}/lpot-validation/.coveragerc_base
                    cp ${WORKSPACE}/lpot-validation/.coveragerc ${COVERAGE_RCFILE}
                    ut_log_name=${WORKSPACE}/unit_test_base.log
                    coverage_path="coverage_results_base"
                    mkdir -p ${WORKSPACE}/${coverage_path}
                else
                    target_path=${WORKSPACE}/lpot-models
                    export COVERAGE_RCFILE=${WORKSPACE}/lpot-validation/.coveragerc
                    ut_log_name=${WORKSPACE}/ut_tf_${tensorflow_version}_pt_${pytorch_version}.log
                    coverage_path="coverage_results"
                fi
                if [ ! -d ${target_path} ]; then
                    echo "\\"lpot-model\\" not found. Exiting..."
                    exit 1
                fi
                echo -e "\\nInstalling ut requirements..."
                cd ${target_path}/test
                if [ -f "requirements.txt" ]; then
                    sed -i '/^neural-compressor/d' requirements.txt
                    sed -i '/^intel-tensorflow/d' requirements.txt
                    sed -i '/find-links https:\\/\\/download.pytorch.org\\/whl\\/torch_stable.html/d' requirements.txt
                    sed -i '/^torch/d' requirements.txt
                    sed -i '/^mxnet-mkl/d' requirements.txt
                    sed -i '/^onnx>=/d;/^onnx==/d;/^onnxruntime>=/d;/^onnxruntime==/d' requirements.txt
                    n=0
                    until [ "$n" -ge 5 ]
                    do
                        python -m pip install --no-cache-dir -r requirements.txt && pip install coverage && break
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
                intel_tf=$(pip list | grep 'tensorflow' | grep -c 'intel') || true
                if [[ "${tensorflow_version}" = "2.6.0" ]] || [[ "${intel_tf}" = "0" ]]; then
                    export TF_ENABLE_ONEDNN_OPTS=1
                    echo "export TF_ENABLE_ONEDNN_OPTS=1 ..."
                elif [[ "${tensorflow_version}" = "2.5.0" ]]; then
                    # default use block format
                    export TF_ENABLE_MKL_NATIVE_FORMAT=0
                    echo "export TF_ENABLE_MKL_NATIVE_FORMAT=0 ..."
                fi
                lpot_path=$(python -c 'import neural_compressor; import os; print(os.path.dirname(neural_compressor.__file__))')
                find . -name "test*.py" | sed 's,\\.\\/,coverage run --source='"${lpot_path}"' --append ,g' | sed 's/$/ --verbose/'> run.sh
                if [ -d "tfnewapi" ]; then 
                    grep "tfnewapi/" run.sh > run_tfnewapi.sh
                    sed -i '/tfnewapi/d' run.sh
                    echo "cat run_tfnewapi.sh..."
                    cat run_tfnewapi.sh 
                fi
                echo "cat run.sh..."
                cat run.sh 
                coverage erase
                bash run.sh 2>&1 | tee ${ut_log_name}
                if [ -d "tfnewapi" ]; then 
                    echo "Run special UT with TFnewAPI..."
                    pip uninstall intel-tensorflow -y
                    pip install ${WORKSPACE}/tensorflow*.whl
                    echo "-------------"
                    bash run_tfnewapi.sh 2>&1 | tee -a ${ut_log_name}
                fi
                coverage report -m --rcfile=${COVERAGE_RCFILE} | tee -a ${ut_log_name}
                coverage html -d ${WORKSPACE}/${coverage_path}/htmlcov --rcfile=${COVERAGE_RCFILE}
                coverage xml -o ${WORKSPACE}/${coverage_path}/coverage.xml --rcfile=${COVERAGE_RCFILE}
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
                    string(name: "inc_url", value: "${lpot_url}"),
                    string(name: "inc_branch", value: "${lpot_branch}"),
                    string(name: "PR_source_branch", value: "${MR_source_branch}"),
                    string(name: "PR_target_branch", value: "${MR_target_branch}"),
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
                if (tf_binary_build_job == ""){
                    List TFBinaryBuildParams = [
                            string(name: "python_version", value: "${python_version}"),
                            string(name: "val_branch", value: "${val_branch}"),
                    ]
                    downstreamJob = build job: "TF-spr-base-wheel-build", propagate: false, parameters: TFBinaryBuildParams

                    tf_binary_build_job = downstreamJob.getNumber()
                    echo "tf_binary_build_job: ${tf_binary_build_job}"
                    echo "downstreamJob.getResult(): ${downstreamJob.getResult()}"
                    if (downstreamJob.getResult() != "SUCCESS") {
                        currentBuild.result = "FAILURE"
                        failed_build_url = downstreamJob.absoluteUrl
                        echo "failed_build_url: ${failed_build_url}"
                        error("---- lpot wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
                    }
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
            if (MR_source_branch != "" && run_coverage) {
                // Pre-CI
                parallel(
                    "PR-env": {build_conda_env(conda_env)},
                    "base-env": {build_conda_env("${conda_env}_base")}
                )
            } else {
                // nightly, weekly, extention, release
                build_conda_env(conda_env)
            }  
        }

        if (run_coverage){
            stage('unit test') {
                // ut test
                if (MR_source_branch == "") {
                    ut_log_name="${WORKSPACE}/ut_tf_${tensorflow_version}_pt_${pytorch_version}.log"
                    run_coverage_test(false, MR_target_branch)
                } else {
                    ut_log_name="${WORKSPACE}/ut_tf_${tensorflow_version}_pt_${pytorch_version}.log"
                    ut_log_name_base="${WORKSPACE}/ut_tf_${tensorflow_version}_pt_${pytorch_version}.log"
                    parallel(
                        "PR-test": {run_coverage_test(false, MR_source_branch)},
                        "base-test": {run_coverage_test(true, MR_source_branch)}
                    )
                }
                // Coverage status check
                timeout(120) {
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
                        println("Getting coverage on branch \"" + branch + "\"")
                        // Get coverage baselinesummary
                        sh '''#!/bin/bash
                        export PATH=${HOME}/miniconda3/bin/:$PATH
                        source activate ${conda_env}
                        python ${WORKSPACE}/lpot-validation/scripts/get_coverage_summary.py \
                                    --cov-xml=${WORKSPACE}/coverage_results_base/coverage.xml \
                                    --summary-file=${WORKSPACE}/coverage_summary_base.log
                        '''
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
                echo "+---------------- unit test For TF ${tensorflow_version} PT ${pytorch_version} ----------------+"
                withEnv(["ext_version=${tensorflow_version}_${pytorch_version}"]){
                    timeout(120) {
                        withCredentials([string(credentialsId: '2f98cfad-c470-4c49-a85a-43c236507236', variable: 'SIGOPT_TOKEN')]) {
                            ut_status = sh(returnStatus: true, script: '''#!/bin/bash
                            export PATH=${HOME}/miniconda3/bin/:$PATH
                            source activate ${conda_env}
                            echo "Checking neural_compressor..."
                            python -V
                            pip list
                            c_lpot=$(pip list | grep -c 'neural-compressor') || true  # Prevent from exiting when 'neural_compressor' not found
                            if [ ${c_lpot} != 0 ]; then
                                pip uninstall neural-compressor-full -y
                                pip list
                            fi
                            echo "Install neural_compressor binary..."
                            n=0
                            until [ "$n" -ge 5 ]
                            do
                                pip install neural_compressor*.whl && break
                                n=$((n+1))
                                sleep 5
                            done
                            echo "re-install pycocotools resolve the issue with numpy..."
                            pip uninstall pycocotools -y
                            pip install --no-cache-dir pycocotools
                            if [ ! -d ${WORKSPACE}/lpot-models ]; then
                                echo "\\"lpot-model\\" not found. Exiting..."
                                exit 1
                            fi
                            echo -e "\\nInstalling ut requirements..."
                            cd ${WORKSPACE}/lpot-models/test
                            if [ -f "requirements.txt" ]; then
                                sed -i '/^neural-compressor/d' requirements.txt
                                sed -i '/^intel-tensorflow/d' requirements.txt
                                sed -i '/find-links https:\\/\\/download.pytorch.org\\/whl\\/torch_stable.html/d' requirements.txt
                                sed -i '/^torch/d' requirements.txt
                                sed -i '/^mxnet-mkl/d' requirements.txt
                                sed -i '/^onnx>=/d;/^onnx==/d;/^onnxruntime>=/d;/^onnxruntime==/d' requirements.txt
                                n=0
                                until [ "$n" -ge 5 ]
                                do
                                    python -m pip install --no-cache-dir -r requirements.txt && break
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
                            if [[ "${tensorflow_version}" = "2.6.0" ]]; then
                                export TF_ENABLE_ONEDNN_OPTS=1
                                echo "export TF_ENABLE_ONEDNN_OPTS=1 ..."
                            elif [[ "${tensorflow_version}" = "2.5.0" ]]; then
                                # default use block format
                                export TF_ENABLE_MKL_NATIVE_FORMAT=0
                                echo "export TF_ENABLE_MKL_NATIVE_FORMAT=0 ..."
                            fi
                            find . -name "test*.py" | sed 's,\\.\\/,python ,g' | sed 's/$/ --verbose/'  > run.sh
                            ut_log_name=${WORKSPACE}/ut_tf_${tensorflow_version}_pt_${pytorch_version}.log
                            
                            if [ -d "tfnewapi" ]; then
                                grep "tfnewapi/" run.sh > run_tfnewapi.sh
                                sed -i '/tfnewapi/d' run.sh
                                echo "cat run_tfnewapi.sh..."
                                cat run_tfnewapi.sh 
                            fi
                            echo "cat run.sh..."
                            cat run.sh 
                            echo "-------------"
                            bash run.sh 2>&1 | tee ${ut_log_name}
                
                            if [ -d "tfnewapi" ]; then
                                echo "Run special UT with TFnewAPI..."
                                pip uninstall intel-tensorflow -y
                                pip install ${WORKSPACE}/tensorflow*.whl
                                echo "-------------"
                                bash run_tfnewapi.sh 2>&1 | tee -a ${ut_log_name}
                            fi

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
    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log, coverage_status.txt, **/coverage_results/**/*, **/coverage_results_base/**/*', excludes: null
            fingerprint: true
        }
    }
}
