import argparse
import os
import numpy as np
import math
import sys

import torchvision.transforms as transforms
from torchvision.utils import save_image

from torch.utils.data import DataLoader
from torchvision import datasets
from torch.autograd import Variable

import torch.nn as nn
import torch.nn.functional as F
import torch
import time

os.makedirs("images", exist_ok=True)

parser = argparse.ArgumentParser()
parser.add_argument("--n_epochs", type=int, default=200, help="number of epochs of training")
parser.add_argument("--batch_size", type=int, default=64, help="size of the batches")
parser.add_argument("--lr", type=float, default=0.00005, help="learning rate")
parser.add_argument("--n_cpu", type=int, default=8, help="number of cpu threads to use during batch generation")
parser.add_argument("--latent_dim", type=int, default=100, help="dimensionality of the latent space")
parser.add_argument("--img_size", type=int, default=28, help="size of each image dimension")
parser.add_argument("--channels", type=int, default=1, help="number of image channels")
parser.add_argument("--n_critic", type=int, default=5, help="number of training steps for discriminator per iter")
parser.add_argument("--clip_value", type=float, default=0.01, help="lower and upper clip value for disc. weights")
parser.add_argument("--sample_interval", type=int, default=400, help="interval betwen image samples")
parser.add_argument('--inference', action='store_true', default=False)
parser.add_argument('--num-iterations', default=10000, type=int)
parser.add_argument('--ipex', action='store_true', default=False)
parser.add_argument('--precision', default='float32', help='Precision, "float32" or "bfloat16"')
parser.add_argument('--channels_last', type=int, default=1, help='use channels last format')
parser.add_argument('--tune', action='store_true', default=False, help="Tune")
parser.add_argument('--profile', action='store_true', default=False, help="Profile")
opt = parser.parse_args()
print(opt)

img_shape = (opt.channels, opt.img_size, opt.img_size)

cuda = True if torch.cuda.is_available() else False

def test_quant_fx_graph(model):
    from torch.quantization import default_qconfig
    from torch.quantization.quantize_fx import prepare_fx, convert_fx, fuse_fx
    model = model.eval()
    qconfig_dict = {"": default_qconfig}
    model = prepare_fx(model, qconfig_dict)
    model = convert_fx(model)
    print(type(model).__name__, " fx mode quantization Pass!")
    return model

def save_profile_result(filename, table):
    import xlsxwriter
    workbook = xlsxwriter.Workbook(filename)
    worksheet = workbook.add_worksheet()
    keys = ["Name", "Self CPU total %", "Self CPU total", "CPU total %" , "CPU total", \
            "CPU time avg", "Number of Calls"]
    for j in range(len(keys)):
        worksheet.write(0, j, keys[j])

    lines = table.split("\n")
    for i in range(3, len(lines)-4):
        words = lines[i].split(" ")
        j = 0
        for word in words:
            if not word == "":
                worksheet.write(i-2, j, word)
                j += 1
    workbook.close()

class DataLoader(object):
    def __init__(self, data=None, batch_size=1):
        self.data = data
        self.batch_size = batch_size
    def __iter__(self):
        yield self.data[0], self.data[1]


class Generator(nn.Module):
    def __init__(self):
        super(Generator, self).__init__()

        def block(in_feat, out_feat, normalize=True):
            layers = [nn.Linear(in_feat, out_feat)]
            if normalize:
                layers.append(nn.BatchNorm1d(out_feat, 0.8))
            layers.append(nn.LeakyReLU(0.2, inplace=True))
            return layers

        self.model = nn.Sequential(
            *block(opt.latent_dim, 128, normalize=False),
            *block(128, 256),
            *block(256, 512),
            *block(512, 1024),
            nn.Linear(1024, int(np.prod(img_shape))),
            nn.Tanh()
        )

    def forward(self, z):
        img = self.model(z)
        img = img.view(img.shape[0], *img_shape) #-1
        print(img.shape)
        return img


class Discriminator(nn.Module):
    def __init__(self):
        super(Discriminator, self).__init__()

        self.model = nn.Sequential(
            nn.Linear(int(np.prod(img_shape)), 512),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Linear(512, 256),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Linear(256, 1),
        )

    def forward(self, img):
        img_flat = img.view(img.shape[0], -1)
        validity = self.model(img_flat)
        return validity


# Initialize generator and discriminator
generator = Generator()
discriminator = Discriminator()

if cuda:
    generator.cuda()
    discriminator.cuda()

# Configure data loader
os.makedirs("../../data/mnist", exist_ok=True)
dataloader = torch.utils.data.DataLoader(
    datasets.MNIST(
        "../../data/mnist",
        train=True,
        download=True,
        transform=transforms.Compose([transforms.ToTensor(), transforms.Normalize([0.5], [0.5])]),
    ),
    batch_size=opt.batch_size,
    shuffle=True,
)

# Optimizers
optimizer_G = torch.optim.RMSprop(generator.parameters(), lr=opt.lr)
optimizer_D = torch.optim.RMSprop(discriminator.parameters(), lr=opt.lr)

Tensor = torch.cuda.FloatTensor if cuda else torch.FloatTensor
FloatTensor = torch.cuda.FloatTensor if cuda else torch.FloatTensor
LongTensor = torch.cuda.LongTensor if cuda else torch.LongTensor
# from torch.fx import symbolic_trace
# traced = symbolic_trace(generator)
# torch.save(traced, "traced_wgan-generator.pth")
# print('saved trace')
# try:
#     q_model = torch.jit.script(generator.eval())
# except:
#     try:
#         for input, _ in dataloader:
#             q_model = torch.jit.trace(generator.eval(), input)
#             break
#     except:
#         logger.info("This model can't convert to Script model")
# q_model.save("jit_wgan-generator.pth")
# print('saved jit')
# print(0/0)
# ----------
#  Training
# ----------

