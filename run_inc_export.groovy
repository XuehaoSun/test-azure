@NonCPS
import groovy.time.TimeCategory 
import groovy.time.TimeDuration
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'

// parameters
python_version="3.8"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

sub_node_label=""
if ('sub_node_label' in params && params.sub_node_label != ''){
    sub_node_label = params.sub_node_label
}
echo "sub_node_label is ${sub_node_label}"

test_title=""
if ('test_title' in params && params.test_title != ''){        
    test_title = params.test_title
}
echo "test_title is ${test_title}"

frameworks=""
if ('frameworks' in params && params.frameworks != ''){        
    frameworks = params.frameworks
}
echo "frameworks is ${frameworks}"

tensorflow_version=""
if ('tensorflow_version' in params && params.tensorflow_version != ''){
    tensorflow_version = params.tensorflow_version
}
echo "tensorflow_version is ${tensorflow_version}"

pytorch_version=""
if ('pytorch_version' in params && params.pytorch_version != ''){
    pytorch_version = params.pytorch_version
}
echo "pytorch_version is ${pytorch_version}"

onnx_version=""
if ('onnx_version' in params && params.onnx_version != ''){
    onnx_version = params.onnx_version
}
echo "onnx_version is ${onnx_version}"

onnxruntime_version=""
if ('onnxruntime_version' in params && params.onnxruntime_version != ''){
    onnxruntime_version = params.onnxruntime_version
}
echo "onnxruntime_version is ${onnxruntime_version}"

inc_url=""
if ('inc_url' in params && params.inc_url != ''){
    inc_url = params.inc_url
}
echo "inc_url is ${inc_url}"

inc_branch=""
if ('inc_branch' in params && params.inc_branch != ''){
    inc_branch = params.inc_branch
}
echo "inc_branch is ${inc_branch}"

val_branch=""
if ('val_branch' in params && params.val_branch != ''){
    val_branch = params.val_branch
}
echo "val_branch is ${val_branch}"

model_name=""
if ('model_name' in params && params.model_name != ''){
    model_name = params.model_name
}
echo "model_name is ${model_name}"

ABORT_DUPLICATE_TEST=""
if ('ABORT_DUPLICATE_TEST' in params && params.ABORT_DUPLICATE_TEST != ''){
    ABORT_DUPLICATE_TEST = params.ABORT_DUPLICATE_TEST
}
echo "ABORT_DUPLICATE_TEST is ${ABORT_DUPLICATE_TEST}"

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

export_only=false
if (params.export_only != null){
    export_only=params.export_only
}
echo "export_only = ${export_only}"

// set variable
conda_env_name=''
model_paths=[]

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
        dir(WORKSPACE) {
            deleteDir()
            sh '''#!/bin/bash -x
                rm -rf *
                rm -rf .git
                sudo rm -rf *
                sudo rm -rf .git
                git config --global user.email "sys_lpot_val@intel.com"
                git config --global user.name "sys-lpot-val"
            '''
        }
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
    checkout changelog: true, poll: true, scm: [
            $class                           : 'GitSCM',
            branches                         : [[name: "${inc_branch}"]],
            browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
            doGenerateSubmoduleConfigurations: false,
            extensions                       : [
                    [$class: 'RelativeTargetDirectory', relativeTargetDir: "neural-compressor"],
                    [$class: 'CloneOption', timeout: 20]
            ],
            submoduleCfg                     : [],
            userRemoteConfigs                : [
                    [credentialsId: "${credential}",
                     url          : "${inc_url}"]
            ],
            depth                            : 1
    ]
}

def create_conda_env(_tf_ver,_pt_ver,_ort_ver,_onnx_ver){

    def String cmd = "bash ${WORKSPACE}/lpot-validation/scripts/create_conda_env.sh \
                    --python_version=\"${python_version}\" \
                    --tensorflow_version=\"${_tf_ver}\" \
                    --pytorch_version=\"${_pt_ver}\" \
                    --onnx_version=\"${_onnx_ver}\" \
                    --onnxruntime_version=\"${_ort_ver}\" \
                    --conda_env_name=\"${conda_env_name}\""

    retry(10){
        timeout(20){
            sh """#!/bin/bash
                ${cmd}
            """
        }
    }
}

