export PATH=/usr/local/gcc-9.4/bin:${PATH}
export LD_LIBRARY_PATH=/usr/local/gcc-9.4/lib64:${LD_LIBRARY_PATH}

export KMP_BLOCKTIME=1
export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
export GLOG_minloglevel=2

model=$1
seq_len=$2
ncores_per_instance=$3
bs=$4
config=$5
weight=$6
precision=$7

iteration=10
sockets=$(lscpu | grep 'Socket(s)' | cut -d: -f2 | xargs echo -n)
ncores_per_socket=$( lscpu | grep 'Core(s) per socket' | cut -d: -f2 | xargs echo -n)
cores=$(($sockets*$ncores_per_socket))

export OMP_NUM_THREADS=${ncores_per_instance}

run_cmd=" ./inferencer --config=${config} --weight=${weight} --iterations=${iteration} --batch_size=${bs} --seq_len=${seq_len}"
for((j=0;$j<${cores};j=$(($j + ${ncores_per_instance}))));
do
        numactl -C "$j-$((j + ncores_per_instance -1))" \
        ${run_cmd} 2>&1|tee ${cores}_${ncores_per_instance}_${bs}_${precision}_${j}.log &
done
wait

throughput=$(find . -name "${cores}_${ncores_per_instance}_${bs}_${precision}*" | xargs grep -nRH "Through" | awk '{print $NF}' | awk '{ SUM += $1} END { print SUM }')
echo "throughput,${model},${seq_len},${cores},${ncores_per_instance},${bs},${precision},${throughput}" >> ${WORKSPACE}/summary.txt

log_dir=${WORKSPACE}/${model}/${seq_len}/${cores}_${ncores_per_instance}_${bs}
mkdir -p ${log_dir}
mv *.log ${log_dir}
