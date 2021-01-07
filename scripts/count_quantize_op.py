from lpot.adaptor.tf_utils.util import read_graph, write_graph
import argparse
parser = argparse.ArgumentParser()
parser.add_argument("--fp32_model", type=str, required=True, help="Path to fp32 model.")
parser.add_argument("--int8_model", type=str, required=True, help="Path to int8 model.")
args = parser.parse_args()


def get_op_type(model):
    Conv2D_count = 0
    MatMul_count = 0
    DepthwiseConv2d_count = 0
    ConcatV2_count = 0
    graph_def = read_graph(model)
    for i in graph_def.node:
        op_name = i.op
        if op_name == 'Conv2D':
            Conv2D_count += 1
        elif op_name == 'MatMul':
            MatMul_count += 1
        elif op_name == 'DepthwiseConv2dNative':
            DepthwiseConv2d_count += 1
        elif op_name == 'ConcatV2':
            ConcatV2_count += 1
    print("Conv2D: {0}".format(Conv2D_count))
    print("MatMul: {0}".format(MatMul_count))
    print("DepthwiseConv2dNative: {0}".format(DepthwiseConv2d_count))
    print("ConcatV2: {0}".format(ConcatV2_count))


def main():
    fp32_model = args.fp32_model
    int8_model = args.int8_model
    if fp32_model.rsplit('.', 1)[-1] == 'pb':
        print('----input model op count----')
        get_op_type(fp32_model)
    if int8_model.rsplit('.', 1)[-1] == 'pb':
        print('----output model op count----')
        get_op_type(int8_model)


if __name__ == "__main__":
    main()
