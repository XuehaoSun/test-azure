@NonCPS

import groovy.json.*
import hudson.model.*
import jenkins.model.*

def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}
credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'
windows_job = "intel-lpot-validation-windows"
linux_job = "intel-lpot-validation"

def autoCancel = false

sys_lpot_val_credentialsId = "dcf0dff2-03fb-45b0-9e64-5b4db466bee5"

// setting test_title
test_title = "Neural Compressor Tests"
if ('test_title' in params && params.test_title != '') {
    test_title = params.test_title
}
echo "Running named ${test_title}"

conda_env_mode = "pypi"
if ('conda_env_mode' in params && params.conda_env_mode != '') {
    conda_env_mode = params.conda_env_mode
}
echo "Running test on ${conda_env_mode}"

// setting node_label
node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

// setting node_label
sub_node_label = "lpot"
if ('sub_node_label' in params && params.sub_node_label != '') {
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

// setting pytorch oob models
pytorch_oob_models = ""
if ('pytorch_oob_models' in params && params.pytorch_oob_models != '') {
    pytorch_oob_models = params.pytorch_oob_models
}
echo "pytorch_oob_models: ${pytorch_oob_models}"

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

lpot_url="https://github.com/intel-innersource/frameworks.ai.lpot.intel-lpot.git"
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

RUN_SPELLCHECK=false
if ('RUN_SPELLCHECK' in params && params.RUN_SPELLCHECK){
    echo "RUN_SPELLCHECK is true"
    RUN_SPELLCHECK=params.RUN_SPELLCHECK
}
echo "RUN_SPELLCHECK = ${RUN_SPELLCHECK}"

COUNT_CODE_LINES=false
if ('COUNT_CODE_LINES' in params && params.COUNT_CODE_LINES){
    echo "COUNT_CODE_LINES is true"
    COUNT_CODE_LINES=params.COUNT_CODE_LINES
}
echo "COUNT_CODE_LINES = ${COUNT_CODE_LINES}"

// set ut extension test for tensorflow
ut_extension_tensorflows=''
if (params.ut_extension_tensorflows != null) {
    ut_extension_tensorflows = params.ut_extension_tensorflows
}
echo "ut_extension_tensorflows: ${ut_extension_tensorflows}"

// set ut extension test for pytorch
ut_extension_pytorch=''
if (params.ut_extension_pytorch != null) {
    ut_extension_pytorch = params.ut_extension_pytorch
}
echo "ut_extension_pytorch: ${ut_extension_pytorch}"

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
    EXCEL_REPORT=params.EXCEL_REPORT
}
echo "EXCEL_REPORT is ${EXCEL_REPORT}"

ABORT_DUPLICATE_TEST = false
if (params.ABORT_DUPLICATE_TEST != null){
    ABORT_DUPLICATE_TEST=params.ABORT_DUPLICATE_TEST
}
echo "ABORT_DUPLICATE_TEST is ${ABORT_DUPLICATE_TEST}"

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

// setting tensorflow_oob_models_windows
tensorflow_oob_models_windows = ""
if ('tensorflow_oob_models_windows' in params && params.tensorflow_oob_models_windows != '') {
    tensorflow_oob_models_windows = params.tensorflow_oob_models_windows
}
echo "tensorflow_oob_models_windows: ${tensorflow_oob_models_windows}"

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

pypi_version='default'
lpot_branch = ''
// pass down commit instead of branch, the unify the test commit.
lpot_commit = ''
PR_source_branch = ''
PR_target_branch = ''
if ('lpot_branch' in params && params.lpot_branch != '') {
   lpot_branch = params.lpot_branch
}else{
    PR_source_branch = params.GITHUB_PR_SOURCE_BRANCH
    PR_target_branch = params.GITHUB_PR_TARGET_BRANCH
}
echo "lpot_branch: $lpot_branch"
echo "PR_source_branch: $PR_source_branch"
echo "PR_target_branch: $PR_target_branch"

