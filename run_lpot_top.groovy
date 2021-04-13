@NonCPS

import groovy.json.*
import hudson.model.*
import jenkins.model.*

def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}
credential = 'lab_tfbot'
windows_job = "intel-lpot-validation-windows"
linux_job = "intel-lpot-validation"

// setting test_title
test_title = "LPOT Tests"
if ('test_title' in params && params.test_title != '') {
    test_title = params.test_title
}
echo "Running named ${test_title}"

// setting node_label
node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

// setting node_label
sub_node_label = "lpot"
if ('node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${node_label}"

// chose test frameworks tensorflow,mxnet,pytorch,onnxrt
Frameworks = ""
if ('Frameworks' in params && params.Frameworks != '') {
    Frameworks = params.Frameworks
}
echo "Frameworks: ${Frameworks}"

// setting tensorflow_version
tensorflow_version = '2.2.0'
if ('tensorflow_version' in params && params.tensorflow_version != '') {
    tensorflow_version = params.tensorflow_version
}
echo "tensorflow_version: ${tensorflow_version}"

// setting tensorflow models
tensorflow_models = ""
if ('tensorflow_models' in params && params.tensorflow_models != '') {
    tensorflow_models = params.tensorflow_models
}
echo "tensorflow_models: ${tensorflow_models}"

// setting tensorflow_oob_models
tensorflow_oob_models = ""
if ('tensorflow_oob_models' in params && params.tensorflow_oob_models != '') {
    tensorflow_oob_models = params.tensorflow_oob_models
}
echo "tensorflow_oob_models: ${tensorflow_oob_models}"

// setting mxnet_version
mxnet_version = '1.7.0'
if ('mxnet_version' in params && params.mxnet_version != '') {
    mxnet_version = params.mxnet_version
}
echo "mxnet_version: ${mxnet_version}"

// setting mxnet models
mxnet_models = ""
if ('mxnet_models' in params && params.mxnet_models != '') {
    mxnet_models = params.mxnet_models
}
echo "mxnet_models: ${mxnet_models}"

// setting pytorch_version
pytorch_version = '1.5.0+cpu'
if ('pytorch_version' in params && params.pytorch_version != '') {
    pytorch_version = params.pytorch_version
}
echo "pytorch_version: ${pytorch_version}"

// setting pytorch models
pytorch_models = ""
if ('pytorch_models' in params && params.pytorch_models != '') {
    pytorch_models = params.pytorch_models
}
echo "pytorch_models: ${pytorch_models}"

// setting onnx_version
onnx_version = '1.7.0'
if ('onnx_version' in params && params.onnx_version != '') {
    onnx_version = params.onnx_version
}
echo "onnx_version: ${onnx_version}"

// setting onnxruntime version
onnxruntime_version = '1.5.2'
if ('onnxruntime_version' in params && params.onnxruntime_version != '') {
    onnxruntime_version = params.onnxruntime_version
}
println("onnxruntime_version: " + onnxruntime_version)

// setting onnx models
onnxrt_models = ""
if ('onnxrt_models' in params && params.onnxrt_models != '') {
    onnxrt_models = params.onnxrt_models
}
echo "onnxrt_models: ${onnxrt_models}"

lpot_url="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

RUN_PYLINT=false
if ('RUN_PYLINT' in params && params.RUN_PYLINT){
    echo "RUN_PYLINT is true"
    RUN_PYLINT=params.RUN_PYLINT
}
echo "RUN_PYLINT = ${RUN_PYLINT}"

RUN_BANDIT=false
if ('RUN_BANDIT' in params && params.RUN_BANDIT){
    echo "RUN_BANDIT is true"
    RUN_BANDIT=params.RUN_BANDIT
}
echo "RUN_BANDIT = ${RUN_BANDIT}"

RUN_UT=true
if (params.RUN_UT != null){
    RUN_UT=params.RUN_UT
}
echo "RUN_UT = ${RUN_UT}"

// set ut extension test
ut_extension_tensorflows='1.15.2,1.15UP2'
if (params.ut_extension_tensorflows != null) {
    ut_extension_tensorflows = params.ut_extension_tensorflows
}
echo "ut_extension_tensorflows: ${ut_extension_tensorflows}"

RUN_COVERAGE=true
if (params.RUN_COVERAGE != null){
    RUN_COVERAGE=params.RUN_COVERAGE
}
echo "RUN_COVERAGE = ${RUN_COVERAGE}"

CHECK_COPYRIGHT=false
if (params.CHECK_COPYRIGHT != null){
    CHECK_COPYRIGHT=params.CHECK_COPYRIGHT
}
echo "CHECK_COPYRIGHT = ${CHECK_COPYRIGHT}"

EXCEL_REPORT=false
if ('EXCEL_REPORT' in params && params.EXCEL_REPORT){
    echo "EXCEL_REPORT is true"
    EXCEL_REPORT=params.EXCEL_REPORT
}

ABORT_DUPLICATE_MR = false
if ('ABORT_DUPLICATE_MR' in params && params.ABORT_DUPLICATE_MR){
    ABORT_DUPLICATE_MR=params.ABORT_DUPLICATE_MR
}
echo "ABORT_DUPLICATE_MR is ${ABORT_DUPLICATE_MR}"

FEATURE_TESTS=false
if (params.FEATURE_TESTS != null){
    FEATURE_TESTS=params.FEATURE_TESTS
}
echo "FEATURE_TESTS = ${FEATURE_TESTS}"

// Platforms specification pattern: "os1:cpu_name1,cpuname_2;os2:cpu_name1,cpuname_3"
PLATFORMS = "linux:*"
if ('PLATFORMS' in params && params.PLATFORMS != ''){
    PLATFORMS = params.PLATFORMS
}
echo "PLATFORMS: ${PLATFORMS}"

// parameters to support windows measurement
// chose windows test frameworks tensorflow,mxnet,pytorch,onnxrt
Frameworks_windows = ""
if ('Frameworks_windows' in params && params.Frameworks_windows != '') {
    Frameworks_windows = params.Frameworks_windows
}
echo "Frameworks_windows: ${Frameworks_windows}"

// setting tensorflow_models_windows
tensorflow_models_windows = ""
if ('tensorflow_models_windows' in params && params.tensorflow_models_windows != '') {
    tensorflow_models_windows = params.tensorflow_models_windows
}
echo "tensorflow_models_windows: ${tensorflow_models_windows}"

// setting mxnet_models_windows
mxnet_models_windows = ""
if ('mxnet_models_windows' in params && params.mxnet_models_windows != '') {
    mxnet_models_windows = params.mxnet_models_windows
}
echo "mxnet_models_windows: ${mxnet_models_windows}"

// setting pytorch_models_windows
pytorch_models_windows = ""
if ('pytorch_models_windows' in params && params.pytorch_models_windows != '') {
    pytorch_models_windows = params.pytorch_models_windows
}
echo "pytorch_models_windows: ${pytorch_models_windows}"

// setting onnxrt_models_windows
onnxrt_models_windows = ""
if ('onnxrt_models_windows' in params && params.onnxrt_models_windows != '') {
    onnxrt_models_windows = params.onnxrt_models_windows
}
echo "onnxrt_models_windows: ${onnxrt_models_windows}"

lpot_branch = ''
MR_source_branch = ''
MR_target_branch = ''
if ('lpot_branch' in params && params.lpot_branch != '') {
    lpot_branch = params.lpot_branch
}else{
    if ("${gitlabSourceBranch}" != '') {
        MR_source_branch = "${gitlabSourceBranch}"
        MR_target_branch = "${gitlabTargetBranch}"
        updateGitlabCommitStatus state: 'pending'
        addGitLabMRComment comment: "Pipeline started: [Job-${BUILD_NUMBER}](${BUILD_URL})"
        gitLabConnection('gitlab.devtools.intel.com')
    }
}
echo "lpot_branch: $lpot_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

if ( MR_source_branch != '') {
    echo "gitlabMergeRequestLastCommit: $gitlabMergeRequestLastCommit"
}

// setting refer_build
refer_build = "x0"
if ('refer_build' in params && params.refer_build != '') {
    refer_build = params.refer_build
}
echo "Running ${refer_build}"

test_mode = 'nightly'
if ('test_mode' in params && params.test_mode != ''){
    test_mode = params.test_mode
}
if ( MR_source_branch != ''){
    test_mode = 'mr'
    email_subject="MR${gitlabMergeRequestIid}: ${test_title}"
}else if (test_mode == 'weekly'){
    email_subject="Weekly: ${test_title}"
    currentBuild.description = params.weekly_description
}else if (test_mode == 'nightly') {
    email_subject="Nightly: ${test_title}"
}else{
    email_subject="${test_title}"
}
echo "test_mode: ${test_mode}"
echo "email_subject: $email_subject"

python_version = "3.6"
if ('python_version' in params && params.python_version != '') {
    python_version = params.python_version
}
echo "Python version: ${python_version}"

strategy = "basic"
if ('strategy' in params && params.strategy != '') {
    strategy = params.strategy
}
echo "Strategy: ${strategy}"

mode  = 'accuracy,latency'
if ('mode' in params && params.mode != '') {
    mode = params.mode
}
echo "Mode: ${mode}"

tuning_timeout="10800"
if ('tuning_timeout' in params && params.tuning_timeout != ''){
    tuning_timeout=params.tuning_timeout
}
echo "tuning_timeout: ${tuning_timeout}"

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

pipeline_failFast=false
if (params.pipeline_failFast != null){
    pipeline_failFast=params.pipeline_failFast
}
echo "pipeline_failFast = ${pipeline_failFast}"

RUN_PROFILING=false
if (params.RUN_PROFILING != null){
    RUN_PROFILING=params.RUN_PROFILING
}
echo "RUN_PROFILING = ${RUN_PROFILING}"

binary_build_job = ""

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

dataset_prefix=""
if ('dataset_prefix' in params && params.dataset_prefix != ''){
    dataset_prefix=params.dataset_prefix
}
echo "dataset_prefix: ${dataset_prefix}"

tuning_precision="default"
if ('tuning_precision' in params && params.tuning_precision != ''){
    tuning_precision=params.tuning_precision
}
echo "tuning_precision: ${tuning_precision}"


feature_list = ""
if ("feature_list" in params && params.feature_list != "") {
    feature_list = params.feature_list
}
echo "feature_list: ${feature_list}"


def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        sudo rm -rf *
        git config --global user.email "lab_tfbot@intel.com"
        git config --global user.name "lab_tfbot"
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

def download() {
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

def BuildParams(job_framework, job_model, python_version, strategy, cpu, os){

    framework_version = ''
    if (job_framework == 'tensorflow'){
        framework_version = "${tensorflow_version}"
    }else if (job_framework == 'pytorch'){
        framework_version = "${pytorch_version}"
    }else if (job_framework == 'mxnet'){
        framework_version = "${mxnet_version}"
    }else if (job_framework == 'onnxrt'){
        framework_version = "${onnxruntime_version}"
    }
    println("llsu-----> ${cpu} : ${os} : ${job_framework} : ${framework_version}")

    pass_mode=mode
    println("llsu-----> ${pass_mode}")


    def subnode_label = sub_node_label + " && " + os;

    if (!['any', '*'].contains(cpu)) {
        subnode_label += " && " + cpu
    }


    List ParamsPerJob = []

    ParamsPerJob += string(name: "sub_node_label", value: "${subnode_label}")
    ParamsPerJob += string(name: "framework", value: "${job_framework}")
    ParamsPerJob += string(name: "framework_version", value: "${framework_version}")
    ParamsPerJob += string(name: "onnx_version", value: "${onnx_version}")
    ParamsPerJob += string(name: "model", value: "${job_model}")
    ParamsPerJob += string(name: "lpot_url", value: "${lpot_url}")
    ParamsPerJob += string(name: "lpot_branch", value: "${lpot_branch}")
    ParamsPerJob += string(name: "MR_source_branch", value: "${MR_source_branch}")
    ParamsPerJob += string(name: "MR_target_branch", value: "${MR_target_branch}")
    ParamsPerJob += string(name: "python_version", value: "${python_version}")
    ParamsPerJob += string(name: "strategy", value: "${strategy}")
    ParamsPerJob += string(name: "test_mode", value: "${test_mode}")
    ParamsPerJob += string(name: "binary_build_job", value: "${binary_build_job}")
    ParamsPerJob += string(name: "mode", value: "${pass_mode}")
    ParamsPerJob += string(name: "tuning_timeout", value: "${tuning_timeout}")
    ParamsPerJob += string(name: "max_trials", value: "${max_trials}")
    ParamsPerJob += booleanParam(name: "tune_only", value: tune_only)
    ParamsPerJob += booleanParam(name: "RUN_PROFILING", value: RUN_PROFILING)
    ParamsPerJob += string(name: "val_branch", value: "${val_branch}")
    ParamsPerJob += string(name: "cpu", value: "${cpu}")
    ParamsPerJob += string(name: "os", value: "${os}")
    ParamsPerJob += string(name: "dataset_prefix", value: "${dataset_prefix}")
    ParamsPerJob += string(name: "refer_build", value: "${refer_build}")

    return ParamsPerJob
}

def getPerfJobs() {
    def jobs = [:]
    PLATFORMS.split(";").each { systemConfig ->
        def system = systemConfig.split(":")[0]
        platforms = systemConfig.split(":")[1].split(",")
        platforms.each { platform ->
            def cpu = platform
            // Get frameworks list and sub jenkins job
            job_frameworks = Frameworks.split(',')
            def sub_jenkins_job = linux_job
            if (system == "windows") {
                job_frameworks = Frameworks_windows.split(',')
                sub_jenkins_job = windows_job
            }
            job_frameworks.each { job_framework ->
                // Get models list
                def job_models = []
                if (job_framework == 'tensorflow'){
                    //job_models=eval("${job_framework}_models")
                    tf_oob_models = parseStrToList(tensorflow_oob_models)
                    job_models = parseStrToList(tensorflow_models)
                    if (system == "windows") {
                        tf_oob_models = parseStrToList(tensorflow_oob_models_windows)
                        job_models = parseStrToList(tensorflow_models_windows)
                    }
                    job_models = job_models.plus(tf_oob_models)

                }else if (job_framework == 'pytorch'){
                    job_models = parseStrToList(pytorch_models)
                    if (system == "windows") {
                        job_models = parseStrToList(pytorch_models_windows)
                    }
                }else if (job_framework == 'mxnet'){
                    job_models = parseStrToList(mxnet_models)
                    if (system == "windows") {
                        job_models = parseStrToList(mxnet_models_windows)
                    }
                }else if (job_framework == 'onnxrt'){
                    job_models = parseStrToList(onnxrt_models)
                    if (system == "windows") {
                        job_models = parseStrToList(onnxrt_models_windows)
                    }
                }
                if (MR_source_branch != '' && system == "linux"){
                    add_models_list = collectModelList(job_framework)
                    job_models = job_models.plus(add_models_list)
                    job_models.unique()
                }
                echo "${job_models}"
                echo "llsu-----> ${job_framework}"
                job_models.each { job_model ->
                    jobs["${job_model}_${job_framework}_${system}_${cpu}"] = {

                        // execute build
                        println("${cpu}, ${system}, ${job_framework}, ${job_model}")

                        downstreamJob = build job: sub_jenkins_job, propagate: false, parameters: BuildParams(job_framework, job_model, python_version, strategy, cpu, system)

                        catchError {
                            copyArtifacts(
                                    projectName: sub_jenkins_job,
                                    selector: specific("${downstreamJob.getNumber()}"),
                                    filter: "*.log, tuning_config.yaml, ${job_framework}*.json",
                                    fingerprintArtifacts: true,
                                    target: "${job_framework}/${job_model}",
                                    optional: true)

                            // Archive in Jenkins
                            archiveArtifacts artifacts: "${job_framework}/${job_model}/**", allowEmptyArchive: true
                        }

                        downstreamJobStatus = downstreamJob.result
                        def failed_build_result = downstreamJob.result
                        def failed_build_url = downstreamJob.absoluteUrl

                        if (failed_build_result != 'SUCCESS') {
                            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE'){
                                if (test_mode != 'nightly'){
                                    currentBuild.result = "FAILURE"
                                }
                                sh " tail -n 50 ${job_framework}/${job_model}/*.log > ${WORKSPACE}/details.failed.build 2>&1 "
                                failed_build_detail = readFile file: "${WORKSPACE}/details.failed.build"
                                error("---- ${cpu}_${system}_${job_framework}_${job_model} got failed! ---- Details in ${failed_build_url}consoleText! ---- \n ${failed_build_detail}")
                            }
                        }
                    }
                }
            }
        }
    }
    if (MR_source_branch != ''|| pipeline_failFast) {
        echo "enable failFast"
        jobs.failFast = true
    }
    return jobs
}

def codeScan(tool) {
    List codeScanParams = [
        string(name: "TOOL", value: "${tool}"),
        string(name: "lpot_url", value: "${lpot_url}"),
        string(name: "lpot_branch", value: "${lpot_branch}"),
        string(name: "MR_source_branch", value: "${MR_source_branch}"),
        string(name: "MR_target_branch", value: "${MR_target_branch}"),
        string(name: "val_branch", value: "${val_branch}"),
        string(name: "python_version", value: "${python_version}")
    ]

    downstreamJob = build job: "intel-lpot-format-scan", propagate: false, parameters: codeScanParams

    copyArtifacts(
        projectName: "intel-lpot-format-scan",
        selector: specific("${downstreamJob.getNumber()}"),
        filter: '*.json,*.log',
        fingerprintArtifacts: true,
        target: "format_scan",
        optional: true)

    text_comment = readFile file: "${overview_log}"
    writeFile file: "${overview_log}", text: text_comment + "intel-lpot-format-scan," + tool + "," + downstreamJob.result + "," + downstreamJob.number + "\n"

    // Archive in Jenkins
    archiveArtifacts artifacts: "format_scan/**", allowEmptyArchive: true
    
    if (downstreamJob.result != 'SUCCESS') {
        currentBuild.result = "FAILURE"
        if (MR_source_branch != '') {
            error("${tool} scan failed!")
        }
    }
}

def copyrightCheck() {
    List copyrightCheckParams = [
            string(name: "lpot_url", value: "${lpot_url}"),
            string(name: "MR_source_branch", value: "${MR_source_branch}"),
            string(name: "MR_target_branch", value: "${MR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}")
    ]

    downstreamJob = build job: "intel-lpot-copyright-check", propagate: false, parameters: copyrightCheckParams

    copyArtifacts(
            projectName: "intel-lpot-copyright-check",
            selector: specific("${downstreamJob.getNumber()}"),
            filter: '*.log',
            fingerprintArtifacts: true,
            target: "copyrightCheck",
            optional: true)

    text_comment = readFile file: "${overview_log}"
    writeFile file: "${overview_log}", text: text_comment + "intel-lpot-copyright-check," + downstreamJob.result + "," + downstreamJob.number + "\n"

    // Archive in Jenkins
    archiveArtifacts artifacts: "copyrightCheck/**", allowEmptyArchive: true

    if (downstreamJob.result != 'SUCCESS') {
        currentBuild.result = "FAILURE"
        if (MR_source_branch != '') {
            error("Copyright check failed!")
        }
    }
}

def featureTests() {
    List featureTestsParams = [
            string(name: "lpot_url", value: "${lpot_url}"),
            string(name: "lpot_branch", value: "${lpot_branch}"),
            string(name: "MR_source_branch", value: "${MR_source_branch}"),
            string(name: "MR_target_branch", value: "${MR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "feature_list", value: "${feature_list}")
    ]

    downstreamJob = build job: "lpot-feature-test-top", propagate: false, parameters: featureTestsParams

    copyArtifacts(
            projectName: "lpot-feature-test-top",
            selector: specific("${downstreamJob.getNumber()}"),
            filter: '*.log',
            fingerprintArtifacts: true,
            target: "featureTests",
            optional: true)

    // Archive in Jenkins
    archiveArtifacts artifacts: "featureTests/**", allowEmptyArchive: true

    if (downstreamJob.result != 'SUCCESS') {
        currentBuild.result = "FAILURE"
        if (MR_source_branch != '') {
            error("Feature tests check failed!")
        }
    }
}

def collectLog() {

    echo "---------------------------------------------------------"
    echo "------------  running collectLog  -------------"
    echo "---------------------------------------------------------"

    def steps_print_models = [
        "resnet50v1.5",
        "resnet50v1",
        "inception_v1"
    ]

    PLATFORMS.split(";").each { systemConfig ->
        def system = systemConfig.split(":")[0]
        platforms = systemConfig.split(":")[1].split(",")
        platforms.each { platform ->
            def cpu = platform
            // Get frameworks list
            job_frameworks = Frameworks.split(',')
            if (system == "windows") {
                job_frameworks = Frameworks_windows.split(',')
            }

            job_frameworks.each { job_framework ->
                job_models = []
                if (job_framework == 'tensorflow'){
                    tf_oob_models = parseStrToList(tensorflow_oob_models)
                    job_models = parseStrToList(tensorflow_models)
                    if (system == "windows") {
                        tf_oob_models = parseStrToList(tensorflow_oob_models_windows)
                        job_models = parseStrToList(tensorflow_models_windows)
                    }
                    job_models = job_models.plus(tf_oob_models)
                }else if (job_framework == 'pytorch'){
                    job_models = parseStrToList(pytorch_models)
                     if (system == "windows") {
                        job_models = parseStrToList(pytorch_models_windows)
                    }
                }else if (job_framework == 'mxnet'){
                    job_models = parseStrToList(mxnet_models)
                    if (system == "windows") {
                        job_models = parseStrToList(mxnet_models_windows)
                    }
                }else if (job_framework == 'onnxrt'){
                    job_models = parseStrToList(onnxrt_models)
                    if (system == "windows") {
                        job_models = parseStrToList(onnxrt_models_windows)
                    }
                }

                if (MR_source_branch != ''){
                    add_models_list = collectModelList(job_framework)
                    job_models = job_models.plus(add_models_list)
                    job_models.unique()
                }

                job_models.each { job_model ->
                    echo "-------- ${cpu} - ${system} - ${job_framework} - ${job_model} --------"
    
                     // Generate tuning info log

                    sh """#!/bin/bash -x
                        if [[ -f ${WORKSPACE}/${job_framework}/${job_model}/tuning_info.log ]]; then
                            cat ${WORKSPACE}/${job_framework}/${job_model}/tuning_info.log >> ${WORKSPACE}/tuning_info.log
                        else
                            echo "${system};Unknown;${job_framework};${job_model};basic;;;${RUN_DISPLAY_URL};;;" >> ${WORKSPACE}/tuning_info.log
                        fi
                    """

                    // helloworld keras with specific log collection in tuning mode
                    if (job_model == "helloworld_keras") {
                        return
                    }

                    if (MR_source_branch != '' && steps_print_models.contains(job_model)) {
                        return
                    }

                    echo "Getting results for ${job_framework} - ${job_model}"
                    sh """#!/bin/bash -x
                        if [[ -f ${WORKSPACE}/${job_framework}/${job_model}/summary.log ]]; then
                            cat ${WORKSPACE}/${job_framework}/${job_model}/summary.log >> ${WORKSPACE}/summary.log
                        else
                            echo "${system};Unknown;${job_framework};INT8;${job_model};Inference;Latency;;;${RUN_DISPLAY_URL}" >> ${WORKSPACE}/summary.log
                            echo "${system};Unknown;${job_framework};FP32;${job_model};Inference;Latency;;;${RUN_DISPLAY_URL}" >> ${WORKSPACE}/summary.log
                        fi
                    """
                }
            }
        }
    }
    echo "done running collectLog ......."
    stash allowEmpty: true, includes: "*.log, *.json", name: "logfile"
}

def collectUTLog() {

    echo "------------  running collectUTLog  -------------"
    dir("$WORKSPACE/unittest"){
        def ut_tfs = ["${tensorflow_version}"]
        ut_ext_tfs = parseStrToList(ut_extension_tensorflows)
        ut_tfs = ut_tfs.plus(ut_ext_tfs)
        ut_tfs.unique()
        ut_tfs.each { tf_version ->
            withEnv(["tf_version=${tf_version}"]){
                sh ''' #!/bin/bash
                   overview_log="${WORKSPACE}/summary_overview.log"
                   if [ $(ls -l | grep -c ${tf_version}) != 0 ]; then
                     ut_log_name=$WORKSPACE/unittest/unit_test_${tf_version}.log
                     if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
                       ut_status='FAILURE'
                     else
                       ut_status='SUCCESS'
                     fi
                     echo "unit_test_with_TF${tf_version},${ut_status},${BUILD_URL}artifact/unittest/unit_test_${tf_version}.log" | tee -a ${overview_log}
                   fi
                '''
            }
        }
    }
}

def UTBuildParams(tf_version, run_coverage){

    List ParamsPerJob = []

    ParamsPerJob += string(name: "binary_build_job", value: "${binary_build_job}")
    ParamsPerJob += string(name: "lpot_url", value: "${lpot_url}")
    ParamsPerJob += string(name: "lpot_branch", value: "${lpot_branch}")
    ParamsPerJob += string(name: "MR_source_branch", value: "${MR_source_branch}")
    ParamsPerJob += string(name: "MR_target_branch", value: "${MR_target_branch}")
    ParamsPerJob += string(name: "python_version", value: "${python_version}")
    ParamsPerJob += string(name: "tensorflow_version", value: "${tf_version}")
    ParamsPerJob += string(name: "mxnet_version", value: "${mxnet_version}")
    ParamsPerJob += string(name: "pytorch_version", value: "${pytorch_version}")
    ParamsPerJob += string(name: "onnx_version", value: "${onnx_version}")
    ParamsPerJob += string(name: "onnxruntime_version", value: "${onnxruntime_version}")
    ParamsPerJob += string(name: "val_branch", value: "${val_branch}")
    ParamsPerJob += booleanParam(name: "run_coverage", value: run_coverage)

    return ParamsPerJob
}

def unitTestJobs() {

    def ut_jobs = [:]
    def ut_extension_tfs = parseStrToList(ut_extension_tensorflows)

    ut_jobs["main_ut"] = {
        downstreamJob = build job: "lpot-unit-test", propagate: false, parameters: UTBuildParams(tensorflow_version, RUN_COVERAGE)
        catchError {
            copyArtifacts(
                    projectName: "lpot-unit-test",
                    selector: specific("${downstreamJob.getNumber()}"),
                    filter: '*.log, *.txt, **/coverage_results/**/*',
                    fingerprintArtifacts: true,
                    target: "unittest")

            archiveArtifacts artifacts: "unittest/**", allowEmptyArchive: true
        }

        // Update timestamps of the test reports
        sh '''
            cd ${WORKSPACE}/unittest
            touch *.xml
        '''

        if (downstreamJob.result != 'SUCCESS'){
            currentBuild.result = "FAILURE"
        }

        if (RUN_COVERAGE){
            overview = readFile file: "${overview_log}"
            coverage_status = readFile file: "unittest/coverage_status.txt"
            writeFile file: "${overview_log}", text: overview + coverage_status + "\n"

            // Coverage decrease is not allowed in MRs
            if (lpot_branch == "" && coverage_status.split(",")[1] != "SUCCESS") {
                currentBuild.result = "FAILURE"
            }
        }
    }
    if (ut_extension_tensorflows != ''){
        ut_extension_tfs.each{ ut_extension_tf ->
            ut_jobs["${ut_extension_tf}_extension_ut"] = {
                downstreamJob = build job: "lpot-unit-test", propagate: false, parameters: UTBuildParams(ut_extension_tf, false)
                catchError {
                    copyArtifacts(
                            projectName: "lpot-unit-test",
                            selector: specific("${downstreamJob.getNumber()}"),
                            filter: '*.log, *.txt',
                            fingerprintArtifacts: true,
                            target: "unittest")

                    archiveArtifacts artifacts: "unittest/**", allowEmptyArchive: true
                }

                if (downstreamJob.result != 'SUCCESS'){
                    currentBuild.result = "FAILURE"
                }
            }
        }
    }

    return ut_jobs
}

def buildBinary(){
    List binaryBuildParams = [
            string(name: "lpot_url", value: "${lpot_url}"),
            string(name: "lpot_branch", value: "${lpot_branch}"),
            string(name: "MR_source_branch", value: "${MR_source_branch}"),
            string(name: "MR_target_branch", value: "${MR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "tuning_precision", value: "${tuning_precision}")
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

def generateReport() {
    if(refer_build != 'x0') {
        def refer_job_name
        if(test_mode == "extension"){
            refer_job_name="intel-lpot-validation-top-nightly"
        }else{
            refer_job_name=currentBuild.projectName
        }
        copyArtifacts(
                projectName: refer_job_name,
                selector: specific("${refer_build}"),
                filter: 'summary.log,tuning_info.log',
                fingerprintArtifacts: true,
                target: "reference")
    }

    dir(WORKSPACE) {
        qtools_commit = sh (
            script: 'cd lpot-models && git rev-parse HEAD',
            returnStdout: true
        ).trim()
        def Jenkins_job_status = currentBuild.result
        println("Jenkins_job_status ==== " + Jenkins_job_status)
        if (Jenkins_job_status == null){
            Jenkins_job_status = "CHECK"
        }
        withEnv([
            "qtools_branch=${lpot_branch}",
            "qtools_commit=${qtools_commit}",
            "summaryLog=${SUMMARYTXT}",
            "summaryLogLast=${summaryLogLast}",
            "tuneLog=${TUNETXT}",
            "tuneLogLast=${tuneLogLast}",
            "overview_log=${overview_log}",
            "coverage_summary=${coverage_summary}",
            "coverage_summary_base=${coverage_summary_base}",
            "Jenkins_job_status=${Jenkins_job_status}",
            "feature_tests_summary=${WORKSPACE}/featureTests/summary.log"
        ]) {
            sh '''
                if [[ ${qtools_branch} == '' ]]; then
                    chmod 775 ./lpot-validation/scripts/generate_lpot_report_mr.sh
                    ./lpot-validation/scripts/generate_lpot_report_mr.sh
                else
                    chmod 775 ./lpot-validation/scripts/generate_lpot_report.sh
                    ./lpot-validation/scripts/generate_lpot_report.sh
                fi
            '''
        }
    }
}

def generateExcelReport() {
    withEnv([
        "summaryLog=${SUMMARYTXT}",
        "tuneLog=${TUNETXT}",
    ]) {
        sh '''#!/bin/bash
            set -x

            if [ ! -d "${WORKSPACE}/.lpot-report-generator" ]; then
                python3 -m venv ${WORKSPACE}/.lpot-report-generator
            fi

            source ${WORKSPACE}/.lpot-report-generator/bin/activate

            set +e
            python -m pip install --index-url https://pypi.douban.com/simple -r ./lpot-validation/scripts/report_generator/requirements.txt
            exit_code=$?
            set -e

            if [ $exit_code -ne 0 ]; then
                for requirement in `cat ./lpot-validation/scripts/report_generator/requirements.txt`
                do
                    requirement_whl=$(find ${HOME}/whls  -iname "${requirement}*.whl")
                    if [ ! -z ${requirement_whl} ]; then
                        python -m pip install ${requirement_whl}
                    else
                        echo "Could not found whl file for ${requirement}"
                        exit 1
                    fi
                done
            fi

            python ./lpot-validation/scripts/report_generator/generate_excel_report.py \
                --tuning-log="${tuneLog}" \
                --summary-log="${summaryLog}" \
                --tensorflow-version="${tensorflow_version}" \
                --mxnet-version="${mxnet_version}" \
                --pytorch-version="${pytorch_version}" \
                --onnxruntime-version="${onnxruntime_version}"
        '''
    }
}

def sendReport() {
    dir("$WORKSPACE") {
        if (MR_source_branch != '') {
            recipient_list = "${gitlabUserEmail}"
            if ('recipient_list' in params && params.recipient_list != '') {
                recipient_list = params.recipient_list + ',' + gitlabUserEmail
            }
        } else {
            recipient_list = ''
            if ('recipient_list' in params && params.recipient_list != '') {
                recipient_list = params.recipient_list
            }
        }

        emailext subject: "${email_subject}",
                to: "${recipient_list}",
                replyTo: "${recipient_list}",
                body: '''${FILE,path="report.html"}''',
                attachmentsPattern: "lpot_report.xlsx",
                mimeType: 'text/html'

    }
}

def collectModelList(framework) {
    add_models_list=[]
    dir("$WORKSPACE/lpot-models"){
        def modelconf =  jsonParse(readFile("$WORKSPACE/lpot-validation/config/model_list.json"))

        withEnv(["MR_target_branch=${MR_target_branch}", "framework=${framework}"]) {
            sh (
                    script: 'git --no-pager diff --name-only $(git show-ref -s remotes/origin/${MR_target_branch}) > diff.log',
                    returnStdout: true
            ).trim()
            classes = sh (
                    script: 'echo $(cat diff.log | grep \'examples\' | sed "/README.md/d" | grep "${framework}" | cut -d/ -f3 | sort -u)',
                    returnStdout: true
            ).trim()
        }
        if ( classes != '' ){
            classes_list = classes.split(' ')
            println("classes_list = " + classes_list )
            classes_list.each{ per_class ->
                println("per_class -> " + per_class)
                sub_add_models_list = modelconf."${framework}"."${per_class}"
                if (sub_add_models_list != null) {
                    String dataClass = sub_add_models_list.getClass()
                    if (dataClass != "class java.util.ArrayList") {
                        withEnv(["framework=${framework}", "class=${per_class}"]) {
                            series = sh(
                                    script: 'echo $(cat diff.log | grep \'examples\' | sed "/README.md/d" | grep "${framework}/${class}" | cut -d/ -f4 | sort -u)',
                                    returnStdout: true
                            ).trim()
                        }
                        series_list = series.split(' ')
                        series_list.each { per_series ->
                            println("per_series -> " + per_series)
                            sub_add_models_list = modelconf."${framework}"."${per_class}"."${per_series}"
                            if (sub_add_models_list != null){
                                add_models_list = add_models_list.plus(sub_add_models_list)
                                println("sub_add_models_list = " + sub_add_models_list)
                            }
                        }
                    } else {
                        add_models_list = add_models_list.plus(sub_add_models_list)
                        println("sub_add_models_list = " + sub_add_models_list)
                    }
                }
            }
        }
    }
    println("add_models_list = " + add_models_list)
    return add_models_list
}

def parseStrToList(srtingElements, delimiter=',') {
    if (srtingElements == ''){
        return []
    }
    return srtingElements[0..srtingElements.length()-1].tokenize(delimiter)
}

def cancelPreviousBuilds() {
  echo "Source Branch for this build is: ${gitlabSourceBranch}"
  def jobName = env.JOB_NAME
  def currentBuildNumber = env.BUILD_NUMBER.toInteger()
  def currentJob = Jenkins.instance.getItemByFullName(jobName)

  for (def build : currentJob.builds) {
    def buildBranch = build.getEnvironment()['gitlabSourceBranch']
    def buildCommit = build.getEnvironment()['gitlabMergeRequestLastCommit']

    if (build.isBuilding() && (build.number.toInteger() < currentBuildNumber)) {
        if (buildCommit == gitlabMergeRequestLastCommit) {
            currentBuild.result = "ABORTED"
            addGitLabMRComment comment: "Executed test on the same commit. Aborting latest build.: [Job-${BUILD_NUMBER}](${BUILD_URL})"
            error('Executed test on the same commit. Aborting current build.')
        } else if (buildBranch == gitlabSourceBranch) {
            echo "Older build ${build.number} Source Branch is ${buildBranch}"
            echo "Older build still queued. Sending kill signal to build number: ${build.number}"
            build.doTerm()
            addGitLabMRComment comment: "Previous pipeline has been canceled: [Job-${build.number}](${build.url})"
        }
    }
  }
}

if (ABORT_DUPLICATE_MR && "${MR_source_branch}" != '') {
    stage("Cancel previous builds") {
        cancelPreviousBuilds()
    }
}

node( node_label ) {

    try {
        cleanup()
        dir('lpot-validation') {
            retry(5) {
                checkout scm
            }
        }

        // Setup logs path
        echo "WORKSPACE IS ${WORKSPACE}"
        SUMMARYTXT = "${WORKSPACE}/summary.log"
        writeFile file: SUMMARYTXT, text: "OS;Platform;Framework;Precision;Model;Mode;Type;BS;Value;Url\n"
        summaryLogLast = "${WORKSPACE}/reference/summary.log"

        TUNETXT = "${WORKSPACE}/tuning_info.log"
        writeFile file: TUNETXT, text: "OS;Platform;Framework;Model;Strategy;Tune_time\n"
        tuneLogLast = "${WORKSPACE}/reference/tuning_info.log"

        coverage_summary = "${WORKSPACE}/unittest/coverage_summary.log"
        coverage_summary_base = "${WORKSPACE}/unittest/coverage_summary_base.log"

        // over view log
        overview_log = "${WORKSPACE}/summary_overview.log"
        writeFile file: overview_log,
            text: "Jenkins Job, Build Status, Build ID\n"

        def fw_versions = [
            "tensorflow": tensorflow_version,
            "pytorch": pytorch_version,
            "mxnet": mxnet_version,
            "onnxruntime": onnxruntime_version,
        ]

        writeJSON file: "fw_versions.json", json: fw_versions, pretty: 4
        
        download()

        stage('Build wheel'){
            buildBinary()
        }

        def job_list = [:]
        if (RUN_UT) {
            def ut_jobs = unitTestJobs()
            job_list = job_list + ut_jobs
        }
        if (RUN_PYLINT) {
            job_list["Pylint Scan"] = {
                codeScan("pylint")
            }
        }
        if (RUN_BANDIT) {
            job_list["Bandit Scan"] = {
                codeScan("bandit")
            }
        }
        if (CHECK_COPYRIGHT && MR_source_branch != '') {
            job_list["Copyright Check"] = {
                copyrightCheck()
            }
        }

        if (FEATURE_TESTS && feature_list != '') {
            job_list["Feature tests"] = {
                featureTests()
            }
        }

        if ( Frameworks != '' || Frameworks_windows != '' ){
            def perf_jobs = getPerfJobs()
            job_list = job_list + perf_jobs
        }

        if (MR_source_branch != ''|| pipeline_failFast) {
            echo "enable failFast"
            job_list.failFast = true
        }

        if (job_list.size() > 0) {
            stage("Execute tests") {
                parallel job_list
            }
        }
    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        error(e.toString())

    } finally {
        stage("Collect Logs") {
            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
               if ( Frameworks != '' || Frameworks_windows != '' ){
                   collectLog()
               }
                if (RUN_UT){
                    collectUTLog()
                }
            }
        }

        stage("Generate report") {
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                generateReport()
            }
        }

        if (EXCEL_REPORT) {
            stage("Generate excel report") {
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    retry(5) {
                        generateExcelReport()
                    }
                }
            }
        }

        stage("Send report") {
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                sendReport()
            }
        }
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log, *.html, *.xlsx, *.json', excludes: null, allowEmptyArchive: true
            fingerprint: true
        }

        if (MR_source_branch != ''){
            // If default model has perf regression, then fail the job.
            def destFile = new File("${WORKSPACE}/perf_regression.log")
            if (destFile.exists()) {
                currentBuild.result = 'FAILURE'
                println("------------------Default model performance regression!!!!!!!!!!!!!!!!!!!!!!!")
            }
            if (currentBuild.result == 'FAILURE' || currentBuild.result == 'ABORTED') {
                echo "pipeline failed"
                updateGitlabCommitStatus state: 'failed'
                addGitLabMRComment comment: "Pipeline failed! [Job-${BUILD_NUMBER}](${BUILD_URL}) [Test Report](${BUILD_URL}artifact/report.html)"
            } else {
                echo "pipeline success"
                updateGitlabCommitStatus state: 'success'
                addGitLabMRComment comment: "Pipeline success! [Job-${BUILD_NUMBER}](${BUILD_URL}) [Test Report](${BUILD_URL}artifact/report.html)"
            }
        }
    }
}
