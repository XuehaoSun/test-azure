@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}
credential = '5da0b320-00b8-4312-b653-36d4cf980fcb'

// setting test_title
test_title = "iLiT Tests"
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
sub_node_label = "ILIT"
if ('node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${node_label}"

// chose test frameworks
Frameworks = "tensorflow,mxnet,pytorch"
if ('Frameworks' in params && params.Frameworks != '') {
    Frameworks = params.Frameworks
}
echo "Frameworks: ${Frameworks}"

// setting tensorflow_version
tensorflow_version = '1.15.2'
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

// setting mxnet_version
mxnet_version = '1.6.0'
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
pytorch_version = '1.5.0'
if ('pytorch_version' in params && params.pytorch_version != '') {
    pytorch_version = params.pytorch_version
}
echo "pytorch_version: ${pytorch_version}"

// setting mxnet models
pytorch_models = ""
if ('pytorch_models' in params && params.pytorch_models != '') {
    pytorch_models = params.pytorch_models
}
echo "pytorch_models: ${pytorch_models}"

// ilit-validation branch to get test groovy
validation_branch = 'master'
if ('validation_branch' in params && params.validation_branch != '') {
    validation_branch = params.validation_branch
}
echo "validation_branch: $validation_branch"

ilit_url="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool"
if ('ilit_url' in params && params.ilit_url != ''){
    ilit_url = params.ilit_url
}
echo "ilit_url is ${ilit_url}"

try { echo "RUN_PYLINT=${RUN_PYLINT}"; } catch (Exception e) { RUN_PYLINT="false" ; echo "RUN_PYLINT=${RUN_PYLINT}" }

nigthly_test_branch = ''
MR_source_branch = ''
MR_target_branch = ''
if ('nigthly_test_branch' in params && params.nigthly_test_branch != '') {
    nigthly_test_branch = params.nigthly_test_branch
}else{
    if ("${gitlabSourceBranch}" != '') {
        MR_source_branch = "${gitlabSourceBranch}"
        MR_target_branch = "${gitlabTargetBranch}"
        updateGitlabCommitStatus state: 'pending'
        gitLabConnection('gitlab.devtools.intel.com')
    }
}
echo "nigthly_test_branch: $nigthly_test_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

// setting refer_build
refer_build = "x0"
if ('refer_build' in params && params.refer_build != '') {
    refer_build = params.refer_build
}
echo "Running ${refer_build}"

email_subject="${test_title}"
if ( MR_source_branch != ''){
    email_subject="MR${gitlabMergeRequestIid}: ${test_title}"
}else {
    email_subject="Nightly: ${test_title}"
}
echo "email_subject: $email_subject"

def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        sudo rm -rf *
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
    if(MR_source_branch != ''){
        checkout changelog: true, poll: true, scm: [
                $class                           : 'GitSCM',
                branches                         : [[name: "${MR_source_branch}"]],
                browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                doGenerateSubmoduleConfigurations: false,
                extensions                       : [
                        [$class: 'RelativeTargetDirectory', relativeTargetDir: "ilit-models"],
                        [$class: 'CloneOption', timeout: 60],
                        [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${MR_target_branch}"]]
                ],
                submoduleCfg                     : [],
                userRemoteConfigs                : [
                        [credentialsId: "${credential}",
                         url          : "${ilit_url}"]
                ]
        ]
    }
    else {
        checkout changelog: true, poll: true, scm: [
                $class                           : 'GitSCM',
                branches                         : [[name: "${nigthly_test_branch}"]],
                browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                doGenerateSubmoduleConfigurations: false,
                extensions                       : [
                        [$class: 'RelativeTargetDirectory', relativeTargetDir: "ilit-models"],
                        [$class: 'CloneOption', timeout: 60]
                ],
                submoduleCfg                     : [],
                userRemoteConfigs                : [
                        [credentialsId: "${credential}",
                         url          : "${ilit_url}"]
                ]
        ]
    }
}

def BuildParams(job_framework, job_model){

    framework_version = ''
    if (job_framework == 'tensorflow'){
        framework_version = "${tensorflow_version}"
    }else if (job_framework == 'pytorch'){
        framework_version = "${pytorch_version}"
    }else if (job_framework == 'mxnet'){
        framework_version = "${mxnet_version}"
    }
    echo "llsu-----> ${job_framework} : ${framework_version}"

    List ParamsPerJob = []

    ParamsPerJob += string(name: "sub_node_label", value: "${sub_node_label}")
    ParamsPerJob += string(name: "framework", value: "${job_framework}")
    ParamsPerJob += string(name: "framework_version", value: "${framework_version}")
    ParamsPerJob += string(name: "model", value: "${job_model}")
    ParamsPerJob += string(name: "ilit_url", value: "${ilit_url}")
    ParamsPerJob += string(name: "nigthly_test_branch", value: "${nigthly_test_branch}")
    ParamsPerJob += string(name: "MR_source_branch", value: "${MR_source_branch}")
    ParamsPerJob += string(name: "MR_target_branch", value: "${MR_target_branch}")

    return ParamsPerJob
}

def doBuild() {

    def jobs = [:]

    job_frameworks = Frameworks.split(',')

    job_frameworks.each { job_framework ->
        job_models = []
        if (job_framework == 'tensorflow'){
            //job_models=eval("${job_framework}_models")
            job_models = tensorflow_models.split(',')
        }else if (job_framework == 'pytorch'){
            job_models = pytorch_models.split(',')
        }else if (job_framework == 'mxnet'){
            job_models = mxnet_models.split(',')
        }
        if (MR_source_branch != ''){
            add_models_list = collectModelList(job_framework)
            job_models = job_models.plus(add_models_list)
            job_models.unique()
        }
        echo "${job_models}"
        echo "llsu-----> ${job_framework}"
        job_models.each { job_model ->
            jobs["${job_framework}_${job_model}"] = {
                catchError {
                    stage("Run Model ${job_model} on ${job_framework}") {
                        // execute build
                        echo "${job_model}, ${job_framework}"
                        def downstreamJob = build job: "test_suyue_intel-iLit-validation", propagate: false, parameters: BuildParams(job_framework, job_model)

                            catchError {

                                copyArtifacts(
                                        projectName: "test_suyue_intel-iLit-validation",
                                        selector: specific("${downstreamJob.getNumber()}"),
                                        filter: '*.log',
                                        fingerprintArtifacts: true,
                                        target: "${job_framework}/${job_model}")

                                // Archive in Jenkins
                                archiveArtifacts artifacts: "${job_framework}/${job_model}/**"
                            }

                            if (downstreamJob.getResult() != 'SUCCESS')
                            {
                                currentBuild.result = "FAILURE"
                            }
                    }
                }
            }
        }
    }

    parallel jobs

}

def pylintScan() {
    catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
        List pylintScanParams = [
            string(name: "ilit_url", value: "${ilit_url}"),
            string(name: "nigthly_test_branch", value: "${nigthly_test_branch}"),
            string(name: "MR_source_branch", value: "${gitlabSourceBranch}"),
            string(name: "MR_target_branch", value: "${gitlabTargetBranch}"),
        ]
        def downstreamJob = build job: "intel-iLit-format-scan", propagate: false, parameters: pylintScanParams
        copyArtifacts(
            projectName: "intel-iLit-format-scan",
            selector: specific("${downstreamJob.getNumber()}"),
            filter: '*.json',
            fingerprintArtifacts: true,
            target: "format_scan",
            optional: true)

        // Archive in Jenkins
        archiveArtifacts artifacts: "format_scan/**", allowEmptyArchive: true

        if (downstreamJob.getResult() != "SUCCESS") {
            currentBuild.result = "FAILURE"
        }
    }
}