def get_model_info(model_name){
    def String fwk = (frameworks=='TF2ONNX')?"tf2onnx":"pt2onnx"
    def modelParams =  jsonParse(readFile("${WORKSPACE}/neural-compressor/examples/.config/model_params_${fwk}.json"))
    return modelParams[fwk][model_name]
}

def export_model(fp32_model_info, precision){

    def String framework = (frameworks=='TF2ONNX')? "tensorflow" : "pytorch"

    // export output model path
    def String onnx_model_path = "${WORKSPACE}/onnx-${model_name}-export-${precision}.onnx"

    //trigger export for fp32, quant+export for int8
    sh """#!/bin/bash -x
        bash ${WORKSPACE}/lpot-validation/scripts/export_model_test/run_export_trigger_new_api.sh \
            --python_version=${python_version} \
            --framework=${framework} \
            --model=${model_name} \
            --model_src_dir=${WORKSPACE}/neural-compressor/examples/${framework}/${fp32_model_info.model_src_dir} \
            --dataset_location=${fp32_model_info.source_model_dataset} \
            --input_model=${fp32_model_info.input_model} \
            --conda_env_name=${conda_env_name} \
            --output_model=${onnx_model_path} \
            --precision=${precision} \
            --main_script=${fp32_model_info.main_script} 2>&1 | tee ${framework}-${model_name}-${precision}-export.log
    """
    // Check onnx model exists to verify export was successfully.
    if (fileExists(onnx_model_path)) {
        println("export succeed")
    } else {
        println("export failed")
        currentBuild.result = "FAILURE"
        throw new Exception("export model failed, onnx model ${onnx_model_path} doesn't exist.")
    }

    return onnx_model_path

}

def benchmark(bench_info){
    def String precision = bench_info.precision.toLowerCase()
    def String framework = ""
    if (bench_info.input_model.endsWith("pb")){
        framework="tensorflow"
    }else if (bench_info.input_model.endsWith("onnx")){
        framework="onnxrt"
    }else {
        framework="pytorch"
    }
    def String fwk_dir = (frameworks=='TF2ONNX')? "tensorflow" : "pytorch"
    def String save_mode = (bench_info.benchmark_mode=="performance")? "throughput":"accuracy"

    sh """#!/bin/bash
        bash ${WORKSPACE}/lpot-validation/scripts/export_model_test/run_benchmark_trigger_new_api.sh \
            --framework=${framework} \
            --model=${model_name} \
            --model_src_dir=${WORKSPACE}/neural-compressor/examples/${fwk_dir}/${bench_info.model_src_dir} \
            --dataset_location=${bench_info.dataset_location} \
            --input_model=${bench_info.input_model} \
            --precision=${precision} \
            --mode=${bench_info.benchmark_mode} \
            --batch_size=${bench_info.batch_size} \
            --multi_instance=true \
            --conda_env_name=${conda_env_name} \
            --main_script=${bench_info.main_script} 2>&1 | tee ${framework}-${model_name}-${precision}-${save_mode}.log
        """
}

