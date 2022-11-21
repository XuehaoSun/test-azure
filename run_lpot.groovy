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
echo "Running test on ${conda_env_mode}"

// test framework
framework = "tensorflow"
if ('framework' in params && params.framework != '') {
    framework = params.framework
}
echo "framework: ${framework}"

// setting tensorflow_version
tensorflow_version = ''
if ('tensorflow_version' in params && params.tensorflow_version != '') {
    tensorflow_version = params.tensorflow_version
}
echo "tensorflow_version: ${tensorflow_version}"

// setting itex_version
itex_version = ''
if ('itex_version' in params && params.itex_version != '') {
    itex_version = params.itex_version
}
echo "itex_version: ${itex_version}"

// setting itex_mode: none,native,onednn_graph
itex_mode = 'none'
if ('itex_mode' in params && params.itex_mode != '') {
    itex_mode = params.itex_mode
}
echo "itex_mode: ${itex_mode}"

// setting pytorch_version
pytorch_version = ''
if ('pytorch_version' in params && params.pytorch_version != '') {
    pytorch_version = params.pytorch_version
}
echo "pytorch_version: ${pytorch_version}"

// setting mxnet_version
mxnet_version = ''
if ('mxnet_version' in params && params.mxnet_version != '') {
    mxnet_version = params.mxnet_version
}
echo "mxnet_version: ${mxnet_version}"

// setting onnxruntime version
onnxruntime_version = ''
if ('onnxruntime_version' in params && params.onnxruntime_version != '') {
    onnxruntime_version = params.onnxruntime_version
}
println("onnxruntime_version: " + onnxruntime_version)

// setting onnx_version
onnx_version  = ''
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

strategy="basic"
if ('strategy' in params && params.strategy != ''){
    strategy = params.strategy
}
echo "strategy is ${strategy}"

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

tf_binary_build_job=""
if ('tf_binary_build_job' in params && params.tf_binary_build_job != ''){
    tf_binary_build_job = params.tf_binary_build_job
}
echo "tf_binary_build_job is ${tf_binary_build_job}"

itex_binary_build_job = ""
if ('itex_binary_build_job' in params && params.itex_binary_build_job != ''){
    itex_binary_build_job=params.itex_binary_build_job
}
echo "itex_binary_build_job: ${itex_binary_build_job}"

pyt_binary_build_job=""  
if ('pyt_binary_build_job' in params && params.pyt_binary_build_job != ''){
    pyt_binary_build_job = params.pyt_binary_build_job
}
echo "pyt_binary_build_job is ${pyt_binary_build_job}"

test_mode="nightly"
if ('test_mode' in params && params.test_mode != ''){
    test_mode = params.test_mode
}
echo "test_mode is ${test_mode}"

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

timeout="timeout 10800"
if ('tuning_timeout' in params && params.tuning_timeout != ''){
    tuning_timeout=params.tuning_timeout
    timeout="timeout ${tuning_timeout}"
}
echo "timeout: ${timeout}"

max_trials=""
if ('max_trials' in params && params.max_trials != ''){
    max_trials=params.max_trials
}
echo "max_trials: ${max_trials}"

tune_only=false
if (params.tune_only != null){
    tune_only=params.tune_only
}
echo "tune_only = ${tune_only}"

RUN_PROFILING=false
if (params.RUN_PROFILING != null){
    RUN_PROFILING=params.RUN_PROFILING
}
echo "RUN_PROFILING = ${RUN_PROFILING}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

collect_tuned_model=false
if (params.collect_tuned_model != null){
    collect_tuned_model=params.collect_tuned_model
}
echo "collect_tuned_model = ${collect_tuned_model}"

inferencer_config="4:64,4:128,24:1"
if ('inferencer_config' in params && params.inferencer_config != ''){
    inferencer_config=params.inferencer_config
}
echo "inferencer_config: ${inferencer_config}"

perf_bs = "1"
if ('perf_bs' in params && params.perf_bs != '') {
    perf_bs = params.perf_bs
}
echo "Performance batch size: ${perf_bs}"

multi_instance=true
if (params.multi_instance != null){
    multi_instance = params.multi_instance
}
echo "Multi instance: ${multi_instance}"

// specify sub node label for pytorch models
if(framework == 'pytorch') {
    if (model == 'dlrm' || model == 'dlrm_fx' || model == 'dlrm_ipex') {
        sub_node_label = 'dlrm'
    }
}

