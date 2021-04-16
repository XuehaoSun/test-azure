@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

credential = 'lab_tfbot'

currentBuild.description = framework + '-' + model

// parameters
// setting node_label
sub_node_label = "lpot"
if ('sub_node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${sub_node_label}"

// test framework
framework = "tensorflow"
if ('framework' in params && params.framework != '') {
    framework = params.framework
}
echo "framework: ${framework}"

// setting framework_version
framework_version  = '1.15.2'
if ('framework_version' in params && params.framework_version != '') {
    framework_version = params.framework_version
}
echo "framework_version: ${framework_version}"

// setting onnx_version
onnx_version  = '1.7.0'
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

mode = 'accuracy,latency'
if ('mode' in params && params.mode != '') {
    mode = params.mode
}
mode_list = parseStrToList(mode)
echo "Mode: ${mode}"

lpot_url="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

requirement_list="ruamel.yaml"
if ('requirement_list' in params && params.requirement_list != ''){
    requirement_list = params.requirement_list
}
echo "requirement_list is ${requirement_list}"

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

torchvision_version = ""
if (framework == "pytorch") {
    if (model == 'blendcnn' || model == 'resnest50'){
        framework_version = '1.6.0+cpu'
    }

    pytorch_version_base = framework_version.split('\\+')[0]
    try {
        pytorch_version_postfix = framework_version.split('\\+')[1]
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
}
println("torchvision_version: " + torchvision_version)


def algorithm=''
def new_conda_env=true
if(framework == 'pytorch'){
    label=model.split('_')
    if(label[0] == 'bert' || label[-1] == 'MRPC' || label[-1] == 'WikiText'){
        sub_node_label=sub_node_label + " && " + 'py-bert'
        new_conda_env=false
    }
    if(model == 'dlrm'){
        sub_node_label='dlrm'
    }
    if(label[-1] == 'ipex'){
        sub_node_label=sub_node_label + " && " + 'py-ipex'
        new_conda_env=false
    }
}

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
        sudo rm -rf *
        # set perf BKC
        cat /sys/devices/system/cpu/intel_pstate/no_turbo
        lscpu
        sudo cpupower frequency-set -g performance
        cat /proc/sys/kernel/numa_balancing
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

def create_conda_env(){
    retry(20){
            sh """#!/bin/bash
                bash ${WORKSPACE}/lpot-validation/scripts/create_conda_env.sh \
                    --model="${model}" \
                    --framework="${framework}" \
                    --framework_version="${framework_version}" \
                    --python_version="${python_version}" \
                    --torchvision_version="${torchvision_version}" \
                    --onnx_version="${onnx_version}" \
                    --requirement_list="${requirement_list}" \
                    --conda_env_name="${conda_env_name}"
            """
        }
}

def runPerfTest(mode, precision, output_path="${WORKSPACE}") {
    def modelConf =  jsonParse(readFile("$WORKSPACE/lpot-validation/config/model_params_${framework}.json"))."${framework}"."${model}"
    def model_src_dir = modelConf."model_src_dir"
    def dataset_location = modelConf."dataset_location"
    def input_model = modelConf."input_model"
    def yaml = modelConf."yaml"
    def batch_size = modelConf."batch_size"
    def new_benchmark = modelConf."new_benchmark"

    if (new_benchmark == true) {
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
                --model_src_dir=${model_src_dir} 
            set_environment
            echo "=================================="

            export PYTHONPATH=${WORKSPACE}/lpot-models:\$PYTHONPATH
            python ${WORKSPACE}/lpot-validation/scripts/run_new_benchmark_trigger.py \
                --framework=${framework} \
                --model=${model} \
                --model_src_dir=${WORKSPACE}/lpot-models/examples/${framework}/${model_src_dir} \
                --input_model=${dataset_prefix}${input_model} \
                --precision=${precision} \
                --mode=${mode} \
                --batch_size=${batch_size} \
                --yaml=${yaml} \
                --cpu=${cpu} \
                --output_path=${output_path}
            """
    } else {
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
                --model_src_dir=${WORKSPACE}/lpot-models/examples/${framework}/${model_src_dir} \
                --dataset_location=${dataset_prefix}${dataset_location} \
                --input_model=${dataset_prefix}${input_model} \
                --precision=${precision} \
                --mode=${mode} \
                --batch_size=${batch_size} \
                --conda_env_name=${conda_env_name} \
                --yaml=${yaml} \
                --os=${os} \
                --cpu=${cpu} \
                --profiling=${RUN_PROFILING} \
                --output_path=${output_path}
            """
    }
}

def getReferenceData() {
    stage("Get reference data") {
        if(refer_build != 'x0') {

            def refer_job_name = "${JOB_NAME}"

            if (test_mode == "extension") {
                refer_job_name="intel-lpot-validation-top-nightly"
            } else {
                if (upstreamJobName) {
                    refer_job_name = upstreamJobName
                }
            }
            println("Copying artifacts from ${refer_job_name} job")
            copyArtifacts(
                projectName: refer_job_name,
                selector: specific("${refer_build}"),
                filter: 'summary.log,',
                fingerprintArtifacts: true,
                target: "reference")

            sh"""#!/bin/bash
                set -x
                export PATH=${HOME}/miniconda3/bin/:$PATH
                if [[ ${framework} = 'pytorch' ]] && [[ ${model} = 'dlrm' ]]; then
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
        if [[ ${framework} = 'pytorch' ]] && [[ ${model} = 'dlrm' ]]; then
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
                        --framework_version=\"${framework_version}\" \
                        --python_version=\"${python_version}\" \
                        --model=\"${model}\" \
                        --logs_dir=\"${rerun_path}\" \
                        --output_dir=\"${rerun_path}\""

                sh """#!/bin/bash
                    set -x
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    if [[ ${framework} = 'pytorch' ]] && [[ ${model} = 'dlrm' ]]; then
                        export PATH=${HOME}/anaconda3/bin/:$PATH
                    fi
                    source activate ${conda_env_name}
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
            logs_prefix_url = JENKINS_URL + upstreamUrl + upstreamBuild + "/artifact/${framework}/${model}/"
        }

        println("Collecting logs...")

        cmd = "python ${WORKSPACE}/lpot-validation/scripts/collect_logs_lpot.py \
        --framework=\"${framework}\" \
        --framework_version=\"${framework_version}\" \
        --python_version=\"${python_version}\" \
        --model=\"${model}\" \
        --logs_dir=\"${WORKSPACE}\" \
        --output_dir=\"${WORKSPACE}\" \
        --logs_prefix_url=\"${logs_prefix_url}\" \
        --job_url=\"${BUILD_URL}/consoleText\""

        if (MR_source_branch != "") {
            cmd += " --mr"
        }

        required = "["
        precision_list.each { precision ->
            mode_list.each { mode ->
                required += "{\'precision\': \'${precision}\', \'mode\': \'${mode}\'},"
                }
        }
        required = required.substring(0, required.length() - 1) + "]"
        cmd += " --required=\"${required}\""

        sh """#!/bin/bash
            set -x
            export PATH=${HOME}/miniconda3/bin/:$PATH
            if [[ ${framework} = 'pytorch' ]] && [[ ${model} = 'dlrm' ]]; then
                export PATH=${HOME}/anaconda3/bin/:$PATH
            fi
            source activate ${conda_env_name}
            ${cmd}
        """

        println("Logs collected.")
    }
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
            stage("Build"){
                if (new_conda_env){
                    create_conda_env()
                }else{
                    println("Test need a special local conda env, DO NOT create again!!!")
                }

            }

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

            getReferenceData()

            // get params for tuning and benchmark
            def modelConf =  jsonParse(readFile("$WORKSPACE/lpot-validation/config/model_params_${framework}.json"))."${framework}"."${model}"
            model_src_dir = modelConf."model_src_dir"
            dataset_location = modelConf."dataset_location"
            input_model = modelConf."input_model"
            yaml = modelConf."yaml"

            //mr test will cover different strategies, the other test mode will use the passed strategy
            if ( MR_source_branch != '' ){
                if (framework == "tensorflow"){
                    strategy = "basic"
                    if (model_src_dir == "image_recognition"){
                        dataset_location = "/tf_dataset/dataset/TF_mini_imagenet"
                        println("MR test tensorflow model_src_dir is image_recognition.")
                        println("So set dataset_location to /tf_dataset/dataset/TF_mini_imagenet")
                    }
                    if (model_src_dir == "object_detection" && model == "ssd_resnet50_v1"){
                        // set mini-coco for obj mr test, set absolute baseline replace relative one to reach the acc goal
                        dataset_location = "/tf_dataset/tensorflow/mini-coco-500.record"
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
                }else if(framework == "pytorch" && model == "resnet18"){
                    strategy = "bayesian"
                }else if(framework == "mxnet" && model == "resnet50v1"){
                    strategy = "mse"
                }else{
                    strategy = "basic"
                }
            }

            if ( MR_source_branch != '' ){
                timeout="timeout 5400"
            }
            echo "Tuning timeout ${timeout}"
            stage("Tuning") {

                sh """#!/bin/bash -x
                    echo "Running ---- ${framework}, ${model}, ${strategy} ----Tuning"
                    
                    echo "-------w-------"
                    w
                    echo "-------w-------"
                    ${timeout} bash ${WORKSPACE}/lpot-validation/scripts/run_tuning_trigger.sh \
                        --framework=${framework} \
                        --model=${model} \
                        --model_src_dir=${WORKSPACE}/lpot-models/examples/${framework}/${model_src_dir} \
                        --dataset_location=${dataset_prefix}${dataset_location} \
                        --input_model=${dataset_prefix}${input_model} \
                        --yaml=${yaml} \
                        --strategy=${strategy} \
                        --max_trials=${max_trials} \
                        --algorithm=${algorithm} \
                        --conda_env_name=${conda_env_name} \
                        2>&1 | tee ${framework}-${model}-${os}-${cpu}-tune.log
                """
            }

            stage("Check tuning status") {
                dir("${WORKSPACE}"){
                    withEnv([
                            "framework=${framework}",
                            "model=${model}",
                            "os=${os}",
                            "cpu=${cpu}"]) {
                        sh '''#!/bin/bash -x
                            control_phrase="Found a quantized model which meet accuracy goal."
                            if [ $(grep "${control_phrase}" ${framework}-${model}-${os}-${cpu}-tune.log | wc -l) == 0 ];then
                                exit 1
                            fi
                        '''
                    }
                }
            }
            // Set Latency mode for MR tests
            if (lpot_branch == '' && MR_source_branch != '') {
                mode_list = ["latency"]
            }

            
            if (!tune_only) {
                println("==========nightly benchmark========")
                timeout(360) {
                    stage("Performance") {
                        precision_list.each { precision ->
                            echo "precision is ${precision}"
                            // oob only support dummy data
                                if (['oob_models'].contains(model_src_dir) || model == 'style_transfer') {
                                mode_list = ['latency']
                                echo "mode list is ${mode_list}"
                            }
                            mode_list.each { mode ->
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
            checkReferenceData()
        }
    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // save log files
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: "${framework}*.log,${framework}*.json,summary.log,tuning_info.log,reference_data.json", excludes: null
            fingerprint: true
        }
    }
    
}
