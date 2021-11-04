credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'
sys_lpot_val_credentialsId = "dcf0dff2-03fb-45b0-9e64-5b4db466bee5"

def autoCancel = false
// setting node_label
node_label = "master"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

// setting node_label
sub_node_label = ""
if ('sub_node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${node_label}"

// setting test_title
test_title = "DeepEngine PreCI"
if ('test_title' in params && params.test_title != '') {
    test_title = params.test_title
}
echo "Running named ${test_title}"

python_version = "3.6"
if ('python_version' in params && params.python_version != '') {
    python_version = params.python_version
}
echo "Python version: ${python_version}"

lpot_url="git@github.com:intel-innersource/frameworks.ai.lpot.intel-lpot.git"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

RUN_UT=true
if (params.RUN_UT != null){
    RUN_UT=params.RUN_UT
}
echo "RUN_UT = ${RUN_UT}"

RUN_COVERAGE=true
if (params.RUN_COVERAGE != null){
    RUN_COVERAGE=params.RUN_COVERAGE
}
echo "RUN_COVERAGE = ${RUN_COVERAGE}"

RUN_CPPLINT=false
if (params.RUN_CPPLINT != null){
    RUN_CPPLINT=params.RUN_CPPLINT
}
echo "RUN_CPPLINT = ${RUN_CPPLINT}"

RUN_PYLINT=false
if (params.RUN_PYLINT != null){
    RUN_PYLINT=params.RUN_PYLINT
}
echo "RUN_PYLINT = ${RUN_PYLINT}"

RUN_BANDIT=false
if (params.RUN_BANDIT != null){
    RUN_BANDIT=params.RUN_BANDIT
}
echo "RUN_BANDIT = ${RUN_BANDIT}"

RUN_SPELLCHECK=false
if (params.RUN_SPELLCHECK != null){
    RUN_SPELLCHECK=params.RUN_SPELLCHECK
}
echo "RUN_SPELLCHECK = ${RUN_SPELLCHECK}"

// setting refer_build
refer_build = "x0"
if ('refer_build' in params && params.refer_build != '') {
    refer_build = params.refer_build
}
echo "Running ${refer_build}"

lpot_commit = ''
lpot_branch = ''
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
if ( PR_source_branch != '') {
    // githubPRComment comment: "Pipeline started: [Job-${BUILD_NUMBER}](${BUILD_URL})"
    ActualCommitAuthorEmail=env.GITHUB_PR_AUTHOR_EMAIL
    TriggerAuthorEmail=env.GITHUB_PR_TRIGGER_SENDER_EMAIL
    ghprbActualCommit=env.GITHUB_PR_HEAD_SHA
    ghprbPullLink=env.GITHUB_PR_URL
    ghprbPullId=env.GITHUB_PR_NUMBER

    echo "ActualCommitAuthorEmail: ${ActualCommitAuthorEmail}"
    echo "TriggerAuthorEmail: ${TriggerAuthorEmail}"
    echo "ghprbActualCommit: ${ghprbActualCommit}"
    echo "ghprbPullLink: ${ghprbPullLink}"
    echo "ghprbPullId: ${ghprbPullId}"
}

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

// ncores_per_instance:bs
benchmark_config="4:1,28:64,28:1,7:64,7:2"
if ('benchmark_config' in params && params.benchmark_config != ''){
    benchmark_config=params.benchmark_config
}
echo "benchmark_config: ${benchmark_config}"

benchmark_model_list=''
if ('benchmark_model_list' in params && params.benchmark_model_list != ''){
    benchmark_model_list=params.benchmark_model_list
}
echo "benchmark_model_list: ${benchmark_model_list}"

accuracy_model_list=''
if ('accuracy_model_list' in params && params.accuracy_model_list != ''){
    accuracy_model_list=params.accuracy_model_list
}
echo "accuracy_model_list: ${accuracy_model_list}"

inc_model_list=''
if ('inc_model_list' in params && params.inc_model_list != ''){
    inc_model_list=params.inc_model_list
}
echo "inc_model_list: ${inc_model_list}"

inc_mode  = 'accuracy,latency'
if ('inc_mode' in params && params.inc_mode != '') {
    inc_mode = params.inc_mode
}
echo "inc_mode: ${inc_mode}"

test_mode = 'engine'
if ('test_mode' in params && params.test_mode != ''){
    test_mode = params.test_mode
}

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

precision = 'int8,fp32'
if ('precision' in params && params.precision != '') {
    precision = params.precision
}
echo "Precision: ${precision}"

if ( PR_source_branch != ''){
    email_subject="PR${ghprbPullId}: ${test_title}"
}else{
    email_subject="Nightly: ${test_title}"
}
echo "email_subject: $email_subject"

pipeline_failFast=false
if (params.pipeline_failFast != null){
    pipeline_failFast=params.pipeline_failFast
}
echo "pipeline_failFast = ${pipeline_failFast}"

binary_build_job = ""

def cleanup() {
    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
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
    }
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
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "deep-engine"],
                            [$class: 'CloneOption', timeout: 10],
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
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "deep-engine"],
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
                sh """
                    curl \
                    -X POST \
                    -H \"Accept: application/vnd.github.v3+json\" \
                    -H \"Authorization: Bearer $LPOT_VAL_GH_TOKEN\" \
                    --proxy child-prc.intel.com:913 \
                    https://api.github.com/repos/intel-innersource/frameworks.ai.lpot.intel-lpot/statuses/${commit_sha} \
                    -d '{\"state\": \"${state}\", \"context\": \"Jenkins CI\", \"target_url\": \"${RUN_DISPLAY_URL}\", \"description\": \"${description}\"}'
                """
            }
        }
    } catch (e) {
        println("Could not set status \"${state}\" for ${env.GITHUB_PR_HEAD_SHA} commit.")
        currentBuild.result = "FAILURE"
        if (e.toString() == "org.jenkinsci.plugins.workflow.steps.FlowInterruptedException" && e.getCauses().size() == 0) {
            println("Setting autoCancel flag to true.")
            autoCancel = true
            println("autoCancel: ${autoCancel}")
        }
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
                    --proxy child-prc.intel.com:913 \
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

def unitTestJobs(unit_test_mode) {
    def ut_jobs = [:]
    List UTBuildParams = [
            string(name: "deepengine_url", value: "${lpot_url}"),
            string(name: "deepengine_branch", value: "${lpot_branch}"),
            string(name: "PR_source_branch", value: "${PR_source_branch}"),
            string(name: "PR_target_branch", value: "${PR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "binary_build_job", value: "${binary_build_job}"),
            booleanParam(name: "run_coverage", value: RUN_COVERAGE),
            string(name: "unit_test_mode", value: "${unit_test_mode}")
    ]
    ut_jobs[unit_test_mode] = {
        downstreamJob = build job: "deep-engine-unit-test", propagate: false, parameters: UTBuildParams
        catchError {
            copyArtifacts(
                    projectName: "deep-engine-unit-test",
                    selector: specific("${downstreamJob.getNumber()}"),
                    filter: '*.log, *.txt',
                    fingerprintArtifacts: true,
                    target: "unittest")

            archiveArtifacts artifacts: "unittest/**", allowEmptyArchive: true
        }

        if (downstreamJob.result != 'SUCCESS') {
            def sub_job_url = downstreamJob.absoluteUrl
            withEnv(["sub_job_url=${sub_job_url}", "ut_mode=${unit_test_mode}"]){
                sh '''#!/bin/bash
                overview_log="${WORKSPACE}/summary_overview.log"
                echo "deep-engine_ut_${ut_mode},FAILURE,${sub_job_url}" | tee -a ${overview_log}
                '''
            }
            currentBuild.result = "FAILURE"
            error("---${unit_test_mode} test failed---")
        }
    }
    return ut_jobs
}

def perfJobs() {
    def perf_jobs = [:]
    def subnode_label = sub_node_label + " && linux";
    List perfParams = [
            string(name: "node_label", value: "${subnode_label}"),
            string(name: "deepengine_url", value: "${lpot_url}"),
            string(name: "deepengine_branch", value: "${lpot_branch}"),
            string(name: "PR_source_branch", value: "${PR_source_branch}"),
            string(name: "PR_target_branch", value: "${PR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "benchmark_config", value: "${benchmark_config}"),
            string(name: "model_list", value: "${benchmark_model_list}"),
            string(name: "binary_build_job", value: "${binary_build_job}"),
            string(name: "python_version", value: "${python_version}")
    ]
    perf_jobs["benchmark"] = {
        downstreamJob = build job: "deep-engine-benchmark", propagate: false, parameters: perfParams
        catchError {
            copyArtifacts(
                    projectName: "deep-engine-benchmark",
                    selector: specific("${downstreamJob.getNumber()}"),
                    filter: '**/*',
                    fingerprintArtifacts: true,
                    target: "benchmark")

            archiveArtifacts artifacts: "benchmark/**", allowEmptyArchive: true
        }

        sh '''#!/bin/bash 
            if [ -f ${WORKSPACE}/benchmark/summary ]; then 
                cat ${WORKSPACE}/benchmark/summary >> ${WORKSPACE}/summary.log
            fi
        '''

        def sub_job_url = downstreamJob.absoluteUrl
        if (downstreamJob.result != 'SUCCESS') {
            withEnv(["sub_job_url=${sub_job_url}"]){
                sh '''#!/bin/bash
                overview_log="${WORKSPACE}/summary_overview.log"
                echo "deep-engine_benchmark,FAILURE,${sub_job_url}" | tee -a ${overview_log}
                '''
            }
            currentBuild.result = "FAILURE"
            error("---benchmark failed---")
        }else{
            sh '''#!/bin/bash
                overview_log="${WORKSPACE}/summary_overview.log"
                echo "deep-engine_benchmark,SUCCESS,${BUILD_URL}artifact/benchmark/summary.log" | tee -a ${overview_log}
            '''
        }
    }
    return perf_jobs
}

def accJobs() {
    def acc_jobs = [:]
    def subnode_label = sub_node_label + " && linux";
    List perfParams = [
            string(name: "node_label", value: "${subnode_label}"),
            string(name: "deepengine_url", value: "${lpot_url}"),
            string(name: "deepengine_branch", value: "${lpot_branch}"),
            string(name: "PR_source_branch", value: "${PR_source_branch}"),
            string(name: "PR_target_branch", value: "${PR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "model_list", value: "${accuracy_model_list}")
    ]
    acc_jobs["accuracy"] = {
        downstreamJob = build job: "deep-engine-accuracy", propagate: false, parameters: perfParams
        catchError {
            copyArtifacts(
                    projectName: "deep-engine-accuracy",
                    selector: specific("${downstreamJob.getNumber()}"),
                    filter: '**/*',
                    fingerprintArtifacts: true,
                    target: "accuracy")

            archiveArtifacts artifacts: "accuracy/**", allowEmptyArchive: true
        }

        sh '''#!/bin/bash 
            if [ -f ${WORKSPACE}/accuracy/summary.log ]; then 
                cat ${WORKSPACE}/accuracy/summary.log >> ${WORKSPACE}/summary.log
            fi
        '''

        def sub_job_url = downstreamJob.absoluteUrl
        if (downstreamJob.result != 'SUCCESS') {
            withEnv(["sub_job_url=${sub_job_url}"]){
                sh '''#!/bin/bash
                overview_log="${WORKSPACE}/summary_overview.log"
                echo "deep-engine_accuracy,FAILURE,${sub_job_url}" | tee -a ${overview_log}
                '''
            }
            currentBuild.result = "FAILURE"
            error("---accuracy failed---")
        }else{
            withEnv(["sub_job_url=${sub_job_url}"]) {
                sh '''#!/bin/bash
                overview_log="${WORKSPACE}/summary_overview.log"
                echo "deep-engine_accuracy,SUCCESS,${sub_job_url}" | tee -a ${overview_log}
                '''
            }
        }
    }
    return acc_jobs
}

def incParams(job_framework, job_model, python_version, strategy, cpu, os){

    framework_version = 'na'

    println("llsu-----> ${cpu} : ${os} : ${job_framework} : ${framework_version}: ${inc_mode}")

    def subnode_label = sub_node_label + " && " + os;

    if (!['any', '*'].contains(cpu)) {
        subnode_label += " && " + cpu
    }

    List ParamsPerJob = []

    ParamsPerJob += string(name: "sub_node_label", value: "${subnode_label}")
    ParamsPerJob += string(name: "framework", value: "${job_framework}")
    ParamsPerJob += string(name: "framework_version", value: "${framework_version}")
    ParamsPerJob += string(name: "model", value: "${job_model}")
    ParamsPerJob += string(name: "lpot_url", value: "${lpot_url}")
    ParamsPerJob += string(name: "lpot_branch", value: "${lpot_commit}")
    ParamsPerJob += string(name: "MR_source_branch", value: "${PR_source_branch}")
    ParamsPerJob += string(name: "MR_target_branch", value: "${PR_target_branch}")
    ParamsPerJob += string(name: "python_version", value: "${python_version}")
    ParamsPerJob += string(name: "strategy", value: "${strategy}")
    ParamsPerJob += string(name: "test_mode", value: "${test_mode}")
    ParamsPerJob += string(name: "binary_build_job", value: "${binary_build_job}")
    ParamsPerJob += string(name: "mode", value: "${inc_mode}")
    ParamsPerJob += string(name: "tuning_timeout", value: "${tuning_timeout}")
    ParamsPerJob += string(name: "max_trials", value: "${max_trials}")
    ParamsPerJob += booleanParam(name: "tune_only", value: tune_only)
    ParamsPerJob += string(name: "val_branch", value: "${val_branch}")
    ParamsPerJob += string(name: "cpu", value: "${cpu}")
    ParamsPerJob += string(name: "os", value: "${os}")
    ParamsPerJob += string(name: "refer_build", value: "${refer_build}")
    ParamsPerJob += string(name: "precision", value: "${precision}")

    return ParamsPerJob
}

def incJobs() {
    def jobs = [:]

    // Get models list
    def job_models = []
    job_models = inc_model_list.split(',')

    job_models.each { job_model ->
        jobs["${job_model}_engine"] = {

            // execute build
            println("Current engine model is --> "+"${job_model}")
            sub_jenkins_job = "deep-engine-inc"
            job_framework = "engine"
            downstreamJob = build job: sub_jenkins_job, propagate: false, parameters: incParams(job_framework, job_model, python_version, 'basic', 'clx8280', 'linux')

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
                    currentBuild.result = "FAILURE"
                    sh " tail -n 50 ${job_framework}/${job_model}/*.log > ${WORKSPACE}/details.failed.build 2>&1 "
                    failed_build_detail = readFile file: "${WORKSPACE}/details.failed.build"
                    error("---- ${job_framework}_${job_model} got failed! ---- Details in ${failed_build_url}consoleText! ---- \n ${failed_build_detail}")
                }
            }

            echo "Getting results for ${job_framework} - ${job_model}"
            sh """#!/bin/bash -x
                if [[ -f ${WORKSPACE}/${job_framework}/${job_model}/tuning_info.log ]]; then
                    cat ${WORKSPACE}/${job_framework}/${job_model}/tuning_info.log >> ${WORKSPACE}/tuning_info.log
                else
                    echo "linux;Unknown;${job_framework};N/A;${job_model};basic;;;${RUN_DISPLAY_URL};;;" >> ${WORKSPACE}/tuning_info.log
                fi
            """
            sh """#!/bin/bash -x
                if [[ -f ${WORKSPACE}/${job_framework}/${job_model}/summary.log ]]; then
                    cat ${WORKSPACE}/${job_framework}/${job_model}/summary.log >> ${WORKSPACE}/summary.log
                else
                    echo "Unknown;Unknown;${job_framework};N/A;INT8;${job_model};Inference;Latency;;;${RUN_DISPLAY_URL}" >> ${WORKSPACE}/summary.log
                    echo "Unknown;Unknown;${job_framework};N/A;FP32;${job_model};Inference;Latency;;;${RUN_DISPLAY_URL}" >> ${WORKSPACE}/summary.log
                fi
            """
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
            string(name: "deepengine_url", value: "${lpot_url}"),
            string(name: "deepengine_branch", value: "${lpot_branch}"),
            string(name: "PR_source_branch", value: "${PR_source_branch}"),
            string(name: "PR_target_branch", value: "${PR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}")
    ]

    downstreamJob = build job: "deep-engine-code-scan", propagate: false, parameters: codeScanParams

    copyArtifacts(
            projectName: "deep-engine-code-scan",
            selector: specific("${downstreamJob.getNumber()}"),
            filter: '*.log',
            fingerprintArtifacts: true,
            target: "code_scan",
            optional: true)

    overview_log="${WORKSPACE}/summary_overview.log"

    text_comment = readFile file: "${overview_log}"
    writeFile file: "${overview_log}", text: text_comment + "deep-engine-code-scan," + tool + "," + downstreamJob.result + "," + downstreamJob.number + "\n"

    // Archive in Jenkins
    archiveArtifacts artifacts: "code_scan/**", allowEmptyArchive: true

    if (downstreamJob.result != 'SUCCESS') {
        currentBuild.result = "FAILURE"
        if (PR_source_branch != '') {
            error("${tool} scan failed!")
        }
    }
}

def collectUTLog() {
    echo "------------  running collectUTLog  -------------"
    sh ''' #!/bin/bash
        overview_log="${WORKSPACE}/summary_overview.log"
        ut_log_name=$WORKSPACE/unittest/unit_test_gtest.log
        if [ -f ${ut_log_name} ];then
            sed -i '/deep-engine_ut_gtest/d' ${overview_log}
            if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "PASSED" ${ut_log_name}) == 0 ]; then
                ut_status='FAILURE'
            else
                ut_status='SUCCESS'
            fi
            echo "deep-engine_ut_gtest,${ut_status},${BUILD_URL}artifact/unittest/unit_test_gtest.log" | tee -a ${overview_log}
        fi
        ut_log_name=$WORKSPACE/unittest/unit_test_pytest.log
        if [ -f ${ut_log_name} ];then
            sed -i '/deep-engine_ut_pytest/d' ${overview_log}
            if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
                ut_status='FAILURE'
            else
                ut_status='SUCCESS'
            fi
            echo "deep-engine_ut_pytest,${ut_status},${BUILD_URL}artifact/unittest/unit_test_pytest.log" | tee -a ${overview_log}
        fi
    '''
}

def generateReport() {
    if(refer_build != 'x0') {
        catchError {
            def refer_job_name = currentBuild.projectName
            copyArtifacts(
                    projectName: refer_job_name,
                    selector: specific("${refer_build}"),
                    filter: 'summary.log,tuning_info.log',
                    fingerprintArtifacts: true,
                    target: "reference")
        }
    }

    dir(WORKSPACE) {
        def Jenkins_job_status = currentBuild.result
        println("Jenkins_job_status = " + Jenkins_job_status)
        if (Jenkins_job_status == null){
            Jenkins_job_status = "CHECK"
        }
        withEnv([
                "lpot_branch=${lpot_branch}",
                "lpot_commit=${lpot_commit}",
                "summaryLog=${summaryLog}",
                "summaryLogLast=${summaryLogLast}",
                "tuneLog=${tuneLog}",
                "tuneLogLast=${tuneLogLast}",
                "overviewLog=${overviewLog}",
                "Jenkins_job_status=${Jenkins_job_status}",
                "ghprbActualCommit=${ghprbActualCommit}",
                "ghprbPullLink=${ghprbPullLink}",
                "ghprbPullId=${ghprbPullId}",
                "PR_source_branch=${PR_source_branch}",
                "PR_target_branch=${PR_target_branch}",
                "coverage_summary=${coverage_summary}",
                "coverage_summary_base=${coverage_summary_base}",

        ]) {
            sh '''
                if [[ ${lpot_branch} == '' ]]; then
                    bash ${WORKSPACE}/lpot-validation/deep-engine/scripts/generate_deep-engine_report_pr.sh
                else
                    bash ${WORKSPACE}/lpot-validation/deep-engine/scripts/generate_deep-engine_report.sh
                fi
            '''
        }
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
                mimeType: 'text/html'

    }
}

def buildBinary(){
    pypi_version='default'
    List binaryBuildParams = [
            string(name: "python_version", value: "${python_version}"),
            string(name: "lpot_url", value: "${lpot_url}"),
            string(name: "lpot_branch", value: "${lpot_commit}"),
            string(name: "MR_source_branch", value: "${PR_source_branch}"),
            string(name: "MR_target_branch", value: "${PR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "pypi_version", value: "${pypi_version}")
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

node( node_label ) {
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
        download()
        if (lpot_branch != ''){
            lpot_commit = sh (
                    script: 'cd deep-engine && git rev-parse HEAD',
                    returnStdout: true
            ).trim()
        }

        if (PR_source_branch != ''){
            sh"""#!/bin/bash
                cd deep-engine
                echo "PR_source_branch: "
                git show-ref -s remotes/origin/${PR_source_branch}
                echo "PR_target_branch: "
                git show-ref -s remotes/origin/${PR_target_branch}
            """
        }

        stage('Build wheel'){
            buildBinary()
        }

        // Setup logs path
        echo "WORKSPACE IS ${WORKSPACE}"
        summaryLog = "${WORKSPACE}/summary.log"
        writeFile file: summaryLog, text: "OS;Platform;Framework;Version;Precision;Model;Mode;Type;BS;Value;Url\n"
        summaryLogLast = "${WORKSPACE}/reference/summary.log"

        tuneLog = "${WORKSPACE}/tuning_info.log"
        writeFile file: tuneLog, text: "OS;Platform;Framework;Version;Model;Strategy;Tune_time\n"
        tuneLogLast = "${WORKSPACE}/reference/tuning_info.log"

        // over view log
        overviewLog = "${WORKSPACE}/summary_overview.log"
        writeFile file: overviewLog, text: "Jenkins Job, Build Status, Build ID\n"

        // coverage summary
        coverage_summary = "${WORKSPACE}/unittest/coverage_summary.log"
        coverage_summary_base = "${WORKSPACE}/unittest/coverage_summary_base.log"


        def job_list = [:]
        if (RUN_UT) {
            println("Add ut job...")
            def gtest_ut_job = unitTestJobs('gtest')
            def pytest_ut_job = unitTestJobs('pytest')
            job_list = job_list + gtest_ut_job + pytest_ut_job
        }
        if (RUN_CPPLINT){
            println("Add cpplint scan to job...")
            job_list["cpplint Scan"] = {
                codeScan("cpplint")
            }
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

        if (benchmark_model_list != ''){
            println("Add benchmark job...")
            def perf_jobs = perfJobs()
            job_list = job_list + perf_jobs
        }

        if (accuracy_model_list != ''){
            println("Add accuracy job...")
            def acc_jobs = accJobs()
            job_list = job_list + acc_jobs
        }

        if (inc_model_list != ''){
            println("Add INC job...")
            def inc_jobs = incJobs()
            job_list = job_list + inc_jobs
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
        error(e.toString())

    } finally {

        stage("Collect Logs") {
            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
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