conda_env_name=''
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

dtype=""
if ('dtype' in params && params.dtype != ''){
    dtype=params.dtype
}
echo "dtype: ${dtype}"

nightly_cpu_list = ["clx8280-070", "clx8280-071", "clx8280-072", "clx8280-073", "clx8260-136", "clx8260-137", "clx8280-0769"]
upstreamBuild = ""
upstreamJobName = ""
upstreamUrl = ""
algorithm=""
tf_new_api=""

backend=""
if (itex_mode == "native"){
    backend="tensorflow"
}else if (itex_mode == "onednn_graph"){
    backend="tensorflow_itex"
}

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
        if [[ "${CPU_NAME}" == "clx8280-07"* ]] || [[ "${CPU_NAME}" == "clx8260"* ]]; then
            rm -rf *
            rm -rf .git
            # set perf BKC
            cat /sys/devices/system/cpu/intel_pstate/no_turbo
            lscpu
            cat /proc/sys/kernel/numa_balancing
        else
            rm -rf *
            rm -rf .git
            sudo rm -rf *
            sudo rm -rf .git
            # set perf BKC
            cat /sys/devices/system/cpu/intel_pstate/no_turbo
            lscpu
            sudo cpupower frequency-set -g performance
            cat /proc/sys/kernel/numa_balancing
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
    }  // catch

}

def parseStrToList(srtingElements, delimiter=',') {
    if (srtingElements == ''){
        return []
    }
    return srtingElements[0..srtingElements.length()-1].tokenize(delimiter)
}

def create_conda_env(_tf_ver,_itex_ver,_pt_ver,_mx_ver,_ort_ver,_onnx_ver,install_ipex){

    def cmd = "bash ${WORKSPACE}/lpot-validation/scripts/create_conda_env.sh \
                    --model=\"${model}\" \
                    --python_version=\"${python_version}\" \
                    --tensorflow_version=\"${_tf_ver}\" \
                    --itex_version=\"${_itex_ver}\" \
                    --pytorch_version=\"${_pt_ver}\" \
                    --mxnet_version=\"${_mx_ver}\" \
                    --onnx_version=\"${_onnx_ver}\" \
                    --onnxruntime_version=\"${_ort_ver}\" \
                    --conda_env_name=\"${conda_env_name}\""

    if (install_ipex) {
        cmd += " --install_ipex=\"true\""
    }
    retry(20){
        timeout(10){
            sh """#!/bin/bash
                ${cmd}
            """
        }
    }
}

