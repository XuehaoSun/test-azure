

str1 = "hello"
str2 = "world"
str3 = '!'
assert len(str1) == len(str2) == len(str3), \
    'Wrong length for str1:{}, str2:{} or str3:{}, \
     all should be the same.'.format(
        len(str1), len(str2), len(str3)
    )
def get_onnx_model():
    model = torchvision.models.resnet18()
    x = Variable(torch.randn(1, 3, 224, 224))
    torch_out = torch.onnx.export(model, x, "resnet18.onnx", export_params=True, verbose=True)
print("hello!!!")
print("hello!p!!")
print("test")
