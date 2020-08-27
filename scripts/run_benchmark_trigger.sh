#!/bin/bash

set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "9" ] ; then
    echo 'ERROR:'
    echo "Expected 9 parameters got $#"
    printf 'Please use following parameters:
    --framework=<framework name>
    --model=<model name>
    --model_src_dir=<path to model tuning script>
    --dataset_location=<path to dataset>
    --input_model=<path to input model>
    --precision=<kind of data precision>
    --mode=<benchmark mode>
    --batch_size=<batch_size for accuracy and throughput>
    --conda_env_name=<conda environment name>
    '
    exit 1
fi

for i in "$@"
do
    case $i in
        --framework=*)
            framework=`echo $i | sed "s/${PATTERN}//"`;;
        --model=*)
            model=`echo $i | sed "s/${PATTERN}//"`;;
        --model_src_dir=*)
            model_src_dir=`echo $i | sed "s/${PATTERN}//"`;;
        --dataset_location=*)
            dataset_location=`echo $i | sed "s/${PATTERN}//"`;;
        --input_model=*)
            input_model=`echo $i | sed "s/${PATTERN}//"`;;
        --precision=*)
            precision=`echo $i | sed "s/${PATTERN}//"`;;
        --mode=*)
            mode=`echo $i | sed "s/${PATTERN}//"`;;
        --batch_size=*)
            batch_size=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

# Run Benchmark
main() {
    # Import common functions
    source ${WORKSPACE}/ilit-validation/scripts/env_setup.sh --framework=${framework} --model=${model} --conda_env_name=${conda_env_name}

    echo -e "\nSetting environment..."
    set_environment

    if [ -d ${model_src_dir} ]; then
        cd ${model_src_dir}
        echo -e "\nWorking in $(pwd)..."
    else
        echo "[ERROR] model_src_dir \"${model_src_dir}\" not exists."
        exit 1
    fi

    echo -e "\nInstalling model requirements..."
    if [ -f "requirements.txt" ]; then
        sed -i '/ilit/d' requirements.txt
        python -m pip install -r requirements.txt
        pip list
    else
        echo "Not found requirements.txt file."
    fi


    echo -e "\nGetting git information..."
    echo "$(git remote -v)"
    echo "$(git branch)"
    echo "$(git show | head -5)"

    q_model=${WORKSPACE}/${framework}-${model}-tune
    if [ ${framework} == "tensorflow" ]; then
        q_model="${q_model}.pb"
    fi

    # ------ WORKAROUND FOR MXNET RESNET50V1 -----
    topology=${model}
    if [ "${model}" == "resnet50v1" ]; then
        topology="resnet50_v1"
    fi

    if [[ "${model}" == *"_qat" ]]; then
        topology="${model%_qat} "
    fi

    if [ ${precision} == 'int8' ]; then
      input_model=${q_model}
    fi
    # set parameters for benchmark
    parameters="--topology=${topology} --dataset_location=${dataset_location} --input_model=${input_model}"

    case ${mode} in
      accuracy)
        run_accuracy;;
      throughput)
        run_benchmark;;
      latency)
        run_benchmark;;
      *)
        echo "MODE ${mode} not recognized."; exit 1;;
    esac
}

function run_accuracy {
  parameters="${parameters} --mode=accuracy --batch_size=${batch_size}"

  if [ -f "run_benchmark.sh" ]; then
        run_cmd="bash run_benchmark.sh ${parameters}"
  else
        echo "Not found run_benchmark file."
        exit 1
  fi

  logFile=${WORKSPACE}/${framework}_${model}_${precision}_${mode}.log
  echo "RUNCMD: $run_cmd " >& ${logFile}
  eval "${run_cmd}" >> ${logFile}
}

function run_benchmark {
  # get cpu information for multi-instance
  nsockets=$( lscpu | grep 'Socket(s)' | cut -d: -f2 | xargs echo -n)
  ncores_per_socket=${ncores_per_socket:=$( lscpu | grep 'Core(s) per socket' | cut -d: -f2 | xargs echo -n)}
  total_cores=$((nsockets * ncores_per_socket))

  if [[ ${mode} == "latency" ]]; then
      ncores_per_instance=4
      batch_size=1
      iters=200
      counts=3
  else
      ncores_per_instance=${ncores_per_socket}
      iters=100
      counts=1
  fi

  export OMP_NUM_THREADS=${ncores_per_instance}

  if [ "${model}" == "wide_deep_large_ds" ]; then
    iters=200
  fi

  parameters="${parameters} --mode=benchmark --batch_size=${batch_size} --iters=${iters}"

  if [ -f "run_benchmark.sh" ]; then
        run_cmd="bash run_benchmark.sh ${parameters}"
  else
        echo "Not found run_benchmark file."
        exit 1
  fi

  echo "BENCHMARK RUNCMD: $run_cmd "
  logFile=${WORKSPACE}/${framework}_${model}_${precision}_${mode}
  for((k=0;$k<${counts};k=$(($k + 1))));
  do
    for((j=0;$j<${total_cores};j=$(($j + ${ncores_per_instance}))));
    do
       numactl -l -C "$j-$((j + ncores_per_instance -1)),$((j + total_cores))-$((j + total_cores + ncores_per_instance- 1))" \
       ${run_cmd} 2>&1|tee ${logFile}_${total_cores}_${ncores_per_instance}_${j}_${k}.log &
    done
    wait
  done
  wait

}

main