def runPerfTest(mode, precision, output_path="${WORKSPACE}") {
    def modelConf =  jsonParse(readFile("$WORKSPACE/lpot-validation/config/model_params_${framework}.json"))."${framework}"."${model}"
    def model_src_dir = modelConf."model_src_dir"
    def dataset_location = modelConf."dataset_location"
    def input_model = modelConf."input_model"
    def yaml = modelConf."yaml"
    def new_benchmark = modelConf."new_benchmark"
    def batch_size = modelConf."batch_size"
    if (perf_bs != "default" && mode != "accuracy") {
        batch_size = perf_bs
    }

    if ( MR_source_branch != '' ){
        //PR test will cover different strategies, the other test mode will use the passed strategy
        if (framework == "tensorflow"){
            strategy = "basic"
            if (model_src_dir == "image_recognition"){
                dataset_location = "/tf_dataset/dataset/TF_mini_imagenet"
                println("MR test tensorflow model_src_dir is image_recognition.")
                println("So set dataset_location to /tf_dataset/dataset/TF_mini_imagenet")
            }
            if (model_src_dir == "object_detection"){
                // set mini-coco for obj mr test, set absolute baseline replace relative one to reach the acc goal
                dataset_location = "/tf_dataset/tensorflow/mini-coco-100.record"
                withEnv(["model_src_dir=${model_src_dir}"]) {
                    sh(
                        script: 'sed -i "/relative:/s|relative:.*|absolute: 0.01|g" ${WORKSPACE}/lpot-models/examples/${framework}/${model_src_dir}/ssd_resnet50_v1.yaml',
                        returnStdout: true
                    ).trim()
                }
            }
            if (model == "inception_v1"){
                // set kl test for inception_v1
                algorithm='kl'
            }
            if (model == "resnet50_fashion") {
                dataset_location = "/tf_dataset2/datasets/mnist/FashionMNIST_small"
            }
        }else if(framework == "pytorch") {
            if (model == "resnet18") {
                strategy = "bayesian"
            }
            if (model_src_dir.contains("image_recognition/imagenet/cpu/ptq")) {
                dataset_location = "/tf_dataset2/datasets/mini-imageraw"
            }
        }else if(framework == "mxnet" && model == "resnet50v1"){
            strategy = "mse"
        } else if (framework == "onnxrt" && model == "resnet50-v1-12") {
            strategy = "basic"
            dataset_location = "/tf_dataset2/datasets/imagenet/ImagenetRaw/ImagenetRaw_small_5000/ILSVRC2012_img_val"
        }else{
            strategy = "basic"
        }

        // set timeout for PR test
        timeout="timeout 5400"
    }

    // set model_src_dir for PT oob models
    if (framework=='pytorch' && (model_src_dir=~'oob_models').find()){
        model_src_dir="${WORKSPACE}/lpot-validation/examples/${framework}/${model_src_dir}"
    }else{
        model_src_dir="${WORKSPACE}/lpot-models/examples/${framework}/${model_src_dir}"
    }
    if (new_benchmark == true) {
        def cmd = "python ${WORKSPACE}/lpot-validation/scripts/run_new_benchmark_trigger.py \
                --framework=${framework} \
                --model=${model} \
                --model_src_dir=${model_src_dir} \
                --input_model=${dataset_prefix}${input_model} \
                --precision=${precision} \
                --mode=${mode} \
                --batch_size=${batch_size} \
                --yaml=${yaml} \
                --cpu=${cpu} \
                --output_path=${output_path} \
                --dataset_location=${dataset_prefix}${dataset_location}"
        if (multi_instance) {
            cmd += " --multi_instance"
        }

        withCredentials([string(credentialsId: '2f98cfad-c470-4c49-a85a-43c236507236', variable: 'SIGOPT_TOKEN')]) {
            sh """#!/bin/bash -x
                echo "Running ---- ${framework}, ${model},${precision},${mode} ---- Benchmarking - New"
                
                echo "-------w-------"
                w
                echo "-------w-------"

                echo "=======cache clean======="
                sudo bash ${WORKSPACE}/lpot-validation/scripts/cache_clean.sh
                echo "========================="

            
                echo "======= Activate conda env ======="
                source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh \
                    --framework=${framework} \
                    --model=${model} \
                    --conda_env_name=${conda_env_name} \
                    --conda_env_mode=${conda_env_mode} \
                    --log_level=${log_level} \
                    --itex_mode=${itex_mode}
                set_environment
                echo "=================================="

                export PYTHONPATH=${WORKSPACE}/lpot-models:\$PYTHONPATH

                ${cmd}
                """
        }
    } else {
        withCredentials([string(credentialsId: '2f98cfad-c470-4c49-a85a-43c236507236', variable: 'SIGOPT_TOKEN')]) {
            sh """#!/bin/bash -x
                echo "Running ---- ${framework}, ${model},${precision},${mode} ---- Benchmarking"
                
                echo "-------w-------"
                w
                echo "-------w-------"
                echo "=======cache clean======="
                
                sudo bash ${WORKSPACE}/lpot-validation/scripts/cache_clean.sh

                echo "=======cache clean======="
                bash ${WORKSPACE}/lpot-validation/scripts/run_benchmark_trigger.sh \
                    --framework=${framework} \
                    --model=${model} \
                    --model_src_dir=${model_src_dir} \
                    --dataset_location=${dataset_prefix}${dataset_location} \
                    --input_model=${dataset_prefix}${input_model} \
                    --precision=${precision} \
                    --mode=${mode} \
                    --batch_size=${batch_size} \
                    --multi_instance=${multi_instance} \
                    --conda_env_name=${conda_env_name} \
                    --conda_env_mode=${conda_env_mode} \
                    --yaml=${yaml} \
                    --os=${os} \
                    --cpu=${cpu} \
                    --profiling=${RUN_PROFILING} \
                    --output_path=${output_path} \
                    --log_level=${log_level}
                """
        }
    }
}

