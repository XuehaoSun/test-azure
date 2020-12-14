credential = 'lab_tfbot'
try{ echo "BRANCH=${BRANCH}"; } catch (Exception e) { BRANCH="master" ; echo "BRANCH=${BRANCH}" }
def JOB_NAMES_LIST = JOB_NAMES.split(",")
def BUILD_NUMBERS_LIST = BUILD_NUMBERS.split(",")

if (BUILD_NUMBERS_LIST.size() != 2) {
    error("Please specify exactly two builds to compare.")
}

def numberOfJobs = JOB_NAMES_LIST.size()

if (JOB_NAMES == "" || numberOfJobs < 1 || numberOfJobs > 2) {
    error("Please specify one or two job names to compare.")
}

// Extend job name list when only one job is passed
if (numberOfJobs == 1) {
    println("Extending job name list...")
    for (i = 1; i < BUILD_NUMBERS_LIST.size(); i++) {
        JOB_NAMES_LIST += JOB_NAMES_LIST[0]
    }
}

node(NODE_NAME) {

    stage("Clone validation repository") {
        dir('lpot-validation') {
            retry(5) {
                checkout scm
            }
        }
    }
    stage("Get builds summary") {
        for (i = 0; i < BUILD_NUMBERS_LIST.size(); i++) {
            println(JOB_NAMES_LIST[i] + " #" + BUILD_NUMBERS_LIST[i])
            copyArtifacts(
                projectName: JOB_NAMES_LIST[i],
                selector: specific("${BUILD_NUMBERS_LIST[i]}"),
                filter: 'summary.log, tuning_info.log',
                fingerprintArtifacts: true,
                target: "build_${i}")
            writeFile file: "build_${i}/build_info.txt", text: "${JOB_NAMES_LIST[i]},${BUILD_NUMBERS_LIST[i]}"
        }
    }

    stage("Generate report") {
        ref_dir = "build_0"
        new_dir = "build_1"
        withEnv([
            "summaryLog=${new_dir}/summary.log",
            "summaryLogLast=${ref_dir}/summary.log",
            "tuneLog=${new_dir}/tuning_info.log",
            "tuneLogLast=${ref_dir}/tuning_info.log",
        ]) {
            sh """
                chmod 775 ./lpot-validation/scripts/generate_lpot_custom_report.sh
                ./lpot-validation/scripts/generate_lpot_custom_report.sh --ref_dir=${ref_dir} --new_dir=${new_dir}
            """
        }
    }

    stage("Send report") {
            if (REPORT_RECIPIENTS.size() <= 0) {
                print("Report recipients not specified.")
            } else {
                emailext subject: "lpot report comparison",
                to: "${REPORT_RECIPIENTS}",
                body: '''${FILE,path="report.html"}''',
                attachmentsPattern: "",
                mimeType: 'text/html'
            }
        }

    stage("Archive artifacts") {
        archiveArtifacts artifacts: 'build_*/*.log, build_*/*.txt, **/*.html', excludes: null, allowEmptyArchive: true
    }
}
