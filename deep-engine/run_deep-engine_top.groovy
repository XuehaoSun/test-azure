credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'
sys_lpot_val_credentialsId = "dcf0dff2-03fb-45b0-9e64-5b4db466bee5"

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

deepengine_url="git@github.com:intel-innersource/frameworks.ai.deep-engine.intel-deep-engine.git"
if ('deepengine_url' in params && params.deepengine_url != ''){
    deepengine_url = params.deepengine_url
}
echo "deepengine_url is ${deepengine_url}"

RUN_UT=true
if (params.RUN_UT != null){
    RUN_UT=params.RUN_UT
}
echo "RUN_UT = ${RUN_UT}"

// setting refer_build
refer_build = "x0"
if ('refer_build' in params && params.refer_build != '') {
    refer_build = params.refer_build
}
echo "Running ${refer_build}"

deepengine_branch = ''
PR_source_branch = ''
PR_target_branch = ''
if ('deepengine_branch' in params && params.deepengine_branch != '') {
    deepengine_branch = params.deepengine_branch
}else{
    PR_source_branch = params.GITHUB_PR_SOURCE_BRANCH
    PR_target_branch = params.GITHUB_PR_TARGET_BRANCH
}
echo "deepengine_branch: $deepengine_branch"
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

model_list='bert_large,bert_base'
if ('model_list' in params && params.model_list != ''){
    model_list=params.model_list
}
echo "model_list: ${model_list}"

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
                            [$class: 'CloneOption', timeout: 60],
                            [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${PR_target_branch}"]]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${deepengine_url}"]
                    ]
            ]
        }
        else {
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${deepengine_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "deep-engine"],
                            [$class: 'CloneOption', timeout: 60]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${deepengine_url}"]
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
                    https://api.github.com/repos/intel-innersource/frameworks.ai.deep-engine.intel-deep-engine/statuses/${commit_sha} \
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
                sh """
                    curl \
                    -X POST \
                    -H \"Accept: application/vnd.github.v3+json\" \
                    -H \"Authorization: Bearer $LPOT_VAL_GH_TOKEN\" \
                    --proxy child-prc.intel.com:913 \
                    https://api.github.com/repos/intel-innersource/frameworks.ai.deep-engine.intel-deep-engine/issues/${issueNumber}/comments \
                    -d '{\"body\": \"${comment}\"}'
                """
            }
        }
    } catch (e) {
        println("Could not add comment for PR #${env.GITHUB_PR_NUMBER}")
        currentBuild.result = "FAILURE"
        error(e.toString())
    }
}

def unitTestJobs() {
    def ut_jobs = [:]
    List UTBuildParams = [
            string(name: "deepengine_url", value: "${deepengine_url}"),
            string(name: "deepengine_branch", value: "${deepengine_branch}"),
            string(name: "PR_source_branch", value: "${PR_source_branch}"),
            string(name: "PR_target_branch", value: "${PR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}")
    ]
    ut_jobs["gtest"] = {
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
            withEnv(["sub_job_url=${sub_job_url}"]){
                sh '''#!/bin/bash
                overview_log="${WORKSPACE}/summary_overview.log"
                echo "deep-engine_ut_gtest,FAILURE,${sub_job_url}" | tee -a ${overview_log}
                '''
            }
            currentBuild.result = "FAILURE"
            error("---gtest failed---")
        }
    }
    return ut_jobs
}

def perfJobs() {
    def perf_jobs = [:]
    List perfParams = [
            string(name: "node_label", value: "${sub_node_label}"),
            string(name: "deepengine_url", value: "${deepengine_url}"),
            string(name: "deepengine_branch", value: "${deepengine_branch}"),
            string(name: "PR_source_branch", value: "${PR_source_branch}"),
            string(name: "PR_target_branch", value: "${PR_target_branch}"),
            string(name: "val_branch", value: "${val_branch}"),
            string(name: "benchmark_config", value: "${benchmark_config}"),
            string(name: "model_list", value: "${model_list}")
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
                echo "deep-engine_benchmark,SUCCESS,${BUILD_URL}artifact/benchmark/summary.txt" | tee -a ${overview_log}
            '''
        }
    }
    return perf_jobs
}

def collectUTLog() {
    echo "------------  running collectUTLog  -------------"
    sh ''' #!/bin/bash
        overview_log="${WORKSPACE}/summary_overview.log"
        ut_log_name=$WORKSPACE/unittest/unit_test_gtest.log
        if [ ! -f ${ut_log_name} ];then
            if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "PASSED" ${ut_log_name}) == 0 ]; then
                ut_status='FAILURE'
            else
                ut_status='SUCCESS'
            fi
            echo "deep-engine_ut_gtest,${ut_status},${BUILD_URL}artifact/unittest/unit_test_gtest.log" | tee -a ${overview_log}
        fi  
    '''
}

def generateReport() {
    if(refer_build != 'x0') {
        def refer_job_name = currentBuild.projectName
        copyArtifacts(
                projectName: refer_job_name,
                selector: specific("${refer_build}"),
                filter: 'benchmark/summary.txt',
                fingerprintArtifacts: true,
                target: "reference")
    }

    dir(WORKSPACE) {
        def Jenkins_job_status = currentBuild.result
        println("Jenkins_job_status = " + Jenkins_job_status)
        if (Jenkins_job_status == null){
            Jenkins_job_status = "CHECK"
        }
        withEnv([
                "deepengine_branch=${deepengine_branch}",
                "summaryLog=${summaryLog}",
                "summaryLogLast=${summaryLogLast}",
                "overviewLog=${overviewLog}",
                "Jenkins_job_status=${Jenkins_job_status}",
                "ghprbActualCommit=${ghprbActualCommit}",
                "ghprbPullLink=${ghprbPullLink}",
                "ghprbPullId=${ghprbPullId}",
                "PR_source_branch=${PR_source_branch}",
                "PR_target_branch=${PR_target_branch}"

        ]) {
            sh '''
                if [[ ${deepengine_branch} == '' ]]; then
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

        // Setup logs path
        echo "WORKSPACE IS ${WORKSPACE}"
        summaryLog = "${WORKSPACE}/benchmark/summary.txt"
        summaryLogLast = "${WORKSPACE}/reference/benchmark/summary.txt"

        // over view log
        overviewLog = "${WORKSPACE}/summary_overview.log"
        writeFile file: overviewLog, text: "Jenkins Job, Build Status, Build ID\n"


        def job_list = [:]
        if (RUN_UT) {
            println("Add ut job...")
            def ut_jobs = unitTestJobs()
            job_list = job_list + ut_jobs
        }

        if (model_list != ''){
            println("Add benchmark job...")
            def perf_jobs = perfJobs()
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
                // githubPRComment comment: "Pipeline failed! [Job-${BUILD_NUMBER}](${BUILD_URL}) [Test Report](${BUILD_URL}artifact/report.html)"
                updateGithubCommitStatus("failure", "Pipeline failed!")
                comment = "Pipeline failed! [Job-${BUILD_NUMBER}](${BUILD_URL}) [Test Report](${BUILD_URL}artifact/report.html)"
                createGithubIssueComment(comment)
            } else {
                echo "pipeline success"
                // githubPRComment comment: "Pipeline success! [Job-${BUILD_NUMBER}](${BUILD_URL}) [Test Report](${BUILD_URL}artifact/report.html)"
                updateGithubCommitStatus("success", "Pipeline success!")
                comment = "Pipeline success! [Job-${BUILD_NUMBER}](${BUILD_URL}) [Test Report](${BUILD_URL}artifact/report.html)"
                createGithubIssueComment(comment)
            }
        }
    }
}