def log_collect(){
    stage("Collect Source logs") {
        println("Updating logs prefix..")
        def Boolean use_tune_acc = true
        def String logs_prefix_url = ""
        def fwks = [(frameworks=='TF2ONNX')? "tensorflow" : "pytorch", "onnxrt"]
        fwks.each{ framework ->
            def String data_source = (framework=="onnxrt")?"Target":"Source"

            println("Collecting logs...")

            def String cmd = "python ${WORKSPACE}/lpot-validation/scripts/collect_export_model_log.py \
                    --python_version=\"${python_version}\" \
                    --framework=\"${framework}\" \
                    --frameworks=\"${frameworks}\" \
                    --model=\"${model_name}\" \
                    --logs_dir=\"${WORKSPACE}\" \
                    --output_dir=\"${WORKSPACE}\" \
                    --data_source=\"${data_source}\" \
                    --batch_size=\"32\" \
                    --job_url=\"${BUILD_URL}/consoleText\""

            if (use_tune_acc && data_source=="Source") {
                cmd += " --tune_acc"
            }

            required = "["
            ["int8", "fp32"].each { precision ->
                ["throughput", "accuracy"].each { mode ->
                    if ( data_source!="Source" || mode!="accuracy"){
                        required += "{\'precision\': \'${precision}\', \'mode\': \'${mode}\'},"  
                    }
                }
            }
            required = required.substring(0, required.length() - 1) + "]"

            cmd += " --required=\"${required}\""
            withEnv(["conda_env_name=${conda_env_name}"]) {
                sh """#!/bin/bash
                    echo "======= Activate conda env ======="
                    source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh \\
                        --framework=${framework} \\
                        --model=${model_name} \\
                        --conda_env_name=${conda_env_name}
                    set_environment
                    echo "=================================="
                    ${cmd}
                """
            }
            println("Logs collected.")
        }
    }
}

node( sub_node_label ){
    try {
        cleanup()
        dir('lpot-validation') {
            checkout scm
        }

        stage("download"){
            download()
        }

        stage('parse model information'){
            fp32_model_info = get_model_info(model_name)
            fp32_model_info.each {
                key, value -> 
                    println("${key}: ${value}")
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
        }

        stage("Build Conda Env"){
            // specify conda env
            def new_conda_env = true

            if (new_conda_env){
                println("Start to create conda env...")
                def _tf_ver="${tensorflow_version}"
                def _pt_ver="${pytorch_version}"
                def _ort_ver="${onnxruntime_version}"
                def _onnx_ver="${onnx_version}"

                conda_env_name="inc-export-test-tf${_tf_ver}-pt${_pt_ver}-ort${_ort_ver}"
                create_conda_env(_tf_ver,_pt_ver,_ort_ver,_onnx_ver)
            }else{
                println("Test need a special local conda env, DO NOT create again!!!")
            }

            println("Final conda env name is: ${conda_env_name}")
        }

        //                   FP32_fwk       INT8_fwk        INT8_onnx    FP32_onnx
        //  Performance    benchmark()     benchmark()     benchmark()  benchmark()
        //  Accuarcy       export_model()  export_model()  benchmark()  benchmark()

        stage("model export"){
            // export model
            //    - FP32_fwk --> FP32_onnx
            //    - INT8_fwk --> INT8_onnx
            def String int8_onnx_model_path = export_model(fp32_model_info, "int8")
            def String fp32_onnx_model_path = export_model(fp32_model_info, "fp32")

            def String int8_quant_model_path = (frameworks=='TF2ONNX')? "${WORKSPACE}/tensorflow-${model_name}-tune.pb" : "${fp32_model_info.input_model}"
            model_paths = ["Source":["INT8": int8_quant_model_path, "FP32": fp32_model_info['input_model']],
                           "Target":["INT8": int8_onnx_model_path, "FP32": fp32_onnx_model_path]]
        }

        if (!export_only){
            stage("benchmark"){
                // run benchmark
                def List benchmark_list = [["Target", "Source"], ["INT8", "FP32"], ["performance", "accuracy"]].combinations()
                for (item in benchmark_list) {
                    def (String fwk, String precision, String mode) = item
                    if ( fwk!="Source" || mode!="accuracy"){
                        benchmark(["input_model": model_paths[fwk][precision],
                                   "precision": precision,
                                   "benchmark_mode": mode,
                                   "model_src_dir": fp32_model_info.model_src_dir,
                                   "batch_size": (mode=="performance") ? "1":"${fp32_model_info.batch_size}",
                                   "main_script": fp32_model_info.main_script,
                                   "dataset_location": fp32_model_info[( fwk=="Source" )?"source_model_dataset":"target_model_dataset"]])
                    }
                }
            }
        }

        stage("log_analysis"){
            log_collect()
        }

    } catch(e) {
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
