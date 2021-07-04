@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

// setting node_label
node_label = ""
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

// setting tensorflow_oob_models
tensorflow_oob_models = ""
if ('tensorflow_oob_models' in params && params.tensorflow_oob_models != '') {
    tensorflow_oob_models = params.tensorflow_oob_models
}
echo "tensorflow_oob_models: ${tensorflow_oob_models}"

// benchmark precision
precision = 'fp32'
if ('precision' in params && params.precision != '') {
    precision = params.precision
}
echo "Precision: ${precision}"

// int8 model path
int8_path_prefix=""
if ('int8_path_prefix' in params && params.int8_path_prefix != ''){
    int8_path_prefix=params.int8_path_prefix
}
echo "int8_path_prefix: ${int8_path_prefix}"

// fp32 model path
fp32_path_prefix=""
if ('fp32_path_prefix' in params && params.fp32_path_prefix != ''){
    fp32_path_prefix=params.fp32_path_prefix
}
echo "fp32_path_prefix: ${fp32_path_prefix}"

conda_env_name=""
if ('conda_env_name' in params && params.conda_env_name != ''){
    conda_env_name=params.conda_env_name
}
echo "conda_env_name: ${conda_env_name}"

verbose=false
if (params.verbose != null){
    verbose=params.verbose
}
echo "verbose: ${verbose}"

def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        rm -rf *
        rm -rf .git
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

def parseStrToList(srtingElements, delimiter=',') {
    if (srtingElements == ''){
        return []
    }
    return srtingElements[0..srtingElements.length()-1].tokenize(delimiter)
}

node(node_label){
    try{

        cleanup()

        dir('lpot-validation') {
            retry(5) {
                checkout scm
            }
        }

        echo "WORKSPACE IS ${WORKSPACE}"
        SUMMARYTXT = "${WORKSPACE}/summary.log"
        writeFile file: SUMMARYTXT, text: "Framework,Precision,Model,Mode,BS,Value\n"


        stage("Benchmark"){
            def model_list = []
            model_list = parseStrToList(tensorflow_oob_models)
            model_list.each { each_model ->
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    // get input model
                    if (precision == "int8") {
                        input_model = "${int8_path_prefix}" + "/" + "tensorflow-" + "${each_model}" + "-tune.pb"
                    } else {
                        try {
                            // get params for tuning and benchmark
                            def modelConf = jsonParse(readFile("$WORKSPACE/lpot-validation/config/model_params_tensorflow.json"))."tensorflow"."${each_model}"
                            input_model = modelConf."input_model"
                            input_model = "${fp32_path_prefix}" + "${input_model}"
                        } catch (e) {
                            error("Could not load parameters for ${framework} ${model}")
                        }
                    }
                    sh """#!/bin/bash -x
                        echo "Running ---- ${each_model},${precision} ---- Benchmarking"
                        
                        echo "-------w-------"
                        w
                        echo "-------w-------"
                        echo "=======cache clean======="
                        
                        sudo bash ${WORKSPACE}/lpot-validation/scripts/cache_clean.sh
            
                        echo "=======cache clean======="
                        bash ${WORKSPACE}/lpot-validation/OOB_benchmark/launch_benchmark.sh \
                            --model=${each_model} \
                            --input_model=${input_model} \
                            --precision=${precision} \
                            --conda_env_name=${conda_env_name} \
                            --verbose=${verbose}
                    """
                }
            }
        }

    }catch(e){
        currentBuild.result = "FAILURE"
        throw e
    }finally {
        stage("Archive Artifacts") {
            dir(WORKSPACE){
                sh'''#!/bin/bash
                if [ -f summary.log ]; then
                    cp summary.log results.csv
                fi
                '''
            }
            archiveArtifacts artifacts: "summary.log,results.csv,benchmark_logs/**,verbose_logs/**", excludes: null
            fingerprint: true
        }
    }

}