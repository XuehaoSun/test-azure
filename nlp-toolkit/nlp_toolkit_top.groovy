// 1. model test
//   workflow: optimize/deploy
//  (1) optimize(pytorch/tensorflow/onnx): 
//     (a) pytorch: 
//        cleanup -> build binary -> env setup -> quantize -> savemodel(torch) -> benchmark(torch)
//  (2) deploy(nlp_excutor/ipex):
//     (a) nlp_excutor: 
//        cleanup -> build binary -> env setup -> prepare dataset -> prepare model(to onnx) -> onnx_to_ir -> benchmark -> inference 
// 2. unit test
//    target: optimize(include preprocess)/ backend
//   (1) optimize: pytest
//   (2) backend: pytest + gtest
// 3. format check
//   (1) python
//   (2) cpp
//   (3) spell
//   (4) copyright
// 4. collect log
// 5. generate report
////////////////////////////
// test mode: pre-CI/nightly
//  
// params setting
@NonCPS

import groovy.json.*
import hudson.model.*
import jenkins.model.*

credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'
sys_val_credentialsId = "dcf0dff2-03fb-45b0-9e64-5b4db466bee5"
def autoCancel = false
// test mode: pre-CI / nightly / extension
test_mode = "nightly"
if ('test_mode' in params && params.test_mode != '') {
    test_mode = params.test_mode
}
echo "test mode ${test_mode}"
// conda env mode: pypi / conda / source
conda_env_mode = "pypi"
if ('conda_env_mode' in params && params.conda_env_mode != '') {
    conda_env_mode = params.conda_env_mode
}
echo "conda_env_mode ${conda_env_mode}"
// setting node_label
node_label = "master"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
sub_node_label = ""
if ('sub_node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${node_label}"

// set workflow optimize / deploy
workflows = ""
if ('workflows' in params && params.workflows != '') {
    workflows = params.workflows
}
echo "work flows are ${workflows}"
workflows_list = parseStrToList(workflows)

// optimize frameworks tensorflow / pytorch / onnxrt
optimize_frameworks = ""
if ('optimize_frameworks' in params && params.optimize_frameworks != '') {
    optimize_frameworks = params.optimize_frameworks
}
echo "optimize frameworks: ${optimize_frameworks}"

// deploy backends nlp_excutor / ipex
deploy_backends = ""
if ('deploy_backends' in params && params.deploy_backends != '') {
    deploy_backends = params.deploy_backends
}
echo "deploy backends: ${deploy_backends}"

// setting pytorch_version
pytorch_version = '1.10.0+cpu'
if ('pytorch_version' in params && params.pytorch_version != '') {
    pytorch_version = params.pytorch_version
}
echo "pytorch_version: ${pytorch_version}"

// setting onnx_version
onnx_version = '1.9.0'
if ('onnx_version' in params && params.onnx_version != '') {
    onnx_version = params.onnx_version
}
echo "onnx version: ${onnx_version}"

// setting onnxruntime version
onnxruntime_version = '1.10.0'
if ('onnxruntime_version' in params && params.onnxruntime_version != '') {
    onnxruntime_version = params.onnxruntime_version
}
echo "onnxruntime version: ${onnxruntime_version}"

// setting tensorflow_version
tensorflow_version = '2.8.0'
if ('tensorflow_version' in params && params.tensorflow_version != '') {
    tensorflow_version = params.tensorflow_version
}
echo "tensorflow_version: ${tensorflow_version}"

// setting pytorch models for optimize
pytorch_models = ""
if ('pytorch_models' in params && params.pytorch_models != '') {
    pytorch_models = params.pytorch_models
}
echo "pytorch_models: ${pytorch_models}"

// setting nlp_excutor models for deploy
nlp_excutor_models = ''
if ('nlp_excutor_models' in params && params.nlp_excutor_models != '') {
    nlp_excutor_models=params.nlp_excutor_models
}
echo "nlp_excutor_models: ${nlp_excutor_models}"

ipex_models = ""
tensorflow_models = ""
if ('tensorflow_models' in params && params.tensorflow_models != '') {
    tensorflow_models=params.tensorflow_models
}
echo "tensorflow_models: ${tensorflow_models}"
// ncores_per_instance:bs for nlp_excutor inference
inferencer_config = "4:64,4:128,28:1"
if ('inferencer_config' in params && params.inferencer_config != '') {
    inferencer_config=params.inferencer_config
}
echo "inferencer_config: ${inferencer_config}"

//other settings
nlp_url = "https://github.com/intel-innersource/frameworks.ai.nlp-toolkit.intel-nlp-toolkit.git"
if ('nlp_url' in params && params.nlp_url != '') {
    nlp_url = params.nlp_url
}
echo "nlp_url is ${nlp_url}"

// Platforms specification pattern: "os1:cpu_name1,cpuname_2;os2:cpu_name1,cpuname_3"
PLATFORMS = "linux:*"
if ('PLATFORMS' in params && params.PLATFORMS != '') {
    PLATFORMS = params.PLATFORMS
}
echo "PLATFORMS: ${PLATFORMS}"

python_version = "3.7"
if ('python_version' in params && params.python_version != '') {
    python_version = params.python_version
}
echo "Python version: ${python_version}"

mode  = 'accuracy,throughput'
if ('mode' in params && params.mode != '') {
    mode = params.mode
}
echo "Mode: ${mode}"

tuning_timeout = "10800"
if ('tuning_timeout' in params && params.tuning_timeout != '') {
    tuning_timeout=params.tuning_timeout
}
echo "tuning_timeout: ${tuning_timeout}"

tune_only = false
if (params.tune_only != null) {
    tune_only=params.tune_only
}
echo "tune_only = ${tune_only}"

pipeline_failFast = false
if (params.pipeline_failFast != null) {
    pipeline_failFast=params.pipeline_failFast
}
echo "pipeline_failFast = ${pipeline_failFast}"

val_branch = "master"
if ('val_branch' in params && params.val_branch != '') {
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

dataset_prefix = ""
if ('dataset_prefix' in params && params.dataset_prefix != '') {
    dataset_prefix=params.dataset_prefix
}
echo "dataset_prefix: ${dataset_prefix}"

collect_tuned_model = false
if (params.collect_tuned_model != null) {
    collect_tuned_model=params.collect_tuned_model
}
echo "collect_tuned_model = ${collect_tuned_model}"

precision = 'int8,fp32'
if ('precision' in params && params.precision != '') {
    precision = params.precision
}
echo "Precision: ${precision}"

format_scan_only = false
if (params.format_scan_only != null) {
    format_scan_only=params.format_scan_only
}
echo "format_scan_only = ${format_scan_only}"

multi_instance = true
if (params.multi_instance != null) {
    multi_instance = params.multi_instance
}
echo "Multi instance: ${multi_instance}"

log_level = "DEBUG"
if ('log_level' in params && params.log_level != '') {
    log_level=params.log_level
}
echo "log_level: ${log_level}"

RUN_PYLINT = false
if ('RUN_PYLINT' in params && params.RUN_PYLINT) {
    echo "RUN_PYLINT is true"
    RUN_PYLINT=params.RUN_PYLINT
}
echo "RUN_PYLINT = ${RUN_PYLINT}"

RUN_CPPLINT = false
if ('RUN_CPPLINT' in params && params.RUN_CPPLINT) {
    echo "RUN_CPPLINT is true"
    RUN_CPPLINT=params.RUN_CPPLINT
}
echo "RUN_CPPLINT = ${RUN_CPPLINT}"

RUN_BANDIT = false
if ('RUN_BANDIT' in params && params.RUN_BANDIT) {
    echo "RUN_BANDIT is true"
    RUN_BANDIT=params.RUN_BANDIT
}
echo "RUN_BANDIT = ${RUN_BANDIT}"

RUN_UT_OPT = false
if (params.RUN_UT_OPT != null) {
    RUN_UT_OPT=params.RUN_UT_OPT
}
echo "RUN UT OPTIMIZE= ${RUN_UT_OPT}"

// set ut extension test for pytorch
ut_extension_pytorch = ""
if (params.ut_extension_pytorch != null) {
    ut_extension_pytorch = params.ut_extension_pytorch
}
echo "ut_extension_pytorch: ${ut_extension_pytorch}"

RUN_UT_BAK = false
if (params.RUN_UT_BAK != null) {
    RUN_UT_BAK=params.RUN_UT_BAK
}
echo "RUN UT BACKEND = ${RUN_UT_BAK}"

RUN_COVERAGE = true
if (params.RUN_COVERAGE != null) {
    RUN_COVERAGE=params.RUN_COVERAGE
}
echo "RUN_COVERAGE = ${RUN_COVERAGE}"

RUN_SPELLCHECK = false
if ('RUN_SPELLCHECK' in params && params.RUN_SPELLCHECK) {
    echo "RUN_SPELLCHECK is true"
    RUN_SPELLCHECK=params.RUN_SPELLCHECK
}
echo "RUN_SPELLCHECK = ${RUN_SPELLCHECK}"

CHECK_COPYRIGHT = false
if (params.CHECK_COPYRIGHT != null) {
    CHECK_COPYRIGHT=params.CHECK_COPYRIGHT
}
echo "CHECK_COPYRIGHT = ${CHECK_COPYRIGHT}"

EXCEL_REPORT = false
if ('EXCEL_REPORT' in params && params.EXCEL_REPORT) {
    EXCEL_REPORT=params.EXCEL_REPORT
}
echo "EXCEL_REPORT is ${EXCEL_REPORT}"

ABORT_DUPLICATE_TEST = false
if (params.ABORT_DUPLICATE_TEST != null) {
    ABORT_DUPLICATE_TEST=params.ABORT_DUPLICATE_TEST
}
echo "ABORT_DUPLICATE_TEST is ${ABORT_DUPLICATE_TEST}"

/////////
source_branch = ""
target_branch = ""
nlp_branch = ""
nlp_commit = ""
ActualCommitAuthorEmail = ''
TriggerAuthorEmail = ''
ghprbActualCommit = ''
ghprbPullLink = ''
ghprbPullId = ''
ghprbSourceBranch = ''
if (params.nlp_branch != null) {
    nlp_branch = params.nlp_branch
}
echo "nlp_branch: ${nlp_branch}"
if (test_mode == "pre-CI") {
    source_branch = params.GITHUB_PR_SOURCE_BRANCH
    target_branch = params.GITHUB_PR_TARGET_BRANCH
    // githubPRComment comment: "Pipeline started: [Job-${BUILD_NUMBER}](${BUILD_URL})"
    ActualCommitAuthorEmail=env.GITHUB_PR_AUTHOR_EMAIL
    TriggerAuthorEmail=env.GITHUB_PR_TRIGGER_SENDER_EMAIL
    ghprbActualCommit=env.GITHUB_PR_HEAD_SHA
    ghprbPullLink=env.GITHUB_PR_URL
    ghprbPullId=env.GITHUB_PR_NUMBER
    echo "ActualCommitAuthorEmail: ${env.GITHUB_PR_AUTHOR_EMAIL}"
    echo "TriggerAuthorEmail: ${env.GITHUB_PR_TRIGGER_SENDER_EMAIL}"
    echo "ghprbActualCommit: ${env.GITHUB_PR_HEAD_SHA}"
    echo "ghprbPullLink: ${env.GITHUB_PR_URL}"
    echo "ghprbPullId: ${env.GITHUB_PR_NUMBER}"
    test_title = "NLP-TOOLKIT PRE-CI TEST"
    email_subject="PR${ghprbPullId}: ${test_title}"
} else {
    test_title = "NIGHTLY NLP-TOOLKIT TEST"
    email_subject="${test_title}"
}
// if use custom test title
if ('test_title' in params && params.test_title != '') {
    test_title = params.test_title
    email_subject="${test_title}"
}
echo "test title ${test_title}"

pypi_version='default'
install_nlp_toolkit="true"
target_path="./nlp_toolkit"
lpot_url="https://github.com/intel-innersource/frameworks.ai.lpot.intel-lpot.git"
lpot_branch="master"
if ('lpot_branch' in params && params.lpot_branch) {
    lpot_branch=params.lpot_branch
}

def parseStrToList(srtingElements, delimiter=',') {
    if (srtingElements == '') {
        return []
    }
    return srtingElements[0..srtingElements.length()-1].tokenize(delimiter)
}

def updateGithubCommitStatus(String state, String description) {
    try {
        supportedStatuses = ["error", "failure", "pending", "success"]
        if (!supportedStatuses.contains(state)) {
            error("Unknown status: ${state}")
        }
        withCredentials([string(credentialsId: sys_val_credentialsId, variable: 'LPOT_VAL_GH_TOKEN')]) {
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
                    https://api.github.com/repos/intel-innersource/frameworks.ai.nlp-toolkit.intel-nlp-toolkit/statuses/${commit_sha} \
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
        withCredentials([string(credentialsId: sys_val_credentialsId, variable: 'LPOT_VAL_GH_TOKEN')]) {
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
                    https://api.github.com/repos/intel-innersource/frameworks.ai.nlp-toolkit.intel-nlp-toolkit/issues/${issueNumber}/comments \
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
        if (test_mode == "pre-CI") {
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${source_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "nlp-toolkit"],
                            [$class: 'CloneOption', timeout: 5],
                            [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${target_branch}"]]
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
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "nlp-toolkit"],
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
/// choose install from source or pypi ///
def buildBinaryNLP() {
    List binaryBuildParamsNLP = [
        string(name: "python_version", value: "${python_version}"),
        string(name: "nlp_url", value: "${nlp_url}"),
        string(name: "nlp_branch", value: "${nlp_branch}"),
        string(name: "MR_source_branch", value: "${source_branch}"),
        string(name: "MR_target_branch", value: "${target_branch}"),
        string(name: "val_branch", value: "${val_branch}"),
        string(name: "pypi_version", value: "${pypi_version}")
    ]
    if (conda_env_mode == "conda") {
        binaryBuildParamsNLP += string(name: "conda_env", value: "lpot_conda_build")
        binaryBuildParamsNLP += string(name: "binary_class", value: "conda")
    }
    downstreamJob = build job: "nlp-toolkit-release-wheel-build", propagate: false, parameters: binaryBuildParamsNLP
    binary_build_job_nlp = downstreamJob.getNumber()
    echo "binary_build_job_nlp: ${binary_build_job_nlp}"
    echo "downstreamJob.getResult(): ${downstreamJob.getResult()}"
    if (downstreamJob.getResult() != "SUCCESS") {
        currentBuild.result = "FAILURE"
        failed_build_url = downstreamJob.absoluteUrl
        echo "failed_build_url: ${failed_build_url}"
        error("---- nlp wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
    }
}

def buildBinary(){
    List binaryBuildParams = [
            string(name: "python_version", value: "${python_version}"),
            string(name: "lpot_url", value: "${lpot_url}"),
            string(name: "lpot_branch", value: "${lpot_branch}"),
            string(name: "MR_source_branch", value: "${source_branch}"),
            string(name: "MR_target_branch", value: "${target_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "pypi_version", value: "${pypi_version}"),
            string(name: "build_mode", value: "basic")
    ]
    if(conda_env_mode == "conda") {
        binaryBuildParams += string(name: "conda_env", value: "lpot_conda_build")
        binaryBuildParams += string(name: "binary_class", value: "conda")
    }
    downstreamJob = build job: "lpot-release-wheel-build", propagate: false, parameters: binaryBuildParams
    binary_build_job = downstreamJob.getNumber()
    echo "binary_build_job: ${binary_build_job}"
    echo "downstreamJob.getResult(): ${downstreamJob.getResult()}"
    if (downstreamJob.getResult() != "SUCCESS") {
        currentBuild.result = "FAILURE"
        failed_build_url = downstreamJob.absoluteUrl
        echo "failed_build_url: ${failed_build_url}"
        error("---- nlp wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
    }
}

def codeScan(tool) {
    List codeScanParams = [
        string(name: "TOOL", value: "${tool}"),
        string(name: "nlp_url", value: "${nlp_url}"),
        string(name: "nlp_branch", value: "${nlp_commit}"),
        string(name: "MR_source_branch", value: "${source_branch}"),
        string(name: "MR_target_branch", value: "${target_branch}"),
        string(name: "val_branch", value: "${val_branch}"),
        string(name: "python_version", value: "${python_version}")
    ]

    downstreamJob = build job: "nlp-format-scan-localtest", propagate: false, parameters: codeScanParams
    copyArtifacts(
        projectName: "nlp-format-scan-localtest",
        selector: specific("${downstreamJob.getNumber()}"),
        filter: '*.json,*.log,*.csv',
        fingerprintArtifacts: true,
        target: "format_scan",
        optional: true)
    if (tool != "cloc") {
        text_comment = readFile file: "${overviewLog}"
        writeFile file: "${overviewLog}", text: text_comment + "nlp-format-scan-localtest," + tool + "," + downstreamJob.result + "," + downstreamJob.number + "\n"
    }
    // Archive in Jenkins
    archiveArtifacts artifacts: "format_scan/**", allowEmptyArchive: true
    if (downstreamJob.result != 'SUCCESS') {
        currentBuild.result = "FAILURE"
        if (test_mode == "pre-CI") {
            error("${tool} scan failed!")
        }
    }
}

def copyrightCheck() {
    List copyrightCheckParams = [
            string(name: "lpot_url", value: "${nlp_url}"),
            string(name: "MR_source_branch", value: "${source_branch}"),
            string(name: "MR_target_branch", value: "${target_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "target_path", value: "${target_path}")
    ]
    downstreamJob = build job: "intel-lpot-copyright-check", propagate: false, parameters: copyrightCheckParams
    copyArtifacts(
            projectName: "intel-lpot-copyright-check",
            selector: specific("${downstreamJob.getNumber()}"),
            filter: '*.log',
            fingerprintArtifacts: true,
            target: "copyrightCheck",
            optional: true)
    text_comment = readFile file: "${overviewLog}"
    writeFile file: "${overviewLog}", text: text_comment + "nlp-toolkit-copyright-check," + downstreamJob.result + "," + downstreamJob.number + "\n"
    // Archive in Jenkins
    archiveArtifacts artifacts: "copyrightCheck/**", allowEmptyArchive: true
    if (downstreamJob.result != 'SUCCESS') {
        currentBuild.result = "FAILURE"
        if (test_mode == "pre-CI") {
            error("Copyright check failed!")
        }
    }
}

def unitTestBackend(unit_test_mode) {
    def ut_jobs = [:]
    List UTBuildParams = [
        string(name: "nlp_url", value: "${nlp_url}"),
        string(name: "nlp_branch", value: "${nlp_commit}"),
        string(name: "PR_source_branch", value: "${source_branch}"),
        string(name: "PR_target_branch", value: "${target_branch}"),
        string(name: "val_branch", value: "${val_branch}"),
        string(name: "binary_build_job_nlp", value: "${binary_build_job_nlp}"),
        string(name: "binary_build_job", value: "${binary_build_job}"),
        booleanParam(name: "run_coverage", value: RUN_COVERAGE),
        string(name: "unit_test_mode", value: "${unit_test_mode}")
    ]
    ut_jobs[unit_test_mode] = {
        downstreamJob = build job: "nlp-toolkit-backend-ut", propagate: false, parameters: UTBuildParams
        catchError {
            copyArtifacts(
                    projectName: "nlp-toolkit-backend-ut",
                    selector: specific("${downstreamJob.getNumber()}"),
                    filter: '*.log, *.txt, **/coverage_results_backend/**/*, **/coverage_results_base_backend/**/*',
                    fingerprintArtifacts: true,
                    target: "unittest")
            archiveArtifacts artifacts: "unittest/**", allowEmptyArchive: true
        }
        if (downstreamJob.result != 'SUCCESS') {
            def sub_job_url = downstreamJob.absoluteUrl
            withEnv(["sub_job_url=${sub_job_url}", "ut_mode=${unit_test_mode}"]){
                sh '''#!/bin/bash
                overviewLog="${WORKSPACE}/summary_overview.log"
                echo "deep-engine_ut_${ut_mode},FAILURE,${sub_job_url}" | tee -a ${overviewLog}
                '''
            }
            currentBuild.result = "FAILURE"
            error("---${unit_test_mode} test failed---")
        }
        println(RUN_COVERAGE)
        println(unit_test_mode)
        if (RUN_COVERAGE && unit_test_mode == "pytest"){
            overview = readFile file: "${overviewLog}"
            coverage_status_engine = readFile file: "unittest/coverage_status_engine.txt"
            writeFile file: "${overviewLog}", text: overview + coverage_status_engine + "\n"
            // Coverage decrease is not allowed in MRs
            if (test_mode == "pre-CI" && coverage_status_engine.split(",")[1] != "SUCCESS") {
                currentBuild.result = "FAILURE"
            }
        }
    }
    return ut_jobs
}

def unitTestJobsOptimize() {
    def ut_jobs = [:]
    ut_jobs["main_ut"] = {
        downstreamJob = build job: "nlp-toolkit-optimize-ut", propagate: false, parameters: UTBuildParams(tensorflow_version, pytorch_version, RUN_COVERAGE)
        catchError {
            copyArtifacts(
                    projectName: "nlp-toolkit-optimize-ut",
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
        if (downstreamJob.result != 'SUCCESS') {
            currentBuild.result = "FAILURE"
        }
        if (RUN_COVERAGE) {
            overview = readFile file: "${overviewLog}"
            coverage_status = readFile file: "unittest/coverage_status.txt"
            writeFile file: "${overviewLog}", text: overview + coverage_status + "\n"
            // Coverage decrease is not allowed in MRs
            if (test_mode == "pre-CI" && coverage_status.split(",")[1] != "SUCCESS") {
                currentBuild.result = "FAILURE"
            }
        }
    }
    // for extension framework versions
    def ut_extension_pts = parseStrToList(ut_extension_pytorch)
    if (ut_extension_pts != '' ) {
        ut_extension_pts.eachWithIndex{ ut_extension_pt, i ->
            def pt_version = pytorch_version
            if (ut_extension_pytorch != '' && i < ut_extension_pts.size()) {
                pt_version = ut_extension_pts[i] 
            }
            ut_jobs["${ut_extension_pt}_pt_${pt_version}_pt_extension_ut"] = {
                downstreamJob = build job: "nlp-toolkit-optimize-ut", propagate: false, parameters: UTBuildParams(tensorflow_version, pt_version, false)
                catchError {
                    copyArtifacts(
                            projectName: "nlp-toolkit-optimize-ut",
                            selector: specific("${downstreamJob.getNumber()}"),
                            filter: '*.log, *.txt',
                            fingerprintArtifacts: true,
                            target: "unittest")

                    archiveArtifacts artifacts: "unittest/**", allowEmptyArchive: true
                }
                if (downstreamJob.result != 'SUCCESS') {
                    currentBuild.result = "FAILURE"
                }
            }
        }
    }
    return ut_jobs
}

def UTBuildParams(tf_version, pt_version, run_coverage) {
    List ParamsPerJob = []
    ParamsPerJob += string(name: "binary_build_job_nlp", value: "${binary_build_job_nlp}")
    ParamsPerJob += string(name: "binary_build_job", value: "${binary_build_job}")
    ParamsPerJob += string(name: "nlp_url", value: "${nlp_url}")
    ParamsPerJob += string(name: "nlp_branch", value: "${nlp_commit}")
    ParamsPerJob += string(name: "MR_source_branch", value: "${source_branch}")
    ParamsPerJob += string(name: "MR_target_branch", value: "${target_branch}")
    ParamsPerJob += string(name: "python_version", value: "${python_version}")
    ParamsPerJob += string(name: "tensorflow_version", value: "${tf_version}")
    ParamsPerJob += string(name: "pytorch_version", value: "${pt_version}")
    ParamsPerJob += string(name: "onnx_version", value: "${onnx_version}")
    ParamsPerJob += string(name: "onnxruntime_version", value: "${onnxruntime_version}")
    ParamsPerJob += string(name: "val_branch", value: "${val_branch}")
    ParamsPerJob += booleanParam(name: "run_coverage", value: run_coverage)
    ParamsPerJob += string(name: "conda_env_mode", value: "${conda_env_mode}")

    return ParamsPerJob
}

def collectUT_optimize_Log() {
    echo "------------  running collect UT Log of Optimize-------------"
    dir("$WORKSPACE/unittest") {
        def ut_tfs = ["${tensorflow_version}"]
        def ut_pts = ["${pytorch_version}"]
        ut_ext_pts = parseStrToList(ut_extension_pytorch)
        ut_pts = ut_pts.plus(ut_ext_pts)
        ut_tfs.unique()
        ut_pts.unique()
        ut_tfs.each { tf_version ->
            withEnv(["tf_version=${tf_version}"]) {
                sh ''' #!/bin/bash
                   overview_log="${WORKSPACE}/summary_overview.log"
                   if [ $(ls -l | grep -c ${tf_version}) != 0 ]; then
                     ut_log_name=ut_tf_${tf_version}_pt_${pytorch_version}.log
                     [[ ! -f $ut_log_name ]] && ut_log_name=`ls -a | grep -E "ut_tf_${tf_version}_pt_([0-9]+.){2}[0-9]+(.cpu)?.log" | head -1`
                     pt_version=`echo -e "${ut_log_name}" | grep -Po "pt_([0-9]+.){2}[0-9]+(.cpu)?" | awk -F "_" '{print $2}'`
                     if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ] || [ $(grep -c "core dumped" ${ut_log_name}) != 0 ] || [ $(grep -c "Segmentation fault" ${ut_log_name}) != 0 ];then
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
            withEnv(["pt_version=${pt_version}"]) {
                sh ''' #!/bin/bash
                   overview_log="${WORKSPACE}/summary_overview.log"
                   if [ $(ls -l | grep -c ${pt_version}) != 0 ]; then
                     pt_version_tmp=${pt_version%+*}
                     ut_log_name=ut_tf_${tensorflow_version}_pt_${pt_version}.log
                     [[ ! -f $ut_log_name ]] && ut_log_name=`ls -a | grep -E "ut_tf_.*_pt_${pt_version_tmp}.*log" | head -1`
                     tf_version=`echo -e "${ut_log_name}" | grep -Po "tf_.*_" | awk -F "_" '{print $2}'`
                     if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ] || [ $(grep -c "core dumped" ${ut_log_name}) != 0 ] || [ $(grep -c "Segmentation fault" ${ut_log_name}) != 0 ];then
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

def collectUT_backend_Log() {
    echo "------------  running collect UT Log of backend -------------"
    sh ''' #!/bin/bash
        overview_log="${WORKSPACE}/summary_overview.log"
        ut_log_name=$WORKSPACE/unittest/unit_test_gtest.log
        if [ -f ${ut_log_name} ];then
            sed -i '/deep-engine_ut_gtest/d' ${overview_log}
            if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ] || [ $(grep -c "core dumped" ${ut_log_name}) != 0 ] || [ $(grep -c "Segmentation fault" ${ut_log_name}) != 0 ];then
                ut_status='FAILURE'
            else
                ut_status='SUCCESS'
            fi
            echo "deep-engine_ut_gtest,${ut_status},${BUILD_URL}artifact/unittest/unit_test_gtest.log" | tee -a ${overview_log}
        fi
        ut_log_name=$WORKSPACE/unittest/unit_test_pytest.log
        if [ -f ${ut_log_name} ];then
            sed -i '/deep-engine_ut_pytest/d' ${overview_log}
            if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ] || [ $(grep -c "core dumped" ${ut_log_name}) != 0 ] || [ $(grep -c "Segmentation fault" ${ut_log_name}) != 0 ];then
                ut_status='FAILURE'
            else
                ut_status='SUCCESS'
            fi
            echo "deep-engine_ut_pytest,${ut_status},${BUILD_URL}artifact/unittest/unit_test_pytest.log" | tee -a ${overview_log}
        fi
    '''
}

def BuildParams(job_framework, model, cpu, os){
    framework_version = ''
    if (job_framework == 'pytorch'){
        framework_version = "${pytorch_version}"
    } else if (job_framework == "nlp_excutor"){
        framework_version = "na"
    } else if (job_framework == "tensorflow"){
        framework_version = "${tensorflow_version}"
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
    ParamsPerJob += string(name: "model", value: "${model}")
    ParamsPerJob += string(name: "nlp_url", value: "${nlp_url}")
    ParamsPerJob += string(name: "nlp_branch", value: "${nlp_commit}")
    ParamsPerJob += string(name: "MR_source_branch", value: "${source_branch}")
    ParamsPerJob += string(name: "MR_target_branch", value: "${target_branch}")
    ParamsPerJob += string(name: "python_version", value: "${python_version}")
    ParamsPerJob += string(name: "test_mode", value: "${test_mode}")
    ParamsPerJob += string(name: "binary_build_job", value: "${binary_build_job}")
    ParamsPerJob += string(name: "binary_build_job_nlp", value: "${binary_build_job_nlp}")
    ParamsPerJob += string(name: "mode", value: "${pass_mode}")
    ParamsPerJob += booleanParam(name: "multi_instance", value: multi_instance)
    ParamsPerJob += string(name: "tuning_timeout", value: "${tuning_timeout}")
    ParamsPerJob += booleanParam(name: "tune_only", value: tune_only)
    ParamsPerJob += string(name: "val_branch", value: "${val_branch}")
    ParamsPerJob += string(name: "cpu", value: "${cpu}")
    ParamsPerJob += string(name: "os", value: "${os}")
    ParamsPerJob += string(name: "dataset_prefix", value: "${dataset_prefix}")
    ParamsPerJob += string(name: "refer_build", value: "${refer_build}")
    ParamsPerJob += booleanParam(name: "collect_tuned_model", value: collect_tuned_model)
    ParamsPerJob += string(name: "precision", value: "${precision}")
    ParamsPerJob += string(name: "conda_env_mode", value: "${conda_env_mode}")
    ParamsPerJob += string(name: "log_level", value: "${log_level}")
    ParamsPerJob += string(name: "install_nlp_toolkit", value: "${install_nlp_toolkit}")
    ParamsPerJob += string(name: "inferencer_config", value: "${inferencer_config}")

    return ParamsPerJob
}

def model_test_optimize() {
    def workflow="optimize"
    def jobs = [:]
    PLATFORMS.split(";").each { systemConfig ->
        def system = systemConfig.split(":")[0]
        platforms = systemConfig.split(":")[1].split(",")
        platforms.each { platform ->
            def cpu = platform
            // Get frameworks list and sub jenkins job
            job_frameworks = optimize_frameworks.split(',')
            def sub_jenkins_job = "nlp_toolkit_optimize_validation_localtest"
            job_frameworks.each { job_framework ->
                // Get models list
                def job_models = []
                if (job_framework == 'pytorch') {
                    job_models = parseStrToList(pytorch_models)
                } else if (job_framework == 'tensorflow') {
                    job_models = parseStrToList(tensorflow_models)
                }
                echo "${job_models}"
                echo "framwork-----> ${job_framework}"
                job_models.each { job_model ->
                    jobs["${workflow}_${job_model}_${job_framework}_${system}_${cpu}"] = {
                        // execute build
                        println("${workflow}, ${cpu}, ${system}, ${job_framework}, ${job_model}")
                        downstreamJob = build job: sub_jenkins_job, propagate: false, parameters: BuildParams(job_framework, job_model, cpu, system)
                        catchError {
                            copyArtifacts(
                                    projectName: sub_jenkins_job,
                                    selector: specific("${downstreamJob.getNumber()}"),
                                    filter: "*.log, tuning_config.yaml, ${job_framework}*.json",
                                    fingerprintArtifacts: true,
                                    target: "${workflow}/${job_framework}/${job_model}",
                                    optional: true)
                            if (collect_tuned_model) {
                                copyArtifacts(
                                        projectName: sub_jenkins_job,
                                        selector: specific("${downstreamJob.getNumber()}"),
                                        filter: "${job_framework}-${job_model}-tune*",
                                        fingerprintArtifacts: true,
                                        target: "${workflow}/${job_framework}/tuned_model",
                                        optional: true)
                            }
                            // Archive in Jenkins
                            archiveArtifacts artifacts: "${workflow}/${job_framework}/${job_model}/**", allowEmptyArchive: true
                        }
                        downstreamJobStatus = downstreamJob.result
                        def failed_build_result = downstreamJob.result
                        def failed_build_url = downstreamJob.absoluteUrl
                        if (failed_build_result != 'SUCCESS') {
                            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                                if (test_mode != 'nightly') {
                                    currentBuild.result = "FAILURE"
                                }
                                sh " tail -n 50 ${workflow}/${job_framework}/${job_model}/*.log > ${WORKSPACE}/details.failed.build 2>&1 "
                                failed_build_detail = readFile file: "${WORKSPACE}/details.failed.build"
                                error("---- ${workflow}_${cpu}_${system}_${job_framework}_${job_model} got failed! ---- Details in ${failed_build_url}consoleText! ---- \n ${failed_build_detail}")
                            }
                        }
                    }
                }
            }
        }
    }
    //if (test_mode == "pre-CI" || pipeline_failFast) {
    //    echo "enable failFast"
    //    jobs.failFast = true
    //}
    return jobs
}

def model_test_deploy() {
    def workflow="deploy"
    def jobs = [:]
    PLATFORMS.split(";").each { systemConfig ->
        def system = systemConfig.split(":")[0]
        platforms = systemConfig.split(":")[1].split(",")
        platforms.each { platform ->
            def cpu = platform
            // Get frameworks list and sub jenkins job
            job_frameworks = deploy_backends.split(',')
            def sub_jenkins_job = "nlp_toolkit_deploy_validation_localtest"
            job_frameworks.each { job_framework ->
                // Get models list
                def job_models = []
                if (job_framework == 'nlp_excutor') {
                    job_models = parseStrToList(nlp_excutor_models)
                } else if (job_framework == 'ipex') {
                    job_models = parseStrToList(ipex_models)
                }
                echo "${job_models}"
                echo "framwork-----> ${job_framework}"
                job_models.each { job_model ->
                    jobs["${workflow}_${job_model}_${job_framework}_${system}_${cpu}"] = {
                        // execute build
                        println("${workflow}, ${cpu}, ${system}, ${job_framework}, ${job_model}")
                        downstreamJob = build job: sub_jenkins_job, propagate: false, parameters: BuildParams(job_framework, job_model, cpu, system)
                        catchError {
                            copyArtifacts(
                                    projectName: sub_jenkins_job,
                                    selector: specific("${downstreamJob.getNumber()}"),
                                    filter: "*.log, ${job_framework}*.json, engine-${job_model}/**",
                                    fingerprintArtifacts: true,
                                    target: "${workflow}/${job_framework}/${job_model}",
                                    optional: true)
                            if (collect_tuned_model) {
                                copyArtifacts(
                                        projectName: sub_jenkins_job,
                                        selector: specific("${downstreamJob.getNumber()}"),
                                        filter: "${job_framework}-${job_model}-tune*",
                                        fingerprintArtifacts: true,
                                        target: "${workflow}/${job_framework}/tuned_model",
                                        optional: true)
                            }
                            // Archive in Jenkins
                            archiveArtifacts artifacts: "${workflow}/${job_framework}/${job_model}/**", allowEmptyArchive: true
                        }
                        downstreamJobStatus = downstreamJob.result
                        def failed_build_result = downstreamJob.result
                        def failed_build_url = downstreamJob.absoluteUrl
                        if (failed_build_result != 'SUCCESS') {
                            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                                if (test_mode != 'nightly') {
                                    currentBuild.result = "FAILURE"
                                }
                                sh " tail -n 50 ${workflow}/${job_framework}/${job_model}/*.log > ${WORKSPACE}/details.failed.build 2>&1 "
                                failed_build_detail = readFile file: "${WORKSPACE}/details.failed.build"
                                error("---- ${workflow}_${cpu}_${system}_${job_framework}_${job_model} got failed! ---- Details in ${failed_build_url}consoleText! ---- \n ${failed_build_detail}")
                            }
                        }
                    }
                }
            }
        }
    }
    //if (test_mode == "pre-CI" || pipeline_failFast) {
    //    echo "enable failFast"
    //    jobs.failFast = true
    //}
    return jobs
}

def collect_optimize_Log() {
    echo "------------  running collect Log for optimize -------------"
    echo "---------------------------------------------------------"
    def workflow = "optimize"
    PLATFORMS.split(";").each { systemConfig ->
        def system = systemConfig.split(":")[0]
        platforms = systemConfig.split(":")[1].split(",")
        platforms.each { platform ->
            def cpu = platform
            // Get frameworks list and sub jenkins job
            job_frameworks = optimize_frameworks.split(',')
            def sub_jenkins_job = "nlp_toolkit_optimize_validation_localtest"
            job_frameworks.each { job_framework ->
                // Get models list
                def job_models = []
                if (job_framework == 'pytorch') {
                    job_models = parseStrToList(pytorch_models)
                } else if (job_framework == 'tensorflow') {
                    job_models = parseStrToList(tensorflow_models)
                }
                echo "${job_models}"
                echo "framwork-----> ${job_framework}"
                job_models.each { job_model ->
                    echo "-------- ${cpu} - ${system} - ${job_framework} - ${job_model} --------"
                     // Generate tuning info log
                    sh """#!/bin/bash -x
                        if [[ -f ${WORKSPACE}/${workflow}/${job_framework}/${job_model}/tuning_info.log ]]; then
                            cat ${WORKSPACE}/${workflow}/${job_framework}/${job_model}/tuning_info.log >> ${WORKSPACE}/tuning_info.log
                        else
                            echo "${system};Unknown;${workflow};${job_framework};N/A;${job_model};;;${RUN_DISPLAY_URL};;;" >> ${WORKSPACE}/tuning_info.log
                        fi
                    """
                    echo "Getting results for ${job_framework} - ${job_model}"
                    sh """#!/bin/bash -x
                        if [[ -f ${WORKSPACE}/${workflow}/${job_framework}/${job_model}/summary.log ]]; then
                            cat ${WORKSPACE}/${workflow}/${job_framework}/${job_model}/summary.log >> ${WORKSPACE}/summary.log
                        else
                            echo "${system};Unknown;${workflow};${job_framework};N/A;INT8;${job_model};Inference;Performance;;;${RUN_DISPLAY_URL}" >> ${WORKSPACE}/summary.log
                            echo "${system};Unknown;${workflow};${job_framework};N/A;FP32;${job_model};Inference;Performance;;;${RUN_DISPLAY_URL}" >> ${WORKSPACE}/summary.log
                        fi
                    """
                }
            }
        }
    } 
    echo "done running collectLog ......."
    stash allowEmpty: true, includes: "*.log, *.json", name: "logfile"  
}

def collect_deploy_Log() {
    echo "------------  running collect Log for deploy -------------"
    echo "---------------------------------------------------------"
    def workflow = "deploy"
    PLATFORMS.split(";").each { systemConfig ->
        def system = systemConfig.split(":")[0]
        platforms = systemConfig.split(":")[1].split(",")
        platforms.each { platform ->
            def cpu = platform
            job_frameworks = deploy_backends.split(',')
            job_frameworks.each { job_framework ->
                // Get models list
                def job_models = []
                if (job_framework == 'nlp_excutor') {
                    job_models = parseStrToList(nlp_excutor_models)
                } else if (job_framework == 'ipex') {
                    job_models = parseStrToList(ipex_models)
                }
                echo "${job_models}"
                echo "framwork-----> ${job_framework}"
                job_models.each { job_model ->
                    echo "-------- ${cpu} - ${system} - ${job_framework} - ${job_model} --------"
                     // Generate tuning info log
                    sh """#!/bin/bash -x
                        if [[ -f ${WORKSPACE}/${workflow}/${job_framework}/${job_model}/tuning_info.log ]]; then
                            cat ${WORKSPACE}/${workflow}/${job_framework}/${job_model}/tuning_info.log >> ${WORKSPACE}/tuning_info.log
                        else
                            echo "${system};Unknown;${workflow};${job_framework};N/A;${job_model};;;${RUN_DISPLAY_URL};;;" >> ${WORKSPACE}/tuning_info.log
                        fi
                    """
                    echo "Getting results for ${job_framework} - ${job_model}"
                    sh """#!/bin/bash -x
                        if [[ -f ${WORKSPACE}/${workflow}/${job_framework}/${job_model}/summary.log ]]; then
                            cat ${WORKSPACE}/${workflow}/${job_framework}/${job_model}/summary.log >> ${WORKSPACE}/summary.log
                        else
                            echo "${system};Unknown;${workflow};${job_framework};N/A;INT8;${job_model};Inference;Performance;;;${RUN_DISPLAY_URL}" >> ${WORKSPACE}/summary.log
                            echo "${system};Unknown;${workflow};${job_framework};N/A;FP32;${job_model};Inference;Performance;;;${RUN_DISPLAY_URL}" >> ${WORKSPACE}/summary.log
                        fi
                    """
                    echo "Getting benchmark results for ${job_framework} - ${job_model}"
                    sh """#!/bin/bash -x
                        if [[ -f ${WORKSPACE}/${workflow}/${job_framework}/${job_model}/inferencer_summary.log ]]; then
                            cat ${WORKSPACE}/${workflow}/${job_framework}/${job_model}/inferencer_summary.log >> ${WORKSPACE}/inferencer_summary.log
                        else
                            echo "${job_framework},throughput,${job_model},,,,,INT8," >> ${WORKSPACE}/inferencer_summary.log
                            echo "${job_framework},throughput,${job_model},,,,,FP32," >> ${WORKSPACE}/inferencer_summary.log
                        fi
                    """
                }
            }
        }
    } 
    echo "done running collectLog ......."
    stash allowEmpty: true, includes: "*.log, *.json", name: "logfile"
}

def generateReport() {
    if (refer_build != 'x0') {
        def refer_job_name
        if (test_mode == "extension") {
            refer_job_name = "nlp-toolkit-validation-nightly"
        } else if (test_mode == "pre-CI") {
            refer_job_name = "nlp-toolkit-validation-PR"
        } else {
            refer_job_name = currentBuild.projectName
        }
        try{
            copyArtifacts(
                projectName: refer_job_name,
                selector: specific("${refer_build}"),
                filter: 'summary.log,tuning_info.log,inferencer_summary.log',
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
            "qtools_branch=${nlp_branch}",
            "qtools_commit=${nlp_commit}",
            "summaryLog=${summaryLog}",
            "summaryLogLast=${summaryLogLast}",
            "inferencerSummaryLog=${inferencerSummaryLog}",
            "inferencerSummaryLogLast=${inferencerSummaryLogLast}",
            "tuneLog=${tuneLog}",
            "tuneLogLast=${tuneLogLast}",
            "overviewLog=${overviewLog}",
            "coverage_summary_optimize=${coverage_summary_optimize}",
            "coverage_summary_optimize_base=${coverage_summary_optimize_base}",
            "coverage_summary_deploy=${coverage_summary_deploy}",
            "coverage_summary_deploy_base=${coverage_summary_deploy_base}",
            "Jenkins_job_status=${Jenkins_job_status}",
            "ghprbActualCommit=${ghprbActualCommit}",
            "ghprbPullLink=${ghprbPullLink}",
            "ghprbPullId=${ghprbPullId}",
            "MR_source_branch=${source_branch}",
            "MR_target_branch=${target_branch}",
            "nc_code_lines_summary=${WORKSPACE}/format_scan/nc_code_lines_summary.csv",
            "engine_code_lines_summary=${WORKSPACE}/format_scan/engine_code_lines_summary.csv"

        ]) {
            sh '''
                if [[ ${qtools_branch} == '' ]]; then
                    chmod 775 ./lpot-validation/nlp-toolkit/scripts/generate_report_pr.sh
                    ./lpot-validation/nlp-toolkit/scripts/generate_report_pr.sh
                else
                    chmod 775 ./lpot-validation/nlp-toolkit/scripts/generate_report.sh
                    ./lpot-validation/nlp-toolkit/scripts/generate_report.sh
                fi
            '''
        }
    }
}

def generateExcelReport() {
    withEnv([
        "summaryLog=${summaryLog}",
        "tuneLog=${tuneLog}",
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
        if (test_mode == "pre-CI") {
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
                attachmentsPattern: "nlp_toolkit_report.xlsx",
                mimeType: 'text/html'

    }
}

///// full process /////

node( node_label ) {
    if (test_mode == "pre-CI") {
        if (ABORT_DUPLICATE_TEST) {
            stage("Cancel previous builds") {
                (exit_code, message) = cancelPreviousBuilds()
                if (exit_code != 0) {
                    createGithubIssueComment(message)
                }
                if (exit_code == 1) {
                    error("Executed test on the same commit. Aborting current build.")
                }
            }
        } else {
            println("pending")
            updateGithubCommitStatus("pending", "Waiting for status to be reported")
        }
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
        summaryLog = "${WORKSPACE}/summary.log"
        writeFile file: summaryLog, text: "OS;Platform;Workflow;Backend;Version;Precision;Model;Mode;Type;BS;Value;Url\n"
        summaryLogLast = "${WORKSPACE}/reference/summary.log"

        tuneLog = "${WORKSPACE}/tuning_info.log"
        writeFile file: tuneLog, text: "OS;Platform;Workflow;Backend;Version;Model;Tune_time\n"
        tuneLogLast = "${WORKSPACE}/reference/tuning_info.log"

        inferencerSummaryLog = "${WORKSPACE}/inferencer_summary.log"
        inferencerSummaryLogLast = "${WORKSPACE}/reference/inferencer_summary.log"

        // over view log
        overviewLog = "${WORKSPACE}/summary_overview.log"
        writeFile file: overviewLog, text: "Jenkins Job, Build Status, Build ID\n"

        // coverage summary of optimize
        coverage_summary_optimize = "${WORKSPACE}/unittest/coverage_summary_optimize.log"
        coverage_summary_optimize_base = "${WORKSPACE}/unittest/coverage_summary_optimize_base.log"

        // coverage summary of deploy
        coverage_summary_deploy = "${WORKSPACE}/unittest/coverage_summary_deploy.log"
        coverage_summary_deploy_base = "${WORKSPACE}/unittest/coverage_summary_deploy_base.log"
        
        download()
        if (test_mode == "pre-CI") {
            sh"""#!/bin/bash
                cd nlp-toolkit
                echo "source_branch: "
                git show-ref -s remotes/origin/${source_branch}
                echo "target_branch: "
                git show-ref -s remotes/origin/${target_branch}
            """
        } else {
            nlp_commit = sh (
                    script: 'cd nlp-toolkit && git rev-parse HEAD',
                    returnStdout: true
            ).trim()
            // set env to close duplicate nightly build
            env.INC_COMMIT = nlp_commit
            println("INC_COMMIT = " + env.INC_COMMIT)
            if (ABORT_DUPLICATE_TEST) {
                previous_INC_COMMIT = currentBuild.previousBuiltBuild.buildVariables.INC_COMMIT
                if ( env.INC_COMMIT == previous_INC_COMMIT) {
                    println("Kill the current Buils --> " + currentBuild.rawBuild.getFullDisplayName())
                    currentBuild.rawBuild.doKill()
                }
            }
        }

        stage('Build wheel') {
            if (!format_scan_only) {
                def build_job_list = [:]
                build_job_list["build INC"] = {
                    buildBinary()
                } 
                build_job_list["build NLP"] = {
                    buildBinaryNLP()
                } 
                parallel build_job_list
            } else {
                echo "Format scan only, don't need to build binary!"
            }
        }

        def job_list = [:]
        if (RUN_UT_OPT) {
            def ut_jobs = unitTestJobsOptimize()
            job_list = job_list + ut_jobs
        }
        if (RUN_UT_BAK) {
            def ut_jobs_pytest = unitTestBackend("pytest")
            def ut_jobs_gtest = unitTestBackend("gtest")
            job_list = job_list + ut_jobs_pytest + ut_jobs_gtest
        }
        if (RUN_PYLINT) {
            job_list["Pylint Scan"] = {
                codeScan("pylint")
            }
        }
        if (RUN_CPPLINT) {
            println("Add cpplint scan to job...")
            job_list["cpplint Scan"] = {
                codeScan("cpplint")
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
        if (CHECK_COPYRIGHT) {
            job_list["Copyright Check"] = {
                copyrightCheck()
            }
        }
        if ( "optimize" in workflows_list && optimize_frameworks != "") {
            def perf_jobs = model_test_optimize()
            job_list = job_list + perf_jobs
        }
        if ( "deploy" in workflows_list && deploy_backends != "") {
            def perf_jobs = model_test_deploy()
            job_list = job_list + perf_jobs
        }
        //if (test_mode == "pre-CI" || pipeline_failFast) {
        //    echo "enable failFast"
        //    job_list.failFast = true
        //}
        if (job_list.size() > 0) {
            stage("Execute tests") {
                parallel job_list
            }
        }
    } catch(e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        if (e.toString() == "org.jenkinsci.plugins.workflow.steps.FlowInterruptedException" && e.getCauses().size() == 0) {
            println("Setting autoCancel flag to true.")
            autoCancel = true
            println("autoCancel: ${autoCancel}")
        }
        error(e.toString())
    } finally {
        stage("Collect Logs") {
            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                if ( "optimize" in workflows_list && optimize_frameworks != "") {
                    collect_optimize_Log()
                }
                if ( "deploy" in workflows_list && deploy_backends != "") {
                    collect_deploy_Log()
                }
                if (RUN_UT_BAK) {
                    collectUT_backend_Log()
                }
                if (RUN_UT_OPT) {
                    collectUT_optimize_Log()
                }
                if (collect_tuned_model) {
                    sh (
                        script: 'cp -r ./tensorflow/tuned_model /tmp/',
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
            } finally {
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
            archiveArtifacts artifacts: '*.log, *.html, *.xlsx, *.json, *.txt, reference/*', excludes: null, allowEmptyArchive: true
            fingerprint: true
        }
        if (test_mode == "pre-CI") {
            // If default model has perf regression, then fail the job.
            def destFile = new File("${WORKSPACE}/perf_regression.log")
            if (destFile.exists()) {
                currentBuild.result = 'FAILURE'
                println("------------------Default model performance regression!!!!!!!!!!!!!!!!!!!!!!!")
            }
            if (currentBuild.result == 'FAILURE' || currentBuild.result == 'ABORTED') {
                echo "pipeline failed"
                echo "autoCancel: ${autoCancel}"
                if (autoCancel) {
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