def generate(netG, batchsize, device):
    if opt.tune:
        from neural_compressor.experimental import Quantization, common
        quantizer = Quantization(('../../conf.yaml'))
        n_row = 10
        fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (n_row ** 2, opt.latent_dim))))
        labels = np.array([num for _ in range(n_row) for num in range(n_row)])
        labels = Variable(LongTensor(labels))
        dataset = (fixed_noise, labels)
        calib_dataloader = DataLoader(dataset)
        quantizer.calib_dataloader = calib_dataloader
        quantizer.model = common.Model(netG)
        q_model = quantizer()
        q_model.save("saved_results")
        return
    else:
        if opt.precision == 'int8':
            from neural_compressor.utils.pytorch import load
            netG = load("saved_results"
            , netG)


    # print(netG_int8)
    n_row = 10
    fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (n_row ** 2, opt.latent_dim))))
    dry_run = 5
    netG.eval()

    if opt.channels_last:
        netG_oob, fixed_noise_oob = netG, fixed_noise
        netG_oob = netG_oob.to(memory_format=torch.channels_last)
        try:
            fixed_noise_oob = fixed_noise_oob.to(memory_format=torch.channels_last)
        except:
            print("Input NHWC failed! Use normal input.")
        netG, fixed_noise = netG_oob, fixed_noise_oob
    else:
        netG = netG.to(device=device)
        fixed_noise = fixed_noise.to(device=device)
    # if opt.jit:
    #     netG = torch.jit.trace(netG, fixed_noise)
    tic = 0
    
    with torch.no_grad():
        for i in range(dry_run + opt.num_iterations):
            if i == dry_run:
                tic = time.time()
                fake = netG(fixed_noise)
            elif i > dry_run:
                if opt.profile and i == dry_run+opt.num_iterations-1:
                    if opt.precision == "bfloat16":
                        with torch.cpu.amp.autocast(enabled=True, dtype=torch.bfloat16):
                            with torch.autograd.profiler.profile() as prof:
                                fake = netG(fixed_noise)
                    elif opt.precision =='lpot':
                        with torch.autograd.profiler.profile() as prof:
                            fake = netG(fixed_noise)
                        print(prof.key_averages().table(sort_by="self_cpu_time_total"))
                    elif opt.precision == 'int8':
                        with torch.autograd.profiler.profile() as prof:
                            fake = netG_int8(fixed_noise)
                    else:
                        with torch.autograd.profiler.profile() as prof:
                            fake = netG(fixed_noise)
                    print(prof.key_averages().table(sort_by="self_cpu_time_total"))
                    table_res = prof.key_averages().table(sort_by="cpu_time_total")
                    timeline_path = "./timeline_logs/wgan/"
                    if not os.path.exists(timeline_path):
                        os.makedirs(timeline_path)
                    save_profile_result(timeline_path + torch.backends.quantized.engine + opt.precision+ "_result_average.xlsx", table_res)
                else:
                    if opt.precision == "bfloat16":
                        with torch.cpu.amp.autocast(enabled=True, dtype=torch.bfloat16):
                            fake = netG(fixed_noise)
                    else:
                        fake = netG(fixed_noise)
            else:
                fake = netG(fixed_noise)


    toc = time.time() - tic
    print("Throughput: %.2f images/sec, batchsize: %d" % ((opt.num_iterations*batchsize)/toc, batchsize))
    print("Latency: %.2f ms" % (1000*toc/opt.num_iterations))

if opt.inference:
    print("----------------Generation benchmarking---------------")
    generate(generator, opt.batch_size, device=torch.device('cpu'))
    import sys
    sys.exit(0)

# batches_done = 0
# for epoch in range(opt.n_epochs):

#     for i, (imgs, _) in enumerate(dataloader):

#         # Configure input
#         real_imgs = Variable(imgs.type(Tensor))

#         # ---------------------
#         #  Train Discriminator
#         # ---------------------

#         optimizer_D.zero_grad()

#         # Sample noise as generator input
#         z = Variable(Tensor(np.random.normal(0, 1, (imgs.shape[0], opt.latent_dim))))

#         # Generate a batch of images
#         fake_imgs = generator(z).detach()
#         # Adversarial loss
#         loss_D = -torch.mean(discriminator(real_imgs)) + torch.mean(discriminator(fake_imgs))

#         loss_D.backward()
#         optimizer_D.step()

#         # Clip weights of discriminator
#         for p in discriminator.parameters():
#             p.data.clamp_(-opt.clip_value, opt.clip_value)

#         # Train the generator every n_critic iterations
#         if i % opt.n_critic == 0:

#             # -----------------
#             #  Train Generator
#             # -----------------

#             optimizer_G.zero_grad()

#             # Generate a batch of images
#             gen_imgs = generator(z)
#             # Adversarial loss
#             loss_G = -torch.mean(discriminator(gen_imgs))

#             loss_G.backward()
#             optimizer_G.step()

#             print(
#                 "[Epoch %d/%d] [Batch %d/%d] [D loss: %f] [G loss: %f]"
#                 % (epoch, opt.n_epochs, batches_done % len(dataloader), len(dataloader), loss_D.item(), loss_G.item())
#             )

#         if batches_done % opt.sample_interval == 0:
#             save_image(gen_imgs.data[:25], "images/%d.png" % batches_done, nrow=5, normalize=True)
#         batches_done += 1
