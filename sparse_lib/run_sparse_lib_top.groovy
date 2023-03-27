// 1. unit test
// 2. benchmark
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
import groovy.io.FileType

credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'
sys_val_credentialsId = "dcf0dff2-03fb-45b0-9e64-5b4db466bee5"
def autoCancel = false
// test mode: pre-CI / nightly / extension
test_mode = params.test_mode ?: "pre-CI"
echo "test mode ${test_mode}"

// conda env mode: pypi / conda / source
conda_env_mode = params.conda_env_mode ?: "pypi"
echo "conda_env_mode ${conda_env_mode}"

python_version = params.python_version ?: "3.8"
echo "python_version: ${python_version}"

// setting node_label
node_label = params.node_label ?: "lpot"
sub_node_label = params.sub_node_label ?: "spr,clx,icx"
sub_node_ut = params.sub_node_ut ?: sub_node_label
echo "Running on node ${node_label}; sub-job node ${sub_node_label}; ut node ${sub_node_ut}"

// setting windows node_label
windows_node = "icx-windows"
echo "windows_node is ${windows_node}"


// run GPU test
is_gpu = params.is_gpu ?: false 
echo "run gpu ${is_gpu}"


//other settings
nlp_url = params.nlp_url ?: "https://github.com/intel-innersource/frameworks.ai.nlp-toolkit.intel-nlp-toolkit.git"
echo "nlp_url is ${nlp_url}"

lpot_url = params.lpot_url ?: "https://github.com/intel-innersource/frameworks.ai.lpot.intel-lpot.git"
echo "lpot_url is ${lpot_url}"

lpot_branch = params.lpot_branch ?: "master"
echo "lpot_branch is ${lpot_branch}"

pipeline_failFast = params.pipeline_failFast != null ? params.pipeline_failFast : false
echo "pipeline_failFast = ${pipeline_failFast}"

val_branch = params.val_branch ?: "main"
echo "val_branch: ${val_branch}"

format_scan_only = params.format_scan_only != null ? params.format_scan_only : false
echo "format_scan_only = ${format_scan_only}"

RUN_UT = params.RUN_UT != null ? params.RUN_UT : false
echo "RUN UT = ${RUN_UT}"

RUN_WINDOWS =params.RUN_WINDOWS !=null ? params.RUN_WINDOWS : false
echo "RUN WINDOWS = ${RUN_WINDOWS}"

RUN_BENCHMARK = params.RUN_BENCHMARK != null ? params.RUN_BENCHMARK : false
echo "RUN BENCHMARK = ${RUN_BENCHMARK}"

RUN_CPPLINT = params.RUN_CPPLINT != null ? params.RUN_CPPLINT : false
echo "RUN_CPPLINT = ${RUN_CPPLINT}"

RUN_CLANGFORMAT = params.RUN_CLANGFORMAT != null ? params.RUN_CLANGFORMAT : false
echo "RUN_CLANGFORMAT = ${RUN_CLANGFORMAT}"

RUN_BANDIT = params.RUN_BANDIT != null ? params.RUN_BANDIT : false
echo "RUN_BANDIT = ${RUN_BANDIT}"

RUN_SPELLCHECK = params.RUN_SPELLCHECK != null ? params.RUN_SPELLCHECK : false
echo "RUN_SPELLCHECK = ${RUN_SPELLCHECK}"

CHECK_COPYRIGHT = params.CHECK_COPYRIGHT != null ? params.CHECK_COPYRIGHT : false
echo "CHECK_COPYRIGHT = ${CHECK_COPYRIGHT}"

ABORT_DUPLICATE_TEST = params.ABORT_DUPLICATE_TEST != null ? params.ABORT_DUPLICATE_TEST : false
echo "ABORT_DUPLICATE_TEST is ${ABORT_DUPLICATE_TEST}"

sparse_ut_only = params.sparse_ut_only != null ? params.sparse_ut_only : false
echo "sparse_ut_only is ${sparse_ut_only}"

