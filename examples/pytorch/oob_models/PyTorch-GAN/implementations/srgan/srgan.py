"""
Super-resolution of CelebA using Generative Adversarial Networks.
The dataset can be downloaded from: https://www.dropbox.com/sh/8oqt9vytwxb3s4r/AADIKlz8PR9zr6Y20qbkunrba/Img/img_align_celeba.zip?dl=0
(if not available there see if options are listed at http://mmlab.ie.cuhk.edu.hk/projects/CelebA.html)
Instrustion on running the script:
1. Download the dataset from the provided link
2. Save the folder 'img_align_celeba' to '../../data/'
4. Run the sript using command 'python3 srgan.py'
"""

import argparse
import os
import numpy as np
import math
import itertools
import sys

import torchvision.transforms as transforms
from torchvision.utils import save_image, make_grid

from torch.utils.data import DataLoader
from torch.autograd import Variable
from torchvision import datasets



from models import *
from datasets import *

import torch.nn as nn
import torch.nn.functional as F
import torch
import time

os.makedirs("images", exist_ok=True)
os.makedirs("saved_models", exist_ok=True)

parser = argparse.ArgumentParser()
parser.add_argument("--epoch", type=int, default=0, help="epoch to start training from")
parser.add_argument("--n_epochs", type=int, default=200, help="number of epochs of training")
parser.add_argument("--dataset_name", type=str, default="img_align_celeba", help="name of the dataset")
parser.add_argument("--batch_size", type=int, default=4, help="size of the batches")
parser.add_argument("--lr", type=float, default=0.0002, help="adam: learning rate")
parser.add_argument("--b1", type=float, default=0.5, help="adam: decay of first order momentum of gradient")
parser.add_argument("--b2", type=float, default=0.999, help="adam: decay of first order momentum of gradient")
parser.add_argument("--decay_epoch", type=int, default=100, help="epoch from which to start lr decay")
parser.add_argument("--n_cpu", type=int, default=8, help="number of cpu threads to use during batch generation")
parser.add_argument("--hr_height", type=int, default=256, help="high res. image height")
parser.add_argument("--hr_width", type=int, default=256, help="high res. image width")
parser.add_argument("--channels", type=int, default=3, help="number of image channels")
parser.add_argument("--sample_interval", type=int, default=100, help="interval between saving image samples")
parser.add_argument("--checkpoint_interval", type=int, default=-1, help="interval between model checkpoints")
parser.add_argument('--inference', action='store_true', default=False)
parser.add_argument('--num-iterations', default=10000, type=int)
parser.add_argument('--ipex', action='store_true', default=False)
parser.add_argument('--precision', default='float32', help='Precision, "float32" or "bfloat16"')
parser.add_argument('--channels_last', type=int, default=1, help='use channels last format')
parser.add_argument("--img_size", type=int, default=32, help="size of each image dimension")
parser.add_argument("--latent_dim", type=int, default=62, help="dimensionality of the latent space")
parser.add_argument('--tune', action='store_true', default=False, help="Tune")
parser.add_argument('--profile', action='store_true', default=False, help="Profile")
opt = parser.parse_args()
print(opt)

cuda = torch.cuda.is_available()

hr_shape = (opt.hr_height, opt.hr_width)

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

def test_lpot_fx_quant(model, config_path='../conf.yaml', dataset=None):
    from neural_compressor.experimental import Quantization, common
    from neural_compressor.utils.pytorch import load
    quantizer = Quantization(config_path)
    if dataset is None:
        n_row = 10
        fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (n_row ** 2, 3, opt.latent_dim, opt.latent_dim))))
        labels = np.array([num for _ in range(n_row) for num in range(n_row)])
        labels = Variable(LongTensor(labels))
        dataset = (fixed_noise, labels)
        calib_dataloader = DataLoader(dataset)
    quantizer.calib_dataloader = calib_dataloader
    quantizer.model = common.Model(model)
    q_model = quantizer()
    return q_model.model

def weights_init_normal(m):
    classname = m.__class__.__name__
    if classname.find("Conv") != -1:
        torch.nn.init.normal_(m.weight.data, 0.0, 0.02)
    elif classname.find("BatchNorm2d") != -1:
        torch.nn.init.normal_(m.weight.data, 1.0, 0.02)
        torch.nn.init.constant_(m.bias.data, 0.0)

# Initialize generator and discriminator
generator = GeneratorResNet()
discriminator = Discriminator(input_shape=(opt.channels, *hr_shape))
feature_extractor = FeatureExtractor()

# Initialize weights
generator.apply(weights_init_normal)
discriminator.apply(weights_init_normal)

# Set feature extractor to inference mode
feature_extractor.eval()

# Losses
criterion_GAN = torch.nn.MSELoss()
criterion_content = torch.nn.L1Loss()

if cuda:
    generator = generator.cuda()
    discriminator = discriminator.cuda()
    feature_extractor = feature_extractor.cuda()
    criterion_GAN = criterion_GAN.cuda()
    criterion_content = criterion_content.cuda()

if opt.epoch != 0:
    # Load pretrained models
    generator.load_state_dict(torch.load("saved_models/generator_%d.pth"))
    discriminator.load_state_dict(torch.load("saved_models/discriminator_%d.pth"))

# Optimizers
optimizer_G = torch.optim.Adam(generator.parameters(), lr=opt.lr, betas=(opt.b1, opt.b2))
optimizer_D = torch.optim.Adam(discriminator.parameters(), lr=opt.lr, betas=(opt.b1, opt.b2))

Tensor = torch.cuda.FloatTensor if cuda else torch.Tensor
FloatTensor = torch.cuda.FloatTensor if cuda else torch.FloatTensor
LongTensor = torch.cuda.LongTensor if cuda else torch.LongTensor

