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
if ('lpot_branch' in params && params.lpot_branch != '') {
    lpot_branch = params.lpot_branch

}
echo "lpot_branch: $lpot_branch"

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

test_case_list=""
if ('test_case_list' in params && params.test_case_list != ''){
    test_case_list=params.test_case_list
}
echo "test_case_list: ${test_case_list}"

test_trials=100
if ('test_trials' in params && params.test_trials != ''){
    test_trials=params.test_trials
}
echo "test_trials: ${test_trials}"

log_level='DEFAULT'
if ('log_level' in params && params.log_level != ''){
    log_level=params.log_level
}
echo "log_level: ${log_level}"

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

RUN_COVERAGE=false
if (params.RUN_COVERAGE != null){
    RUN_COVERAGE=params.RUN_COVERAGE
}
echo "RUN_COVERAGE = ${RUN_COVERAGE}"

UT_STRESS_TEST=true
if (params.UT_STRESS_TEST != null){
    UT_STRESS_TEST=params.UT_STRESS_TEST
}
echo "UT_STRESS_TEST = ${UT_STRESS_TEST}"

tf_binary_build_job=""
if ('tf_binary_build_job' in params && params.tf_binary_build_job != ''){
    tf_binary_build_job = params.tf_binary_build_job
}
if (python_version == "3.7"){
    tf_binary_build_job = 100
}else if (python_version == "3.8"){
    tf_binary_build_job = 'lastSuccessfulBuild'
}else if (python_version == "3.9"){
    tf_binary_build_job = 101
}else if (python_version == "3.10"){
    tf_binary_build_job = 102
}

echo "tf_binary_build_job is ${tf_binary_build_job}"

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

