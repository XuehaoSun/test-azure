#!/bin/bash -x

export GLOG_minloglevel=2
export DNNL_MAX_CPU_ISA=AVX512_CORE_AMX

model=$1
ir_path=$2
ncores_per_instance=$3
bs=$4
precision=$5

framework="nlp_executor"
seq_len=128
iteration=50
warm_up_steps=10
if [ "$precision" = "fp32" ]; then
    iteration=20
fi
sockets=$(lscpu | grep 'Socket(s)' | cut -d: -f2 | xargs echo -n)
ncores_per_socket=$( lscpu | grep 'Core(s) per socket' | cut -d: -f2 | xargs echo -n)
cores=$(($sockets*$ncores_per_socket))
if [ "$bs" = "1" ]; then
    iteration=1000
    cp /tf_dataset2/models/deep-engine/libiomp5.so .
    export LD_PRELOAD=${WORKSPACE}/libiomp5.so
fi

export OMP_NUM_THREADS=${ncores_per_instance}
run_cmd=" neural_engine --config=${ir_path}/conf.yaml --weight=${ir_path}/model.bin -w=${warm_up_steps} --iterations=${iteration} --batch_size=${bs} --seq_len=${seq_len}"
log_dir=${WORKSPACE}/engine-${model}/${seq_len}_${cores}_${ncores_per_instance}_${bs}
mkdir -p ${log_dir}
for((j=0;$j<${cores};j=$(($j + ${ncores_per_instance}))));
do
    numactl -C "$j-$((j + ncores_per_instance -1))" \
    ${run_cmd} 2>&1|tee ${log_dir}/${cores}_${ncores_per_instance}_${bs}_${precision}_${j}.log &
done
wait

cd ${log_dir}
throughput=$(find . -name "${cores}_${ncores_per_instance}_${bs}_${precision}*" | xargs grep -rn "Throughput" | awk '{print $NF}' | awk '{ SUM += $1} END { print SUM }')
echo "${framework},throughput,${model},${seq_len},${cores},${ncores_per_instance},${bs},${precision},${throughput}"
echo "${framework},throughput,${model},${seq_len},${cores},${ncores_per_instance},${bs},${precision},${throughput},${logs_prefix_url}/engine-${model}" >> ${WORKSPACE}/inferencer_summary.log
