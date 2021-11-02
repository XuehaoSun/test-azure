#!/bin/bash -x

export GLOG_minloglevel=2
export DNNL_MAX_CPU_ISA=AVX512_CORE_AMX
export LD_PRELOAD=${WORKSPACE}/deep-engine/engine/examples/nlp/libiomp5.so

model=$1
seq_len=$2
ncores_per_instance=$3
bs=$4
config=$5
weight=$6
precision=$7

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
    cores=${ncores_per_socket}
fi

export OMP_NUM_THREADS=${ncores_per_instance}
run_cmd=" inferencer --config=${config} --weight=${weight} -w=${warm_up_steps} --iterations=${iteration} --batch_size=${bs} --seq_len=${seq_len}"
for((j=0;$j<${cores};j=$(($j + ${ncores_per_instance}))));
do
    numactl -C "$j-$((j + ncores_per_instance -1))" \
    ${run_cmd} 2>&1|tee ${cores}_${ncores_per_instance}_${bs}_${precision}_${j}.log &
done
wait

throughput=$(find . -name "${cores}_${ncores_per_instance}_${bs}_${precision}*" | xargs grep -nRH "Through" | awk '{print $NF}' | awk '{ SUM += $1} END { print SUM }')
echo "throughput,${model},${seq_len},${cores},${ncores_per_instance},${bs},${precision},${throughput}" >> ${WORKSPACE}/summary

log_dir=${WORKSPACE}/${model}/${seq_len}/${cores}_${ncores_per_instance}_${bs}
mkdir -p ${log_dir}
mv *.log ${log_dir}