def build_conda_env() {
    if ("${CPU_NAME}" != ""){
        conda_env="${conda_env}-${CPU_NAME}"
    }
    println("full conda_env_name = " + conda_env)
    withEnv([
            "pytorch_version=${pytorch_version}",
            "torchvision_version=${torchvision_version}",
            "tensorflow_version=${tensorflow_version}",
            "mxnet_version=${mxnet_version}",
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
                    --python_version="${python_version}" \
                    --tensorflow_version="${tensorflow_version}" \
                    --pytorch_version="${pytorch_version}" \
                    --torchvision_version="${torchvision_version}" \
                    --mxnet_version="${mxnet_version}" \
                    --onnx_version="${onnx_version}" \
                    --onnxruntime_version="${onnxruntime_version}" \
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
        rm -rf /tmp/.neural_compressor/inc_ut/resnet_v2
        for((i=0; i<${#local_file_list[@]}; i++))
        do
            filename=${local_file_list[i]}
            [[ ! -f ${local_path}/${filename} && ! -d ${local_path}/${filename} ]] && continue
            [[ -d ${local_path}/${filename%/*} ]] && mkdir -p ${target_path[i]}${filename%/*} && cp -r ${local_path}/${filename} ${target_path[i]} && continue
            cp -r ${local_path}/${filename} ${target_path[i]}${filename}
        done
    '''
}

def binary_install() {
    withEnv(["conda_env=${conda_env}"]) {
        sh'''#!/bin/bash
            export PATH=${HOME}/miniconda3/bin/:$PATH
            source activate ${conda_env}

            echo "Checking neural_compressor..."
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
                    python -m pip install --no-cache-dir -r requirements.txt && pip install coverage && break
                    n=$((n+1))
                    sleep 5
                done

                pip list
            else
                echo "Not found requirements.txt file."
            fi
        '''
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
            build_conda_env()
            println "now conda env is " + conda_env
            binary_install()
        }

        stage("ut stress test") {
            ut_cases = test_case_list.split(',')
            run_ut_scripts = "${WORKSPACE}/lpot-models/test/run.sh"
            run_tfnewapi_scripts = "${WORKSPACE}/lpot-models/test/run_tfnewapi.sh"
            writeFile file: run_ut_scripts, text: ""
            writeFile file: run_tfnewapi_scripts, text: ""
            ut_cases.each{ ut_case ->
                if (ut_case=~"tfnewapi"){
                    run_ut_context = readFile file: run_tfnewapi_scripts
                    writeFile file: run_tfnewapi_scripts, text: run_ut_context + "python " + ut_case + "\n"
                }else{
                    run_ut_context = readFile file: run_ut_scripts
                    writeFile file: run_ut_scripts, text: run_ut_context + "python " + ut_case + "\n"
                }
            }
            if (UT_STRESS_TEST){
                println("UT_STRESS_TEST...")
                println "when ut stress test, conda env is " + conda_env
                withEnv(["run_ut_scripts=${run_ut_scripts}", "run_tfnewapi_scripts=${run_tfnewapi_scripts}", "test_trials=${test_trials}", "log_level=${log_level}", "conda_env=${conda_env}"]){
                    sh'''#!/bin/bash
                    if [ "${log_level}" != "DEFAULT" ]; then
                      export LOGLEVEL=${log_level}
                    fi
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    source activate ${conda_env}
                    intel_tf=$(pip list | grep 'tensorflow' | grep -c 'intel') || true
                    if [[ "${tensorflow_version}" = "2.6.0" ]] || [[ "${intel_tf}" = "0" ]]; then
                        export TF_ENABLE_ONEDNN_OPTS=1
                        echo "export TF_ENABLE_ONEDNN_OPTS=1 ..."
                    elif [[ "${tensorflow_version}" = "2.5.0" ]]; then
                        # default use block format
                        export TF_ENABLE_MKL_NATIVE_FORMAT=0
                        echo "export TF_ENABLE_MKL_NATIVE_FORMAT=0 ..."
                    fi
                    cd ${WORKSPACE}/lpot-models/test
                    
                    ut_log_name=${WORKSPACE}/unit_test_${test_trials}.log
                    if [ -f "${run_ut_scripts}" ]; then
                        cat ${run_ut_scripts}
                        for((j=0;$j<${test_trials};j=$(($j + 1))));
                        do
                          echo "------ Start of test around ${j} -------" >> ${ut_log_name}
                          bash ${run_ut_scripts} 2>&1 | tee -a ${ut_log_name}
                          echo "\n" >> ${ut_log_name}
                          if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
                            exit 1
                          fi
                        done
                    fi
                    if [ -f "${run_tfnewapi_scripts}" ]; then
                        cat ${run_tfnewapi_scripts}
                        pip install ${WORKSPACE}/tensorflow*.whl
                        for((j=0;$j<${test_trials};j=$(($j + 1))));
                        do
                          echo "------ Start of test around ${j} -------" >> ${ut_log_name}
                          bash ${run_tfnewapi_scripts} 2>&1 | tee -a ${ut_log_name}
                          echo "\n" >> ${ut_log_name}
                          if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
                            exit 1
                          fi
                        done
                    fi
                '''
                }
            }

            if (RUN_COVERAGE){
                println("RUN_COVERAGE...")
                withEnv(["run_ut_scripts=${run_ut_scripts}", "run_tfnewapi_scripts=${run_tfnewapi_scripts}", "log_level=${log_level}", "conda_env=${conda_env}"]){
                    sh'''#!/bin/bash
                        if [ "${log_level}" != "DEFAULT" ]; then
                            export LOGLEVEL=${log_level}
                        fi
                        export PATH=${HOME}/miniconda3/bin/:$PATH
                        source activate ${conda_env}
                        if [[ "${tensorflow_version}" = "2.6.0" ]]; then
                            export TF_ENABLE_ONEDNN_OPTS=1
                            echo "export TF_ENABLE_ONEDNN_OPTS=1 ..."
                        elif [[ "${tensorflow_version}" = "2.5.0" ]]; then
                            # default use block format
                            export TF_ENABLE_MKL_NATIVE_FORMAT=0
                            echo "export TF_ENABLE_MKL_NATIVE_FORMAT=0 ..."
                        fi
                        export COVERAGE_RCFILE=${WORKSPACE}/lpot-validation/.coveragerc
                        cd ${WORKSPACE}/lpot-models/test
                        lpot_path=$(python -c 'import neural_compressor; import os; print(os.path.dirname(neural_compressor.__file__))')
                        
                        coverage erase
                        if [ -f "${run_ut_scripts}" ]; then 
                            sed -i 's,python ,coverage run --source='"${lpot_path}"' --append ,g' ${run_ut_scripts}
                            cat ${run_ut_scripts}
                            bash ${run_ut_scripts} 
                        fi
                        if [ -f "${run_tfnewapi_scripts}" ]; then
                            sed -i 's,python ,coverage run --source='"${lpot_path}"' --append ,g' ${run_tfnewapi_scripts}
                            cat ${run_tfnewapi_scripts}
                            pip install ${WORKSPACE}/tensorflow*.whl
                            bash ${run_tfnewapi_scripts}
                        fi
                        coverage report -m
                        coverage html -d ${WORKSPACE}/coverage_results/htmlcov
                        coverage xml -o ${WORKSPACE}/coverage_results/coverage.xml
                        
                    '''
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