def getReferenceData() {
    stage("Get reference data") {
        if(refer_build != 'x0') {

            def refer_job_name = "${JOB_NAME}"

            if (test_mode == "extension") {
                if (framework=="baremetal"){
                    refer_job_name="intel-deep-engine-validation-top-nightly"
                }else{
                    refer_job_name="intel-lpot-validation-top-weekly"
                }
            }else if(test_mode == "mr" && framework != "baremetal" && tf_new_api != "true"){
                refer_job_name = "intel-lpot-validation-top-PR"
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
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    if [[ ${framework} = 'pytorch' ]] && [[ ${model} = "dlrm"* ]]; then
                        export PATH=${HOME}/anaconda3/bin/:$PATH
                    fi
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
        export PATH=${HOME}/miniconda3/bin/:$PATH
        if [[ ${framework} = 'pytorch' ]] && [[ ${model} = "dlrm"* ]]; then
            export PATH=${HOME}/anaconda3/bin/:$PATH
        fi
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
                        --python_version=\"${python_version}\" \
                        --model=\"${model}\" \
                        --logs_dir=\"${rerun_path}\" \
                        --output_dir=\"${rerun_path}\""

                sh """#!/bin/bash
                    set -x
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    if [[ ${framework} = 'pytorch' ]] && [[ ${model} = "dlrm"* ]]; then
                        export PATH=${HOME}/anaconda3/bin/:$PATH
                    fi
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
            logs_prefix_url = JENKINS_URL + upstreamUrl + upstreamBuild + "/artifact/${os}/${framework}/${model}/"
        }

        println("Collecting logs...")

        cmd = "python ${WORKSPACE}/lpot-validation/scripts/collect_logs_lpot.py \
        --framework=\"${framework}\" \
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

        if (tune_only){
            required = []
        }

        cmd += " --required=\"${required}\""
        withEnv(["conda_env_name=${conda_env_name}"]) {
            sh """#!/bin/bash
                set -x
                export PATH=${HOME}/miniconda3/bin/:$PATH
                if [[ ${framework} = 'pytorch' ]] && [[ ${model} = "dlrm"* ]]; then
                    export PATH=${HOME}/anaconda3/bin/:$PATH
                fi
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
        inc_config_path="${WORKSPACE}/lpot-models/examples/.config"
        if [ -d "${inc_config_path}" ]; then
            cp ${inc_config_path}/* ${WORKSPACE}/lpot-validation/config
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

    cleanup()
    dir('lpot-validation') {
        retry(5) {
            checkout scm
        }
    }

    try {
        try {

            stage("Download") {
                retry(5) {
                    if(MR_source_branch != ''){
                        checkout changelog: true, poll: true, scm: [
                                $class                           : 'GitSCM',
                                branches                         : [[name: "${MR_source_branch}"]],
                                browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                                doGenerateSubmoduleConfigurations: false,
                                extensions                       : [
                                        [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                                        [$class: 'CloneOption', timeout: 10],
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
                                        [$class: 'CloneOption', timeout: 10]
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

            if ("${binary_build_job}" == "") {
                stage('Build binary') {
                    List binaryBuildParams = [
                        string(name: "inc_url", value: "${lpot_url}"),
                        string(name: "inc_branch", value: "${lpot_branch}"),
                        string(name: "PR_source_branch", value: "${PR_source_branch}"),
                        string(name: "PR_target_branch", value: "${PR_target_branch}"),
                        string(name: "val_branch", value: "${val_branch}"),
                        string(name: "conda_env", value: "${conda_build_env_name}"),
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
                    def binary_type = "wheel"
                    if (conda_env_mode == "conda"){
                        binary_type = "conda"
                    }
                    copyArtifacts(
                            projectName: 'lpot-release-build',
                            selector: specific("${binary_build_job}"),
                            filter: "linux_binaries/${binary_type}/${python_version}/neural_compressor*.whl, linux_binaries/${binary_type}/${python_version}/neural_compressor*.tar.gz, linux_binaries/${binary_type}/${python_version}/neural-compressor*.tar.bz2",
                            fingerprintArtifacts: true,
                            flatten: true,
                            target: "${WORKSPACE}")
                }
                if (tensorflow_version == "spr-base" && framework == "tensorflow"){
                    tf_new_api="true"
                    copyArtifacts(
                            projectName: 'TF-spr-base-wheel-build', 
                            selector: specific("${tf_binary_build_job}"),
                            filter: 'tensorflow*.whl',
                            fingerprintArtifacts: true,
                            flatten: true,
                            target: "${WORKSPACE}")
                }else{
                    if (framework == "tensorflow"){
                        tf_new_api="false"
                    }
                }
                if (itex_version == "nightly" && framework == "tensorflow"){
                    copyArtifacts(
                            projectName: 'ITEX-binary-build',
                            selector: specific("${itex_binary_build_job}"),
                            filter: "intel_extension_for_tensorflow*.whl",
                            fingerprintArtifacts: true,
                            flatten: true,
                            target: "${WORKSPACE}")
                }
                if (pytorch_version == "nightly" && framework == "pytorch"){
                    copyArtifacts(
                            projectName: 'ipex-binary-build',
                            selector: specific("${pyt_binary_build_job}"),
                            filter: "torch*.whl,intel_extension_for_pytorch*.whl,torchvision*.whl",
                            fingerprintArtifacts: true,
                            flatten: true,
                            target: "${WORKSPACE}")
                }
            }

            // sync config json file
            syncConfigFile()

            getReferenceData()

            stage("Get model parameters") {
                println("Getting model parameters...")
                try {
                    // get params for tuning and benchmark
                    def modelConf =  jsonParse(readFile("$WORKSPACE/lpot-validation/config/model_params_${framework}.json"))."${framework}"."${model}"
                    model_src_dir = modelConf."model_src_dir"
                    dataset_location = modelConf."dataset_location"
                    input_model = modelConf."input_model"
                    yaml = modelConf."yaml"
                    sampling_size = ""
                } catch(e) {
                    error("Could not load parameters for ${framework} ${model}")
                }

                if ( MR_source_branch != '' ){
                    
                    //PR test will cover different strategies, the other test mode will use the passed strategy
                    if (framework == "tensorflow"){
                        strategy = "basic"
                        if (model_src_dir == "image_recognition/tensorflow_models/quantization/ptq"){
                            dataset_location = "/tf_dataset/dataset/TF_mini_imagenet"
                            println("PR test tensorflow model_src_dir is image_recognition.")
                            println("So set dataset_location to /tf_dataset/dataset/TF_mini_imagenet")
                        }
                        if (model_src_dir == "object_detection/tensorflow_models/quantization/ptq"){
                            // set mini-coco for obj mr test, set absolute baseline replace relative one to reach the acc goal
                            dataset_location = "/tf_dataset/tensorflow/mini-coco-100.record"
                            withEnv(["model_src_dir=${model_src_dir}"]) {
                                sh(
                                    script: 'sed -i "/relative:/s|relative:.*|absolute: 0.01|g" ${WORKSPACE}/lpot-models/examples/${framework}/${model_src_dir}/ssd_resnet50_v1.yaml',
                                    returnStdout: true
                                ).trim()
                            }
                            if (model == "faster_rcnn_resnet101_saved") {
                                sampling_size = "50,100"
                            }
                        }
                        if (model == "inception_v1"){
                            // set kl test for inception_v1
                            algorithm='kl'
                        }
                        if (model == "resnet50_fashion") {
                            dataset_location = "/tf_dataset2/datasets/mnist/FashionMNIST_small"
                        }
                    }else if(framework == "pytorch") {
                        if (model == "resnet18") {
                            strategy = "bayesian"
                        } 
                        if (model_src_dir.contains("image_recognition/torchvision_models/quantization")) {
                            dataset_location = "/tf_dataset2/datasets/mini-imageraw"
                        }
                    }else if(framework == "mxnet" && model == "resnet50v1"){
                        strategy = "mse"
                    } else if (framework == "onnxrt" && model == "resnet50-v1-12") {
                        strategy = "basic"
                        dataset_location = "/tf_dataset2/datasets/imagenet/ImagenetRaw/ImagenetRaw_small_5000/ILSVRC2012_img_val"
                    }else{
                        strategy = "basic"
                    }
                    // set timeout for PR test
                    timeout="timeout 5400"
                }
            }

            stage("Build Conda Env"){
                // specify conda env
                def new_conda_env = true
                def install_ipex = false
                if(framework == 'pytorch'){
                    label=model_src_dir.split('/')
                    if(label[1] == 'language_translation' && pytorch_version == '1.5.0+cpu'){
                        new_conda_env=false
                        conda_env_name='pytorch-bert-1.6'
                    }
                    if(label[-1] == 'ipex'){
                        conda_env_name="pt-ipex-${pytorch_version}-${python_version}"
                        install_ipex = true
                    }
                    if(label[-1] == 'qat'&& pytorch_version == '1.5.0+cpu'){
                        pytorch_version='1.8.0+cpu'
                        conda_env_name="${framework}-${pytorch_version}-${python_version}"
                    }
                    label=model.split('_')
                    if(label[-1] == 'fx' && pytorch_version == '1.5.0+cpu'){
                        pytorch_version = '1.8.0+cpu'
                        conda_env_name="${framework}-${pytorch_version}-${python_version}"
                    }
                    if(model == "bert_base_MRPC_qat"){
                        pytorch_version = '1.8.0+cpu'
                        conda_env_name="${framework}-${pytorch_version}-${python_version}"
                    }
                    if(model == "bert_large_ipex"  && pytorch_version != 'nightly'){
                        framework_version_base = pytorch_version.split('\\.')[1]
                        if(framework_version_base.toInteger() < 12){
                            pytorch_version = '1.12.1+cpu'
                            conda_env_name="${framework}-${pytorch_version}-${python_version}"
                        }
                    }
                    if(model == "bert_large_1_10_ipex"){
                        framework_version_base = pytorch_version.split('\\.')[1]
                        if(framework_version_base.toInteger() > 11){
                            pytorch_version = '1.11.0+cpu'
                            conda_env_name="${framework}-${pytorch_version}-${python_version}"
                        }
                    }
                }
                if (framework == "tensorflow") {
                    label=model.split('_')
                    if((model == 'bert_base_mrpc' || label[-1] == 'slim') && (!(tensorflow_version=~'1.15').find())){
                        tensorflow_version = '1.15UP3'
                        python_version=3.7
                        conda_env_name="${framework}-${tensorflow_version}-${python_version}"
                    }
                    if(model == 'yolo_v3'){
                        tensorflow_version = '1.15UP3'
                        python_version=3.7
                        conda_env_name="${framework}-${tensorflow_version}-${python_version}"
                    }
                }

                if (new_conda_env){
                    println("Start to create conda env...")
                    def _tf_ver=''
                    def _itex_ver=''
                    def _pt_ver=''
                    def _mx_ver=''
                    def _ort_ver=''
                    def _onnx_ver=''
                    def framework_version=''
                    if (framework=='tensorflow'){
                        framework_version=tensorflow_version
                        _tf_ver=tensorflow_version
                        if (itex_mode != "none"){
                            _itex_ver=itex_version
                            framework_version="$tensorflow_version-itex-$itex_version"
                        }
                    }else if(framework=='pytorch'){
                        framework_version=pytorch_version
                        _pt_ver=pytorch_version
                    }else if(framework=='mxnet'){
                        framework_version=mxnet_version
                        _mx_ver=mxnet_version
                    }else if(framework=='onnxrt'){
                        framework_version=onnxruntime_version
                        _ort_ver=onnxruntime_version
                        _onnx_ver=onnx_version
                    }
                    env_name_list=framework_version.split('=')
                    if (env_name_list[0] == 'customized'){
                        conda_env_name="${framework}-customized-${python_version}"
                    }else {
                        conda_env_name="${framework}-${framework_version}-${python_version}"
                    }

                    if ("${CPU_NAME}" != ""){
                        conda_env_name="${conda_env_name}-${CPU_NAME}"
                    }
                    create_conda_env(_tf_ver,_itex_ver,_pt_ver,_mx_ver,_ort_ver,_onnx_ver,install_ipex)
                }else{
                    println("Test need a special local conda env, DO NOT create again!!!")
                }

                println("Final conda env name is: $conda_env_name")
            }

            stage("Tuning") {
                echo "Tuning timeout ${timeout}"
                echo "CPU_NAME is ${CPU_NAME}"
                if (cpu in nightly_cpu_list){
                    cpu = cpu.split("-")[0]
                }
                if (framework=='pytorch' && (model_src_dir=~'oob_models').find()){
                    model_src_dir="${WORKSPACE}/lpot-validation/examples/${framework}/${model_src_dir}"
                }else{
                    model_src_dir="${WORKSPACE}/lpot-models/examples/${framework}/${model_src_dir}"
                }
                withCredentials([string(credentialsId: '2f98cfad-c470-4c49-a85a-43c236507236', variable: 'SIGOPT_TOKEN')]) {
                    sh """#!/bin/bash -x
                        echo "Running ---- ${framework}, ${model}, ${strategy} ----Tuning"
                        
                        echo "-------w-------"
                        w
                        echo "-------w-------"
                        echo "input path is ${dataset_prefix}${input_model}"
                        ${timeout} bash ${WORKSPACE}/lpot-validation/scripts/run_tuning_trigger.sh \
                            --python_version=${python_version} \
                            --framework=${framework} \
                            --model=${model} \
                            --model_src_dir=${model_src_dir} \
                            --dataset_location=${dataset_prefix}${dataset_location} \
                            --input_model=${dataset_prefix}${input_model} \
                            --yaml=${yaml} \
                            --strategy=${strategy} \
                            --strategy_token=${SIGOPT_TOKEN} \
                            --max_trials=${max_trials} \
                            --algorithm=${algorithm} \
                            --sampling_size="${sampling_size}" \
                            --conda_env_name=${conda_env_name} \
                            --conda_env_mode=${conda_env_mode} \
                            --log_level=${log_level} \
                            --dtype=${dtype} \
                            --backend=${backend} \
                            2>&1 | tee ${framework}-${model}-${os}-${cpu}-tune.log
                    """
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
            // Set Throughput mode for MR tests
            if (lpot_branch == '' && MR_source_branch != '') {
                mode_list = ["throughput"]
            }
            
            if (!tune_only) {
                timeout(720) {
                    stage("Performance") {
                        println("==========run benchmark========")
                        tf_perf_only_list = ['style_transfer', 'yolo_v3',
                                             'vgg16_keras', 'vgg16_keras_h5',
                                             'vgg19_keras', 'vgg19_keras_h5',
                                             'resnet50_keras', 'resnet50_keras_h5',
                                             'mobilenetv1_saved', 'mobilenetv2_saved',
                                             'efficientnet_v2_b0']
                        onnx_perf_only_list = ['unet']
                        precision_list.each { precision ->
                            echo "precision is ${precision}"
                            // oob only support dummy data
                            if ((model_src_dir=~'oob_models').find()
                                 || (framework == "tensorflow" && tf_perf_only_list.contains(model))
                                 || (framework == "onnxrt" && onnx_perf_only_list.contains(model))) {
                                mode_list = mode_list - 'accuracy'
                                echo "mode list is ${mode_list}"
                            }
                            mode_list.each { mode ->
                                runPerfTest(mode, precision)
                            }
                        }
                    }
                }
            }

            if (framework == 'baremetal' && (model_src_dir=~'nlp').find()){
                stage("Inferencer Benchmark"){
                    println("==========run inferencer benchmark========")
                    precision_list.each { precision ->
                        def ir_path = ''
                        if(precision == 'fp32'){
                            sh """#!/bin/bash -x
                            export PATH=${HOME}/miniconda3/bin/:$PATH
                            source activate ${conda_env_name}
                            python ${WORKSPACE}/lpot-validation/deep-engine/scripts/convert_ir.py --fp32_models=${input_model}
                            """
                            ir_path = "${WORKSPACE}/ir"
                        }else{
                            ir_path = "${WORKSPACE}/${framework}-${model}-tune"
                        }
                        inferencer_config.split(',').each { each_ben_conf ->
                            def ncores_per_instance = each_ben_conf.split(':')[0]
                            def bs = each_ben_conf.split(':')[1]

                            timeout(120) {
                                sh """#!/bin/bash -x
                                echo "Running ----${model}, ${ir_path}, ${ncores_per_instance},${bs},${precision} ----Inferencer Benchmark"
                                
                                echo "=======cache clean======="
                                sudo bash ${WORKSPACE}/lpot-validation/scripts/cache_clean.sh
                                echo "========================="
                                export PATH=${HOME}/miniconda3/bin/:$PATH
                                source activate ${conda_env_name}
                                bash ${WORKSPACE}/lpot-validation/deep-engine/scripts/launch_benchmark.sh ${model} ${ir_path} ${ncores_per_instance} ${bs} ${precision}
                            """
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
            checkReferenceData()
        }
    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // save log files
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: "${framework}*.log,${framework}*.json,${framework}-${model}/**,inferencer_summary.log,summary.log,tuning_info.log,reference_data.json,yaml_record.log", excludes: null
            fingerprint: true
            if (collect_tuned_model){
                archiveArtifacts artifacts: "${framework}-${model}-tune*,${framework}-${model}-tune/**", excludes: null
                fingerprint: true
            }
        }
    }
    
}
