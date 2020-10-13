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

// setting tensorflow_oob_models
tensorflow_oob_models = ""
if ('tensorflow_oob_models' in params && params.tensorflow_oob_models != '') {
    tensorflow_oob_models = params.tensorflow_oob_models
}
echo "tensorflow_oob_models: ${tensorflow_oob_models}"

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
pytorch_version = '1.5.0+cpu'
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
test_mode = ''
if ( MR_source_branch != ''){
    email_subject="MR${gitlabMergeRequestIid}: ${test_title}"
}else if ('test_mode' in params && params.test_mode == 'weekly'){
    test_mode = params.test_mode
    email_subject="Weekly: ${test_title}"
    RUN_UT=false
    currentBuild.description = params.weekly_description
}else if ('test_mode' in params && params.test_mode == 'extension'){
    test_mode = params.test_mode
    email_subject="${test_title}"
}else {
    email_subject="Nightly: ${test_title}"
}
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

binary_build_job = "lastSuccessfulBuild"
// internal benchmark model list, which should use combine mode
internal_benchmark_models = [
        "resnet50v1.0",
        "resnet50v1.5",
        "resnet101",
        "inception_v1",
        "inception_v2",
        "inception_v3",
        "inception_v4",
        "inception_resnet_v2",
        "vgg16",
        "vgg19",
        "densenet121",
        "densenet161",
        "densenet169",
        "resnetv2_50",
        "resnetv2_101",
        "resnetv2_152",
        "mobilenetv1",
        "mobilenetv2"
]

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
}

def BuildParams(job_framework, job_model, python_version, strategy){

    framework_version = ''
    if (job_framework == 'tensorflow'){
        framework_version = "${tensorflow_version}"
    }else if (job_framework == 'pytorch'){
        framework_version = "${pytorch_version}"
    }else if (job_framework == 'mxnet'){
        framework_version = "${mxnet_version}"
    }
    println("llsu-----> ${job_framework} : ${framework_version}")

    pass_mode=mode
    if ( internal_benchmark_models.contains(job_model) ){
        pass_mode = "combine"
    }
    println("llsu-----> ${pass_mode}")

    List ParamsPerJob = []

    ParamsPerJob += string(name: "sub_node_label", value: "${sub_node_label}")
    ParamsPerJob += string(name: "framework", value: "${job_framework}")
    ParamsPerJob += string(name: "framework_version", value: "${framework_version}")
    ParamsPerJob += string(name: "model", value: "${job_model}")
    ParamsPerJob += string(name: "ilit_url", value: "${ilit_url}")
    ParamsPerJob += string(name: "nigthly_test_branch", value: "${nigthly_test_branch}")
    ParamsPerJob += string(name: "MR_source_branch", value: "${MR_source_branch}")
    ParamsPerJob += string(name: "MR_target_branch", value: "${MR_target_branch}")
    ParamsPerJob += string(name: "python_version", value: "${python_version}")
    ParamsPerJob += string(name: "strategy", value: "${strategy}")
    ParamsPerJob += string(name: "test_mode", value: "${test_mode}")
    ParamsPerJob += string(name: "binary_build_job", value: "${binary_build_job}")
    ParamsPerJob += string(name: "mode", value: "${pass_mode}")

    return ParamsPerJob
}

def getPerfJobs() {

    def jobs = [:]

    job_frameworks = Frameworks.split(',')

    job_frameworks.each { job_framework ->
        def job_models = []
        if (job_framework == 'tensorflow'){
            //job_models=eval("${job_framework}_models")
            tf_oob_models = parseStrToList(tensorflow_oob_models)
            job_models = parseStrToList(tensorflow_models)
            job_models = job_models.plus(tf_oob_models)
        }else if (job_framework == 'pytorch'){
            job_models = parseStrToList(pytorch_models)
        }else if (job_framework == 'mxnet'){
            job_models = parseStrToList(mxnet_models)
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
            
                // execute build
                echo "${job_model}, ${job_framework}"

                downstreamJob = build job: "intel-iLit-validation", propagate: false, parameters: BuildParams(job_framework, job_model, python_version, strategy)

                copyArtifacts(
                    projectName: "intel-iLit-validation",
                    selector: specific("${downstreamJob.getNumber()}"),
                    filter: '*.log',
                    fingerprintArtifacts: true,
                    target: "${job_framework}/${job_model}",
                    optional: true)

                // Archive in Jenkins
                archiveArtifacts artifacts: "${job_framework}/${job_model}/**", allowEmptyArchive: true

                downstreamJobStatus = downstreamJob.result
                def failed_build_result = downstreamJob.result
                def failed_build_url = downstreamJob.absoluteUrl

                if (failed_build_result != 'SUCCESS' && (MR_source_branch != '' || test_mode == 'extension')) {
                    sh " tail -n 50 ${job_framework}/${job_model}/*.log > ${WORKSPACE}/details.failed.build 2>&1 "
                    failed_build_detail = readFile file: "${WORKSPACE}/details.failed.build"
                    currentBuild.result = "FAILURE"
                    error("---- ${job_framework}_${job_model} got failed! ---- Details in ${failed_build_url}consoleText! ---- \n ${failed_build_detail}")
                }

                if (failed_build_result != 'SUCCESS' && test_mode == 'weekly') {
                    currentBuild.result = "FAILURE"
                    error("---- ${job_framework}_${job_model} got failed! ---- Details in ${failed_build_url}consoleText! ---- \n ${failed_build_detail}")
                }

                if (downstreamJob.result != 'SUCCESS') {
                    error("---- ${job_framework}_${job_model} got failed! ---- Details in ${failed_build_url}consoleText! ---- \n ${failed_build_detail}")
                }
            }
        }
    }
    
    if (MR_source_branch != '') {
        echo "enable failFast"
        jobs.failFast = true
    }
    return jobs
}

