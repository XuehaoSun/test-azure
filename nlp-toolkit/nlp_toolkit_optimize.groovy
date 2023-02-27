@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'

currentBuild.description = framework + '-' + model

// parameters
// setting node_label
sub_node_label = "lpot"
if ('sub_node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${sub_node_label}"

conda_env_mode = "pypi"
if ('conda_env_mode' in params && params.conda_env_mode != '') {
    conda_env_mode = params.conda_env_mode
}
echo "conda_env_mode ${conda_env_mode}"

// test framework
framework = "pytorch"
if ('framework' in params && params.framework != '') {
    framework = params.framework
}
echo "framework: ${framework}"

// setting framework_version
framework_version  = '1.10.0+cpu'
if ('framework_version' in params && params.framework_version != '') {
    framework_version = params.framework_version
}
echo "framework_version: ${framework_version}"

// setting onnx_version
onnx_version  = '1.9.0'
if ('onnx_version' in params && params.onnx_version != '') {
    onnx_version = params.onnx_version
}
echo "onnx_version: ${onnx_version}"

// model
model = 'resnet50'
if ('model' in params && params.model != '') {
    model = params.model
}
echo "Model: ${model}"

precision = 'int8,fp32'
if ('precision' in params && params.precision != '') {
    precision = params.precision
}
precision_list = parseStrToList(precision)
echo "Precision: ${precision}"

mode = 'accuracy,throughput'
if ('mode' in params && params.mode != '') {
    mode = params.mode
}
mode_list = parseStrToList(mode)
echo "Mode: ${mode}"

nlp_url = "https://github.com/intel-innersource/frameworks.ai.nlp-toolkit.intel-nlp-toolkit.git"
if ('nlp_url' in params && params.nlp_url != '') {
    nlp_url = params.nlp_url
}
echo "nlp_url is ${nlp_url}"

python_version = "3.7"
if ('python_version' in params && params.python_version != '') {
    python_version = params.python_version
}
echo "python_version is ${python_version}"

strategy = "basic"
if ('strategy' in params && params.strategy != '') {
    strategy = params.strategy
}
echo "strategy is ${strategy}"

binary_build_job_nlp = ""
if ('binary_build_job_nlp' in params && params.binary_build_job_nlp != '') {
    binary_build_job_nlp = params.binary_build_job_nlp
}
echo "binary_build_job_nlp is ${binary_build_job_nlp}"

test_mode = "pre-CI"
if ('test_mode' in params && params.test_mode != '') {
    test_mode = params.test_mode
}
echo "test_mode is ${test_mode}"

nlp_branch = ''
MR_source_branch = ''
MR_target_branch = ''
if ('nlp_branch' in params && params.nlp_branch != '') {
    nlp_branch = params.nlp_branch
} else {
    MR_source_branch = params.MR_source_branch
    MR_target_branch = params.MR_target_branch
}
echo "nlp_branch: $nlp_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

timeout = "timeout 10800"
if ('tuning_timeout' in params && params.tuning_timeout != '') {
    tuning_timeout=params.tuning_timeout
    timeout="timeout ${tuning_timeout}"
}
echo "timeout: ${timeout}"

tune_only = false
if (params.tune_only != null) {
    tune_only=params.tune_only
}
echo "tune_only = ${tune_only}"

val_branch = "main"
if ('val_branch' in params && params.val_branch != '') {
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

perf_bs = "1"
if ('perf_bs' in params && params.perf_bs != '') {
    perf_bs = params.perf_bs
}
echo "Performance batch size: ${perf_bs}"

collect_tuned_model = false
if (params.collect_tuned_model != null) {
    collect_tuned_model=params.collect_tuned_model
}
echo "collect_tuned_model = ${collect_tuned_model}"

multi_instance=false
if (params.multi_instance != null){
    multi_instance = params.multi_instance
}
echo "Multi instance: ${multi_instance}"

conda_env_name=''
env_name_list=framework_version.split('=')
if (env_name_list[0] == 'customized'){
    conda_env_name="${framework}-customized-${python_version}"
}else {
    conda_env_name="${framework}-${framework_version}-${python_version}"
}
println("conda_env_name = " + conda_env_name)

cpu="unknown"
os="unknown"
if ('os' in params && params.os != ''){
    os=params.os
}
echo "os: ${os}"

refer_build = "x0"
if ('refer_build' in params && params.refer_build != '') {
    refer_build = params.refer_build
}
echo "Refer build is ${refer_build}"

dataset_prefix=""
if ('dataset_prefix' in params && params.dataset_prefix != ''){
    dataset_prefix=params.dataset_prefix
}
echo "dataset_prefix: ${dataset_prefix}"

log_level="DEBUG"
if ('log_level' in params && params.log_level != ''){
    log_level=params.log_level
}
echo "log_level: ${log_level}"

install_nlp_toolkit="true"
if ('install_nlp_toolkit' in params && params.install_nlp_toolkit != ''){
    install_nlp_toolkit=params.install_nlp_toolkit
}
echo "install_nlp_toolkit: ${install_nlp_toolkit}"

lpot_branch = "master"
if ('lpot_branch' in params && params.lpot_branch) {
    lpot_branch=params.lpot_branch
}

binary_mode = "full"
if ('binary_mode' in params && params.binary_mode != '') {
    binary_mode = params.binary_mode
}
echo "binary_mode: $binary_mode"

lpot_url = "https://github.com/intel/neural-compressor.git"
nightly_cpu_list = ["clx8280-070", "clx8280-071", "clx8280-072", "clx8280-073", "clx8260-136", "clx8260-137", "clx8280-0769"]

workflow = "optimize"
upstreamBuild = ""
upstreamJobName = ""
upstreamUrl = ""

MAX_RERUNS = 3

@NonCPS
def getUpstreamInfo() {
    def upstream_job = currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause)
    if (!upstream_job) {
        return
    }
    println("Found upstream job. Updating info...")
    upstreamJobName = upstream_job.upstreamProject
    upstreamBuild = upstream_job.upstreamBuild
    upstreamUrl = upstream_job.upstreamUrl
}
def cleanup() {

    try {
        sh '''#!/bin/bash 
        set -x
        cd $WORKSPACE
        rm -rf *
        rm -rf .git
        # set perf BKC
        cat /sys/devices/system/cpu/intel_pstate/no_turbo
        lscpu
        cat /proc/sys/kernel/numa_balancing
        '''
    } catch(e) {
        echo "==============================================="
        echo "ERROR: Exception caught in cleanup()           "
        echo "ERROR: ${e}"
        echo "==============================================="
        echo "Error while doing cleanup"
    }  // catch

}

def download() {
    retry(5) {
        if(MR_source_branch != ''){
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${MR_source_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "nlp-models"],
                            [$class: 'CloneOption', timeout: 5],
                            [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${MR_target_branch}"]]
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
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "nlp-models"],
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
}

def parseStrToList(srtingElements, delimiter=',') {
    if (srtingElements == ''){
        return []
    }
    return srtingElements[0..srtingElements.length()-1].tokenize(delimiter)
}

def create_conda_env(tensorflow_version, pytorch_version, onnxruntime_version, install_ipex){

    def cmd = "bash ${WORKSPACE}/lpot-validation/scripts/create_conda_env.sh \
                    --model=\"${model}\" \
                    --python_version=\"${python_version}\" \
                    --tensorflow_version=\"${tensorflow_version}\" \
                    --pytorch_version=\"${pytorch_version}\" \
                    --onnx_version=\"${onnx_version}\" \
                    --onnxruntime_version=\"${onnxruntime_version}\" \
                    --conda_env_name=\"${conda_env_name}\""

    if (install_ipex) {
        cmd += " --install_ipex=\"true\""
    }
    retry(20){
        timeout(60){
            sh """#!/bin/bash
                ${cmd}
            """
        }
        withEnv(["framework=${framework}","conda_env_name=${conda_env_name}","model=${model}","conda_env_mode=${conda_env_mode}","log_level=${log_level}","install_nlp_toolkit=${install_nlp_toolkit}"]) {
            sh '''#!/bin/bash
                    echo -e "\nSetting environment..."
                    source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh --framework=${framework} --model=${model} --conda_env_name=${conda_env_name} --conda_env_mode=${conda_env_mode} --log_level=${log_level} --install_nlp_toolkit=${install_nlp_toolkit} --install_inc="true"
                    set_environment
                '''
        }
    }
}

def runPerfTest(mode, precision) {
    def modelConf =  jsonParse(readFile("$WORKSPACE/nlp-models/examples/.config/${framework}_optimize.json"))."${model}"
    def benchmark_cmd = modelConf."benchmark"."cmd"
    def benchmark_params = modelConf."benchmark"."params"
    def batch_size = 0
    if (perf_bs != "default" && mode != "accuracy") {
        batch_size = perf_bs
    }
    benchmark_params.each{ k, v ->
        if (k == "batch_size" && batch_size != 0){
            v = batch_size
        }
        if (k == "int8"){
            v = (precision == k)
        }
        if (k == "mode"){
            if (mode == "accuracy"){
                v = "accuracy"
            }else{
                v = "benchmark"
            }
        }
        if (k == "cache_dir") {
            if ("${USER_NAME}" == "sdp" || "${USER_NAME}" == "SDP") {
                v = "${HOME}/.cache/huggingface"
            }
        }
        if (k == "dataset_location") {
            v = "${working_dir_fullpath}/data"
        }
        benchmark_cmd += " --${k}=${v}"
    }
    echo "Final cmd is ${benchmark_cmd}"

    withEnv(["framework=${framework}",
             "model=${model}",
             "precision=${precision}",
             "os=${os}",
             "cpu=${cpu}",
             "working_dir=${working_dir_fullpath}",
             "benchmark_cmd=${benchmark_cmd}",
             "conda_env_name=${conda_env_name}",
             "multi_instance=${multi_instance}",
             "mode=${mode}",
             "WORKSPACE=${WORKSPACE}"]) {
        sh '''#!/bin/bash -x
            echo "Running ---- ${framework}, ${model},${precision} ---- Benchmarking"
            echo "=======cache clean======="
            sudo bash ${WORKSPACE}/lpot-validation/scripts/cache_clean.sh
            
            echo "=======run benchmark======="
            [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
            [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
            source activate ${conda_env_name}
            cd ${working_dir}
            echo "working in ${working_dir}"
            logFile="${WORKSPACE}/${framework}-${model}-${precision}-${mode}-${os}-${cpu}"
            #echo ${benchmark_cmd} > ${logFile}_cmd.txt
            
            if [[ ${mode} == "accuracy" ]]; then
                echo "------------ACCURACY BENCHMARK---------"
                ${benchmark_cmd} 2>&1 | tee ${logFile}.log 
                status=$?
                if [ ${status} != 0 ]; then
                    echo "Benchmark process returned non-zero exit code."
                    exit 1
                fi
            else
                echo "-----------PERFORMANCE BENCHMARK-----------"
                ncores_per_socket=${ncores_per_socket:=$( lscpu | grep 'Core(s) per socket' | cut -d: -f2 | xargs echo -n)}
                benchmark_pids=()
                if [ "${multi_instance}" == "false" ]; then
                    ncores_per_instance=$ncores_per_socket
                else
                    ncores_per_instance=4
                fi
                export OMP_NUM_THREADS=${ncores_per_instance}
                
                for((j=0;$j<${ncores_per_socket};j=$(($j + ${ncores_per_instance}))));
                do
                end_core_num=$((j + ncores_per_instance -1))
                if [ ${end_core_num} -ge ${ncores_per_socket} ]; then
                    end_core_num=$((ncores_per_socket-1))
                fi
                numactl -m 0 -C "$j-$end_core_num" \
                    ${benchmark_cmd} 2>&1|tee ${logFile}-${ncores_per_socket}-${ncores_per_instance}-${j}.log &
                    benchmark_pids+=($!)
                done
            
                status="SUCCESS"
                for pid in "${benchmark_pids[@]}"; do
                    wait $pid
                    exit_code=$?
                    echo "Detected exit code: ${exit_code}"
                    if [ ${exit_code} == 0 ]; then
                        echo "Process ${pid} succeeded"
                    else
                        echo "Process ${pid} failed"
                        status="FAILURE"
                    fi
                done
                echo "Benchmark process status: ${status}"
                if [ ${status} == "FAILURE" ]; then
                    echo "Benchmark process returned non-zero exit code."
                    exit 1
                fi
            fi
        '''
    }

}

def getReferenceData() {
    stage("Get reference data") {
        if(refer_build != 'x0') {

            def refer_job_name = "${JOB_NAME}"

            if (test_mode == "extension") {
                refer_job_name="nlp-toolkit-validation-nightly"
            }else if(test_mode == "mr"){
                refer_job_name = "nlp-toolkit-validation-PR"
            } else {
                if (upstreamJobName) {
                    refer_job_name = upstreamJobName
                }
            }
            println("Copying artifacts from ${refer_job_name} job")
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE'){
                copyArtifacts(
                        projectName: refer_job_name,
                        selector: specific("${refer_build}"),
                        filter: 'summary.log,',
                        fingerprintArtifacts: true,
                        target: "reference")
            }
            withEnv(["conda_env_name=${conda_env_name}"]) {
                sh"""#!/bin/bash
                    set -x
                    [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                    [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                    source activate ${conda_env_name}

                    python ${WORKSPACE}/lpot-validation/scripts/parse_summary.py \
                        --summary-file=${WORKSPACE}/reference/summary.log \
                        --output-name=${WORKSPACE}/reference_data.json
                    """
            }
        }
    }
}

def findPerfDrops(result_json, os="", platform="", precision="", mode="") {
    def cmd = "python ${WORKSPACE}/lpot-validation/scripts/compare_results.py \
                --new_result=\"${result_json}\" \
                --reference_data=\"${WORKSPACE}/reference_data.json\" \
                --framework=\"${framework}\" \
                --model=\"${model}\" \
                --os=\"${os}\" \
                --platform=\"${platform}\""
    if ("${precision}" != "") {
        cmd += " --precision=${precision}"
    }

    if ("${mode}" != "") {
        cmd += " --mode=${mode}"
    }
    
    def drops = sh(returnStdout: true, script: """#!/bin/bash
        set -x
        [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
        [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
        source activate ${conda_env_name}
        ${cmd}
        """)

    println(drops)
    if (drops != "") {
        return drops.split(";")
    }
    println("Drops not found.")
    return []
}

def checkReferenceData() {
    stage("Check reference data") {
        def drops = findPerfDrops(
            "${WORKSPACE}/${framework}-${model}-${os}-${cpu}.json",
            "${os}",
            "${cpu}"
        )
        println("Drops: ${drops}")
        println("Drops.size(): ${drops.size()}")
        for (idx = 0; idx < drops.size(); idx++) {
            def drop = drops[idx]
            println("Retrying detected drop: ${drop}")
            def mode = drop.trim().split(",")[0]
            def precision = drop.trim().split(",")[1]
            println("Detected drop on ${mode} mode with ${precision} precision.")
            rerun_num = 0
            while (rerun_num < MAX_RERUNS) {
                rerun_num += 1
                def rerun_path = "${WORKSPACE}/rerun_${mode}_${precision}_${rerun_num}"
                runPerfTest(mode, precision, "${rerun_path}")

                // Copy tuning log to rerun path
                sh """
                    mkdir -p ${rerun_path}
                    cp ${WORKSPACE}/${framework}-${model}-${os}-${cpu}-tune.log ${rerun_path}/
                """

                // Collect logs
                cmd = "python ${WORKSPACE}/lpot-validation/scripts/collect_logs_lpot.py \
                        --framework=\"${framework}\" \
                        --workflow=\"${workflow}\" \
                        --python_version=\"${python_version}\" \
                        --model=\"${model}\" \
                        --logs_dir=\"${rerun_path}\" \
                        --output_dir=\"${rerun_path}\""

                sh """#!/bin/bash
                    set -x
                    [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                    [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                    source activate ${conda_env_name}
                    pip list
                    ${cmd}
                """

                // Check drop
                def mode_drops = findPerfDrops(
                    "${rerun_path}/${framework}-${model}-${os}-${cpu}.json",
                    "${os}",
                    "${cpu}",
                    "${precision}",
                    "${mode}",
                )
                if (mode_drops.size() == 0) {
                    println("Found stable performance for ${mode} ${precision} in ${rerun_path}")  // Need to replace rerun logs to new one and re-collect result
                    sh """
                        # Remove previous summary
                        rm ${WORKSPACE}/${framework}-${model}-${os}-${cpu}.json
                        rm ${WORKSPACE}/summary.log

                        # Remove old logs
                        rm ${WORKSPACE}/${framework}-${model}-${precision}-${mode}-${os}-${cpu}*

                        # Copy logs without drop
                        cp ${rerun_path}/${framework}-${model}-${precision}-${mode}-${os}-${cpu}* ${WORKSPACE}/
                    """
                    collectLogs()
                    break
                }
            }
        }
    }
}

def collectLogs() {
    stage("Collect logs") {
        println("Updating logs prefix..")
        logs_prefix_url = ""
        if (upstreamUrl != "") {
            logs_prefix_url = JENKINS_URL + upstreamUrl + upstreamBuild + "/artifact/optimize/${framework}/${model}/"
        }

        println("Collecting logs...")

        cmd = "python ${WORKSPACE}/lpot-validation/scripts/collect_logs_lpot.py \
        --framework=\"${framework}\" \
        --workflow=\"${workflow}\" \
        --python_version=\"${python_version}\" \
        --model=\"${model}\" \
        --logs_dir=\"${WORKSPACE}\" \
        --output_dir=\"${WORKSPACE}\" \
        --logs_prefix_url=\"${logs_prefix_url}\" \
        --job_url=\"${BUILD_URL}/consoleText\""

        println("--------mode--------->" + mode)
        if (MR_source_branch != "" || mode == "throughput") {
            cmd += " --tune_acc"
        }

        required = "["
        precision_list.each { precision ->
            mode_list.each { mode ->
                required += "{\'precision\': \'${precision}\', \'mode\': \'${mode}\'},"
                }
        }
        required = required.substring(0, required.length() - 1) + "]"
        cmd += " --required=\"${required}\""
        withEnv(["conda_env_name=${conda_env_name}"]) {
            sh """#!/bin/bash
                set -x
                [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                source activate ${conda_env_name}
                pip list
                ${cmd}
            """
        }
        println("Logs collected.")
    }
}

def syncConfigFile(){
    sh '''#!/bin/bash
        set -x
        inc_config_path="${WORKSPACE}/nlp-models/examples/.config"
        ls $inc_config_path
        if [ -d "${inc_config_path}" ]; then
            rm -fr ${WORKSPACE}/lpot-validation/config/
            mkdir -p ${WORKSPACE}/lpot-validation/config
            cp ${inc_config_path}/* ${WORKSPACE}/lpot-validation/config
            ls ${WORKSPACE}/lpot-validation/config
        fi
    '''
}

node( sub_node_label ) {
    // Get CPU name
    if (['unknown','any', '*'].contains(cpu)) {
        cpu = env.CPU_NAME
        echo "Detected cpu: ${cpu}"
        if (cpu == '' && 'cpu' in params && params.cpu != ''){
            cpu=params.cpu
        }
    }
    if (cpu in nightly_cpu_list){
        dataset_prefix="/home2/tensorflow-broad-product/oob"
        echo "run tuning, dataset_prefix: ${dataset_prefix}"
    }

    getUpstreamInfo()
    println("upstreamBuild = ${upstreamBuild}")
    println("upstreamJobName = ${upstreamJobName}")
    println("upstreamUrl = ${upstreamUrl}")

    // clean WORKSPACE
    cleanup()

    try {
        try {
            stage("Download") {
                // download repo
                dir('lpot-validation') {
                    retry(5) {
                        checkout scm
                    }
                }
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
                            string(name: "MR_source_branch", value: "${MR_source_branch}"),
                            string(name: "MR_target_branch", value: "${MR_target_branch}"),
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
                catchError {
                    copyArtifacts(
                            projectName: 'nlp-toolkit-release-wheel-build',
                            selector: specific("${binary_build_job_nlp}"),
                            filter: 'intel_extension_for_transformers-*.whl, intel_extension_for_transformers*.tar.bz2, intel_extension_for_transformers-*.tar.gz',
                            fingerprintArtifacts: true,
                            target: "${WORKSPACE}")
                }
            }

            // sync config json file
            // syncConfigFile() tmp code for test
            sh '''#!/bin/bash
            mkdir -p ${WORKSPACE}/lpot-validation/config && cp ${WORKSPACE}/lpot-validation/nlp-toolkit/config/* ${WORKSPACE}/lpot-validation/config
            ls ${WORKSPACE}/lpot-validation/config
            '''

            // getReferenceData() tmp hide

            stage("Build Conda Env"){
                def install_ipex = false
                if(model.find('ipex')){
                    conda_env_name="pt-ipex-${framework_version}-${python_version}"
                    install_ipex = true
                }
                if ("${CPU_NAME}" != ""){
                    conda_env_name="${conda_env_name}-${CPU_NAME}"
                }

                def tensorflow_version=''
                def pytorch_version=''
                def onnxruntime_version=''
                if (framework=='tensorflow'){
                    tensorflow_version=framework_version
                }else if(framework=='pytorch'){
                    pytorch_version=framework_version
                }else if(framework=='onnxrt'){
                    onnxruntime_version=framework_version
                }
                create_conda_env(tensorflow_version, pytorch_version, onnxruntime_version, install_ipex)

                println("Final conda env name is: $conda_env_name")
            }

            stage("Tuning") {
                echo "Tuning timeout ${timeout}"
                echo "CPU_NAME is ${CPU_NAME}"
                if (cpu in nightly_cpu_list){
                    cpu = cpu.split("-")[0]
                }
                try {
                    // get params for tuning
                    def modelConf =  jsonParse(readFile("$WORKSPACE/nlp-models/examples/.config/${framework}_optimize.json"))."${model}"

                    working_dir = modelConf."working_dir"
                    working_dir_fullpath = "${WORKSPACE}/nlp-models/examples/${working_dir}"

                    tune_cmd = modelConf."tune"."cmd"
                    def tune_params = modelConf."tune"."params"

                    tune_params.each{ k, v ->
                        if (k == "cache_dir") {
                            if ("${USER_NAME}" == "sdp" || "${USER_NAME}" == "SDP") {
                                v = "${HOME}/.cache/huggingface"
                            }
                        }
                        if (k == "dataset_location") {
                            withEnv(["data_path=${v}", "working_dir_fullpath=${working_dir_fullpath}"]){
                                sh '''#!/bin/bash -x
                                    cp -r ${data_path} ${working_dir_fullpath}/data
                                '''
                            }
                            v = "${working_dir_fullpath}/data"
                        }
                        tune_cmd += " --${k}=${v}"
                    }
                    echo "Final cmd is ${tune_cmd}"
                } catch(e) {
                    error("Could not genereate tuning cmd for ${framework} ${model}")
                }

                withEnv([
                        "framework=${framework}",
                        "model=${model}",
                        "timeout=${timeout}",
                        "os=${os}",
                        "cpu=${cpu}",
                        "working_dir=${working_dir_fullpath}",
                        "tune_cmd=${tune_cmd}",
                        "conda_env_name=${conda_env_name}",
                        "WORKSPACE=${WORKSPACE}"]) {
                    sh '''#!/bin/bash -x
                        echo "Running ---- ${framework}, ${model}----Tuning"
                        [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                        [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                        source activate ${conda_env_name}
                        cd ${working_dir}
                        echo "Working in ${working_dir}"
                        echo -e "\nInstalling model requirements..."
                        if [ -f "requirements.txt" ]; then
                            sed -i '/neural-compressor/d' requirements.txt
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
                        ${timeout} ${tune_cmd} 2>&1 | tee ${WORKSPACE}/${framework}-${model}-${os}-${cpu}-tune.log
                    '''
                }
                // Check tuning status
                dir("${WORKSPACE}"){
                    withEnv([
                            "framework=${framework}",
                            "model=${model}",
                            "os=${os}",
                            "cpu=${cpu}"]) {
                        sh '''#!/bin/bash -x
                            control_phrase="model which meet accuracy goal."
                            if [ $(grep "${control_phrase}" ${framework}-${model}-${os}-${cpu}-tune.log | wc -l) == 0 ];then
                                exit 1
                            fi
                            if [ $(grep "${control_phrase}" ${framework}-${model}-${os}-${cpu}-tune.log | grep "Not found" | wc -l) == 1 ];then
                                exit 1
                            fi
                        '''
                    }
                }
            }
            
            if (!tune_only) {
                timeout(720) {
                    stage("Performance") {
                        println("==========run benchmark========")
                        precision_list.each { precision ->
                            mode_list.each { mode ->
                                echo "Run $mode with ${precision}"
                                runPerfTest(mode, precision)
                            }
                        }
                    }
                }
            }
        } catch(e) {
            currentBuild.result = "FAILURE"
            throw e
        } finally {
            collectLogs()
            // checkReferenceData()
        }
    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // save log files
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: "${framework}*.log,${framework}*.json,${framework}-${model}/**,inferencer_summary.log,summary.log,tuning_info.log,reference_data.json", excludes: null
            fingerprint: true
            if (collect_tuned_model){
                archiveArtifacts artifacts: "${framework}-${model}-tune*", excludes: null
                fingerprint: true
            }
        }
    }
    
}