// provide previous build to skip
build_job_nlp = params.build_job_nlp
build_job_lpot = params.build_job_lpot


// ncores_per_instance:bs for engine inference
inferencer_config = params.inferencer_config ?: "4:8,7:8,24:1"
echo "inferencer_config: ${inferencer_config}"

/////////
MR_source_branch = ""
MR_target_branch = ""
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
    MR_source_branch = params.GITHUB_PR_SOURCE_BRANCH
    MR_target_branch = params.GITHUB_PR_TARGET_BRANCH
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
    test_title = "Sparse Lib PRE-CI Test"
    email_subject="PR${ghprbPullId}: ${test_title}"
} else {
    test_title = "Sparse Lib Test"
    email_subject="${test_title}"
}
// if use custom test title
test_title = params.test_title ?: test_title
echo "test title ${test_title}"

target_path="./intel_extension_for_transformers/backends/neural_engine/kernels"


// Processing PR comment params
echo "params.GITHUB_PR_COMMENT_BODY_MATCH = ${params.GITHUB_PR_COMMENT_BODY_MATCH}"
arg_map = [:]
if (params.GITHUB_PR_COMMENT_BODY_MATCH) {
    params.GITHUB_PR_COMMENT_BODY_MATCH.split('\\s').each { arg ->
        def arg_kv = arg.split('=')
        def arg_k = arg_kv[0]
        if (arg_k.startsWith('--')) arg_k = arg_k[2..arg_k.length()-1]
        arg_map[arg_k] = arg_kv.size() > 1 ? arg_kv[1] : true
    }
    echo "arg_map=${arg_map}"

    //! Update README accordingly after changing the way reading arg_map
    RUN_UT = arg_map.ut as Boolean
    RUN_WINDOWS = arg_map.windows as Boolean
    if (arg_map.ut == 'sparse_only') sparse_ut_only = true
    RUN_BENCHMARK = arg_map.benchmark as Boolean
    RUN_CPPLINT = arg_map.cpplint as Boolean
    RUN_BANDIT = arg_map.bandit as Boolean
    RUN_SPELLCHECK = arg_map.spell as Boolean
    CHECK_COPYRIGHT = arg_map.copyright as Boolean
    inferencer_config = arg_map.inferencer_config ?: inferencer_config
    sub_node_label = arg_map.node ?: sub_node_label
    sub_node_ut = arg_map.node_ut ?: sub_node_ut
    is_gpu = arg_map.gpu ?: is_gpu as Boolean
    echo """ PR comment args changes params:
        RUN_UT=${RUN_UT}
        sparse_ut_only=${sparse_ut_only}
        RUN_BENCHMARK=${RUN_BENCHMARK}
        RUN_CPPLINT=${RUN_CPPLINT}
        RUN_BANDIT=${RUN_BANDIT}
        RUN_SPELLCHECK=${RUN_SPELLCHECK}
        CHECK_COPYRIGHT=${CHECK_COPYRIGHT}
        inferencer_config=${inferencer_config}
        sub_node_label=${sub_node_label}
        sub_node_ut=${sub_node_ut}
        run_gpu=${is_gpu}
    """
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
                    branches                         : [[name: "${MR_source_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "nlp-toolkit"],
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

def codeScan(tool) {
    List codeScanParams = [
        string(name: "TOOL", value: "${tool}"),
        string(name: "nlp_url", value: "${nlp_url}"),
        string(name: "nlp_branch", value: "${nlp_commit}"),
        string(name: "MR_source_branch", value: "${MR_source_branch}"),
        string(name: "MR_target_branch", value: "${MR_target_branch}"),
        string(name: "val_branch", value: "${val_branch}"),
        string(name: "python_version", value: "${python_version}")
    ]

    def downstreamJob = build job: "sparse-lib-format-scan", propagate: false, parameters: codeScanParams
    copyArtifacts(
        projectName: "sparse-lib-format-scan",
        selector: specific("${downstreamJob.getNumber()}"),
        filter: '*.json,*.log,*.csv',
        fingerprintArtifacts: true,
        target: "format_scan",
        optional: true)
    if (tool != "cloc") {
        text_comment = readFile file: "${overview_log}"
        writeFile file: "${overview_log}", text: text_comment + "sparse-lib-format-scan," + tool + "," + downstreamJob.result + "," + downstreamJob.number + "\n"
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
            string(name: "MR_source_branch", value: "${MR_source_branch}"),
            string(name: "MR_target_branch", value: "${MR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "target_path", value: "${target_path}")
    ]
    def downstreamJob = build job: "intel-lpot-copyright-check", propagate: false, parameters: copyrightCheckParams
    copyArtifacts(
            projectName: "intel-lpot-copyright-check",
            selector: specific("${downstreamJob.getNumber()}"),
            filter: '*.log',
            fingerprintArtifacts: true,
            target: "copyrightCheck",
            optional: true)
    text_comment = readFile file: "${overview_log}"
    writeFile file: "${overview_log}", text: text_comment + "nlp-toolkit-copyright-check," + downstreamJob.result + "," + downstreamJob.number + "\n"
    // Archive in Jenkins
    archiveArtifacts artifacts: "copyrightCheck/**", allowEmptyArchive: true
    if (downstreamJob.result != 'SUCCESS') {
        currentBuild.result = "FAILURE"
        if (test_mode == "pre-CI") {
            error("Copyright check failed!")
        }
    }
}

def windowsJobBackend(){
    def windows_job = [:]
    def unit_test_mode = "gtest" 
    List WindowsParams = [
        string(name: "nlp_url", value: "${nlp_url}"),
        string(name: "nlp_branch", value: "${nlp_commit}"),
        string(name: "PR_source_branch", value: "${MR_source_branch}"),
        string(name: "PR_target_branch", value: "${MR_target_branch}"),
        string(name: "unit_test_mode", value: "${unit_test_mode}"),
        string(name: "node_label", value: "${windows_node}"),
        string(name: "lpot_url",value:"${lpot_url}"),
        string(name: "val_branch", value: "${val_branch}"),
    ]
    sub_job_name = "sparse-lib-windows"
    windows_job[unit_test_mode] = {
        println("building windows sparse lib UT")
        def downstreamJob = build job: sub_job_name, propagate: false, parameters: WindowsParams
        catchError {
            copyArtifacts(
                    projectName: sub_job_name,
                    selector: specific("${downstreamJob.getNumber()}"),
                    filter: 'a/intel_extension_for_transformers/backends/neural_engine/build/*log,a/intel_extension_for_transformers/backends/neural_engine/build/bin/Debug/*log,a/*log',
                    fingerprintArtifacts: true,
                    target: "unittest")
            archiveArtifacts artifacts: "unittest/**", allowEmptyArchive: true
        }
        def failed_build_url = downstreamJob.absoluteUrl
        if (downstreamJob.result != 'SUCCESS') {
            println("got sparse lib windows UT failed")
            win_ut_failed_detail = readFile file: "unittest/a/win_error_log"
            currentBuild.result = "FAILURE"
            error("---Windows gtest test failed--- Details in ${failed_build_url}consoleText! ---- \n ${win_ut_failed_detail}")
        }      
    }
    return windows_job
}

def unitTestBackend(device) {
    def ut_jobs = [:]
    def unit_test_mode = "gtest" 
    List UTBuildParams = [
        string(name: "nlp_url", value: "${nlp_url}"),
        string(name: "nlp_branch", value: "${nlp_commit}"),
        string(name: "PR_source_branch", value: "${MR_source_branch}"),
        string(name: "PR_target_branch", value: "${MR_target_branch}"),
        string(name: "python_version", value: "${python_version}"),
        string(name: "val_branch", value: "${val_branch}"),
        string(name: "unit_test_mode", value: "${unit_test_mode}"),
        string(name: "node_label", value: "${device}")
    ]
    if (sparse_ut_only) {
        sub_job_name = "sparse-lib-ut"
    } else {
        sub_job_name = "nlp-toolkit-backend-ut"
    }
    println("ut params buildup")
    ut_jobs["${unit_test_mode}_${device}"] = {
        println("building sparse lib UT")
        def downstreamJob = build job: sub_job_name, propagate: false, parameters: UTBuildParams
        catchError {
            copyArtifacts(
                    projectName: sub_job_name,
                    selector: specific("${downstreamJob.getNumber()}"),
                    filter: '*.log, *.txt',
                    fingerprintArtifacts: true,
                    target: "unittest/${device}")
            archiveArtifacts artifacts: "unittest/${device}/**", allowEmptyArchive: true
        }
        if (downstreamJob.result != 'SUCCESS') {
            println("got sparse lib UT failed")
            def sub_job_url = downstreamJob.absoluteUrl
            withEnv(["sub_job_url=${sub_job_url}", "ut_mode=${unit_test_mode}"]){
                sh '''#!/bin/bash
                overview_log="${WORKSPACE}/summary_overview.log"
                echo "deep-engine_ut_${ut_mode}_${device},FAILURE,${sub_job_url}" | tee -a ${overview_log}
                '''
            }
            currentBuild.result = "FAILURE"
            error("---gtest test failed---")
        }
    }
    return ut_jobs
}

def collectWINUT_backend_Log(){
    echo "------------  running collect WINDOWS UT Log of backend -------------"
    sh ''' #!/bin/bash
        overview_log="${WORKSPACE}/summary_overview.log"
        win_ut_log_name=$WORKSPACE/unittest/a/win_error_log
        if [ -f ${win_ut_log_name} ];then
            ut_status='FAILURE'
        else
            ut_status='SUCCESS'
        fi
        echo "sparselib_windows_ut_gtest,${ut_status},${BUILD_URL}artifact/unittest/a/win_error_log/*view*" | tee -a ${overview_log}
        
    '''
}

def collectUT_backend_Log(device) {
    withEnv(["device=${device}"]) {
        echo "------------  running collect UT Log of backend -------------"
        sh ''' #!/bin/bash
            overview_log="${WORKSPACE}/summary_overview.log"
            ut_log_name=$WORKSPACE/unittest/${device}/unit_test_gtest.log
            if [ -f ${ut_log_name} ];then
                sed -i '/deep-engine_ut_gtest/d' ${overview_log}
                if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] ||
                    [ $(grep -c "PASSED" ${ut_log_name}) == 0 ] ||
                    [ $(grep -c "Segmentation fault" ${ut_log_name}) != 0 ] ||
                    [ $(grep -c "core dumped" ${ut_log_name}) != 0 ] ||
                    [ $(grep -c "==ERROR:" ${ut_log_name}) != 0 ]; then
                    ut_status='FAILURE'
                else
                    ut_status='SUCCESS'
                fi
                echo "deep-engine_ut_gtest_${device},${ut_status},${BUILD_URL}artifact/unittest/${device}/unit_test_gtest.log" | tee -a ${overview_log}
            fi
        '''
    }
    
}

def collectGPU_Log(){
    echo "------------  running collect GPU Test Log  -------------"
    sh ''' #!/bin/bash
        overview_log="${WORKSPACE}/summary_overview.log"
        gpu_log_name=$WORKSPACE/gputest/gpu_test.log
        if [ -f ${gpu_log_name} ];then
            if [ $(grep -c "FAILED" ${gpu_log_name}) != 0 ] ||
                [ $(grep -c "PASSED" ${gpu_log_name}) == 0 ] ||
                [ $(grep -c "Segmentation fault" ${gpu_log_name}) != 0 ] ||
                [ $(grep -c "core dumped" ${gpu_log_name}) != 0 ] ||
                [ $(grep -c "==ERROR:" ${gpu_log_name}) != 0 ]; then
                gputest_status='FAILURE'
            else
                gputest_status='SUCCESS'
            fi
        fi
        echo "sparselib_gpu_test,${gputest_status},${BUILD_URL}artifact/gputest/gpu_test.log" | tee -a ${overview_log}
        
    '''
}

def runGPUTest() {
    List GPUParams = [
        string(name: "nlp_url", value: "${nlp_url}"),
        string(name: "nlp_branch", value: "${nlp_commit}"),
        string(name: "MR_source_branch", value: "${MR_source_branch}"),
        string(name: "MR_target_branch", value: "${MR_target_branch}"),
        string(name: "sub_node_label", value: "${sub_node_label}"),
        string(name: "python_version",value:"${python_version}"),
        string(name: "val_branch", value: "${val_branch}"),
    ]
    sub_job_name = "sparse-lib-gpu"
   
    println("building GPU test")
    def downstreamJob = build job: sub_job_name, propagate: false, parameters: GPUParams
    catchError {
        copyArtifacts(
                projectName: sub_job_name,
                selector: specific("${downstreamJob.getNumber()}"),
                filter: '*log',
                fingerprintArtifacts: true,
                target: "gputest")
        archiveArtifacts artifacts: "gputest/**", allowEmptyArchive: true
    }
    def failed_build_url = downstreamJob.absoluteUrl
    if (downstreamJob.result != 'SUCCESS') {
        println("got sparse libgpu UT failed")
        currentBuild.result = "FAILURE"
        error("---GPU gtest test failed--- Details in ${failed_build_url}consoleText! ---- \n ${win_ut_failed_detail}")
    }      
}

def sparse_benchmark_jobs(device) {
    def jobs = [:]

    List benchmark_job_prarms = []
    benchmark_job_prarms += string(name: "sub_node_label", value: "${device}")
    benchmark_job_prarms += string(name: "nlp_url", value: "${nlp_url}")
    benchmark_job_prarms += string(name: "nlp_branch", value: "${nlp_commit}")
    benchmark_job_prarms += string(name: "MR_source_branch", value: "${MR_source_branch}")
    benchmark_job_prarms += string(name: "MR_target_branch", value: "${MR_target_branch}")
    benchmark_job_prarms += string(name: "python_version", value: "${python_version}")
    benchmark_job_prarms += string(name: "test_mode", value: "${test_mode}")
    benchmark_job_prarms += string(name: "val_branch", value: "${val_branch}")

    jobs["sparse lib benchmark on ${device}"] = {
        println("adding sparse lib test")
        def downstreamJob = build job: "local_sparse_lib_test", propagate: false, parameters: benchmark_job_prarms
        catchError {
            copyArtifacts(
                    projectName: "local_sparse_lib_test",
                    selector: specific("${downstreamJob.getNumber()}"),
                    fingerprintArtifacts: true,
                    target: "sparse_test/${device}",
                    optional: true)
            // Archive in Jenkins
            archiveArtifacts artifacts: "sparse_test/${device}/**", allowEmptyArchive: true
        }
        def failed_build_result = downstreamJob.result
        def failed_build_url = downstreamJob.absoluteUrl
        if (failed_build_result != 'SUCCESS') {
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                if (test_mode != 'nightly') {
                    currentBuild.result = "FAILURE"
                }
                sh " tail -n 50 sparse_test/${device}/**/*.log > ${WORKSPACE}/details.failed.build 2>&1 "
                failed_build_detail = readFile file: "${WORKSPACE}/details.failed.build"
                error("---- sparse lib test got failed! ---- Details in ${failed_build_url}consoleText! ---- \n ${failed_build_detail}")
            }
        }
    }
    
    return jobs
}


def buildBinaryLpot(){
    println("Building INC binary ...")
    List binaryBuildParams = [
            string(name: "inc_url", value: "${lpot_url}"),
            string(name: "inc_branch", value: "${lpot_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "LINUX_BINARY_CLASSES", value: "wheel"),
            string(name: "LINUX_PYTHON_VERSIONS", value: "${python_version}"),
            string(name: "WINDOWS_BINARY_CLASSES", value: ""),
            string(name: "WINDOWS_PYTHON_VERSIONS", value: ""),
    ]
    if(conda_env_mode == "conda") {
        binaryBuildParams += string(name: "conda_env", value: "lpot_conda_build")
        binaryBuildParams += string(name: "binary_class", value: "conda")
    }
    def downstreamJob = build job: "lpot-release-build", propagate: false, parameters: binaryBuildParams
    def job_id = downstreamJob.getNumber()
    echo "job_id: ${job_id}"
    echo "downstreamJob.getResult(): ${downstreamJob.getResult()}"
    if (downstreamJob.getResult() != "SUCCESS") {
        currentBuild.result = "FAILURE"
        failed_build_url = downstreamJob.absoluteUrl
        error("---- lpot wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
    }
    return job_id
}

def buildBinaryNLP() {
    println("Building nlp-toolkit binary ...")
    List binaryBuildParamsNLP = [
        string(name: "python_version", value: "${python_version}"),
        string(name: "nlp_url", value: "${nlp_url}"),
        string(name: "nlp_branch", value: "${nlp_branch}"),
        string(name: "MR_source_branch", value: "${MR_source_branch}"),
        string(name: "MR_target_branch", value: "${MR_target_branch}"),
        string(name: "val_branch", value: "${val_branch}"),
        string(name: "conda_env", value: "nlp_binary_build"),
        string(name: "binary_class", value: "wheel"),

    ]
    def downstreamJob = build job: "nlp-toolkit-release-wheel-build", propagate: false, parameters: binaryBuildParamsNLP
    def job_id = downstreamJob.getNumber()
    echo "job_id: ${job_id}"
    echo "downstreamJob.getResult(): ${downstreamJob.getResult()}"
    if (downstreamJob.getResult() != "SUCCESS") {
        currentBuild.result = "FAILURE"
        failed_build_url = downstreamJob.absoluteUrl
        echo "failed_build_url: ${failed_build_url}"
        error("---- nlp wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
    }
    return job_id
}


def generateReport() {
    dir(WORKSPACE) {
        def Jenkins_job_status = currentBuild.result
        println("Jenkins_job_status ==== " + Jenkins_job_status)
        if (Jenkins_job_status == null){
            Jenkins_job_status = "CHECK"
        }
        println("summary_dir = ${summary_dir}")
        //println("summary_dir_last = ${summary_dir_last}")
        
        withEnv([
            "report_title=${test_mode} test",
            "job_params=${arg_map}",
            "qtools_branch=${nlp_branch}",
            "qtools_commit=${nlp_commit}",
            "summary_dir=${summary_dir}",
            "device_list=${sub_node_label}",
            "overview_log=${overview_log}",
            "Jenkins_job_status=${Jenkins_job_status}",
            "ghprbActualCommit=${ghprbActualCommit}",
            "ghprbPullLink=${ghprbPullLink}",
            "ghprbPullId=${ghprbPullId}",
            "MR_source_branch=${MR_source_branch}",
            "MR_target_branch=${MR_target_branch}",
        ]) {
            sh '''
                chmod 775 ./lpot-validation/sparse_lib/generate_sparse_lib.sh
                devices=$(echo $device_list | tr ',' ' ')
                for device in ${devices[@]}; do
                    bash -x ./lpot-validation/sparse_lib/generate_sparse_lib.sh --device=${device}
                done
            '''
        }
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

        if (fileExists("report_icx.html")) {
            def text_comment = readFile file: "report_icx.html"
            writeFile file: "report.html", text: text_comment 
        } else if (fileExists("report_spr.html")){
            def text_comment = readFile file: "report_spr.html"
            writeFile file: "report.html", text: text_comment 
        } else if (fileExists("report_gpu.html")){
            def text_comment = readFile file: "report_gpu.html"
            writeFile file: "report.html", text: text_comment 
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
        overview_log = "${WORKSPACE}/summary_overview.log"
        writeFile file: overview_log, text: "Jenkins Job, Build Status, Build ID\n"
        summary_dir = "${WORKSPACE}/sparse_test"
        //summary_dir_last = "${WORKSPACE}/sparse_test/benchmark_log/ref"

        // Setup logs path
        download()
        if (test_mode == "pre-CI") {
            sh"""#!/bin/bash
                cd nlp-toolkit
                echo "MR_source_branch: "
                git show-ref -s remotes/origin/${MR_source_branch}
                echo "MR_target_branch: "
                git show-ref -s remotes/origin/${MR_target_branch}
            """
        } else {
            nlp_commit = sh (
                    script: 'cd nlp-toolkit && git rev-parse HEAD',
                    returnStdout: true
            ).trim()
            // set env to close duplicate nightly build
            env.INC_COMMIT = nlp_commit
            println("INC_COMMIT = " + env.INC_COMMIT)
            //if (ABORT_DUPLICATE_TEST) {
            //    previous_INC_COMMIT = currentBuild.previousBuiltBuild.buildVariables.INC_COMMIT
            //    if ( env.INC_COMMIT == previous_INC_COMMIT) {
            //        println("Kill the current Buils --> " + currentBuild.rawBuild.getFullDisplayName())
            //        currentBuild.rawBuild.doKill()
            //    }
            //}
        }
        println("start assigning tests jobs")
        def job_list = [:]
        if (RUN_UT) {
            sub_node_label.split(",").each { device ->
                println("Add unittest on ${device} to job...")
                def ut_jobs_gtest = unitTestBackend(device)
                job_list = job_list + ut_jobs_gtest
            }   
        }
        if (RUN_WINDOWS){
            println("Add windows to jod...")
            def windows_job = windowsJobBackend()
            job_list = job_list + windows_job   
        }
        if (RUN_CPPLINT) {
            println("Add cpplint scan to job...")
            job_list["cpplint Scan"] = {
                codeScan("cpplint")
            }
        }
        if (RUN_CLANGFORMAT) {
            println("Add clangformat scan to job...")
            job_list["clangformat Scan"] = {
                codeScan("clangformat")
            }
        }
        if (RUN_SPELLCHECK) {
            println("Add pyspelling scan to job...")
            job_list["pyspelling Scan"] = {
                codeScan("pyspelling")
            }
        }
        if (RUN_BANDIT) {
            job_list["Bandit Scan"] = {
                codeScan("bandit")
            }
        }
        if (CHECK_COPYRIGHT) {
            job_list["Copyright Check"] = {
                copyrightCheck()
            }
        }
        
        if (RUN_BENCHMARK) {
            println("Add benchmark to job...")
            sub_node_label.split(",").each { device ->
                println("running on ${device}")
                def benchmark_jobs = sparse_benchmark_jobs(device)
                job_list = job_list + benchmark_jobs
            }
        }

        if (is_gpu) {
            println("running on gpu")
            job_list["GPU Test"] = {
                runGPUTest()
            }
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
                if (RUN_UT) {
                    sub_node_label.split(",").each { device ->
                        println("collecting UT log on ${device}")
                        collectUT_backend_Log(device)
                    }
                    
                }
                if(RUN_WINDOWS){
                    collectWINUT_backend_Log()
                }
                if (is_gpu) {
                    collectGPU_Log()
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
        }
        stage("Send report") {
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                sendReport()
            }
        }
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log, *.html, *.xlsx, *.json, *.txt, benchmark_log/**, reference/**', excludes: null, allowEmptyArchive: true
            fingerprint: true
        }
        if (test_mode == "pre-CI") {
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