def codeScan(tool) {
    List codeScanParams = [
        string(name: "TOOL", value: "${tool}"),
        string(name: "ilit_url", value: "${ilit_url}"),
        string(name: "nigthly_test_branch", value: "${nigthly_test_branch}"),
        string(name: "MR_source_branch", value: "${MR_source_branch}"),
        string(name: "MR_target_branch", value: "${MR_target_branch}"),
    ]

    downstreamJob = build job: "intel-iLit-format-scan", propagate: false, parameters: codeScanParams

    copyArtifacts(
        projectName: "intel-iLit-format-scan",
        selector: specific("${downstreamJob.getNumber()}"),
        filter: '*.json,*.log',
        fingerprintArtifacts: true,
        target: "format_scan",
        optional: true)

    text_comment = readFile file: "${overview_log}"
    writeFile file: "${overview_log}", text: text_comment + "intel-iLit-format-scan," + tool + "," + downstreamJob.result + "," + downstreamJob.number + "\n"

    // Archive in Jenkins
    archiveArtifacts artifacts: "format_scan/**", allowEmptyArchive: true
    
    if (downstreamJob.result != 'SUCCESS') {
        currentBuild.result = "FAILURE"
        if (MR_source_branch != '') {
            error("${tool} scan failed!")
        }
    }
}