ActualCommitAuthorEmail=''
TriggerAuthorEmail=''
ghprbActualCommit=''
ghprbPullLink=''
ghprbPullId=''
ghprbSourceBranch=''
if ( PR_source_branch != '') {
    // githubPRComment comment: "Pipeline started: [Job-${BUILD_NUMBER}](${BUILD_URL})"
    ActualCommitAuthorEmail=env.GITHUB_PR_AUTHOR_EMAIL
    TriggerAuthorEmail=env.GITHUB_PR_TRIGGER_SENDER_EMAIL
    ghprbSourceBranch=env.GITHUB_PR_SOURCE_BRANCH
    ghprbActualCommit=env.GITHUB_PR_HEAD_SHA
    ghprbPullLink=env.GITHUB_PR_URL
    ghprbPullId=env.GITHUB_PR_NUMBER

    echo "ActualCommitAuthorEmail: ${env.GITHUB_PR_AUTHOR_EMAIL}"
    echo "TriggerAuthorEmail: ${env.GITHUB_PR_TRIGGER_SENDER_EMAIL}"
    echo "ghprbActualCommit: ${env.GITHUB_PR_HEAD_SHA}"
    echo "ghprbPullLink: ${env.GITHUB_PR_URL}"
    echo "ghprbPullId: ${env.GITHUB_PR_NUMBER}"
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
if ( PR_source_branch != ''){
    test_mode = 'mr'
    email_subject="PR${ghprbPullId}: ${test_title}"
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

mode  = 'accuracy,throughput'
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
tf_binary_build_job = ""
if ('tf_binary_build_job' in params && params.tf_binary_build_job != ''){
    tf_binary_build_job=params.tf_binary_build_job
}
echo "tf_binary_build_job: ${tf_binary_build_job}"

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

feature_list = ""
if ("feature_list" in params && params.feature_list != "") {
    feature_list = params.feature_list
}
echo "feature_list: ${feature_list}"

collect_tuned_model=false
if (params.collect_tuned_model != null){
    collect_tuned_model=params.collect_tuned_model
}
echo "collect_tuned_model = ${collect_tuned_model}"

upload_nightly_binary=false
if (params.upload_nightly_binary != null){
    upload_nightly_binary=params.upload_nightly_binary
}
echo "upload_nightly_binary = ${upload_nightly_binary}"

upstream_nightly_source=false
if (params.upstream_nightly_source != null){
    upstream_nightly_source=params.upstream_nightly_source
}
echo "upstream_nightly_source = ${upstream_nightly_source}"

precision = 'int8,fp32'
if ('precision' in params && params.precision != '') {
    precision = params.precision
}
echo "Precision: ${precision}"

format_scan_only=false
if (params.format_scan_only != null){
    format_scan_only=params.format_scan_only
}
echo "format_scan_only = ${format_scan_only}"

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

log_level="DEBUG"
if ('log_level' in params && params.log_level != ''){
    log_level=params.log_level
}
echo "log_level: ${log_level}"

def updateGithubCommitStatus(String state, String description) {
    try {
        supportedStatuses = ["error", "failure", "pending", "success"]
        if (!supportedStatuses.contains(state)) {
            error("Unknown status: ${state}")
        }
        withCredentials([string(credentialsId: sys_lpot_val_credentialsId, variable: 'LPOT_VAL_GH_TOKEN')]) {
            withEnv([
            "commit_sha=${env.GITHUB_PR_HEAD_SHA}",
            "state=${state}",
            "description=${description}"
            ]) {
                sh """#!/bin/bash -x
                    curl \
                    -X POST \
                    -H \"Accept: application/vnd.github.v3+json\" \
                    -H \"Authorization: Bearer $LPOT_VAL_GH_TOKEN\" \
                    --proxy proxy-prc.intel.com:913 \
                    https://api.github.com/repos/intel-innersource/frameworks.ai.lpot.intel-lpot/statuses/${commit_sha} \
                    -d '{\"state\": \"${state}\", \"context\": \"Jenkins CI\", \"target_url\": \"${RUN_DISPLAY_URL}\", \"description\": \"${description}\"}'
                """
            }
        }
    } catch (e) {
        println("Could not set status \"${state}\" for ${env.GITHUB_PR_HEAD_SHA} commit.")
        currentBuild.result = "FAILURE"
        error(e.toString())
    }
}

def createGithubIssueComment(String comment) {
    try {
        withCredentials([string(credentialsId: sys_lpot_val_credentialsId, variable: 'LPOT_VAL_GH_TOKEN')]) {
            withEnv([
            "issueNumber=${env.GITHUB_PR_NUMBER}",
            "comment=${comment}",
            ]) {
                sh """#!/bin/bash -x
                    curl \
                    -X POST \
                    -H \"Accept: application/vnd.github.v3+json\" \
                    -H \"Authorization: Bearer $LPOT_VAL_GH_TOKEN\" \
                    --proxy proxy-prc.intel.com:913 \
                    https://api.github.com/repos/intel-innersource/frameworks.ai.lpot.intel-lpot/issues/${issueNumber}/comments \
                    -d '{\"body\": \"${comment}\"}'
                """
            }
        }
    } catch (e) {
        println("Could not add comment for PR #${env.GITHUB_PR_NUMBER}")
        currentBuild.result = "FAILURE"
        println("ERROR\n" + e.toString())
        error(e.toString())
    }
}


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
    }  // catch

}

