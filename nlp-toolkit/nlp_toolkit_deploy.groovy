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
framework = "engine"
if ('framework' in params && params.framework != '') {
    framework = params.framework
}
echo "framework: ${framework}"

// setting framework_version
framework_version  = '0.2'
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

inc_version = '2.0'
if ('inc_version' in params && params.inc_version != '') {
    inc_version = params.inc_version
}
echo "inc_version: ${inc_version}"

itrex_version = '1.0b0'
if ('itrex_version' in params && params.itrex_version != '') {
    itrex_version = params.itrex_version
}
echo "itrex_version: ${itrex_version}"

compatibility_test = false
if (params.compatibility_test != null) {
    compatibility_test=params.compatibility_test
}
echo "compatibility_test = ${compatibility_test}"

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

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

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
}else{
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

performance_only = false
if (params.performance_only != null) {
    performance_only=params.performance_only
}
echo "performance_only = ${performance_only}"

val_branch = "main"
if ('val_branch' in params && params.val_branch != '') {
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

collect_tuned_model = false
if (params.collect_tuned_model != null) {
    collect_tuned_model=params.collect_tuned_model
}
echo "collect_tuned_model = ${collect_tuned_model}"

inferencer_config = "4:64,4:128,24:1"
if ('inferencer_config' in params && params.inferencer_config != '') {
    inferencer_config=params.inferencer_config
}
echo "inferencer_config: ${inferencer_config}"

multi_instance = true
if (params.multi_instance != null) {
    multi_instance = params.multi_instance
}
echo "Multi instance: ${multi_instance}"

conda_env_name = ''
env_name_list=framework_version.split('=')
if (env_name_list[0] == 'customized') {
    conda_env_name="${framework}-customized-${python_version}"
} else {
    conda_env_name="${framework}-${framework_version}-${python_version}"
}
println("conda_env_name = " + conda_env_name)

log_level = "DEBUG"
if ('log_level' in params && params.log_level != '') {
    log_level=params.log_level
}
echo "log_level: ${log_level}"

cpu="unknown"
os="unknown"
if ('os' in params && params.os != '') {
    os=params.os
}
echo "os: ${os}"

refer_build = "x0"
if ('refer_build' in params && params.refer_build != '') {
    refer_build = params.refer_build
}
echo "Refer build is ${refer_build}"

dataset_prefix = ""
if ('dataset_prefix' in params && params.dataset_prefix != '') {
    dataset_prefix=params.dataset_prefix
}
echo "dataset_prefix: ${dataset_prefix}"

install_nlp_toolkit = "true"
if ('install_nlp_toolkit' in params && params.install_nlp_toolkit != '') {
    install_nlp_toolkit=params.install_nlp_toolkit
}
echo "install_nlp_toolkit: ${install_nlp_toolkit}"

launcher_mode = ""
if ('launcher_mode' in params && params.launcher_mode != '') {
    launcher_mode=params.launcher_mode
}
echo "launcher_mode: ${launcher_mode}"
launcher_mode_list = parseStrToList(launcher_mode)

lpot_url = "https://github.com/intel/neural-compressor.git"
lpot_branch = "master"
if ('lpot_branch' in params && params.lpot_branch) {
    lpot_branch=params.lpot_branch
}

perf_bs = "1"
if ('perf_bs' in params && params.perf_bs != '') {
    perf_bs = params.perf_bs
}
echo "Performance batch size: ${perf_bs}"

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

sparse_model_list = ["distilbert_base_uncased_squad_sparse", "bert_mini_sparse"]
not_support_inferencer_list = ["length_adaptive_dynamic", "vit_large", "vit_base"]
mono_socket = params.mono_socket as Boolean
echo "mono_socket: ${mono_socket}"

workflow = "deploy"
nightly_cpu_list = ["clx8280-070", "clx8280-071", "clx8280-072", "clx8280-073", "clx8260-136", "clx8260-137", "clx8280-0769"]
upstreamBuild = ""
upstreamJobName = ""
upstreamUrl = ""
performance_only_list = []
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

def parseStrToList(srtingElements, delimiter=',') {
    if (srtingElements == ''){
        return []
    }
    return srtingElements[0..srtingElements.length()-1].tokenize(delimiter)
}

def create_conda_env(install_ipex){
    def cmd = "bash ${WORKSPACE}/lpot-validation/scripts/create_conda_env.sh \
                    --model=\"${model}\" \
                    --python_version=\"${python_version}\" \
                    --conda_env_name=\"${conda_env_name}\""

    if (install_ipex) {
        cmd += " --install_ipex=\"true\""
    }
    retry(20){
        timeout(60){
            sh """#!/bin/bash
                echo "cmd is ${cmd}"
                ${cmd}
            """
            withEnv(["framework=${framework}","conda_env_name=${conda_env_name}","model=${model}","conda_env_mode=${conda_env_mode}","log_level=${log_level}","install_nlp_toolkit=${install_nlp_toolkit}", "compatibility_test=${compatibility_test}", "inc_version=${inc_version}", "itrex_version=${itrex_version}"]) {
                sh '''#!/bin/bash
                    echo -e "\nSetting environment..."
                    if [[ ${compatibility_test} == "true" ]]; then
                        source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh --framework=${framework} --model=${model} --conda_env_name=${conda_env_name} --conda_env_mode=${conda_env_mode} --log_level=${log_level} --install_nlp_toolkit=${install_nlp_toolkit} --install_inc="true"  --compatibility_test=${compatibility_test} --inc_version=${inc_version} --itrex_version=${itrex_version} 
                    else
                        source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh --framework=${framework} --model=${model} --conda_env_name=${conda_env_name} --conda_env_mode=${conda_env_mode} --log_level=${log_level} --install_nlp_toolkit=${install_nlp_toolkit} --install_inc="true"
                    fi
                    set_environment
                '''
            }
        }
    }
}

def runPerfTest(mode, local_precision, benchmark_cmd, output_path="${WORKSPACE}") {
    def local_benchmark_cmd = benchmark_cmd
    def batch_size = 0
    if (perf_bs != "default" && mode != "accuracy") {
        batch_size = perf_bs
    }
    benchmark_cmd_params.each{ k, v -> 
        if (k == "mode") {
            v = mode
            if (v == "benchmark_only") {
                println("backend not support benchmark only")
                return
            }
        }
        if (k == "model") {
            if ("${USER_NAME}" == "sdp" || "${USER_NAME}" == "SDP") {
                v = hf_model_name
            }
        }
        if (k == "precision") {
            v = local_precision
        }
        if (k == "cache_dir") {
            if ("${USER_NAME}" == "sdp" || "${USER_NAME}" == "SDP") {
                v = "${HOME}/.cache/huggingface"
            }
            if (model == "vit_large" || model == "vit_base") {
                v = "${HOME}/.cache/nlp_toolkit/vit"
            }
        }
        if (k == "ir_path") {
            v = "${working_dir_fullpath}/${local_precision}_ir"
        }
        if (k == "batch_size" && batch_size != 0){
            v = batch_size
        }
       local_benchmark_cmd += " --${k}=${v}" 
    }
    if (model != "gpt-j-6b" && model != "stable_diffusion") {
        local_benchmark_cmd += " --output=${output_path}   --log_name=${framework}-${model}-${local_precision}-${os}-${cpu}"
    }
    echo "benchmark cmd is ${local_benchmark_cmd}"
    withEnv(["framework=${framework}","model=${model}","precision=${local_precision}","os=${os}","cpu=${cpu}","working_dir=${working_dir_fullpath}","data_path=${data_dir}","benchmark_cmd=${local_benchmark_cmd}", "conda_env_name=${conda_env_name}", "output_path=${output_path}", "multi_instance=${multi_instance}","mode=${mode}"]) {
        sh '''#!/bin/bash -x
            echo "Running ---- ${framework}, ${model},${precision} ---- Benchmarking"
            echo "=======cache clean======="
            sudo bash ${WORKSPACE}/lpot-validation/scripts/cache_clean.sh
            echo "=======run benchmark======="
            export PYTHONPATH=${WORKSPACE}/nlp-models:\$PYTHONPATH
            [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
            [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
            export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env_name}/lib/:$LD_LIBRARY_PATH
            export GLOG_minloglevel=2
            export TRANSFORMERS_OFFLINE=1
            source activate ${conda_env_name}
            if [[ ${cpu} == *"spr"* ]] || [[ ${cpu} == *"SPR"* ]] || [[ ${cpu} == *"Spr"* ]];then
                export PATH=/opt/rh/gcc-toolset-11/root/usr/bin:$PATH
                export LD_LIBRARY_PATH=/opt/rh/gcc-toolset-11/root/usr/lib:$LD_LIBRARY_PATH
                export CC=/opt/rh/gcc-toolset-11/root/usr/bin/gcc
                export CXX=/opt/rh/gcc-toolset-11/root/usr/bin/g++
                gcc -v
            fi
            if [[ ${model} == "gpt-j-6b" ]]; then
                export KMP_BLOCKTIME=1
                export KMP_SETTINGS=1
                export KMP_AFFINITY=granularity=fine,compact,1,0
                # IOMP
                #export LD_PRELOAD=${LD_PRELOAD}:${CONDA_PREFIX}/lib/libiomp5.so
                # Tcmalloc is a recommended malloc implementation that emphasizes fragmentation avoidance and scalable concurrency support.
                #export LD_PRELOAD=${LD_PRELOAD}:${CONDA_PREFIX}/lib/libtcmalloc.so
            fi
            echo "final benchmark cmd of precision ${precision} is ${benchmark_cmd}"
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
            if [[ "${model}" == "vit_large"* ]] || [[ "${model}" == "vit_base"* ]]; then
                cp -r ${data_path} ${working_dir}/data
                cp -r ${data_path}/* ${HOME}/.cache/nlp_toolkit/vit/
            fi
            echo "working in ${working_dir}"
            ${benchmark_cmd} 2>&1|tee overall.log
        '''
    }
}

def runLauncherTest(mode, precision, launcher_cmd, launcher_cmd_params) {
    def local_launcher_cmd = launcher_cmd
    def local_launcher_cmd_params = launcher_cmd_params."${mode}"
    local_launcher_cmd_params.each{ k, v -> 
       local_launcher_cmd += " --${k}=${v}" 
    }
    def local_benchmark_cmd = "${working_dir}/run_executor.py"
    def batch_size = 0
    if (perf_bs != "default" && mode != "accuracy") {
        batch_size = perf_bs
    }
    benchmark_cmd_params.each{ k, v -> 
        if (k == "mode") {
            v = "performance"
        }
        if (k == "input_model") {
            if (v == "sparse_ir") {
                v = "${working_dir_fullpath}/sparse_${precision}_ir"
            } else if (! v.find("/tf_dataset")) {
                v = "${working_dir_fullpath}/${v}/${precision}-model.onnx"
            }
        }
        if (k == "batch_size" && batch_size != 0){
            v = batch_size
        }
       local_benchmark_cmd += " --${k}=${v}" 
    }
    echo "launcher cmd is ${local_launcher_cmd} ${local_benchmark_cmd}"
    withEnv(["framework=${framework}","model=${model}","precision=${precision}","data_path=${data_dir}","benchmark_cmd=${local_benchmark_cmd}", "conda_env_name=${conda_env_name}", "launcher_cmd=${local_launcher_cmd}"]) {
        sh '''#!/bin/bash -x
            echo "Running ---- ${framework}, ${model},${precision} ---- Benchmarking"
            echo "=======cache clean======="
            sudo bash ${WORKSPACE}/lpot-validation/scripts/cache_clean.sh
            echo "=======launcher benchmark======="
            [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
            [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
            export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env_name}/lib/:$LD_LIBRARY_PATH
            source activate ${conda_env_name}
            echo "final launcher benchmark cmd of precision ${precision} is ${launcher_cmd}"
            cd ${WORKSPACE}/nlp-models/examples/
            echo "working in ${WORKSPACE}/nlp-models/examples"
            GLOG_minloglevel=2 python ${launcher_cmd} ${benchmark_cmd}
        '''
    }
}
def prepare_models(local_precision, prepare_cmd) {
    def local_prepare_cmd = prepare_cmd
    prepare_cmd_params.each{ k, v -> 
        if (k == "precision") {
            v = local_precision
        }
        if (k == "dtype") {
            v = local_precision
        }
        if (k == "pt_file") {
            if (local_precision == "int8") {
                v = "/tf_dataset2/models/nlp_toolkit/gpt-j/best_model_bk.pt"
            } else {
                v = "${v}_${local_precision}"
            }
        }
        if (k == "output_dir") {
            v = "${working_dir_fullpath}/${v}"
            if (framework == "ipex") {
                v = "${v}/${local_precision}"
            }
        }
        if (k == "output_model") {
            if (model == "gpt-j-6b") {
                v = "${working_dir_fullpath}/${local_precision}_ir"
            }
        }
        if (k == "cache_dir") {
            if ("${USER_NAME}" == "sdp" || "${USER_NAME}" == "SDP") {
                v = "${HOME}/.cache/huggingface"
            }
        }
        if (k == "input_model") {
            if ("${USER_NAME}" == "sdp" || "${USER_NAME}" == "SDP") {
                v = hf_model_name
            }
            if (model == "stable_diffusion") {
                withEnv(["model_path=${v}", "working_dir_fullpath=${working_dir_fullpath}"]) {
                    sh '''#!/bin/bash -x
                    cp -r "${model_path}" "${working_dir_fullpath}/"
                    '''
                }
                v = "${working_dir_fullpath}/stable-diffusion"
            }
        }
        local_prepare_cmd += " --${k}=${v}" 
    }
    echo "prepare model cmd is ${local_prepare_cmd}"
    if (cpu in nightly_cpu_list){
        cpu = cpu.split("-")[0]
    }
    if (model == "vit_large*" || model == "vit_base*") {
        withEnv(["data_dir=${data_dir}"]) {
            sh '''#!/bin/bash -x
                cp -r ${data_dir} ${HOME}/.cache/nlp_toolkit/vit
            '''
        }    
    }
    withEnv(["framework=${framework}","model=${model}","timeout=${timeout}","os=${os}","cpu=${cpu}","working_dir=${working_dir_fullpath}","prepare_cmd=${local_prepare_cmd}","conda_env_name=${conda_env_name}"]) {
        sh '''#!/bin/bash -x
            echo "Running ---- ${framework}, ${model}----Tuning"
            [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
            [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
            export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env_name}/lib/:$LD_LIBRARY_PATH
            export TRANSFORMERS_OFFLINE=1
            source activate ${conda_env_name}
            if [[ ${cpu} == *"spr"* ]] || [[ ${cpu} == *"SPR"* ]] || [[ ${cpu} == *"Spr"* ]]; then
                export PATH=/opt/rh/gcc-toolset-11/root/usr/bin:$PATH
                export LD_LIBRARY_PATH=/opt/rh/gcc-toolset-11/root/usr/lib:$LD_LIBRARY_PATH
                export CC=/opt/rh/gcc-toolset-11/root/usr/bin/gcc
                export CXX=/opt/rh/gcc-toolset-11/root/usr/bin/g++
                gcc -v
            fi
            if [[ ${model} == "gpt-j-6b" ]]; then
                conda install mkl mkl-include -y
                conda install gperftools jemalloc==5.2.1 -c conda-forge -y
            fi
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
            ${timeout} ${prepare_cmd} 2>&1 | tee -a ${WORKSPACE}/${framework}-${model}-${os}-${cpu}-tune.log
        '''
    }
    // Check tuning status
    if (local_precision == "int8" && model != "gpt-j-6b") {
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
}

def run_inferencer(ncores_per_instance, bs, precision) {
    logs_prefix_url = ""
    if (upstreamUrl != "") {
        logs_prefix_url = JENKINS_URL + upstreamUrl + upstreamBuild + "/artifact/deploy/${framework}/${model}/"
    }
    if (model in performance_only_list) {
        model_path = benchmark_cmd_params."input_model"
    } else if (model in sparse_model_list) {
        model_path = "${working_dir_fullpath}/sparse_${precision}_ir"
    } else if (model in not_support_inferencer_list) {
        return
    }
    else {
        model_path = "${working_dir_fullpath}/ir"
    }
    def mono_socket_opt = mono_socket ? '1' : '0'
    withEnv([
        "conda_env_name=${conda_env_name}",
        "working_dir_fullpath=${working_dir_fullpath}",
        "model=${model}",
        "ncores_per_instance=${ncores_per_instance}",
        "bs=${bs}",
        "precision=${precision}",
        "logs_prefix_url=${logs_prefix_url}",
        "ir_path=${model_path}",
        "mono_socket_opt=${mono_socket_opt}",
    ]){
        sh'''#!/bin/bash -x
        [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
        [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
        export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env_name}/lib/:$LD_LIBRARY_PATH
        source activate ${conda_env_name}
        echo "Running ----${model}, ${ir_path}, ${ncores_per_instance},${bs},${precision} ----Inferencer Benchmark"
        sudo bash ${WORKSPACE}/lpot-validation/scripts/cache_clean.sh
        bash ${WORKSPACE}/lpot-validation/nlp-toolkit/scripts/launch_benchmark.sh ${model} ${ir_path} ${ncores_per_instance} ${bs} ${precision} ${working_dir_fullpath} ${mono_socket_opt}
        '''
    }
}

def getReferenceData() {
    stage("Get reference data") {
        if(refer_build != 'x0') {
            def refer_job_name = "${JOB_NAME}"
            if (test_mode == "extension") {
                refer_job_name="nlp-toolkit-validation-nightly"
            }else if(test_mode == "pre-CI"){
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
                runPerfTest(mode, precision, benchmark_cmd, "${rerun_path}")

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
            logs_prefix_url = JENKINS_URL + upstreamUrl + upstreamBuild + "/artifact/deploy/${framework}/${model}/"
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

def collectLauncherLogs(launchermode, precision) {
    logs_prefix_url = ""
    if (upstreamUrl != "") {
        logs_prefix_url = JENKINS_URL + upstreamUrl + upstreamBuild + "/artifact/deploy/${framework}/${model}/launcher_${precision}_${launchermode}/"
    }
    withEnv(["conda_env_name=${conda_env_name}", "logs_prefix_url=${logs_prefix_url}", "framework=${framework}", "model=${model}", "precision=${precision}", "mode=${launchermode}"]) {
        sh '''#!/bin/bash
            set -x
            [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
            [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
            source activate ${conda_env_name}
            output_file=${WORKSPACE}/nlp-models/examples/out.csv
            echo "working in"
            pwd
            if [[ ! -f ${output_file} ]]; then
                echo "${framework},${mode},${model},batch,cores per instance,Troughput,${precision}," >> ${WORKSPACE}/launcher_summary.log
            else
                throughput=$(grep -A 2 "best," ${output_file} | tail -1 | awk -F "," '{print $1","$3","$4}')
                log_file=$(grep -A 2 "best," ${output_file} | tail -1 | awk -F "=" '{print $NF}' | awk '{print $1}' | sed 's|\"||g')
                logfile=${log_file##*/}
                logfile=${logs_prefix_url}${logfile}
                echo "${framework},${mode},${model},${throughput},${precision},${logfile}" >> ${WORKSPACE}/launcher_summary.log
                [[ -d ${WORKSPACE}/launcher_${precision}_${mode} ]] && rm -fr ${WORKSPACE}/launcher_${precision}_${mode}
                mkdir -p ${WORKSPACE}/launcher_${precision}_${mode}
                mv ${WORKSPACE}/nlp-models/examples/*.log ${WORKSPACE}/launcher_${precision}_${mode}/
                mv ${WORKSPACE}/nlp-models/examples/out.csv ${WORKSPACE}/out_${precision}_${mode}.csv
            fi
        '''
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
                if (binary_mode == "backend" && test_install_backend) {
                    catchError {
                        copyArtifacts(
                            projectName: 'nlp-toolkit-release-wheel-build',
                            selector: specific("${binary_build_job_nlp}"),
                            filter: 'intel_extension_for_transformers_backends*.whl, intel_extension_for_transformers-*.tar.gz',
                            fingerprintArtifacts: true,
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

            // sync config json file
            //syncConfigFile()
            sh '''#!/bin/bash
            mkdir -p ${WORKSPACE}/lpot-validation/config && cp ${WORKSPACE}/lpot-validation/nlp-toolkit/config/* ${WORKSPACE}/lpot-validation/config
            ls ${WORKSPACE}/lpot-validation/config
            '''

            //getReferenceData()

            stage("Build Conda Env") {
                def install_ipex = false
                if (model.find('ipex')) {
                    conda_env_name="pt-ipex-${framework_version}-${python_version}"
                    install_ipex = true
                }
                if (framework == "ipex") {
                    conda_env_name="ipex-${framework_version}-${python_version}"
                    install_ipex = false
                }
                if ("${CPU_NAME}" != "") {
                    conda_env_name="${conda_env_name}-${CPU_NAME}"
                }
                println("final conda_env_name = " + conda_env_name)
                create_conda_env(install_ipex)
                
            }
            
            stage("Get model params") {
                try {
                    // get params for tuning and benchmark
                    if (compatibility_test) {
                        model_name = model.split("-itrex")[0]
                    } else {
                        model_name = "${model}"
                    }
                    def modelConf =  jsonParse(readFile("$WORKSPACE/nlp-models/examples/.config/${framework}_deploy.json"))."${model_name}"
                    working_dir = modelConf."working_dir"
                    working_dir_fullpath = "${WORKSPACE}/nlp-models/examples/${working_dir}"
                    data_dir_local = modelConf."data_dir"
                    data_dir = "${dataset_prefix}/${data_dir_local}"
                    hf_model_name = modelConf."hf_model_name"
                    prepare_cmd = ""
                    try {
                        prepare_cmd = modelConf."prepare"."cmd"
                        prepare_cmd_params = modelConf."prepare"."params"
                    } catch(e) {
                        println("run pipeline, do not need prepare model")
                    }
                    benchmark_cmd = modelConf."benchmark"."cmd"
                    benchmark_cmd_params = modelConf."benchmark"."params"

                    launcher_cmd = modelConf."launcher"."cmd"
                    launcher_cmd_params = modelConf."launcher"."params"
                } catch(e) {
                    error("Could not genereate cmd for ${framework} ${model}")
                }
            }
            if (model in performance_only_list) {
                performance_only = true
                // although no need quantiza, still need to install requirement.txt
                withEnv(["working_dir=${working_dir_fullpath}","conda_env_name=${conda_env_name}"]) {
                    sh '''#!/bin/bash -x
                    [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
                    [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
                    export LD_LIBRARY_PATH=${HOME}/miniconda3/envs/${conda_env_name}/lib/:$LD_LIBRARY_PATH
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
                    '''
                }
            }
            if ("fp32" in precision_list) {
                stage("FP32 workflow") {
                    if (prepare_cmd != "") {
                        prepare_models("fp32", prepare_cmd)
                    }
                    echo "--------START BENCHMARK----------"
                    mode_list.each { mode ->
                        runPerfTest(mode, "fp32", benchmark_cmd)
                        if (framework == "engine") {
                            if ( mode == "throughput" && launcher_mode != "") {
                                stage("Launcher Benchmark"){
                                    println("==========run launcher benchmark========")
                                    launcher_mode_list.each { launch_mode ->
                                        runLauncherTest(launch_mode, "fp32", launcher_cmd, launcher_cmd_params)
                                        collectLauncherLogs(launch_mode, "fp32")
                                    }
                                }
                            }
                        }
                    }
                    if (framework == "engine") {
                        stage("Inferencer Benchmark"){
                            println("==========run inferencer benchmark========")
                            inferencer_config.split(',').each { each_ben_conf ->
                                def ncores_per_instance = each_ben_conf.split(':')[0]
                                def bs = each_ben_conf.split(':')[1]
                                run_inferencer(ncores_per_instance, bs, "fp32")
                            }
                        }
                    }
                }
            }
            if ("int8" in precision_list) {
                stage("INT8 workflow") {
                    if (prepare_cmd != "") {
                        prepare_models("int8", prepare_cmd)
                    }
                    echo "--------START BENCHMARK----------"
                    mode_list.each { mode ->
                        runPerfTest(mode, "int8", benchmark_cmd)
                        if (framework == "engine") {
                            if ( mode == "throughput" && launcher_mode != "") {
                                stage("Launcher Benchmark"){
                                    println("==========run launcher benchmark========")
                                    launcher_mode_list.each { launch_mode ->
                                        runLauncherTest(launch_mode, "int8", launcher_cmd, launcher_cmd_params)
                                        collectLauncherLogs(launch_mode, "int8")
                                    }
                                }
                            }
                        }
                    }
                    if (framework == "engine") {
                        stage("Inferencer Benchmark"){
                            println("==========run inferencer benchmark========")
                            inferencer_config.split(',').each { each_ben_conf ->
                                def ncores_per_instance = each_ben_conf.split(':')[0]
                                def bs = each_ben_conf.split(':')[1]
                                run_inferencer(ncores_per_instance, bs, "int8")
                            }
                        }  
                    }   
                }   
            }
            if ("bf16" in precision_list) {
                stage("BF16 workflow") {
                    if (prepare_cmd != "") {
                        prepare_models("bf16", prepare_cmd)
                    }
                    echo "--------START BENCHMARK----------"
                    mode_list.each { mode ->
                        runPerfTest(mode, "bf16", benchmark_cmd)
                        if (framework == "engine") {
                            if ( mode == "throughput" && launcher_mode != "") {
                                stage("Launcher Benchmark"){
                                    println("==========run launcher benchmark========")
                                    launcher_mode_list.each { launch_mode ->
                                        runLauncherTest(launch_mode, "bf16", launcher_cmd, launcher_cmd_params)
                                        collectLauncherLogs(launch_mode, "bf16")
                                    }
                                }
                            }
                        }
                    }
                    if (framework == "engine") {
                        stage("Inferencer Benchmark"){
                            println("==========run inferencer benchmark========")
                            inferencer_config.split(',').each { each_ben_conf ->
                                def ncores_per_instance = each_ben_conf.split(':')[0]
                                def bs = each_ben_conf.split(':')[1]
                                run_inferencer(ncores_per_instance, bs, "bf16")
                            }
                        }  
                    }     
                }
            }
            if ("dynamic_int8" in precision_list) {
                stage("Dynamic INT8 workflow") {
                    if (prepare_cmd != "") {
                        prepare_models("dynamic_int8", prepare_cmd)
                    }
                    echo "--------START BENCHMARK----------"
                    mode_list.each { mode ->
                        runPerfTest(mode, "dynamic_int8", benchmark_cmd)
                        if (framework == "engine") {
                            if ( mode == "throughput" && launcher_mode != "") {
                                stage("Launcher Benchmark"){
                                    println("==========run launcher benchmark========")
                                    launcher_mode_list.each { launch_mode ->
                                        runLauncherTest(launch_mode, "dynamic_int8", launcher_cmd, launcher_cmd_params)
                                        collectLauncherLogs(launch_mode, "dynamic_int8")
                                    }
                                }
                            }
                        }
                    }
                    if (framework == "engine") {
                        stage("Inferencer Benchmark"){
                            println("==========run inferencer benchmark========")
                            inferencer_config.split(',').each { each_ben_conf ->
                                def ncores_per_instance = each_ben_conf.split(':')[0]
                                def bs = each_ben_conf.split(':')[1]
                                run_inferencer(ncores_per_instance, bs, "dynamic_int8")
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
            //checkReferenceData()
        }
    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // save log files
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: "${framework}*.log,${framework}*.json,${framework}-${model}/**,engine-${model}/**,launcher*/**,*.csv,launcher_summary.log,inferencer_summary.log,summary.log,tuning_info.log,reference_data.json", excludes: null
            fingerprint: true
            if (collect_tuned_model){
                archiveArtifacts artifacts: "${framework}-${model}-tune*", excludes: null
                fingerprint: true
            }
        }
    }
    
}