def collectLog() {

    echo "---------------------------------------------------------"
    echo "------------  running collectLog  -------------"
    echo "---------------------------------------------------------"
    precision_list = ["fp32", "int8"]
    if ( MR_source_branch != '' ) {
        mode_list = ["throughput"]
    } else {
        mode_list = ["throughput", "latency"]
    }

    job_frameworks = Frameworks.split(',')
    job_frameworks.each { job_framework ->
        job_models = []
        if (job_framework == 'tensorflow'){
            job_models = tensorflow_models.split(',')
        }else if (job_framework == 'pytorch'){
            job_models = pytorch_models.split(',')
        }else if (job_framework == 'mxnet'){
            job_models = mxnet_models.split(',')
        }

        if (MR_source_branch != ''){
            add_models_list = collectModelList(job_framework)
            job_models = job_models.plus(add_models_list)
            job_models.unique()
        }

        job_models.each { job_model ->
            // Generate tuning info log
            withEnv(["current_model=$job_model","current_framework=$job_framework"]) {
                sh '''#!/bin/bash -x
                    cd $WORKSPACE
                    chmod 775 ilit-validation/scripts/collect_logs_ilit.sh
                    ilit-validation/scripts/collect_logs_ilit.sh --model=${current_model} --framework=${current_framework} --mode=tuning              
                '''
            }
            precision_list.each { precision ->
                mode_list.each { mode ->
                    // For pytorch we collect throughput and accuracy for int8 model from tuning log.
                    if (job_framework == "pytorch" && precision == "int8") {
                        return
                    }
                    withEnv(["current_model=$job_model","current_framework=$job_framework","precision=$precision","mode=$mode"]) {

                        sh '''#!/bin/bash -x
                            cd $WORKSPACE
                            chmod 775 ilit-validation/scripts/collect_logs_ilit.sh
                            ilit-validation/scripts/collect_logs_ilit.sh --model=${current_model} --framework=${current_framework} --precision=${precision} --mode=${mode}              
                        '''
                    }
                }
            }
        }
    }
    echo "done running collectLog ......."
    stash allowEmpty: true, includes: "*.log, *.json", name: "logfile"

}

