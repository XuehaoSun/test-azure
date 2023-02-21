credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'

// parameters
python_version="3.10"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

node_label=""
if ('node_label' in params && params.node_label != ''){        
    node_label = params.node_label
}
echo "node_label is ${node_label}"

sub_node_label=""
if ('sub_node_label' in params && params.sub_node_label != ''){
    sub_node_label = params.sub_node_label
}
echo "sub_node_label is ${sub_node_label}"

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

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

TF2ONNX_models=""
if ('TF2ONNX_models' in params && params.TF2ONNX_models != ''){
    TF2ONNX_models = params.TF2ONNX_models
}
echo "TF2ONNX_models is ${TF2ONNX_models}"

PT2ONNX_models=""
if ('PT2ONNX_models' in params && params.PT2ONNX_models != ''){
    PT2ONNX_models = params.PT2ONNX_models
}
echo "PT2ONNX_models is ${PT2ONNX_models}"

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

recipient_list=""
if ('recipient_list' in params && params.recipient_list != ''){
    recipient_list = params.recipient_list
}
echo "recipient_list is ${recipient_list}"

val_branch=""
if ('val_branch' in params && params.val_branch != ''){
    val_branch = params.val_branch
}
echo "val_branch is ${val_branch}"

ABORT_DUPLICATE_TEST=""
if ('ABORT_DUPLICATE_TEST' in params && params.ABORT_DUPLICATE_TEST != ''){
    ABORT_DUPLICATE_TEST = params.ABORT_DUPLICATE_TEST
}
echo "ABORT_DUPLICATE_TEST is ${ABORT_DUPLICATE_TEST}"

export_only=false
if (params.export_only != null){
    export_only=params.export_only
}
echo "export_only = ${export_only}"


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
    checkout changelog: true, poll: true, scm: [
            $class                           : 'GitSCM',
            branches                         : [[name: "${inc_branch}"]],
            browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
            doGenerateSubmoduleConfigurations: false,
            extensions                       : [
                    [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                    [$class: 'CloneOption', timeout: 15]
            ],
            submoduleCfg                     : [],
            userRemoteConfigs                : [
                    [credentialsId: "${credential}",
                     url          : "${inc_url}"]
            ]
    ]
}

def export_test() {
    def jobs = [:]
    if( frameworks=="TF2ONNX") {
        export_jobs = TF2ONNX_models.split(',')
    }else{
        export_jobs = PT2ONNX_models.split(',')
    }
    
    def system = "linux"
    export_jobs.each { export_job ->

        jobs["${export_job}"] = {

            echo "---test --- model export --- ${export_job} --- ${system}"
            def String subnode_label = sub_node_label + " && " + system;

            def List jobParams = [
                    string(name: "python_version", value: "${python_version}"),
                    string(name: "sub_node_label", value: "${subnode_label}"),
                    string(name: "binary_build_job", value: "${binary_build_job}"),
                    string(name: "frameworks", value: "${frameworks}"),
                    string(name: "tensorflow_version", value: "${tensorflow_version}"),
                    string(name: "pytorch_version", value: "${pytorch_version}"),
                    string(name: "onnx_version", value: "${onnx_version}"),
                    string(name: "onnxruntime_version", value: "${onnxruntime_version}"),
                    string(name: "model_name", value: "${export_job}"),
                    string(name: "val_branch", value: "${val_branch}"),
                    string(name: "inc_url", value: "${inc_url}"),
                    string(name: "inc_branch", value: "${inc_branch}"),
                    booleanParam(name: "export_only", value: export_only)
            ]

            def downstreamJob
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                downstreamJob = build job: "inc-model-export-test", propagate: false, parameters: jobParams

                catchError {
                    copyArtifacts(
                            projectName: "inc-model-export-test",
                            selector: specific("${downstreamJob.getNumber()}"),
                            filter: '*.log',
                            fingerprintArtifacts: true,
                            target: "${export_job}")

                    // Archive in Jenkins
                    archiveArtifacts artifacts: "${export_job}/**"
                }

                def failed_build_result = downstreamJob.result
                def failed_build_url = downstreamJob.absoluteUrl

                if (downstreamJob && failed_build_result != 'SUCCESS') {
                    currentBuild.result = "FAILURE"
                    throw new Exception("Downstream Job failed.")
                }
            }
        }
    }
    parallel jobs
}

def buildBinary(){
    List binaryBuildParams = [
            string(name: "inc_url", value: "${inc_url}"),
            string(name: "inc_branch", value: "${inc_branch}"),
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

def generateReport(){
    dir(WORKSPACE) {
        lpot_commit = sh (
                script: 'cd lpot-models && git rev-parse HEAD',
                returnStdout: true
        ).trim()
        withEnv([
                "inc_branch=${inc_branch}",
                "lpot_commit=${lpot_commit}",
                "modelExportSummaryLog=${SUMMARYLOG}"
        ]) {
            sh '''
                chmod 775 ./lpot-validation/scripts/export_model_test/generate_model_export_report.sh
                ./lpot-validation/scripts/export_model_test/generate_model_export_report.sh
            '''
        }
    }
}

def sendReport(){
    //todo
    sh '''
        echo "report"
    '''
}

node( node_label ){
    try {
        cleanup()

        // Setup logs path
        echo "WORKSPACE IS ${WORKSPACE}"
        SUMMARYLOG = "${WORKSPACE}/summary_all.log"
        writeFile file: SUMMARYLOG, text: "OS;Platform;Framework;Version;Precision;Model;DataSource;Mode;BS;Value;Url\n"

        dir('lpot-validation') {
            checkout scm
        }
        stage("download"){
            download()
        }
        stage('Build wheel'){
            if ("${binary_build_job}" == "") {
                buildBinary()
            }else{
                echo "use the binary build job pass by....."
            }
        }
        stage("export test") {
            export_test()
        }

    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        stage("result check"){
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                export_jobs.each { model_name ->
                    echo "-------- ${model_name} --------"
                        dir(WORKSPACE) {
                            withEnv([
                                "model_name=${model_name}",
                                "model_export_summary_log=${SUMMARYLOG}"
                            ]) {
                                sh '''
                                    chmod 775 ./lpot-validation/scripts/export_model_test/collect_log_model_export.sh
                                    ./lpot-validation/scripts/export_model_test/collect_log_model_export.sh
                                '''
                            }
                        }
                }
            }
        }

        stage("Generate report") {
            generateReport()
        }

        stage("Send report") {
            sendReport()
        }
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log,*.html', excludes: null
            fingerprint: true
        }
    }
}
