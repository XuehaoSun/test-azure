top_node_label = "master"
if ('top_node_label' in params && params.top_node_label != '') {
    top_node_label = params.top_node_label
}
echo "Running on node ${top_node_label}"

def Exception(String step, def e) {
    def msg = "Job name: $JOB_NAME\nBuild ID:$BUILD_ID\nStep: $step\nException: $e"
    echo "$msg"
    currentBuild.description = "Failed on $step step"
    currentBuild.result = "FAILURE"
}


def cleanup() {
    try {
        stage("Cleanup") {
            dir(WORKSPACE) {
                sh """
                    rm -rf *
                    rm -rf .git
                    sudo rm -rf *
                    sudo rm -rf .git
                """
            }
        }
    } catch(e) {
        Exception("Cleanup", e)
        throw e
    }
}

def getConfigurationsTree() {
    try {
        stage("Get configurations tree") {
            def configurationsTree = [
                "linux": [
                    "binaryClasses": normalizeList(LINUX_BINARY_CLASSES),
                    "pythonVersions": normalizeList(LINUX_PYTHON_VERSIONS),
                ],
                "windows": [
                    "binaryClasses":  normalizeList(WINDOWS_BINARY_CLASSES),
                    "pythonVersions":  normalizeList(WINDOWS_PYTHON_VERSIONS),
                ]
            ]
            return configurationsTree
        }
    } catch(e) {
        Exception("GetConfigurationsTree", e)
        throw e
    }
}


def  normalizeList(elements) {
    if (elements instanceof List) {
        return elements
    }
    if (elements instanceof String) {
        if (elements == "") {
            return []
        }
        return elements.split(",")
    }
    raise Exception("Could not normalize list. Unknown class: ${elements.getClass()}")
}


def getJobsList(configurationsTree) {
    try {
        def jobsList = [:]
        stage("Get jobs list") {
            configurationsTree.each { osEntry ->
                def osName = osEntry.key
                def binaryClasses = osEntry.value["binaryClasses"]
                def pythonVersions = osEntry.value["pythonVersions"]
                if (!binaryClasses || !pythonVersions) {
                    println("Skipping ${osName} builds as there is not enough infomation about binary classes or python versions!")
                    return
                }
                def possibleCombinations = [pythonVersions, binaryClasses].combinations()
                possibleCombinations.each { combination ->
                    def String pythonVersion = combination[0]
                    def String binaryClass = combination[1]
                    jobsList["${osName}_${binaryClass}_${pythonVersion}"] = {
                        getSingleJob(osName, binaryClass, pythonVersion)
                    }
                }
            }
        }
        jobsList.failFast = false
        return jobsList
    } catch(e) {
        Exception("GetJobsList", e)
        throw e
    }
}


def getSingleJob(String osName, String binaryClass, String pythonVersion) {
    stage("Build ${osName} ${binaryClass} with python ${pythonVersion}") {
        println("Starting ${osName} ${binaryClass} build job with python ${pythonVersion}")
        def buildParams = getBuildParams(osName, binaryClass, pythonVersion)
        def jobName = getBuildJobByOS(osName)

        def downstreamJob = build job: jobName,
                           propagate: false,
                           parameters: buildParams

        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
            if (downstreamJob.result != "SUCCESS") {
                error("${osName} ${binaryClass} build job with python ${osName} failed.")
            }
        }
        copyArtifacts(
            projectName: jobName,
            selector: specific("${downstreamJob.getNumber()}"),
            filter: "*.whl, *.tar.bz2, *.tar.gz",
            fingerprintArtifacts: true,
            target: "${osName}_binaries/${binaryClass}/${pythonVersion}",
            optional: true)
    }
}

def getBuildJobByOS(osName) {
    switch(osName) {
        case "linux":
            return "lpot-release-wheel-build"
        case "windows":
            return "lpot-release-wheel-build-win"
        default:
            throw new Exception("Unknown OS: \"${osName}\"")
    }
}

def getBuildParams(osName, binaryClass, pythonVersion) {
    def subnode_label = node_label + " && " + osName;

    List jobParams = []

    jobParams += string(name: "node_label", value: "${subnode_label}")
    jobParams += string(name: "lpot_url", value: "${inc_url}")
    jobParams += string(name: "MR_source_branch", value: "${PR_source_branch}")
    jobParams += string(name: "MR_target_branch", value: "${PR_target_branch}")
    jobParams += string(name: "lpot_branch", value: "${inc_branch}")
    jobParams += string(name: "conda_env", value: "${conda_env}")
    jobParams += string(name: "binary_class", value: "${binaryClass}")
    jobParams += string(name: "val_branch", value: "${val_branch}")
    jobParams += string(name: "python_version", value: "${pythonVersion}")

    return jobParams
}

def runJobs(jobsList) {
    try {
        if (jobsList.size() > 0) {
            stage("Run build jobs") {
                parallel(jobsList)
            }
        }
    } catch(e) {
        Exception("RunBuildJobs", e)
        throw e
    }
}

def archiveArtifacts() {
    try {
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: 'windows_binaries/**, linux_binaries/**', excludes: null, allowEmptyArchive: true
            fingerprint: true
        }
    } catch(e) {
        Exception("ArchiveArtifacts", e)
        throw e
    }
}


def main() {
    try {
        cleanup()
        configurationsTree = getConfigurationsTree()
        def jobsList = getJobsList(configurationsTree)
        runJobs(jobsList)
    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        archiveArtifacts()
    }
}


node(top_node_label) {
    main()
}
