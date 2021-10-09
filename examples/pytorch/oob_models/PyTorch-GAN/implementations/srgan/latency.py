import re
import os
import subprocess


def read_latency(file):
    with open(file, 'r') as f:
        content = f.read()
        lat_comp = re.compile("latency: \d+.\d+")
        lat_data = re.findall(lat_comp, content)
        return float(lat_data[0].split()[1])


pattern = "int8"

'''
        "vgg11", "shufflenet_v2_x0_5", "densenet169", "densenet121", "densenet201", "mnasnet0_5", \
        "resnet152", "inception_v3", "resnet101", "resnet50", "resnet18", "spnasnet_100", "mnasnet1_0", \
        "resnext50_32x4d", "resnet34", "resnext101_32x8d", "wide_resnet101_2", "wide_resnet50_2", \
        "efficientnet_b0", "googlenet", "efficientnet_b1", "vgg19", "squeezenet1_1", "vgg19_bn", \
        "squeezenet1_0", "vgg16", "shufflenet_v2_x1_0", "vgg16_bn", 
        "vgg13", "vgg13_bn", "alexnet", "densenet161", "vgg11_bn", "fbnetc_100", \
        "spnasnet_100", "efficientnet_b2", "efficientnet_b3", "efficientnet_b4", "efficientnet_b5", \
        "efficientnet_b6", "efficientnet_b7","efficientnet_b8"
'''
model_list = ['srgan']


core_num = 24
process_num = int(core_num/4)



for model in model_list:
    print(model)

    commond = "python srgan.py --precision lpot --batch_size 1 --inference --num-iterations 100 --latent_dim 16"
    subprocess.check_output(commond, shell=True)

    i = 0
    cmd = f""
    
    for n in range(process_num):
        head = "numactl --localalloc --physcpubind {},{},{},{}  timeout 3600 ".format(i, i+1, i+2, i+3)
        end = " > bench_{}_lpot_{}.log 2>&1 & ".format(model, n+1)
        i += 4
        full_commond = head + commond + end
        cmd += full_commond
    cmd += "\nwait"
    subprocess.check_output(cmd, shell=True)

    int8_latency_all = 0
    for n in range(process_num):
        int8_file = "bench_{}_lpot_{}.log".format(model, n+1)
        int8_latency = read_latency(int8_file)
        int8_latency_all += int8_latency
    print("int8 average latency/ms: {:.3f}".format(int8_latency_all/process_num))

    # commond = "python ./main.py -e --data /lustre/dataset/imagenet/img_raw/ --performance --pretrained \
    #             --no-cuda -j 1 -a {} -b 1 --precision int8".format(model)
    # commond = "python srgan.py --precision int8 --batch_size 1 --inference --num-iterations 100 --latent_dim 16"
    # subprocess.check_output(commond, shell=True)

    # i = 0
    # cmd = f""
    # # commond = "python ./main.py -e --data /lustre/dataset/imagenet/img_raw/ --performance --pretrained \
    # #             --no-cuda -j 1 -a {} -b 1 --precision int8".format(model)
    # commond = "python srgan.py --precision int8 --batch_size 1 --inference --num-iterations 100 --latent_dim 16"
    # for n in range(process_num):
    #     head = "numactl --localalloc --physcpubind {},{},{},{}  timeout 3600 ".format(i, i+1, i+2, i+3)
    #     end = " > bench_{}_int8_{}.log 2>&1 & ".format(model, n+1)
    #     i += 4
    #     full_commond = head + commond + end
    #     cmd += full_commond
    # cmd += "\nwait"
    # subprocess.check_output(cmd, shell=True)

    # int8_latency_all = 0
    # for n in range(process_num):
    #     int8_file = "bench_{}_int8_{}.log".format(model, n+1)
    #     int8_latency = read_latency(int8_file)
    #     int8_latency_all += int8_latency
    # print("int8 average latency/ms: {:.3f}".format(int8_latency_all/process_num))

    i = 0
    cmd = f""
    # commond = "python ./main.py -e --data /lustre/dataset/imagenet/img_raw/ --performance --pretrained \
    #             --no-cuda -j 1 -a {} -b 1 --precision float32".format(model)
    commond = "python srgan.py --batch_size 1 --inference --num-iterations 100 --latent_dim 16"
    for n in range(process_num):
        head = "numactl --localalloc --physcpubind {},{},{},{}  timeout 3600 ".format(i, i+1, i+2, i+3)
        end = " > bench_{}_fp32_{}.log 2>&1 & ".format(model, n+1)
        i += 4
        full_commond = head + commond + end
        cmd += full_commond
    cmd += "\nwait"
    subprocess.check_output(cmd, shell=True)

    fp32_latency_all = 0
    for n in range(process_num):
        fp32_file = "bench_{}_fp32_{}.log".format(model, n+1)
        fp32_latency = read_latency(fp32_file)
        fp32_latency_all += fp32_latency
    print("fp32 average latency/ms: {:.3f}".format(fp32_latency_all/process_num))