def collectLog() {

    echo "---------------------------------------------------------"
    echo "------------  running collectLog  -------------"
    echo "---------------------------------------------------------"
    def dummy_inference_models = [
        "resnet50v1.5",
        "resnet50v1",
        "inception_v1",
        "wide_deep_large_ds"
    ]

    precision_list = ["fp32", "int8"]
    if ( MR_source_branch != '' ) {
        echo "Setting mode_list to latency..."
        mode_list = ["latency"]
    } else {
        echo "Parsing ${mode} to list..."
        mode_list = parseStrToList(mode)
    }
    echo "Global mode list: ${mode_list}"


    job_frameworks = Frameworks.split(',')
    job_frameworks.each { job_framework ->
        job_models = []
        if (job_framework == 'tensorflow'){
            tf_oob_models = parseStrToList(tensorflow_oob_models)
            job_models = parseStrToList(tensorflow_models)
            job_models = job_models.plus(tf_oob_models)
        }else if (job_framework == 'pytorch'){
            job_models = parseStrToList(pytorch_models)
        }else if (job_framework == 'mxnet'){
            job_models = parseStrToList(mxnet_models)
        }

        if (MR_source_branch != ''){
            add_models_list = collectModelList(job_framework)
            job_models = job_models.plus(add_models_list)
            job_models.unique()
        }

        job_models.each { job_model ->
            echo "-------- ${job_framework} - ${job_model} --------"
            tf_oob_models = parseStrToList(tensorflow_oob_models)
            // reset mode_list for each model
            if ( MR_source_branch != '' ) {
                mode_list = ["latency"]
            } else {
                mode_list = parseStrToList(mode)
            }

            if ( job_model in tf_oob_models || job_model == 'style_transfer') {
                echo "Found OOB model or \"style_transfer\" model. Setting \"latency\" mode."
                mode_list = ["latency"]
            }
            if ( internal_benchmark_models.contains(job_model) ){
                echo "Found \"internal benchmark model\". Setting \"combine\" mode."
                mode_list = ["combine"]
            }
            println("mode_list---->" + mode_list)
            // Generate tuning info log
            withEnv(["current_model=$job_model","current_framework=$job_framework","MR=$MR_source_branch"]) {
                sh '''#!/bin/bash -x
                    cd $WORKSPACE
                    chmod 775 ilit-validation/scripts/collect_logs_ilit.sh
                    ilit-validation/scripts/collect_logs_ilit.sh --model=${current_model} --framework=${current_framework} --mode=tuning --mr=${MR}             
                '''
            }
            // For pytorch we collect throughput and accuracy for int8 model from tuning log.
            if (job_framework == "pytorch") {
                return
            }

            if (MR_source_branch != '' && dummy_inference_models.contains(job_model)) {
                return
            }
            precision_list.each { precision ->
                mode_list.each { mode ->
                    echo "Getting results for ${job_framework} - ${job_model} - ${precision} - ${mode}"
                    withEnv(["current_model=$job_model", "current_framework=$job_framework", "precision=$precision", "mode=$mode"]) {
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

    List unitTestParams = [
            string(name: "binary_build_job", value: "${binary_build_job}"),
            string(name: "ilit_url", value: "${ilit_url}"),
            string(name: "nigthly_test_branch", value: "${nigthly_test_branch}"),
            string(name: "MR_source_branch", value: "${MR_source_branch}"),
            string(name: "MR_target_branch", value: "${MR_target_branch}"),
    ]
    downstreamJob = build job: "iLit-unit-test", propagate: false, parameters: unitTestParams

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

    if (downstreamJob.result != 'SUCCESS') {
        error("Unit tests failed.")
    }
}

def buildBinary(){
    List binaryBuildParams = [
            string(name: "ilit_url", value: "${ilit_url}"),
            string(name: "nigthly_test_branch", value: "${nigthly_test_branch}"),
            string(name: "MR_source_branch", value: "${MR_source_branch}"),
            string(name: "MR_target_branch", value: "${MR_target_branch}"),
    ]
    downstreamJob = build job: "iLiT-release-wheel-build", propagate: false, parameters: binaryBuildParams
    
    binary_build_job = downstreamJob.getNumber()
    echo "binary_build_job: ${binary_build_job}"
    echo "downstreamJob.getResult(): ${downstreamJob.getResult()}"
    if (downstreamJob.getResult() != "SUCCESS") {
        currentBuild.result = "FAILURE"
        failed_build_url = downstreamJob.absoluteUrl
        echo "failed_build_url: ${failed_build_url}"
        error("---- iLiT wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
    }
}

def generateReport() {
    if(refer_build != 'x0') {
        def refer_job_name
        if(test_mode == "extension"){
            refer_job_name="intel-iLit-validation-top-nightly"
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
            script: 'cd ilit-models && git rev-parse HEAD',
            returnStdout: true
        ).trim()
        def Jenkins_job_status = currentBuild.result
        println("Jenkins_job_status ==== " + Jenkins_job_status)
        if (Jenkins_job_status == null){
            Jenkins_job_status = "CHECK"
        }
        withEnv([
            "qtools_branch=${nigthly_test_branch}",
            "qtools_commit=${qtools_commit}",
            "summaryLog=${SUMMARYTXT}",
            "summaryLogLast=${summaryLogLast}",
            "tuneLog=${TUNETXT}",
            "tuneLogLast=${tuneLogLast}",
            "overview_log=${overview_log}",
            "Jenkins_job_status=${Jenkins_job_status}"
        ]) {
            sh '''
                if [[ ${qtools_branch} == '' ]]; then
                    chmod 775 ./ilit-validation/scripts/generate_ilit_report_mr.sh
                    ./ilit-validation/scripts/generate_ilit_report_mr.sh
                else
                    chmod 775 ./ilit-validation/scripts/generate_ilit_report.sh
                    ./ilit-validation/scripts/generate_ilit_report.sh
                fi
            '''
        }
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
                attachmentsPattern: "",
                mimeType: 'text/html'

    }
}

def collectModelList(framework) {
    add_models_list=[]
    dir("$WORKSPACE/ilit-models"){
        def modelconf =  jsonParse(readFile("$WORKSPACE/ilit-validation/config/model_list.json"))

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

node( node_label ) {

    try {
        cleanup()
        dir('ilit-validation') {
            retry(5) {
                checkout scm
            }
        }

        // Setup logs path
        echo "WORKSPACE IS ${WORKSPACE}"
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

        download()

        stage('Build wheel'){
            buildBinary()
        }

        def job_list = [:]
        if (RUN_UT) {
            job_list["Unit Test"] = {
                unitTest()
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
        
        def perf_jobs = getPerfJobs()
        job_list = job_list + perf_jobs
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
            collectLog()
        }
        stage("Generate report") {
            generateReport()
        }

        stage("Send report") {
            sendReport()
        }
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log,*.html', excludes: null, allowEmptyArchive: true
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
