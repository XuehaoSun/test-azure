import argparse
import ast
import json
import os

import tensorflow as tf
from neural_compressor.adaptor.tf_utils.graph_rewriter.graph_base import GraphRewriterBase
from neural_compressor.adaptor.tf_utils.graph_util import GraphAnalyzer
from tensorflow.core.framework import attr_value_pb2
from tensorflow.python.framework import dtypes

parser = argparse.ArgumentParser()
parser.add_argument("--model_path", type=str, required=True, help="tensorflow oob model path")
parser.add_argument("--logfile_path", type=str, required=True, help="logfile path path")
args = parser.parse_args()

DT_BFLOAT16 = 14
DT_FP32 = 1
DT_INT32 = 3
DT_INT64 = 9

bf16_Allow_list = [
    "Conv2D", "Conv2DBackpropFilter", "Conv2DBackpropInput", "Conv3D", "Conv3DBackpropFilterV2", "Conv3DBackpropInputV2",
    "DepthwiseConv2dNative", "DepthwiseConv2dNativeBackpropFilter", "DepthwiseConv2dNativeBackpropInput", "GRUBlockCell",
    "AUGRUBlockCell", "MklGRU", "MklAUGRU", "MatMul", "BatchMatMul", "BatchMatMulV2", "Einsum"
]
bf16_infer_list = [
    "Add", "AddN", "AddV2", "AvgPool", "AvgPool3D", "AvgPool3DGrad", "AvgPoolGrad", "BiasAdd", "BiasAddGrad", "BiasAddV1",
    "Erf", "FusedBatchNormV2", "FusedBatchNormGradV2", "FusedBatchNormV3", "FusedBatchNormGradV3", "LeakyRelu",
    "LeakyReluGrad", "Mul", "Sub", "Elu", "EluGrad", "FloorDiv", "_FusedBatchNormEx", "Log", "Log1p", "LogSoftmax", "Prod",
    "RealDiv", "Reciprocal", "Selu", "SeluGrad", "Sigmoid", "SigmoidGrad", "Softmax", "Softplus", "SoftplusGrad", "Softsign",
    "SoftsignGrad", "Sqrt", "Tanh", "TanhGrad"
]
bf16_clear_list = [
    "Abs", "ArgMax", "ArgMin", "BatchToSpace", "BatchToSpaceND", "BroadcastTo", "Ceil", "CheckNumerics", "ClipByValue",
    "Concat", "ConcatV2", "DepthToSpace", "DynamicPartition", "DynamicStitch", "EnsureShape", "Enter", "Equal", "Exit",
    "ExpandDims", "Fill", "Floor", "Gather", "GatherNd", "GatherV2", "Greater", "GreaterEqual", "Identity", "IsFinite",
    "IsInf", "IsNan", "Less", "LessEqual", "Max", "Maximum", "MaxPool", "MaxPool3D", "MaxPool3DGrad", "MaxPoolGrad",
    "MaxPoolGradGrad", "MaxPoolGradGradV2", "MaxPoolGradV2", "MaxPoolV2", "Merge", "Min", "Minimum", "MirrorPad",
    "MirrorPadGrad", "Neg", "NextIteration", "NotEqual", "OnesLike", "Pack", "Pad", "PadV2", "PreventGradient", "Rank",
    "Relu", "Relu6", "Relu6Grad", "ReluGrad", "Reshape", "ResizeNearestNeighbor", "ResizeNearestNeighborGrad", "Reverse",
    "ReverseSequence", "ReverseV2", "Round", "Select", "SelectV2", "Shape", "ShapeN", "Sign", "Slice", "Snapshot",
    "SpaceToBatch", "SpaceToBatchND", "SpaceToDepth", "Split", "SplitV", "Squeeze", "StopGradient", "StridedSlice",
    "StridedSliceGrad", "Switch", "Tile", "TopK", "TopKV2", "Transpose", "Where", "Unpack", "ZerosLike"
]
int8_list = [
    ['Conv2D', ["Add", "AddV2", "AddN"], ('Relu', 'swish_f32', "Add", "AddV2", "AddN")],
    ['Conv2D', ["Add", "AddV2", "AddN"], ["Add", "AddV2", "AddN"], 'Relu'],
    ['Conv2D', ["Add", "AddV2", "AddN"], 'Relu6', 'Mul', 'Mul'],
    ['Conv2D', ['BiasAdd', 'Relu', 'Elu', 'LeakyRelu', 'Sigmoid', 'swish_f32']],
    ['Conv2D', 'BiasAdd', ["Add", "AddV2", "AddN"], ('Relu', 'LeakyRelu')],
    ['Conv2D', 'BiasAdd', ["Add", "AddV2", "AddN"], 'Relu6', 'Mul', 'Mul'],
    ['Conv2D', 'BiasAdd', ['Relu', 'Elu', 'LeakyRelu', 'swish_f32', 'Sigmoid']],
    ['Conv2D', 'BiasAdd', ['Relu', 'LeakyRelu'], ["Add", "AddV2", "AddN"]],
    ['Conv2D', 'LeakyRelu', ["Add", "AddV2", "AddN"]],
    ["MatMul", "BiasAdd", ("Add", "AddV2", "AddN", 'Gelu', "Relu", "Relu6", "Elu", "Sigmoid", "Tanh", "LeakyRelu")],
    ['MatMul', ['Gelu', 'Relu', 'Relu6', 'Elu', 'Sigmoid', 'Tanh', 'LeakyRelu']],
    ["DepthwiseConv2D", "BiasAdd", ("Relu", 'LeakyRelu', 'Sigmoid', 'swish_f32')],
    ["DepthwiseConv2D", "BiasAdd", ["Add", "AddV2", "AddN"], "Relu6", "Mul", "Mul"],
    ['DepthwiseConv2dNative', ['LeakyRelu', 'Relu', 'swish_f32']],
    ['Conv3D', ('BiasAdd')],
    ['Conv3D', ('BiasAdd'), ["Add", "AddV2", "AddN"], ('Relu')],
    [['BatchMatMul', 'BatchMatMulV2'], ['Mul', 'Add', 'AddV2', 'AddN']],
    [['BatchMatMul', 'BatchMatMulV2'], 'Mul', ['Add', 'AddV2', 'AddN']],
    [['Conv2D', 'MatMul', "DepthwiseConv2D", 'ConcatV2', 'MaxPool', 'MaxPool3D', 'AvgPool', 'FusedInstanceNorm', 'InstanceNorm']],
    [['FusedBatchNorm', 'FusedBatchNormV2', 'FusedBatchNormV3'], ('Relu')],
    [['Conv2D', 'Conv3D', 'DepthwiseConv2dNative'], 'Mul']
]


