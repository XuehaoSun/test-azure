#!/bin/bash
set -x

set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='

for i in "$@"; do
  case $i in
  --framework=*)
    framework=$(echo $i | sed "s/${PATTERN}//")
    ;;
  --model=*)
    model=$(echo $i | sed "s/${PATTERN}//")
    ;;
  --input_model=*)
    input_model=$(echo $i | sed "s/${PATTERN}//")
    ;;
  --precision=*)
    precision=$(echo $i | sed "s/${PATTERN}//")
    ;;
  --batch_size=*)
    batch_size=$(echo $i | sed "s/${PATTERN}//")
    ;;
  --conda_env_name=*)
    conda_env_name=$(echo $i | sed "s/${PATTERN}//")
    ;;
  *)
    echo "Parameter $i not recognized."
    exit 1
    ;;
  esac
done

main() {
  # Import common functions
  source ${WORKSPACE}/ilit-validation/scripts/env_setup.sh --framework=${framework} --model=${model} --conda_env_name=${conda_env_name}

  echo -e "\nSetting environment..."
  set_environment

  echo -e "\nGetting git information..."
  echo "$(git remote -v)"
  echo "$(git branch)"
  echo "$(git show | head -5)"

  q_model=${WORKSPACE}/${framework}-${model}-tune
  if [ ${framework} == "tensorflow" ]; then
      q_model="${q_model}.pb"
  fi

  if [ ${precision} == 'int8' ]; then
    input_model=${q_model}
  fi

  cd ${WORKSPACE}/ilit-validation/examples/${framework}
  if [ ${framework} = "tensorflow" ] && [ ${model} = "resnet50v1.0" ]; then
    run_cmd="python main.py --input-graph ${input_model} --input input --output predict  --benchmark"
  fi

  if [ ${framework} = "tensorflow" ] && [ ${model} = "resnet50v1.5" ]; then
    run_cmd="python main.py --input-graph ${input_model} --input input_tensor --output softmax_tensor --r_mean 123.68 --g_mean 116.78 --b_mean 103.94 --benchmark"
  fi

  if [ ${framework} = "mxnet" ]; then
    symbol_file=${input_model}/"resnet50_v1-symbol.json"
    param_file=${input_model}/"resnet50_v1-0000.params"
    run_cmd="python imagenet_inference.py \
            --symbol-file=${symbol_file} \
            --param-file=${param_file} \
            --batch-size=${batch_size} \
            --num-inference-batches=100 \
            --ctx=cpu \
            --rgb-mean=123.68,116.779,103.939 --rgb-std=58.393,57.12,57.375 \
            --benchmark True"
  fi

  run_benchmark
}

function run_benchmark {
  # get cpu information for multi-instance
  nsockets=$( lscpu | grep 'Socket(s)' | cut -d: -f2 | xargs echo -n)
  ncores_per_socket=${ncores_per_socket:=$( lscpu | grep 'Core(s) per socket' | cut -d: -f2 | xargs echo -n)}
  total_cores=$((nsockets * ncores_per_socket))

  ncores_per_instance=${ncores_per_socket}

  export OMP_NUM_THREADS=${ncores_per_instance}
  echo "RUN_CMD: ${run_cmd}"
  logFile=${WORKSPACE}/${framework}_${model}_${precision}_throughput
  for((j=0;$j<${total_cores};j=$(($j + ${ncores_per_instance}))));
  do
     numactl -l -C "$j-$((j + ncores_per_instance -1)),$((j + total_cores))-$((j + total_cores + ncores_per_instance- 1))" \
     ${run_cmd} 2>&1|tee ${logFile}_${total_cores}_${ncores_per_instance}_${j}.log &
  done

  wait

}

main