credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'

// parameters
node_label = "lpot"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

// setting node_label
sub_node_label = "lpot"
if ('sub_node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${sub_node_label}"

lpot_url="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

requirement_list="ruamel.yaml==0.17.4"
if ('requirement_list' in params && params.requirement_list != ''){
    requirement_list = params.requirement_list
}
echo "requirement_list is ${requirement_list}"

python_version="3.7"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

lpot_branch = ''
if ('lpot_branch' in params && params.lpot_branch != '') {
    lpot_branch = params.lpot_branch
}
echo "lpot_branch: $lpot_branch"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

feature_list = ''
if ('feature_list' in params && params.feature_list != '') {
    feature_list = params.feature_list
}
echo "feature_list: ${feature_list}"

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
            branches                         : [[name: "${lpot_branch}"]],
            browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
            doGenerateSubmoduleConfigurations: false,
            extensions                       : [
                    [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                    [$class: 'CloneOption', timeout: 20]
            ],
            submoduleCfg                     : [],
            userRemoteConfigs                : [
                    [credentialsId: "${credential}",
                     url          : "${lpot_url}"]
            ]
    ]
}

def parallel_jobs() {
    def jobs = [:]
    PLATFORMS.split(";").each { systemConfig ->
        def system = systemConfig.split(":")[0]
        platforms = systemConfig.split(":")[1].split(",")
        platforms.each { platform ->
            def cpu = platform
            if (system == "windows") {
                throw Exception("Windows is not yet supported in feature tests.")
            }

            job_features = feature_list.split(',')

            job_features.each { job_feature ->

                jobs["${job_feature}"] = {

                    echo "---test --- feature --- ${job_feature} --- ${system} --- ${cpu}"
                    def subnode_label = sub_node_label + " && " + system;

                    if (!['any', '*'].contains(cpu)) {
                        subnode_label += " && " + cpu
                    }

                    List featureParams = [
                            string(name: "python_version", value: "${python_version}"),
                            string(name: "sub_node_label", value: "${subnode_label}"),
                            string(name: "binary_build_job", value: "${binary_build_job}"),
                            string(name: "lpot_url", value: "${lpot_url}"),
                            string(name: "lpot_branch", value: "${lpot_branch}"),
                            string(name: "feature_name", value: "${job_feature}"),
                            string(name: "val_branch", value: "${val_branch}"),
                            string(name: "cpu", value: "${cpu}"),
                            string(name: "os", value: "${system}")
                    ]

                    def downstreamJob
                    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                        downstreamJob = build job: "lpot-feature-test", propagate: false, parameters: featureParams

                        catchError {
                            copyArtifacts(
                                    projectName: "lpot-feature-test",
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
        }
    }
    parallel jobs
}

def buildBinary(){
    List binaryBuildParams = [
            string(name: "inc_url", value: "${lpot_url}"),
            string(name: "inc_branch", value: "${lpot_branch}"),
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
                "lpot_branch=${lpot_branch}",
                "lpot_commit=${lpot_commit}",
                "summaryLog=${SUMMARYLOG}"
        ]) {
            sh '''
                chmod 775 ./lpot-validation/scripts/feature_test/generate_feature_report.sh
                ./lpot-validation/scripts/feature_test/generate_feature_report.sh
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
        writeFile file: SUMMARYLOG, text: "PLATFORM;FEATURE;STATUS;URL\n"

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
        stage("feature test") {
            parallel_jobs()
        }

    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        stage("result check"){
            job_features = feature_list.split(',')
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                job_features.each { feature ->
                    echo "-------- ${feature} --------"
                    dir(WORKSPACE) {
                        withEnv([
                                "feature_name=${feature}",
                                "summaryLog=${SUMMARYLOG}"
                        ]) {
                            sh '''
                        chmod 775 ./lpot-validation/scripts/feature_test/collect_log/collect_log_${feature_name}.sh
                        ./lpot-validation/scripts/feature_test/collect_log/collect_log_${feature_name}.sh
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