def download() {
    retry(5) {
        if(PR_source_branch != ''){
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${PR_source_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                            [$class: 'CloneOption', timeout: 5],
                            [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${PR_target_branch}"]]
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

def BuildParams(job_framework, job_model, perf_bs, python_version, strategy, cpu, os){

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
    ParamsPerJob += string(name: "lpot_branch", value: "${lpot_commit}")
    ParamsPerJob += string(name: "MR_source_branch", value: "${PR_source_branch}")
    ParamsPerJob += string(name: "MR_target_branch", value: "${PR_target_branch}")
    ParamsPerJob += string(name: "python_version", value: "${python_version}")
    ParamsPerJob += string(name: "strategy", value: "${strategy}")
    ParamsPerJob += string(name: "test_mode", value: "${test_mode}")
    ParamsPerJob += string(name: "binary_build_job", value: "${binary_build_job}")
    ParamsPerJob += string(name: "tf_binary_build_job", value: "${tf_binary_build_job}")
    ParamsPerJob += string(name: "mode", value: "${pass_mode}")
    ParamsPerJob += string(name: "perf_bs", value: "${perf_bs}")
    ParamsPerJob += booleanParam(name: "multi_instance", value: multi_instance)
    ParamsPerJob += string(name: "tuning_timeout", value: "${tuning_timeout}")
    ParamsPerJob += string(name: "max_trials", value: "${max_trials}")
    ParamsPerJob += booleanParam(name: "tune_only", value: tune_only)
    ParamsPerJob += booleanParam(name: "RUN_PROFILING", value: RUN_PROFILING)
    ParamsPerJob += string(name: "val_branch", value: "${val_branch}")
    ParamsPerJob += string(name: "cpu", value: "${cpu}")
    ParamsPerJob += string(name: "os", value: "${os}")
    ParamsPerJob += string(name: "dataset_prefix", value: "${dataset_prefix}")
    ParamsPerJob += string(name: "refer_build", value: "${refer_build}")
    ParamsPerJob += booleanParam(name: "collect_tuned_model", value: collect_tuned_model)
    ParamsPerJob += string(name: "precision", value: "${precision}")
    ParamsPerJob += string(name: "conda_env_mode", value: "${conda_env_mode}")
    ParamsPerJob += string(name: "log_level", value: "${log_level}")

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
                    pt_oob_models = parseStrToList(pytorch_oob_models)
                    job_models = parseStrToList(pytorch_models)
                    if (system == "windows") {
                        job_models = parseStrToList(pytorch_models_windows)
                    }
                    job_models = job_models.plus(pt_oob_models)

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
                if (PR_source_branch != '' && system == "linux"){
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

                        downstreamJob = build job: sub_jenkins_job, propagate: false, parameters: BuildParams(job_framework, job_model, perf_bs, python_version, strategy, cpu, system)

                        catchError {
                            copyArtifacts(
                                    projectName: sub_jenkins_job,
                                    selector: specific("${downstreamJob.getNumber()}"),
                                    filter: "*.log, tuning_config.yaml, ${job_framework}*.json",
                                    fingerprintArtifacts: true,
                                    target: "${system}/${job_framework}/${job_model}",
                                    optional: true)
                            if (collect_tuned_model){
                                copyArtifacts(
                                        projectName: sub_jenkins_job,
                                        selector: specific("${downstreamJob.getNumber()}"),
                                        filter: "${job_framework}-${job_model}-tune*, ${job_framework}-${job_model}-tune/**",
                                        fingerprintArtifacts: true,
                                        target: "${job_framework}/tuned_model",
                                        optional: true)
                            }

                            // Archive in Jenkins
                            archiveArtifacts artifacts: "${system}/${job_framework}/${job_model}/**", allowEmptyArchive: true
                        }

                        downstreamJobStatus = downstreamJob.result
                        def failed_build_result = downstreamJob.result
                        def failed_build_url = downstreamJob.absoluteUrl

                        if (failed_build_result != 'SUCCESS') {
                            catchError(buildResult: 'FAILURE', stageResult: 'FAILURE'){
                                sh " tail -n 50 ${system}/${job_framework}/${job_model}/*.log > ${WORKSPACE}/details.failed.build 2>&1 "
                                failed_build_detail = readFile file: "${WORKSPACE}/details.failed.build"
                                error("---- ${cpu}_${system}_${job_framework}_${job_model} got failed! ---- Details in ${failed_build_url}consoleText! ---- \n ${failed_build_detail}")
                            }
                        }
                    }
                }
            }
        }
    }
    if (PR_source_branch != ''|| pipeline_failFast) {
        echo "enable failFast"
        jobs.failFast = true
    }
    return jobs
}

def codeScan(tool) {
    List codeScanParams = [
        string(name: "TOOL", value: "${tool}"),
        string(name: "lpot_url", value: "${lpot_url}"),
        string(name: "lpot_branch", value: "${lpot_commit}"),
        string(name: "MR_source_branch", value: "${PR_source_branch}"),
        string(name: "MR_target_branch", value: "${PR_target_branch}"),
        string(name: "val_branch", value: "${val_branch}"),
        string(name: "python_version", value: "${python_version}")
    ]

    downstreamJob = build job: "intel-lpot-format-scan", propagate: false, parameters: codeScanParams

    copyArtifacts(
        projectName: "intel-lpot-format-scan",
        selector: specific("${downstreamJob.getNumber()}"),
        filter: '*.json,*.log,*.csv',
        fingerprintArtifacts: true,
        target: "format_scan",
        optional: true)

    if (tool != "cloc") {
        text_comment = readFile file: "${overview_log}"
        writeFile file: "${overview_log}", text: text_comment + "intel-lpot-format-scan," + tool + "," + downstreamJob.result + "," + downstreamJob.number + "\n"
    }

    // Archive in Jenkins
    archiveArtifacts artifacts: "format_scan/**", allowEmptyArchive: true
    
    if (downstreamJob.result != 'SUCCESS') {
        currentBuild.result = "FAILURE"
        if (PR_source_branch != '') {
            error("${tool} scan failed!")
        }
    }
}

def copyrightCheck() {
    List copyrightCheckParams = [
            string(name: "lpot_url", value: "${lpot_url}"),
            string(name: "MR_source_branch", value: "${PR_source_branch}"),
            string(name: "MR_target_branch", value: "${PR_target_branch}"),
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
        if (PR_source_branch != '') {
            error("Copyright check failed!")
        }
    }
}

def featureTests() {
    List featureTestsParams = [
            string(name: "lpot_url", value: "${lpot_url}"),
            string(name: "lpot_branch", value: "${lpot_commit}"),
            string(name: "MR_source_branch", value: "${PR_source_branch}"),
            string(name: "MR_target_branch", value: "${PR_target_branch}"),
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
        if (PR_source_branch != '') {
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
                    pt_oob_models = parseStrToList(pytorch_oob_models)
                    job_models = parseStrToList(pytorch_models)
                     if (system == "windows") {
                        job_models = parseStrToList(pytorch_models_windows)
                    }
                    job_models = job_models.plus(pt_oob_models)
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

                if (PR_source_branch != ''){
                    add_models_list = collectModelList(job_framework)
                    job_models = job_models.plus(add_models_list)
                    job_models.unique()
                }

                job_models.each { job_model ->
                    echo "-------- ${cpu} - ${system} - ${job_framework} - ${job_model} --------"

                     // Generate tuning info log

                    sh """#!/bin/bash -x
                        if [[ -f ${WORKSPACE}/${system}/${job_framework}/${job_model}/tuning_info.log ]]; then
                            cat ${WORKSPACE}/${system}/${job_framework}/${job_model}/tuning_info.log >> ${WORKSPACE}/tuning_info.log
                        else
                            echo "${system};Unknown;${job_framework};N/A;${job_model};basic;;;${RUN_DISPLAY_URL};;;" >> ${WORKSPACE}/tuning_info.log
                        fi
                    """

                    // helloworld keras with specific log collection in tuning mode
                    if (job_model == "helloworld_keras") {
                        return
                    }

                    echo "Getting results for ${job_framework} - ${job_model}"
                    sh """#!/bin/bash -x
                        if [[ -f ${WORKSPACE}/${system}/${job_framework}/${job_model}/summary.log ]]; then
                            cat ${WORKSPACE}/${system}/${job_framework}/${job_model}/summary.log >> ${WORKSPACE}/summary.log
                        else
                            echo "${system};Unknown;${job_framework};N/A;INT8;${job_model};Inference;Performance;;;${RUN_DISPLAY_URL}" >> ${WORKSPACE}/summary.log
                            echo "${system};Unknown;${job_framework};N/A;FP32;${job_model};Inference;Performance;;;${RUN_DISPLAY_URL}" >> ${WORKSPACE}/summary.log
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
        def ut_pts = ["${pytorch_version}"]
        ut_ext_tfs = parseStrToList(ut_extension_tensorflows)
        ut_ext_pts = parseStrToList(ut_extension_pytorch)
        ut_tfs = ut_tfs.plus(ut_ext_tfs)
        ut_pts = ut_pts.plus(ut_ext_pts)
        ut_tfs.unique()
        ut_pts.unique()
        ut_tfs.each { tf_version ->
            withEnv(["tf_version=${tf_version}"]){
                sh ''' #!/bin/bash
                   overview_log="${WORKSPACE}/summary_overview.log"
                   if [ $(ls -l | grep -c ${tf_version}) != 0 ]; then
                     ut_log_name=ut_tf_${tf_version}_pt_${pytorch_version}.log
                     [[ ! -f $ut_log_name ]] && ut_log_name=`ls -a | grep -E "ut_tf_${tf_version}_pt_([0-9]+.){2}[0-9]+(.cpu)?.log" | head -1`
                     pt_version=`echo -e "${ut_log_name}" | grep -Po "pt_([0-9]+.){2}[0-9]+(.cpu)?" | awk -F "_" '{print $2}'`
                     if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
                       ut_status='FAILURE'
                     else
                       ut_status='SUCCESS'
                     fi
                     echo "unit_test_with_TF${tf_version},${ut_status},${BUILD_URL}artifact/unittest/ut_tf_${tf_version}_pt_${pt_version}.log" | tee -a ${overview_log}
                   fi
                '''
            }
        }
        ut_pts.each { pt_version ->
            withEnv(["pt_version=${pt_version}"]){
                sh ''' #!/bin/bash
                   overview_log="${WORKSPACE}/summary_overview.log"
                   if [ $(ls -l | grep -c ${pt_version}) != 0 ]; then
                     pt_version_tmp=${pt_version%+*}
                     ut_log_name=ut_tf_${tensorflow_version}_pt_${pt_version}.log
                     [[ ! -f $ut_log_name ]] && ut_log_name=`ls -a | grep -E "ut_tf_.*_pt_${pt_version_tmp}.*log" | head -1`
                     tf_version=`echo -e "${ut_log_name}" | grep -Po "tf_.*_" | awk -F "_" '{print $2}'`
                     if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
                       ut_status='FAILURE'
                     else
                       ut_status='SUCCESS'
                     fi
                     echo "unit_test_with_PT${pt_version},${ut_status},${BUILD_URL}artifact/unittest/ut_tf_${tf_version}_pt_${pt_version}.log" | tee -a ${overview_log}
                   fi
                '''
            }
        }

    }
}

def UTBuildParams(tf_version, pt_version, run_coverage){

    List ParamsPerJob = []

    ParamsPerJob += string(name: "binary_build_job", value: "${binary_build_job}")
    ParamsPerJob += string(name: "lpot_url", value: "${lpot_url}")
    ParamsPerJob += string(name: "lpot_branch", value: "${lpot_commit}")
    ParamsPerJob += string(name: "MR_source_branch", value: "${PR_source_branch}")
    ParamsPerJob += string(name: "MR_target_branch", value: "${PR_target_branch}")
    ParamsPerJob += string(name: "python_version", value: "${python_version}")
    ParamsPerJob += string(name: "tensorflow_version", value: "${tf_version}")
    ParamsPerJob += string(name: "mxnet_version", value: "${mxnet_version}")
    ParamsPerJob += string(name: "pytorch_version", value: "${pt_version}")
    ParamsPerJob += string(name: "onnx_version", value: "${onnx_version}")
    ParamsPerJob += string(name: "onnxruntime_version", value: "${onnxruntime_version}")
    ParamsPerJob += string(name: "val_branch", value: "${val_branch}")
    ParamsPerJob += booleanParam(name: "run_coverage", value: run_coverage)
    ParamsPerJob += string(name: "conda_env_mode", value: "${conda_env_mode}")

    return ParamsPerJob
}

def unitTestJobs() {

    def ut_jobs = [:]
    def ut_extension_tfs = parseStrToList(ut_extension_tensorflows)
    def ut_extension_pts = parseStrToList(ut_extension_pytorch)

    ut_jobs["main_ut"] = {
        downstreamJob = build job: "lpot-unit-test", propagate: false, parameters: UTBuildParams(tensorflow_version, pytorch_version, RUN_COVERAGE)
        catchError {
            copyArtifacts(
                    projectName: "lpot-unit-test",
                    selector: specific("${downstreamJob.getNumber()}"),
                    filter: '*.log, *.txt, **/coverage_results/**/*, **/coverage_results_base/**/*',
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
            if (lpot_branch == "") {
                // only for PR coverage test
                sh '''
                    cd ${WORKSPACE}/unittest
                    ut_log_name="ut_tf_${tensorflow_version}_pt_${pytorch_version}.log"
                    coverage_detail="${WORKSPACE}/coverage_detail.html"
                    touch ${coverage_detail}
                    bash -x ${WORKSPACE}/lpot-validation/scripts/compare_coverage.sh ${coverage_detail} ${ut_log_name} unit_test_base.log neural_compressor
                    cat ${coverage_detail}
                '''
            }
        }
    }
    if (ut_extension_tensorflows != '' ){
        ut_extension_tfs.eachWithIndex{ ut_extension_tf, i ->
            def pt_version = pytorch_version
            if (ut_extension_pytorch != '' && i < ut_extension_pts.size()){
                    pt_version = ut_extension_pts[i] }
            ut_jobs["${ut_extension_tf}_tf_${pt_version}_pt_extension_ut"] = {
                downstreamJob = build job: "lpot-unit-test", propagate: false, parameters: UTBuildParams(ut_extension_tf, pt_version, false)
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
    if (ut_extension_pts.size() > ut_extension_tfs.size()) {
        for (int i = ut_extension_tfs.size(); i < ut_extension_pts.size(); i++ ){
            def ut_extension_pt = ut_extension_pts[i]
            ut_jobs["${tensorflow_version}_tf_${ut_extension_pt}_pt_extension_ut"] = {
                downstreamJob = build job: "lpot-unit-test", propagate: false, parameters: UTBuildParams(tensorflow_version, ut_extension_pt, false)
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

    if (tensorflow_version == "spr-base" && tf_binary_build_job == ""){
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

    def conda_build_env_name = "inc_binary_build"
    if (conda_env_mode == "conda") {
        conda_build_env_name = "lpot_conda_build"
    }

    def LINUX_BINARY_CLASSES = ""
    def WINDOWS_BINARY_CLASSES = ""

    PLATFORMS.split(";").each { systemConfig ->
        def system = systemConfig.split(":")[0]
        if (system == "linux") {
            LINUX_BINARY_CLASSES = "wheel"
            if(conda_env_mode == "conda") {
                LINUX_BINARY_CLASSES = "conda"
            }
        }
        if (system == "windows") {
            WINDOWS_BINARY_CLASSES = "wheel"
            if(conda_env_mode == "conda") {
                WINDOWS_BINARY_CLASSES = "conda"
            }
        }
    }

    List binaryBuildParams = [
            string(name: "inc_url", value: "${lpot_url}"),
            string(name: "inc_branch", value: "${lpot_commit}"),
            string(name: "PR_source_branch", value: "${PR_source_branch}"),
            string(name: "PR_target_branch", value: "${PR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "conda_env", value: "${conda_build_env_name}"),
            string(name: "pypi_version", value: "${pypi_version}"),
            string(name: "LINUX_BINARY_CLASSES", value: "${LINUX_BINARY_CLASSES}"),
            string(name: "LINUX_PYTHON_VERSIONS", value: "${python_version}"),
            string(name: "WINDOWS_BINARY_CLASSES", value: "${WINDOWS_BINARY_CLASSES}"),
            string(name: "WINDOWS_PYTHON_VERSIONS", value: "${python_version}"),
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

def generateReport() {
    if(refer_build != 'x0') {
        def refer_job_name
        if(test_mode == "extension") {
            refer_job_name = "intel-lpot-validation-top-weekly"
        }else if(test_mode == "mr" && tensorflow_version != "spr-base"){
            refer_job_name = "intel-lpot-validation-top-PR"
        }else{
            refer_job_name = currentBuild.projectName
        }
        try{
            copyArtifacts(
                projectName: refer_job_name,
                selector: specific("${refer_build}"),
                filter: 'summary.log,tuning_info.log',
                fingerprintArtifacts: true,
                target: "reference")
        } catch(err) {
            println("Copy reference artifact failed, try make up an empty one")
            withEnv(["tuneLogLast=${tuneLogLast}", "summaryLogLast=${summaryLogLast}"]){
            sh '''#!/bin/bash -x
                if [[ ! -f ${tuneLogLast} ]]; then
                    [[ ! -d ${WORKSPACE}/reference ]] && sudo mkdir ${WORKSPACE}/reference
                    touch ${tuneLogLast}
                fi
                if [[ ! -f ${summaryLogLast} ]]; then
                    [[ ! -d ${WORKSPACE}/reference ]] && sudo mkdir ${WORKSPACE}/reference
                    touch ${summaryLogLast}
                fi
            '''
            }
        }
    }

    dir(WORKSPACE) {
        def Jenkins_job_status = currentBuild.result
        println("Jenkins_job_status ==== " + Jenkins_job_status)
        if (Jenkins_job_status == null){
            Jenkins_job_status = "CHECK"
        }
        withEnv([
            "qtools_branch=${lpot_branch}",
            "qtools_commit=${lpot_commit}",
            "summaryLog=${SUMMARYTXT}",
            "summaryLogLast=${summaryLogLast}",
            "tuneLog=${TUNETXT}",
            "tuneLogLast=${tuneLogLast}",
            "overview_log=${overview_log}",
            "coverage_summary=${coverage_summary}",
            "coverage_summary_base=${coverage_summary_base}",
            "Jenkins_job_status=${Jenkins_job_status}",
            "feature_tests_summary=${WORKSPACE}/featureTests/summary.log",
            "ghprbActualCommit=${ghprbActualCommit}",
            "ghprbPullLink=${ghprbPullLink}",
            "ghprbPullId=${ghprbPullId}",
            "MR_source_branch=${PR_source_branch}",
            "MR_target_branch=${PR_target_branch}",
            "nc_code_lines_summary=${WORKSPACE}/format_scan/nc_code_lines_summary.csv",
            "engine_code_lines_summary=${WORKSPACE}/format_scan/engine_code_lines_summary.csv"

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
            python -m pip install -r ./lpot-validation/scripts/report_generator/requirements.txt
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
                --summary-log="${summaryLog}"
        '''
    }
}

def sendReport() {
    dir("$WORKSPACE") {
        if (PR_source_branch != '') {
            recipient_list = ActualCommitAuthorEmail + ',' + TriggerAuthorEmail
            if ('recipient_list' in params && params.recipient_list != '') {
                recipient_list = params.recipient_list + ',' + ActualCommitAuthorEmail + ',' + TriggerAuthorEmail
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

        withEnv(["PR_target_branch=${PR_target_branch}", "framework=${framework}"]) {
            sh (
                    script: 'git --no-pager diff --name-only $(git show-ref -s remotes/origin/${PR_target_branch}) > diff.log',
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
  def jobName = env.JOB_NAME
  def currentBuildNumber = env.BUILD_NUMBER.toInteger()
  def currentJob = Jenkins.instance.getItemByFullName(jobName)
  
  for (def build : currentJob.builds) {
    def buildEnv = build.getEnvironment()
    def buildBranch = buildEnv['GITHUB_PR_SOURCE_BRANCH']
    def buildCommit = buildEnv['GITHUB_PR_HEAD_SHA']
  
    if (build.isBuilding() && (build.number.toInteger() < currentBuildNumber)) {
        if (buildCommit == ghprbActualCommit) {
            currentBuild.result = "ABORTED"
            comment = "Executed test on the same commit. Aborting latest build.: [Job-${BUILD_NUMBER}](${BUILD_URL})"
            return [1, comment]
        } else if (buildBranch == env.GITHUB_PR_SOURCE_BRANCH) {
            echo "Older build ${build.number} Source Branch is ${buildBranch}"
            echo "Older build still queued. Sending kill signal to build number: ${build.number}"
            build.doTerm()
            buildNumber = buildEnv['BUILD_NUMBER']
            buildUrl = buildEnv['BUILD_URL']
            comment = "Previous pipeline has been canceled: [Job-${buildNumber}](${buildUrl})"
            return [2, comment]
            
        }
    }
  }
  return [0, "Nothing to abort."]
}

def uploadNightlyBinary(){
    List binaryBuildParams = [
        string(name: "lpot_url", value: "${lpot_url}"),
        string(name: "val_branch", value: "${val_branch}"),
        string(name: "lpot_branch", value: "${lpot_commit}"),
        string(name: "pypi_version", value: "${pypi_version}")
    ]
    downstreamJob = build job: "lpot-nightly-binary-upload", propagate: false, parameters: binaryBuildParams

    echo "downstreamJob.getResult(): ${downstreamJob.getResult()}"
}

def upstreanNightlySource(){
    sh"""#!/bin/bash
        cd lpot-models
        git branch
        git remote -v
        git remote add upstream https://github.com/intel/neural-compressor.git
        git remote -v
        git push upstream HEAD:master
    """
}

node( node_label ) {
    
    if (ABORT_DUPLICATE_TEST && "${PR_source_branch}" != '') {
        stage("Cancel previous builds") {
            (exit_code, message) = cancelPreviousBuilds()
            if (exit_code != 0) {
                createGithubIssueComment(message)
            }
            if (exit_code == 1) {
                error("Executed test on the same commit. Aborting current build.")
            }
        }
    }

    if (PR_source_branch != '') {
        updateGithubCommitStatus("pending", "Waiting for status to be reported")
    }
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
        writeFile file: SUMMARYTXT, text: "OS;Platform;Framework;Version;Precision;Model;Mode;Type;BS;Value;Url\n"
        summaryLogLast = "${WORKSPACE}/reference/summary.log"

        TUNETXT = "${WORKSPACE}/tuning_info.log"
        writeFile file: TUNETXT, text: "OS;Platform;Framework;Version;Model;Strategy;Tune_time\n"
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
        if (lpot_branch != ''){
            lpot_commit = sh (
                    script: 'cd lpot-models && git rev-parse HEAD',
                    returnStdout: true
            ).trim()

            // set env to close duplicate nightly build
            env.INC_COMMIT = lpot_commit
            println("INC_COMMIT = " + env.INC_COMMIT)

            if (ABORT_DUPLICATE_TEST){
                previous_INC_COMMIT = currentBuild.previousBuiltBuild.buildVariables.INC_COMMIT
                if ( env.INC_COMMIT == previous_INC_COMMIT){
                    println("Kill the current Buils --> " + currentBuild.rawBuild.getFullDisplayName())
                    currentBuild.rawBuild.doKill()
                }
            }
        }

        if (upload_nightly_binary){
            base_version=sh(
                    script: 'cd lpot-models/neural_compressor && grep \'__version__\' version.py | awk -F \'\\"\' \'{print $(NF-1)}\'',
                    returnStdout: true
            ).trim()
            date_info=sh(
                    script: 'date +%Y-%m-%d | tr -cd "[0-9]"',
                    returnStdout: true
            ).trim()
            pypi_version = base_version +'dev'+date_info
        }

        if (PR_source_branch != ''){
            sh"""#!/bin/bash
                cd lpot-models
                echo "PR_source_branch: "
                git show-ref -s remotes/origin/${PR_source_branch}
                echo "PR_target_branch: "
                git show-ref -s remotes/origin/${PR_target_branch}
            """
        }

        stage('Build wheel'){
            if (!format_scan_only){
                buildBinary()
            }else{
                echo "Format scan only, don't need to build binary!"
            }
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
        if (RUN_SPELLCHECK) {
            job_list["Spellcheck Scan"] = {
                codeScan("pyspelling")
            }
        }
        if (COUNT_CODE_LINES) {
            job_list["Code Lines Count"] = {
                codeScan("cloc")
            }
        }

        if (CHECK_COPYRIGHT && PR_source_branch != '') {
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

        if (PR_source_branch != ''|| pipeline_failFast) {
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
        if (e.toString() == "org.jenkinsci.plugins.workflow.steps.FlowInterruptedException" && e.getCauses().size() == 0) {
            println("Setting autoCancel flag to true.")
            autoCancel = true
            println("autoCancel: ${autoCancel}")
        }
        error(e.toString())

    } finally {
        if (upload_nightly_binary){
            stage("upload nightly binary"){
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    if (currentBuild.result != 'FAILURE' && currentBuild.result != 'ABORTED') {
                        uploadNightlyBinary()
                    }else{
                        println('Nightly build not succeed, will not push binary.')
                    }
                }
            }
        }

        if (upstream_nightly_source){
            stage("upstream nightly source"){
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    if (currentBuild.result != 'FAILURE' && currentBuild.result != 'ABORTED') {
                        upstreanNightlySource()
                    }else{
                        println('Nightly build not succeed, will not upstream source code.')
                    }
                }
            }
        }

        stage("Collect Logs") {
            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
               if ( Frameworks != '' || Frameworks_windows != '' ){
                   collectLog()
               }
                if (RUN_UT){
                    collectUTLog()
                }

                if (collect_tuned_model){
                    sh (
                            script: 'cp -r ./*/tuned_model /tmp/',
                            returnStdout: true
                    ).trim()
                }
            }
        }

            try {
                stage("Generate report") {
                    generateReport()
                }
            } catch(error) {
                recipient_list = "suyue.chen@intel.com,wenxin.zhang@intel.com"
                emailext attachLog: true, body: "Generate report failed (see ${env.BUILD_URL}): ${error}", subject: "${email_subject}", to: "${recipient_list}"
                throw error
            }finally {
                if (EXCEL_REPORT) {
                    stage("Generate excel report") {
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            retry(3) {
                                generateExcelReport()
                            }
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

        if (PR_source_branch != ''){
            // If default model has perf regression, then fail the job.
            def destFile = new File("${WORKSPACE}/perf_regression.log")
            if (destFile.exists()) {
                currentBuild.result = 'FAILURE'
                println("------------------Default model performance regression!!!!!!!!!!!!!!!!!!!!!!!")
            }
            if (currentBuild.result == 'FAILURE' || currentBuild.result == 'ABORTED') {
                echo "pipeline failed"
                echo "autoCancel: ${autoCancel}"
                if (PR_source_branch != '' && autoCancel) {
                    echo "Build was auto cancelled. Skipping sending status and comment to GitHub."
                    return
                }
                updateGithubCommitStatus("failure", "Pipeline failed!")
                comment = "Pipeline failed! [Job-${BUILD_NUMBER}](${BUILD_URL}) [Test Report](${BUILD_URL}artifact/report.html)"
                createGithubIssueComment(comment)
            } else {
                echo "pipeline success"
                updateGithubCommitStatus("success", "Pipeline success!")
                comment = "Pipeline success! [Job-${BUILD_NUMBER}](${BUILD_URL}) [Test Report](${BUILD_URL}artifact/report.html)"
                createGithubIssueComment(comment)
            }
        }
    }
}
