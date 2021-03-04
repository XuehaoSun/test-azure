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
echo "Running ${model}"

precision = 'int8,fp32'
if ('precision' in params && params.precision != '') {
    precision = params.precision
}
def precision_list = parseStrToList(precision)
echo "Running ${precision}"

mode = 'accuracy,latency'
if ('mode' in params && params.mode != '') {
    mode = params.mode
}
def mode_list = parseStrToList(mode)
echo "Running ${mode}"

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
if ('cpu' in params && params.cpu != ''){
    cpu=params.cpu
}
echo "cpu: ${cpu}"

os="unknown"
if ('os' in params && params.os != ''){
    os=params.os
}
echo "os: ${os}"

dataset_prefix=""
if ('dataset_prefix' in params && params.dataset_prefix != ''){
    dataset_prefix=params.dataset_prefix
}
echo "dataset_prefix: ${dataset_prefix}"

def cleanup() {

    try {
        sh '''#!/bin/bash 
        set -x
        cd $WORKSPACE
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
                    --model=${model} \
                    --framework=${framework} \
                    --framework_version=${framework_version} \
                    --python_version=${python_version} \
                    --onnx_version=${onnx_version} \
                    --requirement_list=${requirement_list} \
                    --conda_env_name=${conda_env_name}
            """
        }
}

node( sub_node_label ) {
    // Get CPU name from env variable if not defined
    if (['unknown','any', '*'].contains(cpu)) {
        cpu = env.CPU_NAME
        echo "Detected cpu: ${cpu}"
    }

    cleanup()
    dir('lpot-validation') {
        retry(5) {
            checkout scm
        }
    }

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

        // get params for tuning and benchmark
        def modelConf =  jsonParse(readFile("$WORKSPACE/lpot-validation/config/model_params_${framework}.json"))
        model_src_dir = modelConf."${framework}"."${model}"."model_src_dir"
        dataset_location = modelConf."${framework}"."${model}"."dataset_location"
        input_model = modelConf."${framework}"."${model}"."input_model"
        yaml = modelConf."${framework}"."${model}"."yaml"

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

        stage("Check tuning status"){
            dir("${WORKSPACE}"){
                withEnv([
                        "framework=${framework}",
                        "model=${model}",
                        "os=${os}",
                        "cpu=${cpu}"]) {
                    sh '''#!/bin/bash -x
                        control_phrase="Found a quantized model which meet accuracy goal."
                        if [ "${model}" == "helloworld_keras" ]; then
                            control_phrase="Inference is done."
                        fi
                        if [ $(grep "${control_phrase}" ${framework}-${model}-${os}-${cpu}-tune.log | wc -l) == 0 ];then
                            exit 1
                        fi
                    '''
                }
            }
        }

        // Set Latency mode for MR tests
        if (lpot_branch == ''&& MR_source_branch != '') {
            mode_list = ["latency"]
        }

        // MR test dummy inference
        def dummy_inference_models = [
            "resnet50v1.5",
            "resnet50v1",
            "inception_v1"]
        if (lpot_branch == '' && dummy_inference_models.contains(model)) {
            batch_size = modelConf."${framework}"."${model}"."batch_size"
            stage("MR Performance") {
                precision_list.each { precision ->
                    echo "precision is ${precision}"
                    mode_list.each { mode ->
                        if (mode == 'latency') {
                            batch_size = 1
                        } 
                        sh """#!/bin/bash -x
                        echo "Running ---- ${framework}, ${model},${precision},${mode} ---- Benchmarking"
                        
                        echo "-------w-------"
                        w
                        echo "-------w-------"
                        echo "=======cache clean======="
                        
                        sudo bash ${WORKSPACE}/lpot-validation/scripts/cache_clean.sh
        
                        echo "=======cache clean======="
                        bash ${WORKSPACE}/lpot-validation/scripts/run_dummy_inference.sh \
                            --framework=${framework} \
                            --model=${model} \
                            --input_model=${dataset_prefix}${input_model} \
                            --precision=${precision} \
                            --mode=${mode} \
                            --batch_size=${batch_size} \
                            --os=${os} \
                            --cpu=${cpu} \
                            --conda_env_name=${conda_env_name}
                        """
                    }
                }
            }
        } else {
            // Nightly tests and OOB MR tests 
            if (!tune_only && model != "helloworld_keras") {
                println("==========nightly benchmark========")
                batch_size = modelConf."${framework}"."${model}"."batch_size"
                timeout(360) {
                    stage("Performance") {
                        precision_list.each { precision ->
                            echo "precision is ${precision}"
                            // oob only support dummy data
                            if (model_src_dir == 'oob_models' || model == 'style_transfer') {
                                mode_list = ['latency']
                                echo "mode list is ${mode_list}"
                            }

                            mode_list.each { mode ->
                                echo "mode is ${mode}"
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
                                    --model_src_dir=${WORKSPACE}/lpot-models/examples/${framework}/${model_src_dir} \\
                                    --dataset_location=${dataset_prefix}${dataset_location} \
                                    --input_model=${dataset_prefix}${input_model} \
                                    --precision=${precision} \
                                    --mode=${mode} \
                                    --batch_size=${batch_size} \
                                    --conda_env_name=${conda_env_name} \
                                    --yaml=${yaml} \
                                    --os=${os} \
                                    --cpu=${cpu} \
                                    --profiling=${RUN_PROFILING}
                                """
                            }
                        }
                    }
                }
            }
        }

    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {

        // save log files
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: "${framework}*.log", excludes: null
            fingerprint: true
        }
    }
    
}