# dataloader = DataLoader(
#     ImageDataset("../../data/%s" % opt.dataset_name, hr_shape=hr_shape),
#     batch_size=opt.batch_size,
#     shuffle=True,
#     num_workers=1,
# )
os.makedirs("../../data/mnist", exist_ok=True)
dataloader = torch.utils.data.DataLoader(
    datasets.MNIST(
        "../../data/mnist",
        train=True,
        download=True,
        transform=transforms.Compose(
            [transforms.Resize(opt.img_size), transforms.ToTensor(), transforms.Normalize([0.5], [0.5])]
        ),
    ),
    batch_size=opt.batch_size,
    shuffle=True,
)


# from torch.fx import symbolic_trace
# traced = symbolic_trace(generator)
# torch.save(traced, "traced_sgan-generator.pth")
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
# q_model.save("jit_sgan-generator.pth")
# print('saved jit')
# print(0/0)
def generate(netG, batchsize, device):
    if opt.tune:
        from neural_compressor.experimental import Quantization, common
        quantizer = Quantization('../../conf.yaml')
        n_row = 10
        fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (n_row ** 2, 3, opt.latent_dim, opt.latent_dim))))
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
        if opt.precision == "int8":
            from neural_compressor.utils.pytorch import load
            netG = load("saved_results", netG)

    n_row = 10
    fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (n_row ** 2, 3, opt.latent_dim, opt.latent_dim))))

    # fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (1, opt.latent_dim)))) #Variable(FloatTensor(np.random.normal(0, 1, (img_shape[0], opt.latent_dim))))
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
                fake = netG(fixed_noise)

                if opt.profile and i == dry_run+opt.num_iterations-1:
                    if opt.precision == "bfloat16":
                        with torch.cpu.amp.autocast(enabled=True, dtype=torch.bfloat16):
                            with torch.autograd.profiler.profile() as prof:
                                fake = netG(fixed_noise)
                    else:
                        with torch.autograd.profiler.profile() as prof:
                            fake = netG(fixed_noise)
                    print(prof.key_averages().table(sort_by="self_cpu_time_total"))
                    table_res = prof.key_averages().table(sort_by="cpu_time_total")
                    timeline_path = "./timeline_logs/srgan/"
                    if not os.path.exists(timeline_path):
                        os.makedirs(timeline_path)
                    save_profile_result(timeline_path + torch.backends.quantized.engine + opt.precision + "_result_average.xlsx", table_res)
                # else:
                #     if opt.precision == "bfloat16":
                #         with torch.cpu.amp.autocast(enabled=True, dtype=torch.bfloat16):
                #             fake = netG(fixed_noise)
                #     else:
                #         fake = netG(fixed_noise)
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

# ----------
#  Training
# ----------

# for epoch in range(opt.epoch, opt.n_epochs):
#     for i, imgs in enumerate(dataloader):

#         # Configure model input
#         imgs_lr = Variable(imgs["lr"].type(Tensor))
#         imgs_hr = Variable(imgs["hr"].type(Tensor))

#         # Adversarial ground truths
#         valid = Variable(Tensor(np.ones((imgs_lr.size(0), *discriminator.output_shape))), requires_grad=False)
#         fake = Variable(Tensor(np.zeros((imgs_lr.size(0), *discriminator.output_shape))), requires_grad=False)

#         # ------------------
#         #  Train Generators
#         # ------------------

#         optimizer_G.zero_grad()

#         # Generate a high resolution image from low resolution input
#         gen_hr = generator(imgs_lr)

#         # Adversarial loss
#         loss_GAN = criterion_GAN(discriminator(gen_hr), valid)

#         # Content loss
#         gen_features = feature_extractor(gen_hr)
#         real_features = feature_extractor(imgs_hr)
#         loss_content = criterion_content(gen_features, real_features.detach())

#         # Total loss
#         loss_G = loss_content + 1e-3 * loss_GAN

#         loss_G.backward()
#         optimizer_G.step()

#         # ---------------------
#         #  Train Discriminator
#         # ---------------------

#         optimizer_D.zero_grad()

#         # Loss of real and fake images
#         loss_real = criterion_GAN(discriminator(imgs_hr), valid)
#         loss_fake = criterion_GAN(discriminator(gen_hr.detach()), fake)

#         # Total loss
#         loss_D = (loss_real + loss_fake) / 2

#         loss_D.backward()
#         optimizer_D.step()

#         # --------------
#         #  Log Progress
#         # --------------

#         sys.stdout.write(
#             "[Epoch %d/%d] [Batch %d/%d] [D loss: %f] [G loss: %f]"
#             % (epoch, opt.n_epochs, i, len(dataloader), loss_D.item(), loss_G.item())
#         )

#         batches_done = epoch * len(dataloader) + i
#         if batches_done % opt.sample_interval == 0:
#             # Save image grid with upsampled inputs and SRGAN outputs
#             imgs_lr = nn.functional.interpolate(imgs_lr, scale_factor=4)
#             gen_hr = make_grid(gen_hr, nrow=1, normalize=True)
#             imgs_lr = make_grid(imgs_lr, nrow=1, normalize=True)
#             img_grid = torch.cat((imgs_lr, gen_hr), -1)
#             save_image(img_grid, "images/%d.png" % batches_done, normalize=False)

#     if opt.checkpoint_interval != -1 and epoch % opt.checkpoint_interval == 0:
#         # Save model checkpoints
#         torch.save(generator.state_dict(), "saved_models/generator_%d.pth" % epoch)
#         torch.save(discriminator.state_dict(), "saved_models/discriminator_%d.pth" % epoch)