class CheckGraphQuantize(GraphRewriterBase):

    def __init__(self, model, model_name):
        super().__init__(model)
        self.input_graph = tf.compat.v1.GraphDef()
        with open(self.model, "rb") as f:
            self.input_graph.ParseFromString(f.read())
        self.model_name = model_name
        self.check_graph = GraphAnalyzer()
        self.check_graph.graph = self.input_graph
        self.graph_info = self.check_graph.parse_graph()

    def check(self, check_dtype=None, op_list=[]):
        target_nodes = self.check_graph.query_fusion_pattern_nodes(op_list)
        total_node_number = len(target_nodes)
        log_text_list = []
        summary_txt_msg = []
        summary_json_msg = []
        int32_count = 0
        int64_count = 0
        fp32_count = 0
        count = 0

        if (check_dtype == "int8"):
            result_dict = {}
            for item in target_nodes:
                item[-1] = [("Add" if name in ["AddV2", "AddN"] else name) for name in item[-1]]
                log_text = "gap detected:\t" + self.model_name + '\t' + str(item)
                print(log_text)
                log_text_list.append(log_text)
                key = str(item[-1])
                result_dict[key] = result_dict[key] + 1 if (result_dict.get(key, "")) else 1

            for op_name, count in result_dict.items():
                summary_txt_msg.append('{0:<50}\t{1:<40} fail: {2:<5}'.format(self.model_name, op_name, str(count)))
                summary_json_msg.append({"model_name": self.model_name, "op_name": ast.literal_eval(op_name), "fail": count})

        elif (check_dtype == "bf16"):
            for item in target_nodes:
                attr = self.graph_info[item[0]].node.attr["T"].type or self.graph_info[item[0]].node.attr["Tparams"].type
                if (attr == DT_BFLOAT16):
                    log_text = "correct converted:\t" + self.model_name + '\t' + str(item)
                elif (attr == DT_INT32):
                    log_text = "int32 detected:\t" + self.model_name + '\t' + str(item)
                    int32_count += 1
                elif (attr == DT_INT64):
                    log_text = "int64 detected:\t" + self.model_name + '\t' + str(item)
                    int64_count += 1
                elif (attr == DT_FP32):
                    log_text = "gap detected:\t" + self.model_name + '\t' + str(item)
                    fp32_count += 1
                else:
                    log_text = "others detected:\t" + self.model_name + '\t' + str(item) + '\n' + str(self.graph_info[item[0]].node.attr)
                    count += 1
                print(log_text)
                log_text_list.append(log_text)

            if (count or int32_count or int64_count or fp32_count):
                summary_txt_msg = [
                    '{0:<50}\t{1:<40} total: {2:<5} int32: {3:<5} int64: {4:<5} fp32(fail): {5:<5} others: {6:<5}'.format(
                        self.model_name, str(op_list), str(total_node_number), str(int32_count), str(int64_count),
                        str(fp32_count), str(count))
                ]
                summary_json_msg = [{
                    "model_name": self.model_name,
                    "op_name": op_list,
                    "total": total_node_number,
                    "int32": int32_count,
                    "int64": int64_count,
                    "fp32(fail)": fp32_count,
                    "others": count
                }]

        else:
            raise ValueError("check_dtype: " + check_dtype + " doesn't exist!")

        return summary_json_msg, log_text_list, summary_txt_msg