def unitTest() {

    catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
        List unitTestParams = [
                string(name: "ilit_url", value: "${ilit_url}"),
                string(name: "nigthly_test_branch", value: "${nigthly_test_branch}"),
                string(name: "MR_source_branch", value: "${gitlabSourceBranch}"),
                string(name: "MR_target_branch", value: "${gitlabTargetBranch}"),
        ]
        def downstreamJob = build job: "iLit-unit-test", propagate: false, parameters: unitTestParams

        text_commnet = readFile file: "${overview_log}"
        writeFile file: "${overview_log}", text: text_commnet + "iLit-unit-test," + downstreamJob.result + "," + downstreamJob.number + "\n"

        copyArtifacts(
                projectName: "iLit-unit-test",
                selector: specific("${downstreamJob.getNumber()}"),
                filter: '*.log',
                fingerprintArtifacts: true,
                target: "unittest")

        // Archive in Jenkins
        archiveArtifacts artifacts: "unittest/**", allowEmptyArchive: true

        if (downstreamJob.getResult() != "SUCCESS") {
            currentBuild.result = "FAILURE"
        }
    }

}

def collectModelList(framework) {
    add_models_list=[]
    dir("$WORKSPACE/ilit-models"){
        def modelconf =  jsonParse(readFile("$WORKSPACE/ilit-validation/config/model_list.json"))
        sh (
            script: 'git --no-pager diff --name-only $(git show-ref -s remotes/origin/${MR_target_branch}) | grep \'examples\' > diff.log',
            returnStdout: true
        ).trim()
        add_models_dir = sh (
                script: 'echo $(cat diff.log | grep "${framework}" | cut -d/ -f3 | sort -u)',
                returnStdout: true
        ).trim()
        if ( add_models_dir != '' ){
            add_models_dir_list = add_models_dir.split(' ')
            add_models_dir_list.each{ per_model_dir ->
                sub_add_models_list = modelconf."${framework}"."${per_model_dir}"
                add_models_list=add_models_list.plus(sub_add_models_list)
            }
        }
    }
    return add_models_list
}

node( node_label ) {

    try {
        cleanup()
        dir('ilit-validation') {
            checkout scm
        }

        download()

        SUMMARYTXT = "${WORKSPACE}/summary.log"
        writeFile file: SUMMARYTXT, text: "Framework;Platform;Precision;Model;Mode;Type;BS;Value;Url\n"
        summaryLogLast = "${WORKSPACE}/reference/summary.log"

        TUNETXT = "${WORKSPACE}/tuning_info.log"
        writeFile file: TUNETXT, text: "Framework;Model;Strategy;Tune_time\n"
        tuneLogLast = "${WORKSPACE}/reference/tuning_info.log"

        // over view log
        overview_log = "${WORKSPACE}/summary_overview.log"
        writeFile file: overview_log,
            text: "Jenkins Job, Build Status, Build ID\n"

//        stage("unit test"){
//            unitTest()
//        }
//
//        if (RUN_PYLINT == "true") {
//            stage("Pylint Scan") {
//                pylintScan()
//            }
//        }

        stage("tune-parallel") {
            doBuild()
        }

        stage("Collect Logs") {
            collectLog()
        }

        stage("report"){

            if(refer_build != 'x0') {
                copyArtifacts(
                        projectName: currentBuild.projectName,
                        selector: specific("${refer_build}"),
                        filter: 'summary.log,tuning_info.log',
                        fingerprintArtifacts: true,
                        target: "reference")
            }

            dir(WORKSPACE) {
                qtools_commit = sh (
                    script: 'cd ilit-models && git rev-parse HEAD',
                    returnStdout: true
                ).trim()

                withEnv([
                    "qtools_branch=${nigthly_test_branch}",
                    "qtools_commit=${qtools_commit}",
                    "summaryLog=${SUMMARYTXT}",
                    "summaryLogLast=${summaryLogLast}",
                    "tuneLog=${TUNETXT}",
                    "tuneLogLast=${tuneLogLast}",
                    "overview_log=${overview_log}"
                ]) {
                    sh '''
                        chmod 775 ./ilit-validation/scripts/generate_ilit_report_mr.sh
                        ./ilit-validation/scripts/generate_ilit_report_mr.sh
                    '''
                }
            }
        }

        stage("send email") {
            dir("$WORKSPACE") {
                if (MR_source_branch != '') {
                    recipient_list = 'suyue.chen@intel.com,' + "${gitlabUserEmail}"
                    if ('recipient_list' in params && params.recipient_list != '') {
                        recipient_list = params.recipient_list + ',' + gitlabUserEmail
                    }
                } else {
                    recipient_list = 'suyue.chen@intel.com'
                    if ('recipient_list' in params && params.recipient_list != '') {
                        recipient_list = params.recipient_list
                    }
                }

                emailext subject: "${email_subject}",
                        to: "${recipient_list}",
                        replyTo: "${recipient_list}",
                        body: '''${FILE,path="report.html"}''',
                        attachmentsPattern: "",
                        mimeType: 'text/html'

            }
        }

    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e

    } finally {

        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log,*.html', excludes: null
            fingerprint: true
        }

        if (currentBuild.result == 'FAILURE' || currentBuild.result == 'ABORTED') {
            echo "pipeline failed"
            updateGitlabCommitStatus state: 'failed'
        } else {
            echo "pipeline success"
            updateGitlabCommitStatus state: 'success'
        }
    }
}
