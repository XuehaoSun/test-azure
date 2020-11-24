credential = 'lab_tfbot'

// parameters
node_label = "ilit"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

// setting node_label
sub_node_label = "ilit"
if ('sub_node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${sub_node_label}"

ilit_url="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool"
if ('ilit_url' in params && params.ilit_url != ''){
    ilit_url = params.ilit_url
}
echo "ilit_url is ${ilit_url}"

requirement_list="ruamel.yaml"
if ('requirement_list' in params && params.requirement_list != ''){
    requirement_list = params.requirement_list
}
echo "requirement_list is ${requirement_list}"

python_version="3.6"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

binary_build_job="lastSuccessfulBuild"
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

ilit_branch = ''
if ('ilit_branch' in params && params.ilit_branch != '') {
    ilit_branch = params.ilit_branch
}
echo "ilit_branch: $ilit_branch"

feature_list = ''
if ('feature_list' in params && params.feature_list != '') {
    feature_list = params.feature_list
}
echo "feature_list: ${feature_list}"

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
    checkout changelog: true, poll: true, scm: [
            $class                           : 'GitSCM',
            branches                         : [[name: "${ilit_branch}"]],
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

def parallel_jobs() {
    def jobs = [:]

    job_features = feature_list.split(',')

    job_features.each { job_feature ->

        jobs["${job_feature}"] = {

            echo "---test --- feature --- ${job_feature}"

            List featureParams = [
                    string(name: "sub_node_label", value: "${sub_node_label}"),
                    string(name: "binary_build_job", value: "${binary_build_job}"),
                    string(name: "ilit_url", value: "${ilit_url}"),
                    string(name: "ilit_branch", value: "${ilit_branch}"),
                    string(name: "feature_name", value: "${job_feature}")
            ]

            def downstreamJob
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                downstreamJob = build job: "iLit-feature-test", propagate: false, parameters: featureParams

                catchError {
                    copyArtifacts(
                            projectName: "iLit-feature-test",
                            selector: specific("${downstreamJob.getNumber()}"),
                            filter: '*.log',
                            fingerprintArtifacts: true,
                            target: "${job_feature}")

                    // Archive in Jenkins
                    archiveArtifacts artifacts: "${job_feature}/**"
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
            string(name: "ilit_url", value: "${ilit_url}"),
            string(name: "ilit_branch", value: "${ilit_branch}")
    ]
    def downstreamJob = build job: "iLiT-release-wheel-build", propagate: false, parameters: binaryBuildParams

    binary_build_job = downstreamJob.getNumber()
    if (downstreamJob.getResult() != "SUCCESS") {
        currentBuild.result = "FAILURE"
        failed_build_url = downstreamJob.absoluteUrl
        error("---- iLiT wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
    }
}

def generateReport(){
    dir(WORKSPACE) {
        ilit_commit = sh (
                script: 'cd ilit-models && git rev-parse HEAD',
                returnStdout: true
        ).trim()
        withEnv([
                "ilit_branch=${ilit_branch}",
                "ilit_commit=${ilit_commit}",
                "summaryLog=${SUMMARYLOG}"
        ]) {
            sh '''
                chmod 775 ./ilit-validation/scripts/feature_test/generate_feature_report.sh
                ./ilit-validation/scripts/feature_test/generate_feature_report.sh
            '''
        }
    }
}

def sendReport(){

}

node( node_label ){
    try {
        cleanup()

        // Setup logs path
        echo "WORKSPACE IS ${WORKSPACE}"
        SUMMARYLOG = "${WORKSPACE}/summary.log"
        writeFile file: SUMMARYLOG, text: "FEATURE;STATUS\n"

        dir('ilit-validation') {
            checkout scm
        }
        stage("download"){
            download()
        }
        stage('Build wheel'){
            buildBinary()
        }
        stage("feature test") {
            parallel_jobs()
        }

    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        stage("result check"){
            job_features = feature_list.split(',')
            job_features.each { feature ->
                echo "-------- ${feature} --------"
                dir(WORKSPACE){
                    withEnv([
                            "feature_name=${feature}",
                            "summaryLog=${SUMMARYLOG}"
                    ]) {
                        sh '''
                        chmod 775 ./ilit-validation/scripts/feature_test/collect_log_${feature_name}.sh
                        ./ilit-validation/scripts/feature_test/collect_log_${feature_name}.sh
                    '''
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