class CheckTFOOB():

    def __init__(self, model_list, support_list):
        self.model_list = model_list
        self.support_list = support_list
        self.summary_json_path = "summary.json"
        self.log_path = "log.txt"
        self.summary_txt_path = "summary.txt"

    def check_graph(self):
        self.create_files()
        for model in self.model_list:
            model_name = self.get_model_name(model)
            graph = CheckGraphQuantize(model, model_name)
            self.summary_json_path, self.log_path, self.summary_txt_path = os.path.join(args.logfile_path, 'summary_int8.json'), os.path.join(args.logfile_path, "log_int8.txt"), os.path.join(args.logfile_path, "summary_int8.txt")
            self.check_fp32_to_int8(graph)
            self.summary_json_path, self.log_path, self.summary_txt_path = os.path.join(args.logfile_path, 'summary_bf16.json'), os.path.join(args.logfile_path, "log_bf16.txt"), os.path.join(args.logfile_path, "summary_bf16.txt")
            self.check_fp32_to_bf16(graph)
        return None

    def check_fp32_to_int8(self, graph):
        for item in self.support_list['int8_list']:
            summary_json, log_text, msg = graph.check(check_dtype='int8', op_list=item)
            self.write_summary_json(summary_json)
            self.write_log(log_text)
            self.write_summary_txt(msg)

    def check_fp32_to_bf16(self, graph):
        for item in self.support_list['bf16_list']:
            summary_json, log_text, msg = graph.check(check_dtype='bf16', op_list=item)
            self.write_summary_json(summary_json)
            self.write_log(log_text)
            self.write_summary_txt(msg)

    def get_model_name(self, model):
        model_name = model.split('/')[-1]
        model_name = model_name.split('-')[1:-1]
        model_name = '-'.join(model_name)
        return model_name

    def create_files(self):
        files = [
            'summary_int8.json', "log_int8.txt", "summary_int8.txt", 'summary_bf16.json', "log_bf16.txt", "summary_bf16.txt"
        ]
        for file in files:
            file = os.path.join(args.logfile_path, file)
            if (not os.path.exists(file)):
                file = open(file, 'w')
                file.close()

    def write_summary_json(self, summary):
        if (not len(summary)):
            return 0
        try:
            with open(self.summary_json_path, "r", encoding='utf-8') as f:
                content = json.load(f)
                content.append(summary)
        except json.decoder.JSONDecodeError:
            with open(self.summary_json_path, "w", encoding='utf-8') as f:
                json.dump([summary], f, indent=2, ensure_ascii=False)
        else:
            with open(self.summary_json_path, "w", encoding='utf-8') as f:
                json.dump(content, f, indent=2, ensure_ascii=False)

    def write_summary_txt(self, summary_txt_msg):
        if (not len(summary_txt_msg)):
            return 0
        file = open(self.summary_txt_path, 'a')
        for msg in summary_txt_msg:
            file.write(str(msg) + '\n')
        file.close()

    def write_log(self, log_list):
        if (not len(log_list)):
            return 0
        f = open(self.log_path, 'a')
        for text in log_list:
            f.write(text + '\n')
        f.close()


def find_file(search_path, include_str=None, exclude_strs=[]):
    files = []
    names = os.listdir(search_path)

    for name in names:
        path = os.path.abspath(os.path.join(search_path, name))
        if (os.path.isfile(path)):
            if ((include_str is not None) and (include_str not in name)):
                continue
            for exclude_str in exclude_strs:
                if (exclude_str in name):
                    break
            else:
                files.append(path)
        else:
            files += find_file(path, include_str=include_str, exclude_strs=exclude_strs)

    return files


if __name__ == '__main__':
    bf16_list = [bf16_Allow_list, bf16_infer_list, bf16_clear_list]
    bf16_list = [[item] for sublist in bf16_list for item in sublist]

    support_op_list = {'int8_list': int8_list, 'bf16_list': bf16_list}
    model_list = find_file(args.model_path, '.pb')
    model_list = [os.path.join(args.model_path, model) for model in model_list]

    test = CheckTFOOB(model_list, support_op_list)
    test.check_graph()
