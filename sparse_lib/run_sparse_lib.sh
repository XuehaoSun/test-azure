export BENCHMARK_ITER=100
export BENCHMARK_NO_REFRESH=0
op=$1
mode=$2
shape_list=$3
param=$4
order_set=$5
sparse_ratio_list=$6
precision=$7
post_op=$8
dim_list=$9
output_log=${WORKSPACE}/benchmark.log
echo "params list"
echo -e ${op}
echo -e ${mode}
echo -e ${shape_list}
echo -e ${param}
echo -e ${order_set}
echo -e ${sparse_ratio}
echo -e ${precision}
echo -e ${post_op}
echo -e ${dim_list}
conda activate ${conda_env_name} || source activate ${conda_env_name}
if [[ ${precision} == "bf16" ]] && [[ ${op} == "sparse_matmul" ]]; then
    [[ -d ${WORKSPACE}/lpot-models/nlp_toolkit/backends/neural_engine/test/SparseLib/benchmark/build ]] && rm -fr ${WORKSPACE}/lpot-models/nlp_toolkit/backends/neural_engine/test/SparseLib/benchmark/build
    cd ${WORKSPACE}/lpot-models
    git submodule update --init --recursive
    cd nlp_toolkit/backends/neural_engine/test/SparseLib/benchmark
    mkdir build
    cd build
    cmake .. -DSPARSE_LIB_USE_AMX=True
    make -j
else
    if [[ ! -d ${WORKSPACE}/lpot-models/nlp_toolkit/backends/neural_engine/test/SparseLib/benchmark/build ]]; then
        cd ${WORKSPACE}/lpot-models
        git submodule update --init --recursive
        cd nlp_toolkit/backends/neural_engine/test/SparseLib/benchmark
        mkdir build
        cd build
        cmake .. 
        make -j
    else
        cd ${WORKSPACE}/lpot-models/nlp_toolkit/backends/neural_engine/test/SparseLib/benchmark/build
    fi
fi


function get_best_result {
    local cmd=$1
    local run_times=10
    echo "start running" > results_file
    for((j=0;$j<${run_times};j=$(($j + 1))));
    do
        ${cmd} >> results_file 2>&1
    done
    if [[ ${mode} == "acc" ]]; then
        correct_times=$(cat results_file | grep correct | wc -l)
        if [[ $correct_times == $run_times ]]; then
            echo "acc;correct;"
        else
            echo "acc;incorrect;"
        fi
    else
        execution_time=$(cat results_file | grep -o "kernel execution time: [0-9]\+\.[0-9]\+" | sort -t : -k 2 | sed -n 5p | awk -F ": " '{print $2}')
        GFLOPS=$(cat results_file | grep -o "GFLOPS:.*" | sort -t : -k 2 | sed -n 5p | awk -F ":" '{print $2}')
        echo "perf;${execution_time},${GFLOPS};"
    fi
    echo "running commond ${cmd}" >> ${output_log}
    cat results_file >> ${output_log}
}

if [[ -z ${param} ]] || [[ ${param} == "" ]]; then
    echo "no need run for ${op} with precision ${precision}"
else 
    if [[ ${op} == "sparse_matmul" ]]; then
        for shapes in ${shape_list[@]}
        do
            echo "shape is ${shapes}"
            for dim in ${dim_list[@]}
            do
                echo "dim is ${dim}"
                shapes_refine=$(echo ${shapes} | tr '_' " ")
                echo "after handling, shape is ${shapes_refine}"
                shape="${shapes_refine} ${dim}"
                for sparse_ratio in ${sparse_ratio_list[@]}
                do
                    cmd="./benchmark ${mode} ${op} ${order_set} ${shape} ${sparse_ratio} ${param}"
                    result=$(get_best_result "${cmd}")
                    echo "sparse_matmul;${order_set};${shapes}_${dim};${sparse_ratio};${precision};${post_op};${result}"
                    echo "sparse_matmul;${order_set};${shapes}_${dim};${sparse_ratio};${precision};${post_op};${result}" >> ${WORKSPACE}/benchmark_summary.log
                done
            done
        done

    else
        for shapes in ${shape_list[@]}
        do
            echo "shape is ${shapes}"
            shape=$(echo ${shapes} | tr '_' " ")
            echo "after handling, shape is ${shape}"
            cmd="./benchmark ${mode} ${op} ${shape} ${param}"
            result=$(get_best_result "${cmd}")
            echo "${op};na;${shapes};na;${precision};${post_op};${result}"
            echo "${op};na;${shapes};na;${precision};${post_op};${result}" >> ${WORKSPACE}/benchmark_summary.log
        done  
    fi
fi
